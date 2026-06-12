package com.shimanamisan.baseballmarket.message.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.message.application.MessageService;
import com.shimanamisan.baseballmarket.product.application.ProductService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

  private static final int BOARD_ID = 7;
  private static final int CURRENT_USER_ID = 42;
  private static final String EMAIL = "buyer@example.com";

  @Mock private MessageService messageService;
  @Mock private ProductService productService;
  @Mock private UserService userService;
  @Mock private RedirectAttributes redirect;
  @Mock private Principal principal;
  @Mock private User currentUser;

  private MessageController controller;

  @BeforeEach
  void setUp() {
    controller = new MessageController(messageService, productService, userService);
    when(principal.getName()).thenReturn(EMAIL);
    when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(currentUser));
    when(currentUser.getId()).thenReturn(CURRENT_USER_ID);
  }

  private SendMessageRequest form(String msg) {
    SendMessageRequest form = new SendMessageRequest();
    form.setMsg(msg);
    return form;
  }

  @Test
  @DisplayName("バリデーションエラー時は入力本文を submittedMsg としてフラッシュ属性に載せ再入力を保持する")
  void preservesInputOnValidationError() {
    // Arrange
    String submitted = "x".repeat(501); // 500 文字超過を想定した入力
    doThrow(new ValidationException("本文は500文字以内で入力してください"))
        .when(messageService)
        .sendMessage(anyInt(), anyInt(), eq(submitted));

    // Act
    String view = controller.send(BOARD_ID, form(submitted), principal, redirect);

    // Assert
    verify(redirect).addFlashAttribute("submittedMsg", submitted);
    verify(redirect).addFlashAttribute("errorMessage", "本文は500文字以内で入力してください");
    org.assertj.core.api.Assertions.assertThat(view).isEqualTo("redirect:/message?b_id=" + BOARD_ID);
  }

  @Test
  @DisplayName("正常送信時は submittedMsg / errorMessage を載せず PRG リダイレクトのみ行う")
  void doesNotPreserveInputOnSuccess() {
    // Arrange: sendMessage は例外を投げない（正常）

    // Act
    String view = controller.send(BOARD_ID, form("こんにちは"), principal, redirect);

    // Assert
    verify(redirect, never()).addFlashAttribute(eq("submittedMsg"), org.mockito.ArgumentMatchers.any());
    verify(redirect, never()).addFlashAttribute(eq("errorMessage"), org.mockito.ArgumentMatchers.any());
    verify(messageService).sendMessage(BOARD_ID, CURRENT_USER_ID, "こんにちは");
    org.assertj.core.api.Assertions.assertThat(view).isEqualTo("redirect:/message?b_id=" + BOARD_ID);
  }
}
