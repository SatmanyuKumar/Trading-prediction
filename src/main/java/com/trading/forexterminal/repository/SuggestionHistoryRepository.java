package com.trading.forexterminal.repository;

import com.trading.forexterminal.entity.SuggestionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuggestionHistoryRepository extends JpaRepository<SuggestionHistoryEntity, String> {
    List<SuggestionHistoryEntity> findAllByOrderBySuggestedTimeDesc();
}
