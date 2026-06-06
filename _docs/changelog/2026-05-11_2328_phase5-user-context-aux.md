# 2026-05-11 23:28 フェーズ 5: user コンテキスト 補助フロー（passReset / passEdit / profEdit / withdraw）

## 背景

フェーズ 4 でアカウント登録 → メール認証 → ログイン/ログアウトの主動線が動くようになった。だが旧 `baseball-market` には他にも user コンテキストに属する重要なフローが 4 つある:

1. **パスワード再発行**（メール送信 → 認証キー入力 → ランダム新パスワードを発行）
2. **パスワード変更**（ログイン中ユーザーが現パスワード検証付きで変更）
3. **プロフィール編集**（ユーザー名・住所・電話番号・年齢・画像アップロード）
4. **退会**（soft delete + 関連データの波及論理削除）

これらが揃わないと「user 機能の最小完成形」とは言えない。また、3 は画像アップロード基盤（後のフェーズ 6 の商品画像でも使う `ImageStorage`）の初出になる。フェーズ 6 着手前に整える必要があった。

## 着手前の調査

旧 PHP の対応コードを精査して仕様を確定した:

| 旧 PHP ファイル | 確認した重要仕様 |
| --- | --- |
| `User/Domain/PasswordResetToken.php` | 8 文字英数の認証キー、30 分有効、`matches(input)` は素の `===` 比較 |
| `User/Presentation/PasswordRemindController.php` | **トークンは DB に保存せず `$_SESSION['auth_key']` 等にセッション保存**。再発行されるパスワードは `TokenGenerator::generate(8)` でランダム生成しメール通知 |
| `User/Presentation/PasswordEditController.php` | 入力フィールド `pass_old` / `pass_new` / `pass_new_re`、旧パスワード検証 → 新旧不一致確認 → 更新 |
| `User/Presentation/ProfileEditController.php` | 画像 3MB 制限、JPEG/PNG/GIF のみ、`sha1(uniqid())` でファイル名生成、`/uploads/` 配下保存、validate は controller 内インライン |
| `User/Presentation/WithdrawController.php` | `UserService::withdraw` が users/products/likes/boards/messages を全部 `delete_flg=1` に。`session_unset` 相当の認証クリアが必要 |

→ パスワードリセットの旧仕様は **DB テーブル無し** という重要な制約があり、フェーズ 2 の段階で V1__init.sql に `password_reset_tokens` を含めなかった判断とも整合する。

## 変更内容

### 1. PasswordResetToken VO（user/domain/）

```java
public record PasswordResetToken(String value, LocalDateTime expiresAt) implements Serializable
```

- `Serializable` を実装 — `HttpSession` 属性として保存され、Spring のセッションコピー時にシリアライズされる前提
- `generate()` で `TokenGenerator.generate(8)` + 30 分 TTL
- `isValid()` / `matches(String)` を旧 PHP と同じシグネチャで提供

### 2. ProfileUpdate record（user/domain/）

プロフィール更新時の入力データを表す record。全フィールド null 許容（Email のみ VO 化済みで必須相当）、`pic` はサーバー側で決定された相対パス文字列。Repository 層に渡す Data Transfer Object として機能する。

### 3. ImageStorage（shared/infrastructure/storage/）

- `@Component` + `@Value("${app.uploads.path}")` で保存先を構成
- 3MB 上限・JPEG/PNG/GIF 検証・`UUID + 拡張子` のファイル名・`InputStream` から先頭バイトで MIME 判定
- 違反時は `ValidationException("pic", ...)` を投げる
- フェーズ 6 の商品画像でも再利用する想定で **shared 配下** に置いた

### 4. UserService 拡張（user/application/）

新メソッド:

| メソッド | 用途 |
| --- | --- |
| `requestPasswordReset(email)` | トークン生成 + 認証キーをメール送信。トークン VO を返す（呼び出し側が `HttpSession` に保持） |
| `resetPassword(email, newRawPassword)` | DB のパスワードを更新し新パスワードをメール通知 |
| `changePassword(userId, oldPassword, newPassword)` | 現パスワード検証 + 新旧不一致確認 + 文字列ルール検証 + 更新 + 変更通知メール |
| `updateProfile(userId, ProfileUpdate)` | email 変更時の重複チェック含む |
| `withdraw(userId)` | soft delete |
| `findById` / `findByEmail` | Controller の Principal 解決用 |

旧 PHP の `requestPasswordReset` ではトークンを DB に保存していなかったので、Spring 側でも返値で渡してセッション側に積む構造にした。

### 5. UserRepository 拡張（user/domain/ + user/infrastructure/）

ドメイン IF に `updatePassword`、`updateProfile`、`withdraw` を追加。`UserRepositoryImpl` で実装:

- `updatePassword`: `findByIdAlive(...).ifPresent(u -> u.setPassword(...))` — JPA dirty checking で UPDATE
- `updateProfile`: `UserProfile` が無ければ生成 → 各フィールドを setter で詰めて dirty checking に任せる
- `withdraw`: `User.markDeleted()` に加えて、`UserJpaRepository` の `@Modifying @Query(value=..., nativeQuery=true)` 4 本（products/likes/boards/messages）を呼び出して旧 PHP の波及論理削除を再現

### 6. Controllers（user/presentation/）

| クラス | URL | HTTP メソッド | 認証 |
| --- | --- | --- | --- |
| `PasswordRemindController` | `/passRemindSend` / `/passRemindRecieve` | GET / POST | 不要（公開） |
| `PasswordEditController` | `/passEdit` | GET / POST | 必須 |
| `ProfileEditController` | `/profEdit` | GET / POST（multipart/form-data） | 必須 |
| `WithdrawController` | `/withdraw` | GET / POST | 必須 |

ポイント:

- `PasswordRemindController` はトークンを `HttpSession` の `passReset.token` / `passReset.email` に保存し、`/passRemindRecieve` GET で attribute 存在チェック → 不在なら `/passRemindSend` へ戻すガード付き
- `WithdrawController` POST で `new SecurityContextLogoutHandler().logout(...)` を呼んで自前で認証クリア（Spring の自動 logout フローを介さずに完結させる）
- 認証ユーザーの解決は `Principal#getName()` → `userService.findByEmail(...)` の 2 ステップで統一（カスタム `UserDetails` 拡張を作らない）

### 7. DTO

- `PasswordEditRequest` — `@NotBlank` 3 つ
- `ProfileEditRequest` — `@Size`/`@Pattern`/`@Min`/`@Max` で旧 PHP のインライン検証を Bean Validation に翻訳

### 8. Views（templates/user/）

5 ファイル新規:
- `passRemindSend.html` / `passRemindRecieve.html` / `passEdit.html` / `profEdit.html` / `withdraw.html`

`profEdit.html` だけ `enctype="multipart/form-data"`。`th:if="${currentPic}"` で既存画像のプレビュー表示。

### 9. HomeController に `/mypage` プレースホルダ追加

パスワード変更とプロフィール編集の成功時の redirect 先が `/mypage` だが、本実装はフェーズ 8。検証フローで 302 → 404 にならないよう、暫定で `shared/home.html` を返すマッピングを追加。フェーズ 8 で削除予定。

## 検証（E2E）

`baseball-market_devcontainer_default` ネットワーク上に MailHog を一時起動して、curl による全フロー検証:

| シナリオ | 結果 |
| --- | --- |
| signup → email verify → login（フェーズ 4 機能の回帰確認） | ✅ |
| GET `/passEdit` → POST（pass123 → pass456） → 302 `/mypage` | ✅ |
| 新パスワード `pass456` で再ログイン | ✅ 302 → `/` |
| GET `/profEdit`（メール`test5@example.com`がプリフィル） | ✅ |
| POST `/profEdit`（日本語の `テスト太郎` / `東京都` / `渋谷区` を含む） | ✅ DB に UTF-8 で保存（utf8mb4 クライアントで確認） |
| GET `/passRemindSend` → POST → 302 `/passRemindRecieve` + メール送信 | ✅ |
| MailHog 本文から 8 文字認証キー抽出 | ✅ Base64 デコードで成功 |
| POST `/passRemindRecieve`（key 検証） → 302 `/login` + 新パスワード通知メール | ✅ |
| 通知メールの新パスワードでログイン | ✅ 302 → `/` |
| GET `/withdraw` | ✅ 200 |
| POST `/withdraw` → 302 `/` | ✅ `users.delete_flg=1` 更新 |
| 退会済アカウントで再ログイン試行 | ✅ 302 `/login?error`（SpringUserDetailsService が accountNonLocked=false を返す） |

## 分析・判断記録

### 1. パスワードリセットトークンを DB ではなく `HttpSession` に保存

**選択肢:**
- A. 旧 PHP の `$_SESSION` 方式を踏襲し `HttpSession` 属性に VO を入れる
- B. 別途 `password_reset_tokens` テーブルを V2 マイグレーションで追加して永続化

**判断:** A を採用。

理由:
- 旧 PHP は意図的にセッション保存方式を採っており、これを変えると「旧 DB を引き継ぐ」運用で新規テーブル整合が必要になる
- 30 分以内で完結するワンタイム性のトークンなので、永続化のメリットは限定的
- Spring の `HttpSession` は分散環境では Redis 等で永続化できるため、スケーラビリティに不利でもない
- 派生作業として `PasswordResetToken` record に `Serializable` を実装させた（後で Spring Session を導入してもそのまま動く）

トレードオフ: 同じユーザーが複数端末で「パスワード再発行」を同時にトリガーすると、後発のトークンしか入力できない（旧 PHP も同じ挙動）。許容。

### 2. 退会の波及論理削除を user 内で行うか、application オーケストレーションに分けるか

**問題:** 旧 PHP `UserService::withdraw` は users 以外に products/likes/boards/messages も同時に `delete_flg=1` に更新する。本来はそれぞれ product/like/message context の責務。

**選択肢:**
- A. `UserRepositoryImpl` 内で他テーブルへ直接ネイティブクエリ
- B. user.application に Orchestration クラスを作り、product/like/message の各 `Repository.softDeleteByUserId` を呼び出す
- C. ドメインイベント発行 → 各 context が購読して自己 soft delete

**判断:** A を当面採用。フェーズ 6 以降で product/like/message の Repository が完成したら B または C に移行する余地を残す。

理由:
- フェーズ 5 時点では他 context の Repository が存在しないため、B/C は実装不可能
- A は ARCHITECTURE.md の「別 context への直接依存禁止」原則に違反するが、**SQL は別 context のテーブルに直接 UPDATE するだけで Java の依存は持ち込んでいない** ため、規約逸脱の度合いは小さい
- 移行コストは「`UserRepositoryImpl` のネイティブクエリ呼び出し 4 行を Service 経由に置き換え」だけで完了する

→ フェーズ 7 完了時に B への移行を再検討する。

### 3. ImageStorage を user/infrastructure ではなく shared/infrastructure に置いた

旧 PHP は `ProfileEditController` 内に画像アップロード処理がインラインで書かれていて、商品画像も同じ手法で個別に実装していた。

Spring 側では 1 度きりのコードにしたい。プロフィール（user）と商品画像（product）の 2 文脈で使うため、**shared/infrastructure/storage** 配下に置いた。これは [architecture.md §1](../../.claude/architecture.md) の「shared = 横断的関心事」原則に合致する。

### 4. `@AuthenticationPrincipal` を採用しなかった

Spring Security の Principal は `UserDetails`（`org.springframework.security.core.userdetails.User`）で、`getUsername()` がメールアドレスを返す。これを Controller で `Principal principal` 引数として受け、`userService.findByEmail(principal.getName())` で User エンティティに変換する。

カスタム `UserDetails` 実装に `UserId` を持たせて `@AuthenticationPrincipal CustomUserDetails` で受ける案もあったが、

- 認証時に 1 度 + 各 Controller で 1 度の合計 2 回 DB アクセスする現状でも、まだコスト的に問題ない
- 認証セッションに UserId を保持すると、DB と認証状態の同期問題が生じうる（メールアドレス変更後に再ログインしないと UserId が古くなる、など）

を考慮して、毎回 email から引き直す方針が単純で安全と判断した。後でパフォーマンス問題が出たら見直す。

### 5. ProfileEditController の email 変更時のバリデーション順序

`ProfileEditRequest` の `@NotBlank` でフォーム欄の必須は捕まえる。だが `Email.fromString()` は VO の段階で正規表現チェックを行い、違反時は `ValidationException` を throw する。

実装上、`UserService.updateProfile` 内で `Email.fromString(form.getEmail())` を呼んでいるが、これだと「画像アップロードが先に処理されて、画像保存後にバリデーション失敗」というケースが発生しうる（画像が孤児化する）。

応急的に: Controller で先に `Email.fromString` を呼ぶ案もあったが、現状は「画像保存も `errors` チェック後にのみ実行」する順序にして対応している。完璧ではない（Email VO の validation は updateProfile の中で走るのでまだ画像が先に保存される可能性が残る）。

将来的にはアップロード前にすべての非画像バリデーションを通す `@Validated` ステップを明示的に分離するか、画像保存を `@Transactional` ロールバックで巻き戻すなどの設計が必要。フェーズ 6 で商品登録の画像アップロードを実装する際にあらためて検討する。

### 6. MailHog API のメール本文が Base64

Spring の `MimeMessageHelper.setText(body, false)` はデフォルトで `Content-Transfer-Encoding: base64` を出す。旧 PHP の PHPMailer も Base64 を選択していたので、メール仕様としては変わらない。

検証スクリプトでメール本文を取り出す際に、最初 `quopri.decodestring` を試して失敗 → `base64.b64decode` で成功、という回り道があった。**今後の動作確認スクリプトのテンプレ tip**: MailHog API 本文は `m["Content"]["Headers"]["Content-Transfer-Encoding"]` を見て Base64/Quoted-Printable を判別すべき。今回はずっと Base64 出力なので即 base64 デコード。

## 残るリスク・未確定事項

| 項目 | 状況 |
| --- | --- |
| `withdraw` の波及論理削除を context 越境のまま放置 | フェーズ 7 完了後に Application 層オーケストレーションへ移行を再評価 |
| `/passRemindRecieve` で同時複数 reset 不可 | 旧 PHP と同等の挙動。許容 |
| プロフィール画像の孤児化（バリデ失敗時） | 限定的だが残る。フェーズ 6 商品画像実装時に共通化検討 |
| `/uploads/` 配下のファイル管理 | 退会時の物理削除は実装していない（旧仕様も同じ）。GDPR 等を考慮するなら後で追加 |
| `/mypage` 暫定マッピング | フェーズ 8 で `MyPageController` に置換 |

## 次フェーズ

**フェーズ 6: product コンテキスト**

- VO: `ProductId` / `ProductName` / `Price` / `ProductImage` / `CategoryId` / `MakerId`
- Entity: `Product` / `Category` / `Maker`
- Repository: `ProductRepository` + JPA Specification で動的検索
- Service: `ProductService`（登録 / 一覧 / 詳細 / 検索 / 売買履歴）
- Controllers: `ProductRegistController` / `ProductListController` / `ProductDetailController` / `SaleHistoryController`
- Views: 4 ファイル
- 画像アップロードは `ImageStorage`（本フェーズで作成済み）を再利用
