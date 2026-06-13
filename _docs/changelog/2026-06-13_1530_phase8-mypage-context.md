# 2026-06-13 15:30 mypage コンテキスト（マイページ集約表示）

> **リプレース全 context 完了の節目**。旧 `baseball-market`（PHP / DDD）の最後の未移植機能であった `MyPage/` を Spring Boot へ移植した。これで `user` / `product` / `message` / `like` / `mypage` の全 Bounded Context が出揃い、PHP からのフルリプレースが機能面で一巡した。

## 背景

旧 PHP の `MyPage/Application/MyPageService`（`getMyProducts` / `getMyLikes` / `getMyBoards` / `getMyPageData`）は、`/mypage` で 3 集約を表示するための専用サービスだった。各メソッドが `DatabaseConnection` を直接握り、products / likes / boards を JOIN する生 SQL を投げていた（context という概念が無いため境界を跨ぐ JOIN が許されていた）。

Spring 版では context 境界を守るため、mypage は**自前の永続化を持たない Orchestrator** とし、product / like / message の各 application API を ID 値連携で束ねる構成にする。これは旧 PHP の素朴な JOIN を、DDD の境界づけられたコンテキストへ落とし込む設計判断である。

旧画面の構成（`MyPage/Presentation/views/mypage.php`）:

- ユーザー情報ヘッダ（出品件数 / お気に入り件数 / プロフィール編集・出品ボタン）
- 登録商品一覧（グリッド、SOLD 表示）
- 連絡掲示板一覧（商品名・メッセージ件数・更新日時・掲示板リンク）
- お気に入り商品一覧（グリッド、SOLD 表示）
- クイックアクション（出品 / 販売履歴 / パスワード変更 / 退会）

## 着手前の調査

- `HomeController`（`shared/infrastructure/web`）が暫定で `/mypage` を `shared/home` にマッピングしていた（フェーズ 8 で置換予定とコメントあり）。
- `ProductService` に出品一覧 `findByOwner(userId)`、`LikeService` に `getUserLikedProductIds(userId)`（新しい順の商品 ID 一覧、まさに mypage 消費用に用意済み）が既存。
- `MessageService` には「参加中の掲示板一覧」を返す API が無かった（`getBoardWithMessages` は単一掲示板のみ）。
- `Board` エンティティの実フィールドを実コードで確認: 出品者 `saleUser`、購入者 `buyUser`、削除フラグ `deleteFlg`(Byte)、更新日時 `updatedAt`。`isParticipant` / `getPartnerUserId` のビジネスメソッドあり。
- 既存 Controller テストは `@WebMvcTest` ではなく **plain Mockito で Controller メソッドを直接呼ぶ**スタイル（`MessageControllerTest` 参照）。本実装もこの慣習を踏襲した。

## 変更内容

### 1. message context への API 追加（後方互換の新規追加）

参加掲示板一覧を返す読み取り API を追加。Board の実フィールド名を確認した上で JPQL を記述し、message domain の境界は崩していない。

- `MessageRepository#findBoardsByParticipant(int userId)` を追加（domain IF）。
- `BoardJpaRepository#findByParticipantAlive`（package-private）:
  `where (b.saleUser = :userId or b.buyUser = :userId) and b.deleteFlg = 0 order by b.updatedAt desc`。
- `MessageRepositoryImpl#findBoardsByParticipant` で委譲。
- `MessageService#findParticipatingBoards(int userId)`（`@Transactional(readOnly = true)`）で `List<Board>` を公開。商品・相手ユーザーの表示情報は呼び出し元（mypage）が解決する旨を Javadoc に明記。

### 2. product context への API 追加（後方互換の新規追加）

お気に入りの「like → 商品 ID → product 表示情報」を N+1 なしで解決する一括取得 API。

- `ProductRepository#findByIdsAlive(List<Integer> ids)` を追加（domain IF）。並び順は保証しない（呼び出し元が整列する旨を Javadoc に明記）。
- `ProductJpaRepository#findByIdsAlive`: `where p.id in :ids and p.deleteFlg = 0`。**`delete_flg = 0` 除外により、お気に入りの削除済み商品は一覧から自然に消える**（旧 PHP の `p.delete_flg = 0` 条件と同挙動）。
- `ProductRepositoryImpl#findByIdsAlive`: 空リストは即 `List.of()`（JPA の `in ()` を踏まないための防御）。
- `ProductService#findByIds(List<Integer> ids)`（`@Transactional(readOnly = true)`）:
  リポジトリ結果を ID→Product の Map に積み、**渡された ID の並び順を維持**して返す。
  - **like の「新しい順」を保つ**ためのキー実装。`findByIdsAlive` は IN 句で順不同に返るため、Map 経由で要求順へ再整列する。
  - 削除済み等で返らない ID は Map に無く、結果から自然に脱落する。
  - 空入力はリポジトリを呼ばず即空返し。

### 3. mypage context（新設・application / presentation の 2 層のみ）

mypage は自前のドメイン・永続化を持たないため、**domain / infrastructure 層を作らない**（architecture の「集約表示専任 Orchestrator」判断。ユーザー承認済み）。

#### application（`mypage/application/`）

- `MyPageView`（record）— 集約 DTO。
  - `List<Product> ownedProducts` / `List<Product> likedProducts` / `List<BoardSummary> boards`。
  - 内包 record `BoardSummary(int boardId, int productId, String productName, Integer partnerUserId, LocalDateTime updatedAt)`。掲示板一覧は **商品名・相手・更新日時**のみの軽量表示（メッセージ件数集計はユーザー確定事項により省略）。`productName` は削除済み等で取得できなければ null。
- `MyPageService`（`@Service` + クラス `@Transactional(readOnly = true)`）
  - DI: `ProductService` / `LikeService` / `MessageService`（他 context の application API のみを束ね、他 context のドメイン・リポジトリへは直接触れない）。
  - `getMyPage(int userId)`:
    - 出品 = `productService.findByOwner(userId)`
    - お気に入り = `likeService.getUserLikedProductIds(userId)` → 空でなければ `productService.findByIds(ids)`（新しい順維持・削除済み除外）
    - 掲示板 = `messageService.findParticipatingBoards(userId)` → 掲示板の商品 ID を distinct で集めて `productService.findByIds` で商品名を一括解決（N+1 回避）し、`Board.getPartnerUserId(userId)` で視点に応じた相手 ID を導出して `BoardSummary` へ変換。

#### presentation（`mypage/presentation/`）

- `MyPageController`（`@Controller`）`GET /mypage`:
  - `Principal` → `userService.findByEmail` → `User`（既存 Controller と同パターン）。未解決時は `ValidationException`。
  - `myPageService.getMyPage(user.getId())` の結果を `myPage` 属性へ。`currentUser` / `siteTitle` も載せ、`mypage/index` を返す。

### 4. テンプレート `templates/mypage/index.html`

旧 `mypage.php` を Thymeleaf 化。`shared/layout` の `header` / `footer` 断片を流用。出品・お気に入りはグリッド + SOLD オーバーレイ、掲示板は商品名・相手・更新日時・掲示板リンク。クイックアクションは既存ルート（`/registProduct` `/tranSale` `/passEdit` `/withdraw` `/profEdit` `/productDetail/{id}` `/message?b_id=`）へリンク。出力は `th:text` で自動エスケープ。

### 5. 暫定 `HomeController` の撤去

`/mypage` を本実装へ置換したことで `HomeController` は空になるため**削除**した（`/` は既に `ProductListController` が担当。他からの参照無しを確認済み）。`shared/layout` の「マイページ」リンク（`@{/mypage}`）が本実装で有効化される。`shared/home.html` テンプレートは孤立するが無害なため本フェーズでは残置。

## 検証

TDD（🔴Red → 🟢Green → 🔵Refactor）で実装。

- `MessageServiceTest`: `findParticipatingBoards` がリポジトリ結果（更新日時降順）をそのまま返す / 0 件で空返し。
- `ProductServiceTest`（新規）: `findByIds` の ID 順維持・空入力でリポジトリ非呼び出し・削除済み（未返却）ID の除外。
- `MyPageServiceTest`（中核）: 3 集約の束ね / お気に入りの like 順維持 / 全 0 件（お気に入り ID 空時は `findByIds` を呼ばない）/ 削除済みお気に入りの除外 / 出品者・購入者視点での相手 ID 導出 / 各 service へ正しい userId を渡す。
- `MyPageControllerTest`: 正常時のモデル属性・テンプレート名 / ユーザー未解決時は集約取得を呼ばず例外。
- 境界値・異常系（0 件、削除済み、他人視点）を網羅。

**テスト実行**（このサンドボックスに JDK 無し。`eclipse-temurin:21-jdk` コンテナで `/build` 隔離実行）:

- 関連テスト（mypage / ProductServiceTest / MessageServiceTest）: **全件 BUILD SUCCESSFUL**。
- 全件実行（`SPRING_DATASOURCE_URL` で `baseball-market-spring-db` を指定）: **60 件中 59 件成功**。唯一の失敗は `BaseballMarketSpringApplicationTests.contextLoads()` で、ad-hoc コンテナが compose ネットワーク外のため Flyway が DB ホスト名を解決できない `UnknownHostException`（既知の環境問題、`_docs/2026-06-13_0942_replacement-progress-analysis.md` 記載）。本質的には全テストグリーン。

## 分析・判断記録

### フェーズ 6 売買履歴の Native Query を現状維持した理由

`ProductRepositoryImpl#findSaleHistory` は products INNER JOIN boards LEFT JOIN user_profiles のネイティブクエリのまま残した。これを JPQL 化するには product context に Board / UserProfile のエンティティマッピングを持ち込む必要があり、**他 context（message / user）のテーブルを product のドメインに引き込んで context 境界を侵食する**。したがって Native Query 採用は「妥協」ではなく、context 境界を守るための**合理的選択**である。mypage の掲示板商品名解決を `productService.findByIds` の ID 連携で組んだのも同じ思想（JOIN ではなく application 層での ID 束ね）。

### mypage に domain / infrastructure を作らなかった理由

mypage は固有の集約・不変条件・永続化を一切持たず、既存 3 context の読み取り API を束ねて表示用 DTO に詰めるだけの Orchestrator である。空の domain / infrastructure を機械的に置くのは過剰で、`MyPageView`（DTO）と `MyPageService`（束ね）と `MyPageController` で必要十分。これは architecture の「集約が小さいうちは過剰な抽象化を避ける」方針に沿う（ユーザー承認済み）。

### お気に入り並び順の維持を product 側に置いた理由

`findByIds` の IN 句は順不同で返るため、「like の新しい順」を維持する責務は ID 列を知る側＝呼び出し元に必要。mypage で並べ替えてもよいが、`findByIds` の「渡した順を返す」契約自体が他用途でも有用なため product application 層に置き、Javadoc とテストで契約を明文化した。

## 残るリスク / 申し送り

- 掲示板一覧の「相手」は現状 `ユーザー#{partnerUserId}` 表示。ユーザー名表示にするには user context の一括取得 API（`findByIds` 相当）が必要。承認済みスコープ（mypage は product / like / message のみ束ねる）に収めるため今回は ID 表示に留めた。UX 改善余地として申し送る。
- `shared/home.html` が孤立。整理フェーズで削除候補。
- 掲示板・お気に入りとも件数が増えた場合のページング未対応（旧 PHP も全件表示だったため挙動は踏襲）。

## 次フェーズ

機能リプレースは本フェーズで一巡（全 context 出揃い）。以降は以下が候補:

- 横断的な整理（孤立テンプレート削除、`@WebMvcTest` への統一検討、Native Query の扱い再評価）。
- 非機能（ページング、ログ整備、`contextLoads` を含む統合テストの環境整備）。
- リリース準備（`develop` → `main` の直接マージ + `vX.Y.Z` タグ）。
