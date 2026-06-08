package com.shimanamisan.baseballmarket.message.domain;

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
 * boards テーブルに対応する掲示板エンティティ。
 *
 * 1 つの取引（出品者と購入者のやり取り）に 1 つの掲示板が対応する。
 * 旧 PHP の Message\Domain\Board のビジネスメソッド（isParticipant / getPartnerUserId 等）を保持する。
 * 別 context への参照（出品者・購入者・商品）は ID 値で保持する（context 境界を守る）。
 */
@Entity
@Table(name = "boards")
public class Board {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "sale_user", nullable = false)
  private Integer saleUser;

  @Column(name = "buy_user", nullable = false)
  private Integer buyUser;

  @Column(name = "product_id", nullable = false)
  private Integer productId;

  @Column(name = "delete_flg", nullable = false)
  private Byte deleteFlg = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Board() {}

  public Board(int saleUser, int buyUser, int productId) {
    this.saleUser = saleUser;
    this.buyUser = buyUser;
    this.productId = productId;
  }

  @PrePersist
  void onPersist() {
    var now = LocalDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
    if (deleteFlg == null) deleteFlg = (byte) 0;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Integer getId() { return id; }
  public Integer getSaleUser() { return saleUser; }
  public Integer getBuyUser() { return buyUser; }
  public Integer getProductId() { return productId; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }

  public boolean isDeleted() {
    return deleteFlg != null && deleteFlg != 0;
  }

  public BoardId getBoardId() {
    return id == null ? BoardId.temporary() : BoardId.fromLong(id.longValue());
  }

  // ---------- ビジネスメソッド ----------

  /** 指定ユーザーがこの掲示板の参加者（出品者または購入者）か。 */
  public boolean isParticipant(int userId) {
    return isSeller(userId) || isBuyer(userId);
  }

  /** 指定ユーザーから見た取引相手のユーザーID。参加者でなければ null。 */
  public Integer getPartnerUserId(int userId) {
    if (isSeller(userId)) {
      return buyUser;
    }
    if (isBuyer(userId)) {
      return saleUser;
    }
    return null;
  }

  public boolean isSeller(int userId) {
    return saleUser != null && saleUser == userId;
  }

  public boolean isBuyer(int userId) {
    return buyUser != null && buyUser == userId;
  }
}
