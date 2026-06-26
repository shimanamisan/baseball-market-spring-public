#!/bin/bash
###############################################################################
# migrate-dev-data.sh のユニットテスト（純 bash・bats 不要）
#
# 実行: bash deploy/scripts/tests/migrate-dev-data.test.sh
#
# 方針:
#   - 対象スクリプトを `source` して関数だけを読み込む（末尾のソースガードにより
#     ディスパッチは走らない）。
#   - docker / tar など外部 IO はモック（PATH 先頭に挿入）で差し替え、振る舞いを検証する。
###############################################################################
set -uo pipefail

TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_UNDER_TEST="$TESTS_DIR/../migrate-dev-data.sh"

PASS=0; FAIL=0
pass() { PASS=$((PASS + 1)); echo "  ✓ $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  ✗ $1"; echo "      $2"; }

assert_eq() { # 期待値 実際値 ラベル
  if [ "$1" = "$2" ]; then pass "$3"; else fail "$3" "expected=[$1] actual=[$2]"; fi
}

# 対象スクリプトを source（ガードにより case は実行されない）。
# 対象は内部で `set -euo pipefail` を有効化するため、テストランナーの制御フローが
# errexit に支配されないよう、source 後に -e を明示的に無効化する（テストは exit する
# 関数をサブシェルで検証するため）。
# shellcheck source=/dev/null
source "$SCRIPT_UNDER_TEST"
set +e

# ---------------------------------------------------------------------------
# #82 resolve_uploads_path
# ---------------------------------------------------------------------------
echo "[#82] resolve_uploads_path"

# .env が APP_UPLOADS_PATH 行を含むなら、その値を返す
test_env_with_value() {
  local d; d="$(mktemp -d)"
  printf 'OTHER=1\nAPP_UPLOADS_PATH=/data/uploads\n' > "$d/.env"
  local got; got="$(APP_UPLOADS_PATH= resolve_uploads_path "$d")"
  rm -rf "$d"
  assert_eq "/data/uploads" "$got" ".env に値があればそれを返す"
}

# .env はあるが APP_UPLOADS_PATH 行が無いとき、既定値を返し正常終了する（本命: set -e で落ちないこと）
test_env_without_key_falls_back() {
  local d; d="$(mktemp -d)"
  printf 'OTHER=1\n' > "$d/.env"
  local got rc
  got="$(APP_UPLOADS_PATH= resolve_uploads_path "$d")"; rc=$?
  rm -rf "$d"
  assert_eq "0" "$rc" "APP_UPLOADS_PATH 未定義でも異常終了しない（rc=0）"
  assert_eq "/var/lib/baseball-market/uploads" "$got" "APP_UPLOADS_PATH 未定義なら既定値を返す"
}

# 環境変数 APP_UPLOADS_PATH が最優先される
test_env_var_precedence() {
  local d; d="$(mktemp -d)"
  printf 'APP_UPLOADS_PATH=/from/dotenv\n' > "$d/.env"
  local got; got="$(APP_UPLOADS_PATH=/from/env resolve_uploads_path "$d")"
  rm -rf "$d"
  assert_eq "/from/env" "$got" "環境変数が .env より優先される"
}

test_env_with_value
test_env_without_key_falls_back
test_env_var_precedence

# ---------------------------------------------------------------------------
# #83 deploy_images は異常終了経路でも一時ディレクトリを残さない
# ---------------------------------------------------------------------------
echo "[#83] deploy_images の一時ディレクトリ・クリーンアップ"

test_deploy_images_cleanup_on_exit() {
  local app="test-app-container"
  local sandbox; sandbox="$(mktemp -d)"   # この配下に mktemp -d させ、残留を観測する
  local bindir="$sandbox/bin"; mkdir -p "$bindir"

  # docker モック: ps→コンテナ名を返す / exec の find 呼び出し→0（img_count=0 で異常終了させる）/ それ以外→no-op
  cat > "$bindir/docker" <<EOF
#!/bin/bash
case "\$1" in
  ps)   echo "$app" ;;
  exec) for a in "\$@"; do case "\$a" in *find*) echo 0; exit 0;; esac; done; exit 0 ;;
  *)    exit 0 ;;
esac
EOF
  # tar モック: 展開せず成功するだけ
  printf '#!/bin/bash\nexit 0\n' > "$bindir/tar"
  chmod +x "$bindir/docker" "$bindir/tar"

  local tar_in="$sandbox/uploads.tar.gz"; : > "$tar_in"   # 存在チェックを満たす空ファイル
  local deploy_dir="$sandbox/deploy"; mkdir -p "$deploy_dir"   # .env 無し → 既定パス

  # 一時ディレクトリ生成先を sandbox/tmp に固定して残留を観測する
  local tmproot="$sandbox/tmp"; mkdir -p "$tmproot"

  # deploy_images は img_count=0 で exit 1 する。`( )` サブシェルはトラップをリセットし
  # 本番（exit がメインシェルで起きて EXIT トラップ発火）と挙動が変わるため、別 bash プロセスで
  # source して呼び、本番経路を忠実に再現する。
  PATH="$bindir:$PATH" TMPDIR="$tmproot" \
    bash -c 'source "$1"; deploy_images "$2" "$3" "$4"' _ \
      "$SCRIPT_UNDER_TEST" "$tar_in" "$app" "$deploy_dir" >/dev/null 2>&1
  local rc=$?

  local leftover; leftover="$(find "$tmproot" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')"
  rm -rf "$sandbox"

  assert_eq "1" "$rc" "img_count=0 のとき異常終了する（rc=1）"
  assert_eq "0" "$leftover" "異常終了しても一時ディレクトリが残らない"
}

test_deploy_images_cleanup_on_exit

# ---------------------------------------------------------------------------
echo
echo "結果: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
