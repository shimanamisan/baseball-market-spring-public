package com.shimanamisan.baseballmarket.message.domain;

public record BoardId(long value) {

  public BoardId {
    if (value < 0) {
      throw new IllegalArgumentException("掲示板IDは0以上の整数である必要があります");
    }
  }

  public static BoardId fromLong(long value) {
    return new BoardId(value);
  }

  public static BoardId temporary() {
    return new BoardId(0);
  }

  public boolean isTemporary() {
    return value == 0;
  }
}
