package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Category;
import com.shimanamisan.baseballmarket.product.domain.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepositoryImpl implements CategoryRepository {

  private final CategoryJpaRepository jpa;

  public CategoryRepositoryImpl(CategoryJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Category> findAll() {
    return jpa.findAllAlive();
  }

  @Override
  public Optional<Category> findById(Integer id) {
    return id == null ? Optional.empty() : jpa.findByIdAlive(id);
  }
}
