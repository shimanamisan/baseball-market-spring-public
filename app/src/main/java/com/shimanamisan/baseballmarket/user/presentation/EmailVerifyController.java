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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/emailVerify")
public class EmailVerifyController {

  private static final Logger log = LoggerFactory.getLogger(EmailVerifyController.class);

  private final UserService userService;

  public EmailVerifyController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public String verify(@RequestParam(value = "token", required = false) String token, Model model) {
    Map<String, String> errors = new HashMap<>();
    boolean success = false;

    if (token == null || token.isBlank()) {
      errors.put("common", "無効な確認リンクです。");
    } else {
      try {
        userService.verifyEmail(token);
        success = true;
      } catch (ValidationException e) {
        errors.put("common", e.getFirstError());
        log.warn("[emailVerify] 確認エラー: {}", e.getFirstError());
      } catch (RuntimeException e) {
        log.error("[emailVerify] 予期しないエラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }

    model.addAttribute("siteTitle", "メールアドレス確認");
    model.addAttribute("errors", errors);
    model.addAttribute("success", success);
    return "user/emailVerify";
  }
}
