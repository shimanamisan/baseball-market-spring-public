package com.shimanamisan.baseballmarket.product.presentation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ProductRegistRequest {

  @NotBlank(message = "商品名を入力してください")
  @Size(max = 255, message = "商品名は255文字以内で入力してください")
  private String name = "";

  @NotNull(message = "カテゴリを選択してください")
  @Min(value = 1, message = "カテゴリを選択してください")
  private Integer categoryId;

  @NotNull(message = "メーカーを選択してください")
  @Min(value = 1, message = "メーカーを選択してください")
  private Integer makerId;

  @NotNull(message = "価格を入力してください")
  @Min(value = 0, message = "価格は0円以上で入力してください")
  private Integer price;

  @Size(max = 500, message = "詳細は500文字以内で入力してください")
  private String comment = "";

  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public Integer getCategoryId() { return categoryId; }
  public void setCategoryId(Integer v) { this.categoryId = v; }
  public Integer getMakerId() { return makerId; }
  public void setMakerId(Integer v) { this.makerId = v; }
  public Integer getPrice() { return price; }
  public void setPrice(Integer v) { this.price = v; }
  public String getComment() { return comment; }
  public void setComment(String v) { this.comment = v; }
}
