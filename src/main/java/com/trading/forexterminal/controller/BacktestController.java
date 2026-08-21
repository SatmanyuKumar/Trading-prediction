package com.trading.forexterminal.controller;

import com.trading.forexterminal.model.BacktestResult;
import com.trading.forexterminal.service.BacktestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest")
@CrossOrigin(origins = "*")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    @GetMapping
    public ResponseEntity<BacktestResult> runBacktest(
            @RequestParam(name = "symbol", defaultValue = "XAUUSD") String symbol,
            @RequestParam(name = "timeframe", defaultValue = "15m") String timeframe,
            @RequestParam(name = "tradeMode", defaultValue = "SCALP") String tradeMode,
            @RequestParam(name = "candles", defaultValue = "300") int candles,
            @RequestParam(name = "initialCapital", defaultValue = "30.0") double initialCapital,
            @RequestParam(name = "lotSize", defaultValue = "0.01") double lotSize) {
        BacktestResult result = backtestService.runBacktest(symbol, timeframe, tradeMode, candles, initialCapital, lotSize);
        return ResponseEntity.ok(result);
    }
}
