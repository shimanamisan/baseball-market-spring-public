# 2026-06-21 17:00 本番デプロイ基盤フェーズ2（本番用マルチステージ Dockerfile + .dockerignore）

> ブランチ `feature/prod-dockerfile` / PR #65（develop へマージ済み）。フェーズ1（PR #64・[2026-06-21_1645](2026-06-21_1645_feat-prod-deploy-foundation-phase1.md)）に続き、実行可能 jar をコンテナ化する本番用 Dockerfile を追加する。

## 背景

本番は ghcr.io のイメージを pull して起動する構成（旧 Laravel と同様）。そのためのイメージビルド定義（本番用 Dockerfile）が未整備だった。dev は `.devcontainer/Dockerfile`（bash 常駐の開発用）のみで、本番実行用ではない。Gradle はコンポジットビルドだが実体は `app/` に在り `app/settings.gradle` で自己完結するため、`app/` 内で `bootJar` すればよい（devcontainer で `cd /app/app && ./gradlew` しているのと同じ）。

## 変更内容

### 1. `Dockerfile`（リポジトリ直下・本番用・マルチステージ）
- **Stage1（build）**: `eclipse-temurin:21-jdk` で `app/` の `bootJar` を生成。wrapper / ビルド定義（`gradlew` / `settings.gradle` / `build.gradle` / `gradle/`）を先にコピーして依存解決を Docker レイヤキャッシュに乗せ、ソース変更時の再ビルドを高速化。テストは CI で別途実行するため `-x test`
- **Stage2（runtime）**: `eclipse-temurin:21-jre` で**非 root**（`spring` ユーザー）実行
  - `useradd --create-home --home-dir /app` で `/app` をユーザー所有で作成
  - compose / Nginx Proxy Manager (NPM) のヘルスチェック用に `curl` を導入
  - `APP_UPLOADS_PATH` 既定先（`/var/lib/baseball-market/uploads`）を所有権付きで用意（本番は外部ボリュームをここにマウント）
  - `COPY --chown=spring:spring` で jar を実行ユーザー所有でコピー
  - プロファイルは compose の `SPRING_PROFILES_ACTIVE`、JVM opts は `JDK_JAVA_OPTIONS` で注入（`ENTRYPOINT` は exec 形式でシグナル伝播を維持）

### 2. `.dockerignore`（新規）
- `build` / `.gradle` / `.git` / `_docs` / `deploy` / `.github` 等を除外しビルドコンテキストを縮小
- 開発専用シード（`app/seed-data`・`app/src/main/resources/db/seed`）と dev アップロードを本番イメージから除外（匿名化 legacy データ・画像を焼き込まない）

## 検証

| 項目 | 結果 |
| --- | --- |
| イメージビルド | `docker build` 成功（bootJar 約 20s） |
| コンテナ起動 | prod プロファイルで `/actuator/health` が 200 `{"status":"UP"}` |
| 非 root 実行 | `spring` ユーザー（非root・`useradd --system` のシステム割当 UID で固定はしない）。`/app`・`/app/app.jar` ともに `spring:spring` 所有 |
| Flyway 適用 | `db/migration` のみを 0 から適用。dev シードはイメージに含まれず実行されない |
| uploads 既定先 | 非 root で書込み可能（起動時 `ImageStorage` の `createDirectories` がエラーなし） |
| plain jar 衝突 | `bootJar` のみ実行のため `build/libs` は jar 1 つ。ワイルドカード COPY は単一解決し成功 |

## 残課題・申し送り

| 項目 | 状況 |
| --- | --- |
| 後続フェーズ3 | `deploy/prod/{docker-compose.yml, env.template}` + `deploy/scripts/deploy.sh`（Flyway 自動適用のため手動 migrate なし）。`env.template` / `PRODUCTION_ENV` に `REMEMBER_ME_KEY` を含める |
| 後続フェーズ4 | `.github/workflows/deploy-production.yml`（手動 `workflow_dispatch` トリガー）|
