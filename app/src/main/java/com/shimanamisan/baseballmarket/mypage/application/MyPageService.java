package com.shimanamisan.baseballmarket.mypage.application;

import com.shimanamisan.baseballmarket.like.application.LikeService;
import com.shimanamisan.baseballmarket.message.application.MessageService;
import com.shimanamisan.baseballmarket.message.domain.Board;
import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.product.domain.Product;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * マイページの集約表示を担う Orchestrator サービス。
 *
 * 旧 PHP MyPage\Application\MyPageService（getMyProducts / getMyLikes / getMyBoards）を移植。
 * 自前の永続化は持たず、product / like / message の application API を ID 値連携で束ねる。
 * 他 context のドメインへ直接アクセスはせず、context 境界は各 application サービス経由で守る。
 */
@Service
@Transactional(readOnly = true)
public class MyPageService {

  private final ProductService productService;
  private final LikeService likeService;
  private final MessageService messageService;

  public MyPageService(
      ProductService productService, LikeService likeService, MessageService messageService) {
    this.productService = productService;
    this.likeService = likeService;
    this.messageService = messageService;
  }

  /** ログインユーザーのマイページ表示に必要な3集約を取得する。 */
  public MyPageView getMyPage(int userId) {
    List<Product> ownedProducts = productService.findByOwner(userId);
    List<Product> likedProducts = resolveLikedProducts(userId);
    List<MyPageView.BoardSummary> boards = resolveBoards(userId);
    return new MyPageView(ownedProducts, likedProducts, boards);
  }

  /** お気に入り商品ID（新しい順）を product の表示情報へ解決する。並び順は ProductService 側で維持される。 */
  private List<Product> resolveLikedProducts(int userId) {
    List<Integer> likedProductIds = likeService.getUserLikedProductIds(userId);
    if (likedProductIds.isEmpty()) {
      return List.of();
    }
    return productService.findByIds(likedProductIds);
  }

  /** 参加掲示板（更新日時降順）を商品名・相手ユーザーID と組み合わせた表示用サマリへ変換する。 */
  private List<MyPageView.BoardSummary> resolveBoards(int userId) {
    List<Board> boards = messageService.findParticipatingBoards(userId);
    if (boards.isEmpty()) {
      return List.of();
    }

    List<Integer> productIds = boards.stream().map(Board::getProductId).distinct().toList();
    Map<Integer, String> productNameById = new HashMap<>();
    for (Product p : productService.findByIds(productIds)) {
      productNameById.put(p.getId(), p.getProductName());
    }

    List<MyPageView.BoardSummary> summaries = new ArrayList<>(boards.size());
    for (Board b : boards) {
      summaries.add(
          new MyPageView.BoardSummary(
              b.getId(),
              b.getProductId(),
              productNameById.get(b.getProductId()),
              b.getPartnerUserId(userId),
              b.getUpdatedAt()));
    }
    return summaries;
  }
}
