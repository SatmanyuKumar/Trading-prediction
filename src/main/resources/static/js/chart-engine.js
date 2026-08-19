/**
 * Pro TradingView-Grade High Performance Smooth Canvas Charting Engine
 * Features:
 * - Perfectly centered candles (TradingView style 28% top & bottom balanced margin)
 * - Right-edge candle breathing room (5 empty bars before price axis)
 * - 2D Smooth Free Panning (Drag horizontally for Time, vertically for Price)
 * - Price axis vertical scale dragging & Time axis horizontal scale dragging
 * - Cursor-anchored smooth wheel zoom
 * - On-Chart floating controls (Zoom In/Out, Auto-Fit, Reset, Maximize)
 * - Keyboard navigation (Arrows, +, -, 0, Space)
 * - High-DPI Retina scaling with zero blur
 * - High contrast visual overlays (Untouched FVGs with 50% CE, OBs, SR rays, BOS, SL/TP)
 */
class TradingChartEngine {
    constructor(canvasId, tooltipId) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.tooltip = document.getElementById(tooltipId);

        this.candles = [];
        this.fvgs = [];
        this.obs = [];
        this.srs = [];
        this.structures = [];
        this.ema20 = [];
        this.ema50 = [];
        this.ema200 = [];
        this.tradeSetup = null;

        // Display Toggles
        this.flags = {
            fvg: true,
            ob: true,
            sr: true,
            bos: true,
            ema: true,
            setup: true
        };

        // Smooth Viewport Parameters (TradingView Defaults)
        this.candleWidth = 10.0;
        this.candleGap = 4.0;
        this.panOffset = 10.0; // 10 empty bar breathing space on right for future setup box
        this.verticalScaleMultiplier = 1.0; // custom vertical zoom
        this.verticalOffset = 0.0; // custom vertical price pan

        this.paddingTop = 20;
        this.paddingBottom = 20;
        this.priceAxisWidth = 90;
        this.timeAxisHeight = 28;

        // Interaction State
        this.isDragging = false;
        this.isDraggingPriceAxis = false;
        this.isDraggingTimeAxis = false;
        this.dragStartX = 0;
        this.dragStartY = 0;
        this.initialPanOffset = 10.0;
        this.initialVerticalOffset = 0;
        this.initialCandleWidth = 10;
        this.initialVerticalScale = 1.0;

        // Cached Price Range for 2D Pan
        this.cachedPriceRange = 1.0;

        this.mouseX = -1;
        this.mouseY = -1;
        this.needsRender = true;

        this.init();
        this.startRenderLoop();
    }

    init() {
        this.handleResize();
        window.addEventListener('resize', () => {
            this.handleResize();
            this.requestRender();
        });

        // Mouse Down (Handles 2D canvas pan, price axis scale, and time axis scale)
        this.canvas.addEventListener('mousedown', (e) => {
            const rect = this.canvas.getBoundingClientRect();
            const x = (e.clientX - rect.left);
            const y = (e.clientY - rect.top);
            const chartW = this.displayWidth - this.priceAxisWidth;
            const chartH = this.displayHeight - this.timeAxisHeight;

            if (x >= chartW) {
                // Dragging Price Axis -> Smooth Vertical Height Scaling
                this.isDraggingPriceAxis = true;
                this.dragStartY = e.clientY;
                this.initialVerticalScale = this.verticalScaleMultiplier;
            } else if (y >= chartH) {
                // Dragging Time Axis -> Smooth Horizontal Bar Width Scaling
                this.isDraggingTimeAxis = true;
                this.dragStartX = e.clientX;
                this.initialCandleWidth = this.candleWidth;
            } else {
                // Dragging Chart Canvas -> Full 2D Smooth Panning (X & Y)
                this.isDragging = true;
                this.dragStartX = e.clientX;
                this.dragStartY = e.clientY;
                this.initialPanOffset = this.panOffset;
                this.initialVerticalOffset = this.verticalOffset;
            }
        });

        window.addEventListener('mouseup', () => {
            this.isDragging = false;
            this.isDraggingPriceAxis = false;
            this.isDraggingTimeAxis = false;
        });

        // Mouse Move (1:1 smooth 2D tracking)
        this.canvas.addEventListener('mousemove', (e) => {
            const rect = this.canvas.getBoundingClientRect();
            this.mouseX = (e.clientX - rect.left);
            this.mouseY = (e.clientY - rect.top);

            const chartW = this.displayWidth - this.priceAxisWidth;
            const chartH = this.displayHeight - this.timeAxisHeight;

            // Cursor styling based on hover area
            if (this.mouseX >= chartW) {
                this.canvas.style.cursor = 'ns-resize';
            } else if (this.mouseY >= chartH) {
                this.canvas.style.cursor = 'ew-resize';
            } else if (this.isDragging) {
                this.canvas.style.cursor = 'grabbing';
            } else {
                this.canvas.style.cursor = 'crosshair';
            }

            if (this.isDragging) {
                const deltaX = e.clientX - this.dragStartX;
                const deltaY = e.clientY - this.dragStartY;
                const step = this.candleWidth + this.candleGap;

                // 1. Horizontal Time Pan
                this.panOffset = this.initialPanOffset + (deltaX / step);
                this.clampPanOffset();

                // 2. Vertical Price Pan (2D Panning)
                const usableH = Math.max(50, chartH - this.paddingTop - this.paddingBottom);
                const priceDelta = (deltaY / usableH) * this.cachedPriceRange;
                this.verticalOffset = this.initialVerticalOffset + priceDelta;

                this.requestRender();
            } else if (this.isDraggingPriceAxis) {
                const deltaY = this.dragStartY - e.clientY;
                const factor = 1.0 + (deltaY / 180.0);
                this.verticalScaleMultiplier = Math.max(0.15, Math.min(6.0, this.initialVerticalScale * factor));
                this.requestRender();
            } else if (this.isDraggingTimeAxis) {
                const deltaX = this.dragStartX - e.clientX;
                const factor = 1.0 + (deltaX / 250.0);
                this.candleWidth = Math.max(2.5, Math.min(45.0, this.initialCandleWidth / factor));
                this.candleGap = Math.max(1.0, this.candleWidth * 0.4);
                this.requestRender();
            } else {
                this.requestRender();
            }
        });

        this.canvas.addEventListener('mouseleave', () => {
            this.mouseX = -1;
            this.mouseY = -1;
            this.requestRender();
        });

        // Buttery Smooth Cursor-Anchored Wheel Zoom
        this.canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const rect = this.canvas.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const chartW = this.displayWidth - this.priceAxisWidth;

            if (mouseX < 0 || mouseX > chartW) return;

            // Zoom intensity factor
            const zoomFactor = e.deltaY < 0 ? 1.15 : 0.87;
            const prevWidth = this.candleWidth;
            const newWidth = Math.max(2.5, Math.min(45.0, prevWidth * zoomFactor));

            if (prevWidth !== newWidth) {
                const stepPrev = prevWidth + this.candleGap;
                const stepNew = newWidth + Math.max(1.0, newWidth * 0.4);

                const candlesFromRight = (chartW - mouseX) / stepPrev;
                const newCandlesFromRight = (chartW - mouseX) / stepNew;

                this.panOffset += (candlesFromRight - newCandlesFromRight);
                this.candleWidth = newWidth;
                this.candleGap = Math.max(1.0, newWidth * 0.4);
                this.clampPanOffset();
                this.requestRender();
            }
        }, { passive: false });

        // Double Click to Reset Scale & View
        this.canvas.addEventListener('dblclick', () => {
            this.autoScale();
        });

        // Keyboard Shortcuts for Total Chart Control
        window.addEventListener('keydown', (e) => {
            if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) return;

            if (e.key === '+' || e.key === '=') {
                this.zoomIn();
            } else if (e.key === '-' || e.key === '_') {
                this.zoomOut();
            } else if (e.key === '0' || e.key === ' ') {
                this.autoScale();
            } else if (e.key === 'ArrowLeft') {
                this.panOffset += 4.0;
                this.clampPanOffset();
                this.requestRender();
            } else if (e.key === 'ArrowRight') {
                this.panOffset -= 4.0;
                this.clampPanOffset();
                this.requestRender();
            } else if (e.key === 'ArrowUp') {
                this.verticalOffset += (this.cachedPriceRange * 0.05);
                this.requestRender();
            } else if (e.key === 'ArrowDown') {
                this.verticalOffset -= (this.cachedPriceRange * 0.05);
                this.requestRender();
            }
        });
    }

    startRenderLoop() {
        const loop = () => {
            if (this.needsRender) {
                this.needsRender = false;
                this.render();
            }
            requestAnimationFrame(loop);
        };
        requestAnimationFrame(loop);
    }

    requestRender() {
        this.needsRender = true;
    }

    handleResize() {
        const rect = this.canvas.parentElement.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) return;

        const dpr = window.devicePixelRatio || 1;
        this.canvas.width = rect.width * dpr;
        this.canvas.height = rect.height * dpr;
        this.ctx.setTransform(1, 0, 0, 1, 0, 0);
        this.ctx.scale(dpr, dpr);

        this.displayWidth = rect.width;
        this.displayHeight = rect.height;
        this.requestRender();
    }

    setData(analysisData) {
        if (!analysisData) return;
        this.candles = analysisData.candles || [];
        this.fvgs = analysisData.fairValueGaps || [];
        this.obs = analysisData.orderBlocks || [];
        this.srs = analysisData.supportResistanceList || [];
        this.structures = analysisData.marketStructures || [];
        this.ema20 = analysisData.ema20 || [];
        this.ema50 = analysisData.ema50 || [];
        this.ema200 = analysisData.ema200 || [];
        this.tradeSetup = analysisData.tradeSetup || null;
        this.requestRender();
    }

    setFlags(newFlags) {
        this.flags = { ...this.flags, ...newFlags };
        this.requestRender();
    }

    clampPanOffset() {
        const maxOffset = Math.max(0, this.candles.length - 8);
        this.panOffset = Math.max(-80.0, Math.min(maxOffset, this.panOffset));
    }

    zoomIn() {
        this.candleWidth = Math.min(45.0, this.candleWidth * 1.25);
        this.candleGap = Math.max(1.0, this.candleWidth * 0.4);
        this.clampPanOffset();
        this.requestRender();
    }

    zoomOut() {
        this.candleWidth = Math.max(2.5, this.candleWidth * 0.8);
        this.candleGap = Math.max(1.0, this.candleWidth * 0.4);
        this.clampPanOffset();
        this.requestRender();
    }

    autoScale() {
        this.verticalScaleMultiplier = 1.0;
        this.verticalOffset = 0.0;
        this.panOffset = 10.0; // Reset to 10 empty bars breathing room for future setup
        this.requestRender();
    }

    resetZoom() {
        this.candleWidth = 10.0;
        this.candleGap = 4.0;
        this.panOffset = 10.0;
        this.verticalScaleMultiplier = 1.0;
        this.verticalOffset = 0.0;
        this.requestRender();
    }

    render() {
        if (!this.ctx || !this.displayWidth || !this.displayHeight) return;

        const w = this.displayWidth;
        const h = this.displayHeight;
        const ctx = this.ctx;

        ctx.clearRect(0, 0, w, h);
        ctx.fillStyle = '#080b11';
        ctx.fillRect(0, 0, w, h);

        if (this.candles.length === 0) {
            ctx.fillStyle = '#94a3b8';
            ctx.font = '14px Plus Jakarta Sans';
            ctx.textAlign = 'center';
            ctx.fillText('Streaming live authentic exchange candles...', w / 2, h / 2);
            return;
        }

        const chartW = w - this.priceAxisWidth;
        const chartH = h - this.timeAxisHeight;
        const step = this.candleWidth + this.candleGap;
        const visibleCount = Math.ceil(chartW / step) + 6;

        // Sub-pixel fractional slice calculation
        const fractionalEnd = (this.candles.length - 1) - this.panOffset;
        const endIndex = Math.min(this.candles.length - 1, Math.max(0, Math.ceil(fractionalEnd)));
        const startIndex = Math.max(0, Math.floor(fractionalEnd - visibleCount));
        const visibleCandles = this.candles.slice(startIndex, endIndex + 1);

        if (visibleCandles.length === 0) return;

        // 1. Calculate candle high/low strictly from visible candles for perfect vertical centering
        let candleMin = Infinity;
        let candleMax = -Infinity;

        visibleCandles.forEach(c => {
            const l = c.low !== undefined ? c.low : c.getLow();
            const h = c.high !== undefined ? c.high : c.getHigh();
            if (l < candleMin) candleMin = l;
            if (h > candleMax) candleMax = h;
        });

        if (candleMin === Infinity || candleMax === -Infinity) return;

        // Include nearest overhead & underneath key levels into view calculation
        let displayMax = candleMax;
        let displayMin = candleMin;

        if (this.flags && this.flags.sr && this.srs) {
            this.srs.forEach(sr => {
                if (sr.price > candleMax && sr.price <= candleMax * 1.25) {
                    displayMax = Math.max(displayMax, sr.price);
                }
                if (sr.price < candleMin && sr.price >= candleMin * 0.85) {
                    displayMin = Math.min(displayMin, sr.price);
                }
            });
        }

        const candleRange = Math.max(0.001, displayMax - displayMin);
        const candleMid = (displayMin + displayMax) / 2.0;

        const paddedRange = candleRange * 1.30;
        const effectiveHalfRange = (paddedRange / 2.0) / this.verticalScaleMultiplier;
        const centerPrice = candleMid + this.verticalOffset;

        let minPrice = centerPrice - effectiveHalfRange;
        let maxPrice = centerPrice + effectiveHalfRange;

        this.cachedPriceRange = maxPrice - minPrice || 1.0;

        const priceToY = (p) => {
            return chartH - this.paddingBottom - ((p - minPrice) / (maxPrice - minPrice)) * (chartH - this.paddingTop - this.paddingBottom);
        };

        const yToPrice = (y) => {
            return minPrice + ((chartH - this.paddingBottom - y) / (chartH - this.paddingTop - this.paddingBottom)) * (maxPrice - minPrice);
        };

        const indexToX = (idx) => {
            return chartW - ((fractionalEnd - idx) * step) - (this.candleWidth / 2);
        };

        // 1. Grid
        this.drawGrid(ctx, chartW, chartH, minPrice, maxPrice, priceToY);

        // 2. Support & Resistance (if checked)
        if (this.flags.sr) {
            this.drawSupportResistance(ctx, chartW, priceToY);
        }

        // 3. Nearest Untouched Valid Fair Value Gaps (if checked)
        if (this.flags.fvg) {
            this.drawValidUntouchedFVGs(ctx, chartW, indexToX, priceToY);
        }

        // 4. Nearest Untouched Order Blocks (if checked)
        if (this.flags.ob) {
            this.drawValidUntouchedOBs(ctx, chartW, indexToX, priceToY);
        }

        // 5. EMAs (if checked)
        if (this.flags.ema) {
            this.drawEMALine(ctx, this.ema20, startIndex, endIndex, indexToX, priceToY, '#00f2fe', 1.5);
            this.drawEMALine(ctx, this.ema50, startIndex, endIndex, indexToX, priceToY, '#f97316', 1.5);
            this.drawEMALine(ctx, this.ema200, startIndex, endIndex, indexToX, priceToY, '#a855f7', 2.0);
        }

        // 6. Candlesticks (Sub-pixel antialiased, perfectly centered)
        this.drawCandles(ctx, visibleCandles, startIndex, indexToX, priceToY);

        // 7. Market Structure (BOS) (if checked)
        if (this.flags.bos) {
            this.drawMarketStructures(ctx, startIndex, endIndex, indexToX, priceToY);
        }

        // 8. Trade Setup (SL & TP Targets) (if checked)
        if (this.flags.setup && this.tradeSetup) {
            this.drawTradeSetupOverlay(ctx, chartW, priceToY, indexToX);
        }

        // 9. Scales & Axis Labels
        this.drawPriceAxis(ctx, chartW, chartH, minPrice, maxPrice, priceToY);
        this.drawTimeAxis(ctx, chartW, chartH, visibleCandles, startIndex, indexToX);

        // 10. Live Current Price Line & Crosshair
        const lastCandle = this.candles[this.candles.length - 1];
        const lastPrice = lastCandle.close !== undefined ? lastCandle.close : lastCandle.getClose();
        this.drawCurrentPriceLine(ctx, chartW, lastPrice, priceToY);

        if (this.mouseX >= 0 && this.mouseY >= 0 && this.mouseX <= chartW && this.mouseY <= chartH) {
            this.drawCrosshair(ctx, chartW, chartH, this.mouseX, this.mouseY, yToPrice);
        }
    }

    drawGrid(ctx, chartW, chartH, minPrice, maxPrice, priceToY) {
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.035)';
        ctx.lineWidth = 1;
        const stepCount = 8;
        const priceStep = (maxPrice - minPrice) / stepCount;
        for (let i = 0; i <= stepCount; i++) {
            const p = minPrice + i * priceStep;
            const y = priceToY(p);
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(chartW, y);
            ctx.stroke();
        }
    }

    drawCandles(ctx, visibleCandles, startIndex, indexToX, priceToY) {
        if (!visibleCandles || visibleCandles.length === 0) return;

        const wickWidth = Math.max(1.0, this.candleWidth > 12 ? 1.5 : 1.0);
        const halfW = this.candleWidth / 2;

        const bullWicks = [];
        const bullBodies = [];
        const bearWicks = [];
        const bearBodies = [];

        const totalCandles = this.candles.length;

        for (let i = 0; i < visibleCandles.length; i++) {
            const c = visibleCandles[i];
            const idx = startIndex + i;
            const x = indexToX(idx);
            const open = c.open !== undefined ? c.open : c.getOpen();
            const close = c.close !== undefined ? c.close : c.getClose();
            const high = c.high !== undefined ? c.high : c.getHigh();
            const low = c.low !== undefined ? c.low : c.getLow();

            const isBull = close >= open;
            const yOpen = priceToY(open);
            const yClose = priceToY(close);
            const yHigh = priceToY(high);
            const yLow = priceToY(low);

            const cx = x + halfW;
            const bodyY = Math.min(yOpen, yClose);
            const bodyH = Math.max(1.5, Math.abs(yOpen - yClose));

            if (isBull) {
                bullWicks.push(cx, yHigh, cx, yLow);
                bullBodies.push(x, bodyY, this.candleWidth, bodyH);
            } else {
                bearWicks.push(cx, yHigh, cx, yLow);
                bearBodies.push(x, bodyY, this.candleWidth, bodyH);
            }
        }

        // 1. Draw ALL Bullish Candles in 1 Single GPU Path (60-120 FPS)
        if (bullWicks.length > 0) {
            ctx.strokeStyle = '#10b981';
            ctx.lineWidth = wickWidth;
            ctx.beginPath();
            for (let i = 0; i < bullWicks.length; i += 4) {
                ctx.moveTo(bullWicks[i], bullWicks[i + 1]);
                ctx.lineTo(bullWicks[i + 2], bullWicks[i + 3]);
            }
            ctx.stroke();

            ctx.fillStyle = '#10b981';
            for (let i = 0; i < bullBodies.length; i += 4) {
                ctx.fillRect(bullBodies[i], bullBodies[i + 1], bullBodies[i + 2], bullBodies[i + 3]);
            }
        }

        // 2. Draw ALL Bearish Candles in 1 Single GPU Path
        if (bearWicks.length > 0) {
            ctx.strokeStyle = '#ef4444';
            ctx.lineWidth = wickWidth;
            ctx.beginPath();
            for (let i = 0; i < bearWicks.length; i += 4) {
                ctx.moveTo(bearWicks[i], bearWicks[i + 1]);
                ctx.lineTo(bearWicks[i + 2], bearWicks[i + 3]);
            }
            ctx.stroke();

            ctx.fillStyle = '#ef4444';
            for (let i = 0; i < bearBodies.length; i += 4) {
                ctx.fillRect(bearBodies[i], bearBodies[i + 1], bearBodies[i + 2], bearBodies[i + 3]);
            }
        }

        // 3. Highlight the latest active running candle
        const lastIdx = totalCandles - 1;
        if (lastIdx >= startIndex && lastIdx < startIndex + visibleCandles.length) {
            const lastC = visibleCandles[lastIdx - startIndex];
            const lastX = indexToX(lastIdx);
            const lastOpen = lastC.open !== undefined ? lastC.open : lastC.getOpen();
            const lastClose = lastC.close !== undefined ? lastC.close : lastC.getClose();
            const lastYOpen = priceToY(lastOpen);
            const lastYClose = priceToY(lastClose);
            const bodyY = Math.min(lastYOpen, lastYClose);
            const bodyH = Math.max(1.5, Math.abs(lastYOpen - lastYClose));

            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1.4;
            ctx.strokeRect(lastX - 0.5, bodyY - 0.5, this.candleWidth + 1, bodyH + 1);
        }
    }

    drawValidUntouchedFVGs(ctx, chartW, indexToX, priceToY) {
        this.fvgs.forEach(fvg => {
            const top = fvg.top;
            const bottom = fvg.bottom;
            const ce = fvg.consequentEncroachment || (top + bottom) / 2;
            const isBull = fvg.type === 'BULLISH';
            const isSwing = fvg.id && fvg.id.includes('SWING');

            const yTop = priceToY(top);
            const yBottom = priceToY(bottom);
            const yCE = priceToY(ce);
            const h = Math.max(4, Math.abs(yBottom - yTop));

            const startX = Math.max(0, indexToX(fvg.candleIndex));
            const endX = chartW;

            if (isSwing) {
                ctx.fillStyle = isBull ? 'rgba(168, 85, 247, 0.22)' : 'rgba(236, 72, 153, 0.22)';
                ctx.strokeStyle = isBull ? '#a855f7' : '#ec4899';
            } else {
                ctx.fillStyle = isBull ? 'rgba(0, 242, 254, 0.20)' : 'rgba(239, 68, 68, 0.20)';
                ctx.strokeStyle = isBull ? '#00f2fe' : '#ef4444';
            }
            
            ctx.fillRect(startX, Math.min(yTop, yBottom), endX - startX, h);

            // Glowing Boundary Box
            ctx.lineWidth = isSwing ? 1.8 : 1.4;
            ctx.strokeRect(startX, Math.min(yTop, yBottom), endX - startX, h);

            // 50% Consequent Encroachment Line
            ctx.setLineDash([5, 4]);
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 1.3;
            ctx.beginPath();
            ctx.moveTo(startX, yCE);
            ctx.lineTo(endX, yCE);
            ctx.stroke();
            ctx.setLineDash([]);

            // Label with Mode Tag
            ctx.fillStyle = isSwing ? (isBull ? '#c084fc' : '#f472b6') : (isBull ? '#00f2fe' : '#ef4444');
            ctx.font = 'bold 10.5px JetBrains Mono';
            ctx.textAlign = 'left';
            const modeTag = isSwing ? '🌊 SWING' : '⚡ SCALP';
            ctx.fillText(`🎯 ${modeTag} ${isBull ? 'DEMAND' : 'SUPPLY'} FVG (50% C.E. ${ce.toFixed(2)})`, startX + 8, yCE - 5);
        });
    }

    drawValidUntouchedOBs(ctx, chartW, indexToX, priceToY) {
        this.obs.forEach(ob => {
            const top = ob.top;
            const bottom = ob.bottom;
            const isBull = ob.type === 'BULLISH';
            const isSwing = ob.id && ob.id.includes('SWING');

            const yTop = priceToY(top);
            const yBottom = priceToY(bottom);
            const h = Math.max(4, Math.abs(yBottom - yTop));

            const startX = Math.max(0, indexToX(ob.candleIndex));
            const endX = chartW;

            if (isSwing) {
                ctx.fillStyle = isBull ? 'rgba(34, 197, 94, 0.20)' : 'rgba(239, 68, 68, 0.20)';
                ctx.strokeStyle = isBull ? '#22c55e' : '#ef4444';
            } else {
                ctx.fillStyle = isBull ? 'rgba(251, 191, 36, 0.18)' : 'rgba(168, 85, 247, 0.18)';
                ctx.strokeStyle = isBull ? '#fbbf24' : '#a855f7';
            }

            ctx.fillRect(startX, Math.min(yTop, yBottom), endX - startX, h);

            ctx.lineWidth = isSwing ? 1.8 : 1.4;
            ctx.strokeRect(startX, Math.min(yTop, yBottom), endX - startX, h);

            ctx.fillStyle = isSwing ? (isBull ? '#4ade80' : '#f87171') : (isBull ? '#fbbf24' : '#a855f7');
            ctx.font = 'bold 10.5px JetBrains Mono';
            ctx.textAlign = 'left';
            const obTag = isSwing ? '🌊 SWING' : '⚡ SCALP';
            ctx.fillText(`📦 ${obTag} ${isBull ? 'DEMAND OB' : 'SUPPLY OB'}`, startX + 8, Math.min(yTop, yBottom) + 13);
        });
    }

    drawSupportResistance(ctx, chartW, priceToY) {
        this.srs.forEach(sr => {
            const y = priceToY(sr.price);
            const typeStr = (sr.type || '').toUpperCase();

            let strokeColor = 'rgba(244, 63, 94, 0.65)';
            let badgeBg = 'rgba(244, 63, 94, 0.22)';
            let textColor = '#f43f5e';
            let lineDash = [6, 4];
            let lineWidth = 1.4;

            if (typeStr.includes('FIB') || typeStr.includes('1.618') || typeStr.includes('1.272')) {
                // Glowing Amber/Cyan Fib Extension
                strokeColor = 'rgba(245, 158, 11, 0.85)';
                badgeBg = 'rgba(245, 158, 11, 0.28)';
                textColor = '#fbbf24';
                lineDash = [7, 3];
                lineWidth = 1.8;
            } else if (typeStr.includes('PSYCHOLOGICAL') || typeStr.includes('INSTITUTIONAL') || typeStr.includes('5,000') || typeStr.includes('CEILING')) {
                // Royal Purple/Blue Institutional Level
                strokeColor = 'rgba(168, 85, 247, 0.85)';
                badgeBg = 'rgba(168, 85, 247, 0.28)';
                textColor = '#c084fc';
                lineDash = [8, 4];
                lineWidth = 1.8;
            } else if (typeStr.includes('BSL') || typeStr.includes('SSL') || typeStr.includes('LIQUIDITY')) {
                // Golden Aqua Liquidity Pool
                strokeColor = 'rgba(0, 242, 254, 0.80)';
                badgeBg = 'rgba(0, 242, 254, 0.22)';
                textColor = '#00f2fe';
                lineDash = [4, 4];
                lineWidth = 1.6;
            } else if (typeStr.includes('SUPPORT')) {
                strokeColor = 'rgba(16, 185, 129, 0.65)';
                badgeBg = 'rgba(16, 185, 129, 0.22)';
                textColor = '#10b981';
            }

            ctx.strokeStyle = strokeColor;
            ctx.lineWidth = lineWidth;
            ctx.setLineDash(lineDash);
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(chartW, y);
            ctx.stroke();
            ctx.setLineDash([]);

            // Dynamic Badge Size
            const labelText = `${sr.type} (${sr.price.toFixed(2)})`;
            ctx.font = 'bold 9.5px JetBrains Mono';
            const textWidth = ctx.measureText(labelText).width;
            const badgeW = Math.max(140, textWidth + 14);

            ctx.fillStyle = badgeBg;
            ctx.fillRect(chartW - badgeW - 10, y - 10, badgeW, 20);
            ctx.strokeStyle = strokeColor;
            ctx.lineWidth = 1;
            ctx.strokeRect(chartW - badgeW - 10, y - 10, badgeW, 20);

            ctx.fillStyle = textColor;
            ctx.textAlign = 'left';
            ctx.fillText(labelText, chartW - badgeW - 3, y + 4);
        });
    }

    drawMarketStructures(ctx, startIndex, endIndex, indexToX, priceToY) {
        this.structures.forEach(st => {
            if (st.candleIndex < startIndex || st.candleIndex > endIndex) return;
            const x = indexToX(st.candleIndex) + (this.candleWidth / 2);
            const y = priceToY(st.price);

            if (st.type === 'BOS') {
                const isBull = st.direction === 'BULLISH';
                ctx.strokeStyle = isBull ? '#10b981' : '#ef4444';
                ctx.lineWidth = 1.5;
                ctx.setLineDash([3, 3]);
                ctx.beginPath();
                ctx.moveTo(x - 20, y);
                ctx.lineTo(x + 20, y);
                ctx.stroke();
                ctx.setLineDash([]);

                ctx.fillStyle = isBull ? '#10b981' : '#ef4444';
                ctx.font = 'bold 10px JetBrains Mono';
                ctx.textAlign = 'center';
                ctx.fillText(`⚡ BOS ${isBull ? '▲' : '▼'}`, x, isBull ? y - 6 : y + 14);
            }
        });
    }

    drawEMALine(ctx, emaData, startIndex, endIndex, indexToX, priceToY, color, width) {
        if (!emaData || emaData.length === 0) return;

        ctx.strokeStyle = color;
        ctx.lineWidth = width;
        ctx.beginPath();
        let started = false;

        for (let i = startIndex; i <= endIndex; i++) {
            if (i >= emaData.length) break;
            const val = emaData[i];
            if (val === null || val === undefined) continue;

            const x = indexToX(i) + (this.candleWidth / 2);
            const y = priceToY(val);

            if (!started) {
                ctx.moveTo(x, y);
                started = true;
            } else {
                ctx.lineTo(x, y);
            }
        }
        ctx.stroke();
    }

    drawTradeSetupOverlay(ctx, chartW, priceToY, indexToX) {
        const setup = this.tradeSetup;
        if (!setup || !setup.signal || setup.signal === 'HOLD' || setup.signal === 'WAIT' || !setup.entryPrice || setup.entryPrice <= 0) return;

        // 🛡️ STRICT 80%+ CHANCE/CONFIDENCE FILTER:
        if (setup.confidence < 80) {
            ctx.fillStyle = 'rgba(30, 41, 59, 0.85)';
            ctx.strokeStyle = 'rgba(148, 163, 184, 0.3)';
            ctx.lineWidth = 1.0;
            const badgeW = 280;
            const badgeH = 24;
            const badgeX = chartW - badgeW - 10;
            const badgeY = 12;
            ctx.beginPath();
            ctx.roundRect ? ctx.roundRect(badgeX, badgeY, badgeW, badgeH, 4) : ctx.rect(badgeX, badgeY, badgeW, badgeH);
            ctx.fill();
            ctx.stroke();

            ctx.font = '600 10.5px JetBrains Mono';
            ctx.fillStyle = '#94a3b8';
            ctx.textAlign = 'center';
            ctx.fillText(`🛡️ Setup Chance: ${setup.confidence}% (< 80% A+ Threshold)`, badgeX + (badgeW / 2), badgeY + 16);
            return;
        }

        const isBuy = setup.signal === 'BUY';
        if (isBuy && (setup.stopLoss >= setup.entryPrice || setup.takeProfit1 <= setup.entryPrice)) return;
        if (!isBuy && (setup.stopLoss <= setup.entryPrice || setup.takeProfit1 >= setup.entryPrice)) return;

        const entryY = priceToY(setup.entryPrice);
        const slY = priceToY(setup.stopLoss);
        const tpY = priceToY(setup.takeProfit1);

        // 🎯 TRADINGVIEW FIXED-WIDTH POSITION BOX (100% NON-RESIZABLE):
        // Fixed 190px width that never stretches or warps on zoom/pan/resize
        const FIXED_BOX_WIDTH = 190;
        const lastCandleIdx = this.candles.length - 1;
        const runningCandleX = indexToX ? indexToX(lastCandleIdx) + this.candleWidth : chartW - 210;
        
        // Locked position: sits at a fixed offset right ahead of the running candle
        const startX = Math.min(chartW - FIXED_BOX_WIDTH - 8, Math.max(8, runningCandleX + 12));
        const endX = startX + FIXED_BOX_WIDTH;
        const boxWidth = FIXED_BOX_WIDTH;

        // 1. Full Guidelines connecting from Current Running Candle to Right Price Axis
        // Entry Guideline (Cyan Glow)
        ctx.setLineDash([4, 3]);
        ctx.strokeStyle = '#00f2fe';
        ctx.lineWidth = 1.8;
        ctx.beginPath();
        ctx.moveTo(runningCandleX, entryY);
        ctx.lineTo(chartW, entryY);
        ctx.stroke();

        // TP Guideline (Green)
        ctx.strokeStyle = 'rgba(16, 185, 129, 0.7)';
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.moveTo(runningCandleX, tpY);
        ctx.lineTo(chartW, tpY);
        ctx.stroke();

        // SL Guideline (Red)
        ctx.strokeStyle = 'rgba(239, 68, 68, 0.7)';
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.moveTo(runningCandleX, slY);
        ctx.lineTo(chartW, slY);
        ctx.stroke();
        ctx.setLineDash([]);

        // 2. Take Profit Target Box (Fixed Width Green Position Zone)
        const tpBoxTop = Math.min(entryY, tpY);
        const tpBoxH = Math.max(6, Math.abs(entryY - tpY));
        ctx.fillStyle = 'rgba(16, 185, 129, 0.25)';
        ctx.fillRect(startX, tpBoxTop, boxWidth, tpBoxH);
        ctx.strokeStyle = '#10b981';
        ctx.lineWidth = 1.8;
        ctx.strokeRect(startX, tpBoxTop, boxWidth, tpBoxH);

        // 3. Stop Loss Protection Box (Fixed Width Red Position Zone)
        const slBoxTop = Math.min(entryY, slY);
        const slBoxH = Math.max(6, Math.abs(entryY - slY));
        ctx.fillStyle = 'rgba(239, 68, 68, 0.25)';
        ctx.fillRect(startX, slBoxTop, boxWidth, slBoxH);
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 1.8;
        ctx.strokeRect(startX, slBoxTop, boxWidth, slBoxH);

        // 4. Center Entry Divider Line (Fixed 190px Width)
        ctx.strokeStyle = '#00f2fe';
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        ctx.moveTo(startX, entryY);
        ctx.lineTo(endX, entryY);
        ctx.stroke();

        // 5. Fixed Center Setup Badge
        const badgeW = 176;
        const badgeH = 22;
        const badgeX = startX + 7;
        const badgeY = entryY - 11;

        ctx.fillStyle = isBuy ? 'rgba(5, 150, 105, 0.96)' : 'rgba(220, 38, 38, 0.96)';
        ctx.strokeStyle = isBuy ? '#34d399' : '#f87171';
        ctx.lineWidth = 1.2;
        
        ctx.beginPath();
        ctx.roundRect ? ctx.roundRect(badgeX, badgeY, badgeW, badgeH, 4) : ctx.rect(badgeX, badgeY, badgeW, badgeH);
        ctx.fill();
        ctx.stroke();

        ctx.font = 'bold 10px JetBrains Mono';
        ctx.fillStyle = '#ffffff';
        ctx.textAlign = 'center';
        const setupIcon = isBuy ? '🚀 BUY' : '🔻 SELL';
        ctx.fillText(`${setupIcon} 50% FVG @ ${setup.entryPrice.toFixed(setup.entryPrice > 500 ? 2 : 4)}`, badgeX + (badgeW / 2), badgeY + 15);

        // TP Label Inside Fixed Green Box
        ctx.font = 'bold 10px JetBrains Mono';
        ctx.fillStyle = '#34d399';
        ctx.textAlign = 'right';
        ctx.fillText(`🎯 TP: ${setup.takeProfit1.toFixed(setup.takeProfit1 > 500 ? 2 : 4)} (1:${setup.riskRewardRatio.toFixed(1)})`, endX - 6, isBuy ? tpBoxTop + 14 : tpBoxTop + tpBoxH - 6);

        // SL Label Inside Fixed Red Box
        ctx.fillStyle = '#f87171';
        ctx.fillText(`🛑 SL: ${setup.stopLoss.toFixed(setup.stopLoss > 500 ? 2 : 4)}`, endX - 6, isBuy ? slBoxTop + slBoxH - 6 : slBoxTop + 14);

        // 6. Right Price Axis Badges for TP & SL
        ctx.fillStyle = '#10b981';
        ctx.fillRect(chartW, tpY - 9, this.priceAxisWidth, 18);
        ctx.fillStyle = '#000000';
        ctx.font = 'bold 10px JetBrains Mono';
        ctx.textAlign = 'left';
        ctx.fillText(`TP ${setup.takeProfit1.toFixed(setup.takeProfit1 > 500 ? 2 : 4)}`, chartW + 4, tpY + 4);

        ctx.fillStyle = '#ef4444';
        ctx.fillRect(chartW, slY - 9, this.priceAxisWidth, 18);
        ctx.fillStyle = '#ffffff';
        ctx.fillText(`SL ${setup.stopLoss.toFixed(setup.stopLoss > 500 ? 2 : 4)}`, chartW + 4, slY + 4);
    }

    drawPriceAxis(ctx, chartW, chartH, minPrice, maxPrice, priceToY) {
        ctx.fillStyle = '#0c111a';
        ctx.fillRect(chartW, 0, this.priceAxisWidth, chartH);
        ctx.strokeStyle = '#1a2233';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(chartW, 0);
        ctx.lineTo(chartW, chartH);
        ctx.stroke();

        ctx.fillStyle = '#94a3b8';
        ctx.font = '11px JetBrains Mono';
        ctx.textAlign = 'left';

        const stepCount = 8;
        const stepVal = (maxPrice - minPrice) / stepCount;
        for (let i = 0; i <= stepCount; i++) {
            const p = minPrice + i * stepVal;
            const y = priceToY(p);
            ctx.fillText(p.toFixed(p > 500 ? 2 : 4), chartW + 8, y + 4);
        }
    }

    drawTimeAxis(ctx, chartW, chartH, visibleCandles, startIndex, indexToX) {
        ctx.fillStyle = '#0c111a';
        ctx.fillRect(0, chartH, chartW, this.timeAxisHeight);
        ctx.strokeStyle = '#1a2233';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(0, chartH);
        ctx.lineTo(chartW, chartH);
        ctx.stroke();

        ctx.fillStyle = '#94a3b8';
        ctx.font = '10px JetBrains Mono';
        ctx.textAlign = 'center';

        const timeStep = Math.max(1, Math.floor(visibleCandles.length / 6));
        for (let i = 0; i < visibleCandles.length; i += timeStep) {
            const c = visibleCandles[i];
            const x = indexToX(startIndex + i) + (this.candleWidth / 2);
            const ts = c.timestamp !== undefined ? c.timestamp : c.getTimestamp();
            const date = new Date(ts);
            const timeStr = `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
            ctx.fillText(timeStr, x, chartH + 18);
        }
    }

    drawCurrentPriceLine(ctx, chartW, currentPrice, priceToY) {
        const y = priceToY(currentPrice);
        ctx.strokeStyle = '#00f2fe';
        ctx.lineWidth = 1.2;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(chartW, y);
        ctx.stroke();
        ctx.setLineDash([]);

        ctx.fillStyle = '#00f2fe';
        ctx.fillRect(chartW, y - 10, this.priceAxisWidth, 20);
        ctx.fillStyle = '#000000';
        ctx.font = 'bold 11px JetBrains Mono';
        ctx.textAlign = 'left';
        ctx.fillText(currentPrice.toFixed(currentPrice > 500 ? 2 : 4), chartW + 6, y + 4);
    }

    drawCrosshair(ctx, chartW, chartH, x, y, yToPrice) {
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)';
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);

        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, chartH);
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(chartW, y);
        ctx.stroke();
        ctx.setLineDash([]);

        const price = yToPrice(y);
        ctx.fillStyle = '#1a2233';
        ctx.fillRect(chartW, y - 9, this.priceAxisWidth, 18);
        ctx.fillStyle = '#ffffff';
        ctx.font = '10px JetBrains Mono';
        ctx.textAlign = 'left';
        ctx.fillText(price.toFixed(price > 500 ? 2 : 4), chartW + 6, y + 4);
    }
}
