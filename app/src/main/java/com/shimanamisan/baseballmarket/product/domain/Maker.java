package com.shimanamisan.baseballmarket.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "makers")
public class Maker {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "maker_name", nullable = false, length = 255)
  private String name;

  @Column(name = "delete_flg", nullable = false)
  private Byte deleteFlg = 0;

  protected Maker() {}

  public Integer getId() { return id; }
  public String getName() { return name; }
  public boolean isDeleted() { return deleteFlg != null && deleteFlg != 0; }
}
