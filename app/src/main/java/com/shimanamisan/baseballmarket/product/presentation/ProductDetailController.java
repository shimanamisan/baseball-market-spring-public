package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.like.application.LikeService;
import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.user.application.UserService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/productDetail")
public class ProductDetailController {

  private final ProductService productService;
  private final LikeService likeService;
  private final UserService userService;

  public ProductDetailController(
      ProductService productService, LikeService likeService, UserService userService) {
    this.productService = productService;
    this.likeService = likeService;
    this.userService = userService;
  }

  @GetMapping("/{id}")
  public String show(
      @PathVariable("id") int id, Principal principal, Model model, RedirectAttributes redirect) {
    if (id <= 0) {
      return "redirect:/";
    }
    Product product = productService
        .findById(ProductId.fromLong(id))
        .orElse(null);
    if (product == null || product.isDeleted()) {
      return "redirect:/";
    }

    boolean liked = false;
    if (principal != null) {
      var currentUser = userService.findByEmail(principal.getName()).orElse(null);
      if (currentUser != null) {
        liked = likeService.isLiked(currentUser.getId(), product.getId());
      }
    }

    model.addAttribute("siteTitle", product.getProductName());
    model.addAttribute("product", product);
    model.addAttribute("liked", liked);
    model.addAttribute("category",
        productService.listCategories().stream()
            .filter(c -> c.getId().equals(product.getCategoryId()))
            .findFirst().orElse(null));
    model.addAttribute("maker",
        productService.listMakers().stream()
            .filter(m -> m.getId().equals(product.getMakerId()))
            .findFirst().orElse(null));
    return "product/detail";
  }
}
