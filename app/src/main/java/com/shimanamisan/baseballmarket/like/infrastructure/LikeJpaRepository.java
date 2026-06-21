package com.shimanamisan.baseballmarket.like.infrastructure;

import com.shimanamisan.baseballmarket.like.domain.Like;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LikeJpaRepository extends JpaRepository<Like, Integer> {

  boolean existsByUserIdAndProductId(Integer userId, Integer productId);

  // 派生クエリ削除（SELECT→remove 方式）。@Modifying のバルク削除は永続化コンテキストを
  // バイパスするため clearAutomatically での全体 clear が必要になり、同一 tx 内の他エンティティを
  // デタッチさせる副作用がある。本メソッドはその副作用を持たず、1次キャッシュ不整合も生じない。
  void deleteByUserIdAndProductId(Integer userId, Integer productId);

  @Query("select l.productId from Like l where l.userId = :userId order by l.createdAt desc")
  List<Integer> findProductIdsByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);
}
