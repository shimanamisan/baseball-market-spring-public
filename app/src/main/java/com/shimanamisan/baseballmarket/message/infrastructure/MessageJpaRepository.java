package com.shimanamisan.baseballmarket.message.infrastructure;

import com.shimanamisan.baseballmarket.message.domain.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MessageJpaRepository extends JpaRepository<Message, Integer> {

  @Query("select m from Message m where m.bordId = :bordId and m.deleteFlg = 0 order by m.sendAt asc")
  List<Message> findByBordIdAlive(Integer bordId);
}
