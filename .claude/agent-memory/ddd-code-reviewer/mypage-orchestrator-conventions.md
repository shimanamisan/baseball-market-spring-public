---
name: mypage-orchestrator-conventions
description: mypage context（フェーズ8）の設計判断。domain/infra を持たない Orchestrator・ID連携・命名揺れ・認可前提
metadata:
  type: project
---

フェーズ8「mypage context（マイページ集約表示）」のレビューで確認・合意した設計判断。

**Orchestrator パターン（ユーザー承認済み・DDD上許容）**
- mypage は自前永続化を持たず domain/infrastructure 層を作らない。application(MyPageService) と presentation(MyPageController) のみ。
- 他 context のドメイン/リポジトリには触れず、ProductService/LikeService/MessageService の application API だけを DI して ID 値連携で束ねる。
- 注意（許容済み）: MyPageService/MyPageView は他 context の **エンティティ**（product.domain.Product / message.domain.Board）を戻り値・引数で直接参照する。厳密な context 分離なら mypage 専用 DTO に詰め替えるのが理想だが、表示用途・読み取り専用・小規模のため現状許容。Product/Board に破壊的変更が入ると mypage が連鎖被弾する点だけ留意。

**ID 順維持の共通イディオム（ProductService#findByIds）**
- like の「新しい順」商品ID列を Map<id,Product> 経由で要求順に整列して返す。リポジトリ(findByIdsAlive)は順序非保証、delete_flg=0 で返らない ID は自然脱落。N+1 回避。空入力はリポジトリ呼ばず List.of()。後続で「ID列→エンティティ順序維持解決」が必要なら同パターンを踏襲。

**命名の揺れ（既知・指摘済み / 要追従）**
- 同一概念の参加掲示板取得が層ごとに名前が違う: application=findParticipatingBoards / domain IF=findBoardsByParticipant / JpaRepository=findByParticipantAlive。意味は通るが将来 grep 追跡しづらい。新規メソッドは層をまたいで命名を揃える方が望ましい。

**認可前提（HomeController 削除後）**
- /mypage は SecurityConfig の permitAll リストに無く anyRequest().authenticated() で保護される。よって MyPageController は Principal が non-null 前提でよい（null チェック不要は妥当）。暫定 /mypage を持っていた shared の HomeController は本実装で置換し削除済み。/ ルートは別途 permitAll かつ formLogin defaultSuccessUrl。

**Native Query 維持の判断（フェーズ6 売買履歴）**
- ProductRepositoryImpl#findSaleHistory は products×boards×user_profiles の native query のまま維持。JPQL 化すると product context に Board/UserProfile を持ち込み境界侵食するため。context 境界優先で native 許容。
