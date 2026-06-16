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

## 動かし方

Dev Container を前提にしています。

1. VS Code に Dev Containers 拡張機能を入れる
2. このリポジトリをクローンして VS Code で開く
3. コマンドパレットから「Reopen in Container」を実行
4. コンテナ起動後、Java 開発環境と MySQL が利用可能になる

```bash
./gradlew :app:bootRun   # アプリ起動
./gradlew :app:test      # テスト実行
```

DB スキーマは Flyway（`app/src/main/resources/db/migration/`）で管理しています。
