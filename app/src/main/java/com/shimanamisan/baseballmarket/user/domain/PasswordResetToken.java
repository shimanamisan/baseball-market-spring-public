package com.shimanamisan.baseballmarket.user.domain;

import com.shimanamisan.baseballmarket.shared.infrastructure.security.TokenGenerator;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * パスワードリセットの認証キー VO。
 *
 * 旧 PHP の PasswordResetToken は 8 文字の英数キー + 30 分有効。
 * セッションに保存される運用のため Serializable を実装する。
 */
public record PasswordResetToken(String value, LocalDateTime expiresAt) implements Serializable {

  private static final int DEFAULT_LENGTH = 8;
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

  public PasswordResetToken {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("token value must not be blank");
    }
    if (expiresAt == null) {
      throw new IllegalArgumentException("expiresAt must not be null");
    }
  }

  public static PasswordResetToken generate() {
    return new PasswordResetToken(TokenGenerator.generate(DEFAULT_LENGTH), LocalDateTime.now().plus(DEFAULT_TTL));
  }

  public boolean isValid() {
    return LocalDateTime.now().isBefore(expiresAt);
  }

  public boolean matches(String input) {
    return value.equals(input);
  }
}
