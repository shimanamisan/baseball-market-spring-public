package com.shimanamisan.baseballmarket.like.domain;

public record LikeId(int value) {

  public LikeId {
    if (value < 0) {
      throw new IllegalArgumentException("お気に入りIDは0以上の整数である必要があります");
    }
  }

  public static LikeId fromInt(int value) {
    return new LikeId(value);
  }

  public static LikeId temporary() {
    return new LikeId(0);
  }

  public boolean isTemporary() {
    return value == 0;
  }
}
