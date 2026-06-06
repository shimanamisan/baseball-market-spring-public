package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.shared.infrastructure.storage.ImageStorage;
import com.shimanamisan.baseballmarket.product.application.ProductRegistrationCommand;
import com.shimanamisan.baseballmarket.product.application.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/registProduct")
public class ProductRegistController {

  private static final Logger log = LoggerFactory.getLogger(ProductRegistController.class);

  private final ProductService productService;
  private final UserService userService;
  private final ImageStorage imageStorage;

  public ProductRegistController(
      ProductService productService, UserService userService, ImageStorage imageStorage) {
    this.productService = productService;
    this.userService = userService;
    this.imageStorage = imageStorage;
  }

  @GetMapping
  public String show(Model model) {
    if (!model.containsAttribute("form")) {
      model.addAttribute("form", new ProductRegistRequest());
    }
    if (!model.containsAttribute("errors")) {
      model.addAttribute("errors", new HashMap<>());
    }
    model.addAttribute("siteTitle", "商品登録");
    model.addAttribute("categories", productService.listCategories());
    model.addAttribute("makers", productService.listMakers());
    return "product/regist";
  }

  @PostMapping
  public String submit(
      @Valid @ModelAttribute("form") ProductRegistRequest form,
      BindingResult binding,
      @RequestParam(value = "pic1", required = false) MultipartFile pic1,
      @RequestParam(value = "pic2", required = false) MultipartFile pic2,
      @RequestParam(value = "pic3", required = false) MultipartFile pic3,
      Principal principal,
      RedirectAttributes redirect,
      Model model) {

    Map<String, String> errors = new HashMap<>();
    for (FieldError fe : binding.getFieldErrors()) {
      errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }

    String p1 = null, p2 = null, p3 = null;
    if (errors.isEmpty()) {
      try {
        p1 = imageStorage.save(pic1);
        p2 = imageStorage.save(pic2);
        p3 = imageStorage.save(pic3);
      } catch (ValidationException e) {
        errors.putAll(e.getErrors());
      }
    }

    if (errors.isEmpty()) {
      try {
        User user = userService
            .findByEmail(principal.getName())
            .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
        var cmd = new ProductRegistrationCommand(
            form.getName(),
            form.getCategoryId() == null ? 0 : form.getCategoryId(),
            form.getMakerId() == null ? 0 : form.getMakerId(),
            form.getPrice() == null ? 0 : form.getPrice(),
            form.getComment() == null ? "" : form.getComment(),
            p1, p2, p3);
        productService.register(user.getId(), cmd);
        redirect.addFlashAttribute("msgSuccess", "商品を登録しました");
        return "redirect:/";
      } catch (ValidationException e) {
        errors.putAll(e.getErrors());
        log.warn("[registProduct] {}", errors);
      } catch (RuntimeException e) {
        log.error("[registProduct] エラー", e);
        errors.put("common", "エラーが発生しました。しばらく経ってからやり直してください。");
      }
    }

    model.addAttribute("siteTitle", "商品登録");
    model.addAttribute("form", form);
    model.addAttribute("errors", errors);
    model.addAttribute("categories", productService.listCategories());
    model.addAttribute("makers", productService.listMakers());
    return "product/regist";
  }
}
