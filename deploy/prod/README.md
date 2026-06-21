# 本番デプロイ（baseball-market-spring）

ghcr.io のイメージを self-hosted runner（本番サーバー）で pull して起動する構成。
公開は app のみ（Nginx Proxy Manager 経由・TLS 終端）、db は内部ネットワークに隔離する。

```
deploy/
├── prod/
│   ├── docker-compose.yml   # 本番スタック（app + db、phpMyAdmin は profile tools）
│   ├── env.template         # 環境変数テンプレート（コピーして .env を作る）
│   └── README.md            # このファイル
└── scripts/
    └── deploy.sh            # デプロイスクリプト（pull → up → health 待機）
```

## コンテナ構成

| サービス | 役割 | 公開 |
| --- | --- | --- |
| `app` | Spring Boot（内蔵 Tomcat:8080、非 root 実行） | NPM 経由のみ（`expose: 8080`） |
| `db` | MySQL 8.0 | 内部ネットワークのみ（外部非公開） |
| `phpmyadmin` | DB 管理 GUI（任意・`profile: tools`） | `127.0.0.1:8091`（SSH トンネル前提） |

- スキーマは **Flyway がアプリ起動時に自動適用**する（手動マイグレーション不要）。
- `app` の healthcheck（`/actuator/health`）は DB 健全性＝マイグレーション完了を含むため、
  healthy 到達がデプロイ成否のゲートになる。

## 前提

- Docker / Docker Compose、外部ネットワーク `nginx-proxy-manager-network` が存在すること
  （無ければ `docker network create nginx-proxy-manager-network`）。
- self-hosted runner が本番サーバーに導入済み（GitHub Actions からデプロイする場合）。

## 初回セットアップ

1. デプロイディレクトリと `.env` を用意する。
   ```bash
   mkdir -p ~/deploy/baseball-market-spring
   cp deploy/prod/env.template ~/deploy/baseball-market-spring/.env
   # __CHANGE_ME__ を実値に置き換える（REMEMBER_ME_KEY は例: openssl rand -hex 32）
   nano ~/deploy/baseball-market-spring/.env
   ```
   GitHub Actions からデプロイする場合は、`.env` を base64 化して Secret `PRODUCTION_ENV`
   に登録する（`base64 -w0 .env`）。ワークフローが runner 上で復号・配置する。

2. ghcr.io が private パッケージなら docker login（public なら不要）。
   ```bash
   echo "$PAT" | docker login ghcr.io -u <user> --password-stdin
   ```

3. デプロイ実行（手動）。
   ```bash
   bash deploy/scripts/deploy.sh
   ```

4. NPM で Proxy Host を作成（Forward Hostname/IP: `baseball-market-spring-app` / Port: `8080`、
   SSL は Let's Encrypt）。`APP_URL` はこの公開ドメインと一致させる
   （メール認証リンクの生成元になるため）。

## GitHub Actions（手動デプロイ）

ワークフロー `.github/workflows/deploy-production.yml` を **手動トリガー**で実行する
（Actions タブ → Deploy to Production → Run workflow）。`run_deploy=false` にすると
イメージの build/push のみ（デプロイは行わない）。

```
build-and-push (ubuntu-latest)
  → ghcr.io/<owner>/baseball-market-spring/app を build & push（latest / timestamp / sha）
deploy (self-hosted, run_deploy=true のとき)
  → PRODUCTION_ENV から .env を生成 → deploy/scripts/deploy.sh 実行
```

### 必要な GitHub Secrets

| Secret | 必須 | 用途 |
| --- | --- | --- |
| `PRODUCTION_ENV` | ○ | 本番 `.env` を base64 化した値（`base64 -w0 .env`）。runner 上で復号・配置 |
| `GHCR_TOKEN` | private パッケージ時のみ | deploy.sh の ghcr ログイン用 PAT |
| `GHCR_USERNAME` | private パッケージ時のみ | 同上のユーザー名 |

- イメージの **push** は自動付与の `GITHUB_TOKEN`（`packages: write`）で足り、手動 Secret は不要。
- `PRODUCTION_ENV` 未設定でも、本番サーバーの `~/deploy/baseball-market-spring/.env` が
  既にあればそれを使う（フォールバック）。

## 運用

```bash
cd ~/deploy/baseball-market-spring
docker compose logs -f app          # ログ
docker compose ps                   # 状態
docker compose restart app          # 再起動
```

### phpMyAdmin（必要時のみ）

外部公開しないため、手元から SSH トンネルでアクセスする。

```bash
# サーバー側で起動
docker compose --profile tools up -d phpmyadmin
# 手元から
ssh -L 8091:localhost:8091 <server>   # → ブラウザで http://localhost:8091
# 終了したら停止
docker compose --profile tools stop phpmyadmin
```

### バックアップ

```bash
docker compose exec db mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" bb_market > backup_$(date +%Y%m%d).sql
```

## 永続データ

| ボリューム | 内容 |
| --- | --- |
| `baseball-market-spring-db-store` | MySQL データ |
| `baseball-market-spring-uploads` | ユーザーアップロード画像（`APP_UPLOADS_PATH`） |

`docker compose down` ではボリュームは保持される（`down -v` で削除されるので本番では使わない）。
