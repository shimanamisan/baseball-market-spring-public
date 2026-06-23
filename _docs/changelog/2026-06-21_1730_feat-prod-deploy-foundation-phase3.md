# 2026-06-21 17:30 本番デプロイ基盤フェーズ3（本番 compose・env テンプレート・deploy.sh）

> ブランチ `feature/prod-deploy-compose` / PR #68（develop へマージ済み）。フェーズ2（PR #65・[2026-06-21_1700](2026-06-21_1700_feat-prod-deploy-foundation-phase2.md)）に続き、ghcr.io のイメージを self-hosted runner で起動するための compose・環境変数テンプレート・デプロイスクリプトを追加する。

## 背景

本番は ghcr.io のイメージを pull して起動する構成（旧 Laravel と同様）。フェーズ2で本番用 Dockerfile を用意したので、それを動かすための「スタック定義（compose）」「秘匿値の枠（env.template）」「再起動を自動化するスクリプト（deploy.sh）」を整える。Spring Boot + Flyway 構成のため、Laravel の deploy.sh から手動マイグレーション/シードを排除できる。

## 変更内容

### 1. `deploy/prod/docker-compose.yml`（本番スタック）
- **app**（Spring Boot 内蔵 Tomcat:8080）と **db**（MySQL 8.0）の 2 サービス。
- 公開は app のみ（`expose: 8080`、`ports` で直接公開しない）→ NPM 経由で TLS 終端。
- **db は `internal: true` ネットワークに隔離**し共有ネットワーク（`nginx-proxy-manager-network`）に晒さない。app のみ両ネットワークに参加。DB host は旧 baseball-market との alias 衝突回避のためコンテナ名固定。
- app の healthcheck は `/actuator/health`（DB 健全性＝Flyway 完了を含む）。`uploads` / `db-store` を named volume で永続化。
- phpMyAdmin は `profiles: [tools]` + `127.0.0.1:8091` バインド（必要時のみ起動・SSH トンネル前提で外部非公開）。

### 2. `deploy/prod/env.template`
Spring 向け env ひな形。`DB_*` / `MAIL_*` / `APP_URL` / `APP_UPLOADS_PATH` / `REMEMBER_ME_KEY` を含む。秘匿値は `__CHANGE_ME__` プレースホルダ。コミットせず `.env`（または `PRODUCTION_ENV`）で管理。

### 3. `deploy/scripts/deploy.sh`
`pull → up -d --remove-orphans → app healthy 待機 → image prune` の流れ。**Flyway 起動時自動適用のため手動 migrate/seed なし**（Laravel 版から簡素化）。

### 4. `deploy/prod/README.md`
構成・初回セットアップ・運用・phpMyAdmin トンネル手順。

### 5. PR レビュー由来の改善（Gemini）
- deploy.sh の health 待機にコンテナ稼働状態チェックを追加し **fail-fast 化**（起動失敗時に 200 秒待たず即失敗）。
- `down` + `up` を **`up -d --remove-orphans`** に変更（差分のみ再作成・db を再起動せずダウンタイム削減）。
- `DEPLOY_DIR` を `$HOME` ベース + 環境変数で上書き可能に（root 等にも対応）。
- phpMyAdmin から**未使用の `MYSQL_ROOT_PASSWORD` を削除**（公式イメージは `PMA_*` を使う。`docker inspect` 経由の機密露出を回避）。

## 検証

| 項目 | 結果 |
| --- | --- |
| `docker compose config` | interpolation・構造（healthcheck / depends_on / networks / volumes）が解決 |
| 実スタック起動（識別子退避コピー・fresh DB） | `db` healthy → `app` が `depends_on: service_healthy` を待って起動 → `/actuator/health` 200 |
| Flyway 自動適用 | fresh DB に `db/migration` のみを 0 から適用（手動 migrate 不要を実証） |
| uploads 永続化 | named volume が `spring:spring` 所有（非 root が書込可能） |
| deploy.sh fail-fast | 誤 DB パスワードでクラッシュ誘発 → ループが `exited` を 5s（1 ループ）で検出し即失敗 |
| phpMyAdmin env | `PMA_HOST` / `PMA_PORT` のみ（`MYSQL_ROOT_PASSWORD` なし） |

## 残課題・申し送り

| 項目 | 状況 |
| --- | --- |
| 後続フェーズ4 | `.github/workflows/deploy-production.yml`（手動トリガー）で build/push + deploy を自動化 |
| 実環境セットアップ | self-hosted runner 登録・GitHub Secrets（`PRODUCTION_ENV` 等）・NPM Proxy Host・DNS(SPF/DKIM) はユーザー側で対応 |
