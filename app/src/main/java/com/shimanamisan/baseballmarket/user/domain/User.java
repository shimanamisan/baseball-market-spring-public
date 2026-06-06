package com.shimanamisan.baseballmarket.user.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * users テーブルに対応するエンティティ。
 *
 * delete_flg は Short (TINYINT) で受け、ヘルパで boolean 化する。
 * ddl-auto=validate でも MySQL TINYINT と矛盾しないことを優先した設計。
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password", nullable = false, length = 255)
  private String password;

  @Column(name = "login_at")
  private LocalDateTime loginAt;

  @Column(name = "delete_flg", nullable = false)
  private Byte deleteFlg = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "email_verified_at")
  private LocalDateTime emailVerifiedAt;

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
  private UserProfile profile;

  protected User() {}

  public User(String email, String passwordHash) {
    this.email = email;
    this.password = passwordHash;
    this.deleteFlg = (byte) 0;
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
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public LocalDateTime getLoginAt() { return loginAt; }
  public void setLoginAt(LocalDateTime loginAt) { this.loginAt = loginAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public LocalDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
  public void setEmailVerifiedAt(LocalDateTime t) { this.emailVerifiedAt = t; }
  public UserProfile getProfile() { return profile; }
  public void setProfile(UserProfile profile) { this.profile = profile; }

  public boolean isDeleted() {
    return deleteFlg != null && deleteFlg != 0;
  }

  public void markDeleted() {
    this.deleteFlg = (byte) 1;
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  public UserId getUserId() {
    return id == null ? UserId.temporary() : UserId.fromLong(id.longValue());
  }
}
