package com.shimanamisan.baseballmarket.message.application;

import com.shimanamisan.baseballmarket.message.domain.Board;
import com.shimanamisan.baseballmarket.message.domain.BoardId;
import com.shimanamisan.baseballmarket.message.domain.Message;
import com.shimanamisan.baseballmarket.message.domain.MessageContent;
import com.shimanamisan.baseballmarket.message.domain.MessageId;
import com.shimanamisan.baseballmarket.message.domain.MessageRepository;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Message context のアプリケーションサービス。
 *
 * boards / messages の操作に責務を限定し、商品・ユーザーの存在確認や表示用情報の取得は
 * 呼び出し元（PurchaseService / MessageController）が他 context の application API 経由で行う。
 */
@Service
@Transactional
public class MessageService {

  private final MessageRepository messageRepository;

  public MessageService(MessageRepository messageRepository) {
    this.messageRepository = messageRepository;
  }

  /**
   * 掲示板を作成する。商品の存在確認は呼び出し元が担保する。
   *
   * @throws ValidationException 出品者と購入者が同一の場合
   */
  public BoardId createBoard(int saleUserId, int buyUserId, int productId) {
    if (saleUserId == buyUserId) {
      throw new ValidationException("自分自身との掲示板は作成できません");
    }
    return messageRepository.saveBoard(new Board(saleUserId, buyUserId, productId));
  }

  /**
   * 掲示板とメッセージ一覧を取得する。参加者以外はアクセス不可。
   *
   * @throws ValidationException 掲示板が存在しない / 参加者でない場合
   */
  @Transactional(readOnly = true)
  public BoardMessages getBoardWithMessages(int boardId, int currentUserId) {
    Board board = findParticipatingBoard(boardId, currentUserId);
    List<Message> messages = messageRepository.findMessagesByBoardId(board.getBoardId());
    return new BoardMessages(board, messages);
  }

  /**
   * メッセージを送信する。送信先は掲示板から取引相手を導出する。
   *
   * @throws ValidationException 掲示板が存在しない / 参加者でない / 本文が不正な場合
   */
  public MessageId sendMessage(int boardId, int fromUserId, String content) {
    Board board = findParticipatingBoard(boardId, fromUserId);
    Integer toUserId = board.getPartnerUserId(fromUserId);
    if (toUserId == null) {
      throw new ValidationException("送信先のユーザーが特定できません");
    }
    MessageContent body = MessageContent.fromString(content);
    Message message =
        new Message(boardId, fromUserId, toUserId, body.value(), LocalDateTime.now());
    return messageRepository.saveMessage(message);
  }

  private Board findParticipatingBoard(int boardId, int userId) {
    Board board =
        messageRepository
            .findBoardById(BoardId.fromLong(boardId))
            .orElseThrow(() -> new ValidationException("掲示板が見つかりません"));
    if (!board.isParticipant(userId)) {
      throw new ValidationException("この掲示板にアクセスする権限がありません");
    }
    return board;
  }
}
