package com.shimanamisan.baseballmarket.user.domain;

import com.shimanamisan.baseballmarket.shared.infrastructure.security.TokenGenerator;
import java.time.Duration;
import java.time.LocalDateTime;

public record EmailVerificationToken(String value, LocalDateTime expiresAt) {

  private static final int DEFAULT_LENGTH = 32;
  private static final Duration DEFAULT_TTL = Duration.ofHours(24);

  public EmailVerificationToken {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("token value must not be blank");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt must not be null");
    }
  }

  public static EmailVerificationToken generate() {
    return generate(DEFAULT_TTL);
  }

  public static EmailVerificationToken generate(Duration ttl) {
    String value = TokenGenerator.generateSecure(DEFAULT_LENGTH);
    return new EmailVerificationToken(value, LocalDateTime.now().plus(ttl));
  }

  public boolean isValid() {
    return LocalDateTime.now().isBefore(expiresAt);
  }
}
