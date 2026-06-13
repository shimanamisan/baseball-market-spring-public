package com.shimanamisan.baseballmarket.like.domain;

import java.util.List;

/**
 * お気に入りリポジトリインターフェース。
 *
 * 旧 PHP LikeRepository を踏襲しつつ、context 跨ぎを避けるため商品エンティティではなく
 * 商品ID 一覧（findProductIdsByUserId）を返す。商品の表示情報は呼び出し元が product context から取得する。
 */
public interface LikeRepository {

  /** お気に入りが存在するか。 */
  boolean exists(int userId, int productId);

  /** お気に入りを追加する。 */
  void add(int userId, int productId);

  /** お気に入りを削除する（物理削除）。 */
  void remove(int userId, int productId);

  /** ユーザーのお気に入り商品ID一覧を新しい順で返す。 */
  List<Integer> findProductIdsByUserId(int userId);
}
