package com.shimanamisan.baseballmarket.user.domain;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.util.regex.Pattern;

/**
 * パスワード VO（ハッシュ値ラッパー）。
 *
 * 旧 PHP の Password VO は plain → hash 変換まで内部で行っていたが、
 * Spring 側では PasswordEncoder を DI したいため、ハッシュ化は application 層の責務に分離。
 * ドメイン層では validateRawPassword(raw) で「平文の文字列ルール」のみを検証し、
 * 実際のハッシュ化は UserService が PasswordEncoder 経由で行い、その結果を fromHash で包む。
 */
public final class Password {

  private static final Pattern ALPHANUMERIC = Pattern.compile("^[a-zA-Z0-9]+$");
  private static final int MIN_LENGTH = 6;
  private static final int MAX_LENGTH = 255;

  private final String hashedValue;

  private Password(String hashedValue) {
    this.hashedValue = hashedValue;
  }

  public static Password fromHash(String hash) {
    if (hash == null || hash.isBlank()) {
      throw new IllegalArgumentException("hash must not be blank");
    }
    return new Password(hash);
  }

  /**
   * 平文パスワードのドメインルールを検証する。違反は ValidationException を投げる。
   * ハッシュ化は呼び出し元（application 層）が行う。
   */
  public static void validateRawPassword(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      throw new ValidationException("pass", "6文字以上で入力してください");
    }
    if (rawPassword.length() > MAX_LENGTH) {
      throw new ValidationException("pass", "256文字以内で入力してください");
    }
    if (!ALPHANUMERIC.matcher(rawPassword).matches()) {
      throw new ValidationException("pass", "半角英数字のみご利用いただけます");
    }
  }

  public String getHash() {
    return hashedValue;
  }
}
