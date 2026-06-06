package com.shimanamisan.baseballmarket.user.infrastructure;

import com.shimanamisan.baseballmarket.user.domain.EmailVerificationTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface EmailVerificationTokenJpaRepository
    extends JpaRepository<EmailVerificationTokenEntity, Integer> {

  Optional<EmailVerificationTokenEntity> findByToken(String token);

  @Modifying
  @Query("delete from EmailVerificationTokenEntity e where e.userId = :userId")
  void deleteByUserId(Integer userId);
}
