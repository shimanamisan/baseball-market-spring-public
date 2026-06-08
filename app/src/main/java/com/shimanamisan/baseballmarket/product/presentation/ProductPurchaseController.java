package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.message.domain.BoardId;
import com.shimanamisan.baseballmarket.product.application.PurchaseService;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 商品購入を受け付ける Controller。
 *
 * 購入導線の入り口は旧 PHP と同じく商品詳細（/productDetail/{id}）配下に置く。
 * 売却済み更新 + 掲示板作成の調整は PurchaseService（product↔message を跨ぐ専用サービス）が担い、
 * 成功時は作成された掲示板へ遷移する。
 */
@Controller
@RequestMapping("/productDetail")
public class ProductPurchaseController {

  private static final Logger log = LoggerFactory.getLogger(ProductPurchaseController.class);

  private final PurchaseService purchaseService;
  private final UserService userService;

  public ProductPurchaseController(PurchaseService purchaseService, UserService userService) {
    this.purchaseService = purchaseService;
    this.userService = userService;
  }

  @PostMapping("/{id}/purchase")
  public String purchase(
      @PathVariable("id") int id, Principal principal, RedirectAttributes redirect) {
    User buyer =
        userService
            .findByEmail(principal.getName())
            .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
    try {
      BoardId boardId = purchaseService.purchase(id, buyer.getId());
      redirect.addFlashAttribute("msgSuccess", "購入しました！相手と連絡を取りましょう！");
      return "redirect:/message?b_id=" + boardId.value();
    } catch (ValidationException e) {
      log.debug("[purchase] product={} {}", id, e.getFirstError());
      redirect.addFlashAttribute("errorMessage", e.getFirstError());
      return "redirect:/productDetail/" + id;
    }
  }
}
