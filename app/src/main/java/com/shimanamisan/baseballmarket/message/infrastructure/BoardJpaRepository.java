package com.shimanamisan.baseballmarket.message.infrastructure;

import com.shimanamisan.baseballmarket.message.domain.Board;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BoardJpaRepository extends JpaRepository<Board, Integer> {

  @Query("select b from Board b where b.id = :id and b.deleteFlg = 0")
  Optional<Board> findByIdAlive(Integer id);
}
