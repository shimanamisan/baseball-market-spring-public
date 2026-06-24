#!/bin/bash
###############################################################################
# baseball-market-spring 開発データ → 本番 一度きり移行スクリプト
###############################################################################
# 目的:
#   開発中の dev DB の全データ（users / products / boards / messages / likes 等）と、
#   商品・プロフィール画像の実体を、本番環境へ一度だけ移行する。
#
# 設計（重要）:
#   - DB に画像は入っていない。DB はパス文字列のみ、画像実体はファイルシステム
#     （APP_UPLOADS_PATH 配下）にある。よって「DB の行」と「画像ファイル」を別々に運ぶ。
#   - dev では商品画像が `seed-images/<file>` プレフィックス＋ dev 限定ハンドラで配信される。
#     本番にこのハンドラは無く `/uploads/**` しか配信しない。そのため移行時に
#       (a) DB のパス値 `seed-images/` → `uploads/` へ正規化
#       (b) 画像実体を本番 uploads volume の直下（フラット）へ配置
#     の両方を行う。WebConfig は `/uploads/**` を `file:${app.uploads.path}/` から
#     フラット解決するため、ファイルは volume 直下に置く（サブディレクトリにしない）。
#   - ユーザーの email/password は dev seed で匿名化済み（user{id}@example.com / 共通ハッシュ）。
#     本番にはデモアカウントとして載る（実個人情報は持ち込まない方針）。
#
# サブコマンド:
#   export   … dev 機（このリポジトリと dev DB がある環境）で実行。
#              data-only SQL ダンプと画像 bundle を出力する。
#   import   … 本番サーバー（self-hosted runner と本番 DB コンテナがある環境）で実行。
#              出力物を本番 DB へ投入し、画像を uploads volume へ展開する。
#
# 使い方:
#   # 1) dev 機で
#   bash deploy/scripts/migrate-dev-data.sh export
#   # → ./migration-out/ に bb_market_data.sql と uploads.tar.gz が出力される
#
#   # 2) 本番サーバーへ転送
#   scp migration-out/bb_market_data.sql migration-out/uploads.tar.gz <prod>:~/migration-in/
#
#   # 3) 本番サーバーで
#   bash deploy/scripts/migrate-dev-data.sh import ~/migration-in
#   #   既存の本番データを消してから入れ直す場合のみ:
#   bash deploy/scripts/migrate-dev-data.sh import ~/migration-in --fresh
###############################################################################
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

DB_NAME="${DB_NAME:-bb_market}"

# FK 依存順（親 → 子）。import では FOREIGN_KEY_CHECKS=0 で囲むため順序に依存しないが、
# 可読性のため依存順で列挙する。email_verification_tokens は dev にデータが無いことが多いが含める。
TABLES=(
  categories
  makers
  users
  user_profiles
  products
  email_verification_tokens
  boards
  messages
  likes
)

# ============================================================================
# export（dev 機で実行）
# ============================================================================
cmd_export() {
  local out_dir="${1:-$REPO_ROOT/migration-out}"
  local dev_container="${DEV_DB_CONTAINER:-baseball-market-spring-db}"
  local dev_root_pw="${DEV_DB_ROOT_PASSWORD:-rootpassword}"
  local seed_images_dir="${SEED_IMAGES_DIR:-$REPO_ROOT/app/seed-data/images}"
  local runtime_uploads_dir="${RUNTIME_UPLOADS_DIR:-$REPO_ROOT/app/src/main/resources/static/uploads}"

  log_info "export 開始（dev コンテナ: $dev_container, DB: $DB_NAME）"
  mkdir -p "$out_dir"

  if ! docker ps --format '{{.Names}}' | grep -qx "$dev_container"; then
    log_error "dev DB コンテナ '$dev_container' が起動していない。devcontainer を起動してから再実行する。"
    exit 1
  fi

  # --- 1. DB を data-only でダンプ。seed-images/ → uploads/ へパス正規化 ---
  local sql_out="$out_dir/bb_market_data.sql"
  log_info "DB を data-only でダンプ → $sql_out"
  {
    echo "-- baseball-market-spring 開発データ移行用ダンプ（data-only / パス正規化済み）"
    echo "SET FOREIGN_KEY_CHECKS=0;"
    echo "SET UNIQUE_CHECKS=0;"
    docker exec "$dev_container" mysqldump \
      -u root -p"$dev_root_pw" \
      --no-create-info --complete-insert --single-transaction \
      --skip-triggers --no-tablespaces --skip-comments \
      "$DB_NAME" "${TABLES[@]}" \
      | sed 's#seed-images/#uploads/#g'
    echo "SET UNIQUE_CHECKS=1;"
    echo "SET FOREIGN_KEY_CHECKS=1;"
  } > "$sql_out"
  log_success "ダンプ完了（$(wc -l < "$sql_out") 行）"

  # --- 2. 画像実体を bundle。seed fixtures と runtime uploads を volume 直下用にフラット集約 ---
  local stage; stage="$(mktemp -d)"
  trap 'rm -rf "$stage"' RETURN
  local count=0
  if [ -d "$seed_images_dir" ]; then
    # .gitkeep 等の隠しメタは除外し、画像のみコピー
    find "$seed_images_dir" -maxdepth 1 -type f ! -name '.*' -exec cp -f {} "$stage/" \;
  fi
  if [ -d "$runtime_uploads_dir" ]; then
    # runtime uploads が seed と同名の場合はランタイム実体を優先（上書き）
    find "$runtime_uploads_dir" -maxdepth 1 -type f ! -name '.gitkeep' ! -name '.*' -exec cp -f {} "$stage/" \;
  fi
  count="$(find "$stage" -type f | wc -l)"
  local tar_out="$out_dir/uploads.tar.gz"
  tar czf "$tar_out" -C "$stage" .
  log_success "画像 bundle 完了（$count ファイル）→ $tar_out"

  echo
  log_info "次のステップ:"
  echo "  1) 本番サーバーへ転送:"
  echo "       scp '$sql_out' '$tar_out' <prod>:~/migration-in/"
  echo "  2) 本番サーバーで:"
  echo "       bash deploy/scripts/migrate-dev-data.sh import ~/migration-in"
}

# ============================================================================
# import（本番サーバーで実行）
# ============================================================================
cmd_import() {
  local in_dir="${1:?使い方: import <入力ディレクトリ> [--fresh]}"
  local fresh="${2:-}"
  local prod_container="${PROD_DB_CONTAINER:-baseball-market-spring-db}"
  local deploy_dir="${DEPLOY_DIR:-$HOME/deploy/baseball-market-spring}"
  local uploads_volume="${UPLOADS_VOLUME:-baseball-market-spring-uploads}"

  local sql_in="$in_dir/bb_market_data.sql"
  local tar_in="$in_dir/uploads.tar.gz"

  log_info "import 開始（本番 DB コンテナ: $prod_container, volume: $uploads_volume）"

  [ -f "$sql_in" ] || { log_error "$sql_in が無い。export 出力を転送したか確認する。"; exit 1; }
  [ -f "$tar_in" ] || { log_error "$tar_in が無い。export 出力を転送したか確認する。"; exit 1; }

  # 本番 DB の root パスワードは デプロイ済み .env（PRODUCTION_ENV 由来）から取得する。
  if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    if [ -f "$deploy_dir/.env" ]; then
      # .env の MYSQL_ROOT_PASSWORD のみ取り出す（他の変数で環境を汚さない）。
      MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$deploy_dir/.env" | head -1 | cut -d= -f2-)"
    fi
  fi
  if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    log_error "MYSQL_ROOT_PASSWORD を取得できない。環境変数で渡すか $deploy_dir/.env を確認する。"
    exit 1
  fi

  if ! docker ps --format '{{.Names}}' | grep -qx "$prod_container"; then
    log_error "本番 DB コンテナ '$prod_container' が起動していない。先に deploy.sh で起動する。"
    exit 1
  fi

  # --- 0.（任意）--fresh: 既存の本番データを TRUNCATE してから入れ直す ---
  if [ "$fresh" = "--fresh" ]; then
    log_warning "--fresh 指定: 本番の対象テーブルを TRUNCATE してから投入する（既存データは消える）"
    log_warning "5 秒後に実行する。中止するなら今 Ctrl-C。"
    sleep 5
    {
      echo "SET FOREIGN_KEY_CHECKS=0;"
      for t in "${TABLES[@]}"; do echo "TRUNCATE TABLE \`$t\`;"; done
      echo "SET FOREIGN_KEY_CHECKS=1;"
    } | docker exec -i "$prod_container" sh -c "exec mysql -u root -p\"$MYSQL_ROOT_PASSWORD\" $DB_NAME"
    log_success "TRUNCATE 完了"
  fi

  # --- 1. DB へ投入 ---
  log_info "本番 DB へデータ投入 ..."
  docker exec -i "$prod_container" sh -c "exec mysql -u root -p\"$MYSQL_ROOT_PASSWORD\" $DB_NAME" < "$sql_in"
  log_success "DB 投入完了"

  # --- 2. 画像を uploads volume 直下へ展開 ---
  # app コンテナは非 root 実行のため、配信に必要な読み取り権限を a+rX で付与する。
  log_info "画像を volume '$uploads_volume' 直下へ展開 ..."
  docker run --rm \
    -v "$uploads_volume":/uploads \
    -v "$in_dir":/src:ro \
    alpine sh -c 'cd /uploads && tar xzf /src/uploads.tar.gz && chmod -R a+rX .'
  local img_count
  img_count="$(docker run --rm -v "$uploads_volume":/uploads alpine sh -c 'find /uploads -maxdepth 1 -type f | wc -l')"
  log_success "画像展開完了（volume 直下のファイル数: $img_count）"

  echo
  log_success "移行完了。ブラウザで商品一覧・詳細・掲示板を開き、画像と掲示板メッセージが表示されることを確認する。"
  log_info "確認用に phpMyAdmin を使う場合: docker compose --profile tools up -d phpmyadmin （127.0.0.1:8091 を SSH トンネル）"
}

# ============================================================================
# エントリポイント
# ============================================================================
case "${1:-}" in
  export) shift; cmd_export "$@" ;;
  import) shift; cmd_import "$@" ;;
  *)
    echo "使い方:"
    echo "  $0 export [出力ディレクトリ]              # dev 機で実行（既定: ./migration-out）"
    echo "  $0 import <入力ディレクトリ> [--fresh]      # 本番サーバーで実行"
    exit 1
    ;;
esac
