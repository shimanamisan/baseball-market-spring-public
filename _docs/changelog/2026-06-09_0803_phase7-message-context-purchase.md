# 2026-06-09 08:03 フェーズ 7: message コンテキスト + 購入フロー開通

## 背景

フェーズ 6 で product コンテキストが完成し、「ユーザー登録 → ログイン → 出品 → 一覧 → 詳細」までは回るようになったが、**購入フローが未配線のまま積み残されていた**。

着手前の状態:

- `product/detail.html` の購入ボタンは `/productDetail/{id}/purchase` に POST するが、**対応コントローラが存在しない**（ボタン文言も「フェーズ 7 で有効化予定」）
- `ProductService.markAsSold` は実装済みだが**呼び出し元が無く**、コメントに「boards 作成は message context（フェーズ 7）で対応する想定」と明記されていた（フェーズ 6 changelog §4 の判断どおり）
- DB スキーマ（`boards` / `messages`）は `V1__init.sql` に既存

旧 PHP の購入処理（`Product/Presentation/ProductDetailController::handlePurchase`）は **「商品を売却済みに更新 + boards を作成」を 1 トランザクション**で行い、作成した掲示板（`msg.php?b_id=`）へ遷移していた。

本フェーズで message コンテキストを新設し購入フローを開通することで、旧 PHP の主動線 **「出品 → 閲覧 → 購入 → 連絡」** が一通り回るようになる。`like` は独立しているため後追いで問題ない（リプレース順序どおり）。

### スコープの確定（着手前にユーザー合意）

| 論点 | 決定 |
| --- | --- |
| 購入調整（product↔message を跨ぐ）の置き場所 | **専用 `PurchaseService`**（ProductService を汚さず、跨ぎを 1 箇所に集約） |
| 今回の実装範囲 | **購入 → 掲示板 → メッセージのコア導線**。マイページの掲示板一覧集約は**フェーズ 8（mypage）に委譲** |

## 着手前の調査

旧 PHP の Message 関連コードと現行 Spring 実装・スキーマを精査し、移植仕様を確定:

| 旧 PHP / 既存 Spring | 抽出した仕様 |
| --- | --- |
| `Message/Domain/MessageContent.php` | 本文は空 NG・500 文字以内 |
| `Message/Domain/Board.php` | `isParticipant` / `getPartnerUserId` / `isSeller` / `isBuyer` のビジネスメソッド |
| `Message/Application/MessageService.php` | createBoard（sale≠buy）、sendMessage（参加者チェック → 相手を宛先に）、getBoardWithMessages（参加者以外は拒否） |
| `Product/Presentation/ProductDetailController::handlePurchase` | 購入 = **(a) sold_out_flg=1 + (b) boards INSERT を 1 トランザクション** → `msg.php?b_id=` へ遷移、フラッシュ「購入しました！相手と連絡を取りましょう！」 |
| `V1__init.sql` | `boards` / `messages` は既存。**`messages.bord_id` は旧タイポを意図的に保持** |
| `ProductService.markAsSold` | `Product.canPurchase`（自分の商品不可 + 売切れ不可）+ `markAsSold` を既に保持 |
| `UserService` | `findById(UserId)` / `findByEmail(String)` あり（掲示板表示の相手情報取得に流用可） |

→ 新規マイグレーション不要。権限エラーは既存方針に合わせ `ValidationException`（`GlobalExceptionHandler` が `shared/error` に流す）で表現し、専用 Unauthorized 例外は新設しない方針とした。

## 変更内容

### 1. message コンテキスト（DDD 一式・新設）

#### domain（`message/domain/`）

| クラス | 形 | ポイント |
| --- | --- | --- |
| `BoardId` / `MessageId` | record(long) | `ProductId`/`UserId` と同形（`fromLong`/`temporary`/`isTemporary`） |
| `MessageContent` | record(String) | 空 NG・500 文字以内。失敗は `ValidationException("msg", …)` |
| `Board` | `@Entity(boards)` | `Integer id`・`Byte deleteFlg`（INT/TINYINT 互換、既存流儀踏襲）。`isParticipant`/`getPartnerUserId`/`isSeller`/`isBuyer` を Entity 上に保持。出品者・購入者・商品は **ID 値で保持**（context 越境参照を作らない） |
| `Message` | `@Entity(messages)` | **`@Column(name="bord_id")` で旧タイポ列を吸収**（フィールド名は `bordId`）。`isSentBy`/`isReceivedBy` |
| `MessageRepository` | interface | `saveBoard` / `findBoardById` / `findMessagesByBoardId` / `saveMessage` |

#### infrastructure（`message/infrastructure/`）

- `BoardJpaRepository`（package-private、`findByIdAlive` で `delete_flg=0`）
- `MessageJpaRepository`（`findByBordIdAlive`、`send_at ASC` 順）
- `MessageRepositoryImpl`（`@Repository`、2 つの JPA を委譲。`ProductRepositoryImpl` 同様 `long→int` 変換を本クラスに閉じ込め）

#### application（`message/application/`）

- `MessageService`（`@Service` + クラス `@Transactional`、read 系は `@Transactional(readOnly=true)`）
  - `createBoard(saleUserId, buyUserId, productId)` — sale≠buy のみ検証。**商品存在チェックは呼び出し元が担保**し、本サービスは message context 内に閉じる
  - `getBoardWithMessages(boardId, currentUserId)` — 未存在 / 非参加者を拒否し `BoardMessages` を返す
  - `sendMessage(boardId, fromUserId, content)` — 参加者チェック → `getPartnerUserId` で宛先導出 → `MessageContent` 検証 → 保存
- `BoardMessages` record（`Board` + `List<Message>`）

#### presentation（`message/presentation/`）

| クラス | URL | 役割 |
| --- | --- | --- |
| `MessageController` | GET `/message?b_id=` | 掲示板 + メッセージ表示。商品（ProductService）・相手/自分（UserService）を application API 経由で組み立て |
| 〃 | POST `/message/{boardId}/messages` | 送信 → PRG で同 URL へ。`ValidationException` はフラッシュエラーで戻す |
| `SendMessageRequest` | — | フォーム入力（`msg`）。本文検証は `MessageContent` に委譲 |

- View: `templates/message/msg.html`（旧 `msg.php` を Thymeleaf 化。`shared/layout` の header/footer 流用、`from_user == currentUserId` で吹き出し左右出し分け、`th:text` 自動エスケープで XSS 対策、最下部スクロール JS）

### 2. 購入フロー（product↔message オーケストレーション）

- `product/application/PurchaseService`（`@Service @Transactional`）— **跨ぎ調整の集約点**。`ProductRepository`（自 context）+ `MessageService`（他 context の application API）を DI。
  `purchase(productId, buyerUserId)`: 商品取得 → `canPurchase` → `markAsSold` → `messageService.createBoard(...)` → `BoardId` 返却。
  `MessageService.createBoard` は `@Transactional` 既定（REQUIRED）で**同一トランザクションに参加**するため、どちらかが失敗すれば両方ロールバックされる（旧 PHP の `beginTransaction`/`commit`/`rollBack` を Spring の宣言的トランザクションで再現）。
- `product/presentation/ProductPurchaseController` — POST `/productDetail/{id}/purchase`。成功でフラッシュ「購入しました！相手と連絡を取りましょう！」+ `/message?b_id=` へ。`ValidationException` は詳細へ戻す。
- `templates/product/detail.html` — 購入ボタンを有効化（文言修正 + `confirm()` ダイアログ + エラーフラッシュ表示）。
- `product/application/ProductService` — **未使用化した `markAsSold` を削除**（購入導線は `PurchaseService` が担い、ロジックは `Product` エンティティの `canPurchase`/`markAsSold` に集約。重複なし）。

### 3. セキュリティ

- `shared/infrastructure/security/SecurityConfig` — `/productDetail/**` は GET 公開だが、`POST /productDetail/*/purchase` を **要認証**として permitAll より先に追加。`/message/**` は既存の `anyRequest().authenticated()` で保護済み。

### 4. テスト（t-wada 流 TDD: JUnit5 + AssertJ + Mockito）

| テスト | 検証内容 |
| --- | --- |
| `MessageContentTest` | 正常 / 空 / null / 500 境界 / 501 超過 |
| `BoardTest` | `isParticipant` / `getPartnerUserId`（seller・buyer・第三者=null）/ `isSeller`・`isBuyer` |
| `MessageServiceTest` | createBoard（sale==buy 拒否・正常）、getBoardWithMessages（未存在・非参加者・正常）、sendMessage（相手算出・非参加者拒否・空本文拒否） |
| `PurchaseServiceTest` | 購入正常（売却済み化 + createBoard 呼び出し）、自己購入拒否、売切れ拒否、商品未存在 |

## 検証

| 項目 | 結果 |
| --- | --- |
| 新規ユニットテスト（domain + application、23 ケース） | ✅ 全緑 |
| `BaseballMarketSpringApplicationTests`（`@SpringBootTest` コンテキスト起動） | ✅ Flyway 移行・`ddl-auto=validate`（新 Entity のスキーマ整合）・全 Bean 配線を確認 |
| `boards` / `messages` 実スキーマと Entity マッピングの突合 | ✅ 全カラム一致（`int→Integer` / `tinyint→Byte` / `text→String` / `datetime→LocalDateTime`） |

実行は devcontainer の `baseball-market-spring-app` コンテナ内（Java 21 / Gradle 8.13）。後述の `db` 別名衝突を回避するため、接続先を `baseball-market-spring-db` に明示して全 24 テスト緑を確認した。

## 分析・判断記録

### 1. 跨ぎ調整を専用 `PurchaseService` に集約

購入は product（売却済み化）と message（掲示板作成）の 2 context にまたがる。選択肢:

- A. **専用 `PurchaseService` を新設**（採用）
- B. `ProductService.purchase()` に `MessageService` を DI して追加
- C. Controller で 2 サービスを順に呼ぶ

C は別トランザクションになり原子性が崩れる（売却済みになったのに掲示板が無い／その逆の半端状態）ため却下。B は ProductService の責務が広がる。A は跨ぎを 1 クラスに閉じ込められ、`@Transactional` 既定（REQUIRED）で `createBoard` を同一トランザクションに巻き込んで原子性を確保できる。`architecture.md` の「context 跨ぎは application 層で ID 連携」にも合致する。

### 2. message domain を product/user から独立させる

`createBoard` は商品の存在確認をしない（呼び出し元の `PurchaseService` が `findById` 済み）。`MessageController` の表示も、商品・ユーザー情報は **message context ではなく product/user の application API から取得**して組み立てる。これにより message domain は他 context の Entity を一切参照せず、境界が保たれる（旧 PHP の `MessageService` は ProductRepository/UserRepository を直接 DI していたが、その密結合は持ち込まない）。

### 3. `messages.bord_id` の旧タイポを保持

`V1__init.sql` のコメントどおり、旧データ互換のため列名 `bord_id`（`board_id` ではない）を維持。Java フィールド名は正しく `bordId` とし、`@Column(name="bord_id")` で吸収。列名の修正は将来の独立したマイグレーション（V2 以降）で慎重に行う領域とし、本フェーズでは触れない。

### 4. 掲示板表示の遅延ロードは OSIV に依存

掲示板ヘッダで相手/自分の `UserProfile`（username/age/prefecture/pic）を表示する。これは `User` の遅延関連で、テンプレートレンダリング時に解決される。Spring Boot 既定の **OSIV（`spring.jpa.open-in-view=true`）** が有効（`application.properties` で無効化していない）であり、既存ページと同じ前提で動く。将来 OSIV を切る場合は表示用 DTO を application 層で組み立てる必要があるため、留意点として記録。

### 5. 権限エラーを `ValidationException` で表現

旧 PHP は `UnauthorizedException` / `NotFoundException` を使い分けていたが、Spring 側は既存方針（`GlobalExceptionHandler` が `ValidationException` → `shared/error`）に合わせ、専用例外を増やさなかった。「掲示板が見つかりません」「アクセスする権限がありません」も `ValidationException` で表現。粒度の細分化が必要になった時点で再検討する。

### 6. 【環境問題】`db` ホスト名の別名衝突で `contextLoads` が素では失敗

`./gradlew test` を素のまま実行すると `contextLoads()` だけが失敗した。原因は **コード起因ではなく環境**:

- 外部ネットワーク `nginx-proxy-manager-network` 上に **`db` という別名のコンテナが複数**存在（旧 PHP の `bb-market-db` 等）。app コンテナ内で `getent hosts db` が **3 つの IP を返す**状態
- Spring アプリが別プロジェクトの MySQL に接続してしまい、`Access denied for user 'bbuser'`（SQLSTATE 28000 / 1045）で Flyway 接続が失敗。**Entity 検証より前の接続段階**でのエラー

切り分け: 正しい `baseball-market-spring-db` を指す URL を `SPRING_DATASOURCE_URL` で上書きすると全テスト緑。つまり Entity マッピングやコードは正しく、純粋にネットワーク上のホスト名衝突。

**恒久対策の候補（別件・要対応）**: `application.properties` の DB ホストを一意なコンテナ名（`baseball-market-spring-db`）にする / app・db を専用ネットワークに分離する / 外部ネットワーク参加コンテナの `db` 別名を見直す。本フェーズの実装対象外として残課題に記載。

## 残るリスク・未確定事項

| 項目 | 状況 |
| --- | --- |
| `db` 別名衝突（環境） | 素の `./gradlew test` で `contextLoads` が落ちる。DB ホスト名の一意化 or ネットワーク分離で恒久対応（別件） |
| マイページの掲示板一覧 | フェーズ 8（mypage context）で実装。それまで掲示板へは「購入直後リダイレクト」か URL 直打ちで到達 |
| `messages.bord_id` のタイポ列 | 当面保持。リネームは将来の独立マイグレーションで |
| 掲示板表示の OSIV 依存 | OSIV を切る判断をする場合は表示用 DTO 化が必要 |
| メッセージのリアルタイム更新 | 旧 PHP 同様ページリロード方式。WebSocket 等は対象外 |

## 次フェーズ

**フェーズ 8: mypage コンテキスト**

- 出品商品 / お気に入り / **参加中の掲示板一覧**の集約表示（旧 `MyPage/Application/MyPageService` 相当）
- 暫定 `HomeController` の `/mypage` を本実装に置換
- フェーズ 6 で Native Query 妥協した売買履歴の再評価（Board Entity が揃ったため JPQL 化 or Orchestrator 化を検討）
- `like` コンテキスト（Ajax いいね）も未着手のため順次
