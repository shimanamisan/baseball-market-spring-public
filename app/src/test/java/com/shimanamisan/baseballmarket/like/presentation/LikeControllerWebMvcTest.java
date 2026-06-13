package com.shimanamisan.baseballmarket.like.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shimanamisan.baseballmarket.like.application.LikeService;
import com.shimanamisan.baseballmarket.like.application.ToggleResult;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LikeController の HTTP 境界（Controller ローカル @ExceptionHandler / JSON シリアライズ / CSRF 連携）を
 * 仕様として固定する WebMvc スライステスト。振る舞いの本体は LikeControllerTest（純 Mockito）でカバーし、
 * ここでは「productId 欠落・型不正時の JSON」「message=null が JSON に出ない」を検証する。
 */
@WebMvcTest(LikeController.class)
class LikeControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LikeService likeService;
  @MockitoBean private UserService userService;

  @Test
  @DisplayName("productId 欠落時は error/パラメータが不正です を返す")
  @WithMockUser(username = "user@example.com")
  void missingProductId() throws Exception {
    mockMvc
        .perform(post("/likes/toggle").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response").value("error"))
        .andExpect(jsonPath("$.message").value("パラメータが不正です"));
  }

  @Test
  @DisplayName("productId が整数でない場合は error/商品IDが不正です を返す")
  @WithMockUser(username = "user@example.com")
  void nonIntegerProductId() throws Exception {
    mockMvc
        .perform(post("/likes/toggle").param("productId", "abc").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response").value("error"))
        .andExpect(jsonPath("$.message").value("商品IDが不正です"));
  }

  @Test
  @DisplayName("add 成功時は message を含まない JSON を返す（@JsonInclude NON_NULL）")
  @WithMockUser(username = "user@example.com")
  void addOmitsNullMessage() throws Exception {
    User user = org.mockito.Mockito.mock(User.class);
    when(user.getId()).thenReturn(42);
    when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(likeService.toggleLike(anyInt(), anyInt())).thenReturn(ToggleResult.ADD);

    mockMvc
        .perform(post("/likes/toggle").param("productId", "30").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.response").value("add"))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  @Test
  @DisplayName("CSRF トークン無しの POST は 403 を返す（CSRF 保護有効の確認）")
  @WithMockUser(username = "user@example.com")
  void rejectsWithoutCsrf() throws Exception {
    mockMvc
        .perform(post("/likes/toggle").param("productId", "30"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("レスポンスの Content-Type は application/json")
  @WithMockUser(username = "user@example.com")
  void respondsJson() throws Exception {
    mockMvc
        .perform(post("/likes/toggle").with(csrf()))
        .andExpect(content().contentTypeCompatibleWith("application/json"));
  }
}
