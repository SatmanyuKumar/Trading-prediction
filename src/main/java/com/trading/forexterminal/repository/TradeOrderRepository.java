package com.trading.forexterminal.repository;

import com.trading.forexterminal.entity.TradeOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeOrderRepository extends JpaRepository<TradeOrderEntity, String> {
    List<TradeOrderEntity> findByStatusInOrderByOpenTimeDesc(List<String> statuses);
    List<TradeOrderEntity> findByStatusNotInOrderByCloseTimeDesc(List<String> statuses);
    List<TradeOrderEntity> findAllByOrderByOpenTimeDesc();
}
