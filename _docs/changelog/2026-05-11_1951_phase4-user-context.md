# 2026-05-11 19:51 フェーズ 4: user コンテキスト（signup → email verify → login → logout）

## 背景

フェーズ 3 で shared 基盤（例外/Security/Mail/共通レイアウト）を整え、フォーム認証と CSRF が動く状態になった。だが UserDetailsService の実体が無いため、Spring Boot 自動生成の InMemory ユーザーで動いていて「メールで本人確認をして DB に永続化されたユーザーがログインする」という旧 PHP の核となる導線が未実装だった。

フェーズ 4 で実装したのは、旧 `baseball-market` の最も中心的なユースケース:

- アカウント登録（signup）
- 確認メール送信（24 時間有効トークン）
- メール内リンクで認証完了（emailVerify）
- 確認メール再送信（emailVerifyResend）
- ログイン / ログアウト
- 認証状態のセッション維持

これにより、フェーズ 5 以降（パスワード変更・プロフィール編集など）で「ログイン済みユーザー」前提のフローを安心して構築できる土台ができる。

## 着手前の調査

旧 PHP の対応コード一式を読み取り、移植仕様を確定した:

| 旧 PHP ファイル | 抽出した仕様 |
| --- | --- |
| `User/Domain/Email.php` | 正規表現 `^[a-zA-Z0-9]+[a-zA-Z0-9._-]*@...$`、255 文字制約 |
| `User/Domain/Password.php` | 半角英数・6 文字以上 255 文字以下、`fromPlainText`/`fromHash`/`verify` の 3 メソッド構造 |
| `User/Domain/EmailVerificationToken.php` | `bin2hex(random_bytes(16))` 相当の 32 文字 hex、24 時間 TTL |
| `User/Application/UserService.php` | register → sendVerificationEmail → verifyEmail → resendVerificationEmail、メール本文（旧テキスト） |
| `User/Infrastructure/UserRepositoryImpl.php` | SELECT は常に `delete_flg = 0` 絞り込み、user_profiles LEFT JOIN、トークン保存時は既存削除→新規 INSERT |
| `User/Presentation/Signup/Login/EmailVerify/EmailVerifyResend Controller` | POST → success 時 flash + 302 redirect、ValidationException → 同画面再描画 + errors マップ |
| `views/signup.php` / `views/login.php` 等 | Tailwind CDN + Remix Icon の UI 構造、`err_msg['common']` `err_msg['email']` 等のキー命名 |

旧 PHP のパスワード再発行はセッションに保存していたため DB テーブルは無いと判明（フェーズ 2 で確認済み）。同様の理由でフェーズ 5 で扱う。

## 変更内容

### 1. Value Objects（4 ファイル, user/domain/）

| クラス | 形 | ポイント |
| --- | --- | --- |
| `UserId` | record(long value) | `temporary()`/`isTemporary()` で旧 `UserId::temporary()` 相当 |
| `Email` | record(String value) | コンパクトコンストラクタでフォーマット + 255 文字検証。違反時 `ValidationException("email", ...)` |
| `Password` | 通常クラス | 旧の `fromPlainText` 内ハッシュ化は Spring の `PasswordEncoder` DI に分離。`validateRawPassword()` static で平文ルールのみ検証、ハッシュ化は UserService が `passwordEncoder.encode(...)` で実施、結果を `fromHash` でラップ |
| `EmailVerificationToken` | record(String, LocalDateTime) | `generate()` で 32 文字 hex（`TokenGenerator.generateSecure(32)`）+ 24h TTL を `Duration` で表現 |

### 2. JPA Entity（3 ファイル, user/domain/）

| クラス | 対応テーブル | 注意点 |
| --- | --- | --- |
| `User` | users | `@PrePersist`/`@PreUpdate` で created/updated 自動。`Integer id`、`Byte deleteFlg`（後述の型不一致対応） |
| `UserProfile` | user_profiles | `@MapsId` + `@OneToOne(fetch=LAZY)` で User と PK 共有 |
| `EmailVerificationTokenEntity` | email_verification_tokens | 独立 PK、`userId` を Integer で保持（FK 制約は V1 で定義済み） |

### 3. Repository 層（3 ファイル, user/infrastructure/）

| クラス | 役割 |
| --- | --- |
| `UserJpaRepository` | package-private、`JpaRepository<User, Integer>`。`@Query` で `delete_flg=0` 絞り込みと `left join fetch u.profile` を実現 |
| `EmailVerificationTokenJpaRepository` | 同上、`@Modifying` で `deleteByUserId` |
| `UserRepositoryImpl` | `@Repository`、ドメイン IF を実装し JPA 委譲。Page/Pageable は本クラス内に閉じ込め、`long ↔ int` 変換ヘルパ `toInt(UserId)` を持つ |

### 4. SpringUserDetailsService（1 ファイル, user/infrastructure/）

旧 `UserService::login` の状態判定（削除済 / 未認証）を Spring Security の `UserDetails` フラグに翻訳:

- `delete_flg=1` → `accountNonLocked = false`（Spring 側で `LockedException`）
- `email_verified_at IS NULL` → `enabled = false`（Spring 側で `DisabledException`）

これにより、`UserService` 側にログイン用メソッドを持たずに済む（フォーム POST は Spring Security の `DaoAuthenticationProvider` + `BCryptPasswordEncoder` Bean が直接処理）。

### 5. UserService（1 ファイル, user/application/）

`@Service` + クラスレベル `@Transactional`。旧 PHP UserService から本フェーズ範囲の 4 メソッドを移植:

- `register(email, rawPassword): UserId` — 重複チェック → 平文検証 → encoder でハッシュ化 → save → `sendVerificationEmail`
- `sendVerificationEmail(userId, email)` — `EmailVerificationToken.generate()` を保存し、Java Text Block で旧と同一の本文を組み立てて `MailService.send(...)` 呼出
- `verifyEmail(token): User` — 期限チェック → `User.setEmailVerifiedAt(now)` + トークン削除
- `resendVerificationEmail(email)` — 認証済み再送防止

### 6. Controllers + DTO（5 ファイル, user/presentation/）

| クラス | URL | HTTP メソッド |
| --- | --- | --- |
| `SignupController` | `/signup` | GET / POST |
| `EmailVerifyController` | `/emailVerify` | GET |
| `EmailVerifyResendController` | `/emailVerifyResend` | GET / POST |
| `LoginController` | `/login` | GET（POST は Spring Security） |
| `SignupRequest` (DTO) | — | `@NotBlank` 3 フィールド |

旧 PHP の挙動を維持:
- バリデーション失敗 → 同画面再描画 + `errors` マップ詰め
- 成功時 → `redirect:/login` + `RedirectAttributes` の `msgSuccess` で「確認メールを送信しました」
- パスワード一致チェックは Bean Validation で表現しきれないため手動

### 7. テンプレート（5 ファイル, templates/）

- `user/signup.html` / `user/login.html` / `user/emailVerify.html` / `user/emailVerifyResend.html`
- `shared/home.html`（フェーズ 6 で Product 一覧に置換予定の暫定ホーム）

旧 PHP の Tailwind + Remix Icon + Noto Sans JP デザインを踏襲。エラー表示は `errors` Map をキー名で参照。共通ヘッダ/フッタは `th:replace="~{shared/layout :: header}"` で取り込み。

### 8. shared への暫定追加

- `HomeController`（shared/infrastructure/web/）— SecurityConfig の `defaultSuccessUrl("/", true)` で必要。フェーズ 6 で `product.presentation.ProductListController` に置き換える前提

## 検証（E2E）

MailHog コンテナを起動の上、curl による完全フロー検証:

| ステップ | 期待 | 結果 |
| --- | --- | --- |
| `GET /signup` | 200 + フォーム描画 | ✅ |
| `POST /signup` (CSRF 込み) | 302 → `/login` | ✅ |
| `users` テーブル | 1 行追加 (`email_verified_at NULL`) | ✅ |
| `email_verification_tokens` | 1 行追加（24h 期限） | ✅ |
| MailHog 受信箱 | 1 通（subject「【メールアドレス確認】…」） | ✅ |
| `GET /emailVerify?token=...` | 200 + 「確認が完了しました」 | ✅ |
| `users.email_verified_at` | now で更新 | ✅ |
| `email_verification_tokens` | 0 行（削除） | ✅ |
| `POST /login` | 302 → `/` | ✅ |
| `GET /` (with session) | ホーム表示 + 「ログイン中: test@example.com」 | ✅ |
| `POST /logout` (CSRF) | 302 → `/` | ✅ |
| `GET /` (logout後) | 「未ログインです」 | ✅ |

## 分析・判断記録

### 1. スキーマ検証エラー: Long vs Integer（INT 列）

`User`/`UserProfile`/`EmailVerificationTokenEntity` を初稿で `Long id` で書いたが、ddl-auto=validate で `Schema-validation: wrong column type ... found [int], but expecting [bigint]` で起動失敗。

**選択肢:**
- A. V1__init.sql を `BIGINT` に変更（Java 慣習側に合わせる）
- B. Java 側を `Integer` に変更（旧スキーマ側に合わせる）

**判断:** B を採用。

理由: V1 を BIGINT に変えると、`users.id` だけでなく `user_profiles.user_id` / `products.user_id` / `boards.sale_user/buy_user` / `messages.from_user/to_user` / `likes.user_id` 等の **FK 列もまとめて BIGINT 化** が必要になる。旧データを `mysqldump` で引き継ぐシナリオで、これら全列の `ALTER TABLE` を整合させる作業負荷は無視できない。Java 側で `Integer` を採用すれば旧スキーマと完全互換でデータ移行も無加工で済む。

派生作業: `UserId` VO は `long value()` のまま維持し、永続化境界で `(int) v` キャストするヘルパ `UserRepositoryImpl#toInt(UserId)` を追加。INT 範囲外を検知するガードも入れた。

### 2. スキーマ検証エラー: Short vs Byte（TINYINT 列）

`delete_flg` を `Short` で宣言したら `expecting [smallint]` で失敗。Hibernate 6 の型マッピングでは:

- `Byte` → TINYINT
- `Short` → SMALLINT
- `Integer` → INT
- `Long` → BIGINT
- `Boolean` → デフォルトでは BIT、設定で TINYINT 可能

旧スキーマは `TINYINT` なので `Byte` に変更。`Boolean` も使えるが `@JdbcTypeCode` での型コード指定が必要で、後の `delete_flg = 0` クエリで「numeric の 0 と比較」の素直さが失われるため、Byte 直の方が単純と判断。`(byte) 0` / `(byte) 1` のキャストは多少冗長だが許容。

### 3. パスワード VO の責務分割

旧 PHP は `Password::fromPlainText($plain)` が内部で `password_hash($plain, PASSWORD_DEFAULT)` を呼んでいた。Java 側で同じ構造を取ると Domain に Spring の `PasswordEncoder` を持ち込むことになり、architecture.md §3「domain は Spring 非依存」に違反する。

判断: 責務を 2 つに割る。

- Domain（`Password.validateRawPassword`）: 平文の文字列ルール検証のみ
- Application（`UserService.register`）: `passwordEncoder.encode(raw)` で hash 化 → `Password.fromHash(hash)` で VO 化

これで Domain は Spring 非依存を保ち、Encoder の差し替え（テストでの `NoOpPasswordEncoder` 等）も自然に行える。

### 4. ログイン認証本体を UserService に書かない

旧 PHP `UserService::login()` は email/password を受けて `verifyPassword` を直接呼んでいた。

Spring 側では同じものを書く必要がない。`UsernamePasswordAuthenticationFilter` + `DaoAuthenticationProvider` + `BCryptPasswordEncoder` Bean + `SpringUserDetailsService` の組み合わせで、`POST /login` が来たら自動的に:

1. SpringUserDetailsService がユーザーをロード
2. enabled/accountNonLocked フラグを判定（disabled/locked なら例外で fail）
3. encoder.matches で平文 vs hash を照合

をやってくれる。UserService.login() を書くと **Spring Security と二重に認証ロジックを持つ** ことになり保守性が悪い。SecurityConfig の宣言だけで完結する設計を選択。

### 5. Spring DevTools がリソース変更を拾わない

`src/main/resources/templates/user/signup.html` を編集しても反映されない問題に遭遇。原因は、DevTools が監視するのは `build/resources/main/` 配下であって、`src/main/resources/` ではない。Gradle の `processResources` タスクが両者を同期する。

対策（運用 tip）: テンプレ編集後は `./gradlew processResources`（Java 編集後は `./gradlew compileJava`）を呼ぶ。今後 Java と HTML を交互に編集する作業では `./gradlew classes` で両方をまとめて反映可能。

### 6. MailHog のネットワーク不一致

`baseball-market-spring-app` コンテナの実所属ネットワークが docker-compose で宣言した `nginx-proxy-manager-network` ではなく、devcontainer のデフォルト `baseball-market-spring_devcontainer_default` であることが判明。

これは devcontainer が compose 経由ではなく独立に起動された経緯による不整合（フェーズ 1 で MailHog を docker-compose に追加したが、devcontainer は既に上がっていて、その compose 設定は適用されなかった）。

応急処置として、MailHog を docker run で起動する際に `--network baseball-market-spring_devcontainer_default --network-alias mailhog` を指定。これで `mailhog` ホスト名解決が通った。

**ユーザー側で恒久対応する場合の選択肢:**
- devcontainer を一旦停止して `docker compose up -d` で全コンテナを再起動（推奨）
- MailHog を別途 docker run で起動するスクリプトを `_docs/` 配下に残す

### 7. Thymeleaf SpEL の null 安全性

`th:classappend="${errors['email']} ? 'border-red-500' : ''"` のような式は SpEL が `errors['email']` を bool 化しようとして null 不可で例外を吐く。代替表記 `${errors['email'] != null}` で OK。

旧 PHP の `<?php if(!empty($err_msg['email'])): ?>` を機械的に変換した結果のミス。今後の view 移植で気をつけるパターンとして残す。

### 8. テスト用ユーザーのクリーンアップ

検証で作った `test@example.com` ユーザーとトークンは検証完了後に `DELETE FROM email_verification_tokens; DELETE FROM users;` で消去した。次フェーズ以降は空状態から始まる。

## 残るリスク・未確定事項

| 項目 | 状況 |
| --- | --- |
| 旧 DB 実データの引き継ぎ | 未実施（mysqldump → import 手順は別途必要） |
| 旧 BCrypt `$2y$` ハッシュとの相互運用 | 旧データ実体が無いため実検証できず。Spring の BCryptPasswordEncoder は `$2y$` も受け付ける仕様のため理論上は OK |
| MailHog の恒久ネットワーク統合 | devcontainer 再起動が必要。今は docker run 起動で凌いでいる |
| `/logout` を SecurityConfig で permitAll にしていない | 認証ユーザーのみ logout 可能（妥当）。未認証で `/logout` を叩くと `/login` リダイレクト。問題なし |
| プロフィール初回登録の動線 | 旧仕様だと signup 直後は user_profiles は無し。プロフィール編集はフェーズ 5 で実装 |

## 次フェーズ

**フェーズ 5: user コンテキスト（補助フロー）**

- パスワード再発行（`/passRemindSend` → メール → `/passRemindRecieve`）— セッションベースで旧仕様踏襲
- パスワード変更（`/passEdit`、要認証）
- プロフィール編集（`/profEdit`、画像アップロード）
- 退会（`/withdraw`、soft delete）
