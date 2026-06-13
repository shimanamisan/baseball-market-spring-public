package com.shimanamisan.baseballmarket.mypage.application;

import com.shimanamisan.baseballmarket.product.domain.Product;
import java.time.LocalDateTime;
import java.util.List;

/**
 * マイページの集約表示 DTO。
 *
 * mypage context は自前の永続化を持たず、他 context（product / like / message）の application API を
 * ID 連携で束ねた結果を本 DTO に詰めて presentation へ渡す。
 *
 * @param ownedProducts ログインユーザーの出品商品一覧（新しい順）
 * @param likedProducts お気に入り商品一覧（like の新しい順を維持。削除済み商品は除外済み）
 * @param boards 参加中の掲示板サマリ一覧（更新日時降順）
 */
public record MyPageView(
    List<Product> ownedProducts, List<Product> likedProducts, List<BoardSummary> boards) {

  /**
   * 掲示板一覧の表示用サマリ。商品エンティティそのものでなく表示に必要な軽量値のみを保持する。
   *
   * @param boardId 掲示板ID（詳細画面への遷移に使う）
   * @param productId 取引対象の商品ID
   * @param productName 取引対象の商品名（削除済み等で取得できない場合は null）
   * @param partnerUserId ログインユーザーから見た取引相手のユーザーID
   * @param updatedAt 掲示板の最終更新日時
   */
  public record BoardSummary(
      int boardId,
      int productId,
      String productName,
      Integer partnerUserId,
      LocalDateTime updatedAt) {}
}
