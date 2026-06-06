package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.product.application.ProductService;
import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
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

  public ProductDetailController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping("/{id}")
  public String show(@PathVariable("id") int id, Model model, RedirectAttributes redirect) {
    if (id <= 0) {
      return "redirect:/";
    }
    Product product = productService
        .findById(ProductId.fromLong(id))
        .orElse(null);
    if (product == null || product.isDeleted()) {
      return "redirect:/";
    }

    model.addAttribute("siteTitle", product.getProductName());
    model.addAttribute("product", product);
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
