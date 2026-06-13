---
name: like-context-conventions
description: like context（フェーズ8a）レビューで確認した設計判断・既知の前提。context跨ぎ・Ajax例外・物理削除・認証ユーザー解決の方針
metadata:
  type: project
---

フェーズ8a「like context（お気に入り Ajax）」の実装でレビュー済みの設計判断。

**context 跨ぎ方針（合意済み・良い）**
- like context は user/product のエンティティに依存せず int userId/productId のみ受け取る。
- LikeRepository は商品エンティティではなく商品ID一覧（findProductIdsByUserId）を返し境界を維持。後続 8b(mypage) が消費。
- 商品存在検証は like 側で行わず FK 制約に委ねる（旧 PHP 踏襲）。

**物理削除採用**
- likes は trigger toggle 用の関連レコード。論理削除でなく物理 DELETE。delete_flg 列は V1 に存在するが exists 判定では参照せず行の有無で判定。
- message context（会話履歴=論理削除）とは性質が異なる。一貫性違反ではない。

**Ajax 例外ハンドリング方針**
- GlobalExceptionHandler は HTML(ModelAndView) を返すため Ajax/JSON に不適。
- LikeController 内で ValidationException / RuntimeException を catch し JSON 化、Controller ローカル @ExceptionHandler で MissingServletRequestParameter / MethodArgumentTypeMismatch を JSON 化。
- 注意点: CSRF/認可で 403 になった場合は Spring の HTML エラーページが返るため、like.js の `res.json()` は失敗し catch に落ちる（黙殺）。permitAll + Controller no_login 判定方針なので 401 経路は無いが、CSRF 期限切れ時の UX は要注意。

**認証ユーザー解決の共通パターン（user/message/like で統一）**
- Controller で Principal を受け `userService.findByEmail(principal.getName())` で User を取得し getId() を使用。
- /likes/** は SecurityConfig で permitAll、未ログインは principal==null → {"response":"no_login"}（旧 UX 踏襲）。

**既知の環境問題**
- `./gradlew test` で BaseballMarketSpringApplicationTests.contextLoads() が db ホスト UnknownHostException で fail するのは既知の環境問題（Flyway が MySQL 未到達）。新規 like テストの失敗ではない。
</content>
</invoke>
