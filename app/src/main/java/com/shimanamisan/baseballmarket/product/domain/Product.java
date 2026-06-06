package com.shimanamisan.baseballmarket.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * products テーブルに対応するエンティティ。
 * 旧 PHP の Product ドメインのビジネスメソッド（canPurchase / markAsSold / canEdit）を保持する。
 */
@Entity
@Table(name = "products")
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "product_name", nullable = false, length = 255)
  private String productName;

  @Column(name = "product_comment", columnDefinition = "TEXT")
  private String productComment;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "category_id", nullable = false)
  private Integer categoryId;

  @Column(name = "maker_id", nullable = false)
  private Integer makerId;

  @Column(name = "price", nullable = false)
  private Integer price;

  @Column(name = "pic1", length = 255)
  private String pic1;

  @Column(name = "pic2", length = 255)
  private String pic2;

  @Column(name = "pic3", length = 255)
  private String pic3;

  @Column(name = "sold_out_flg", nullable = false)
  private Byte soldOutFlg = 0;

  @Column(name = "delete_flg", nullable = false)
  private Byte deleteFlg = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Product() {}

  public Product(
      String productName,
      Integer categoryId,
      Integer makerId,
      Integer price,
      String productComment,
      Integer userId,
      String pic1,
      String pic2,
      String pic3) {
    this.productName = productName;
    this.categoryId = categoryId;
    this.makerId = makerId;
    this.price = price;
    this.productComment = productComment;
    this.userId = userId;
    this.pic1 = pic1;
    this.pic2 = pic2;
    this.pic3 = pic3;
  }

  @PrePersist
  void onPersist() {
    var now = LocalDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
    if (soldOutFlg == null) soldOutFlg = (byte) 0;
    if (deleteFlg == null) deleteFlg = (byte) 0;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Integer getId() { return id; }
  public String getProductName() { return productName; }
  public void setProductName(String v) { this.productName = v; }
  public String getProductComment() { return productComment; }
  public void setProductComment(String v) { this.productComment = v; }
  public Integer getUserId() { return userId; }
  public Integer getCategoryId() { return categoryId; }
  public void setCategoryId(Integer v) { this.categoryId = v; }
  public Integer getMakerId() { return makerId; }
  public void setMakerId(Integer v) { this.makerId = v; }
  public Integer getPrice() { return price; }
  public void setPrice(Integer v) { this.price = v; }
  public String getPic1() { return pic1; }
  public void setPic1(String v) { this.pic1 = v; }
  public String getPic2() { return pic2; }
  public void setPic2(String v) { this.pic2 = v; }
  public String getPic3() { return pic3; }
  public void setPic3(String v) { this.pic3 = v; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }

  public boolean isSoldOut() {
    return soldOutFlg != null && soldOutFlg != 0;
  }

  public boolean isDeleted() {
    return deleteFlg != null && deleteFlg != 0;
  }

  public ProductId getProductId() {
    return id == null ? ProductId.temporary() : ProductId.fromLong(id.longValue());
  }

  // ---------- ビジネスメソッド ----------

  public boolean canPurchase(int buyerUserId) {
    return this.userId != null && this.userId != buyerUserId && !isSoldOut();
  }

  public boolean canEdit(int requestUserId) {
    return this.userId != null && this.userId == requestUserId;
  }

  public void markAsSold() {
    this.soldOutFlg = (byte) 1;
  }

  public void markDeleted() {
    this.deleteFlg = (byte) 1;
  }
}
