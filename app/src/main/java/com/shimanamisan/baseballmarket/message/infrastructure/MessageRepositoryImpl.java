package com.shimanamisan.baseballmarket.message.infrastructure;

import com.shimanamisan.baseballmarket.message.domain.Board;
import com.shimanamisan.baseballmarket.message.domain.BoardId;
import com.shimanamisan.baseballmarket.message.domain.Message;
import com.shimanamisan.baseballmarket.message.domain.MessageId;
import com.shimanamisan.baseballmarket.message.domain.MessageRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepositoryImpl implements MessageRepository {

  private final BoardJpaRepository boardJpa;
  private final MessageJpaRepository messageJpa;

  public MessageRepositoryImpl(BoardJpaRepository boardJpa, MessageJpaRepository messageJpa) {
    this.boardJpa = boardJpa;
    this.messageJpa = messageJpa;
  }

  @Override
  public BoardId saveBoard(Board board) {
    Board saved = boardJpa.save(board);
    return BoardId.fromLong(saved.getId().longValue());
  }

  @Override
  public Optional<Board> findBoardById(BoardId id) {
    return boardJpa.findByIdAlive(toInt(id.value(), "BoardId"));
  }

  @Override
  public List<Message> findMessagesByBoardId(BoardId boardId) {
    return messageJpa.findByBordIdAlive(toInt(boardId.value(), "BoardId"));
  }

  @Override
  public MessageId saveMessage(Message message) {
    Message saved = messageJpa.save(message);
    return MessageId.fromLong(saved.getId().longValue());
  }

  private static int toInt(long value, String label) {
    if (value > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(label + " out of INT range: " + value);
    }
    return (int) value;
  }
}
