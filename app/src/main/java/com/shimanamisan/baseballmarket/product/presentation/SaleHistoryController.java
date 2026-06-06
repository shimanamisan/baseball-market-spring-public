package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SaleHistoryController {

  private final ProductService productService;
  private final UserService userService;

  public SaleHistoryController(ProductService productService, UserService userService) {
    this.productService = productService;
    this.userService = userService;
  }

  @GetMapping("/tranSale")
  public String show(Principal principal, Model model) {
    User user = userService
        .findByEmail(principal.getName())
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
    var items = productService.getSaleHistory(user.getId());

    model.addAttribute("siteTitle", "販売履歴");
    model.addAttribute("items", items);
    return "product/saleHistory";
  }
}
