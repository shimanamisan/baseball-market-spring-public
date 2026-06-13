package com.shimanamisan.baseballmarket.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.like.application.LikeService;
import com.shimanamisan.baseballmarket.message.application.MessageService;
import com.shimanamisan.baseballmarket.message.domain.Board;
import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.product.domain.Product;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

  private static final int USER_ID = 42;

  @Mock private ProductService productService;
  @Mock private LikeService likeService;
  @Mock private MessageService messageService;

  private MyPageService myPageService;

  @BeforeEach
  void setUp() {
    myPageService = new MyPageService(productService, likeService, messageService);
  }

  private Product product(int id, String name) {
    Product p = new Product(name, 1, 1, 1000, null, USER_ID, "pic" + id + ".jpg", null, null);
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private Board board(int id, int saleUser, int buyUser, int productId) {
    Board b = new Board(saleUser, buyUser, productId);
    ReflectionTestUtils.setField(b, "id", id);
    return b;
  }

  @Test
  @DisplayName("出品・お気に入り・参加掲示板の3集約を束ねて返す")
  void aggregatesThreeSections() {
    // Arrange
    Product owned = product(1, "出品グローブ");
    when(productService.findByOwner(USER_ID)).thenReturn(List.of(owned));

    when(likeService.getUserLikedProductIds(USER_ID)).thenReturn(List.of(5, 6));
    Product liked5 = product(5, "お気に入りバット");
    Product liked6 = product(6, "お気に入りスパイク");
    when(productService.findByIds(List.of(5, 6))).thenReturn(List.of(liked5, liked6));

    Board b = board(9, USER_ID, 100, 7);
    when(messageService.findParticipatingBoards(USER_ID)).thenReturn(List.of(b));
    Product boardProduct = product(7, "取引中ボール");
    when(productService.findByIds(List.of(7))).thenReturn(List.of(boardProduct));

    // Act
    MyPageView view = myPageService.getMyPage(USER_ID);

    // Assert
    assertThat(view.ownedProducts()).extracting(Product::getId).containsExactly(1);
    assertThat(view.likedProducts()).extracting(Product::getId).containsExactly(5, 6);
    assertThat(view.boards()).hasSize(1);
    MyPageView.BoardSummary summary = view.boards().get(0);
    assertThat(summary.boardId()).isEqualTo(9);
    assertThat(summary.productName()).isEqualTo("取引中ボール");
    assertThat(summary.partnerUserId()).isEqualTo(100);
    assertThat(summary.updatedAt()).isEqualTo(b.getUpdatedAt());
  }

  @Test
  @DisplayName("お気に入りは like が返した ID 順（新しい順）を維持する")
  void keepsLikeOrder() {
    when(productService.findByOwner(USER_ID)).thenReturn(List.of());
    when(messageService.findParticipatingBoards(USER_ID)).thenReturn(List.of());

    when(likeService.getUserLikedProductIds(USER_ID)).thenReturn(List.of(8, 3, 5));
    when(productService.findByIds(List.of(8, 3, 5)))
        .thenReturn(List.of(product(8, "a"), product(3, "b"), product(5, "c")));

    MyPageView view = myPageService.getMyPage(USER_ID);

    assertThat(view.likedProducts()).extracting(Product::getId).containsExactly(8, 3, 5);
  }

  @Test
  @DisplayName("各集約が0件でも空の集約 DTO を返す")
  void handlesAllEmpty() {
    when(productService.findByOwner(USER_ID)).thenReturn(List.of());
    when(likeService.getUserLikedProductIds(USER_ID)).thenReturn(List.of());
    when(messageService.findParticipatingBoards(USER_ID)).thenReturn(List.of());

    MyPageView view = myPageService.getMyPage(USER_ID);

    assertThat(view.ownedProducts()).isEmpty();
    assertThat(view.likedProducts()).isEmpty();
    assertThat(view.boards()).isEmpty();
    // お気に入り ID が空なら product 解決は呼ばない
    verify(productService, never()).findByIds(anyList());
  }

  @Test
  @DisplayName("お気に入り商品が削除済みで product から返らない場合は一覧から除外される")
  void excludesDeletedLikedProduct() {
    when(productService.findByOwner(USER_ID)).thenReturn(List.of());
    when(messageService.findParticipatingBoards(USER_ID)).thenReturn(List.of());

    when(likeService.getUserLikedProductIds(USER_ID)).thenReturn(List.of(5, 6));
    // 6 は削除済みのため product が返さない
    when(productService.findByIds(List.of(5, 6))).thenReturn(List.of(product(5, "生存")));

    MyPageView view = myPageService.getMyPage(USER_ID);

    assertThat(view.likedProducts()).extracting(Product::getId).containsExactly(5);
  }

  @Test
  @DisplayName("出品掲示板の相手は購入者、購入掲示板の相手は出品者になる")
  void resolvesBoardPartnerFromPerspective() {
    when(productService.findByOwner(USER_ID)).thenReturn(List.of());
    when(likeService.getUserLikedProductIds(USER_ID)).thenReturn(List.of());

    Board asSeller = board(1, USER_ID, 200, 11); // 自分が出品者 → 相手は購入者 200
    Board asBuyer = board(2, 300, USER_ID, 12); // 自分が購入者 → 相手は出品者 300
    when(messageService.findParticipatingBoards(USER_ID))
        .thenReturn(List.of(asSeller, asBuyer));
    lenient()
        .when(productService.findByIds(List.of(11, 12)))
        .thenReturn(List.of(product(11, "p11"), product(12, "p12")));

    MyPageView view = myPageService.getMyPage(USER_ID);

    assertThat(view.boards()).extracting(MyPageView.BoardSummary::partnerUserId)
        .containsExactly(200, 300);
  }

  @Test
  @DisplayName("各 service には常に対象 userId が渡る")
  void passesUserIdToEachService() {
    when(productService.findByOwner(USER_ID)).thenReturn(List.of());
    when(likeService.getUserLikedProductIds(USER_ID)).thenReturn(List.of());
    when(messageService.findParticipatingBoards(USER_ID)).thenReturn(List.of());

    myPageService.getMyPage(USER_ID);

    verify(productService).findByOwner(USER_ID);
    verify(likeService).getUserLikedProductIds(USER_ID);
    verify(messageService).findParticipatingBoards(USER_ID);
  }
}
