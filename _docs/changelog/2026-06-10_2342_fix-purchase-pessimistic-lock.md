# 2026-06-10 23:42 購入処理に悲観ロックを導入し二重購入を防止

> フェーズ 7（message context + 購入フロー）後の改善。Issue #11 / PR #16 由来。changelog 未作成だったため遡って記録。

## 背景

フェーズ 7 で開通した購入フロー（`PurchaseService.purchase`）は、商品取得 → `canPurchase` 判定 → `markAsSold` → 掲示板作成、を 1 トランザクションで行う。

しかし取得が**非ロックの `findById`** だったため、同一商品に対して 2 つの購入リクエストがほぼ同時に来ると、双方が「まだ売れていない」状態を読んで `canPurchase` を通過し、**両方が売却済み化 + 掲示板作成まで進む二重購入**が成立しうる（旧 PHP では DB トランザクション任せだった箇所）。

`V1__init.sql` のスキーマ・MySQL（InnoDB）は行ロックをサポートしており、Spring Data JPA の `@Lock(PESSIMISTIC_WRITE)` で `SELECT ... FOR UPDATE` を発行できる。これを購入導線にだけ適用する。

## 変更内容

### 1. ロック付き取得を domain Repository IF に追加

- `product/domain/ProductRepository` に `findByIdForUpdate(ProductId)` を追加。
  - JavaDoc に「購入処理用の行ロック取得」「必ず application 層の `@Transactional` 内から呼ぶ（ロックはトランザクション終了で解放）」を明記。

### 2. infrastructure で PESSIMISTIC_WRITE を発行

- `product/infrastructure/ProductJpaRepository` に `findByIdAliveForUpdate` を追加。
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id and p.deleteFlg = 0")
  Optional<Product> findByIdAliveForUpdate(Integer id);
  ```
- `ProductRepositoryImpl.findByIdForUpdate` で上記へ委譲（`long→int` 変換はこのクラスに閉じ込めたまま）。

### 3. PurchaseService をロック付き取得に切替

- `purchase()` の取得を `findById` → **`findByIdForUpdate`** に変更。同一商品への同時購入が直列化され、後続トランザクションはロック解放後に `sold_out_flg=1` を読んで `canPurchase` で弾かれる。
- エラーメッセージを競合状況に合わせて細分化:
  - 売却済み（競合に敗れた場合を含む）→ 「申し訳ありません。この商品はすでに売却済みです」
  - 自己出品 → 「自分が出品した商品は購入できません」
  - `isSoldOut()` を優先判定し、競合敗者に的確な文言を返す。

### 4. テスト

- `PurchaseServiceTest` を全面的にロック付き取得前提へ更新。
  - 追加: 「商品はロック付き取得(`findByIdForUpdate`)で読み、非ロックの `findById` は使わない」（`verify(...).findByIdForUpdate` + `verify(never()).findById`）。
  - 既存の自己購入拒否・売却済み拒否ケースに `hasMessageContaining` を追加し、新メッセージ文言を検証。

## 検証

| 項目 | 結果 |
| --- | --- |
| `PurchaseServiceTest`（Mockito、ロック取得の呼び分け + 文言） | ✅ 全緑 |

※ 実 DB 上の `FOR UPDATE` 直列化はユニットテストでは検証範囲外（ロックメソッドが呼ばれることと、`canPurchase` による弾きを担保）。

## 分析・判断記録

### なぜ楽観ロックでなく悲観ロックか

購入は「読んだ瞬間の状態（未売却）」に基づいて副作用（売却済み化 + 掲示板作成）を確定するクリティカルな更新で、競合時はリトライでなく**即座に弾きたい**。楽観ロック（version 列 + リトライ）より、対象行を `FOR UPDATE` で押さえて直列化する悲観ロックの方が、購入のような短時間・低頻度・原子性重視の操作に素直。`PurchaseService` の `@Transactional` 境界内に閉じるため、ロック保持区間も最小。

### MySQL / InnoDB 前提

`@Lock(PESSIMISTIC_WRITE)` は InnoDB で `SELECT ... FOR UPDATE` に変換される。`delete_flg = 0` 条件付きで対象行のみをロックし、テーブル全体はロックしない。

## 残るリスク・未確定事項

| 項目 | 状況 |
| --- | --- |
| 実 DB での同時購入の結合テスト | 未整備。ユニットでは呼び分けまで。将来 `@SpringBootTest` + 並行スレッドでの検証余地 |
| ロック待ちタイムアウトの方針 | 既定（InnoDB `innodb_lock_wait_timeout`）任せ。購入は短区間のため当面据え置き |
