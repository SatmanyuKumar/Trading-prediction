package com.trading.forexterminal.service;

import com.trading.forexterminal.entity.SuggestionHistoryEntity;
import com.trading.forexterminal.model.SuggestionHistoryItem;
import com.trading.forexterminal.model.TradeRadarItem;
import com.trading.forexterminal.repository.SuggestionHistoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SuggestionHistoryService {

    @Autowired
    private SuggestionHistoryRepository suggestionHistoryRepository;

    private final List<SuggestionHistoryItem> suggestionHistory = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        loadHistoryFromDatabase();
    }

    public synchronized void recordSuggestion(TradeRadarItem item) {
        if (item == null) return;
        boolean exists = suggestionHistory.stream().anyMatch(s -> s.getId().equals(item.getId()));
        if (!exists) {
            SuggestionHistoryItem sugg = new SuggestionHistoryItem(
                    item.getId(),
                    item.getSymbol(),
                    item.getTimeframe(),
                    item.getMode(),
                    item.getSignal(),
                    item.getEntryPrice(),
                    item.getStopLoss(),
                    item.getTakeProfit(),
                    item.getRiskRewardRatio(),
                    item.getConfidence(),
                    "ACTIVE_IN_ZONE".equals(item.getStatus()) ? "READY_EXECUTED" : "PENDING_WAIT",
                    item.getTimestamp()
            );

            // 1. Save to Database
            try {
                suggestionHistoryRepository.save(toEntity(sugg));
            } catch (Exception e) {
                System.err.println("Suggestion DB Save Warning: " + e.getMessage());
            }

            // 2. In-memory cache update
            suggestionHistory.add(0, sugg);
            if (suggestionHistory.size() > 200) {
                suggestionHistory.remove(suggestionHistory.size() - 1);
            }
        }
    }

    public synchronized void updateSuggestionExecution(String id, double exitPrice, double pnl, String outcome) {
        for (SuggestionHistoryItem item : suggestionHistory) {
            if (item.getId().equals(id)) {
                item.setExitPrice(exitPrice);
                item.setPnl(pnl);
                item.setTriggerState(outcome);
                item.setClosedTime(System.currentTimeMillis());
                try {
                    suggestionHistoryRepository.save(toEntity(item));
                } catch (Exception ignored) {}
                break;
            }
        }
    }

    public List<SuggestionHistoryItem> getAllSuggestions() {
        return new ArrayList<>(suggestionHistory);
    }

    public synchronized int clearSuggestionHistory() {
        int count = suggestionHistory.size();
        try {
            suggestionHistoryRepository.deleteAll();
        } catch (Exception ignored) {}
        suggestionHistory.clear();
        return count;
    }

    private synchronized void loadHistoryFromDatabase() {
        try {
            List<SuggestionHistoryEntity> entities = suggestionHistoryRepository.findAllByOrderBySuggestedTimeDesc();
            suggestionHistory.clear();
            for (SuggestionHistoryEntity e : entities) {
                suggestionHistory.add(toModel(e));
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load suggestions from DB: " + e.getMessage());
        }
    }

    private SuggestionHistoryEntity toEntity(SuggestionHistoryItem s) {
        return new SuggestionHistoryEntity(
                s.getId(),
                s.getSymbol(),
                s.getTimeframe(),
                s.getMode(),
                s.getSignal(),
                s.getEntryPrice(),
                s.getStopLoss(),
                s.getTakeProfit(),
                s.getRiskRewardRatio(),
                s.getConfidence(),
                s.getTriggerState(),
                s.getExitPrice(),
                s.getPnl(),
                s.getSuggestedTime(),
                s.getExecutedTime(),
                s.getClosedTime()
        );
    }

    private SuggestionHistoryItem toModel(SuggestionHistoryEntity e) {
        SuggestionHistoryItem s = new SuggestionHistoryItem(
                e.getId(),
                e.getSymbol(),
                e.getTimeframe(),
                e.getMode(),
                e.getSignal(),
                e.getEntryPrice(),
                e.getStopLoss(),
                e.getTakeProfit(),
                e.getRiskRewardRatio(),
                e.getConfidence(),
                e.getTriggerState(),
                e.getSuggestedTime()
        );
        s.setExitPrice(e.getExitPrice());
        s.setPnl(e.getPnl());
        s.setExecutedTime(e.getExecutedTime());
        s.setClosedTime(e.getClosedTime());
        return s;
    }
}
