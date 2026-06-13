package com.shimanamisan.baseballmarket.like.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.like.domain.LikeRepository;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

  private static final int USER_ID = 10;
  private static final int PRODUCT_ID = 30;

  @Mock private LikeRepository likeRepository;

  @InjectMocks private LikeService likeService;

  @Nested
  @DisplayName("toggleLike")
  class ToggleLike {

    @Test
    @DisplayName("未登録なら追加して ADD を返す")
    void addsWhenAbsent() {
      when(likeRepository.exists(USER_ID, PRODUCT_ID)).thenReturn(false);

      ToggleResult result = likeService.toggleLike(USER_ID, PRODUCT_ID);

      assertThat(result).isEqualTo(ToggleResult.ADD);
      verify(likeRepository).add(USER_ID, PRODUCT_ID);
      verify(likeRepository, never()).remove(USER_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("登録済みなら削除して REMOVE を返す")
    void removesWhenPresent() {
      when(likeRepository.exists(USER_ID, PRODUCT_ID)).thenReturn(true);

      ToggleResult result = likeService.toggleLike(USER_ID, PRODUCT_ID);

      assertThat(result).isEqualTo(ToggleResult.REMOVE);
      verify(likeRepository).remove(USER_ID, PRODUCT_ID);
      verify(likeRepository, never()).add(USER_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("userId が 0 以下なら ValidationException")
    void rejectsNonPositiveUserId() {
      assertThatThrownBy(() -> likeService.toggleLike(0, PRODUCT_ID))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("ユーザーID");
      verify(likeRepository, never()).exists(0, PRODUCT_ID);
    }

    @Test
    @DisplayName("productId が 0 以下なら ValidationException")
    void rejectsNonPositiveProductId() {
      assertThatThrownBy(() -> likeService.toggleLike(USER_ID, 0))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("商品ID");
      verify(likeRepository, never()).exists(USER_ID, 0);
    }
  }

  @Nested
  @DisplayName("isLiked")
  class IsLiked {

    @Test
    @DisplayName("リポジトリの存在判定をそのまま返す")
    void delegatesToRepository() {
      when(likeRepository.exists(USER_ID, PRODUCT_ID)).thenReturn(true);

      assertThat(likeService.isLiked(USER_ID, PRODUCT_ID)).isTrue();
    }

    @Test
    @DisplayName("userId が 0 以下なら false（未ログインゲスト等は未いいね扱い）")
    void returnsFalseForNonPositiveUserId() {
      assertThat(likeService.isLiked(0, PRODUCT_ID)).isFalse();
      verify(likeRepository, never()).exists(0, PRODUCT_ID);
    }
  }

  @Nested
  @DisplayName("getUserLikedProductIds")
  class GetUserLikedProductIds {

    @Test
    @DisplayName("ユーザーのお気に入り商品ID一覧を返す")
    void returnsProductIds() {
      when(likeRepository.findProductIdsByUserId(USER_ID)).thenReturn(List.of(30, 31));

      List<Integer> result = likeService.getUserLikedProductIds(USER_ID);

      assertThat(result).containsExactly(30, 31);
    }

    @Test
    @DisplayName("userId が 0 以下なら空リスト")
    void returnsEmptyForNonPositiveUserId() {
      assertThat(likeService.getUserLikedProductIds(0)).isEmpty();
      verify(likeRepository, never()).findProductIdsByUserId(0);
    }
  }
}
