package com.shimanamisan.baseballmarket.user.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/passEdit")
public class PasswordEditController {

  private static final Logger log = LoggerFactory.getLogger(PasswordEditController.class);

  private final UserService userService;

  public PasswordEditController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public String show(Model model) {
    model.addAttribute("siteTitle", "パスワード変更");
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", new PasswordEditRequest());
    }
    if (!model.containsAttribute("errors")) {
      model.addAttribute("errors", new HashMap<>());
    }
    return "user/passEdit";
  }

  @PostMapping
  public String submit(
      @Valid @ModelAttribute("form") PasswordEditRequest form,
      BindingResult binding,
      Principal principal,
      RedirectAttributes redirect,
      Model model) {

    Map<String, String> errors = new HashMap<>();
    for (FieldError fe : binding.getFieldErrors()) {
      errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    if (errors.isEmpty() && !form.getPassNew().equals(form.getPassNewRe())) {
      errors.put("passNewRe", "新しいパスワードと確認用パスワードが一致しません");
    }

    if (errors.isEmpty()) {
      try {
        User user = userService
            .findByEmail(principal.getName())
            .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
        userService.changePassword(user.getUserId(), form.getPassOld(), form.getPassNew());
        redirect.addFlashAttribute("msgSuccess", "パスワードを変更しました");
        return "redirect:/mypage";
      } catch (ValidationException e) {
        errors.put("common", e.getFirstError());
        log.warn("[passEdit] {}", e.getFirstError());
      } catch (RuntimeException e) {
        log.error("[passEdit] エラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }
    model.addAttribute("siteTitle", "パスワード変更");
    model.addAttribute("form", form);
    model.addAttribute("errors", errors);
    return "user/passEdit";
  }
}
