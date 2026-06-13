package com.shimanamisan.baseballmarket.like.application;

import com.shimanamisan.baseballmarket.like.domain.LikeRepository;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * like context のアプリケーションサービス。
 *
 * 旧 PHP LikeService の toggleLike / isLiked / getUserLikedProducts を移植する。
 * userId / productId は他 context から ID 値で受け取り、商品・ユーザーの存在確認は呼び出し元が担保する
 * （旧実装同様、ここでは検証せず FK 制約に委ねる）。
 */
@Service
@Transactional
public class LikeService {

  private final LikeRepository likeRepository;

  public LikeService(LikeRepository likeRepository) {
    this.likeRepository = likeRepository;
  }

  /**
   * お気に入りをトグルする。登録済みなら削除、未登録なら追加。
   *
   * @throws ValidationException userId / productId が 0 以下の場合
   */
  public ToggleResult toggleLike(int userId, int productId) {
    if (userId <= 0) {
      throw new ValidationException("ユーザーIDが不正です");
    }
    if (productId <= 0) {
      throw new ValidationException("商品IDが不正です");
    }

    if (likeRepository.exists(userId, productId)) {
      likeRepository.remove(userId, productId);
      return ToggleResult.REMOVE;
    }
    likeRepository.add(userId, productId);
    return ToggleResult.ADD;
  }

  /**
   * お気に入り済みかどうか。userId が不正（未ログインゲスト等）の場合は false を返す。
   */
  @Transactional(readOnly = true)
  public boolean isLiked(int userId, int productId) {
    if (userId <= 0 || productId <= 0) {
      return false;
    }
    return likeRepository.exists(userId, productId);
  }

  /**
   * ユーザーのお気に入り商品ID一覧（新しい順）。後続フェーズ 8b の mypage が消費する。
   * userId が不正な場合は空リストを返す。
   */
  @Transactional(readOnly = true)
  public List<Integer> getUserLikedProductIds(int userId) {
    if (userId <= 0) {
      return List.of();
    }
    return likeRepository.findProductIdsByUserId(userId);
  }
}
