# baseball-market-spring

野球用品を売り買いするフリマサイトです。出品・検索・購入から、取引相手とのメッセージのやり取りまで、フリマアプリで一通り体験する流れを実装しています。

もともと PHP（DDD レイヤード構成）で作っていたアプリを、Spring Boot で一から作り直したものです。同じドメインを別のスタックで設計し直すことで、レイヤー分割や依存方向の引き方をあらためて整理する題材として取り組みました。

![トップページ](_docs/screenshot-top.png)

## 主な機能

- **ユーザー** — 会員登録、メール認証、ログイン、プロフィール編集、パスワード変更／再発行、退会
- **商品** — 出品、一覧、詳細、購入、売買履歴
- **メッセージ** — 商品ごとの掲示板でのやり取りと、そこから繋がる購入フロー
- **いいね** — Ajax でのお気に入り登録
- **マイページ** — 出品中の商品・お気に入り・参加中の掲示板をまとめて確認

## 技術スタック

| 分類 | 使用技術 |
|------|----------|
| 言語 / FW | Java 21 / Spring Boot 3.4 |
| ビルド | Gradle |
| テンプレート | Thymeleaf |
| 永続化 | Spring Data JPA / MySQL 8 |
| マイグレーション | Flyway |
| 認証 | Spring Security |
| メール | Spring Mail（開発環境は MailHog で受信確認） |
| 開発環境 | Dev Container（VS Code + Docker） |

## 設計方針

ドメインを 6 つの境界づけられたコンテキスト（`user` / `product` / `message` / `like` / `mypage` / `shared`）に分け、それぞれを 4 層で構成しています。

```
<context>/
├── domain          エンティティ・値オブジェクト・リポジトリ IF（Spring 非依存）
├── application     ユースケース（@Service / @Transactional の境界）
├── infrastructure  リポジトリ実装・外部 IO
└── presentation    Controller・DTO
```

依存方向は `presentation → application → domain ← infrastructure` の一方向に固定し、ドメイン層にはフレームワーク由来のアノテーションを持ち込まない方針です。トランザクション境界は application 層に限定し、Controller は薄く保っています。

実装は t-wada 流の TDD（Red → Green → Refactor）で進め、各ユースケースは振る舞い単位のテストで仕様を表現しています。

## リポジトリ構成（private / public ミラー）

本プロジェクトは本番デプロイを行う **private リポジトリを正（source of truth）** とし、`main` への push をトリガーに **公開ミラー（`baseball-market-spring-public`）** へ自動同期しています。

本番は自宅サーバ上で Docker コンテナとして稼働しており、その更新を CI/CD（GitHub Actions）で自動化するために、自宅サーバを **self-hosted runner** として登録する構成を採っています。このデプロイ経路は自宅環境に直結するため、最小権限と運用情報の非公開の観点から、公開ミラーには以下を **含めていません**。

- 本番デプロイ用ワークフロー（self-hosted runner 上で実行する private 専用構成）
- ミラー同期用ワークフロー自身、および個人用ローカル設定

そのため公開ミラー側では GitHub Actions（CI/CD）は実行されません。アプリ本体のソースコードと設計はそのまま閲覧できます。

## 前提: edge-proxy-stack（エッジゲートウェイ）

このプロジェクトは、リバースプロキシ基盤として [shimanamisan/edge-proxy-stack](https://github.com/shimanamisan/edge-proxy-stack) を併用することを前提にしています。

edge-proxy-stack は **Nginx Proxy Manager（NPM）+ Portainer** を Docker Compose でまとめた家庭用サーバー向けのエッジゲートウェイで、次の役割を担います。

- ポート 80 / 443 で外部トラフィックを受け、SSL を終端してバックエンドへ振り分ける
- Let's Encrypt の SSL 証明書を Web UI から発行・更新する
- Portainer による Docker コンテナの監視・管理

両スタックは **`nginx-proxy-manager-network` という外部 Docker ネットワークを共有** することで連携します。本プロジェクトの `.devcontainer/docker-compose.yml` は、このネットワークを `external: true` として参照し、`app` / `db` / `mailhog` の各コンテナを参加させています。そのため、**本プロジェクトを起動する前に edge-proxy-stack 側でネットワークを作成しておく必要があります。**

### edge-proxy-stack のセットアップ

```bash
# 1. リポジトリを取得
git clone https://github.com/shimanamisan/edge-proxy-stack.git
cd edge-proxy-stack

# 2. 環境変数を用意（必要に応じてポート等を編集）
cp env.example .env

# 3. 共有ネットワークを作成
chmod +x create-network.sh
./create-network.sh
# 手動の場合: docker network create nginx-proxy-manager-network

# 4. NPM / Portainer を起動
docker compose up -d
```

起動後、`http://<サーバー IP>:81` から NPM 管理画面にアクセスし、初期認証情報（`admin@example.com` / `changeme`）でログインのうえ、**速やかに資格情報を変更** してください。

### このアプリへのルーティング

NPM の管理画面で Proxy Host を追加し、本アプリのコンテナへ転送します。

1. 「Proxy Hosts」→「Add Proxy Host」
2. 公開ドメインを設定
3. Forward Hostname / IP に `baseball-market-spring-app`、Forward Port に `8080` を指定（同一の `nginx-proxy-manager-network` 上でコンテナ名解決される）
4. 「SSL」タブで「Request a new SSL Certificate」を有効化して Let's Encrypt 証明書を発行

> ⚠️ edge-proxy-stack が未起動、または `nginx-proxy-manager-network` が未作成の状態で本プロジェクトの devcontainer / `docker compose` を起動すると、外部ネットワーク参照エラーで起動に失敗します。先に edge-proxy-stack を立ち上げてください。

## 動かし方

Dev Container を前提にしています。

1. 上記「前提: edge-proxy-stack」の手順で `nginx-proxy-manager-network` を作成しておく
2. VS Code に Dev Containers 拡張機能を入れる
3. このリポジトリをクローンして VS Code で開く
4. コマンドパレットから「Reopen in Container」を実行
5. コンテナ起動後、Java 開発環境と MySQL が利用可能になる

```bash
./gradlew :app:bootRun   # アプリ起動
./gradlew :app:test      # テスト実行
```

DB スキーマは Flyway（`app/src/main/resources/db/migration/`）で管理しています。
