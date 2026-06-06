package com.shimanamisan.baseballmarket.user.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.shared.infrastructure.storage.ImageStorage;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.Email;
import com.shimanamisan.baseballmarket.user.domain.ProfileUpdate;
import com.shimanamisan.baseballmarket.user.domain.User;
import com.shimanamisan.baseballmarket.user.domain.UserProfile;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profEdit")
public class ProfileEditController {

  private static final Logger log = LoggerFactory.getLogger(ProfileEditController.class);

  private final UserService userService;
  private final ImageStorage imageStorage;

  public ProfileEditController(UserService userService, ImageStorage imageStorage) {
    this.userService = userService;
    this.imageStorage = imageStorage;
  }

  @GetMapping
  public String show(Principal principal, Model model) {
    User user = userService
        .findByEmail(principal.getName())
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));

    model.addAttribute("siteTitle", "プロフィール編集");
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", toForm(user));
    }
    if (!model.containsAttribute("errors")) {
      model.addAttribute("errors", new HashMap<>());
    }
    model.addAttribute("currentPic", user.getProfile() != null ? user.getProfile().getPic() : null);
    return "user/profEdit";
  }

  @PostMapping
  public String submit(
      @Valid @ModelAttribute("form") ProfileEditRequest form,
      BindingResult binding,
      @RequestParam(value = "pic", required = false) MultipartFile pic,
      Principal principal,
      RedirectAttributes redirect,
      Model model) {

    Map<String, String> errors = new HashMap<>();
    for (FieldError fe : binding.getFieldErrors()) {
      errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }

    User user = userService
        .findByEmail(principal.getName())
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
    String existingPic = user.getProfile() != null ? user.getProfile().getPic() : null;

    String picPath = existingPic;
    if (errors.isEmpty()) {
      try {
        String uploaded = imageStorage.save(pic);
        if (uploaded != null) {
          picPath = uploaded;
        }
      } catch (ValidationException e) {
        errors.putAll(e.getErrors());
      }
    }

    if (errors.isEmpty()) {
      try {
        Email emailVo = Email.fromString(form.getEmail());
        ProfileUpdate update = new ProfileUpdate(
            nullIfBlank(form.getUsername()),
            form.getAge(),
            nullIfBlank(form.getTel()),
            nullIfBlank(form.getZip()),
            nullIfBlank(form.getPrefecture()),
            nullIfBlank(form.getCity()),
            nullIfBlank(form.getStreet()),
            nullIfBlank(form.getBuilding()),
            emailVo,
            picPath);
        userService.updateProfile(user.getUserId(), update);
        redirect.addFlashAttribute("msgSuccess", "プロフィールを更新しました");
        return "redirect:/mypage";
      } catch (ValidationException e) {
        errors.putAll(e.getErrors());
        log.warn("[profEdit] {}", errors);
      } catch (RuntimeException e) {
        log.error("[profEdit] エラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }

    model.addAttribute("siteTitle", "プロフィール編集");
    model.addAttribute("form", form);
    model.addAttribute("errors", errors);
    model.addAttribute("currentPic", existingPic);
    return "user/profEdit";
  }

  private static ProfileEditRequest toForm(User user) {
    ProfileEditRequest req = new ProfileEditRequest();
    req.setEmail(user.getEmail());
    UserProfile p = user.getProfile();
    if (p != null) {
      req.setUsername(p.getUsername() == null ? "" : p.getUsername());
      req.setAge(p.getAge());
      req.setTel(p.getTel() == null ? "" : p.getTel());
      req.setZip(p.getZip() == null ? "" : p.getZip());
      req.setPrefecture(p.getPrefecture() == null ? "" : p.getPrefecture());
      req.setCity(p.getCity() == null ? "" : p.getCity());
      req.setStreet(p.getStreet() == null ? "" : p.getStreet());
      req.setBuilding(p.getBuilding() == null ? "" : p.getBuilding());
    }
    return req;
  }

  private static String nullIfBlank(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
