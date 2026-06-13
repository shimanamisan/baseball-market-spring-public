package com.shimanamisan.baseballmarket.mypage.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.mypage.application.MyPageService;
import com.shimanamisan.baseballmarket.mypage.application.MyPageView;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class MyPageControllerTest {

  private static final int USER_ID = 42;
  private static final String EMAIL = "user@example.com";

  @Mock private MyPageService myPageService;
  @Mock private UserService userService;
  @Mock private Principal principal;
  @Mock private User currentUser;

  private MyPageController controller;

  @BeforeEach
  void setUp() {
    controller = new MyPageController(myPageService, userService);
  }

  @Test
  @DisplayName("ログインユーザーの userId でマイページを取得しモデルに載せ、テンプレート名を返す")
  void rendersMyPage() {
    // Arrange
    when(principal.getName()).thenReturn(EMAIL);
    when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(currentUser));
    when(currentUser.getId()).thenReturn(USER_ID);
    MyPageView view = new MyPageView(List.of(), List.of(), List.of());
    when(myPageService.getMyPage(USER_ID)).thenReturn(view);
    Model model = new ConcurrentModel();

    // Act
    String template = controller.index(principal, model);

    // Assert
    assertThat(template).isEqualTo("mypage/index");
    assertThat(model.getAttribute("myPage")).isSameAs(view);
    assertThat(model.getAttribute("currentUser")).isSameAs(currentUser);
    assertThat(model.getAttribute("siteTitle")).isEqualTo("マイページ");
    verify(myPageService).getMyPage(USER_ID);
  }

  @Test
  @DisplayName("ユーザーが見つからない場合は集約取得を呼ばず ValidationException を投げる")
  void rejectsUnknownUser() {
    // Arrange
    when(principal.getName()).thenReturn(EMAIL);
    when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());
    Model model = new ConcurrentModel();

    // Act / Assert
    assertThatThrownBy(() -> controller.index(principal, model))
        .isInstanceOf(ValidationException.class);
    verifyNoInteractions(myPageService);
  }
}
