package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductJpaRepository
    extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {

  @Query("select p from Product p where p.id = :id and p.deleteFlg = 0")
  Optional<Product> findByIdAlive(Integer id);

  /** 購入時の排他用に対象行を PESSIMISTIC_WRITE（SELECT ... FOR UPDATE）でロックして取得する。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id and p.deleteFlg = 0")
  Optional<Product> findByIdAliveForUpdate(Integer id);

  @Query("select p from Product p where p.id = :id and p.userId = :userId and p.deleteFlg = 0")
  Optional<Product> findByIdAndOwnerAlive(Integer id, Integer userId);

  @Query("select p from Product p where p.userId = :userId and p.deleteFlg = 0 order by p.id desc")
  List<Product> findByOwnerAlive(Integer userId);

  Page<Product> findAll(org.springframework.data.jpa.domain.Specification<Product> spec, Pageable pageable);
}
