# 2026-05-11 23:43 フェーズ 6: product コンテキスト（出品/一覧/詳細/検索/売買履歴）

## 背景

フェーズ 4–5 で user コンテキストが完成し、ログインユーザーが各種設定を編集できる状態になったが、肝心の「マーケット」としての中核 — 商品の出品・閲覧・検索 — が無いままだった。本フェーズで product コンテキストを構築し、ようやく旧 `baseball-market` の主軸機能が揃う。

着手前の状態:

- `/`（トップ）は暫定 `HomeController` がプレースホルダ表示
- 出品、一覧、詳細、販売履歴のいずれも未実装
- 画像アップロード基盤（`ImageStorage`）はフェーズ 5 で構築済み → 本フェーズで再利用

本フェーズが完成すれば、Spring 版でも「ユーザー登録 → ログイン → 商品出品 → 一覧表示 → 詳細閲覧」という旧 PHP の主動線が一通り回せる。次のフェーズ 7（like / message）と組み合わせると、購入フローまで成立する。

## 着手前の調査

旧 PHP の Product 関連コードを精査し、移植仕様を確定:

| 旧 PHP ファイル | 抽出した仕様 |
| --- | --- |
| `Product/Domain/{ProductName,Price,ProductImage}.php` | 255 文字 / 0–99,999,999 円 / パスは空文字 NG。`Price.format()` は `¥1,000` 形式 |
| `Product/Domain/Product.php` | ビジネスメソッド `canPurchase(buyerId)`（自分の商品は不可）/ `markAsSold()` / `canEdit(userId)` |
| `Product/Application/ProductService.php` | コメント 500 字以内、category/maker は `>0` 必須、画像未指定時は既存値を維持 |
| `Product/Infrastructure/ProductRepositoryImpl.php` | 検索は **ホワイトリスト方式の動的 SQL**（sort=1→price ASC、sort=2→DESC、それ以外→デフォルト）、ページネーション 20 件 |
| `findSoldProductsByUserId` | products + boards + user_profiles の JOIN で「売却済み商品 + 購入者名」を取得 |
| `Product/Presentation/ProductDetailController.php` | 購入 POST → sold_out_flg=1 + boards INSERT → /msg.php?b_id=... へリダイレクト（**Board 作成は message context の責務**） |

→ 購入フローのうち **board 作成は本フェーズの管轄外** と判断。フェーズ 7（message context）にまわす。本フェーズでは `ProductService.markAsSold` のみ用意し、Controller には購入ボタンを仮設置するに留めた。

## 変更内容

### 1. Value Objects（4 ファイル, product/domain/）

| クラス | 形 | ポイント |
| --- | --- | --- |
| `ProductId` | record(long) | `temporary()` / `isTemporary()`。永続化境界で int に変換 |
| `ProductName` | record(String) | 255 文字制約、空白 NG |
| `Price` | record(int) | 0–99,999,999、`format()` で `¥1,000` 形式 |
| `ProductImage` | record(String path) | パスが空でないことのみ検証（MIME / サイズは `ImageStorage` の責務） |

プラン段階では `CategoryId` / `MakerId` も VO 化する想定だったが、実装してみると JPA の `Integer` で十分かつ付加価値が薄かったため省略した（詳細は分析・判断 §5）。

### 2. Entities（3 ファイル, product/domain/）

| クラス | テーブル | 注意点 |
| --- | --- | --- |
| `Product` | products | `Integer id`、`Byte deleteFlg` / `soldOutFlg`（INT/TINYINT 互換、フェーズ 4 の学習を踏襲）、ビジネスメソッドを Entity 上に保持 |
| `Category` | categories | id/name/deleteFlg のみ、`category_name` 列を name にマップ |
| `Maker` | makers | 同上、`maker_name` 列 |

### 3. Repository（domain IF 3 + infrastructure 6 ファイル）

#### Domain
- `ProductRepository`, `CategoryRepository`, `MakerRepository`
- 補助 record: `SearchCriteria(categoryId, makerId, sortOrder, page, perPage)` / `SearchResult(items, totalCount, totalPages, currentPage)` / `SaleHistoryItem(...)`

#### Infrastructure
- `ProductJpaRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product>`
- `CategoryJpaRepository` / `MakerJpaRepository`（package-private、`findAllAlive` / `findByIdAlive` のみ）
- `ProductSpecifications.matches(SearchCriteria)` — `delete_flg=0` + categoryId/makerId の動的 Predicate
- `ProductRepositoryImpl` — Pageable は本クラスに閉じ込め、Sort は `sortOrder` から `Sort.Direction` を組み立てる
- `CategoryRepositoryImpl` / `MakerRepositoryImpl`

### 4. ProductService（product/application/）

`@Service` + クラスレベル `@Transactional`、read-only メソッドには `@Transactional(readOnly = true)`。メソッド一覧:

- `register(userId, ProductRegistrationCommand) → ProductId`
- `update(productId, userId, command)` — `findByIdAndOwner` で所有確認後、Entity の setter で dirty checking 更新
- `findById` / `findByOwner` / `search(SearchCriteria)`
- `listCategories` / `listMakers`
- `markAsSold(productId, buyerUserId)` — `canPurchase` チェック
- `deleteProduct(productId, userId)` — `canEdit` チェック
- `getSaleHistory(userId)`
- `validateCommon` — category/maker の `>0` チェックと存在チェックを共通化

### 5. Controllers + DTO（product/presentation/）

| クラス | URL | 認証 | 役割 |
| --- | --- | --- | --- |
| `ProductListController` | GET `/` | 公開 | 検索条件と一覧表示。Phase 6 から `/` は本 Controller が担当 |
| `ProductDetailController` | GET `/productDetail/{id}` | 公開 | 詳細表示。category/maker 名を取得して `Model` に詰める |
| `ProductRegistController` | GET/POST `/registProduct` | 必須 | 画像 3 枚 multipart、`ImageStorage` 再利用 |
| `SaleHistoryController` | GET `/tranSale` | 必須 | 売却済み商品 + 購入者名のリスト |

DTO: `ProductRegistRequest`（`@NotBlank` / `@Size` / `@Min` / `@NotNull` で Bean Validation）

### 6. HomeController の整理

`/` を ProductListController に譲渡。`HomeController` には `/mypage` 暫定マッピングのみ残存（フェーズ 8 で削除予定）。

### 7. Views（4 ファイル, templates/product/）

- `list.html` — カテゴリ / メーカー / ソートのセレクトボックス + ページネーション + 商品カードグリッド
- `detail.html` — 画像 3 枚ギャラリー + 購入ボタン（フェーズ 7 で有効化注記）
- `regist.html` — multipart、画像 3 枚アップロード、エラー表示
- `saleHistory.html` — 売却済リストと購入者名

## 検証（E2E）

`baseball-market_devcontainer_default` ネットワーク上で空 DB に対し:

| シナリオ | 結果 |
| --- | --- |
| カテゴリ × 4 / メーカー × 4 シード投入（utf8mb4 経由） | ✅ |
| signup → email verify → login（フェーズ 4–5 機能の回帰確認） | ✅ |
| GET `/registProduct`（要認証） | ✅ 200 + 「商品登録」タイトル |
| POST `/registProduct`（日本語商品名 `テストグローブ`、コメント込） | ✅ 302 → `/`、DB に UTF-8 で保存（バイト一致確認） |
| GET `/` | ✅ 「全 1 件」、商品名 / 価格 `¥15,000` 表示 |
| GET `/productDetail/1` | ✅ 商品名 + カテゴリ名「グローブ」+ メーカー名「ミズノ」+ コメント表示 |
| GET `/?c_id=2`（カテゴリ絞り込み・該当無し） | ✅ 「全 0 件」 |
| GET `/?c_id=1`（カテゴリ絞り込み・該当あり） | ✅ 「全 1 件」 |
| GET `/tranSale`（売却前） | ✅ 「販売履歴はまだありません」 |
| 手動で `sold_out_flg=1` + boards 行追加 → GET `/tranSale` | ✅ 商品名 / 価格表示、`/` 一覧にも `SOLD` バッジ |

## 分析・判断記録

### 1. 動的検索を Spring Data Specification + Pageable で実装

旧 PHP は `$conditions = ['delete_flg = 0']; if ($categoryId) ...` という文字列連結方式で WHERE 句を組み立てていた。プレースホルダー使用で SQL Injection は避けていたが、可読性は低い。

Spring 側では:
- `ProductSpecifications.matches(SearchCriteria)` で `Predicate` を組み立てる
- `JpaSpecificationExecutor<Product>` の `findAll(Specification, Pageable)` で実行
- `Sort` を `sortOrder` の値から switch 式で安全に決定

これで型安全な動的検索が手に入り、SQL Injection リスクはコンパイル時に消える。`Page<Product>` は `infrastructure` 内で `SearchResult` record に詰め替えて domain IF に返すため、Spring Data 型が外に漏れない（[architecture.md §5](../../.claude/architecture.md) の規約遵守）。

### 2. Sale History を Native Query で実装した妥協

`findSaleHistory` は products + boards + user_profiles の 3 表 JOIN。JPQL なら `Board` Entity と `UserProfile` Entity の参照が必要だが:

- `Board` Entity は **フェーズ 7 まで存在しない**
- `UserProfile` は user context の Entity で、context 越境参照は [architecture.md §3](../../.claude/architecture.md) で禁止

選択肢:
- A. Native SQL で取得し、record に手動マップ
- B. フェーズ 7 を先にやって Board Entity を作ってから実装
- C. user.application に MyPageService のような Orchestrator を作って、複数 context の Service を呼んで組み立てる

**判断: A を採用**。フェーズ 7 まで待つとブロッキング、C は本フェーズで Orchestrator を作るのは時期尚早（用途が単一）。Native SQL を ProductRepositoryImpl 内に閉じ込め、戻り値は `SaleHistoryItem` record にすることで、Application 層に Spring Data 型を漏らさず、テーブル名のハードコードもこのクラスだけに閉じる。

フェーズ 7 完了後に B または C への移行を再評価する（用途が増えるなら C、増えないなら B でも A のままでも可）。

### 3. Update は dirty checking、Insert は新規 Entity

旧 PHP `ProductService::updateProduct` は新しい Product インスタンスを生成して save に渡していた。Spring 側で同じことをすると、JPA は「id 指定の新規エンティティ」と認識してしまい、SELECT 後 UPDATE という不要な往復が増える可能性がある（id が DB 上に存在しないと INSERT を試みる）。

**採用方針:**
- 新規登録 (`register`): `new Product(...)` → `jpa.save(entity)`
- 更新 (`update`): `findByIdAndOwner(id, userId)` で managed Entity を取得 → setter で属性を更新 → トランザクション終了時に Hibernate dirty checking で UPDATE

これで「画像未指定なら既存値を維持」も `if (cmd.pic1Path() != null) existing.setPic1(...)` で素直に書ける。旧 PHP では「画像未指定時は existingProduct.getPic1() を渡す」というロジックが必要だったが、それが不要になった。

### 4. 購入ボタンはフェーズ 6 では仮設置のみ

`product/detail.html` の購入ボタンは `<button>` だけ置いて、`th:action="@{|/productDetail/${product.id}/purchase|}"` の POST 処理はフェーズ 7 で実装する。

理由: 旧 PHP の購入処理は **(a) products.sold_out_flg=1 更新 + (b) boards INSERT** の 2 段。(a) は本 Service の `markAsSold` で実装済みだが、(b) は message context の責務。両方が動かないと、購入後に `/msg/{boardId}` にリダイレクトしても 404 になり、ユーザー体験が中途半端になる。フェーズ 7 で MessageService が完成した時点で、Application 層オーケストレーションとして同時実装する。

テンプレートには「フェーズ 7 で有効化予定」と表示して、押されても何も起きない（404）状態を可視化した。

### 5. `CategoryId` / `MakerId` を VO 化しなかった

プラン段階では VO 化想定だったが、実装してみると:

- 旧 PHP も `int $categoryId` をそのまま引き回していて、VO の付加価値が薄かった
- JPA Entity の `Integer categoryId` フィールドは FK そのもの。VO 化すると Entity 側で `@Convert` を書く必要があり、コード量が増えるだけ
- バリデーションは「`> 0` かつ DB に存在する」のみで、`ProductService.validateCommon` で完結する

→ VO 化はスキップして `Integer` を使う方針に。後で複雑なロジックが追加されたら（例: 階層的カテゴリの導入）VO 化する余地は残る。

### 6. Spring Data の Sort と `sortOrder` の対応付け

旧 PHP は `sortOrder` を文字列マッピングしていた（1 → "ORDER BY price ASC"）。Spring 側では `Sort.by(Direction.ASC, "price")` を返す switch 式に置き換え:

```java
Sort sort = switch (c.sortOrder() == null ? 0 : c.sortOrder()) {
  case 1 -> Sort.by(Sort.Direction.ASC, "price");
  case 2 -> Sort.by(Sort.Direction.DESC, "price");
  default -> Sort.by(Sort.Direction.DESC, "id");
};
```

旧 PHP では sortOrder が 0 や指定なしの場合 ORDER BY が無く挿入順（id 昇順）になっていたが、新規追加が末尾に出るのは UX が悪いため、デフォルトを「id 降順 = 新着順」に変更した。**仕様微変更だがユーザー体験向上のため許容**（要レビュー）。

### 7. テストデータ投入の文字化け

検証中に MySQL CLI から直接 `INSERT INTO user_profiles ... '購入太郎'` した結果、二重 UTF-8 エンコードで保存されてしまい、画面に `è³¼å…¥å¤ªéƒŽ` と表示された。原因は docker exec のターミナル/シェル経由で日本語が CP1252 として解釈されたため。

**結論: アプリ経由でフォーム送信した日本語は正常に保存される**（`テストグローブ` で確認済み）。MySQL CLI からの直接 INSERT は `--default-character-set=utf8mb4` 指定が必要、という運用上の注意点として記録。

## 残るリスク・未確定事項

| 項目 | 状況 |
| --- | --- |
| 購入フロー (`POST /productDetail/{id}/purchase`) | フェーズ 7 で実装 |
| Sale History の Native Query 妥協 | フェーズ 7 で Board Entity 完成後に再評価 |
| ソートデフォルトを `id DESC` に変更した点 | 旧仕様からの軽微な逸脱、UX 改善のため許容 |
| プロダクト編集画面（GET/POST `/registProduct/{id}`） | 旧 PHP は同じ URL で `?p_id=` の有無で分岐していたが、フェーズ 6 では未実装。需要が出たら追加 |
| 商品削除画面 | プラン記載なし。旧 PHP では一覧から削除ボタンで `ProductService.deleteProduct` を呼ぶ仕組み。Service は実装済みだが Controller / View は未実装 |
| 画像ファイル名の hash 衝突確率 | UUID で実質ゼロ、考慮不要 |

## 次フェーズ

**フェーズ 7: like / message コンテキスト**

- `like`: `Like` Entity + `LikeService.toggle` + Ajax `POST /ajaxLike`（CSRF ヘッダで認証）
- `message`: `Board` / `Message` Entity、`MessageService.postMessage / listByProduct`、`/msg/{boardId}` 表示
- `/productDetail/{id}/purchase` POST: `MessageService.createBoard` + `ProductService.markAsSold` のオーケストレーション
- メッセージボード画面（旧 `msg.php`）
