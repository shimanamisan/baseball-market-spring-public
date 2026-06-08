package com.shimanamisan.baseballmarket.message.presentation;

import com.shimanamisan.baseballmarket.message.application.BoardMessages;
import com.shimanamisan.baseballmarket.message.application.MessageService;
import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import com.shimanamisan.baseballmarket.user.domain.UserId;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * メッセージ掲示板の表示・送信を担う Controller。
 *
 * 掲示板（board / messages）は message context が、商品・ユーザーの表示情報は
 * product / user context の application API から取得して画面に組み立てる（context 跨ぎは application 層連携）。
 */
@Controller
public class MessageController {

  private static final Logger log = LoggerFactory.getLogger(MessageController.class);

  private final MessageService messageService;
  private final ProductService productService;
  private final UserService userService;

  public MessageController(
      MessageService messageService, ProductService productService, UserService userService) {
    this.messageService = messageService;
    this.productService = productService;
    this.userService = userService;
  }

  @GetMapping("/message")
  public String show(@RequestParam("b_id") int boardId, Principal principal, Model model) {
    User currentUser = currentUser(principal);
    int currentUserId = currentUser.getId();

    BoardMessages bm = messageService.getBoardWithMessages(boardId, currentUserId);

    Integer partnerId = bm.board().getPartnerUserId(currentUserId);
    User partnerUser =
        partnerId == null
            ? null
            : userService.findById(UserId.fromLong(partnerId)).orElse(null);

    var product =
        productService.findById(ProductId.fromLong(bm.board().getProductId())).orElse(null);

    model.addAttribute("siteTitle", "連絡掲示板");
    model.addAttribute("board", bm.board());
    model.addAttribute("messages", bm.messages());
    model.addAttribute("product", product);
    model.addAttribute("partnerUser", partnerUser);
    model.addAttribute("myUser", currentUser);
    model.addAttribute("currentUserId", currentUserId);
    return "message/msg";
  }

  @PostMapping("/message/{boardId}/messages")
  public String send(
      @PathVariable("boardId") int boardId,
      @ModelAttribute SendMessageRequest form,
      Principal principal,
      RedirectAttributes redirect) {
    int currentUserId = currentUser(principal).getId();
    try {
      messageService.sendMessage(boardId, currentUserId, form.getMsg());
    } catch (ValidationException e) {
      log.debug("[message:send] board={} {}", boardId, e.getFirstError());
      redirect.addFlashAttribute("errorMessage", e.getFirstError());
    }
    return "redirect:/message?b_id=" + boardId;
  }

  private User currentUser(Principal principal) {
    return userService
        .findByEmail(principal.getName())
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
  }
}
