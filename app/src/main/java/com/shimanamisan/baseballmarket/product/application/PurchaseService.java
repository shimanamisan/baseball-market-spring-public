package com.shimanamisan.baseballmarket.product.application;

import com.shimanamisan.baseballmarket.message.application.MessageService;
import com.shimanamisan.baseballmarket.message.domain.BoardId;
import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 購入ユースケースの調整役。product と message の 2 context を跨ぐため専用サービスに集約する。
 *
 * 「商品を売却済みに更新」（product context）と「掲示板を作成」（message context）を
 * 1 トランザクションで原子的に実行する。MessageService.createBoard は @Transactional 既定
 * （REQUIRED）で本トランザクションに参加するため、いずれかが失敗すれば両方ロールバックされる。
 */
@Service
@Transactional
public class PurchaseService {

  private final ProductRepository productRepository;
  private final MessageService messageService;

  public PurchaseService(ProductRepository productRepository, MessageService messageService) {
    this.productRepository = productRepository;
    this.messageService = messageService;
  }

  /**
   * 商品を購入する。
   *
   * @param productId 購入対象の商品ID
   * @param buyerUserId 購入者のユーザーID
   * @return 作成された掲示板ID
   * @throws ValidationException 商品が存在しない / 自分の出品 / 売切れ済みの場合
   */
  public BoardId purchase(int productId, int buyerUserId) {
    // 二重購入対策: 対象商品を行ロック（FOR UPDATE）して取得する。同一商品への同時購入は
    // 直列化され、後続トランザクションはロック解放後に sold_out_flg=1 を読んで canPurchase で弾かれる。
    Product product =
        productRepository
            .findByIdForUpdate(ProductId.fromLong(productId))
            .orElseThrow(() -> new ValidationException("商品が見つかりません"));

    if (!product.canPurchase(buyerUserId)) {
      // 売却済みを優先して判定。競合に敗れた（他ユーザーが先に購入した）場合はここに該当し、
      // 「売却済み」という的確なメッセージを表示する。
      if (product.isSoldOut()) {
        throw new ValidationException("申し訳ありません。この商品はすでに売却済みです");
      }
      throw new ValidationException("自分が出品した商品は購入できません");
    }

    product.markAsSold();

    return messageService.createBoard(product.getUserId(), buyerUserId, productId);
  }
}
