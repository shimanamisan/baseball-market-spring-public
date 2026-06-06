package com.shimanamisan.baseballmarket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "token", nullable = false, unique = true, length = 64)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected EmailVerificationTokenEntity() {}

  public EmailVerificationTokenEntity(Integer userId, String token, LocalDateTime expiresAt) {
    this.userId = userId;
    this.token = token;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onPersist() {
    if (createdAt == null) createdAt = LocalDateTime.now();
  }

  public Integer getId() { return id; }
  public Integer getUserId() { return userId; }
  public String getToken() { return token; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
}
