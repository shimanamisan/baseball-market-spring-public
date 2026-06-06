package com.shimanamisan.baseballmarket.user.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ログイン画面（GET のみ）。
 *
 * POST /login は Spring Security の UsernamePasswordAuthenticationFilter が処理し、
 * 成功時は SecurityConfig の defaultSuccessUrl("/", true) に従って "/" へリダイレクトする。
 */
@Controller
@RequestMapping("/login")
public class LoginController {

  @GetMapping
  public String show(
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "logout", required = false) String logout,
      Model model) {

    model.addAttribute("siteTitle", "ログイン");
    if (error != null) {
      model.addAttribute("errorMessage", "メールアドレスまたはパスワードが違います");
    }
    if (logout != null) {
      model.addAttribute("msgSuccess", "ログアウトしました");
    }
    return "user/login";
  }
}
