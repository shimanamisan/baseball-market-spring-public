# 2026-05-11 19:20 フェーズ 3: shared コンテキスト構築（例外・Security・Mail・共通レイアウト）

## 背景

フェーズ 2 で V1__init.sql を整え、JPA `ddl-auto=validate` が動く土台ができた。次のフェーズ 4（user コンテキストの signup/verify/login/logout）に進むには、複数 context が共通利用する横断的関心事 — 例外、認証、メール送信、画面レイアウト — を先に揃える必要がある。これらが揃っていないと user 機能を作っても繋がるパーツが無く動かせない。

着手時に未整備だった項目:

- ドメイン違反を表す共通例外（旧 `Shared\Domain\Exception\ValidationException` 相当）
- Spring Security の設定（フォーム認証 / CSRF / セッション / remember-me / BCrypt）
- メール送信の Spring 側ラッパー（旧 `Shared\Infrastructure\Mail\MailService` 相当）
- ランダムトークン生成（旧 `Shared\Infrastructure\Security\TokenGenerator` 相当）
- 横断例外ハンドラ
- 全画面共通の Thymeleaf レイアウト

## 着手前の確認: GlobalExceptionHandler の配置

プラン段階で「shared 配下に presentation を置くか、ユーザー確認の上決定」と保留していた論点を最初に確認した。

- [architecture.md §2](../../.claude/architecture.md) は **「shared のみ `presentation` を持たず、`domain` と `infrastructure` のみで構成する」** と明記
- `@ControllerAdvice` は Spring MVC への依存があり Bounded Context の責務ではない
- → **`shared/infrastructure/web/` 配下** に置くことで決着（ユーザー合意済み）

architecture.md の文言を「presentation という名前のパッケージを置かない」と解釈し、Spring MVC への適合層は infrastructure 配下の小パッケージ（`web`）として扱う運用とした。

## 変更内容

### 1. 例外基盤

| ファイル | 役割 |
| --- | --- |
| `shared/domain/exception/DomainException.java` | abstract 基底。各 context のドメイン例外もこれを継承する想定 |
| `shared/domain/exception/ValidationException.java` | 旧 PHP の `getErrors()` / `getFirstError()` を踏襲。単一メッセージ → `errors["common"]`、フィールド別 → `errors[field]` の 2 形態をサポート |

旧 PHP は `string|array` 型ユニオンを `__construct` で受けていたが、Java では 3 つのコンストラクタ（`String`, `Map<String,String>`, `String field, String message`）に分けて型安全に。返却は `Map.copyOf` で **不変ビュー** にして呼び出し側が壊せないようにした。

### 2. TokenGenerator

旧 `mt_rand` ではなく `java.security.SecureRandom` を使用。

- `generate(int length)` → 62 文字種から N 文字。旧 PHP と CHARSET 文字列を完全一致（旧コードのタイポ `IJLK` を含む順序）させ、移行後も同分布になるようにした
- `generateSecure(int length)` → `bin2hex(random_bytes(length/2))` 相当。length は偶数を要求

> ⚠ 旧 PHP の `CHARSET` には `IJLK` という順序の入れ替えがあり（"IJKLM" でなく "IJLKM"）、おそらくタイポ。可読性のためソートし直す案もあったが、**生成パターンの再現性** を優先して残した。次フェーズ以降で問題視されれば直す。

### 3. Mail 基盤

| ファイル | 役割 |
| --- | --- |
| `shared/infrastructure/mail/MailProperties.java` | `@ConfigurationProperties("app.mail")` を持つ record。`fromAddress` / `fromName` を束ねる |
| `shared/infrastructure/mail/MailService.java` | `JavaMailSender` ラッパー。旧 `send(from, to, subject, body): bool` シグネチャ踏襲 |

`@ConfigurationProperties` を有効化するため、メインクラス `BaseballMarketSpringApplication` に `@ConfigurationPropertiesScan("com.shimanamisan.baseballmarket")` を追加。`@EnableConfigurationProperties(MailProperties.class)` を使う案より、context が増えた時の更新負荷が低いため scan 方式を採用。

旧 PHP は `$_ENV['MAIL_HOST']` 等から都度値を取りに行く実装だったが、Spring 側は `JavaMailSender` 自体が `spring.mail.*` を auto-configure するので二重管理を避け、`MailService` は from/to/subject/body だけを扱う薄い層にした。

### 4. SecurityConfig

旧 PHP 仕様の踏襲ポイント:

| 旧 PHP の挙動 | Spring 側の対応 |
| --- | --- |
| email + password でフォームログイン | `.formLogin().usernameParameter("email").passwordParameter("password")` |
| セッション 1 時間 | `server.servlet.session.timeout=1h`（フェーズ 1 で設定済み） |
| remember-me 30 日 | `.rememberMe().tokenValiditySeconds(30*86400)`、フォーム param 名 `remember-me` |
| `session_regenerate_id(true)` | `.sessionFixation(sf -> sf.migrateSession())` |
| 同時 1 セッション | `.maximumSessions(1)` |
| `password_verify` | `BCryptPasswordEncoder` Bean |

公開 URL は旧 PHP の `*.php` ファイル名と完全一致させ、`/signup`, `/login`, `/emailVerify`, `/emailVerifyResend`, `/passRemindSend`, `/passRemindRecieve`, `/productDetail/**` 等を `permitAll`。`/`（商品一覧、未認証でも閲覧可能）も `permitAll`。それ以外は `authenticated`。

CSRF はデフォルト（有効）のまま。Thymeleaf が自動で `_csrf` hidden を埋め込み、Ajax は `<meta name="_csrf">` をフロントで読んでヘッダに付ける構成（フェーズ 7 like 機能で利用）。

### 5. GlobalExceptionHandler

`@ControllerAdvice` で 3 種類のハンドラを定義:

- `handleValidation(ValidationException, request)` — 400 でモデルに `errors`/`message` を詰めて `shared/error` を返す
- `handleNotFound(NoResourceFoundException, request)` — 404 で同テンプレート
- `handleUnexpected(Exception, request)` — 500 で同テンプレート + ログに stack trace

`NoResourceFoundException` のハンドラを **動作確認の過程で追加** した（後述）。

### 6. 共通レイアウト

| ファイル | 役割 |
| --- | --- |
| `templates/shared/layout.html` | `th:fragment="header"` / `th:fragment="footer"` を切り出し。CSRF メタタグ、Tailwind CDN、Remix Icon、Noto Sans JP を `<head>` に常設。`sec:authorize` で認証状態に応じてヘッダのメニュー（ユーザー登録/ログイン vs マイページ/ログアウト）を切替 |
| `templates/shared/error.html` | エラー画面。`${message}` と `${errors}`（Map）を表示する最小構成 |

旧 PHP の `head.php` / `header.php` / `footer.php` を参考にしたが、デザイン詳細はフェーズ 4 以降の各画面実装で肉付けする方針で **骨組みだけ** にした。

### 7. メインクラスの拡張

`BaseballMarketSpringApplication` に `@ConfigurationPropertiesScan` を追加。

## 検証

### コンパイル
```bash
docker exec baseball-market-spring-app bash -lc 'cd /app/app && ./gradlew --no-daemon compileJava'
# → BUILD SUCCESSFUL
```

### bootRun + エンドポイント挙動

| リクエスト | 結果 | 判定 |
| --- | --- | --- |
| `GET /` | 404 + `shared/error.html` 描画（「ページが見つかりません」） | ✅ Controller 未実装で 404、Thymeleaf 解決と GlobalExceptionHandler 連動を確認 |
| `GET /login` | 同上 404 | ✅（LoginController はフェーズ 4） |
| `GET /mypage` | 302 → `/login` | ✅ `anyRequest().authenticated()` 動作 |
| `GET /nonexistent` | 302 → `/login` | ✅ 認証要求が先行 |

レスポンスボディの確認（`/`）:
```html
<title>エラー｜ BASEBALL MARKET</title>
...
<h1 class="text-2xl font-bold text-red-600 mb-4">エラーが発生しました</h1>
<p class="text-gray-700 mb-4">ページが見つかりません</p>
```

→ `shared/error.html` が正常レンダリングされ、`${message}` の Thymeleaf バインディングも動作している。

## 分析・判断記録

### 1. GlobalExceptionHandler が 404 を 500 化してしまう問題

初稿の `GlobalExceptionHandler` は `@ExceptionHandler(ValidationException.class)` と `@ExceptionHandler(Exception.class)` の 2 段構えだった。bootRun 直後に `GET /` を叩くと、本来 404 であるべきところで **500** が返って `Unhandled exception at /: No static resource .` がログに出た。

原因: Spring 6 から、静的リソース解決失敗を `NoResourceFoundException` として **例外で伝搬** する設計に変わった。catch-all の `Exception.class` ハンドラがこれを拾い、500 として処理してしまっていた。

対処: `@ExceptionHandler(NoResourceFoundException.class)` を 1 つ追加し、`HttpStatus.NOT_FOUND` で `shared/error` を返すよう分岐。Spring の例外解決順序は **より具体的なハンドラを優先** するため、これで catch-all から `NoResourceFoundException` だけが先取りされる構造になった。

> 学び: Spring Boot 3 系では 404 もコントローラ層に例外として届く。`@ExceptionHandler(Exception.class)` を書くと暗黙に 404 まで巻き込む。404 を 404 として処理したいなら、専用ハンドラを明示する必要がある。

### 2. SecurityConfig の sessionFixation API 誤用

初稿で `.sessionFixation(SessionFixationProtectionStrategy::new)` と書いてしまった（クラス参照を渡す形）。これは「strategy bean を Customizer で組み立てる」用途で、`SessionFixationConfigurer` のラムダ受領契約と型が合わない。`.sessionFixation(sf -> sf.migrateSession())` に修正。

Spring Security 6 の lambda DSL は、子設定にもう一段 lambda を取らせる入れ子構造になっている。**子の configurer の lambda 内では設定メソッドを直接呼ぶ**（`sf.migrateSession()`）のが定石、と覚え直した。

### 3. MailService に @Service を付けた件

shared/domain は Spring 非依存だが、shared/infrastructure は Spring 依存 OK というのが [architecture.md §3](../../.claude/architecture.md) の方針。`@Service` を `MailService` に付けて DI 可能にした。旧 PHP は `new MailService()` で手動生成していたが、Spring では `JavaMailSender` を構成済みの Bean として注入できる方が自然なため、構成は変更している。これは「Spring らしさ」を理由にした逸脱ではなく、フレームワークの設計に沿った合理化と判断。

### 4. UserDetailsService スタブを今フェーズに含めない判断

プラン 3-3 では `user/infrastructure/SpringUserDetailsServiceImpl.java` のスタブをフェーズ 3 で置く案だった。だが空スタブを置くと、Spring Boot の auto-config（generated password の InMemoryUserDetailsManager）が無効化され、フェーズ 3 時点での起動確認時にログインが「常に失敗」する状態になる。

→ **フェーズ 3 では UserDetailsService Bean を一切定義しない**。Spring Boot 自動生成の InMemory ユーザーが動く（generated password がログに出る）状態を維持し、フェーズ 4 で User Entity と一緒に実 UserDetailsService を投入する方が境界がきれい、と判断。プランを更新せず実装段階の調整として処理した。

### 5. 残るリスク

| 項目 | 状況 |
| --- | --- |
| `/login` の 404 | LoginController がまだ無いため。フェーズ 4 で実装 |
| `/signup` の 404 | 同上 |
| MailService の `from` パラメータ運用 | 旧 PHP は呼び出し側で from を都度渡す方式。Spring 側では `app.mail.from-address` 既定にフォールバックさせており、各呼び出しで `null` を渡す運用が多くなりそう。フェーズ 4 で UserService からの呼び出し時に最終決定 |
| ヘッダ/フッタの旧 PHP デザイン再現度 | フェーズ 4 で各画面を肉付けする際に詳細を反映予定 |

## 次フェーズ

**フェーズ 4: user コンテキスト（signup → email verify → login → logout）**

- VO: `UserId` / `Email`（record） / `Password`（生 vs ハッシュを内部状態化する通常クラス） / `EmailVerificationToken`
- Entity: `User` / `UserProfile` / `EmailVerificationTokenEntity`（JPA）
- Repository: `UserRepository` インターフェース + `UserRepositoryImpl`（Spring Data JPA 委譲）
- Service: `UserService#register / sendVerificationEmail / verifyEmail / resendVerificationEmail`
- Controller: `SignupController` / `EmailVerifyController` / `EmailVerifyResendController` / `LoginController`
- Spring Security 連携: `SpringUserDetailsService`（`UserDetails` 化、`delete_flg=1` → `DisabledException`、未認証 → `LockedException`）
- View: `templates/user/{signup,signup_complete,emailVerify,emailVerifyResend,login}.html`
- 検証: MailHog UI（`http://localhost:8025`）でメール受信を確認、メール内リンクで `users.email_verified_at` が更新される
