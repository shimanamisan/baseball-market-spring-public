package com.shimanamisan.baseballmarket.message.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shimanamisan.baseballmarket.message.domain.Board;
import com.shimanamisan.baseballmarket.message.domain.BoardId;
import com.shimanamisan.baseballmarket.message.domain.Message;
import com.shimanamisan.baseballmarket.message.domain.MessageId;
import com.shimanamisan.baseballmarket.message.domain.MessageRepository;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  private static final int SELLER = 10;
  private static final int BUYER = 20;
  private static final int STRANGER = 99;
  private static final int PRODUCT = 30;
  private static final int BOARD_ID = 5;

  @Mock private MessageRepository messageRepository;

  @InjectMocks private MessageService messageService;

  @Nested
  @DisplayName("createBoard")
  class CreateBoard {
    @Test
    @DisplayName("出品者と購入者が異なれば掲示板を作成する")
    void createsBoard() {
      when(messageRepository.saveBoard(any(Board.class))).thenReturn(BoardId.fromLong(BOARD_ID));

      BoardId result = messageService.createBoard(SELLER, BUYER, PRODUCT);

      assertThat(result.value()).isEqualTo(BOARD_ID);
      ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
      verify(messageRepository).saveBoard(captor.capture());
      Board saved = captor.getValue();
      assertThat(saved.getSaleUser()).isEqualTo(SELLER);
      assertThat(saved.getBuyUser()).isEqualTo(BUYER);
      assertThat(saved.getProductId()).isEqualTo(PRODUCT);
    }

    @Test
    @DisplayName("出品者と購入者が同一なら拒否する")
    void rejectsSameUser() {
      assertThatThrownBy(() -> messageService.createBoard(SELLER, SELLER, PRODUCT))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("自分自身");
      verify(messageRepository, never()).saveBoard(any());
    }
  }

  @Nested
  @DisplayName("getBoardWithMessages")
  class GetBoardWithMessages {
    @Test
    @DisplayName("参加者には掲示板とメッセージを返す")
    void returnsForParticipant() {
      Board board = new Board(SELLER, BUYER, PRODUCT);
      Message message = new Message(BOARD_ID, SELLER, BUYER, "hi", null);
      when(messageRepository.findBoardById(any(BoardId.class))).thenReturn(Optional.of(board));
      when(messageRepository.findMessagesByBoardId(any(BoardId.class)))
          .thenReturn(List.of(message));

      BoardMessages result = messageService.getBoardWithMessages(BOARD_ID, SELLER);

      assertThat(result.board()).isSameAs(board);
      assertThat(result.messages()).containsExactly(message);
    }

    @Test
    @DisplayName("掲示板が存在しなければ拒否する")
    void rejectsMissingBoard() {
      when(messageRepository.findBoardById(any(BoardId.class))).thenReturn(Optional.empty());

      assertThatThrownBy(() -> messageService.getBoardWithMessages(BOARD_ID, SELLER))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("掲示板が見つかりません");
    }

    @Test
    @DisplayName("参加者でなければアクセスを拒否する")
    void rejectsNonParticipant() {
      Board board = new Board(SELLER, BUYER, PRODUCT);
      when(messageRepository.findBoardById(any(BoardId.class))).thenReturn(Optional.of(board));

      assertThatThrownBy(() -> messageService.getBoardWithMessages(BOARD_ID, STRANGER))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("権限がありません");
      verify(messageRepository, never()).findMessagesByBoardId(any());
    }
  }

  @Nested
  @DisplayName("findParticipatingBoards")
  class FindParticipatingBoards {
    @Test
    @DisplayName("リポジトリの返す参加掲示板（更新日時降順）をそのまま返す")
    void returnsRepositoryResult() {
      Board first = new Board(SELLER, BUYER, PRODUCT);
      Board second = new Board(STRANGER, SELLER, 31);
      when(messageRepository.findBoardsByParticipant(SELLER))
          .thenReturn(List.of(first, second));

      List<Board> result = messageService.findParticipatingBoards(SELLER);

      assertThat(result).containsExactly(first, second);
      verify(messageRepository).findBoardsByParticipant(SELLER);
    }

    @Test
    @DisplayName("参加掲示板が無ければ空リストを返す")
    void returnsEmptyWhenNone() {
      when(messageRepository.findBoardsByParticipant(SELLER)).thenReturn(List.of());

      assertThat(messageService.findParticipatingBoards(SELLER)).isEmpty();
    }
  }

  @Nested
  @DisplayName("sendMessage")
  class SendMessage {
    @Test
    @DisplayName("送信者から見た取引相手を宛先にして保存する")
    void sendsToPartner() {
      Board board = new Board(SELLER, BUYER, PRODUCT);
      when(messageRepository.findBoardById(any(BoardId.class))).thenReturn(Optional.of(board));
      when(messageRepository.saveMessage(any(Message.class))).thenReturn(MessageId.fromLong(1));

      messageService.sendMessage(BOARD_ID, SELLER, "よろしくお願いします");

      ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
      verify(messageRepository).saveMessage(captor.capture());
      Message saved = captor.getValue();
      assertThat(saved.getFromUser()).isEqualTo(SELLER);
      assertThat(saved.getToUser()).isEqualTo(BUYER);
      assertThat(saved.getMsg()).isEqualTo("よろしくお願いします");
      assertThat(saved.getBordId()).isEqualTo(BOARD_ID);
    }

    @Test
    @DisplayName("参加者でなければ送信を拒否する")
    void rejectsNonParticipant() {
      Board board = new Board(SELLER, BUYER, PRODUCT);
      when(messageRepository.findBoardById(any(BoardId.class))).thenReturn(Optional.of(board));

      assertThatThrownBy(() -> messageService.sendMessage(BOARD_ID, STRANGER, "hi"))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("権限がありません");
      verify(messageRepository, never()).saveMessage(any());
    }

    @Test
    @DisplayName("空の本文は拒否する")
    void rejectsEmptyContent() {
      Board board = new Board(SELLER, BUYER, PRODUCT);
      when(messageRepository.findBoardById(any(BoardId.class))).thenReturn(Optional.of(board));

      assertThatThrownBy(() -> messageService.sendMessage(BOARD_ID, SELLER, ""))
          .isInstanceOf(ValidationException.class);
      verify(messageRepository, never()).saveMessage(any());
    }
  }
}
