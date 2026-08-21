package com.trading.forexterminal.controller;

import com.trading.forexterminal.model.*;
import com.trading.forexterminal.service.MarketDataService;
import com.trading.forexterminal.service.TradeHistoryService;
import com.trading.forexterminal.service.VantageBridgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MarketController {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private VantageBridgeService vantageBridgeService;

    @Autowired
    private TradeHistoryService tradeHistoryService;

    @GetMapping("/pairs")
    public List<Map<String, String>> getPairs() {
        return List.of(
                Map.of("symbol", "XAUUSD", "name", "Gold vs US Dollar", "category", "Precious Metals"),
                Map.of("symbol", "EURUSD", "name", "Euro vs US Dollar", "category", "Forex Major"),
                Map.of("symbol", "GBPUSD", "name", "British Pound vs US Dollar", "category", "Forex Major"),
                Map.of("symbol", "USDJPY", "name", "US Dollar vs Japanese Yen", "category", "Forex Major"),
                Map.of("symbol", "BTCUSD", "name", "Bitcoin vs US Dollar", "category", "Crypto")
        );
    }

    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> getAccount() {
        Map<String, Object> vantage = vantageBridgeService.getVantageStatus();
        Map<String, Object> acc = new HashMap<>(vantage);
        acc.put("initialBalance", marketDataService.getInitialDemoBalance());
        acc.put("currency", "USD");
        acc.put("vantageConnected", vantage.get("connected"));
        acc.put("vantageAccount", vantage.get("accountNumber"));
        acc.put("vantageServer", vantage.get("server"));
        return ResponseEntity.ok(acc);
    }

    @PostMapping("/account/balance")
    public ResponseEntity<Map<String, Object>> setAccountBalance(@RequestBody Map<String, Object> req) {
        double newBalance = Double.parseDouble(req.getOrDefault("balance", "50000").toString());
        marketDataService.setDemoBalance(newBalance);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "balance", marketDataService.getDemoBalance()
        ));
    }

    @GetMapping("/vantage/status")
    public ResponseEntity<Map<String, Object>> getVantageStatus() {
        return ResponseEntity.ok(vantageBridgeService.getVantageStatus());
    }

    @GetMapping("/vantage/pending-orders")
    public ResponseEntity<List<Map<String, Object>>> getPendingOrders() {
        return ResponseEntity.ok(vantageBridgeService.pollPendingOrders());
    }

    @PostMapping("/vantage/connect")
    public ResponseEntity<Map<String, Object>> connectVantage(@RequestBody Map<String, Object> req) {
        String account = (String) req.getOrDefault("account", "25951798");
        String server = (String) req.getOrDefault("server", "VantageMarkets-Demo");

        vantageBridgeService.setSavedVantageCredentials(account, server);
        return ResponseEntity.ok(Map.of(
                "status", "CONNECTED",
                "account", account,
                "server", server
        ));
    }

    @PostMapping("/vantage/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectVantage() {
        vantageBridgeService.disconnectVantage();
        return ResponseEntity.ok(Map.of("status", "DISCONNECTED"));
    }

    @PostMapping("/vantage/webhook")
    public ResponseEntity<Map<String, Object>> vantageWebhook(@RequestBody String rawPayload) {
        vantageBridgeService.processIncomingVantageMessage(rawPayload);
        return ResponseEntity.ok(Map.of("status", "PROCESSED"));
    }

    @GetMapping("/analysis")
    public ResponseEntity<AnalysisResult> getAnalysis(
            @RequestParam(name = "symbol", defaultValue = "XAUUSD") String symbol,
            @RequestParam(name = "timeframe", defaultValue = "1m") String timeframe,
            @RequestParam(name = "tradeMode", defaultValue = "SCALP") String tradeMode) {
        AnalysisResult analysis = marketDataService.getAnalysis(symbol, timeframe, tradeMode);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/advisor")
    public ResponseEntity<TradeAdvisorResult> getTradeAdvisor(
            @RequestParam(name = "symbol", defaultValue = "XAUUSD") String symbol,
            @RequestParam(name = "timeframe", defaultValue = "1m") String timeframe,
            @RequestParam(name = "tradeMode", defaultValue = "SCALP") String tradeMode,
            @RequestParam(name = "lotSize", defaultValue = "0.10") double lotSize) {
        TradeAdvisorResult advisor = marketDataService.getTradeAdvisor(symbol, timeframe, tradeMode, lotSize);
        return ResponseEntity.ok(advisor);
    }

    @GetMapping("/radar")
    public ResponseEntity<List<TradeRadarItem>> getFutureSetupRadar() {
        return ResponseEntity.ok(marketDataService.getFutureSetupRadar());
    }

    @GetMapping("/suggestions/history")
    public ResponseEntity<List<SuggestionHistoryItem>> getSuggestionHistory() {
        return ResponseEntity.ok(marketDataService.getSuggestionHistory());
    }

    @DeleteMapping("/suggestions/history")
    public ResponseEntity<Map<String, Object>> clearSuggestionHistory() {
        int count = marketDataService.clearSuggestionHistory();
        return ResponseEntity.ok(Map.of("status", "CLEARED", "count", count));
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody Map<String, Object> req) {
        String symbol = (String) req.getOrDefault("symbol", "XAUUSD");
        String type = (String) req.getOrDefault("type", "BUY");
        String timeframe = (String) req.getOrDefault("timeframe", "15m");
        double lotSize = Double.parseDouble(req.getOrDefault("lotSize", "1.0").toString());
        double entry = Double.parseDouble(req.getOrDefault("entry", "0").toString());
        double sl = Double.parseDouble(req.getOrDefault("sl", "0").toString());
        double tp = Double.parseDouble(req.getOrDefault("tp", "0").toString());

        // Place in internal MarketDataService (with Timeframe Priority Anti-Hedging Queue)
        TradeOrder order = marketDataService.placeOrder(symbol, type, timeframe, lotSize, entry, sl, tp);

        // If NOT queued for HTF wait, send to Vantage MT5 immediately
        if (!"PENDING_HTF_QUEUE".equals(order.getStatus())) {
            vantageBridgeService.queueOrder(symbol, type, lotSize, sl, tp);
        }

        return ResponseEntity.ok(Map.of(
                "status", order.getStatus(),
                "orderId", order.getId(),
                "symbol", symbol,
                "type", type,
                "timeframe", timeframe,
                "lots", lotSize,
                "trailingStatus", order.getTrailingStatus()
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<?>> getOrders() {
        Map<String, Object> status = vantageBridgeService.getVantageStatus();
        boolean isVantage = Boolean.TRUE.equals(status.get("connected"));

        List<Object> merged = new ArrayList<>();
        if (isVantage) {
            merged.addAll(vantageBridgeService.getLiveVantagePositions());
        }
        for (TradeOrder o : marketDataService.getOrders()) {
            if ("OPEN".equals(o.getStatus()) || "PENDING_LIMIT".equals(o.getStatus()) || "PENDING_HTF_QUEUE".equals(o.getStatus())) {
                merged.add(o);
            }
        }
        merged.addAll(tradeHistoryService.getAllHistory());
        return ResponseEntity.ok(merged);
    }

    @PostMapping("/orders/{id}/close")
    public ResponseEntity<Map<String, Object>> closeOrder(@PathVariable(name = "id") String id) {
        vantageBridgeService.queueCloseOrder(id);
        marketDataService.closeOrder(id);
        return ResponseEntity.ok(Map.of("status", "CLOSE_QUEUED", "id", id));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> deleteOrder(@PathVariable(name = "id") String id) {
        boolean deleted = marketDataService.deleteOrder(id);
        return ResponseEntity.ok(Map.of("deleted", deleted, "id", id));
    }

    @GetMapping("/orders/history")
    public ResponseEntity<List<TradeOrder>> getTradeHistory() {
        return ResponseEntity.ok(tradeHistoryService.getAllHistory());
    }

    @GetMapping("/orders/export/csv")
    public ResponseEntity<String> exportCsvJournal() {
        String csv = tradeHistoryService.generateCsvJournal();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"trading_journal_history.csv\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csv);
    }

    @DeleteMapping("/orders/history")
    public ResponseEntity<Map<String, Object>> clearTradeHistory() {
        int count = marketDataService.clearTradeHistory();
        return ResponseEntity.ok(Map.of("clearedCount", count, "status", "SUCCESS"));
    }

    @GetMapping(value = "/Trading_Terminal_Engineering_Whitepaper.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> getWhitepaperPdf() {
        try {
            java.io.File pdfFile = new java.io.File("Trading_Terminal_Engineering_Whitepaper.pdf");
            if (!pdfFile.exists()) {
                pdfFile = new java.io.File("src/main/resources/static/Trading_Terminal_Engineering_Whitepaper.pdf");
            }
            if (pdfFile.exists()) {
                byte[] bytes = java.nio.file.Files.readAllBytes(pdfFile.toPath());
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"Trading_Terminal_Engineering_Whitepaper.pdf\"")
                        .header("Content-Type", "application/pdf")
                        .body(bytes);
            }
        } catch (Exception ignored) {}
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/terminal/clean-all")
    public ResponseEntity<Map<String, Object>> cleanAllTerminalHistory() {
        int suggCount = marketDataService.clearSuggestionHistory();
        int histCount = marketDataService.clearTradeHistory();
        marketDataService.clearAllOrders();
        return ResponseEntity.ok(Map.of(
                "status", "ALL_CLEARED",
                "suggestionsCleared", suggCount,
                "historyCleared", histCount
        ));
    }

    @DeleteMapping("/orders/all")
    public ResponseEntity<Map<String, Object>> clearAllOrders() {
        marketDataService.clearAllOrders();
        return ResponseEntity.ok(Map.of("status", "CLEARED_ALL"));
    }
}
