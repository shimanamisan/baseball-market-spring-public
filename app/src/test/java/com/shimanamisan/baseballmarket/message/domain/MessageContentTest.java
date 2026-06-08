package com.shimanamisan.baseballmarket.message.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageContentTest {

  @Test
  @DisplayName("通常の本文を保持できる")
  void holdsNormalContent() {
    MessageContent content = MessageContent.fromString("こんにちは");

    assertThat(content.value()).isEqualTo("こんにちは");
  }

  @Test
  @DisplayName("500文字ちょうどは許可される（境界値）")
  void allowsExactly500Chars() {
    String body = "a".repeat(500);

    assertThat(MessageContent.fromString(body).value()).hasSize(500);
  }

  @Test
  @DisplayName("空文字は ValidationException")
  void rejectsEmpty() {
    assertThatThrownBy(() -> MessageContent.fromString(""))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("メッセージを入力してください");
  }

  @Test
  @DisplayName("null は ValidationException")
  void rejectsNull() {
    assertThatThrownBy(() -> MessageContent.fromString(null))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("メッセージを入力してください");
  }

  @Test
  @DisplayName("501文字以上は ValidationException（境界値）")
  void rejectsOver500Chars() {
    String body = "a".repeat(501);

    assertThatThrownBy(() -> MessageContent.fromString(body))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("500文字以内");
  }
}
