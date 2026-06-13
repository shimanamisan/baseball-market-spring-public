package com.shimanamisan.baseballmarket.like.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.like.application.LikeService;
import com.shimanamisan.baseballmarket.like.application.ToggleResult;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeControllerTest {

  private static final int CURRENT_USER_ID = 42;
  private static final int PRODUCT_ID = 30;
  private static final String EMAIL = "user@example.com";

  @Mock private LikeService likeService;
  @Mock private UserService userService;
  @Mock private Principal principal;
  @Mock private User currentUser;

  private LikeController controller;

  @BeforeEach
  void setUp() {
    controller = new LikeController(likeService, userService);
  }

  private void loggedIn() {
    when(principal.getName()).thenReturn(EMAIL);
    when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(currentUser));
    when(currentUser.getId()).thenReturn(CURRENT_USER_ID);
  }

  @Test
  @DisplayName("未ログイン（principal が null）なら no_login を返す")
  void returnsNoLoginWhenUnauthenticated() {
    LikeToggleResponse res = controller.toggle(PRODUCT_ID, null);

    assertThat(res.response()).isEqualTo("no_login");
    verify(likeService, never()).toggleLike(anyInt(), anyInt());
  }

  @Test
  @DisplayName("追加された場合は add を返す")
  void returnsAddOnAdd() {
    loggedIn();
    when(likeService.toggleLike(CURRENT_USER_ID, PRODUCT_ID)).thenReturn(ToggleResult.ADD);

    LikeToggleResponse res = controller.toggle(PRODUCT_ID, principal);

    assertThat(res.response()).isEqualTo("add");
  }

  @Test
  @DisplayName("削除された場合は remove を返す")
  void returnsRemoveOnRemove() {
    loggedIn();
    when(likeService.toggleLike(CURRENT_USER_ID, PRODUCT_ID)).thenReturn(ToggleResult.REMOVE);

    LikeToggleResponse res = controller.toggle(PRODUCT_ID, principal);

    assertThat(res.response()).isEqualTo("remove");
  }

  @Test
  @DisplayName("ValidationException は error レスポンスとメッセージを返す")
  void returnsErrorOnValidationException() {
    loggedIn();
    when(likeService.toggleLike(CURRENT_USER_ID, PRODUCT_ID))
        .thenThrow(new ValidationException("商品IDが不正です"));

    LikeToggleResponse res = controller.toggle(PRODUCT_ID, principal);

    assertThat(res.response()).isEqualTo("error");
    assertThat(res.message()).isEqualTo("商品IDが不正です");
  }
}
