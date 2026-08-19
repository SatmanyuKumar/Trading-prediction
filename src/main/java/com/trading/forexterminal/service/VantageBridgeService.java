package com.trading.forexterminal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.forexterminal.model.TradeOrder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

@Service
public class VantageBridgeService {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private TradeHistoryService tradeHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServerSocket serverSocket;
    private final List<SocketHandler> connectedClients = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> pendingOrders = new ConcurrentLinkedQueue<>();
    private final List<Map<String, Object>> liveVantagePositions = new CopyOnWriteArrayList<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private volatile boolean isVantageConnected = true;
    private volatile String vantageAccountNumber = "25951798";
    private volatile String vantageServer = "VantageMarkets-Demo";
    private volatile double vantageBalance = 99878.0;
    private volatile double vantageEquity = 98861.0;
    private volatile double vantageMargin = 2613.82;
    private volatile double vantageFreeMargin = 96247.18;
    private volatile double vantageFloatingPnl = -1017.0;
    private volatile int vantageLeverage = 500;
    private volatile long lastHeartbeat = System.currentTimeMillis();

    @PostConstruct
    public void startSocketBridge() {
        marketDataService.setDemoBalance(99878.0);
        threadPool.submit(() -> {
            try {
                serverSocket = new ServerSocket(8222);
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    SocketHandler handler = new SocketHandler(clientSocket);
                    connectedClients.add(handler);
                    threadPool.submit(handler);
                }
            } catch (Exception ignored) {}
        });
    }

    @PreDestroy
    public void stopSocketBridge() {
        try {
            if (serverSocket != null) serverSocket.close();
            for (SocketHandler client : connectedClients) {
                client.close();
            }
        } catch (Exception ignored) {}
    }

    public Map<String, Object> getVantageStatus() {
        Map<String, Object> res = new HashMap<>();
        res.put("connected", isVantageConnected);
        res.put("accountNumber", isVantageConnected ? vantageAccountNumber : "Simulator Mode");
        res.put("server", vantageServer);
        res.put("balance", vantageBalance);
        res.put("equity", vantageEquity);
        res.put("margin", vantageMargin);
        res.put("freeMargin", vantageFreeMargin);
        res.put("floatingPnl", vantageFloatingPnl);
        res.put("leverage", "1:" + vantageLeverage);
        res.put("clientCount", connectedClients.size());
        res.put("pendingOrdersCount", pendingOrders.size());
        res.put("positionsCount", liveVantagePositions.size());
        return res;
    }

    public List<Map<String, Object>> getLiveVantagePositions() {
        return new ArrayList<>(liveVantagePositions);
    }

    public void queueOrder(String symbol, String type, double lots, double sl, double tp) {
        Map<String, Object> orderPayload = Map.of(
                "action", "OPEN_ORDER",
                "symbol", symbol,
                "type", type,
                "lots", lots,
                "sl", sl,
                "tp", tp,
                "comment", "ProSMC_AutoTrade",
                "timestamp", System.currentTimeMillis()
        );

        pendingOrders.add(orderPayload);

        try {
            String json = objectMapper.writeValueAsString(orderPayload) + "\n";
            for (SocketHandler client : connectedClients) {
                client.sendMessage(json);
            }
        } catch (Exception ignored) {}
    }

    public void queueCloseOrder(String ticket) {
        Map<String, Object> closePayload = Map.of(
                "action", "CLOSE_ORDER",
                "ticket", ticket,
                "timestamp", System.currentTimeMillis()
        );

        pendingOrders.add(closePayload);

        try {
            String json = objectMapper.writeValueAsString(closePayload) + "\n";
            for (SocketHandler client : connectedClients) {
                client.sendMessage(json);
            }
        } catch (Exception ignored) {}
    }

    public List<Map<String, Object>> pollPendingOrders() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> ord;
        while ((ord = pendingOrders.poll()) != null) {
            list.add(ord);
        }
        return list;
    }

    public void processIncomingVantageMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.has("type") ? node.get("type").asText() : "";

            this.isVantageConnected = true;
            this.lastHeartbeat = System.currentTimeMillis();

            if (node.has("account")) this.vantageAccountNumber = node.get("account").asText();
            if (node.has("server")) this.vantageServer = node.get("server").asText();
            if (node.has("balance")) {
                this.vantageBalance = node.get("balance").asDouble();
                marketDataService.setDemoBalance(this.vantageBalance);
            }
            if (node.has("equity")) this.vantageEquity = node.get("equity").asDouble();
            if (node.has("margin")) this.vantageMargin = node.get("margin").asDouble();
            if (node.has("freeMargin")) this.vantageFreeMargin = node.get("freeMargin").asDouble();
            if (node.has("leverage")) this.vantageLeverage = node.get("leverage").asInt();

            this.vantageFloatingPnl = this.vantageEquity - this.vantageBalance;

            // Direct Sync of Live Positions from MT5
            if (node.has("positions") && node.get("positions").isArray()) {
                Set<String> currentTickets = new HashSet<>();
                List<Map<String, Object>> newPositions = new ArrayList<>();

                for (JsonNode posNode : node.get("positions")) {
                    String ticket = posNode.has("ticket") ? posNode.get("ticket").asText() : "T-" + UUID.randomUUID();
                    currentTickets.add(ticket);

                    Map<String, Object> pos = new HashMap<>();
                    pos.put("id", ticket);
                    pos.put("symbol", posNode.has("symbol") ? posNode.get("symbol").asText().toUpperCase() : "XAUUSD");
                    pos.put("type", posNode.has("type") ? posNode.get("type").asText() : "BUY");
                    pos.put("lotSize", posNode.has("lots") ? posNode.get("lots").asDouble() : 1.0);
                    pos.put("entryPrice", posNode.has("entryPrice") ? posNode.get("entryPrice").asDouble() : 0.0);
                    pos.put("stopLoss", posNode.has("sl") ? posNode.get("sl").asDouble() : 0.0);
                    pos.put("takeProfit", posNode.has("tp") ? posNode.get("tp").asDouble() : 0.0);
                    pos.put("closePrice", posNode.has("currentPrice") ? posNode.get("currentPrice").asDouble() : 0.0);
                    pos.put("pnl", posNode.has("pnl") ? posNode.get("pnl").asDouble() : 0.0);
                    pos.put("status", "OPEN");
                    pos.put("openTime", posNode.has("time") ? posNode.get("time").asLong() : System.currentTimeMillis());

                    newPositions.add(pos);
                }

                // Detect closed positions from previous sync
                for (Map<String, Object> prev : liveVantagePositions) {
                    String prevId = (String) prev.get("id");
                    if (!currentTickets.contains(prevId)) {
                        // Position was closed in MT5! Save to persistent journal
                        TradeOrder closedRecord = new TradeOrder(
                                prevId,
                                (String) prev.getOrDefault("symbol", "XAUUSD"),
                                (String) prev.getOrDefault("type", "BUY"),
                                (Double) prev.getOrDefault("entryPrice", 0.0),
                                (Double) prev.getOrDefault("stopLoss", 0.0),
                                (Double) prev.getOrDefault("takeProfit", 0.0),
                                (Double) prev.getOrDefault("lotSize", 1.0),
                                0.15,
                                (Long) prev.getOrDefault("openTime", System.currentTimeMillis())
                        );
                        closedRecord.setStatus("CLOSED_MT5");
                        closedRecord.setClosePrice((Double) prev.getOrDefault("closePrice", 0.0));
                        closedRecord.setCloseTime(System.currentTimeMillis());
                        closedRecord.setPnl((Double) prev.getOrDefault("pnl", 0.0));
                        
                        tradeHistoryService.recordClosedTrade(closedRecord);
                    }
                }

                liveVantagePositions.clear();
                liveVantagePositions.addAll(newPositions);
            }
        } catch (Exception ignored) {}
    }

    public void setSavedVantageCredentials(String account, String server) {
        this.isVantageConnected = true;
        this.vantageAccountNumber = account;
        this.vantageServer = server;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public void disconnectVantage() {
        this.isVantageConnected = false;
        this.liveVantagePositions.clear();
    }

    private class SocketHandler implements Runnable {
        private final Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;

        public SocketHandler(Socket socket) {
            this.socket = socket;
            try {
                this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.writer = new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception ignored) {}
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    processIncomingVantageMessage(line);
                }
            } catch (Exception ignored) {
            } finally {
                close();
            }
        }

        public void sendMessage(String msg) {
            if (writer != null) {
                writer.println(msg);
            }
        }

        public void close() {
            try {
                connectedClients.remove(this);
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Exception ignored) {}
        }
    }
}
