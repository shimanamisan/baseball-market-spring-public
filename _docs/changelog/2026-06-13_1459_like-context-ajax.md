# 2026-06-13 14:59 like コンテキスト（Ajax お気に入り）

> PR #32 由来。changelog 未作成だったため遡って記録。
>
> **フェーズ番号の位置づけ**: like はフェーズ 6 changelog の計画で「フェーズ 7: like / message」として message と同枠にあったが、フェーズ 7 は購入導線を優先して message のみ実装し、like を後送りにしていた。本実装はその**積み残しの like 部分を独立コンテキストとして完成**させたもの。次フェーズの **mypage（フェーズ 8）とは別**であり、like がフェーズ 8 を占有するわけではない（mypage の「お気に入り一覧」が本 context の読み取り API を消費する関係）。

## 背景

旧 PHP の `Like/`（Ajax いいね）が唯一 message と並んで未移植のまま残っていた。他 context から独立しているため後追い可能な設計だったが、次フェーズ mypage の「お気に入り一覧」が `likes` テーブルを参照するため、mypage 着手前に like を片付けておく。

旧 PHP の挙動:

- 商品詳細のハートボタンを押すと `ajaxLike.php` に POST し、JSON `{"response": "add"|"remove"|"no_login"|"error", "message": ...}` を返す
- 登録済みなら削除（**物理 DELETE**）、未登録なら追加のトグル
- 未ログインは `no_login` を返してログイン誘導
- フロントは jQuery でハートアイコンを切り替え

## 変更内容

### 1. like コンテキスト（DDD 一式・新設）

#### domain（`like/domain/`）

| クラス | 形 | ポイント |
| --- | --- | --- |
| `LikeId` | record(long) | 他 ID（`ProductId`/`UserId`）と同形（`fromLong`/`temporary`/`isTemporary`）。負値は不正 |
| `Like` | `@Entity(likes)` | `Integer id` / `user_id` / `product_id` / `delete_flg`(Byte) / `created_at` / `updated_at`。`@PrePersist`・`@PreUpdate` で日時管理。他 context は **ID 値で保持**し `@ManyToOne` でぶら下げない |
| `LikeRepository` | interface | `exists` / `add` / `remove`（物理削除）/ `findProductIdsByUserId`（商品 Entity でなく **商品 ID 一覧**を返し context 跨ぎを避ける） |

#### infrastructure（`like/infrastructure/`）

- `LikeJpaRepository`（package-private）: `existsByUserIdAndProductId` / `@Modifying` の `deleteByUserIdAndProductId`（物理削除）/ `findProductIdsByUserIdOrderByCreatedAtDesc`（新しい順の商品 ID）。
- `LikeRepositoryImpl`（`@Repository`）: 上記へ委譲。Spring Data 型をクラス外へ漏らさない。

#### application（`like/application/`）

- `LikeService`（`@Service` + クラス `@Transactional`、read 系は `readOnly=true`）
  - `toggleLike(userId, productId)` — 存在すれば削除、なければ追加。`userId`/`productId` が 0 以下なら `ValidationException`。商品・ユーザーの存在確認は呼び出し元が担保（旧実装同様 FK 制約に委ねる）
  - `isLiked(userId, productId)` — 不正 ID（未ログインゲスト等）は false
  - `getUserLikedProductIds(userId)` — お気に入り商品 ID 一覧（新しい順）。**mypage（フェーズ 8）が消費する読み取り API**。不正 ID は空リスト
- `ToggleResult` enum（`ADD` / `REMOVE`）— 旧 PHP の文字列 `'add'`/`'remove'` を型で表現。Ajax の値表現は presentation 層が決める

#### presentation（`like/presentation/`）

| クラス | URL | 役割 |
| --- | --- | --- |
| `LikeController`（`@RestController`） | POST `/likes/toggle` | Principal からユーザー解決 → `toggleLike` → JSON 返却。未認証は `no_login` |
| `LikeToggleResponse` record | — | 旧 PHP 互換の JSON `{response, message}`。`@JsonInclude(NON_NULL)` で `message` は error 時のみ |

- 例外は **Controller ローカルで JSON 化**（`GlobalExceptionHandler` は HTML を返すため Ajax に不適）:
  - `MissingServletRequestParameterException` → 「パラメータが不正です」
  - `MethodArgumentTypeMismatchException` → 「商品IDが不正です」
  - `ValidationException` / その他 `RuntimeException` → error JSON（後者はログ出力 + 汎用文言）

### 2. 商品詳細ページへの組み込み

- `product/presentation/ProductDetailController.show` に `Principal` を受け取り、ログイン時は `LikeService.isLiked` で初期ハート状態（`liked`）を Model に追加（`UserService.findByEmail` でユーザー解決）。
- `templates/product/detail.html` — ハートボタンに初期 active 状態と `data-product-id` を反映。
- `static/js/like.js`（**新規・素の fetch、jQuery 脱却**）— `.js-click-like` クリックで `/likes/toggle` に POST。`no_login` はログイン誘導、`add`/`remove` でアイコン（`ri-heart-fill`/`ri-heart-line`）と色をトグル。CSRF トークンは `<meta name="_csrf">` から読みヘッダ付与。多重送信防止（`data-loading`）。

### 3. セキュリティ

- `SecurityConfig` の permitAll に `/likes/**` を追加。**未ログインは例外でなく `no_login` JSON** で返すため（旧 UX 踏襲）認可では弾かない。CSRF は標準有効を維持し、フロントが meta 経由でトークンを送る。

### 4. テスト（t-wada 流 TDD: JUnit5 + AssertJ + Mockito）

| テスト | ケース数 | 検証内容 |
| --- | --- | --- |
| `LikeIdTest` | 3 | 正常生成 / 負値拒否 / temporary |
| `LikeServiceTest` | 8 | toggle（追加・削除）/ 不正 ID 拒否 / isLiked（true/false/不正 ID）/ 一覧取得 |
| `LikeControllerTest` | 4 | 単体: no_login / add / remove / error の JSON 組み立て |
| `LikeControllerWebMvcTest` | 5 | WebMvc スライス: エンドポイント疎通・パラメータ欠落/型不正・認証有無 |

## 検証

| 項目 | 結果 |
| --- | --- |
| 新規 like テスト全 20 ケース | ✅ 全緑 |
| `contextLoads`（`@SpringBootTest`） | ⚠️ 既知の `db` 別名衝突（環境問題）で素の `./gradlew test` では失敗。`SPRING_DATASOURCE_URL` で接続先を一意化すれば緑（フェーズ 7 changelog §6 と同件・別対応） |

## 分析・判断記録

### 1. context 跨ぎを ID 値に閉じる

`LikeRepository.findProductIdsByUserId` は商品 Entity でなく**商品 ID のリスト**を返す。お気に入り一覧の商品表示情報は呼び出し元（mypage）が product context の application API から取得する。like domain は user/product の Entity を一切参照せず、境界を保つ（message context と同方針）。

### 2. Ajax 例外を Controller ローカルで JSON 化

`GlobalExceptionHandler` は HTML エラーページ（`shared/error`）を返す前提で、Ajax から呼ぶと HTML が返り JSON を期待するフロントが壊れる。よって like の例外ハンドリングは `LikeController` 内の `@ExceptionHandler` で完結させ、常に `LikeToggleResponse` JSON を返す。

### 3. 未認証を認可で弾かず no_login JSON

`/likes/**` を `authenticated()` にすると未ログイン時にログインページの HTML へリダイレクトされ、Ajax が壊れる。permitAll にして Controller で `principal == null` を `no_login` JSON にし、フロントでログイン誘導する旧 UX を再現。CSRF は無効化せず meta 経由で維持。

### 4. 物理削除を踏襲

`Like` Entity は `delete_flg` を持つが、`remove` は旧 PHP の DELETE 挙動に合わせ**物理削除**（`@Modifying` の delete クエリ）。論理削除に倒すかは将来 mypage で一覧要件が固まった時点で再評価。

### 5. フロントの jQuery 脱却

`like.js` は旧 `assets/js/main.js` のいいね挙動を素の `fetch` で書き直し。`replacement-policy.md` §3.3 の「Ajax 部分は最小限の素の fetch」方針に沿う。

## 残るリスク・未確定事項

| 項目 | 状況 |
| --- | --- |
| `db` 別名衝突（環境） | フェーズ 7 と同件。素の `./gradlew test` で `contextLoads` が落ちる。恒久対応（テスト用プロファイル / 環境変数）は別件 |
| 物理削除 vs 論理削除 | 当面物理削除。mypage のお気に入り一覧要件確定時に再評価 |
| お気に入り数の表示 | 旧 PHP 同様、本実装ではカウント表示は対象外（トグルのみ） |

## 次フェーズ

**フェーズ 8: mypage コンテキスト**（最後の未移植 context）

- 出品商品 / お気に入り / 参加中の掲示板一覧の集約表示。お気に入りは本 context の `getUserLikedProductIds` を消費
- 暫定 `HomeController` の `/mypage` を本実装に置換
- フェーズ 6 で Native Query 妥協した売買履歴の再評価
