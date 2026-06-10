package com.shimanamisan.baseballmarket.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.message.application.MessageService;
import com.shimanamisan.baseballmarket.message.domain.BoardId;
import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

  private static final int SELLER = 10;
  private static final int BUYER = 20;
  private static final int PRODUCT_ID = 30;
  private static final int BOARD_ID = 5;

  @Mock private ProductRepository productRepository;
  @Mock private MessageService messageService;

  @InjectMocks private PurchaseService purchaseService;

  private Product product(int ownerUserId) {
    return new Product("グローブ", 1, 1, 5000, "美品", ownerUserId, null, null, null);
  }

  @Test
  @DisplayName("購入時に商品を売却済みにし、出品者と購入者の掲示板を作成する")
  void purchaseMarksSoldAndCreatesBoard() {
    Product product = product(SELLER);
    when(productRepository.findByIdForUpdate(any(ProductId.class))).thenReturn(Optional.of(product));
    when(messageService.createBoard(SELLER, BUYER, PRODUCT_ID)).thenReturn(BoardId.fromLong(BOARD_ID));

    BoardId result = purchaseService.purchase(PRODUCT_ID, BUYER);

    assertThat(result.value()).isEqualTo(BOARD_ID);
    assertThat(product.isSoldOut()).isTrue();
    verify(messageService).createBoard(SELLER, BUYER, PRODUCT_ID);
  }

  @Test
  @DisplayName("二重購入対策: 商品はロック付き取得(findByIdForUpdate)で読み、非ロックの findById は使わない")
  void readsProductWithPessimisticLock() {
    Product product = product(SELLER);
    when(productRepository.findByIdForUpdate(any(ProductId.class))).thenReturn(Optional.of(product));
    when(messageService.createBoard(SELLER, BUYER, PRODUCT_ID)).thenReturn(BoardId.fromLong(BOARD_ID));

    purchaseService.purchase(PRODUCT_ID, BUYER);

    verify(productRepository).findByIdForUpdate(any(ProductId.class));
    verify(productRepository, never()).findById(any(ProductId.class));
  }

  @Test
  @DisplayName("自分の出品した商品は購入できない")
  void rejectsOwnProduct() {
    Product product = product(BUYER); // 出品者 == 購入者
    when(productRepository.findByIdForUpdate(any(ProductId.class))).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> purchaseService.purchase(PRODUCT_ID, BUYER))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("自分が出品した商品");
    assertThat(product.isSoldOut()).isFalse();
    verify(messageService, never()).createBoard(anyInt(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("売却済みの商品は購入できない（競合に敗れた場合もこの経路で売却済みメッセージを返す）")
  void rejectsSoldProduct() {
    Product product = product(SELLER);
    product.markAsSold();
    when(productRepository.findByIdForUpdate(any(ProductId.class))).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> purchaseService.purchase(PRODUCT_ID, BUYER))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("売却済み");
    verify(messageService, never()).createBoard(anyInt(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("商品が存在しなければ購入できない")
  void rejectsMissingProduct() {
    when(productRepository.findByIdForUpdate(any(ProductId.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> purchaseService.purchase(PRODUCT_ID, BUYER))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("商品が見つかりません");
    verify(messageService, never()).createBoard(anyInt(), anyInt(), anyInt());
  }
}
