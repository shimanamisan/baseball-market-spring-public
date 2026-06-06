# 2026-05-11 08:17 フェーズ 2: Flyway baseline と V1__init.sql 作成

## 背景

フェーズ 1 で基盤（Security/Mail 依存、MailHog、Mail/Session/Flyway baseline 設定）を整えたが、`spring.jpa.hibernate.ddl-auto=validate` のため **マイグレーションスクリプトが 1 つも無い状態では JPA Entity を追加した瞬間にスキーマ検証が失敗する**。続くフェーズ 3 以降の Entity 実装に進むため、旧 `bb_market` を引き継ぐ前提で初期マイグレーション `V1__init.sql` を作成する必要があった。

設計制約:

- 旧 `database/migrations/split_users_table.sql` と `add_email_verification.sql` を **適用済みの状態を最終形** として 1 ファイルで定義する（プロジェクトルール [replacement-policy.md §4](../../.claude/replacement-policy.md)）。
- 旧データを引き継ぐ運用と、新規構築の運用の **両方の経路で同一の V1** が機能すること。
- 旧 PHP コードで実際に使われている **列名／タイポ／NULL 可否** を正確に踏襲すること（学習用プロジェクトとはいえ、旧 Repository を後で再現する際の認知負荷を下げる）。

## 調査ステップと分析

### 1. 旧スキーマの一次資料が限定的

旧プロジェクトには以下しか無く、テーブル全体の DDL がどこにも明文化されていない:

- `database/migrations/split_users_table.sql` — `users` 分割と `user_profiles` 新設のみ
- `database/migrations/add_email_verification.sql` — `email_verified_at` 追加と `email_verification_tokens` 新設のみ
- `.devcontainer/mysql/init/` ディレクトリは **空**（seed/init SQL なし）

旧 DB の実体は別ボリューム `bb-market-db-store` に存在し、本リポジトリの `baseball-market-spring-db-store` には移行されていない。よって **実 DB との照合は今フェーズではできず、旧 PHP の Repository 実装 SQL を一次資料として逆引きする方針** に切り替えた。

### 2. Repository SQL から列構成を逆引き

| テーブル | 一次資料となったファイル／行 | 抽出方法 |
| --- | --- | --- |
| `users` | `split_users_table.sql` ALTER 直後の状態 + `add_email_verification.sql` ADD COLUMN | 既存 SQL から組み立て |
| `user_profiles` | `split_users_table.sql` CREATE 文 | 既存 SQL をそのまま採用 |
| `email_verification_tokens` | `add_email_verification.sql` CREATE 文 | 既存 SQL をそのまま採用 |
| `categories` | `Product/Infrastructure/CategoryRepositoryImpl.php:35` の `$row['category_name']` | 列名・delete_flg 参照を確認 |
| `makers` | `Product/Infrastructure/MakerRepositoryImpl.php:35` の `$row['maker_name']` | 同上 |
| `products` | `Product/Infrastructure/ProductRepositoryImpl.php:234-239` INSERT | 全カラム名・パラメータ型を抽出 |
| `boards` | `Message/Infrastructure/MessageRepositoryImpl.php:129` INSERT + `User/Infrastructure/UserRepositoryImpl.php:219` UPDATE | `sale_user`/`buy_user`/`product_id` + `delete_flg` を確認 |
| `messages` | `Message/Infrastructure/MessageRepositoryImpl.php:147-149` INSERT | **`bord_id` というタイポを発見**、`from_user`/`to_user`/`msg`/`send_at` 構成を確認 |
| `likes` | `Like/Infrastructure/LikeRepositoryImpl.php:49`, `:82` | `delete_flg` 参照と物理 DELETE 併用の二重削除戦略を確認 |

### 3. プランからの逸脱点 3 つを発見

| 当初プラン | 実コード調査の結果 | 採否 |
| --- | --- | --- |
| `messages.board_id` | 旧コードは **`bord_id`**（タイポ） | **旧コード準拠（タイポ保存）**。旧データを引き継ぐ要件のため、リネームは V2 以降で慎重に行う |
| `messages.user_id` / `content` | 旧コードは **`from_user`/`to_user`/`msg`/`send_at`** の 4 列構成 | 旧コード準拠 |
| `password_reset_tokens` テーブル | 旧 [PasswordRemindController.php:50-52](../../../baseball-market/public/src/User/Presentation/PasswordRemindController.php#L50) は **トークンをセッションに保存** しており DB テーブル無し | **V1 に含めない**。フェーズ 5 のパスワード再発行実装は旧方式（セッション保存）を踏襲。永続化が要件化したら V2 で追加 |

## 変更内容

### 1. `app/src/main/resources/db/migration/V1__init.sql` 新規作成

9 テーブルを `CREATE TABLE IF NOT EXISTS` で定義。

要点:

- すべて `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`（旧 `split_users_table.sql` と統一）。
- すべてのテーブルに `created_at` / `updated_at`（`updated_at` は `ON UPDATE CURRENT_TIMESTAMP`）を付与。
- 外部キーを以下に明示:
  - `user_profiles.user_id → users.id`
  - `email_verification_tokens.user_id → users.id ON DELETE CASCADE`
  - `products.user_id → users.id`、`products.category_id → categories.id`、`products.maker_id → makers.id`
  - `boards.sale_user / buy_user → users.id`、`boards.product_id → products.id`
  - `messages.bord_id → boards.id`、`messages.from_user / to_user → users.id`
  - `likes.user_id → users.id`、`likes.product_id → products.id`
- 索引を JOIN・絞り込み多用カラム（`category_id`, `maker_id`, `user_id`, `delete_flg`, `sale_user`, `buy_user`, `bord_id`, `from_user`, `to_user`）に付与。
- `messages.bord_id` のタイポは **コメントで明示** して保存（旧データ互換）。

### 2. Flyway baseline 動作の検証

#### 経路 A: 新規構築（空 DB）

```text
o.f.c.i.s.JdbcTableSchemaHistory : Schema history table `bb_market`.`flyway_schema_history` does not exist yet
o.f.core.internal.command.DbMigrate : Current version of schema `bb_market`: << Empty Schema >>
o.f.core.internal.command.DbMigrate : Migrating schema `bb_market` to version "1 - init"
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema `bb_market`, now at version v1
```

→ V1 が新規実行、9 テーブル + `flyway_schema_history` が生成。

#### 経路 B: baseline（既存テーブルあり）

`flyway_schema_history` テーブルのみ削除しデータテーブル 9 個を残した状態で再起動。

```text
o.f.c.i.s.JdbcTableSchemaHistory : Creating Schema History table `bb_market`.`flyway_schema_history` with baseline ...
o.f.core.internal.command.DbBaseline : Successfully baselined schema with version: 0
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema `bb_market`, now at version v1 (execution time 00:00.040s)
```

→ baseline 0（`existing-bb_market`）が記録された後、V1 が **`CREATE TABLE IF NOT EXISTS`** によって既存テーブルを上書きせず空振り適用。実行時間 40ms（A の 476ms に対し圧倒的に短い）が「実体は何も作っていない」ことを裏付ける。

最終 Flyway 履歴:

| installed_rank | version | description | type | success |
| --- | --- | --- | --- | --- |
| 1 | 0 | existing-bb_market | BASELINE | 1 |
| 2 | 1 | init | SQL | 1 |

## 分析・判断記録

### `CREATE TABLE IF NOT EXISTS` を採用した理由

「経路 A／B で同じ V1 を共用する」要件を満たす最も単純な手段。Flyway 標準の運用ではマイグレーション内部での冪等性は推奨されないが（マイグレーションごとに分離するのが基本）、本件は **「過去スキーマの最終形を 1 本に圧縮した初期マイグレーション」** という特殊な位置付け。`IF NOT EXISTS` を使わずに V1 を書くと、経路 B で `Table 'users' already exists` エラーになる。

代替案として「経路 B 用に `spring.flyway.baseline-version=1` を採用し V1 を完全にスキップさせる」案もあったが、それだと **新規環境では V1 が実行されない** ので両立しない。今回は **`baseline-version=0` + `IF NOT EXISTS`** の組合せが両立解。

### `messages.bord_id` を残す判断

リネームしてもアプリ側コードを書き換えれば動くが、**旧データを引き継ぐ際に `bord_id → board_id` のリネーム ALTER は本番運用では止血操作**（一時的にダウンタイム + アプリ側コード切替）が必要。フェーズ 2 ではこのリスクを取らず、列名を維持しタイポを残した。Spring Entity 側では `@Column(name = "bord_id")` で吸収する。

### `password_reset_tokens` テーブルを作らない判断

旧 PHP はセッションに `auth_key`/`auth_email`/`auth_key_limit` を入れる方式で、DB テーブルが存在しない。**「旧スキーマを正とする」原則**（replacement-policy.md §4）に従って V1 では作成しない。フェーズ 5 のパスワード再発行実装で Spring セッション（または Redis 等の外部ストア）に同じ情報を入れる方針で進める。永続化が要件化したら V2 で `password_reset_tokens` を追加する余地は残る。

### 残るリスクと未確定事項

| 項目 | 状況 |
| --- | --- |
| 旧 DB 実データの引き継ぎ | 現環境の `baseball-market-spring-db-store` は空。`bb-market-db-store` から `mysqldump` → 本環境にインポートする手順を別途実施必要 |
| 旧 DB の実カラム型と本 V1 の型一致 | 実 DB と照合できていない。フェーズ 4 で User Entity を `ddl-auto=validate` で起動した時に乖離が見つかる可能性あり |
| `boards.updated_at` / `messages.updated_at` / `likes.updated_at` の旧 DB 実存 | 旧 PHP コードに更新 SQL が無いため不明。本 V1 では追加しているが、旧 DB に該当列が無い場合、引き継ぎ時に `ddl-auto=validate` が失敗する可能性あり |

## 次フェーズ

**フェーズ 3: shared コンテキスト構築**

- `shared/domain/exception/ValidationException` 等の例外基盤
- `shared/infrastructure/security/SecurityConfig`（フォーム認証 + BCrypt + remember-me 30 日 + CSRF）
- `shared/infrastructure/security/TokenGenerator`
- `shared/infrastructure/mail/MailService`（`JavaMailSender` ラッパー）
- `templates/shared/layout.html`（旧 header.php / footer.php 相当のフラグメント）
- `GlobalExceptionHandler` の配置場所は **`shared` 配下に `presentation` を作るかどうか** を含めて判断（architecture.md §2 では `shared` は presentation を持たない方針）
