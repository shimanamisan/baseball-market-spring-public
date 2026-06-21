package com.shimanamisan.baseballmarket.like.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LikeIdTest {

  @Test
  @DisplayName("正の整数から生成できる")
  void createsFromPositive() {
    LikeId id = LikeId.fromInt(5);

    assertThat(id.value()).isEqualTo(5);
  }

  @Test
  @DisplayName("temporary は値 0 を持ち isTemporary が true")
  void temporaryHasZero() {
    LikeId id = LikeId.temporary();

    assertThat(id.value()).isEqualTo(0);
    assertThat(id.isTemporary()).isTrue();
  }

  @Test
  @DisplayName("負の値は拒否する")
  void rejectsNegative() {
    assertThatThrownBy(() -> LikeId.fromInt(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
