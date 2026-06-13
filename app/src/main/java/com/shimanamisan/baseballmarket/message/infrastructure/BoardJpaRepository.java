package com.shimanamisan.baseballmarket.message.infrastructure;

import com.shimanamisan.baseballmarket.message.domain.Board;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BoardJpaRepository extends JpaRepository<Board, Integer> {

  @Query("select b from Board b where b.id = :id and b.deleteFlg = 0")
  Optional<Board> findByIdAlive(Integer id);

  @Query(
      "select b from Board b "
          + "where (b.saleUser = :userId or b.buyUser = :userId) and b.deleteFlg = 0 "
          + "order by b.updatedAt desc")
  List<Board> findByParticipantAlive(@Param("userId") Integer userId);
}
