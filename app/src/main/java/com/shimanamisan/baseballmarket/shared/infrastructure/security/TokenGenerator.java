package com.shimanamisan.baseballmarket.shared.infrastructure.security;

import java.security.SecureRandom;

/**
 * ランダム文字列生成ユーティリティ。
 *
 * 旧 PHP の Shared\Infrastructure\Security\TokenGenerator を Java へ移植したもの:
 * - generate(int): 旧 mt_rand 版相当。英数 62 種から N 文字。短い認証キー向け
 * - generateSecure(int): 旧 bin2hex(random_bytes($length / 2)) 相当。SecureRandom で hex 文字列を返す
 */
public final class TokenGenerator {

  private static final String CHARSET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJLKMNOPQRSTUVWXYZ0123456789";

  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenGenerator() {}

  public static String generate(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("length must be positive");
    }
    var sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
    }
    return sb.toString();
  }

  /**
   * @param length 出力される hex 文字列の長さ（偶数前提）
   */
  public static String generateSecure(int length) {
    if (length <= 0 || length % 2 != 0) {
      throw new IllegalArgumentException("length must be a positive even number");
    }
    var bytes = new byte[length / 2];
    RANDOM.nextBytes(bytes);
    var sb = new StringBuilder(length);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
