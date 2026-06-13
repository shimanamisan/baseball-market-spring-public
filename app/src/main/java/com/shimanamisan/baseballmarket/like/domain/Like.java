package com.shimanamisan.baseballmarket.like.domain;

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
 * likes テーブルに対応するお気に入りエンティティ。
 *
 * 旧 PHP の Like ドメイン（productId / userId / isOwnedBy）を踏襲する。
 * 別 context（user / product）への参照は ID 値で保持し、@ManyToOne で直接ぶら下げない。
 */
@Entity
@Table(name = "likes")
public class Like {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "product_id", nullable = false)
  private Integer productId;

  @Column(name = "delete_flg", nullable = false)
  private Byte deleteFlg = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Like() {}

  public Like(int userId, int productId) {
    this.userId = userId;
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
  public Integer getUserId() { return userId; }
  public Integer getProductId() { return productId; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
