package com.shimanamisan.baseballmarket.message.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoardTest {

  private static final int SELLER = 10;
  private static final int BUYER = 20;
  private static final int STRANGER = 99;
  private static final int PRODUCT = 30;

  private Board board() {
    return new Board(SELLER, BUYER, PRODUCT);
  }

  @Nested
  @DisplayName("isParticipant")
  class IsParticipant {
    @Test
    @DisplayName("出品者・購入者は参加者")
    void participants() {
      assertThat(board().isParticipant(SELLER)).isTrue();
      assertThat(board().isParticipant(BUYER)).isTrue();
    }

    @Test
    @DisplayName("第三者は参加者でない")
    void stranger() {
      assertThat(board().isParticipant(STRANGER)).isFalse();
    }
  }

  @Nested
  @DisplayName("getPartnerUserId")
  class GetPartnerUserId {
    @Test
    @DisplayName("出品者から見た相手は購入者")
    void fromSeller() {
      assertThat(board().getPartnerUserId(SELLER)).isEqualTo(BUYER);
    }

    @Test
    @DisplayName("購入者から見た相手は出品者")
    void fromBuyer() {
      assertThat(board().getPartnerUserId(BUYER)).isEqualTo(SELLER);
    }

    @Test
    @DisplayName("第三者には相手が存在しない（null）")
    void fromStranger() {
      assertThat(board().getPartnerUserId(STRANGER)).isNull();
    }
  }

  @Test
  @DisplayName("isSeller / isBuyer は役割を判定する")
  void roles() {
    Board board = board();

    assertThat(board.isSeller(SELLER)).isTrue();
    assertThat(board.isSeller(BUYER)).isFalse();
    assertThat(board.isBuyer(BUYER)).isTrue();
    assertThat(board.isBuyer(SELLER)).isFalse();
  }
}
