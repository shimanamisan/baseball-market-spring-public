package com.shimanamisan.baseballmarket.user.infrastructure;

import com.shimanamisan.baseballmarket.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserJpaRepository extends JpaRepository<User, Integer> {

  @Query("select u from User u left join fetch u.profile where u.email = :email and u.deleteFlg = 0")
  Optional<User> findByEmailAlive(String email);

  @Query("select u from User u left join fetch u.profile where u.id = :id and u.deleteFlg = 0")
  Optional<User> findByIdAlive(Integer id);

  @Query("select count(u) > 0 from User u where u.email = :email and u.deleteFlg = 0")
  boolean existsByEmailAlive(String email);

  // 退会時の波及論理削除。products/likes/boards/messages テーブルは別 context が管理する想定だが、
  // 旧 PHP 仕様維持のためここで直接 UPDATE を行う。nativeQuery で対象テーブルを直接叩く。
  @Modifying
  @Query(value = "UPDATE products SET delete_flg = 1 WHERE user_id = :userId", nativeQuery = true)
  void softDeleteProductsByUserId(Integer userId);

  @Modifying
  @Query(value = "UPDATE likes SET delete_flg = 1 WHERE user_id = :userId", nativeQuery = true)
  void softDeleteLikesByUserId(Integer userId);

  @Modifying
  @Query(value = "UPDATE boards SET delete_flg = 1 WHERE sale_user = :userId OR buy_user = :userId", nativeQuery = true)
  void softDeleteBoardsByUserId(Integer userId);

  @Modifying
  @Query(value = "UPDATE messages SET delete_flg = 1 WHERE from_user = :userId OR to_user = :userId", nativeQuery = true)
  void softDeleteMessagesByUserId(Integer userId);
}
