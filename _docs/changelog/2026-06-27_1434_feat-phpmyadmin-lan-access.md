# 2026-06-27 14:34 phpMyAdmin を LAN（192.168.10.0/24）からアクセス可能に

> ブランチ `feature/phpmyadmin-lan-access`。本番・開発の両 compose に同梱済みの phpMyAdmin（DB 管理 GUI）を、SSH トンネル前提から **自宅 LAN（192.168.10.0/24）内の任意の PC からブラウザ直アクセス可能**に変更した。公開範囲は `PMA_BIND_IP` で制御し、指定 IP のインターフェースにのみバインドする方式を採用。本番・開発で同一方式に揃えた。

## 背景

本番 DB の状況を手元のブラウザから確認したいが、従来は phpMyAdmin を `127.0.0.1:8091` にのみバインドしており、アクセスには SSH トンネルが必須だった。自宅サーバは LAN（192.168.10.0/24）内に設置されており、LAN 内からは直接ブラウザで見られるようにしたい（外部からは到達不可のまま）。

## 設計方針

- **「外部 IF にバインドしない」方式で外部到達を構造的に遮断**する。Docker のポート公開はホストの ufw を貫通する既知の罠があるため、ファイアウォール頼みにせず、サーバが LAN セグメント上に持つ IP にのみバインドする。ルータでポート転送しない限り LAN 外からは物理的に届かない。
- **CIDR 制御（`Require ip` 等の Apache レイヤー）は採らない**: Docker の userland-proxy 経由ではリモート IP がゲートウェイ IP に化けて `192.168.10.0/24` 判定が効かないため、誤った安心になる。バインド先 IP の限定で要件（/24 全体から可・外部不可）を満たす。
- **fail-safe デフォルト**: `PMA_BIND_IP` 未設定時は `127.0.0.1` にフォールバックし、設定忘れでも全 IF に晒さない。
- 起動方式は `profile: tools`（必要時のみ起動）を維持。

## 変更内容

### 1. 本番 compose のバインド可変化 — `deploy/prod/docker-compose.yml`

- phpMyAdmin の `ports` を `127.0.0.1:8091:80` → **`${PMA_BIND_IP:-127.0.0.1}:8091:80`** に変更。
- 方針・LAN アクセス手順・fail-safe をコメントに明記。

### 2. 本番 env テンプレート — `deploy/prod/env.template`

- `PMA_BIND_IP` の設定例（既定はコメントアウト）と説明を追加。
- GitHub Actions 経由デプロイ（`PRODUCTION_ENV` Secret）の場合は Secret 元 `.env` にも含めて base64 を更新する旨に留意。

### 3. 開発 compose にも同方式で追加 — `.devcontainer/docker-compose.yml`

- 本番と同設計（`phpmyadmin:5.2-apache` / `profile: tools` / `${PMA_BIND_IP:-127.0.0.1}:8091:80` / `PMA_HOST=baseball-market-spring-db`）で phpMyAdmin サービスを追加。
- dev は `.env`/`env_file` を使わずインライン環境変数のため、`PMA_BIND_IP` の渡し方（シェル変数 or `.devcontainer/.env`）をコメントに明記。
- dev のネットワークは `nginx-proxy-manager-network` 単一のためそこへ接続（本番は `internal` 分離）。
- `profile: tools` のため devcontainer の通常起動では立ち上がらず、起動には影響しない。

### 4. ドキュメント整備

- `README.md`（ルート）に「本番環境の DB 管理（phpMyAdmin）」節を新設し、本番での起動操作手順を記載。
- `deploy/prod/README.md` の phpMyAdmin 節とサービス公開表を LAN アクセス前提に更新。

## 操作（本番）

```bash
cd ~/deploy/baseball-market-spring
echo 'PMA_BIND_IP=192.168.10.5' >> .env   # サーバの LAN IP（実値に置換）
docker compose --profile tools up -d phpmyadmin
# → LAN 内の PC から http://192.168.10.5:8091
#   ログイン: bbuser（DB_USERNAME/DB_PASSWORD）または root（MYSQL_ROOT_PASSWORD）
docker compose --profile tools stop phpmyadmin
```

## 検証

| 項目 | 結果 |
| --- | --- |
| 本番 compose の YAML 構文・ポート定義（`${PMA_BIND_IP:-127.0.0.1}:8091:80`） | OK（PyYAML パース確認） |
| 開発 compose の YAML 構文・ポート定義・profile・network | OK（PyYAML パース確認） |
| 本番サーバ / 開発環境での実起動・LAN ブラウザ到達 | 未（docker 不在の作業環境のため。次回サーバ操作時に実施） |

## 関連

- 本番デプロイ構成の前提エントリ: `2026-06-24_1730_feat-prod-data-migration.md`
