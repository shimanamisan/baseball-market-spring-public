# baseball-market-spring レビューガイドライン

旧 `baseball-market`（PHP / DDD レイヤード）を Spring Boot へフルリプレースするプロジェクト。
Bounded Context × 4 レイヤー構成の DDD と t-wada 流 TDD を採用している。

## 言語
- Summary of Changes を日本語でコメントしてください
- レビューのコメントも日本語でコメントしてください

## 技術スタック
- **言語/ランタイム**: Java 21 / Spring Boot 3.4.x / Gradle（`./gradlew`、マルチプロジェクト構成）
- **Web/テンプレート**: Spring MVC / Thymeleaf（`thymeleaf-extras-springsecurity6`）
- **永続化**: Spring Data JPA / MySQL 8（`bb_market`）/ スキーマ管理は Flyway
- **認証/認可**: Spring Security
- **メール**: Spring Mail（開発時は MailHog）
- **バリデーション**: Bean Validation（`spring-boot-starter-validation`）
- **テスト**: JUnit 5 / AssertJ / Mockito / Spring Security Test
- **パッケージルート**: `com.shimanamisan.baseballmarket`

## アーキテクチャ（Bounded Context × 4 レイヤー）
```
com.shimanamisan.baseballmarket.<context>/
├── domain          // エンティティ・Value Object(record)・Repository IF・ドメイン例外（Spring 非依存）
├── application     // ユースケース／サービス（@Service, @Transactional 境界）
├── infrastructure  // Repository 実装・外部 IO
└── presentation    // Controller・Request/Response DTO
```
- 依存方向: `presentation → application → domain ← infrastructure`
- context: `user` / `product` / `message` / `like` / `mypage` / `shared`

## コーディング規約
- **コンストラクタ DI** を使用（フィールドインジェクション・`new` での直接生成を避ける）
- Controller は薄く保ち、ビジネスロジックは application / domain 層へ
- `@Transactional` は **application 層のみ** に置く
- **domain 層に Spring アノテーションを持ち込まない**（純 POJO / record で表現）
- Value Object は `record` で表現する
- DTO 命名: presentation 層で `XxxRequest` / `XxxResponse`
- 命名規則:
  - パッケージ: 全小文字 / クラス・record: UpperCamelCase / メソッド・変数: lowerCamelCase / 定数: UPPER_SNAKE_CASE
  - テーブル: 複数形 snake_case / 外部キー: `{単数形}_id` / 日時カラム: `{動詞}_at`
- スキーマ変更は Flyway マイグレーション（`app/src/main/resources/db/migration/V*__*.sql`）で行い、`ddl-auto=update` に頼らない
- ログ: `System.out.println` での恒久ログ禁止。ロガーを使用する
- **Lombok の無断導入禁止**

## テスト方針（t-wada 流 TDD）
- サイクル: 🔴 Red（失敗するテストを先に書く）→ 🟢 Green（最小限の実装）→ 🔵 Refactor（振る舞いを変えず構造改善）
- AAA（Arrange-Act-Assert）構造・1 テスト 1 振る舞い
- **実装詳細ではなく振る舞い** を検証する
- 境界値・異常系を必ずカバーする

## レビュー重点項目
1. **レイヤー依存方向の遵守**（`presentation → application → domain ← infrastructure`、逆流していないか）
2. **domain 層の純度**（Spring / JPA アノテーションや framework 依存が混入していないか）
3. **トランザクション境界**（`@Transactional` が application 層に置かれ、Controller / domain に漏れていないか）
4. **SQL インジェクション対策**（パラメータ化クエリ・`@Param` バインド、文字列連結による動的クエリの排除）
5. **N+1 問題**（`@EntityGraph` / `join fetch` の適切な利用）
6. **バリデーション**（入力検証が presentation / domain で適切に行われているか、Value Object の不変条件）
7. **認証・認可**（Spring Security 設定の妥当性、認可漏れ、CSRF 設定）
8. **機密情報の取り扱い**（パスワード・トークンをログ出力・コミットしていないか、パスワードのハッシュ化）
9. **テストカバレッジ**（変更に対応するテストがあるか、境界値・異常系を含むか、振る舞いベースか）
10. **命名・DTO 規約**（`XxxRequest` / `XxxResponse`、テーブル / カラム命名の一貫性）
11. **コンストラクタ DI の徹底**（フィールドインジェクションになっていないか）

## ブランチ運用（参考）
- `main` / `develop` への直接コミット禁止。`feature/*` `bugfix/*` `hotfix/*` で作業し PR 経由でマージ
- コミットメッセージは Conventional Commits ライク（`feat:` / `fix:` / `chore:` / `docs:` / `test:` / `refactor:` ...）
