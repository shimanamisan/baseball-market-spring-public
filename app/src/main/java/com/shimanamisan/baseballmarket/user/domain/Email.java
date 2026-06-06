package com.shimanamisan.baseballmarket.user.domain;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.util.regex.Pattern;

public record Email(String value) {

  private static final Pattern PATTERN = Pattern.compile(
      "^[a-zA-Z0-9]+[a-zA-Z0-9._-]*@[a-zA-Z0-9_-]+[a-zA-Z0-9._-]+$"
  );
  private static final int MAX_LENGTH = 255;

  public Email {
    if (value == null || !PATTERN.matcher(value).matches()) {
      throw new ValidationException("email", "Emailの形式で入力してください");
    }
    if (value.length() > MAX_LENGTH) {
      throw new ValidationException("email", "256文字以内で入力してください");
    }
  }

  public static Email fromString(String value) {
    return new Email(value);
  }
}
