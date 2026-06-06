package com.shimanamisan.baseballmarket.user.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/signup")
public class SignupController {

  private static final Logger log = LoggerFactory.getLogger(SignupController.class);

  private final UserService userService;

  public SignupController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public String show(Model model) {
    model.addAttribute("siteTitle", "新規登録");
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", new SignupRequest());
    }
    if (!model.containsAttribute("errors")) {
      model.addAttribute("errors", new HashMap<String, String>());
    }
    return "user/signup";
  }

  @PostMapping
  public String submit(
      @Valid @ModelAttribute("form") SignupRequest form,
      BindingResult binding,
      RedirectAttributes redirect,
      Model model) {

    Map<String, String> errors = collectFieldErrors(binding);

    // パスワード一致チェックは複合バリデーションのため手動
    if (errors.isEmpty() && !form.getPassword().equals(form.getPasswordConfirm())) {
      errors.put("passwordConfirm", "パスワード（再入力）が合っていません");
    }

    if (errors.isEmpty()) {
      try {
        userService.register(form.getEmail(), form.getPassword());
        redirect.addFlashAttribute("msgSuccess",
            "確認メールを送信しました。メール内のリンクをクリックして登録を完了してください。");
        return "redirect:/login";
      } catch (ValidationException e) {
        errors.putAll(e.getErrors());
        log.warn("[signup] バリデーションエラー: {}", errors);
      } catch (RuntimeException e) {
        log.error("[signup] ユーザー登録エラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }

    model.addAttribute("siteTitle", "新規登録");
    model.addAttribute("form", form);
    model.addAttribute("errors", errors);
    return "user/signup";
  }

  private static Map<String, String> collectFieldErrors(BindingResult binding) {
    Map<String, String> errors = new HashMap<>();
    for (FieldError fe : binding.getFieldErrors()) {
      errors.putIfAbsent(translateFieldName(fe.getField()), fe.getDefaultMessage());
    }
    return errors;
  }

  private static String translateFieldName(String field) {
    // 旧 PHP のキー名（email/pass/pass_re）と新キー（password/passwordConfirm）の橋渡し用
    return switch (field) {
      case "password" -> "password";
      case "passwordConfirm" -> "passwordConfirm";
      default -> field;
    };
  }
}
