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
 * messages テーブルに対応するメッセージエンティティ。
 *
 * ⚠ 列名 bord_id は旧 PHP 由来のタイポを意図的に保持している（V1__init.sql 参照）。
 *    フィールド名は bordId とし、@Column(name = "bord_id") で吸収する。
 */
@Entity
@Table(name = "messages")
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "bord_id", nullable = false)
  private Integer bordId;

  @Column(name = "from_user", nullable = false)
  private Integer fromUser;

  @Column(name = "to_user", nullable = false)
  private Integer toUser;

  @Column(name = "msg", nullable = false, columnDefinition = "TEXT")
  private String msg;

  @Column(name = "send_at", nullable = false)
  private LocalDateTime sendAt;

  @Column(name = "delete_flg", nullable = false)
  private Byte deleteFlg = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Message() {}

  public Message(int bordId, int fromUser, int toUser, String msg, LocalDateTime sendAt) {
    this.bordId = bordId;
    this.fromUser = fromUser;
    this.toUser = toUser;
    this.msg = msg;
    this.sendAt = sendAt;
  }

  @PrePersist
  void onPersist() {
    var now = LocalDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
    if (sendAt == null) sendAt = now;
    if (deleteFlg == null) deleteFlg = (byte) 0;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Integer getId() { return id; }
  public Integer getBordId() { return bordId; }
  public Integer getFromUser() { return fromUser; }
  public Integer getToUser() { return toUser; }
  public String getMsg() { return msg; }
  public LocalDateTime getSendAt() { return sendAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }

  public boolean isDeleted() {
    return deleteFlg != null && deleteFlg != 0;
  }

  public MessageId getMessageId() {
    return id == null ? MessageId.temporary() : MessageId.fromLong(id.longValue());
  }

  // ---------- ビジネスメソッド ----------

  public boolean isSentBy(int userId) {
    return fromUser != null && fromUser == userId;
  }

  public boolean isReceivedBy(int userId) {
    return toUser != null && toUser == userId;
  }
}
