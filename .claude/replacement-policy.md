# PHP → Spring Boot リプレース方針

旧 `baseball-market`（PHP 8 / 自前 DDD レイヤード / MySQL 8 / jQuery + Ajax）を、本リポジトリで Spring Boot にフルリプレースする。

## 1. リプレース対象機能（旧 README より）

以下を機能単位で順次移植する。括弧内は所属 context。

- アカウント登録 / ログイン / ログアウト / 退会 (`user`)
- メール認証（仮登録 → 本登録） (`user`)
- パスワード変更 / パスワード再発行 (`user`)
- プロフィール編集 (`user`)
- 商品登録 / 編集 (`product`)
- 商品一覧 / 詳細 (`product`)
- 商品検索（条件指定 SQL 動的生成） (`product`)
- 売買履歴 (`product`)
- いいね機能（Ajax） (`like`)
- メッセージボード (`message`)
- お気に入り表示・マイページ集約 (`mypage`)

## 2. 技術選定（暫定 / 要確定）

| 項目 | 採用 | 状態 |
| --- | --- | --- |
| ビュー | Thymeleaf（旧 SSR + jQuery 構成を尊重） | 確定 |
| 永続化 | Spring Data JPA | 確定 |
| マイグレーション | Flyway（旧 `database/migrations/*.sql` を移植） | 確定 |
| バリデーション | Jakarta Bean Validation（`@Valid` + Hibernate Validator） | 確定（`spring-boot-starter-validation` 同梱済み） |
| DB ドライバ | MySQL Connector/J | 確定 |
| 認証 | Spring Security + フォーム認証 + BCrypt | 要確定 |
| メール | Spring Boot Starter Mail（旧 PHPMailer 相当） | 要確定 |
| 開発用メールサーバ | MailHog（旧 devcontainer と同じ）を docker-compose に追加 | 要追加 |
| DB スキーマ | 旧 `bb_market` スキーマ（`users` / `user_profiles` 分離後）を踏襲 | 確定 |

「要確定」項目は、対象機能の着手前にユーザーへ確認すること。決まったら本表を更新する。

## 3. 旧構成 → 新構成のマッピング

### 3.1 ディレクトリ

```
旧: public/src/<Context>/{Application,Domain,Infrastructure,Presentation}/
新: app/src/main/java/com/shimanamisan/baseballmarket/<context>/{application,domain,infrastructure,presentation}/
```

### 3.2 ファイル単位の対応例

| 旧 (PHP) | 新 (Java) |
| --- | --- |
| `User\Domain\User` | `user.domain.User`（JPA Entity 兼務、初期段階） |
| `User\Domain\Email`（VO） | `user.domain.Email`（record） |
| `User\Domain\UserRepository`（IF） | `user.domain.UserRepository`（interface） |
| `User\Infrastructure\UserRepositoryImpl` | `user.infrastructure.UserRepositoryImpl`（`@Repository`） |
| `User\Application\UserService` | `user.application.UserService`（`@Service`、`@Transactional`） |
| `User\Presentation\LoginController` | `user.presentation.LoginController`（`@Controller`） |
| `Shared\Infrastructure\Database\DatabaseConnection` | Spring の `DataSource`／JPA で代替 |
| `Shared\Infrastructure\Session\SessionManager` | Spring Security の `SecurityContextHolder` で代替 |
| `Shared\Infrastructure\Security\TokenGenerator` | `java.security.SecureRandom` ベースの自前ユーティリティ |
| `Shared\Infrastructure\Mail\MailService` | `JavaMailSender` ラッパー |
| `Shared\Infrastructure\Logger\Logger` | SLF4J/Logback で置換（自前ラッパーは作らない） |

### 3.3 フロントエンド

- 旧: `src/scss/`（Sass）+ `src/js/`（jQuery）+ Vite ビルド → `public/assets/` へ吐き出し
- 新（暫定）: `src/main/resources/static/` 配下に配置。ビルドツールを残すかは要確認
- jQuery は維持か脱却か未定。Ajax 部分は最小限の素の `fetch` で書き直すのが軽量

## 4. データベース

- 旧 `database/migrations/split_users_table.sql` で適用された `users` / `user_profiles` 分離後のスキーマを正とする
- 新規マイグレーションは Flyway（採用確定後）で `V1__init.sql`, `V2__...` の連番管理に移行
- 既存データを引き継ぐかは要確認（学習用プロジェクトなので新規構築で問題ない可能性が高い）

## 5. 段階的な進め方（推奨順序）

1. **基盤整備**: `demo/` のパッケージ構成を本ルールに沿って作り直す。`build.gradle` に JPA / Security / Validation / Thymeleaf / MySQL Driver / Flyway を追加（採用確定後）
2. **`shared` 整備**: 共通例外、Validator、Logger 設定、Mail 設定
3. **`user` context**: signup → email 認証 → login → logout → password 系 → withdraw → profile edit
4. **`product` context**: 登録 → 一覧 → 詳細 → 検索 → 売買履歴
5. **`like` context**（Ajax）
6. **`message` context**
7. **`mypage` context**
8. **フロントエンド調整 & E2E 動作確認**

各ステップで動く状態を保ち、機能ごとにコミット → PR を切る。

## 6. やらないこと

- 旧 PHP コードの逐語訳（命名や責務分割は本ルール / `architecture.md` 優先）
- 「Spring らしい」という理由で旧構成を平らにする（Bounded Context とレイヤーは維持）
- 旧プロジェクトのテスト（PHPUnit）の機械的移植（テストは Java 側で再設計）
