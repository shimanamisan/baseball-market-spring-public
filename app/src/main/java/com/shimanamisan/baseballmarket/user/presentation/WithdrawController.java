package com.shimanamisan.baseballmarket.user.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/withdraw")
public class WithdrawController {

  private static final Logger log = LoggerFactory.getLogger(WithdrawController.class);

  private final UserService userService;

  public WithdrawController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public String show(Model model) {
    model.addAttribute("siteTitle", "退会");
    return "user/withdraw";
  }

  @PostMapping
  public String submit(
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response,
      RedirectAttributes redirect) {

    try {
      User user = userService
          .findByEmail(principal.getName())
          .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
      userService.withdraw(user.getUserId());

      // セッション破棄 + 認証クリア
      new SecurityContextLogoutHandler()
          .logout(request, response, SecurityContextHolder.getContext().getAuthentication());

      redirect.addFlashAttribute("msgSuccess", "退会が完了しました");
      return "redirect:/";
    } catch (ValidationException e) {
      log.warn("[withdraw] {}", e.getFirstError());
      redirect.addFlashAttribute("errorMessage", e.getFirstError());
      return "redirect:/withdraw";
    } catch (RuntimeException e) {
      log.error("[withdraw] エラー", e);
      redirect.addFlashAttribute("errorMessage", "エラーが発生しました。しばらく経ってからやり直してください。");
      return "redirect:/withdraw";
    }
  }
}
