# 2026-06-26 12:00 移行 import の冪等再実行・展開検証・一時ディレクトリ残留修正

> ブランチ `bugfix/migrate-import-idempotent`（PR #81）。本番初回移行で発生した「全画像 404 障害」の再発防止として、`deploy/scripts/migrate-dev-data.sh` に画像のみ冪等再実行口と展開検証を追加し、あわせて一時ディレクトリ残留バグを修正・ユニットテストを新設した。`2026-06-24_1730_feat-prod-data-migration.md` の続き。

## 背景

初回移行で「DB は投入されたが画像展開（step 2）が抜けて全画像 404」という事象が発生。フル `import` は PK 重複で再実行できずリカバリ手段が無く、さらにリバースプロキシ（NPM）が 404 をキャッシュして延命していた（詳細は障害メモ参照）。再発防止として import 経路を堅牢化する。

## 変更内容

### 1. `import-images` サブコマンド追加（冪等な単体再実行）— commit 24df33b

- 画像展開（step 2）だけを冪等に単体再実行する `import-images <入力ディレクトリ>` を追加。**サブコマンドは export / import / import-images の 3 構成**になった。
- DB 投入済みで画像だけ抜けた場合に、フル `import`（PK 重複で失敗する）を避けて画像展開のみをやり直せる。
- step 2 を共通関数 `deploy_images` / `resolve_uploads_path` に切り出し、`import` と `import-images` で共有。
- **展開後にファイル数を検証**: uploads 直下のファイル数が 0 ならエラー終了、bundle 未満なら警告（取りこぼし検知）。
- 完了出力に NPM アセットキャッシュ（404 延命）のパージ手順を明記。

### 2. 一時ディレクトリ残留の修正（Gemini レビュー #83・妥当）— commit c5921e8

- `deploy_images` / `cmd_export` は `trap 'rm -rf "$tmp"' RETURN` で一時ディレクトリを掃除していたが、**`RETURN` トラップは関数の戻り時のみ発火し、`exit` 経路（展開検証失敗時の `exit 1` 等）では発火せず残留**していた。
- プロセス終了時に確実に削除する**単一 `EXIT` トラップ＋登録レジストリ**（`register_tmpdir`）へ集約。複数関数が個別に `EXIT` トラップを張ると後勝ちで上書き競合するため、配列に蓄積して一括削除する。

### 3. `resolve_uploads_path` の防御的ハードニング（Gemini レビュー #82・実害なし）— commit c5921e8

- 指摘は「`.env` に `APP_UPLOADS_PATH` 行が無いと `grep` の `1` で `set -e` が発火しスクリプトが落ちる」。**実コード検証の結果、実害は無い**と判定:
  - `resolve_uploads_path` の唯一の呼び出しは `app_uploads_path="$(resolve_uploads_path …)"`（コマンド置換）。
  - bash はコマンド置換サブシェルに `set -e` を継承しない（`shopt -s inherit_errexit` 有効時のみ）。本スクリプトは `inherit_errexit` 未設定のため、`grep` マッチ無しでも既定値へ正しくフォールバックする（bash 5.2 実機確認）。
- 将来 `inherit_errexit` 有効化／直接呼び出しに変更した場合に備え、`grep … | … || true` を**防御**として付与。Issue は「実害なし」として訂正クローズ（#82）。

### 4. テスト基盤の新設 — commit c5921e8

- エントリポイントに**ソースガード**（`[ "${BASH_SOURCE[0]}" = "${0}" ]`）を追加し、`source` 時は case ディスパッチせず関数だけ読み込めるようにした（テスト可能化、直接実行の挙動は不変）。
- 純 bash のユニットテスト `deploy/scripts/tests/migrate-dev-data.test.sh`（bats 不要）を追加。
  - `resolve_uploads_path`: `.env` の値採用 / 未定義時の既定値フォールバック（落ちないこと）/ 環境変数優先。
  - `deploy_images`: docker・tar をモックし、展開検証失敗（`exit 1`）でも一時ディレクトリが残らないこと。旧 `RETURN` トラップ版で Red、修正版で Green を確認済み（弁別力あり）。

## 検証

| 項目 | 結果 |
| --- | --- |
| `bash -n`（構文チェック） | OK |
| ユニットテスト（`deploy/scripts/tests/migrate-dev-data.test.sh`） | 6/6 PASS |
| 直接実行（no-args usage / dispatch） | 従来通り（ソースガードによる挙動変化なし） |
| 本番での import-images 実行 | 未（次回障害時/移行時に実施） |

## 関連

- PR #81 / Issue #82（訂正クローズ）/ Issue #83
- 前提エントリ: `2026-06-24_1730_feat-prod-data-migration.md`
