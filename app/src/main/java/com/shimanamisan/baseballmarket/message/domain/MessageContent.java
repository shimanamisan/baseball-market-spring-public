package com.shimanamisan.baseballmarket.message.domain;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;

/**
 * メッセージ本文の値オブジェクト。
 * 旧 PHP の Message\Domain\MessageContent を踏襲（空禁止・500文字以内）。
 */
public record MessageContent(String value) {

  private static final int MAX_LENGTH = 500;

  public MessageContent {
    if (value == null || value.isEmpty()) {
      throw new ValidationException("msg", "メッセージを入力してください");
    }
    if (value.length() > MAX_LENGTH) {
      throw new ValidationException("msg", "メッセージは500文字以内で入力してください");
    }
  }

  public static MessageContent fromString(String value) {
    return new MessageContent(value);
  }
}
