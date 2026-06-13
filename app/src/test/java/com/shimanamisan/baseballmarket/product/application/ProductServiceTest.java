package com.shimanamisan.baseballmarket.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.product.domain.CategoryRepository;
import com.shimanamisan.baseballmarket.product.domain.MakerRepository;
import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private MakerRepository makerRepository;

  @InjectMocks private ProductService productService;

  /** id を持つ Product を最小構成で生成するヘルパ。 */
  private Product productWithId(int id) {
    Product p = new Product("商品" + id, 1, 1, 1000, null, 1, null, null, null);
    org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  @Test
  @DisplayName("findByIds: 空リストを渡したらリポジトリを呼ばず空を返す")
  void returnsEmptyForEmptyInput() {
    List<Product> result = productService.findByIds(List.of());

    assertThat(result).isEmpty();
    verify(productRepository, never()).findByIdsAlive(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  @DisplayName("渡した ID の並び順を維持して返す（like の新しい順を保つため）")
  void preservesRequestedIdOrder() {
    // Arrange: リポジトリは並び順を保証しない想定で、要求と異なる順で返す
    when(productRepository.findByIdsAlive(List.of(3, 1, 2)))
        .thenReturn(List.of(productWithId(1), productWithId(2), productWithId(3)));

    // Act
    List<Product> result = productService.findByIds(List.of(3, 1, 2));

    // Assert: 要求した 3,1,2 の順序になる
    assertThat(result).extracting(Product::getId).containsExactly(3, 1, 2);
  }

  @Test
  @DisplayName("削除済み等でリポジトリから返らない ID は結果から除外される")
  void excludesIdsNotReturnedByRepository() {
    // Arrange: 2 は delete 済みでリポジトリが返さない
    when(productRepository.findByIdsAlive(List.of(3, 2, 1)))
        .thenReturn(List.of(productWithId(1), productWithId(3)));

    // Act
    List<Product> result = productService.findByIds(List.of(3, 2, 1));

    // Assert: 2 は欠落し、残りは要求順を保つ
    assertThat(result).extracting(Product::getId).containsExactly(3, 1);
  }
}
