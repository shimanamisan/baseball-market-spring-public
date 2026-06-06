package com.shimanamisan.baseballmarket.shared.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 暫定マイページプレースホルダ。
 * "/" はフェーズ 6 から product.presentation.ProductListController が担当している。
 * "/mypage" はフェーズ 8 で MyPageController に置き換える予定。
 */
@Controller
public class HomeController {

  @GetMapping("/mypage")
  public String mypage(Model model) {
    model.addAttribute("siteTitle", "マイページ");
    return "shared/home";
  }
}
