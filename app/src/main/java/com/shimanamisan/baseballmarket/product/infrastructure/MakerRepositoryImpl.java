package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Maker;
import com.shimanamisan.baseballmarket.product.domain.MakerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MakerRepositoryImpl implements MakerRepository {

  private final MakerJpaRepository jpa;

  public MakerRepositoryImpl(MakerJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<Maker> findAll() {
    return jpa.findAllAlive();
  }

  @Override
  public Optional<Maker> findById(Integer id) {
    return id == null ? Optional.empty() : jpa.findByIdAlive(id);
  }
}
