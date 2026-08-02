/*
 * Fusion cockpit chart.
 *
 * Deliberately three panels over one shared time axis rather than one panel with three curves:
 * each class needs its own reference band and threshold, and stacking them keeps every
 * comparison vertical. Spaghetti gets twice the height because it is the only class that stops
 * a print - the layout encodes the severity.
 *
 * Kept out of app.js: this is self-contained canvas drawing with no dependency on anything
 * else in the app, and app.js is already long enough.
 *
 * Palette is validated for colour-vision deficiency against both surfaces. Do not swap the
 * hues without re-checking: amber and green both sit below 3:1 contrast on white, which is why
 * every panel carries a written label and a table view exists.
 */
window.YoloFusionChart = (function () {
    "use strict";

    // Mirrors h848.software.yoloraker.fusion.FusionEngine. Kept here so the "what would have
    // happened" replay below matches the server; if the engine changes, change these too.
    const ALARM_AT = 5.0;
    const EXCURSION_MARGIN = 0.15;
    const REFERENCE_CAP = 0.75;
    const HIGH_CONFIDENCE_OVERRIDE = 0.90;
    const CYCLE_SECONDS = 10;

    const PANELS = [
        { key: "spaghetti", name: "Spaghetti", note: "pauses the print", weight: 2.0,
          conf: "confSpaghetti", ref: "refSpaghetti", light: "#f59e0b", dark: "#d97706" },
        { key: "stringing", name: "Stringing", note: "notifies only", weight: 1.0,
          conf: "confStringing", ref: "refStringing", light: "#a855f7", dark: "#a855f7" },
        { key: "zits", name: "Zits", note: "notifies only", weight: 1.0,
          conf: "confZits", ref: "refZits", light: "#22c55e", dark: "#16a34a" }
    ];

    function isDark() {
        const stamped = document.documentElement.getAttribute("data-theme");
        if (stamped === "dark") return true;
        if (stamped === "light") return false;
        return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    }

    function theme() {
        const dark = isDark();
        return {
            dark: dark,
            surface: dark ? "#161d29" : "#ffffff",
            ink: dark ? "#e2e8f0" : "#1e293b",
            muted: dark ? "#94a3b8" : "#64748b",
            faint: dark ? "#64748b" : "#94a3b8",
            border: dark ? "#26303f" : "#e2e8f0",
            grid: dark ? "#202b3a" : "#eef2f6",
            hatch: dark ? "rgba(148,163,184,.16)" : "rgba(100,116,139,.14)",
            critical: dark ? "#f87171" : "#ef4444",
            info: dark ? "#60a5fa" : "#3b82f6",
            colour: p => (dark ? p.dark : p.light)
        };
    }

    const fmtClock = ts => new Date(ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

    /**
     * Replays the stored rows through both decision paths so the shadow-mode question - "is
     * fusion actually suppressing anything?" - has a number instead of a vibe.
     *
     * This is an approximation from what was persisted, not a recording of what the server did:
     * the row stores the score, the reference and the suppression factor, but not the gate
     * result, so that is re-derived here.
     */
    function replay(rows, thresholds) {
        const out = { legacy: 0, fused: 0, byRule: {} };
        const legacyLevel = {}, fusedLevel = {};

        rows.forEach(row => {
            PANELS.forEach(p => {
                const threshold = thresholds[p.key];
                const raw = row[p.conf];
                if (raw == null) return;

                // Old logic: one step per frame over the threshold, one step back otherwise.
                legacyLevel[p.key] = Math.max(0, Math.min(ALARM_AT,
                    (legacyLevel[p.key] || 0) + (raw >= threshold ? 1 : -1)));
                if (legacyLevel[p.key] >= ALARM_AT) { out.legacy++; legacyLevel[p.key] = 0; }

                // Fusion: baseline gate, then a rate scaled by confidence and suppressors.
                const ref = row[p.ref];
                const excursion = ref == null ? raw : raw - Math.min(ref, REFERENCE_CAP);
                const gated = raw >= threshold && excursion >= EXCURSION_MARGIN;
                const overridden = gated && raw >= HIGH_CONFIDENCE_OVERRIDE;
                const suppression = overridden ? 1 : (row.suppression != null ? row.suppression : 1);
                const gain = gated ? (1 + 4 * (raw - threshold)) * suppression : -1;

                fusedLevel[p.key] = Math.max(0, Math.min(ALARM_AT, (fusedLevel[p.key] || 0) + gain));
                if (fusedLevel[p.key] >= ALARM_AT) { out.fused++; fusedLevel[p.key] = 0; }

                // Attribute the difference: why did fusion hold back on a frame the old logic liked?
                if (raw >= threshold && !gated) {
                    out.byRule.BASELINE = (out.byRule.BASELINE || 0) + 1;
                } else if (gated && suppression < 1 && row.fusionRules) {
                    row.fusionRules.split(",").forEach(chunk => {
                        const name = chunk.split("×")[0].split("x")[0].trim();
                        if (name && name !== "NO_BASELINE" && name !== "NO_TELEMETRY") {
                            out.byRule[name] = (out.byRule[name] || 0) + 1;
                        }
                    });
                }
            });
        });
        return out;
    }

    /** Ten minutes of a print where zigzag infill keeps stringing high and a real failure ends it. */
    function demoRows() {
        let seed = 20260802;
        const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
        const rows = [], now = Date.now(), N = 180;
        for (let i = 0; i < N; i++) {
            const infill = 0.44 + 0.24 * Math.sin(i / 7.5) * Math.sin(i / 23 + 1.1);
            let spag = 0.07 + (rnd() - 0.5) * 0.05;
            if (i < 11) spag += 0.34 * (1 - i / 11);
            if (i > 148) spag += Math.min(0.86, (i - 148) / 13) * 0.88;
            const rules = [];
            if (i < 9) rules.push("EARLY_PRINT×0.50");
            if (i >= 74 && i <= 88) rules.push("NO_EXTRUSION×0.40");
            rows.push({
                timestamp: now - (N - i) * CYCLE_SECONDS * 1000,
                confSpaghetti: Math.max(0.02, Math.min(0.97, spag)),
                confStringing: Math.max(0.04, Math.min(0.96, infill + (rnd() - 0.5) * 0.07)),
                confZits: Math.max(0.01, 0.05 + (rnd() - 0.5) * 0.05),
                suppression: rules.length ? (i < 9 ? 0.5 : 0.4) : 1.0,
                fusionRules: rules.join(",")
            });
        }
        // Trailing 20th percentile over a 120-sample window, as DetectionHistory computes it.
        PANELS.forEach(p => {
            rows.forEach((row, i) => {
                const from = Math.max(0, i - 120);
                if (i - from < 12) { row[p.ref] = null; return; }
                const v = rows.slice(from, i + 1).map(r => r[p.conf]).sort((a, b) => a - b);
                row[p.ref] = v[Math.floor(0.2 * v.length)];
            });
        });
        return rows;
    }

    function hasFusionData(rows) {
        return rows.some(r => r.refSpaghetti != null || r.refStringing != null || r.refZits != null);
    }

    function render(canvas, tooltipEl, rows, events, thresholds) {
        const demo = !hasFusionData(rows);
        const data = demo ? demoRows() : rows;
        const ctx = canvas.getContext("2d");
        let hover = -1, geom = null;

        function draw() {
            const t = theme();
            const dpr = window.devicePixelRatio || 1;
            const W = canvas.clientWidth || 800;
            // Height comes from the markup so the view can be resized without touching this file.
            const H = parseInt(canvas.dataset.height || canvas.getAttribute('height') || '380', 10);
            canvas.width = W * dpr;
            canvas.height = H * dpr;
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            ctx.clearRect(0, 0, W, H);

            const N = data.length;
            if (N === 0) {
                ctx.fillStyle = t.muted;
                ctx.font = "13px system-ui, sans-serif";
                ctx.textAlign = "center";
                ctx.fillText("No telemetry recorded yet.", W / 2, H / 2);
                return;
            }

            const padL = 42, padR = 12, padT = 6, axisH = 24, gapY = 12;
            const plotW = Math.max(10, W - padL - padR);
            const totalWeight = PANELS.reduce((s, p) => s + p.weight, 0);
            const availH = H - padT - axisH - gapY * (PANELS.length - 1);
            const x = i => padL + (N === 1 ? plotW / 2 : (i / (N - 1)) * plotW);

            const bands = [];
            let y0 = padT;

            PANELS.forEach(p => {
                const h = (availH * p.weight) / totalWeight;
                const col = t.colour(p);
                const yv = v => y0 + h - Math.max(0, Math.min(1, v)) * h;
                bands.push({ p, top: y0, h: h, yv: yv, col: col });

                ctx.fillStyle = t.surface;
                ctx.fillRect(padL, y0, plotW, h);

                // Suppressed stretches, as a neutral texture. Status colours are reserved for
                // events and must never read as a fourth series.
                //
                // Each run clips to its own rectangle. Clipping to the whole panel instead let
                // the 45-degree strokes - which start a panel-height to the left of the run so
                // the leading diagonal is complete - bleed that far past both ends, so two short
                // suppressed stretches shaded most of the chart.
                let run = null;
                for (let i = 0; i <= N; i++) {
                    const on = i < N && !!data[i].fusionRules && (data[i].suppression || 1) < 1;
                    if (on && run === null) run = i;
                    if (!on && run !== null) {
                        const x1 = x(run), x2 = x(Math.max(run, i - 1));
                        ctx.save();
                        ctx.beginPath();
                        ctx.rect(x1, y0, Math.max(1, x2 - x1), h);
                        ctx.clip();
                        ctx.strokeStyle = t.hatch; ctx.lineWidth = 1.6;
                        for (let px = x1 - h; px < x2 + h; px += 5) {
                            ctx.beginPath(); ctx.moveTo(px, y0 + h); ctx.lineTo(px + h, y0); ctx.stroke();
                        }
                        ctx.restore();
                        run = null;
                    }
                }

                ctx.strokeStyle = t.grid; ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(padL, Math.round(yv(0.5)) + 0.5);
                ctx.lineTo(padL + plotW, Math.round(yv(0.5)) + 0.5);
                ctx.stroke();

                // Reference band in the series' own hue - it is that class's baseline, not a
                // separate entity, so sharing the hue is the honest encoding.
                ctx.save();
                ctx.beginPath(); ctx.rect(padL, y0, plotW, h); ctx.clip();
                let started = false;
                ctx.beginPath();
                for (let i = 0; i < N; i++) {
                    const r = data[i][p.ref];
                    if (r == null) continue;
                    if (!started) { ctx.moveTo(x(i), yv(0)); started = true; }
                    ctx.lineTo(x(i), yv(r));
                }
                if (started) {
                    for (let i = N - 1; i >= 0; i--) {
                        if (data[i][p.ref] != null) { ctx.lineTo(x(i), yv(0)); break; }
                    }
                    ctx.closePath();
                    ctx.globalAlpha = 0.22; ctx.fillStyle = col; ctx.fill(); ctx.globalAlpha = 1;
                }
                ctx.restore();

                const thr = thresholds[p.key];
                ctx.save();
                ctx.setLineDash([5, 4]); ctx.strokeStyle = t.faint; ctx.lineWidth = 1.5;
                ctx.beginPath();
                ctx.moveTo(padL, Math.round(yv(thr)) + 0.5);
                ctx.lineTo(padL + plotW, Math.round(yv(thr)) + 0.5);
                ctx.stroke();
                ctx.restore();

                ctx.beginPath();
                for (let i = 0; i < N; i++) {
                    const v = data[i][p.conf] || 0;
                    if (i === 0) ctx.moveTo(x(i), yv(v)); else ctx.lineTo(x(i), yv(v));
                }
                ctx.strokeStyle = col; ctx.lineWidth = 2; ctx.lineJoin = "round"; ctx.stroke();

                // Written label: identity must not depend on colour alone.
                ctx.font = "600 11.5px system-ui, -apple-system, sans-serif";
                ctx.fillStyle = t.ink; ctx.textAlign = "left"; ctx.textBaseline = "top";
                ctx.fillText(p.name, padL + 7, y0 + 5);
                const nameW = ctx.measureText(p.name).width;
                ctx.font = "10px system-ui, -apple-system, sans-serif";
                ctx.fillStyle = t.faint;
                ctx.fillText(p.note, padL + 15 + nameW, y0 + 6);

                ctx.font = "10px ui-monospace, Menlo, monospace";
                ctx.fillStyle = t.faint; ctx.textAlign = "right"; ctx.textBaseline = "middle";
                ctx.fillText("100", padL - 7, yv(1) + 5);
                ctx.fillText("0", padL - 7, yv(0) - 4);
                ctx.fillText(String(Math.round(thr * 100)), padL - 7, yv(thr));

                ctx.strokeStyle = t.border; ctx.lineWidth = 1;
                ctx.strokeRect(padL + 0.5, y0 + 0.5, plotW - 1, h - 1);

                y0 += h + gapY;
            });

            // Events, pinned to the panel of the class that fired, always with a word.
            const firstTs = data[0].timestamp, lastTs = data[N - 1].timestamp;
            (events || []).forEach(ev => {
                const idx = PANELS.findIndex(p => p.key === ev.type);
                if (idx < 0 || ev.ts < firstTs || ev.ts > lastTs) return;
                const b = bands[idx];
                const frac = lastTs === firstTs ? 0.5 : (ev.ts - firstTs) / (lastTs - firstTs);
                const px = padL + frac * plotW;
                const col = ev.action === "PAUSED" ? t.critical : t.info;

                ctx.save();
                ctx.setLineDash([3, 3]); ctx.strokeStyle = col; ctx.lineWidth = 1.5; ctx.globalAlpha = 0.75;
                ctx.beginPath(); ctx.moveTo(px, b.top); ctx.lineTo(px, b.top + b.h); ctx.stroke();
                ctx.restore();

                ctx.beginPath(); ctx.arc(px, b.top + 12, 5, 0, Math.PI * 2);
                ctx.fillStyle = col; ctx.fill();
                ctx.strokeStyle = t.surface; ctx.lineWidth = 2; ctx.stroke();

                ctx.font = "700 9.5px ui-monospace, Menlo, monospace";
                ctx.fillStyle = col;
                ctx.textAlign = px > W - 110 ? "right" : "left";
                ctx.textBaseline = "middle";
                ctx.fillText(ev.action, px + (px > W - 110 ? -10 : 10), b.top + 12);
            });

            const axisY = H - axisH + 4;
            ctx.strokeStyle = t.border; ctx.lineWidth = 1;
            ctx.beginPath(); ctx.moveTo(padL, axisY + 0.5); ctx.lineTo(padL + plotW, axisY + 0.5); ctx.stroke();
            ctx.font = "10px ui-monospace, Menlo, monospace";
            ctx.fillStyle = t.faint; ctx.textAlign = "center"; ctx.textBaseline = "top";
            const step = Math.max(1, Math.floor(N / 6));
            for (let i = 0; i < N; i += step) ctx.fillText(fmtClock(data[i].timestamp), x(i), axisY + 6);

            if (hover >= 0 && hover < N) {
                const px = x(hover);
                ctx.save();
                ctx.strokeStyle = t.dark ? "#38455a" : "#cbd5e1"; ctx.lineWidth = 1;
                ctx.beginPath(); ctx.moveTo(Math.round(px) + 0.5, padT); ctx.lineTo(Math.round(px) + 0.5, axisY);
                ctx.stroke(); ctx.restore();
                bands.forEach(b => {
                    const v = data[hover][b.p.conf] || 0;
                    ctx.beginPath(); ctx.arc(px, b.yv(v), 4.5, 0, Math.PI * 2);
                    ctx.fillStyle = b.col; ctx.fill();
                    ctx.strokeStyle = t.surface; ctx.lineWidth = 2; ctx.stroke();
                });
            }

            geom = { padL: padL, plotW: plotW, N: N };
        }

        function showTip(clientX) {
            const t = theme();
            const row = data[hover];
            const pct = v => (v * 100).toFixed(0) + " %";
            const lines = PANELS.map(p => {
                const ref = row[p.ref];
                return '<div class="fc-tip-row"><i style="background:' + t.colour(p) + '"></i>' +
                       "<span>" + p.name + "</span><b>" + pct(row[p.conf] || 0) + "</b>" +
                       '<b class="fc-tip-ref">' + (ref == null ? "ref —" : "ref " + pct(ref)) + "</b></div>";
            }).join("");

            const rules = row.fusionRules
                ? row.fusionRules.split(",").map(r => '<span class="fc-chip">' + r + "</span>").join("")
                : '<span class="fc-chip">none</span>';

            tooltipEl.innerHTML =
                '<div class="fc-tip-time">' + new Date(row.timestamp).toLocaleString() + "</div>" +
                lines +
                '<div class="fc-tip-rules"><span class="fc-tip-label">suppressors</span>' + rules + "</div>";
            tooltipEl.classList.add("on");

            const shell = canvas.parentElement.getBoundingClientRect();
            const relX = clientX - shell.left;
            const w = tooltipEl.offsetWidth;
            tooltipEl.style.left = Math.min(Math.max(relX + 16, 4), Math.max(4, shell.width - w - 4)) + "px";
            tooltipEl.style.top = "10px";
        }

        function onMove(e) {
            if (!geom) return;
            const r = canvas.getBoundingClientRect();
            const rel = (e.clientX - r.left - geom.padL) / geom.plotW;
            const i = Math.round(rel * (geom.N - 1));
            if (i < 0 || i >= geom.N) { hover = -1; tooltipEl.classList.remove("on"); draw(); return; }
            hover = i; draw(); showTip(e.clientX);
        }

        canvas.onpointermove = onMove;
        canvas.onpointerleave = () => { hover = -1; tooltipEl.classList.remove("on"); draw(); };

        draw();
        return {
            demo: demo,
            rows: data,
            summary: replay(data, thresholds),
            redraw: draw
        };
    }

    return { render: render, ALARM_AT: ALARM_AT, CYCLE_SECONDS: CYCLE_SECONDS };
})();
