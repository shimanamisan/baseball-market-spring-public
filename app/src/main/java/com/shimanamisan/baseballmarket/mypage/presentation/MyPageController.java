package com.shimanamisan.baseballmarket.mypage.presentation;

import com.shimanamisan.baseballmarket.mypage.application.MyPageService;
import com.shimanamisan.baseballmarket.mypage.application.MyPageView;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * マイページ（出品商品・お気に入り商品・参加掲示板の集約表示）を担う Controller。
 *
 * 表示データの組み立ては mypage.application.MyPageService に委譲し、Controller は
 * ログインユーザーの解決とモデルへの受け渡しのみを行う（薄く保つ）。
 */
@Controller
public class MyPageController {

  private final MyPageService myPageService;
  private final UserService userService;

  public MyPageController(MyPageService myPageService, UserService userService) {
    this.myPageService = myPageService;
    this.userService = userService;
  }

  @GetMapping("/mypage")
  public String index(Principal principal, Model model) {
    User currentUser =
        userService
            .findByEmail(principal.getName())
            .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));

    MyPageView myPage = myPageService.getMyPage(currentUser.getId());

    model.addAttribute("siteTitle", "マイページ");
    model.addAttribute("currentUser", currentUser);
    model.addAttribute("myPage", myPage);
    return "mypage/index";
  }
}
