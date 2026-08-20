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
            @RequestParam(defaultValue = "XAUUSD") String symbol,
            @RequestParam(defaultValue = "15m") String timeframe,
            @RequestParam(defaultValue = "SCALP") String tradeMode,
            @RequestParam(defaultValue = "300") int candles,
            @RequestParam(defaultValue = "30.0") double initialCapital,
            @RequestParam(defaultValue = "0.01") double lotSize) {
        BacktestResult result = backtestService.runBacktest(symbol, timeframe, tradeMode, candles, initialCapital, lotSize);
        return ResponseEntity.ok(result);
    }
}
