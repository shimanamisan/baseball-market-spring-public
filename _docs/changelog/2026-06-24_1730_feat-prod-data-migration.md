# 2026-06-24 17:30 開発データ → 本番 一度きり移行（DB + 画像）

> ブランチ `feature/prod-data-migration`。本番デプロイ後に、開発中の dev DB の全データ（商品・ユーザー・掲示板メッセージ・いいね等）と画像実体を、本番環境へ一度だけ移行するためのスクリプトと手順書。

## 背景・方針

本番初回デプロイで Flyway がスキーマ（空テーブル）を作るところまでは動く。そこへ「開発中に作り込んだデータ」と「商品/プロフィール画像」を載せたい、という要件。

決定事項（ユーザー確認済み）:

| 論点 | 決定 |
| --- | --- |
| 移行元 | **現行の dev DB**（開発中に追加した掲示板/メッセージ/いいね等を含む実状態） |
| 認証情報 | dev seed の匿名化（`user{id}@example.com` / 共通ハッシュ）の**まま**デモ運用。実個人情報は本番に持ち込まない |
| 実装方式 | **移行スクリプト + 手順書**（`mysqldump` data-only + 画像コピー）。phpMyAdmin 手動は不採用 |

### なぜ phpMyAdmin 手動ではないか

- 移行対象は外部キーで連結した 9 テーブル。手動 INSERT は投入順・FK 制約でミスしやすい。
- **画像実体は phpMyAdmin では一切移せない**（画像は DB でなくファイルシステム保存）。結局ファイルコピーは別途必要 = phpMyAdmin だけでは完結しない。
- `mysqldump`/restore + ファイルコピーをスクリプト化すれば再現可能で安全。phpMyAdmin は移行後の**目視確認**に使う。

## 設計上の要点（このプロジェクト固有の罠）

1. **DB に画像は無い**。DB はパス文字列のみ（`products.pic1/2/3`, `user_profiles.pic`）、画像実体は `APP_UPLOADS_PATH` 配下のファイル。→ 「DB の行」と「画像ファイル」を**別々に運ぶ**。
2. **画像パスのプレフィックス不整合**。dev では商品画像が `seed-images/<file>` プレフィックス＋ dev 限定ハンドラ（`SeedImageWebConfig`、`@Profile("dev")`）で配信される。**本番にこのハンドラは無く `/uploads/**` しか配信しない**。よって移行時に:
   - DB のパス値 `seed-images/` → `uploads/` へ**正規化**（スクリプトが `sed` で実施）
   - 画像実体を本番 uploads volume の**直下にフラット配置**（`WebConfig` は `/uploads/**` を `file:${app.uploads.path}/` からフラット解決する。サブディレクトリにしない）
3. **ユーザーの認証情報は匿名化済み**。本番にはデモアカウントとして載る。共通パスワードでログイン可。

現状の dev データでは、商品画像 22 ファイル（`app/seed-data/images/`、DB 値は全て `seed-images/` プレフィックス）が対象。runtime uploads（`app/src/main/resources/static/uploads/`）は空（`.gitkeep` のみ）。

## 追加物

### `deploy/scripts/migrate-dev-data.sh`

2 サブコマンド構成。

- **`export`**（dev 機で実行）: dev DB を data-only でダンプし、`seed-images/`→`uploads/` 正規化＋ FK チェック無効ラッパーを付けて `bb_market_data.sql` を出力。画像を `uploads.tar.gz`（volume 直下用にフラット集約）として出力。既定の出力先は `./migration-out/`。
- **`import`**（本番サーバーで実行）: ダンプを本番 DB コンテナへ投入し、画像を uploads volume 直下へ展開（`chmod -R a+rX` で配信に必要な読み取り権限を付与）。本番 root パスワードはデプロイ済み `.env` の `MYSQL_ROOT_PASSWORD` から取得。`--fresh` で対象テーブルを TRUNCATE してから入れ直し可能。

投入テーブルは FK 依存順（`categories, makers, users, user_profiles, products, email_verification_tokens, boards, messages, likes`）。import は `FOREIGN_KEY_CHECKS=0` で囲むため順序非依存だが可読性のため依存順で列挙。

### `.gitignore`

`migration-out/` / `migration-in/` を追加（匿名化済みでも DB ダンプ・画像 bundle はコミットしない）。

## 実行手順

```bash
# 1) dev 機（このリポジトリ + dev DB がある環境）で出力を生成
bash deploy/scripts/migrate-dev-data.sh export
#    → ./migration-out/bb_market_data.sql と uploads.tar.gz

# 2) 本番サーバーへ転送
scp migration-out/bb_market_data.sql migration-out/uploads.tar.gz <prod>:~/migration-in/

# 3) 本番サーバーで投入（初回 = 空 DB へ載せるだけ）
bash deploy/scripts/migrate-dev-data.sh import ~/migration-in
#    既存の本番データを消して入れ直す場合のみ:
bash deploy/scripts/migrate-dev-data.sh import ~/migration-in --fresh

# 4) ブラウザで商品一覧・詳細・掲示板を開き、画像とメッセージ表示を確認
#    必要なら phpMyAdmin: docker compose --profile tools up -d phpmyadmin（127.0.0.1:8091 を SSH トンネル）
```

### 環境変数による上書き（既定値で動かない場合）

| 変数 | 既定 | 用途 |
| --- | --- | --- |
| `DEV_DB_CONTAINER` | `baseball-market-spring-db` | export: dev DB コンテナ名 |
| `DEV_DB_ROOT_PASSWORD` | `rootpassword` | export: dev DB root パスワード |
| `PROD_DB_CONTAINER` | `baseball-market-spring-db` | import: 本番 DB コンテナ名 |
| `UPLOADS_VOLUME` | `baseball-market-spring-uploads` | import: 画像 volume 名 |
| `DEPLOY_DIR` | `$HOME/deploy/baseball-market-spring` | import: `.env` 取得元 |

## 検証

| 項目 | 結果 |
| --- | --- |
| `bash -n`（構文チェック） | OK |
| export/import のドライ実行（実 DB） | 未（dev/本番の実環境で要実施） |
| 移行後の画像・掲示板表示確認 | 未（本番投入時に実施） |

## 注意・既知の論点

- **一度きりの初期移行用**。`db/seed/R__*.sql` と異なり本番プロファイルで自動実行されることはない（手動実行のみ）。再デプロイで勝手に再投入・上書きされない。
- 以後ユーザーが本番で新規登録・出品・掲示板投稿したデータと、この移行データは混在する。再実行する場合は `--fresh` の挙動（全消し）に注意。
- 旧 PHP の password ハッシュ（`$2y$`）ではなく、dev seed の匿名化共通ハッシュ（BCrypt、Spring Security 検証可）が載る。実ユーザーの実パスワードは移行されない（方針どおり）。
