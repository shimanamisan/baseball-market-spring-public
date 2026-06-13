package com.shimanamisan.baseballmarket.like.infrastructure;

import com.shimanamisan.baseballmarket.like.domain.Like;
import com.shimanamisan.baseballmarket.like.domain.LikeRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * LikeRepository の実装。Spring Data JPA を委譲利用する。
 *
 * 削除は物理削除（旧 PHP LikeRepositoryImpl::remove() が DELETE を発行していた挙動を踏襲）。
 * Spring Data の型はこのクラスの外へ漏らさない。
 */
@Repository
public class LikeRepositoryImpl implements LikeRepository {

  private final LikeJpaRepository jpaRepository;

  public LikeRepositoryImpl(LikeJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public boolean exists(int userId, int productId) {
    return jpaRepository.existsByUserIdAndProductId(userId, productId);
  }

  @Override
  public void add(int userId, int productId) {
    jpaRepository.save(new Like(userId, productId));
  }

  @Override
  public void remove(int userId, int productId) {
    jpaRepository.deleteByUserIdAndProductId(userId, productId);
  }

  @Override
  public List<Integer> findProductIdsByUserId(int userId) {
    return jpaRepository.findProductIdsByUserIdOrderByCreatedAtDesc(userId);
  }
}
