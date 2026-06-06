# コーディング規約

## 言語・バージョン

- Java 21（Devcontainer の toolchain と一致させる）
- Spring Boot 3.4.x 系（`demo/build.gradle` を踏襲）
- ビルドツール: Gradle（Wrapper 同梱、`./gradlew` を使用）

## フォーマット・スタイル

- インデント: スペース 2（旧 PHP 側 `editor.tabSize: 2` を踏襲）
- 行末セミコロン省略不可（Java 標準）
- import の `*` ワイルドカード禁止
- public クラスには JavaDoc を必須としない（自明なものに書かない方針）。**「なぜ」が非自明な箇所のみコメント**

## 命名

- パッケージ: 全小文字（`com.shimanamisan.baseballmarket.user.domain`）
- クラス・record: UpperCamelCase
- メソッド・変数: lowerCamelCase
- 定数: UPPER_SNAKE_CASE
- DTO クラス: `XxxRequest` / `XxxResponse` で統一（context の `presentation` に配置）
- ドメイン例外: `XxxException`（`shared.domain.exception` または各 context の `domain` 配下）

## Lombok

- **使わない**。record / 標準コンストラクタで足りる場合は標準を優先
- 例外的に Lombok を導入したい状況になったら、ユーザーに相談してから入れる

## null 安全

- ドメイン層では Optional<T> をフィールド型として使わない（戻り値型としてのみ許容）
- nullable なフィールドは型に `?` を表現できないため、JavaDoc/命名で明示

## 例外設計

- ドメイン違反: `ValidationException`（`shared.domain.exception`）を投げる
- 業務エラー（例: パスワード不一致、商品が既に売却済み）: 各 context の `domain` 配下の専用例外
- インフラ起因（DB, Mail）: ランタイム例外でラップして上位に伝搬

## トランザクション境界

- `@Transactional` は **`application` 層のサービスメソッドにのみ** 付与
- `presentation` / `domain` / `infrastructure` には付けない
- 読み取り専用は `@Transactional(readOnly = true)`

## ロギング

- SLF4J を使用（`private static final Logger log = LoggerFactory.getLogger(...);`）
- 旧 `Shared\Infrastructure\Logger\Logger.php` の役割は SLF4J + Logback で代替
- 個人情報・パスワード・トークンはログに出さない

## テスト

- JUnit 5 + Spring Boot Test（`build.gradle` の現状構成のまま）
- ドメイン層の単体テストはモック不要で書ける構造を保つ（純 POJO/record だから）
- application 層のテストは Repository をモックして書く
- 結合テスト（DB あり）は `@SpringBootTest` + Testcontainers を推奨候補（採用時はユーザーに相談）

## DB / マイグレーション

- 旧プロジェクトの `database/migrations/*.sql` を起点に Flyway 形式へ移植する想定（最終決定は `replacement-policy.md` 参照）
- スキーマ変更は必ずマイグレーションファイル経由で行い、`spring.jpa.hibernate.ddl-auto=update` に頼らない（現状の `docker-compose.yml` の設定は開発初期の利便性のためのものであり、本番では `validate` または `none`）

## コミット粒度

- 1 コミット = 1 関心事。リプレース対象の「機能 1 つ」または「context 1 つ」程度を目安
- メッセージの prefix は旧リポジトリに合わせて `update:` / `add:` / `fix:` を使用

## 禁止事項

- `System.out.println` での恒久的なログ出力
- ドメイン層への Spring アノテーション持ち込み（`@Component` 等）
- Repository インターフェースに JPA の型を露出させる（`Page<>`, `Pageable` など）→ Application 用の DTO/値で受け渡す
