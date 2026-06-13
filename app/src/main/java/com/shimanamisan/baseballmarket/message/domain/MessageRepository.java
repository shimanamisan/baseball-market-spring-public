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

  /**
   * 指定ユーザーが出品者または購入者として参加している掲示板を、更新日時の降順で返す。
   * 削除済み（delete_flg != 0）の掲示板は除外する。mypage の参加掲示板一覧が消費する。
   */
  List<Board> findBoardsByParticipant(int userId);
}
