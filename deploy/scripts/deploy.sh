#!/bin/bash
###############################################################################
# baseball-market-spring 本番デプロイスクリプト（self-hosted runner で実行）
###############################################################################
# 処理内容:
#   1. デプロイディレクトリ準備と docker-compose.yml の配置
#   2. GitHub Container Registry へのログイン（private パッケージ時）
#   3. 最新イメージの pull
#   4. 既存コンテナの停止・再起動（DB / uploads ボリュームは保持）
#   5. app コンテナの healthy 待機
#   6. 古いイメージのクリーンアップ
#
# 注意: Spring Boot + Flyway 構成のため、Laravel と異なり手動マイグレーション/シードは
#       不要。Flyway がアプリ起動時に db/migration を自動適用する。app の healthcheck は
#       DB 健全性（= マイグレーション完了）を含むため、healthy 到達がデプロイ成否ゲートになる。
###############################################################################
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

APP_CONTAINER="baseball-market-spring-app"

# ============================================================================
# 1. デプロイディレクトリの準備
# ============================================================================
DEPLOY_DIR="/home/$(whoami)/deploy/baseball-market-spring"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIR="$SCRIPT_DIR/../prod"

log_info "Deployment started at $(date '+%Y-%m-%d %H:%M:%S')"
mkdir -p "$DEPLOY_DIR"

log_info "Copying docker-compose.yml to $DEPLOY_DIR"
cp -f "$PROD_DIR/docker-compose.yml" "$DEPLOY_DIR/"

# .env は本番サーバー側で用意するか、ワークフローが PRODUCTION_ENV から生成して配置する。
if [ ! -f "$DEPLOY_DIR/.env" ]; then
  log_error ".env file not found in $DEPLOY_DIR"
  log_error "Create it from deploy/prod/env.template (or supply PRODUCTION_ENV in the workflow)."
  exit 1
fi

cd "$DEPLOY_DIR"

# ============================================================================
# 2. GitHub Container Registry へのログイン（private パッケージのときのみ必要）
# ============================================================================
if [ -n "${GHCR_TOKEN:-}" ]; then
  log_info "Logging in to ghcr.io ..."
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USERNAME:-}" --password-stdin
  log_success "Logged in to ghcr.io"
else
  log_warning "GHCR_TOKEN not set. Assuming public package or cached credentials."
fi

# ============================================================================
# 3-4. イメージ取得 → 再起動（ボリュームは保持）
# ============================================================================
log_info "Pulling latest images ..."
docker compose pull

log_info "Restarting containers ..."
docker compose down --remove-orphans
docker compose up -d

# ============================================================================
# 5. app の healthy 待機（Flyway 適用完了 + DB 健全性を含む）
# ============================================================================
log_info "Waiting for ${APP_CONTAINER} to become healthy ..."
MAX_WAIT=40   # 40 * 5s = 最大 200 秒
n=0
while true; do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$APP_CONTAINER" 2>/dev/null || echo "starting")"
  if [ "$status" = "healthy" ]; then
    log_success "${APP_CONTAINER} is healthy"
    break
  fi
  n=$((n + 1))
  if [ "$n" -ge "$MAX_WAIT" ]; then
    log_error "${APP_CONTAINER} did not become healthy in time (status=$status)"
    docker compose ps
    docker compose logs --tail=80 app
    exit 1
  fi
  log_info "  ... status=$status ($n/$MAX_WAIT)"
  sleep 5
done

# ============================================================================
# 6. クリーンアップ
# ============================================================================
log_info "Pruning old images ..."
docker image prune -f

docker compose ps
log_success "Deployment completed at $(date '+%Y-%m-%d %H:%M:%S')"
