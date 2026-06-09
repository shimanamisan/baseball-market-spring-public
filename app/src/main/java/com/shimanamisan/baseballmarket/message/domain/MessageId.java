package com.shimanamisan.baseballmarket.message.domain;

public record MessageId(long value) {

  public MessageId {
    if (value < 0) {
      throw new IllegalArgumentException("メッセージIDは0以上の整数である必要があります");
    }
  }

  public static MessageId fromLong(long value) {
    return new MessageId(value);
  }

  public static MessageId temporary() {
    return new MessageId(0);
  }

  public boolean isTemporary() {
    return value == 0;
  }
}
