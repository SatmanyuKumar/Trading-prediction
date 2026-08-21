/**
 * Pro SMC Live Terminal - Controller, Spread-Aware Strategy & Vantage MT5 Bridge
 */
document.addEventListener('DOMContentLoaded', () => {
    // Read persisted Vantage state
    const savedVantageConnected = localStorage.getItem('vantageConnected') === 'true';
    const savedVantageAccount = localStorage.getItem('vantageAccount') || '25951798';
    const savedVantageServer = localStorage.getItem('vantageServer') || 'VantageMarkets-Demo';

    // State
    const state = {
        symbol: 'XAUUSD',
        timeframe: '1m',
        tradeMode: 'SCALP', // 'SCALP' or 'SWING'
        tvSymbol: 'OANDA:XAUUSD',
        mode: 'smc', // 'smc' or 'tv'
        journalTab: 'active', // 'active' or 'history'
        initialBalance: 100000.0,
        vantageConnected: savedVantageConnected,
        vantageAccount: savedVantageAccount,
        vantageServer: savedVantageServer,
        analysis: null,
        ws: null,
        tvWidget: null,
        orders: []
    };

    // Initialize SMC Canvas Chart Engine
    const chart = new TradingChartEngine('tradingChart', 'chart-tooltip');

    // DOM Elements
    const statPair = document.getElementById('stat-pair');
    const statPrice = document.getElementById('stat-price');
    const statOpen = document.getElementById('stat-open');
    const statHigh = document.getElementById('stat-high');
    const statLow = document.getElementById('stat-low');
    const statSpread = document.getElementById('stat-spread');
    const statChange = document.getElementById('stat-change');
    const connStatus = document.getElementById('connection-status');

    // Vantage Header & Modal Elements
    const btnOpenVantageModal = document.getElementById('btn-open-vantage-modal');
    const vantageStatusText = document.getElementById('vantage-status-text');
    const vantageModal = document.getElementById('vantage-modal');
    const btnCloseVantageModal = document.getElementById('btn-close-vantage-modal');
    const btnCancelVantage = document.getElementById('btn-cancel-vantage');
    const btnSaveVantage = document.getElementById('btn-save-vantage');
    const btnDisconnectVantage = document.getElementById('btn-disconnect-vantage');
    const inputVantageAccount = document.getElementById('input-vantage-account');
    const vantageServerSelect = document.getElementById('vantage-server-select');
    const accountLabel = document.getElementById('account-label');
    const balanceEditIcon = document.getElementById('balance-edit-icon');

    const signalBadge = document.getElementById('signal-badge');
    const spreadCostBadge = document.getElementById('spread-cost-badge');
    const signalConfidence = document.getElementById('signal-confidence');
    const setupTitle = document.getElementById('setup-title');
    const valEntry = document.getElementById('val-entry');
    const valSl = document.getElementById('val-sl');
    const valSlSub = document.getElementById('val-sl-sub');
    const valTp1 = document.getElementById('val-tp1');
    const valRr = document.getElementById('val-rr');
    const zoneBullFvg = document.getElementById('zone-bull-fvg');
    const zoneBearFvg = document.getElementById('zone-bear-fvg');
    const confluenceList = document.getElementById('confluence-list');
    const bookExplanationBody = document.getElementById('book-explanation-body');

    const buyAskPrice = document.getElementById('buy-ask-price');
    const sellBidPrice = document.getElementById('sell-bid-price');
    const totalFloatingPnl = document.getElementById('total-floating-pnl');
    const totalRealizedPnl = document.getElementById('total-realized-pnl');
    const journalBalance = document.getElementById('journal-balance');
    const journalWinrate = document.getElementById('journal-winrate');
    const countActive = document.getElementById('count-active');
    const countHistory = document.getElementById('count-history');
    const positionsTbody = document.getElementById('positions-tbody');
    
    // Custom Lot Elements
    const orderLotsInput = document.getElementById('order-lots');
    const btnLotMinus = document.getElementById('btn-lot-minus');
    const btnLotPlus = document.getElementById('btn-lot-plus');
    const lotChips = document.querySelectorAll('.lot-chip');
    const pipValueTag = document.getElementById('pip-value-tag');

    // Balance Customization Elements
    const btnOpenBalanceModal = document.getElementById('btn-open-balance-modal');
    const balanceModal = document.getElementById('balance-modal');
    const btnCloseBalanceModal = document.getElementById('btn-close-balance-modal');
    const btnCancelBalance = document.getElementById('btn-cancel-balance');
    const btnSaveBalance = document.getElementById('btn-save-balance');
    const inputCustomBalance = document.getElementById('input-custom-balance');
    const presetBalBtns = document.querySelectorAll('.preset-bal-btn');

    const tabBtnPositions = document.getElementById('tab-btn-positions');
    const tabBtnHistory = document.getElementById('tab-btn-history');
    const btnClearHistory = document.getElementById('btn-clear-history');
    const btnResetAll = document.getElementById('btn-reset-all');

    const tradingCanvas = document.getElementById('tradingChart');
    const tvContainer = document.getElementById('tv_chart_container');
    const btnModeSmc = document.getElementById('btn-mode-smc');
    const btnModeTv = document.getElementById('btn-mode-tv');

    // 1. Fetch Initial Account Info & Vantage Status
    async function loadAccount() {
        try {
            const resp = await fetch('/api/account');
            if (resp.ok) {
                const acc = await resp.json();
                state.initialBalance = acc.balance || 100000.0;
                
                if (acc.vantageConnected || state.vantageConnected) {
                    state.vantageConnected = true;
                    state.vantageAccount = acc.vantageAccount || state.vantageAccount || '25951798';
                    state.vantageServer = acc.vantageServer || state.vantageServer || 'VantageMarkets-Demo';
                }

                updateVantageUI();
                updateOrdersUI();
            }
        } catch (e) {}
    }

    if (btnOpenVantageModal) {
        btnOpenVantageModal.addEventListener('click', () => {
            if (vantageModal) vantageModal.classList.remove('hidden');
            if (inputVantageAccount) inputVantageAccount.value = state.vantageAccount || '25951798';
            if (vantageServerSelect) vantageServerSelect.value = state.vantageServer || 'VantageMarkets-Demo';

            if (state.vantageConnected) {
                if (btnDisconnectVantage) btnDisconnectVantage.classList.remove('hidden');
                if (btnSaveVantage) btnSaveVantage.textContent = 'Re-Sync Vantage Balance';
            } else {
                if (btnDisconnectVantage) btnDisconnectVantage.classList.add('hidden');
                if (btnSaveVantage) btnSaveVantage.textContent = 'Save & Connect Vantage';
            }
        });
    }

    if (btnCloseVantageModal) btnCloseVantageModal.addEventListener('click', () => vantageModal && vantageModal.classList.add('hidden'));
    if (btnCancelVantage) btnCancelVantage.addEventListener('click', () => vantageModal && vantageModal.classList.add('hidden'));
    if (vantageModal) {
        vantageModal.addEventListener('click', (e) => {
            if (e.target === vantageModal) vantageModal.classList.add('hidden');
        });
    }

    if (btnSaveVantage) {
        btnSaveVantage.addEventListener('click', async () => {
            const account = (inputVantageAccount ? inputVantageAccount.value.trim() : '') || '25951798';
            const server = (vantageServerSelect ? vantageServerSelect.value : '') || 'VantageMarkets-Demo';

            try {
                const resp = await fetch('/api/vantage/connect', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        account: account,
                        server: server
                    })
                });

                if (resp.ok) {
                    state.vantageConnected = true;
                    state.vantageAccount = account;
                    state.vantageServer = server;
                    state.initialBalance = 100000.0;

                    localStorage.setItem('vantageConnected', 'true');
                    localStorage.setItem('vantageAccount', account);
                    localStorage.setItem('vantageServer', server);

                    await fetch('/api/orders/all', { method: 'DELETE' });
                    await fetchOrders();

                    if (vantageModal) vantageModal.classList.add('hidden');
                    updateVantageUI();
                    updateOrdersUI();
                }
            } catch (e) {
                console.error('Failed to connect Vantage:', e);
            }
        });
    }

    if (btnDisconnectVantage) {
        btnDisconnectVantage.addEventListener('click', async () => {
            try {
                await fetch('/api/vantage/disconnect', { method: 'POST' });
                state.vantageConnected = false;
                state.vantageAccount = null;

                localStorage.removeItem('vantageConnected');
                localStorage.removeItem('vantageAccount');
                localStorage.removeItem('vantageServer');

                if (vantageModal) vantageModal.classList.add('hidden');
                updateVantageUI();
                updateOrdersUI();
            } catch (e) {}
        });
    }

    function updateVantageUI() {
        if (state.vantageConnected) {
            if (btnOpenVantageModal) btnOpenVantageModal.classList.add('connected');
            if (vantageStatusText) vantageStatusText.textContent = 'VANTAGE MT5: #' + (state.vantageAccount || '25951798');
            if (accountLabel) accountLabel.textContent = 'Vantage Balance:';
            if (balanceEditIcon) balanceEditIcon.style.display = 'none';
        } else {
            if (btnOpenVantageModal) btnOpenVantageModal.classList.remove('connected');
            if (vantageStatusText) vantageStatusText.textContent = 'VANTAGE MT5: CONNECT';
            if (accountLabel) accountLabel.textContent = 'Demo Balance:';
            if (balanceEditIcon) balanceEditIcon.style.display = 'inline-block';
        }
    }

    // 3. Balance Customization Modal (Only in Simulator Mode)
    if (btnOpenBalanceModal) {
        btnOpenBalanceModal.addEventListener('click', () => {
            if (state.vantageConnected) {
                if (btnOpenVantageModal) btnOpenVantageModal.click();
                return;
            }

            if (balanceModal) balanceModal.classList.remove('hidden');
            if (inputCustomBalance) inputCustomBalance.value = state.initialBalance;
            presetBalBtns.forEach(btn => {
                btn.classList.toggle('active', parseFloat(btn.getAttribute('data-amount')) === state.initialBalance);
            });
        });
    }

    if (btnCloseBalanceModal) btnCloseBalanceModal.addEventListener('click', () => balanceModal && balanceModal.classList.add('hidden'));
    if (btnCancelBalance) btnCancelBalance.addEventListener('click', () => balanceModal && balanceModal.classList.add('hidden'));
    if (balanceModal) {
        balanceModal.addEventListener('click', (e) => {
            if (e.target === balanceModal) balanceModal.classList.add('hidden');
        });
    }

    presetBalBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            presetBalBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            if (inputCustomBalance) inputCustomBalance.value = btn.getAttribute('data-amount');
        });
    });

    if (btnSaveBalance) {
        btnSaveBalance.addEventListener('click', async () => {
            const val = parseFloat(inputCustomBalance ? inputCustomBalance.value : '0');
            if (isNaN(val) || val <= 0) {
                alert('Please enter a valid balance amount.');
                return;
            }

            try {
                const resp = await fetch('/api/account/balance', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ balance: val })
                });
                if (resp.ok) {
                    const res = await resp.json();
                    state.initialBalance = res.balance;
                    if (balanceModal) balanceModal.classList.add('hidden');
                    updateOrdersUI();
                }
            } catch (e) {
                console.error('Failed to update balance:', e);
            }
        });
    }

    // 4. Custom Lot Controls & Steppers
    function updatePipValue() {
        const lots = parseFloat(orderLotsInput ? orderLotsInput.value : '1.0') || 1.0;
        let pipVal = 10.0 * lots;
        if (state.symbol.includes('BTC')) {
            pipVal = 1.0 * lots;
        } else if (state.symbol.includes('JPY')) {
            pipVal = 6.5 * lots;
        }
        if (pipValueTag) pipValueTag.textContent = 'Pip Val: ~$' + pipVal.toFixed(2);
    }

    if (orderLotsInput) {
        orderLotsInput.addEventListener('input', () => {
            let val = parseFloat(orderLotsInput.value);
            if (isNaN(val) || val < 0.01) val = 0.01;
            if (val > 100.0) val = 100.0;
            
            lotChips.forEach(chip => {
                chip.classList.toggle('active', parseFloat(chip.getAttribute('data-lot')) === val);
            });
            updatePipValue();
        });
    }

    if (btnLotMinus) {
        btnLotMinus.addEventListener('click', () => {
            let val = parseFloat(orderLotsInput.value) || 1.0;
            if (val > 1.0) {
                val = Math.max(0.01, val - 0.50);
            } else if (val > 0.10) {
                val = Math.max(0.01, val - 0.10);
            } else {
                val = Math.max(0.01, val - 0.01);
            }
            orderLotsInput.value = val.toFixed(2);
            orderLotsInput.dispatchEvent(new Event('input'));
        });
    }

    if (btnLotPlus) {
        btnLotPlus.addEventListener('click', () => {
            let val = parseFloat(orderLotsInput.value) || 1.0;
            if (val >= 1.0) {
                val = Math.min(100.0, val + 0.50);
            } else if (val >= 0.10) {
                val = Math.min(100.0, val + 0.10);
            } else {
                val = Math.min(100.0, val + 0.01);
            }
            orderLotsInput.value = val.toFixed(2);
            orderLotsInput.dispatchEvent(new Event('input'));
        });
    }

    lotChips.forEach(chip => {
        chip.addEventListener('click', () => {
            const lotVal = parseFloat(chip.getAttribute('data-lot'));
            if (orderLotsInput) {
                orderLotsInput.value = lotVal.toFixed(2);
                orderLotsInput.dispatchEvent(new Event('input'));
            }
        });
    });

    // 5. On-Chart Checkbox Toggles
    const checkToggles = {
        fvg: document.getElementById('chk-fvg'),
        ob: document.getElementById('chk-ob'),
        sr: document.getElementById('chk-sr'),
        bos: document.getElementById('chk-bos'),
        ema: document.getElementById('chk-ema'),
        setup: document.getElementById('chk-setup')
    };

    Object.keys(checkToggles).forEach(key => {
        const el = checkToggles[key];
        if (el) {
            el.addEventListener('change', () => {
                if (state.mode === 'tv') {
                    setChartMode('smc');
                }
                chart.setFlags({ [key]: el.checked });
            });
        }
    });

    // 5.1 On-Chart Floating Controls & View Adjustments
    const btnResetZoom = document.getElementById('btn-reset-zoom');
    const btnFitScreen = document.getElementById('btn-fit-screen');
    const btnFloatZoomIn = document.getElementById('btn-float-zoom-in');
    const btnFloatZoomOut = document.getElementById('btn-float-zoom-out');
    const btnFloatAutoFit = document.getElementById('btn-float-autofit');
    const btnFloatReset = document.getElementById('btn-float-reset');
    const btnFloatExpand = document.getElementById('btn-float-expand');
    const chartViewport = document.getElementById('chart-viewport');

    if (btnResetZoom) btnResetZoom.addEventListener('click', () => chart.resetZoom());
    if (btnFitScreen) btnFitScreen.addEventListener('click', () => chart.autoScale());
    if (btnFloatZoomIn) btnFloatZoomIn.addEventListener('click', () => chart.zoomIn());
    if (btnFloatZoomOut) btnFloatZoomOut.addEventListener('click', () => chart.zoomOut());
    if (btnFloatAutoFit) btnFloatAutoFit.addEventListener('click', () => chart.autoScale());
    if (btnFloatReset) btnFloatReset.addEventListener('click', () => chart.resetZoom());
    if (btnFloatExpand) {
        btnFloatExpand.addEventListener('click', () => {
            if (chartViewport) {
                const isExpanded = chartViewport.classList.toggle('expanded');
                btnFloatExpand.textContent = isExpanded ? '⛶ Normal View' : '⛶ Expand Chart';
                setTimeout(() => {
                    chart.handleResize();
                    chart.autoScale();
                }, 350);
            }
        });
    }

    // 6. View Mode Switcher
    if (btnModeSmc) btnModeSmc.addEventListener('click', () => setChartMode('smc'));
    if (btnModeTv) btnModeTv.addEventListener('click', () => setChartMode('tv'));

    function setChartMode(newMode) {
        state.mode = newMode;
        if (newMode === 'smc') {
            if (btnModeSmc) btnModeSmc.classList.add('active');
            if (btnModeTv) btnModeTv.classList.remove('active');
            if (tradingCanvas) tradingCanvas.classList.remove('hidden');
            if (tvContainer) tvContainer.classList.add('hidden');
            chart.handleResize();
        } else {
            if (btnModeTv) btnModeTv.classList.add('active');
            if (btnModeSmc) btnModeSmc.classList.remove('active');
            if (tradingCanvas) tradingCanvas.classList.add('hidden');
            if (tvContainer) tvContainer.classList.remove('hidden');
            initTradingViewWidget(state.tvSymbol, state.timeframe === '1m' ? '1' : (state.timeframe === '5m' ? '5' : (state.timeframe === '15m' ? '15' : '60')));
        }
    }

    function initTradingViewWidget(tvSymbol, interval) {
        if (!tvContainer) return;
        tvContainer.innerHTML = '';
        if (typeof TradingView !== 'undefined') {
            state.tvWidget = new TradingView.widget({
                "autosize": true,
                "symbol": tvSymbol,
                "interval": interval || "1",
                "timezone": "Etc/UTC",
                "theme": "dark",
                "style": "1",
                "locale": "en",
                "toolbar_bg": "#0e131d",
                "enable_publishing": false,
                "allow_symbol_change": true,
                "container_id": "tv_chart_container",
                "hide_side_toolbar": false,
                "withdateranges": true,
                "details": true,
                "studies": [
                    "MASimple@tv-basicstudies",
                    "RSI@tv-basicstudies"
                ]
            });
        }
    }

    // 7. Pair Selector
    const pairBtns = document.querySelectorAll('#pair-selector .tool-btn');
    pairBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            pairBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            state.symbol = btn.getAttribute('data-pair');
            state.tvSymbol = btn.getAttribute('data-tv');
            
            document.querySelectorAll('.ticker-item').forEach(item => {
                item.classList.toggle('active', item.getAttribute('data-pair') === state.symbol);
            });

            if (state.mode === 'tv') {
                initTradingViewWidget(state.tvSymbol, state.timeframe === '1m' ? '1' : '5');
            }
            updatePipValue();
            loadAnalysis();
        });
    });

    document.querySelectorAll('.ticker-item').forEach(item => {
        item.addEventListener('click', () => {
            const p = item.getAttribute('data-pair');
            const targetBtn = document.querySelector('#pair-selector .tool-btn[data-pair="' + p + '"]');
            if (targetBtn) targetBtn.click();
        });
    });

    // 8. Timeframe Selector
    const tfBtns = document.querySelectorAll('#tf-selector .tf-btn');
    tfBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tfBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            state.timeframe = btn.getAttribute('data-tf');
            if (state.mode === 'tv') {
                const tvIntervalMap = {
                    '1m': '1',
                    '3m': '3',
                    '5m': '5',
                    '15m': '15',
                    '30m': '30',
                    '1h': '60',
                    '4h': '240',
                    '1d': 'D'
                };
                const tvInt = tvIntervalMap[state.timeframe] || '1';
                initTradingViewWidget(state.tvSymbol, tvInt);
            }
            loadAnalysis();
        });
    });

    // 8b. Trade Mode Selector (⚡ Scalp vs 🌊 Swing)
    const tradeModeBtns = document.querySelectorAll('#trade-mode-selector .trade-mode-btn');
    tradeModeBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tradeModeBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            state.tradeMode = btn.getAttribute('data-tmode') || 'SCALP';
            loadAnalysis();
        });
    });

    // 8c. Strategy Playbook Interactive Tabs
    const btnPlaybookScalp = document.getElementById('btn-playbook-scalp');
    const btnPlaybookSwing = document.getElementById('btn-playbook-swing');
    const playbookStatusTrend = document.getElementById('playbook-status-trend');
    const playbookStatusValuation = document.getElementById('playbook-status-valuation');
    const playbookStatusEntry = document.getElementById('playbook-status-entry');
    const playbookStatusRisk = document.getElementById('playbook-status-risk');
    const btnPlaybookTrigger = document.getElementById('btn-playbook-trigger');
    const chkTrailingSl = document.getElementById('chk-trailing-sl');

    const btnPlaybookSniper = document.getElementById('btn-playbook-sniper');

    if (btnPlaybookScalp) {
        btnPlaybookScalp.addEventListener('click', () => {
            const btn = document.querySelector('#trade-mode-selector .trade-mode-btn[data-tmode="SCALP"]');
            if (btn) btn.click();
        });
    }

    if (btnPlaybookSwing) {
        btnPlaybookSwing.addEventListener('click', () => {
            const btn = document.querySelector('#trade-mode-selector .trade-mode-btn[data-tmode="SWING"]');
            if (btn) btn.click();
        });
    }

    if (btnPlaybookSniper) {
        btnPlaybookSniper.addEventListener('click', () => {
            const btn = document.querySelector('#trade-mode-selector .trade-mode-btn[data-tmode="SNIPER"]');
            if (btn) btn.click();
        });
    }

    if (chkTrailingSl) {
        chkTrailingSl.addEventListener('change', () => {
            state.trailingSl = chkTrailingSl.checked;
            if (playbookStatusRisk) {
                playbookStatusRisk.textContent = chkTrailingSl.checked 
                    ? '🛡️ Trailing SL Active (1:1 Break-Even Lock)' 
                    : '🔒 Fixed SL Active (Static Buffer)';
            }
        });
    }

    // 9. Journal Tabs & AI Setup History 3-Mode Segmentation
    const tabBtnSuggestions = document.getElementById('tab-btn-suggestions');
    const countSuggestions = document.getElementById('count-suggestions');
    const tableHeadersRow = document.getElementById('table-headers');

    const btnClearAiHistory = document.getElementById('btn-clear-ai-history');
    const suggFilterBar = document.getElementById('sugg-filter-bar');
    const suggCountAll = document.getElementById('sugg-count-all');
    const suggCountScalp = document.getElementById('sugg-count-scalp');
    const suggCountSwing = document.getElementById('sugg-count-swing');
    const suggCountSniper = document.getElementById('sugg-count-sniper');
    const suggFilterStats = document.getElementById('sugg-filter-stats');
    const suggChips = document.querySelectorAll('.sugg-chip');
    
    state.suggFilter = 'ALL';

    suggChips.forEach(chip => {
        chip.addEventListener('click', () => {
            suggChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            state.suggFilter = chip.getAttribute('data-smode') || 'ALL';
            renderSuggestionsUI();
        });
    });

    function syncJournalActionButtons() {
        if (suggFilterBar) {
            if (state.journalTab === 'suggestions') {
                suggFilterBar.classList.remove('hidden');
            } else {
                suggFilterBar.classList.add('hidden');
            }
        }
        if (btnClearAiHistory && btnClearHistory) {
            if (state.journalTab === 'suggestions') {
                btnClearAiHistory.style.display = 'inline-flex';
                btnClearHistory.style.display = 'none';
            } else {
                btnClearAiHistory.style.display = 'none';
                btnClearHistory.style.display = 'inline-flex';
            }
        }
    }

    if (tabBtnPositions) {
        tabBtnPositions.addEventListener('click', () => {
            state.journalTab = 'active';
            tabBtnPositions.classList.add('active');
            if (tabBtnHistory) tabBtnHistory.classList.remove('active');
            if (tabBtnSuggestions) tabBtnSuggestions.classList.remove('active');
            syncJournalActionButtons();
            updateOrdersUI();
        });
    }

    if (tabBtnHistory) {
        tabBtnHistory.addEventListener('click', () => {
            state.journalTab = 'history';
            tabBtnHistory.classList.add('active');
            if (tabBtnPositions) tabBtnPositions.classList.remove('active');
            if (tabBtnSuggestions) tabBtnSuggestions.classList.remove('active');
            syncJournalActionButtons();
            updateOrdersUI();
        });
    }

    if (tabBtnSuggestions) {
        tabBtnSuggestions.addEventListener('click', () => {
            state.journalTab = 'suggestions';
            tabBtnSuggestions.classList.add('active');
            if (tabBtnPositions) tabBtnPositions.classList.remove('active');
            if (tabBtnHistory) tabBtnHistory.classList.remove('active');
            syncJournalActionButtons();
            fetchSuggestions();
        });
    }

    if (btnClearHistory) {
        btnClearHistory.addEventListener('click', async () => {
            if (confirm('Are you sure you want to delete all closed trade history?')) {
                try {
                    await fetch('/api/orders/history', { method: 'DELETE' });
                    await fetchOrders();
                } catch (e) {
                    console.error(e);
                }
            }
        });
    }

    if (btnClearAiHistory) {
        btnClearAiHistory.addEventListener('click', async () => {
            if (confirm('Are you sure you want to clean and wipe all past AI Setup History records?')) {
                try {
                    await fetch('/api/suggestions/history', { method: 'DELETE' });
                    await fetchSuggestions();
                } catch (e) {
                    console.error(e);
                }
            }
        });
    }

    const btnCleanAllHistory = document.getElementById('btn-clean-all-history');

    if (btnCleanAllHistory) {
        btnCleanAllHistory.addEventListener('click', async () => {
            if (confirm('⚠️ MASTER RESET: Are you sure you want to clean and wipe ALL terminal history (Closed Trades, Active Positions, and AI Setup Suggestions)?')) {
                try {
                    const resp = await fetch('/api/terminal/clean-all', { method: 'DELETE' });
                    if (resp.ok) {
                        await fetchOrders();
                        await fetchSuggestions();
                        alert('🧹 SUCCESS: All Trade History, Active Orders, and AI Setup History have been completely wiped clean!');
                    }
                } catch (e) {
                    console.error('Failed to clean all history:', e);
                }
            }
        });
    }

    const btnExpandJournal = document.getElementById('btn-expand-journal');
    const positionsTableContainer = document.querySelector('.positions-table-container');

    if (btnExpandJournal && positionsTableContainer) {
        btnExpandJournal.addEventListener('click', () => {
            const isExp = positionsTableContainer.classList.toggle('expanded');
            btnExpandJournal.textContent = isExp ? '🗗 Standard View' : '⛶ Maximize View';
            btnExpandJournal.style.color = isExp ? '#fbbf24' : '#38bdf8';
            btnExpandJournal.style.borderColor = isExp ? '#fbbf24' : '#38bdf8';
        });
    }

    syncJournalActionButtons();

    if (btnResetAll) {
        btnResetAll.addEventListener('click', async () => {
            if (confirm('Reset trading journal and restore demo balance to $' + state.initialBalance.toLocaleString() + '?')) {
                try {
                    await fetch('/api/orders/all', { method: 'DELETE' });
                    await fetchOrders();
                } catch (e) {
                    console.error(e);
                }
            }
        });
    }

    // 10. Load Market Analysis (Spread-Aware & Mode-Aware)
    async function loadAnalysis() {
        try {
            const resp = await fetch('/api/analysis?symbol=' + state.symbol + '&timeframe=' + state.timeframe + '&tradeMode=' + (state.tradeMode || 'SCALP'));
            if (!resp.ok) throw new Error('API Error');
            const data = await resp.json();
            state.analysis = data;

            chart.setData(data);
            updateStatsBar(data);
            updateSignalPanel(data);
            fetchOrders();
        } catch (err) {
            console.error('Failed to load analysis:', err);
        }
    }

    function updateStatsBar(data) {
        if (!data) return;
        if (statPair) statPair.textContent = data.symbol;
        const curPrice = data.currentPrice;
        if (statPrice) statPrice.textContent = formatPrice(curPrice, data.symbol);

        if (data.candles && data.candles.length > 0) {
            const last = data.candles[data.candles.length - 1];
            if (statOpen) statOpen.textContent = formatPrice(last.open, data.symbol);
            if (statHigh) statHigh.textContent = formatPrice(last.high, data.symbol);
            if (statLow) statLow.textContent = formatPrice(last.low, data.symbol);
        }

        if (statSpread) statSpread.textContent = data.spread ? data.spread.toString() : '0.1';
        const chg = data.change24h || 0.0;
        if (statChange) {
            statChange.textContent = (chg >= 0 ? '+' : '') + chg + '%';
            statChange.className = 'stat-val mono ' + (chg >= 0 ? 'text-up' : 'text-down');
        }

        const ask = curPrice + (data.spread || 0.1);
        const bid = curPrice;
        if (buyAskPrice) buyAskPrice.textContent = 'Ask: ' + formatPrice(ask, data.symbol);
        if (sellBidPrice) sellBidPrice.textContent = 'Bid: ' + formatPrice(bid, data.symbol);
    }

    function updateSignalPanel(data) {
        if (!data || !data.tradeSetup) return;
        const setup = data.tradeSetup;

        const isHold = setup.signal === 'HOLD' || setup.signal === 'WAIT' || setup.confidence < 70 || !setup.entryPrice || setup.entryPrice <= 0;

        if (signalBadge) {
            if (isHold) {
                signalBadge.textContent = '🛡️ CAPITAL PRESERVED (HOLD)';
                signalBadge.className = 'signal-badge hold';
            } else {
                signalBadge.textContent = setup.signal + ' SIGNAL';
                signalBadge.className = 'signal-badge ' + setup.signal.toLowerCase();
            }
        }
        if (spreadCostBadge) spreadCostBadge.textContent = 'Spread: ' + data.spread + ' (Cost Included)';
        if (signalConfidence) signalConfidence.textContent = setup.confidence + '% CONFIDENCE';

        if (setupTitle) setupTitle.textContent = setup.setupType || 'Nearest Untouched FVG + SMC Setup';
        
        if (isHold) {
            if (valEntry) valEntry.textContent = 'Stand Aside';
            if (valSl) valSl.textContent = 'Protected (No Risk)';
            if (valSlSub) valSlSub.textContent = 'Zero Capital Exposure';
            if (valTp1) valTp1.textContent = 'Waiting A+';
            if (valRr) valRr.textContent = '--';
        } else {
            if (valEntry) valEntry.textContent = formatPrice(setup.entryPrice, setup.symbol);
            const valEntry2Sub = document.getElementById('val-entry2-sub');
            if (valEntry2Sub) {
                if (setup.entryPrice2 && Math.abs(setup.entryPrice2 - setup.entryPrice) > 0.0001) {
                    valEntry2Sub.textContent = `🟢 E2 (Near SL): ${formatPrice(setup.entryPrice2, setup.symbol)}`;
                } else {
                    valEntry2Sub.textContent = 'Primary Limit Order';
                }
            }
            if (valSl) valSl.textContent = formatPrice(setup.stopLoss, setup.symbol);
            if (valSlSub) {
                const anchorPt = (setup.confluencePoints || []).find(p => p && p.includes('Invalidation Anchor'));
                if (anchorPt) {
                    valSlSub.textContent = anchorPt.replace('🛡️ Invalidation Anchor: ', '');
                } else {
                    valSlSub.textContent = 'Wick Floor ± Safe Buffer';
                }
            }
            if (valTp1) valTp1.textContent = formatPrice(setup.takeProfit1, setup.symbol);
            if (valRr) valRr.textContent = '1 : ' + setup.riskRewardRatio;
        }

        // Live Trade Entered Box Time formatting
        const signalTimeBadge = document.getElementById('signal-time-badge');
        const valSetupTime = document.getElementById('val-setup-time');
        const valSetupElapsed = document.getElementById('val-setup-elapsed');

        if (setup && setup.timestamp && !isHold) {
            state.activeSetupTimestamp = setup.timestamp;
            const t = new Date(setup.timestamp);
            const timeStr = t.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true });
            if (valSetupTime) valSetupTime.textContent = timeStr;
            updateSetupElapsedTime();
            if (signalTimeBadge) signalTimeBadge.style.display = 'inline-flex';
        } else {
            state.activeSetupTimestamp = null;
            if (signalTimeBadge) signalTimeBadge.style.display = 'none';
        }

        const bullFvg = (data.fairValueGaps || []).find(f => f.type === 'BULLISH' && !f.mitigated);
        const bearFvg = (data.fairValueGaps || []).find(f => f.type === 'BEARISH' && !f.mitigated);

        if (zoneBullFvg) {
            if (bullFvg) {
                zoneBullFvg.textContent = formatPrice(bullFvg.bottom, setup.symbol) + ' - ' + formatPrice(bullFvg.top, setup.symbol) + ' (50% C.E. ' + formatPrice(bullFvg.consequentEncroachment, setup.symbol) + ')';
            } else {
                zoneBullFvg.textContent = 'None nearby / Testing higher support';
            }
        }

        if (zoneBearFvg) {
            if (bearFvg) {
                zoneBearFvg.textContent = formatPrice(bearFvg.bottom, setup.symbol) + ' - ' + formatPrice(bearFvg.top, setup.symbol) + ' (50% C.E. ' + formatPrice(bearFvg.consequentEncroachment, setup.symbol) + ')';
            } else {
                zoneBearFvg.textContent = 'None nearby / Testing overhead resistance';
            }
        }

        if (confluenceList) {
            confluenceList.innerHTML = '';
            (setup.confluencePoints || []).forEach(pt => {
                const li = document.createElement('li');
                li.textContent = pt;
                confluenceList.appendChild(li);
            });
        }

        if (bookExplanationBody) {
            bookExplanationBody.innerHTML = formatMarkdown(setup.bookRulesExplanation);
        }

        // Update Interactive Strategy Playbook Card
        const isBull = setup.signal === 'BUY';
        const isSwing = state.tradeMode === 'SWING';

        if (btnPlaybookScalp) btnPlaybookScalp.classList.toggle('active', !isSwing);
        if (btnPlaybookSwing) btnPlaybookSwing.classList.toggle('active', isSwing);

        if (playbookStatusTrend) {
            playbookStatusTrend.textContent = isHold 
                ? '⚖️ Consolidation / Range (No Clean Bias)' 
                : (isBull ? '🟢 Bullish EMA 20/50/200 Slope' : '🔴 Bearish EMA 20/50/200 Slope');
            playbookStatusTrend.style.color = isHold ? 'var(--text-muted)' : (isBull ? 'var(--bull-green)' : 'var(--bear-red)');
        }

        if (playbookStatusValuation) {
            playbookStatusValuation.textContent = isHold 
                ? '⚖️ Equilibrium (Waiting for Sweep)' 
                : (isBull ? '🟢 Deep Discount (<50% Equilibrium)' : '🔴 Premium Distribution (>50% Equilibrium)');
            playbookStatusValuation.style.color = isHold ? 'var(--text-muted)' : (isBull ? 'var(--bull-green)' : 'var(--bear-red)');
        }

        if (playbookStatusEntry) {
            playbookStatusEntry.textContent = isHold 
                ? '⏳ Stand Aside (Mark Douglas Rule #1)' 
                : '🎯 ' + setup.signal + ' @ ' + formatPrice(setup.entryPrice, setup.symbol) + ' (50% FVG C.E.)';
            playbookStatusEntry.style.color = isHold ? 'var(--text-muted)' : 'var(--accent-cyan)';
        }

        if (playbookStatusRisk) {
            const isTrailing = chkTrailingSl ? chkTrailingSl.checked : true;
            playbookStatusRisk.textContent = isHold 
                ? '🛡️ Capital Preservation Active (0 Risk)' 
                : (isTrailing ? '🛡️ Trailing SL Active (1:1 Break-Even Lock)' : '🔒 Fixed SL Active (Static Buffer)');
            playbookStatusRisk.style.color = isHold ? 'var(--accent-cyan)' : (isTrailing ? 'var(--accent-gold)' : 'var(--text-muted)');
        }

        if (btnPlaybookTrigger) {
            if (isHold) {
                btnPlaybookTrigger.textContent = '⏳ Stand Aside — Waiting for A+ Setup';
                btnPlaybookTrigger.onclick = () => {
                    alert('🛡️ Capital Preservation Mode Active: Market is currently in low-conviction consolidation. Smart Money rules require waiting for a clean liquidity sweep before risking capital.');
                };
            } else {
                btnPlaybookTrigger.textContent = '🚀 1-Click Execute ' + setup.signal + ' (' + (isSwing ? '🌊 Swing' : '⚡ Scalp') + ')';
                btnPlaybookTrigger.onclick = () => {
                    if (btnApplySetup) btnApplySetup.click();
                };
            }
        }
    }

    function formatMarkdown(md) {
        if (!md) return '';
        return md
            .replace(/^### (.*$)/gim, '<h4 class="book-subheading" style="color:var(--accent-cyan); margin: 8px 0;">$1</h4>')
            .replace(/^\* \*\*(.*?)\*\*(.*$)/gim, '<div style="margin: 6px 0;"><strong>$1</strong>$2</div>')
            .replace(/^\* (.*$)/gim, '<div style="margin: 4px 0 4px 12px;">• $1</div>')
            .replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
            .replace(/\n\n/gim, '<br>');
    }

    function formatPrice(val, symbol) {
        if (val === undefined || val === null) return '--';
        const num = parseFloat(val);
        if (symbol && (symbol.includes('XAU') || symbol.includes('BTC') || symbol.includes('JPY'))) {
            return num.toFixed(2);
        }
        return num.toFixed(5);
    }

    // 11. WebSocket Live Tick Stream
    function connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = protocol + '//' + window.location.host + '/ws/market';

        state.ws = new WebSocket(wsUrl);

        state.ws.onopen = () => {
            if (connStatus) connStatus.textContent = 'LIVE FEED ACTIVE';
        };

        state.ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                if (msg.type === 'TICK' && msg.prices) {
                    Object.keys(msg.prices).forEach(pair => {
                        const priceEl = document.getElementById('tick-' + pair);
                        if (priceEl) {
                            const p = msg.prices[pair];
                            priceEl.textContent = formatPrice(p, pair);
                        }
                    });

                    const curPrice = msg.prices[state.symbol];
                    if (curPrice && state.analysis && state.analysis.candles) {
                        const candles = state.analysis.candles;
                        if (candles.length > 0) {
                            const last = candles[candles.length - 1];
                            const now = msg.timestamp || Date.now();
                            const lastTime = last.timestamp || last.time || 0;
                            const tfMs = getTimeframeMs(state.timeframe);

                            if (lastTime > 0 && (now - lastTime >= tfMs)) {
                                // ⚡ Real-Time Instant Candle Spawn
                                const newCandle = {
                                    timestamp: now,
                                    time: now,
                                    open: last.close,
                                    high: Math.max(last.close, curPrice),
                                    low: Math.min(last.close, curPrice),
                                    close: curPrice,
                                    volume: 100
                                };
                                candles.push(newCandle);
                                if (candles.length > 500) candles.shift();
                                chart.setData(state.analysis);
                            } else {
                                last.close = curPrice;
                                if (curPrice > last.high) last.high = curPrice;
                                if (curPrice < last.low) last.low = curPrice;
                                if (state.mode === 'smc') {
                                    chart.requestRender();
                                }
                            }

                            if (statPrice) statPrice.textContent = formatPrice(curPrice, state.symbol);
                            if (statHigh) statHigh.textContent = formatPrice(last.high, state.symbol);
                            if (statLow) statLow.textContent = formatPrice(last.low, state.symbol);
                        }
                    }
                    scheduleOrdersUIUpdate();
                }
            } catch (e) {}
        };

        state.ws.onclose = () => {
            setTimeout(connectWebSocket, 2000);
        };
    }

    function getTimeframeMs(tf) {
        switch (tf) {
            case '1m': return 60 * 1000;
            case '3m': return 3 * 60 * 1000;
            case '5m': return 5 * 60 * 1000;
            case '15m': return 15 * 60 * 1000;
            case '30m': return 30 * 60 * 1000;
            case '1h': return 60 * 60 * 1000;
            case '4h': return 4 * 60 * 60 * 1000;
            case '1d': return 24 * 60 * 60 * 1000;
            default: return 60 * 1000;
        }
    }

    let ordersUpdateScheduled = false;
    function scheduleOrdersUIUpdate() {
        if (ordersUpdateScheduled) return;
        ordersUpdateScheduled = true;
        setTimeout(() => {
            ordersUpdateScheduled = false;
            updateOrdersUI();
        }, 300);
    }

    // 12. Order Execution
    const btnQuickBuy = document.getElementById('btn-quick-buy');
    const btnQuickSell = document.getElementById('btn-quick-sell');
    const btnApplySetup = document.getElementById('btn-apply-setup');

    if (btnQuickBuy) {
        btnQuickBuy.addEventListener('click', () => {
            executeTrade('BUY');
        });
    }

    if (btnQuickSell) {
        btnQuickSell.addEventListener('click', () => {
            executeTrade('SELL');
        });
    }

    // Safety Modal Elements
    const safetyModal = document.getElementById('safety-modal');
    const btnCloseSafetyModal = document.getElementById('btn-close-safety-modal');
    const btnDismissSafety = document.getElementById('btn-dismiss-safety');
    const btnOverrideSafety = document.getElementById('btn-override-safety');
    const safetyModalTitle = document.getElementById('safety-modal-title');
    const safetyModalReason = document.getElementById('safety-modal-reason');
    const safetyMarketState = document.getElementById('safety-market-state');
    const safetyConfidenceScore = document.getElementById('safety-confidence-score');

    if (btnCloseSafetyModal) btnCloseSafetyModal.addEventListener('click', () => safetyModal && safetyModal.classList.add('hidden'));
    if (btnDismissSafety) btnDismissSafety.addEventListener('click', () => safetyModal && safetyModal.classList.add('hidden'));
    if (safetyModal) {
        safetyModal.addEventListener('click', (e) => {
            if (e.target === safetyModal) safetyModal.classList.add('hidden');
        });
    }

    if (btnApplySetup) {
        btnApplySetup.addEventListener('click', () => {
            if (!state.analysis || !state.analysis.tradeSetup) return;
            const setup = state.analysis.tradeSetup;

            // 🛡️ CAPITAL PRESERVATION CHECK: Is setup High-Confidence (>= 80%)?
            const isConsolidation = setup.confidence < 80 || (setup.setupType && setup.setupType.includes('Consolidation'));
            
            if (isConsolidation) {
                // Show Interactive Safety Alert Modal
                if (safetyModalReason) {
                    safetyModalReason.textContent = 'Market structure for ' + state.symbol + ' is currently in low-conviction consolidation without institutional displacement. Confidence is only ' + setup.confidence + '% (A+ requires >= 80%). Executing now risks capital loss.';
                }
                if (safetyMarketState) {
                    safetyMarketState.textContent = setup.setupType || 'Consolidation / Low-Conviction';
                }
                if (safetyConfidenceScore) {
                    safetyConfidenceScore.textContent = setup.confidence + '% (Below 80% A+ Threshold)';
                }
                if (safetyModal) safetyModal.classList.remove('hidden');
                return;
            }

            // High-Confidence A+ Setup: Execute with Exact SL & TP
            executeTrade(setup.signal, setup.entryPrice, setup.stopLoss, setup.takeProfit1);
        });
    }

    if (btnOverrideSafety) {
        btnOverrideSafety.addEventListener('click', () => {
            if (safetyModal) safetyModal.classList.add('hidden');
            if (state.analysis && state.analysis.tradeSetup) {
                const setup = state.analysis.tradeSetup;
                executeTrade(setup.signal, setup.entryPrice, setup.stopLoss, setup.takeProfit1);
            }
        });
    }

    let lastTradeTime = 0;

    async function executeTrade(type, customEntry, customSl, customTp) {
        const now = Date.now();
        if (now - lastTradeTime < 3000) {
            alert('⚠️ Anti-Spam Protection: Please wait a few seconds before placing another trade.');
            return;
        }
        lastTradeTime = now;

        if (!state.analysis) return;
        const curPrice = state.analysis.currentPrice;
        const spread = state.analysis.spread || 0.1;
        const lots = parseFloat(orderLotsInput ? orderLotsInput.value : '0.10') || 0.10;
        const pipStep = state.symbol.includes('XAU') ? 1.0 : 0.0010;

        const entry = customEntry || (type === 'BUY' ? (curPrice + spread) : curPrice);

        let sl = customSl;
        let tp = customTp;

        if (!sl) {
            sl = type === 'BUY' ? entry - (pipStep * 3.5) : entry + spread + (pipStep * 3.5);
        }
        if (!tp) {
            tp = type === 'BUY' ? entry + (pipStep * 8.0) : entry - (pipStep * 8.0);
        }

        try {
            const resp = await fetch('/api/orders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    symbol: state.symbol,
                    type: type,
                    timeframe: state.timeframe,
                    lotSize: lots,
                    entry: entry,
                    sl: sl,
                    tp: tp
                })
            });
            if (resp.ok) {
                fetchOrders();
            }
        } catch (e) {
            console.error('Order execution failed:', e);
        }
    }

    async function fetchOrders() {
        try {
            const resp = await fetch('/api/orders');
            if (!resp.ok) return;
            state.orders = await resp.json();
            if (state.journalTab !== 'suggestions') {
                updateOrdersUI();
            }
        } catch (e) {}
    }

    async function fetchSuggestions() {
        try {
            const resp = await fetch('/api/suggestions/history');
            if (!resp.ok) return;
            state.suggestions = await resp.json();
            if (countSuggestions) countSuggestions.textContent = state.suggestions.length;
            if (state.journalTab === 'suggestions') {
                renderSuggestionsUI();
            }
        } catch (e) {}
    }

    function getNormalizedSuggMode(sugg) {
        if (!sugg) return 'SCALP';
        const m = (sugg.mode || '').toUpperCase();
        const type = (sugg.setupType || '').toUpperCase();
        if (m === 'SNIPER' || type.includes('SNIPER') || type.includes('OTE')) return 'SNIPER';
        if (m === 'SWING' || type.includes('SWING') || type.includes('MACRO')) return 'SWING';
        return 'SCALP';
    }

    function renderSuggestionsUI() {
        if (!positionsTbody) return;
        if (tableHeadersRow) {
            tableHeadersRow.innerHTML = 
                '<th>Time</th>' +
                '<th>Symbol</th>' +
                '<th>TF</th>' +
                '<th>Strategy Mode</th>' +
                '<th>Signal</th>' +
                '<th>Entry Level</th>' +
                '<th>Stop Loss</th>' +
                '<th>Take Profit</th>' +
                '<th>R:R Ratio</th>' +
                '<th>Confidence</th>' +
                '<th>AI Outcome (Pass / Fail)</th>' +
                '<th>Sim. P&amp;L ($)</th>' +
                '<th style="text-align:center;">Action / Switch</th>';
        }

        const list = state.suggestions || [];
        positionsTbody.innerHTML = '';

        // 1. Calculate counts for each of the 3 separate sections
        const scalpList = list.filter(s => getNormalizedSuggMode(s) === 'SCALP');
        const swingList = list.filter(s => getNormalizedSuggMode(s) === 'SWING');
        const sniperList = list.filter(s => getNormalizedSuggMode(s) === 'SNIPER');

        if (suggCountAll) suggCountAll.textContent = list.length;
        if (suggCountScalp) suggCountScalp.textContent = scalpList.length;
        if (suggCountSwing) suggCountSwing.textContent = swingList.length;
        if (suggCountSniper) suggCountSniper.textContent = sniperList.length;

        // 2. Select filtered dataset based on active filter chip
        let filteredList = list;
        let modeLabel = 'All 3 Modes';
        if (state.suggFilter === 'SCALP') {
            filteredList = scalpList;
            modeLabel = '⚡ Scalp Mode';
        } else if (state.suggFilter === 'SWING') {
            filteredList = swingList;
            modeLabel = '🌊 Swing Mode';
        } else if (state.suggFilter === 'SNIPER') {
            filteredList = sniperList;
            modeLabel = '🎯 Deep Sniper Mode';
        }

        if (suggFilterStats) {
            suggFilterStats.textContent = `Showing ${filteredList.length} ${modeLabel} Records`;
        }

        // 3. Mode-Specific Win Rate & Realized PnL Calculation
        let totalWins = 0;
        let totalLosses = 0;
        let totalSuggPnl = 0.0;

        filteredList.forEach(sugg => {
            if (sugg.triggerState === 'TP_HIT') {
                totalWins++;
                totalSuggPnl += (sugg.pnl || 0.0);
            } else if (sugg.triggerState === 'SL_HIT') {
                totalLosses++;
                totalSuggPnl += (sugg.pnl || 0.0);
            }
        });

        const completedCount = totalWins + totalLosses;
        const suggWinRate = completedCount > 0 ? Math.round((totalWins / completedCount) * 100) : 0;

        if (journalWinrate) {
            journalWinrate.textContent = completedCount > 0 ? (suggWinRate + '% (' + totalWins + 'W / ' + totalLosses + 'L)') : '--%';
            journalWinrate.className = 'm-val ' + (suggWinRate >= 50 ? 'text-up' : '');
        }

        if (totalRealizedPnl) {
            totalRealizedPnl.textContent = '$' + totalSuggPnl.toFixed(2);
            totalRealizedPnl.className = 'm-val ' + (totalSuggPnl >= 0 ? 'text-up' : 'text-down');
        }

        if (filteredList.length === 0) {
            const emptyMsg = state.suggFilter === 'ALL'
                ? 'No AI setup suggestions recorded yet. Radar scans and logs setups automatically.'
                : `No ${modeLabel} setups recorded yet. Switch tabs to see other modes.`;
            positionsTbody.innerHTML = '<tr class="empty-row"><td colspan="13">' + emptyMsg + '</td></tr>';
            return;
        }

        filteredList.forEach(sugg => {
            const tr = document.createElement('tr');
            const isBull = sugg.signal === 'BUY';
            const timeStr = new Date(sugg.suggestedTime).toLocaleTimeString();
            const normMode = getNormalizedSuggMode(sugg);
            
            let modeBadge = '<span class="mode-pill scalp" style="background:rgba(14,165,233,0.15); color:#38bdf8; border:1px solid rgba(56,189,248,0.3); padding:2px 7px; border-radius:4px; font-size:10.5px; font-weight:800;">⚡ SCALP</span>';
            if (normMode === 'SWING') {
                modeBadge = '<span class="mode-pill swing" style="background:rgba(168,85,247,0.15); color:#c084fc; border:1px solid rgba(168,85,247,0.3); padding:2px 7px; border-radius:4px; font-size:10.5px; font-weight:800;">🌊 SWING</span>';
            } else if (normMode === 'SNIPER') {
                modeBadge = '<span class="mode-pill sniper" style="background:rgba(234,179,8,0.15); color:#fbbf24; border:1px solid rgba(251,191,36,0.3); padding:2px 7px; border-radius:4px; font-size:10.5px; font-weight:800;">🎯 SNIPER</span>';
            }

            let stateBadge = '<span class="status-badge breakeven">⏳ PENDING PULLBACK</span>';
            let pnlStr = '<span style="color:var(--text-dim);">$0.00</span>';

            if (sugg.triggerState === 'READY_EXECUTED') {
                stateBadge = '<span class="status-badge profit" style="background:rgba(59, 130, 246, 0.2); border-color:#60a5fa; color:#60a5fa;">🔥 RUNNING / FILLED</span>';
                pnlStr = '<span style="color:#60a5fa;">In Progress</span>';
            } else if (sugg.triggerState === 'TP_HIT') {
                stateBadge = '<span class="status-badge profit" style="background:rgba(52, 211, 153, 0.2); border-color:#34d399; color:#34d399; font-weight:bold;">✅ PASSED (🎯 TP HIT)</span>';
                pnlStr = '<span class="text-up bold">+$' + (sugg.pnl ? Number(sugg.pnl).toFixed(2) : '0.00') + '</span>';
            } else if (sugg.triggerState === 'SL_HIT') {
                stateBadge = '<span class="status-badge loss" style="background:rgba(248, 113, 113, 0.2); border-color:#f87171; color:#f87171; font-weight:bold;">❌ FAILED (🛑 SL HIT)</span>';
                pnlStr = '<span class="text-down bold">-$' + (sugg.pnl ? Math.abs(Number(sugg.pnl)).toFixed(2) : '0.00') + '</span>';
            }

            tr.innerHTML = 
                '<td style="font-size:11px; color:var(--text-dim);">' + timeStr + '</td>' +
                '<td><strong>' + sugg.symbol + '</strong></td>' +
                '<td><span class="tf-badge">' + sugg.timeframe + '</span></td>' +
                '<td>' + modeBadge + '</td>' +
                '<td class="' + (isBull ? 'text-up' : 'text-down') + ' bold">' + sugg.signal + '</td>' +
                '<td class="highlight-cyan">' + formatPrice(sugg.entryPrice, sugg.symbol) + '</td>' +
                '<td class="text-down">' + formatPrice(sugg.stopLoss, sugg.symbol) + '</td>' +
                '<td class="text-up">' + formatPrice(sugg.takeProfit, sugg.symbol) + '</td>' +
                '<td class="bold" style="color:#fbbf24;">1 : ' + (sugg.riskRewardRatio ? sugg.riskRewardRatio.toFixed(1) : (normMode === 'SNIPER' ? '8.0' : (normMode === 'SWING' ? '4.5' : '3.0'))) + '</td>' +
                '<td>' + sugg.confidence + '%</td>' +
                '<td>' + stateBadge + '</td>' +
                '<td>' + pnlStr + '</td>' +
                '<td style="text-align:center; white-space:nowrap;">' +
                    '<button class="btn-radar-switch" style="padding:4px 8px; font-size:10px; margin-right:4px; cursor:pointer;" onclick="window.viewSuggestionSetup(\'' + sugg.symbol + '\', \'' + sugg.timeframe + '\', \'' + normMode + '\')">📊 Chart</button>' +
                    '<button class="btn-arm-limit" style="padding:4px 8px; font-size:10px; background:linear-gradient(135deg, #0284c7, #0369a1); color:#fff; border:1px solid #38bdf8; border-radius:4px; font-weight:700; cursor:pointer;" onclick="window.armSuggestionAsLimitOrder(\'' + sugg.symbol + '\', \'' + sugg.signal + '\', \'' + sugg.timeframe + '\', ' + sugg.entryPrice + ', ' + sugg.stopLoss + ', ' + sugg.takeProfit + ')">⚡ Arm Limit</button>' +
                '</td>';

            positionsTbody.appendChild(tr);
        });
    }

    window.armSuggestionAsLimitOrder = async (sym, type, tf, entry, sl, tp) => {
        const lots = parseFloat(orderLotsInput ? orderLotsInput.value : '0.10') || 0.10;
        try {
            const resp = await fetch('/api/orders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    symbol: sym,
                    type: type,
                    timeframe: tf,
                    lotSize: lots,
                    entry: parseFloat(entry),
                    sl: parseFloat(sl),
                    tp: parseFloat(tp)
                })
            });
            if (resp.ok) {
                const res = await resp.json();
                alert('🚀 Pending Limit Order Armed for ' + sym + ' (' + tf + ' ' + type + ') @ ' + entry + '!\nStatus: ' + res.status + '\nCheck the Active Positions tab.');
                fetchOrders();
            }
        } catch (e) {
            console.error('Failed to arm limit order:', e);
        }
    };

    window.viewSuggestionSetup = (sym, tf, mode) => {
        const pairBtn = document.querySelector('#pair-selector .tool-btn[data-pair="' + sym + '"]');
        if (pairBtn) pairBtn.click();

        const tfBtn = document.querySelector('#tf-selector .tf-btn[data-tf="' + tf + '"]');
        if (tfBtn) tfBtn.click();

        const modeBtn = document.querySelector('#trade-mode-selector .trade-mode-btn[data-tmode="' + mode + '"]');
        if (modeBtn) modeBtn.click();

        const chartViewport = document.getElementById('chart-viewport');
        if (chartViewport) {
            chartViewport.classList.add('expanded');
            const btnFloatExpand = document.getElementById('btn-float-expand');
            if (btnFloatExpand) btnFloatExpand.textContent = '🗗 Standard View';
            
            chartViewport.scrollIntoView({ behavior: 'smooth', block: 'start' });
            setTimeout(() => {
                if (chart && chart.resize) chart.resize();
                if (chart && chart.fitScreen) chart.fitScreen();
            }, 200);
        }
    };

    function updateOrdersUI() {
        if (state.journalTab === 'suggestions') {
            renderSuggestionsUI();
            return;
        }

        if (tableHeadersRow) {
            tableHeadersRow.innerHTML = 
                '<th>Ticket #</th>' +
                '<th>Symbol</th>' +
                '<th>Type</th>' +
                '<th>Lots</th>' +
                '<th>Entry Price</th>' +
                '<th>Stop Loss</th>' +
                '<th>Take Profit</th>' +
                '<th>Current Price</th>' +
                '<th>Live P&amp;L ($)</th>' +
                '<th>Status</th>' +
                '<th style="text-align:center;">Action / Manage</th>';
        }

        const orders = state.orders || [];
        const activeList = orders.filter(o => o.status === 'OPEN' || o.status === 'PENDING_LIMIT' || o.status === 'PENDING_HTF_QUEUE');
        const historyList = orders.filter(o => o.status !== 'OPEN' && o.status !== 'PENDING_LIMIT' && o.status !== 'PENDING_HTF_QUEUE');

        if (countActive) countActive.textContent = activeList.length;
        if (countHistory) countHistory.textContent = historyList.length;

        let floatingPnl = 0.0;
        activeList.forEach(o => { floatingPnl += (o.pnl || 0.0); });

        let realizedPnl = 0.0;
        let wins = 0;
        historyList.forEach(o => {
            realizedPnl += (o.pnl || 0.0);
            if (o.pnl > 0) wins++;
        });

        const winRate = historyList.length > 0 ? Math.round((wins / historyList.length) * 100) : 0;
        const netBalance = state.initialBalance;

        if (journalBalance) journalBalance.textContent = '$' + netBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        if (totalFloatingPnl) {
            totalFloatingPnl.textContent = '$' + floatingPnl.toFixed(2);
            totalFloatingPnl.className = 'm-val ' + (floatingPnl >= 0 ? 'text-up' : 'text-down');
        }

        if (totalRealizedPnl) {
            totalRealizedPnl.textContent = '$' + realizedPnl.toFixed(2);
            totalRealizedPnl.className = 'm-val ' + (realizedPnl >= 0 ? 'text-up' : 'text-down');
        }

        if (journalWinrate) {
            journalWinrate.textContent = historyList.length > 0 ? (winRate + '%') : '--%';
            journalWinrate.className = 'm-val ' + (winRate >= 50 ? 'text-up' : '');
        }

        if (!positionsTbody) return;
        const displayList = state.journalTab === 'active' ? activeList : historyList;
        positionsTbody.innerHTML = '';

        if (displayList.length === 0) {
            const emptyMsg = state.journalTab === 'active'
                ? (state.vantageConnected ? 'No open positions in your Vantage MT5 account.' : 'No active open positions. Click BUY, SELL, or Auto-Trade above.')
                : 'No closed trade history yet.';
            positionsTbody.innerHTML = '<tr class="empty-row"><td colspan="11">' + emptyMsg + '</td></tr>';
            return;
        }

        displayList.forEach(ord => {
            const tr = document.createElement('tr');
            const isOpen = ord.status === 'OPEN';
            const isPending = ord.status === 'PENDING_LIMIT';
            const isHtfQueue = ord.status === 'PENDING_HTF_QUEUE';
            const pnlClass = ord.pnl >= 0 ? 'text-up' : 'text-down';
            const curP = ord.closePrice || (state.analysis ? state.analysis.currentPrice : ord.entryPrice);

            let statusBadge = '<span class="status-badge ' + ord.status.toLowerCase() + '">' + ord.status + '</span>';
            if (isHtfQueue) {
                statusBadge = '<span class="status-badge breakeven" style="background:rgba(251, 191, 36, 0.15); color:#fbbf24; border-color:#fbbf24;" title="' + (ord.trailingStatus || 'Waiting for LTF trade to finish') + '">⏳ HTF QUEUED</span>';
            } else if (isPending) {
                statusBadge = '<span class="status-badge breakeven" title="Order armed: waiting for market price to tap 50% FVG C.E. entry level">⏳ PENDING LIMIT</span>';
            } else if (isOpen && ord.trailingStatus && ord.trailingStatus !== 'STANDARD') {
                const isPlus = ord.trailingStatus.includes('PLUS');
                statusBadge = '<span class="status-badge ' + (isPlus ? 'profit' : 'breakeven') + '" title="' + ord.trailingStatus + '">' + (isPlus ? '🔥 PLUS SL' : '🛡️ BE (0 RISK)') + '</span>';
            }

            tr.innerHTML = '<td>#' + ord.id + '</td>' +
                '<td><strong>' + ord.symbol + '</strong> <span style="font-size:10px; color:var(--text-dim);">' + (ord.timeframe || '') + '</span></td>' +
                '<td class="' + (ord.type === 'BUY' ? 'text-up' : 'text-down') + ' bold">' + ord.type + '</td>' +
                '<td>' + Number(ord.lotSize).toFixed(2) + '</td>' +
                '<td>' + formatPrice(ord.entryPrice, ord.symbol) + '</td>' +
                '<td class="text-down">' + formatPrice(ord.stopLoss, ord.symbol) + '</td>' +
                '<td class="text-up">' + formatPrice(ord.takeProfit, ord.symbol) + '</td>' +
                '<td>' + formatPrice(curP, ord.symbol) + '</td>' +
                '<td class="' + pnlClass + ' bold">' + (isPending || isHtfQueue ? '$0.00' : ('$' + Number(ord.pnl).toFixed(2))) + '</td>' +
                '<td>' + statusBadge + '</td>' +
                '<td style="text-align:center;">' +
                    (isPending || isHtfQueue ? '<button class="btn-delete-single" title="Cancel Order" onclick="window.closeTradeOrder(\'' + ord.id + '\')">Cancel</button>' : (isOpen ? '<button class="btn-close-pos" onclick="window.closeTradeOrder(\'' + ord.id + '\')">Close MT5</button>' : '<button class="btn-delete-single" title="Delete record" onclick="window.deleteTradeOrder(\'' + ord.id + '\')">🗑️</button>')) +
                '</td>';
            positionsTbody.appendChild(tr);
        });
    }

    window.closeTradeOrder = async (orderId) => {
        try {
            await fetch('/api/orders/' + orderId + '/close', { method: 'POST' });
            fetchOrders();
        } catch (e) {}
    };

    window.deleteTradeOrder = async (orderId) => {
        try {
            await fetch('/api/orders/' + orderId, { method: 'DELETE' });
            fetchOrders();
        } catch (e) {}
    };

    // 13. Backtest Modal & Execution Engine (Dual Mode: Scalp vs Swing)
    const btnOpenBacktestModal = document.getElementById('btn-open-backtest-modal');
    const backtestModal = document.getElementById('backtest-modal');
    const btnCloseBacktestModal = document.getElementById('btn-close-backtest-modal');
    const btnCloseBacktest = document.getElementById('btn-close-backtest');
    const btnRunBacktest = document.getElementById('btn-run-backtest');

    const backtestSymbol = document.getElementById('backtest-symbol');
    const backtestTimeframe = document.getElementById('backtest-timeframe');
    const backtestMode = document.getElementById('backtest-mode');
    const backtestCandles = document.getElementById('backtest-candles');
    const backtestCapital = document.getElementById('backtest-capital');
    const backtestLotSize = document.getElementById('backtest-lotsize');

    const kpiWinrate = document.getElementById('kpi-winrate');
    const kpiWinsLosses = document.getElementById('kpi-wins-losses');
    const kpiProfitFactor = document.getElementById('kpi-profit-factor');
    const kpiCapitalGrowth = document.getElementById('kpi-capital-growth');
    const kpiNetProfit = document.getElementById('kpi-net-profit');
    const kpiMaxDd = document.getElementById('kpi-max-dd');
    const kpiLotUsed = document.getElementById('kpi-lot-used');
    const backtestTbody = document.getElementById('backtest-tbody');

    // Preset chips click listeners
    document.querySelectorAll('.btn-capital-preset').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.btn-capital-preset').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            if (backtestCapital) backtestCapital.value = btn.dataset.capital;
            if (backtestLotSize) backtestLotSize.value = btn.dataset.lot;
        });
    });

    if (btnOpenBacktestModal) {
        btnOpenBacktestModal.addEventListener('click', () => {
            if (backtestSymbol) backtestSymbol.value = state.symbol;
            if (backtestTimeframe) backtestTimeframe.value = state.timeframe;
            if (backtestMode) backtestMode.value = state.tradeMode || 'SCALP';
            if (backtestModal) backtestModal.classList.remove('hidden');
        });
    }

    if (btnCloseBacktestModal) btnCloseBacktestModal.addEventListener('click', () => backtestModal && backtestModal.classList.add('hidden'));
    if (btnCloseBacktest) btnCloseBacktest.addEventListener('click', () => backtestModal && backtestModal.classList.add('hidden'));
    if (backtestModal) {
        backtestModal.addEventListener('click', (e) => {
            if (e.target === backtestModal) backtestModal.classList.add('hidden');
        });
    }

    if (btnRunBacktest) {
        btnRunBacktest.addEventListener('click', async () => {
            const sym = backtestSymbol ? backtestSymbol.value : state.symbol;
            const tf = backtestTimeframe ? backtestTimeframe.value : '15m';
            const mode = backtestMode ? backtestMode.value : 'SCALP';
            const candles = backtestCandles ? backtestCandles.value : '300';
            const capital = backtestCapital ? parseFloat(backtestCapital.value) || 30.0 : 30.0;
            const lotSize = backtestLotSize ? parseFloat(backtestLotSize.value) || 0.01 : 0.01;

            btnRunBacktest.disabled = true;
            btnRunBacktest.textContent = '⏳ Simulating ' + mode + ' Strategy...';

            try {
                const resp = await fetch('/api/backtest?symbol=' + sym + '&timeframe=' + tf + '&tradeMode=' + mode + '&candles=' + candles + '&initialCapital=' + capital + '&lotSize=' + lotSize);
                if (!resp.ok) throw new Error('Backtest API error');
                const result = await resp.json();
                renderBacktestResults(result);
            } catch (err) {
                console.error('Backtest error:', err);
                alert('Backtest simulation failed. Please try again.');
            } finally {
                btnRunBacktest.disabled = false;
                btnRunBacktest.textContent = '🚀 Run Empirical Backtest';
            }
        });
    }

    function renderBacktestResults(res) {
        if (!res) return;

        if (kpiWinrate) {
            kpiWinrate.textContent = res.winRate + '%';
            kpiWinrate.className = 'kpi-val ' + (res.winRate >= 50 ? 'text-up' : 'text-down');
        }
        if (kpiWinsLosses) {
            const wins = res.winningTrades !== undefined ? res.winningTrades : (res.winCount || 0);
            const losses = res.losingTrades !== undefined ? res.losingTrades : (res.lossCount || 0);
            kpiWinsLosses.textContent = wins + ' Wins / ' + losses + ' Losses (' + res.totalTrades + ' Total)';
        }
        if (kpiProfitFactor) {
            kpiProfitFactor.textContent = res.profitFactor ? res.profitFactor.toFixed(2) : '--';
            kpiProfitFactor.className = 'kpi-val ' + (res.profitFactor >= 1.5 ? 'text-up' : 'highlight-cyan');
        }
        if (kpiCapitalGrowth) {
            const isProfit = res.netProfit >= 0;
            kpiCapitalGrowth.textContent = '$' + res.initialCapital.toFixed(2) + ' ➔ $' + res.finalCapital.toFixed(2);
            kpiCapitalGrowth.className = 'kpi-val ' + (isProfit ? 'text-up' : 'text-down');
        }
        if (kpiNetProfit) {
            const isProfit = res.netProfit >= 0;
            kpiNetProfit.textContent = 'Net P&L: ' + (isProfit ? '+$' : '-$') + Math.abs(res.netProfit).toFixed(2) + ' (' + (res.returnPercentage >= 0 ? '+' : '') + res.returnPercentage.toFixed(1) + '%)';
            kpiNetProfit.className = 'kpi-sub ' + (isProfit ? 'text-up' : 'text-down');
        }
        if (kpiMaxDd) {
            kpiMaxDd.textContent = res.maxDrawdown.toFixed(2) + '%';
        }
        if (kpiLotUsed) {
            kpiLotUsed.textContent = 'Lot: ' + (res.lotSize || 0.01);
        }

        if (backtestTbody) {
            backtestTbody.innerHTML = '';
            const history = res.tradeHistory || [];

            if (history.length === 0) {
                backtestTbody.innerHTML = '<tr><td colspan="10" style="text-align:center; padding: 18px; color: var(--text-dim);">No A+ ' + (res.timeframe || '') + ' trade setups were formed in this historical window.</td></tr>';
                return;
            }

            history.forEach(t => {
                const tr = document.createElement('tr');
                const isWin = t.pnl >= 0;

                let badgeClass = isWin ? 'profit' : 'loss';
                let badgeText = '🛑 SL HIT';
                if (t.exitReason === 'TAKE_PROFIT') {
                    badgeClass = 'profit';
                    badgeText = '🎯 TP HIT';
                } else if (t.exitReason === 'BREAK_EVEN') {
                    badgeClass = 'breakeven';
                    badgeText = '🛡️ BREAK-EVEN';
                } else if (t.exitReason === 'TIME_EXPIRY') {
                    badgeClass = isWin ? 'profit' : 'loss';
                    badgeText = isWin ? '⏱️ TIME WIN' : '⏱️ TIME EXIT';
                }

                tr.innerHTML = '<td>#' + t.tradeNum + '</td>' +
                    '<td class="' + (t.side === 'BUY' ? 'text-up' : 'text-down') + ' bold">' + t.side + ' <span style="font-size:10px; color:var(--text-dim);">(' + (t.mode || 'SCALP') + ')</span></td>' +
                    '<td class="mono">' + (t.lotSize !== undefined ? t.lotSize : (res.lotSize || 0.01)) + '</td>' +
                    '<td>' + Number(t.entryPrice).toFixed(2) + '</td>' +
                    '<td>' + Number(t.exitPrice).toFixed(2) + '</td>' +
                    '<td class="text-down">' + Number(t.stopLoss).toFixed(2) + '</td>' +
                    '<td class="text-up">' + Number(t.takeProfit).toFixed(2) + '</td>' +
                    '<td><span class="status-badge ' + badgeClass + '">' + badgeText + '</span></td>' +
                    '<td class="' + (isWin ? 'text-up' : 'text-down') + ' bold">' + (isWin ? '+$' : '-$') + Math.abs(t.pnl).toFixed(2) + '</td>' +
                    '<td class="mono bold">$' + Number(t.runningBalance).toLocaleString('en-US', { minimumFractionDigits: 2 }) + '</td>';
                backtestTbody.appendChild(tr);
            });
        }
    }

    // =========================================================================
    // 💡 AI TRADE ADVISOR & BUY vs SELL PROFIT COMPARISON MODAL
    // =========================================================================
    const btnTradeAdvisor = document.getElementById('btn-trade-advisor');
    const advisorModal = document.getElementById('advisor-modal');
    const btnCloseAdvisorModal = document.getElementById('btn-close-advisor-modal');
    const btnCloseAdvisorFooter = document.getElementById('btn-close-advisor-footer');
    const advisorSubInfo = document.getElementById('advisor-sub-info');
    const advisorVerdictBadge = document.getElementById('advisor-verdict-badge');
    const advisorExpectancyPill = document.getElementById('advisor-expectancy-pill');
    const advisorVerdictText = document.getElementById('advisor-verdict-text');

    const cardBuyScenario = document.getElementById('card-buy-scenario');
    const tagBuyRecommended = document.getElementById('tag-buy-recommended');
    const buyWinprobText = document.getElementById('buy-winprob-text');
    const buyMeterBar = document.getElementById('buy-meter-bar');
    const buyProfitVal = document.getElementById('buy-profit-val');
    const buyRiskVal = document.getElementById('buy-risk-val');
    const buyRrVal = document.getElementById('buy-rr-val');
    const buyEntryVal = document.getElementById('buy-entry-val');
    const buyConfluencesList = document.getElementById('buy-confluences-list');
    const btnAdvisorExecuteBuy = document.getElementById('btn-advisor-execute-buy');

    const cardSellScenario = document.getElementById('card-sell-scenario');
    const tagSellRecommended = document.getElementById('tag-sell-recommended');
    const sellWinprobText = document.getElementById('sell-winprob-text');
    const sellMeterBar = document.getElementById('sell-meter-bar');
    const sellProfitVal = document.getElementById('sell-profit-val');
    const sellRiskVal = document.getElementById('sell-risk-val');
    const sellRrVal = document.getElementById('sell-rr-val');
    const sellEntryVal = document.getElementById('sell-entry-val');
    const sellConfluencesList = document.getElementById('sell-confluences-list');
    const btnAdvisorExecuteSell = document.getElementById('btn-advisor-execute-sell');

    if (btnTradeAdvisor) {
        btnTradeAdvisor.addEventListener('click', async () => {
            const lots = parseFloat(orderLotsInput ? orderLotsInput.value : '0.10') || 0.10;
            btnTradeAdvisor.style.opacity = '0.6';
            btnTradeAdvisor.disabled = true;

            try {
                const resp = await fetch(`/api/advisor?symbol=${state.symbol}&timeframe=${state.timeframe}&tradeMode=${state.tradeMode}&lotSize=${lots}`);
                if (!resp.ok) throw new Error('Advisor endpoint failed');
                const adv = await resp.json();

                // 1. Subtitle info
                if (advisorSubInfo) {
                    advisorSubInfo.textContent = `${adv.symbol} • ${adv.timeframe} • ${adv.tradeMode === 'SWING' ? '🌊 Swing' : '⚡ Scalp'} Mode • ${adv.lotSize.toFixed(2)} Lots`;
                }

                // 2. Verdict Banner
                if (advisorVerdictBadge) {
                    advisorVerdictBadge.textContent = adv.verdictHeadline;
                    if (adv.strategyVerdict === 'VALID_A_PLUS') {
                        advisorVerdictBadge.className = 'verdict-badge';
                    } else if (adv.strategyVerdict === 'COUNTER_HTF_RISKY') {
                        advisorVerdictBadge.className = 'verdict-badge danger';
                    } else {
                        advisorVerdictBadge.className = 'verdict-badge warn';
                    }
                }

                if (advisorExpectancyPill) {
                    const isPos = adv.mathematicalExpectancy >= 0;
                    advisorExpectancyPill.textContent = `💰 Mathematical Edge: ${isPos ? '+' : ''}$${adv.mathematicalExpectancy.toFixed(2)} / Trade`;
                }

                if (advisorVerdictText) {
                    advisorVerdictText.textContent = adv.verdictExplanation;
                }

                // 3. BUY Scenario
                const buy = adv.buyScenario;
                if (buy) {
                    if (cardBuyScenario) cardBuyScenario.classList.toggle('recommended', buy.recommended);
                    if (tagBuyRecommended) tagBuyRecommended.classList.toggle('hidden', !buy.recommended);
                    if (buyWinprobText) buyWinprobText.textContent = `${buy.winProbability}% Win Prob`;
                    if (buyMeterBar) buyMeterBar.style.width = `${buy.winProbability}%`;
                    if (buyProfitVal) buyProfitVal.textContent = `+$${buy.profitDollars.toFixed(2)} (+${buy.profitPips.toFixed(1)} pips)`;
                    if (buyRiskVal) buyRiskVal.textContent = `-$${buy.riskDollars.toFixed(2)} (-${buy.riskPips.toFixed(1)} pips)`;
                    if (buyRrVal) buyRrVal.textContent = `1 : ${buy.riskRewardRatio.toFixed(2)}`;
                    if (buyEntryVal) buyEntryVal.textContent = formatPrice(buy.entryPrice, adv.symbol);

                    if (buyConfluencesList) {
                        buyConfluencesList.innerHTML = '';
                        (buy.confluences || []).forEach(c => {
                            const li = document.createElement('li');
                            li.textContent = c;
                            buyConfluencesList.appendChild(li);
                        });
                        (buy.warnings || []).forEach(w => {
                            const li = document.createElement('li');
                            li.style.color = '#f87171';
                            li.textContent = w;
                            buyConfluencesList.appendChild(li);
                        });
                    }

                    if (btnAdvisorExecuteBuy) {
                        btnAdvisorExecuteBuy.textContent = `🚀 1-Click Execute BUY (${adv.lotSize.toFixed(2)} Lot)`;
                        btnAdvisorExecuteBuy.onclick = () => {
                            advisorModal.classList.add('hidden');
                            placeOrder('BUY', buy.entryPrice, buy.stopLoss, buy.takeProfit);
                        };
                    }
                }

                // 4. SELL Scenario
                const sell = adv.sellScenario;
                if (sell) {
                    if (cardSellScenario) cardSellScenario.classList.toggle('recommended', sell.recommended);
                    if (tagSellRecommended) tagSellRecommended.classList.toggle('hidden', !sell.recommended);
                    if (sellWinprobText) sellWinprobText.textContent = `${sell.winProbability}% Win Prob`;
                    if (sellMeterBar) sellMeterBar.style.width = `${sell.winProbability}%`;
                    if (sellProfitVal) sellProfitVal.textContent = `+$${sell.profitDollars.toFixed(2)} (+${sell.profitPips.toFixed(1)} pips)`;
                    if (sellRiskVal) sellRiskVal.textContent = `-$${sell.riskDollars.toFixed(2)} (-${sell.riskPips.toFixed(1)} pips)`;
                    if (sellRrVal) sellRrVal.textContent = `1 : ${sell.riskRewardRatio.toFixed(2)}`;
                    if (sellEntryVal) sellEntryVal.textContent = formatPrice(sell.entryPrice, adv.symbol);

                    if (sellConfluencesList) {
                        sellConfluencesList.innerHTML = '';
                        (sell.confluences || []).forEach(c => {
                            const li = document.createElement('li');
                            li.textContent = c;
                            sellConfluencesList.appendChild(li);
                        });
                        (sell.warnings || []).forEach(w => {
                            const li = document.createElement('li');
                            li.style.color = '#f87171';
                            li.textContent = w;
                            sellConfluencesList.appendChild(li);
                        });
                    }

                    if (btnAdvisorExecuteSell) {
                        btnAdvisorExecuteSell.textContent = `🚀 1-Click Execute SELL (${adv.lotSize.toFixed(2)} Lot)`;
                        btnAdvisorExecuteSell.onclick = () => {
                            advisorModal.classList.add('hidden');
                            placeOrder('SELL', sell.entryPrice, sell.stopLoss, sell.takeProfit);
                        };
                    }
                }

                // Open modal
                if (advisorModal) advisorModal.classList.remove('hidden');

            } catch (err) {
                console.error('Advisor error:', err);
                alert('Could not load Trade Advisor analysis. Please try again.');
            } finally {
                btnTradeAdvisor.style.opacity = '1';
                btnTradeAdvisor.disabled = false;
            }
        });
    }

    if (btnCloseAdvisorModal) btnCloseAdvisorModal.addEventListener('click', () => advisorModal && advisorModal.classList.add('hidden'));
    if (btnCloseAdvisorFooter) btnCloseAdvisorFooter.addEventListener('click', () => advisorModal && advisorModal.classList.add('hidden'));
    if (advisorModal) {
        advisorModal.addEventListener('click', (e) => {
            if (e.target === advisorModal) advisorModal.classList.add('hidden');
        });
    }

    // =========================================================================
    // 🎯 1:3+ FUTURE SETUP RADAR & PREDICTIONS SCANNER
    // =========================================================================
    const btnOpenRadarModal = document.getElementById('btn-open-radar-modal');
    const btnViewAllRadar = document.getElementById('btn-view-all-radar');
    const radarModal = document.getElementById('radar-modal');
    const btnCloseRadarModal = document.getElementById('btn-close-radar-modal');
    const radarModalGrid = document.getElementById('radar-modal-grid');
    const radarSidebarList = document.getElementById('radar-sidebar-list');
    const radarLiveCount = document.getElementById('radar-live-count');
    const countRadarAll = document.getElementById('count-radar-all');
    const countRadarActive = document.getElementById('count-radar-active');
    const radarLastUpdated = document.getElementById('radar-last-updated');
    const radarTabBtns = document.querySelectorAll('.radar-tab-btn');

    let currentRadarFilter = 'ALL';
    state.radarList = [];

    async function fetchRadarData() {
        try {
            const resp = await fetch('/api/radar');
            if (!resp.ok) return;
            state.radarList = await resp.json();
            renderRadarUI();
        } catch (e) {
            console.error('Radar fetch error:', e);
        }
    }

    function renderRadarUI() {
        const list = state.radarList || [];
        const activeInZone = list.filter(item => item.status === 'ACTIVE_IN_ZONE');

        if (radarLiveCount) radarLiveCount.textContent = list.length;
        if (countRadarAll) countRadarAll.textContent = list.length;
        if (countRadarActive) countRadarActive.textContent = activeInZone.length;

        if (radarLastUpdated) {
            const now = new Date();
            radarLastUpdated.textContent = 'Updated ' + now.toLocaleTimeString();
        }

        // Render Sidebar Mini Cards
        if (radarSidebarList) {
            if (list.length === 0) {
                radarSidebarList.innerHTML = '<div style="text-align:center; color:var(--text-dim); padding:12px; font-size:11px;">Scanning across assets for >= 1:3.0 setups...</div>';
            } else {
                radarSidebarList.innerHTML = '';
                list.slice(0, 4).forEach(item => {
                    const isBull = item.signal === 'BUY';
                    const isReady = item.status === 'ACTIVE_IN_ZONE';
                    const div = document.createElement('div');
                    div.className = 'radar-mini-card';
                    div.innerHTML = 
                        '<div class="radar-mini-left">' +
                            '<div class="radar-mini-pair">' +
                                '<span class="' + (isBull ? 'text-up' : 'text-down') + '">' + (isBull ? '🟢' : '🔴') + ' ' + item.symbol + '</span>' +
                                '<span style="font-size:10px; color:var(--text-dim);">' + item.timeframe + ' (' + item.mode + ')</span>' +
                            '</div>' +
                            '<div class="radar-mini-desc">Entry: ' + formatPrice(item.entryPrice, item.symbol) + ' (' + item.distanceDescription + ')</div>' +
                        '</div>' +
                        '<div class="radar-mini-right">' +
                            '<div class="radar-mini-rr">1:' + item.riskRewardRatio.toFixed(1) + ' R:R</div>' +
                            '<span class="radar-mini-badge ' + (isReady ? 'profit' : 'breakeven') + '">' + (isReady ? '🔥 READY' : '⏳ PENDING') + '</span>' +
                        '</div>';
                    
                    div.onclick = () => {
                        switchToRadarSetup(item);
                    };
                    radarSidebarList.appendChild(div);
                });
            }
        }

        // Render Modal Full Grid
        if (radarModalGrid) {
            let filtered = list;
            if (currentRadarFilter === 'ACTIVE_IN_ZONE') {
                filtered = list.filter(i => i.status === 'ACTIVE_IN_ZONE');
            } else if (currentRadarFilter === 'SCALP') {
                filtered = list.filter(i => i.mode === 'SCALP');
            } else if (currentRadarFilter === 'SWING') {
                filtered = list.filter(i => i.mode === 'SWING');
            }

            if (filtered.length === 0) {
                radarModalGrid.innerHTML = '<div style="grid-column: 1 / -1; text-align:center; padding: 30px; color:var(--text-dim); font-size:13px;">No setups currently match the selected filter. Algorithmic radar is scanning incoming candles...</div>';
                return;
            }

            radarModalGrid.innerHTML = '';
            filtered.forEach(item => {
                const isBull = item.signal === 'BUY';
                const isReady = item.status === 'ACTIVE_IN_ZONE';
                const card = document.createElement('div');
                card.className = 'radar-card ' + (isReady ? 'active-in-zone' : '');

                card.innerHTML = 
                    '<div class="radar-card-header">' +
                        '<div class="radar-pair-title">' +
                            '<span class="radar-pair-name">' + item.symbolName + '</span>' +
                            '<span class="radar-tf-pill">' + item.timeframe + '</span>' +
                            '<span class="radar-tf-pill" style="color:var(--accent-cyan);">' + item.mode + '</span>' +
                            '<span class="status-badge ' + (isBull ? 'profit' : 'loss') + '">' + (isBull ? '🟢 BUY' : '🔴 SELL') + '</span>' +
                        '</div>' +
                        '<div class="radar-rr-badge">🔥 1 : ' + item.riskRewardRatio.toFixed(2) + ' R:R</div>' +
                    '</div>' +
                    '<div class="radar-coords-grid">' +
                        '<div class="radar-coord-item">' +
                            '<span class="radar-coord-lbl">50% FVG Entry</span>' +
                            '<span class="radar-coord-val highlight-cyan">' + formatPrice(item.entryPrice, item.symbol) + '</span>' +
                        '</div>' +
                        '<div class="radar-coord-item">' +
                            '<span class="radar-coord-lbl">Stop Loss (Risk)</span>' +
                            '<span class="radar-coord-val text-down">' + formatPrice(item.stopLoss, item.symbol) + ' (-$' + item.riskAmount.toFixed(0) + ')</span>' +
                        '</div>' +
                        '<div class="radar-coord-item">' +
                            '<span class="radar-coord-lbl">Take Profit (Target)</span>' +
                            '<span class="radar-coord-val text-up">' + formatPrice(item.takeProfit, item.symbol) + ' (+$' + item.rewardAmount.toFixed(0) + ')</span>' +
                        '</div>' +
                    '</div>' +
                    '<div class="radar-status-row">' +
                        '<span>Trigger Proximity: <strong class="radar-dist-tag">' + item.distanceDescription + '</strong></span>' +
                        '<span class="status-badge ' + (isReady ? 'profit' : 'breakeven') + '">' + (isReady ? '🔥 ACTIVE IN ZONE' : '⏳ PENDING PULLBACK') + '</span>' +
                    '</div>' +
                    '<div class="radar-actions-row">' +
                        '<button class="btn-radar-switch" id="btn-switch-' + item.id + '">📊 Switch Chart &amp; Analyze</button>' +
                        '<button class="btn-radar-apply" id="btn-arm-' + item.id + '">🚀 1-Click Limit Order</button>' +
                    '</div>';

                const btnSwitch = card.querySelector('#btn-switch-' + item.id);
                const btnArm = card.querySelector('#btn-arm-' + item.id);

                if (btnSwitch) {
                    btnSwitch.onclick = () => {
                        if (radarModal) radarModal.classList.add('hidden');
                        switchToRadarSetup(item);
                    };
                }

                if (btnArm) {
                    btnArm.onclick = () => {
                        if (radarModal) radarModal.classList.add('hidden');
                        switchToRadarSetup(item);
                        executeTrade(item.signal, item.entryPrice, item.stopLoss, item.takeProfit);
                    };
                }

                radarModalGrid.appendChild(card);
            });
        }
    }

    function switchToRadarSetup(item) {
        const pairBtn = document.querySelector('#pair-selector .tool-btn[data-pair="' + item.symbol + '"]');
        if (pairBtn) pairBtn.click();

        const tfBtn = document.querySelector('#tf-selector .tf-btn[data-tf="' + item.timeframe + '"]');
        if (tfBtn) tfBtn.click();

        const modeBtn = document.querySelector('#trade-mode-selector .trade-mode-btn[data-tmode="' + item.mode + '"]');
        if (modeBtn) modeBtn.click();
    }

    if (btnOpenRadarModal) {
        btnOpenRadarModal.addEventListener('click', () => {
            fetchRadarData();
            if (radarModal) radarModal.classList.remove('hidden');
        });
    }

    if (btnViewAllRadar) {
        btnViewAllRadar.addEventListener('click', () => {
            fetchRadarData();
            if (radarModal) radarModal.classList.remove('hidden');
        });
    }

    if (btnCloseRadarModal) {
        btnCloseRadarModal.addEventListener('click', () => {
            if (radarModal) radarModal.classList.add('hidden');
        });
    }

    if (radarModal) {
        radarModal.addEventListener('click', (e) => {
            if (e.target === radarModal) radarModal.classList.add('hidden');
        });
    }

    radarTabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            radarTabBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentRadarFilter = btn.getAttribute('data-filter') || 'ALL';
            renderRadarUI();
        });
    });

    function connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/market`;
        
        try {
            const ws = new WebSocket(wsUrl);

            ws.onopen = () => {
                if (connStatus) {
                    connStatus.textContent = 'ONLINE (0 LAG)';
                    connStatus.className = 'status-indicator online';
                }
            };

            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data && data.type === 'TICK' && data.prices) {
                        const curPrice = data.prices[state.symbol];
                        if (curPrice && curPrice > 0) {
                            // Instant Zero-Delay Header Stats Update
                            if (statPrice) statPrice.textContent = formatPrice(curPrice, state.symbol);
                            
                            const sp = (state.analysis && state.analysis.spread) ? state.analysis.spread : (state.symbol.includes('XAU') ? 0.35 : 0.00015);
                            const ask = curPrice + sp;
                            const bid = curPrice;

                            if (buyAskPrice) buyAskPrice.textContent = 'Ask: ' + formatPrice(ask, state.symbol);
                            if (sellBidPrice) sellBidPrice.textContent = 'Bid: ' + formatPrice(bid, state.symbol);

                            // Smooth 60 FPS Sub-pixel Chart Engine Update
                            chart.updateLiveTick(state.symbol, curPrice, curPrice, curPrice, data.timestamp);
                        }
                    }
                } catch (e) {}
            };

            ws.onclose = () => {
                if (connStatus) {
                    connStatus.textContent = 'RECONNECTING...';
                }
                setTimeout(connectWebSocket, 2000);
            };

            ws.onerror = () => {
                ws.close();
            };
        } catch (e) {
            setTimeout(connectWebSocket, 3000);
        }
    }

    // Initial Start
    loadAccount();
    updatePipValue();
    setChartMode('smc');
    loadAnalysis();
    fetchOrders();
    fetchRadarData();
    fetchSuggestions();
    connectWebSocket();

    // Fast initial re-sync after 800ms to guarantee live data without pair switching
    setTimeout(() => {
        loadAnalysis();
        fetchRadarData();
        fetchSuggestions();
    }, 800);

    function updateSetupElapsedTime() {
        if (!state.activeSetupTimestamp) return;
        const valSetupElapsed = document.getElementById('val-setup-elapsed');
        if (!valSetupElapsed) return;
        const elapsedSec = Math.max(0, Math.floor((Date.now() - state.activeSetupTimestamp) / 1000));
        let elapsedStr = '';
        if (elapsedSec < 60) {
            elapsedStr = `(${elapsedSec}s ago)`;
        } else if (elapsedSec < 3600) {
            elapsedStr = `(${Math.floor(elapsedSec / 60)}m ${elapsedSec % 60}s ago)`;
        } else {
            elapsedStr = `(${Math.floor(elapsedSec / 3600)}h ${Math.floor((elapsedSec % 3600) / 60)}m ago)`;
        }
        valSetupElapsed.textContent = elapsedStr;
    }

    setInterval(() => {
        loadAnalysis();
        fetchRadarData();
        fetchSuggestions();
        fetchOrders();
    }, 4000);

    // Dynamic 1-second timer to keep trade box elapsed time ticking live
    setInterval(updateSetupElapsedTime, 1000);
});
