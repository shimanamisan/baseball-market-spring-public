package com.shimanamisan.baseballmarket.user.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.shared.infrastructure.security.TokenGenerator;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.PasswordResetToken;
import jakarta.servlet.http.HttpSession;
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

/**
 * パスワード再発行（旧 PHP の passRemindSend / passRemindRecieve）。
 *
 * 旧仕様踏襲: トークンは DB に保存せず HttpSession に保持する。
 * - SESSION_TOKEN_ATTR: 認証キー本体（VO）
 * - SESSION_EMAIL_ATTR: 対象メールアドレス
 */
@Controller
public class PasswordRemindController {

  private static final Logger log = LoggerFactory.getLogger(PasswordRemindController.class);

  private static final String SESSION_TOKEN_ATTR = "passReset.token";
  private static final String SESSION_EMAIL_ATTR = "passReset.email";

  private final UserService userService;

  public PasswordRemindController(UserService userService) {
    this.userService = userService;
  }

  // ---------- /passRemindSend ----------

  @GetMapping("/passRemindSend")
  public String showSend(Model model) {
    model.addAttribute("siteTitle", "パスワード再発行");
    if (!model.containsAttribute("errors")) {
      model.addAttribute("errors", new HashMap<>());
    }
    if (!model.containsAttribute("email")) {
      model.addAttribute("email", "");
    }
    return "user/passRemindSend";
  }

  @PostMapping("/passRemindSend")
  public String submitSend(
      @RequestParam(value = "email", defaultValue = "") String email,
      HttpSession session,
      RedirectAttributes redirect,
      Model model) {

    Map<String, String> errors = new HashMap<>();
    String trimmed = email == null ? "" : email.trim();
    if (trimmed.isEmpty()) {
      errors.put("email", "メールアドレスを入力してください");
    } else {
      try {
        PasswordResetToken token = userService.requestPasswordReset(trimmed);
        session.setAttribute(SESSION_TOKEN_ATTR, token);
        session.setAttribute(SESSION_EMAIL_ATTR, trimmed);
        redirect.addFlashAttribute("msgSuccess", "パスワード再発行メールを送信しました");
        return "redirect:/passRemindRecieve";
      } catch (ValidationException e) {
        errors.put("common", e.getFirstError());
        log.warn("[passRemindSend] {}", e.getFirstError());
      } catch (RuntimeException e) {
        log.error("[passRemindSend] エラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }
    model.addAttribute("siteTitle", "パスワード再発行");
    model.addAttribute("errors", errors);
    model.addAttribute("email", trimmed);
    return "user/passRemindSend";
  }

  // ---------- /passRemindRecieve ----------

  @GetMapping("/passRemindRecieve")
  public String showReceive(HttpSession session, Model model) {
    if (session.getAttribute(SESSION_TOKEN_ATTR) == null) {
      return "redirect:/passRemindSend";
    }
    model.addAttribute("siteTitle", "認証キー入力");
    if (!model.containsAttribute("errors")) {
      model.addAttribute("errors", new HashMap<>());
    }
    return "user/passRemindRecieve";
  }

  @PostMapping("/passRemindRecieve")
  public String submitReceive(
      @RequestParam(value = "token", defaultValue = "") String inputToken,
      HttpSession session,
      RedirectAttributes redirect,
      Model model) {

    PasswordResetToken stored = (PasswordResetToken) session.getAttribute(SESSION_TOKEN_ATTR);
    String email = (String) session.getAttribute(SESSION_EMAIL_ATTR);
    if (stored == null || email == null) {
      return "redirect:/passRemindSend";
    }

    Map<String, String> errors = new HashMap<>();
    String trimmed = inputToken == null ? "" : inputToken.trim();
    if (trimmed.isEmpty()) {
      errors.put("token", "認証キーを入力してください");
    } else if (!stored.matches(trimmed)) {
      errors.put("token", "認証キーが違います");
    } else if (!stored.isValid()) {
      errors.put("token", "認証キーの有効期限が切れています");
    } else {
      try {
        String newPassword = TokenGenerator.generate(8);
        userService.resetPassword(email, newPassword);
        session.removeAttribute(SESSION_TOKEN_ATTR);
        session.removeAttribute(SESSION_EMAIL_ATTR);
        redirect.addFlashAttribute("msgSuccess", "パスワードを再発行しました。メールをご確認ください。");
        return "redirect:/login";
      } catch (ValidationException e) {
        errors.put("common", e.getFirstError());
        log.warn("[passRemindRecieve] {}", e.getFirstError());
      } catch (RuntimeException e) {
        log.error("[passRemindRecieve] エラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }
    model.addAttribute("siteTitle", "認証キー入力");
    model.addAttribute("errors", errors);
    model.addAttribute("token", trimmed);
    return "user/passRemindRecieve";
  }
}
