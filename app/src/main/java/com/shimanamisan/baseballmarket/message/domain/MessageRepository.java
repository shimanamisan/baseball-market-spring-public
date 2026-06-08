package com.shimanamisan.baseballmarket.message.domain;

import java.util.List;
import java.util.Optional;

/**
 * Message 集約（boards / messages）のリポジトリインターフェース。
 * 実装は message.infrastructure.MessageRepositoryImpl（Spring Data JPA を委譲利用）。
 */
public interface MessageRepository {

  BoardId saveBoard(Board board);

  Optional<Board> findBoardById(BoardId id);

  List<Message> findMessagesByBoardId(BoardId boardId);

  MessageId saveMessage(Message message);
}
