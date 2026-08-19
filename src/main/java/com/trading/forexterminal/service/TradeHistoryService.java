package com.trading.forexterminal.service;

import com.trading.forexterminal.entity.TradeOrderEntity;
import com.trading.forexterminal.model.TradeOrder;
import com.trading.forexterminal.repository.TradeOrderRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TradeHistoryService {

    @Autowired
    private TradeOrderRepository tradeOrderRepository;

    private final List<TradeOrder> persistentHistory = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        loadHistoryFromDatabase();
    }

    public synchronized void recordClosedTrade(TradeOrder order) {
        if (order == null) return;

        // 1. Save to Database
        try {
            TradeOrderEntity entity = toEntity(order);
            tradeOrderRepository.save(entity);
        } catch (Exception e) {
            System.err.println("DB Save Warning: " + e.getMessage());
        }

        // 2. In-memory cache update
        persistentHistory.removeIf(o -> o.getId().equals(order.getId()));
        persistentHistory.add(0, order);
    }

    public List<TradeOrder> getAllHistory() {
        return new ArrayList<>(persistentHistory);
    }

    public synchronized int clearHistory() {
        int count = persistentHistory.size();
        try {
            tradeOrderRepository.deleteAll();
        } catch (Exception ignored) {}
        persistentHistory.clear();
        return count;
    }

    public String generateCsvJournal() {
        StringBuilder csv = new StringBuilder();
        csv.append("Ticket ID,Symbol,Type,Lots,Entry Price,Close Price,Stop Loss,Take Profit,Net Profit ($),Status,Open Time,Close Time\n");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (TradeOrder o : persistentHistory) {
            String openDate = o.getOpenTime() > 0 ? sdf.format(new Date(o.getOpenTime())) : "-";
            String closeDate = o.getCloseTime() > 0 ? sdf.format(new Date(o.getCloseTime())) : "-";

            csv.append(String.format("\"%s\",\"%s\",\"%s\",%.2f,%.5f,%.5f,%.5f,%.5f,%.2f,\"%s\",\"%s\",\"%s\"\n",
                    o.getId(),
                    o.getSymbol(),
                    o.getType(),
                    o.getLotSize(),
                    o.getEntryPrice(),
                    o.getClosePrice(),
                    o.getStopLoss(),
                    o.getTakeProfit(),
                    o.getPnl(),
                    o.getStatus(),
                    openDate,
                    closeDate
            ));
        }

        return csv.toString();
    }

    private synchronized void loadHistoryFromDatabase() {
        try {
            List<TradeOrderEntity> entities = tradeOrderRepository.findByStatusNotInOrderByCloseTimeDesc(List.of("OPEN", "PENDING_LIMIT"));
            persistentHistory.clear();
            for (TradeOrderEntity e : entities) {
                persistentHistory.add(toModel(e));
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load trade history from DB: " + e.getMessage());
        }
    }

    private TradeOrderEntity toEntity(TradeOrder o) {
        return new TradeOrderEntity(
                o.getId(),
                o.getSymbol(),
                o.getType(),
                o.getTimeframe(),
                o.getEntryPrice(),
                o.getStopLoss(),
                o.getTakeProfit(),
                o.getLotSize(),
                o.getSpreadAtEntry(),
                o.getOpenTime(),
                o.getCloseTime(),
                o.getClosePrice(),
                o.getPnl(),
                o.getStatus(),
                o.getTrailingStatus()
        );
    }

    private TradeOrder toModel(TradeOrderEntity e) {
        TradeOrder o = new TradeOrder(
                e.getId(),
                e.getSymbol(),
                e.getType(),
                e.getTimeframe(),
                e.getEntryPrice(),
                e.getStopLoss(),
                e.getTakeProfit(),
                e.getLotSize(),
                e.getSpreadAtEntry(),
                e.getOpenTime()
        );
        o.setCloseTime(e.getCloseTime());
        o.setClosePrice(e.getClosePrice());
        o.setPnl(e.getPnl());
        o.setStatus(e.getStatus());
        o.setTrailingStatus(e.getTrailingStatus());
        return o;
    }
}
