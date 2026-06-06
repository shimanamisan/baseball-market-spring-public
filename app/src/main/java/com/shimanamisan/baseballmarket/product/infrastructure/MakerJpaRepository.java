package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Maker;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MakerJpaRepository extends JpaRepository<Maker, Integer> {

  @Query("select m from Maker m where m.deleteFlg = 0 order by m.id")
  List<Maker> findAllAlive();

  @Query("select m from Maker m where m.id = :id and m.deleteFlg = 0")
  Optional<Maker> findByIdAlive(Integer id);
}
