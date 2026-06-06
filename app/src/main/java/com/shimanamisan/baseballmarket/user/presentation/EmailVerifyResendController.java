package com.shimanamisan.baseballmarket.user.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/emailVerifyResend")
public class EmailVerifyResendController {

  private static final Logger log = LoggerFactory.getLogger(EmailVerifyResendController.class);

  private final UserService userService;

  public EmailVerifyResendController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public String show(Model model) {
    model.addAttribute("siteTitle", "確認メール再送信");
    model.addAttribute("errors", model.containsAttribute("errors") ? model.getAttribute("errors") : new HashMap<>());
    if (!model.containsAttribute("email")) {
      model.addAttribute("email", "");
    }
    return "user/emailVerifyResend";
  }

  @PostMapping
  public String submit(
      @RequestParam(value = "email", defaultValue = "") String email,
      RedirectAttributes redirect,
      Model model) {

    Map<String, String> errors = new HashMap<>();
    String trimmed = email == null ? "" : email.trim();

    if (trimmed.isEmpty()) {
      errors.put("email", "メールアドレスを入力してください。");
    } else {
      try {
        userService.resendVerificationEmail(trimmed);
        redirect.addFlashAttribute("msgSuccess",
            "確認メールを再送信しました。メールをご確認ください。");
        return "redirect:/login";
      } catch (ValidationException e) {
        errors.put("common", e.getFirstError());
        log.warn("[emailVerifyResend] バリデーションエラー: {} email={}", e.getFirstError(), trimmed);
      } catch (RuntimeException e) {
        log.error("[emailVerifyResend] 予期しないエラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }

    model.addAttribute("siteTitle", "確認メール再送信");
    model.addAttribute("errors", errors);
    model.addAttribute("email", trimmed);
    return "user/emailVerifyResend";
  }
}
