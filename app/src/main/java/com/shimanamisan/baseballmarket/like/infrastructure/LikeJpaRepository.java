package com.shimanamisan.baseballmarket.like.infrastructure;

import com.shimanamisan.baseballmarket.like.domain.Like;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LikeJpaRepository extends JpaRepository<Like, Integer> {

  boolean existsByUserIdAndProductId(Integer userId, Integer productId);

  @Modifying(clearAutomatically = true)
  @Query("delete from Like l where l.userId = :userId and l.productId = :productId")
  void deleteByUserIdAndProductId(@Param("userId") Integer userId, @Param("productId") Integer productId);

  @Query("select l.productId from Like l where l.userId = :userId order by l.createdAt desc")
  List<Integer> findProductIdsByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);
}
