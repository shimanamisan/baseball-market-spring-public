package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.product.domain.SearchCriteria;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductListController {

  // 商品グリッドは lg:grid-cols-3（デスクトップ 3 カラム）のため、
  // 3 で割り切れる件数にして最終行の隙間（カードが欠けて見える状態）を防ぐ
  private static final int PER_PAGE = 21;

  private final ProductService productService;

  public ProductListController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping("/")
  public String list(
      @RequestParam(value = "p", defaultValue = "1") int page,
      @RequestParam(value = "c_id", required = false) Integer categoryId,
      @RequestParam(value = "m_id", required = false) Integer makerId,
      @RequestParam(value = "sort", required = false) Integer sort,
      Model model) {

    if (page < 1) page = 1;
    if (categoryId != null && categoryId == 0) categoryId = null;
    if (makerId != null && makerId == 0) makerId = null;
    if (sort != null && sort == 0) sort = null;

    var criteria = new SearchCriteria(categoryId, makerId, sort, page, PER_PAGE);
    var result = productService.search(criteria);

    model.addAttribute("siteTitle", "商品一覧");
    model.addAttribute("result", result);
    model.addAttribute("categories", productService.listCategories());
    model.addAttribute("makers", productService.listMakers());
    model.addAttribute("selectedCategory", categoryId);
    model.addAttribute("selectedMaker", makerId);
    model.addAttribute("selectedSort", sort);
    return "product/list";
  }
}
