package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CategoryJpaRepository extends JpaRepository<Category, Integer> {

  @Query("select c from Category c where c.deleteFlg = 0 order by c.id")
  List<Category> findAllAlive();

  @Query("select c from Category c where c.id = :id and c.deleteFlg = 0")
  Optional<Category> findByIdAlive(Integer id);
}
