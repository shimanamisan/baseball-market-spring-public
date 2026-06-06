package com.shimanamisan.baseballmarket.user.domain;

public record UserId(long value) {

  public UserId {
    if (value < 0) {
      throw new IllegalArgumentException("ユーザーIDは0以上の整数である必要があります");
    }
  }

  public static UserId fromLong(long value) {
    return new UserId(value);
  }

  public static UserId temporary() {
    return new UserId(0);
  }

  public boolean isTemporary() {
    return value == 0;
  }
}
