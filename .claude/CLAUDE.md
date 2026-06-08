# baseball-market-spring — Claude ルール索引

旧 `baseball-market`（PHP / DDD レイヤード）を **Spring Boot へフルリプレース**するプロジェクト。
プロジェクトの現状・着手前チェックはリポジトリ直下の [../CLAUDE.md](../CLAUDE.md) を参照。本ファイルは技術方針・条件付きルール・エージェント運用の索引。

## 技術スタック
- Java 21 / Spring Boot 3.4.x / Gradle（`./gradlew`）
- Thymeleaf / Spring Data JPA / Spring Security / Spring Mail / Validation / Flyway
- MySQL 8（`bb_market`）、開発メールは MailHog
- パッケージルート: `com.shimanamisan.baseballmarket`

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
- 詳細は [architecture.md](architecture.md) / [coding-style.md](coding-style.md) / [replacement-policy.md](replacement-policy.md)

## 基本原則
- コンストラクタ DI を使用（`new` での直接生成を避ける）
- Controller は薄く、ロジックは application/domain へ
- `@Transactional` は application 層のみ
- domain 層に Spring アノテーションを持ち込まない（純 POJO/record）
- N+1 に注意（`@EntityGraph` / `join fetch`）

## 命名規則
- パッケージ: 全小文字 / クラス・record: UpperCamelCase / メソッド・変数: lowerCamelCase / 定数: UPPER_SNAKE_CASE
- DTO: `XxxRequest` / `XxxResponse`（presentation）
- テーブル: 複数形 snake_case / 外部キー: `{単数形}_id` / 日時: `{動詞}_at`

## TDD（t-wada流）
- 中核思想: **テストは設計行為 / テストは仕様書 / 小さく回す**
- サイクル: 🔴 Red（失敗するテストを先に書く）→ 🟢 Green（最小限の実装）→ 🔵 Refactor（振る舞いを変えず構造改善）
- テストは AAA（Arrange-Act-Assert）構造・1テスト1振る舞い・**実装詳細でなく振る舞い**を検証。境界値と異常系を必ずカバー
- 詳細は **`tdd` スキル**（`.claude/skills/tdd/`）が自動適用される（哲学・サイクル・アンチパターン＋ PATTERNS/EXAMPLES）

## コマンド
```bash
./gradlew test                                 # テスト実行
./gradlew test --tests "*XxxServiceTest"       # 特定テスト
./gradlew bootRun                              # ローカル起動
./gradlew build                                # ビルド（テスト含む）
```
※ マルチプロジェクト構成のため必要に応じ `:app:` プレフィックス（例 `./gradlew :app:test`）。
スキーマ変更は Flyway マイグレーション（`app/src/main/resources/db/migration/V*__*.sql`）で行い、`ddl-auto=update` に頼らない。

## 禁止事項
- Controller にビジネスロジックを書かない
- domain 層への Spring アノテーション持ち込み
- `System.out.println` での恒久ログ
- パスワード・トークンのログ出力／コミット
- `main` / `develop` への直接コミット（[rules/git-flow.md](rules/git-flow.md)）
- Lombok の無断導入

## 条件付きルール
`.claude/rules/` に詳細ルールを配置。対象ファイル操作時のみ `paths:` フロントマターで自動読み込み:
- `app/src/main/java/.../<context>/**` → spring_ddd.md
- テスト作成・レビュー → **`tdd` スキル**（`.claude/skills/tdd/`、自動適用）
- `app/src/main/resources/db/migration/**`, `**/domain/**` → db-blueprint.md
- `**/shared/infrastructure/security/**`, `user/**`, `application*.yml` → auth.md
- `app/src/main/resources/templates/**`, `static/**` → uiux.md
- `build.gradle`, `.devcontainer/**`, `application*.yml` → techstack.md
- ブランチ運用 → git-flow.md

## エージェント運用（ルーティング指針）

ユーザーのリクエスト内容に応じて、Agent ツールで以下の subagent を起動する。**該当条件を満たす場合は積極的に（プロアクティブに）起動すること**。判断に迷う場合のみユーザーに確認する。

### 起動マトリクス

| ユーザー要求の種類 | 起動するエージェント | 補足 |
|----------------|-----------------|----|
| 新機能の追加・実装依頼（「〜機能を追加して」「〜を作って」等） | `feature-implementation-planner` | プラン策定 → TDD実装 → 品質保証 → レビュー依頼まで一貫して担当 |
| 実装完了後のコードレビュー依頼 | `ddd-code-reviewer` | 直近の git diff を起点にレビュー。`feature-implementation-planner` から自動連携される |
| 単純な質問・調査・ファイル閲覧 | 起動しない（メイン Claude が直接対応） | エージェント起動はコストが高いため、軽量タスクには使わない |
| コードベース全体の横断的な探索（3クエリ以上） | `Explore` | 読み取り専用の高速探索 |
| 複雑な実装プランの設計のみ | `Plan` | 実装はせずプランだけ欲しい場合 |

### 連携フロー

```
ユーザー要求
    ↓
メイン Claude（統括役・ルーティング判断）
    ↓
[新機能] feature-implementation-planner
    ↓ (実装完了後、自動でレビュー依頼)
ddd-code-reviewer
    ↓ (レビュー結果)
feature-implementation-planner（Critical 指摘を修正 → 再レビュー or 完了報告）
    ↓
ユーザーへ最終報告
```

### 起動時の原則

- **責務が明確に一致する場合のみ起動**: 「コードを少し読みたい」程度ではエージェントを使わずメイン Claude が直接対応する
- **連鎖は subagent 自身に任せる**: `feature-implementation-planner` は完了時に自分で `ddd-code-reviewer` を呼ぶ。メイン Claude が両方を順に呼ぶ必要はない
- **重複呼び出しを避ける**: 同じセッション内で同種のエージェントを複数回呼ぶ場合は、可能な限り 1 回にまとめる
