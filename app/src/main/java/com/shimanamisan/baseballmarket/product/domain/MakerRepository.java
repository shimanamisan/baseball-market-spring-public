package com.shimanamisan.baseballmarket.product.domain;

import java.util.List;
import java.util.Optional;

public interface MakerRepository {

  List<Maker> findAll();

  Optional<Maker> findById(Integer id);
}
