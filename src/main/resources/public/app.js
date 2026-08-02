document.addEventListener('DOMContentLoaded', () => {
    const statusIndicator = document.getElementById('api-status');
    const printersList = document.getElementById('printers-list');
    const modal = document.getElementById('printer-modal');

    // Printer objects by id. Replaces reading them back out of a DOM data attribute, which the
    // table layout relied on and which does not survive the card rewrite.
    const printersById = new Map();
    const findPrinter = id => printersById.get(id) || null;
    const form = document.getElementById('printer-form');
    
    const profileModal = document.getElementById('profile-modal');
    const profileForm = document.getElementById('profile-form');

    // Initial loads
    loadPrinters();
    loadProfile();

    function loadProfile() {
        fetch('/api/profile')
            .then(r => r.json())
            .then(data => {
                document.getElementById('prof-display-name').value = data.displayName;
                document.getElementById('prof-username').value = data.username;
                document.getElementById('prof-auth-disabled').checked = data.authDisabled;
                
                document.getElementById('prof-ret-print-count').value = data.retentionPrintCount || 20;

                document.getElementById('prof-fusion-mode').value = data.fusionMode || 'SHADOW';

                // Load models
                loadModels();
            })
            .catch(err => console.error("Failed to load profile", err));
    }

    // --- Model Management ---
    function loadModels(selectedModelName) {
        fetch('/api/models')
            .then(r => r.json())
            .then(models => {
                const select = document.getElementById('printer-ai-model');
                const tbody = document.getElementById('prof-models-tbody');
                
                select.innerHTML = '';
                tbody.innerHTML = '';
                
                let hasCustom = false;

                models.forEach(m => {
                    // Dropdown
                    const opt = document.createElement('option');
                    opt.value = m;
                    opt.textContent = m === 'INBUILT' ? 'INBUILT (Default)' : m;
                    select.appendChild(opt);
                    
                    // Table
                    if (m !== 'INBUILT') {
                        hasCustom = true;
                        const tr = document.createElement('tr');
                        tr.innerHTML = `
                            <td>${m}</td>
                            <td>
                                <button type="button" class="btn danger" onclick="deleteModel('${m}')" style="padding: 4px 8px; font-size: 0.8rem;">Delete</button>
                            </td>
                        `;
                        tbody.appendChild(tr);
                    }
                });
                
                if (!hasCustom) {
                    tbody.innerHTML = '<tr><td colspan="2">No custom models uploaded.</td></tr>';
                }
                
                if (selectedModelName) {
                    select.value = selectedModelName;
                }
            })
            .catch(err => console.error("Failed to load models", err));
    }

    window.uploadAiModel = function() {
        const fileInput = document.getElementById('prof-model-upload');
        if (!fileInput.files || fileInput.files.length === 0) {
            alert('Please select a file first.');
            return;
        }
        
        const file = fileInput.files[0];
        if (!file.name.toLowerCase().endsWith('.onnx')) {
            alert('File must be a .onnx model.');
            return;
        }
        
        const formData = new FormData();
        formData.append('file', file);
        
        const btn = document.querySelector('button[onclick="uploadAiModel()"]');
        btn.textContent = 'Uploading...';
        btn.disabled = true;
        
        fetch('/api/models/upload', {
            method: 'POST',
            body: formData
        })
        .then(r => {
            if (r.ok) {
                fileInput.value = '';
                document.getElementById('file-chosen-text').textContent = 'Choose a file...';
                loadModels();
                alert('Model uploaded successfully!');
            } else {
                return r.text().then(text => { throw new Error(text); });
            }
        })
        .catch(err => alert('Upload failed: ' + err))
        .finally(() => {
            btn.textContent = 'Upload';
            btn.disabled = false;
        });
    };

    window.deleteModel = function(filename) {
        if (confirm(`Delete model ${filename}?`)) {
            fetch(`/api/models/${filename}`, { method: 'DELETE' })
                .then(r => {
                    if (r.ok) {
                        loadModels();
                    } else {
                        alert('Failed to delete model.');
                    }
                })
                .catch(err => alert('Error: ' + err));
        }
    };

    // Check API Status periodically
    function checkApiStatus() {
        fetch('/api/status')
            .then(r => {
                if (!r.ok) throw new Error("API not ok");
                return r.json();
            })
            .then(data => {
                statusIndicator.title = 'API OK';
                statusIndicator.className = 'status-indicator ok';
            })
            .catch(err => {
                statusIndicator.title = 'API ERROR';
                statusIndicator.className = 'status-indicator error';
            });
    }

    checkApiStatus();
    setInterval(checkApiStatus, 5000);

    const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
                                               .replace(/>/g, '&gt;').replace(/"/g, '&quot;');

    /** Prefer a still frame over an MJPEG stream in the overview: N cards means N connections. */
    function snapshotUrl(webcamUrl) {
        if (!webcamUrl) return null;
        return webcamUrl.replace('action=stream', 'action=snapshot');
    }

    // Load Printers
    function loadPrinters() {
        fetch('/api/printers')
            .then(r => r.json())
            .then(printers => {
                printersList.innerHTML = '';
                printersById.clear();

                if (printers.length === 0) {
                    printersList.innerHTML = '<p style="color: #64748b; font-size: 0.9rem;">No printers configured.</p>';
                    return;
                }

                printers.forEach(p => {
                    printersById.set(p.id, p);

                    const card = document.createElement('div');
                    card.className = 'printer-card' + (p.enabled ? '' : ' is-disabled');
                    card.id = `card-${p.id}`;

                    const snap = snapshotUrl(p.webcamUrl);

                    card.innerHTML = `
                        <div class="printer-thumb">
                            <span class="no-cam" id="nocam-${p.id}">${snap ? 'connecting' : 'no camera'}</span>
                            ${snap ? `<img id="thumb-${p.id}" src="${esc(snap)}" alt="Camera view of ${esc(p.name)}">` : ''}
                        </div>
                        <div class="printer-body">
                            <div class="printer-head">
                                <span class="printer-name">${esc(p.name)}</span>
                                <span class="printer-host">${esc(p.hostname)}</span>
                                <span class="state-pill" id="state-${p.id}"><i></i>CHECKING</span>
                                <label class="printer-enable">
                                    <span class="switch">
                                        <input type="checkbox" onchange="togglePrinterEnabled('${p.id}', this.checked)" ${p.enabled ? 'checked' : ''}>
                                        <span class="slider round"></span>
                                    </span>
                                    Enabled
                                </label>
                            </div>
                            <div class="printer-file" id="file-${p.id}"></div>
                            <div class="printer-progress" id="progress-${p.id}" style="display: none;">
                                <span class="printer-progress-track"><span class="printer-progress-fill" id="progress-fill-${p.id}"></span></span>
                                <span class="printer-progress-txt" id="progress-txt-${p.id}"></span>
                            </div>
                            <div class="printer-stats" id="stats-${p.id}"></div>
                            <div class="ai-meters-compact" id="meters-${p.id}"></div>
                            <div id="sat-${p.id}"></div>
                        </div>
                        <div class="printer-actions">
                            <button class="btn primary" onclick="openDashboard('${p.id}')">Live View</button>
                            <button class="btn" onclick="openHistory('${p.id}', '${esc(p.name)}')">History</button>
                            <button class="btn" onclick="editPrinter('${p.id}')">Edit</button>
                            <button class="btn" onclick="deletePrinter('${p.id}')">Delete</button>
                        </div>
                    `;
                    printersList.appendChild(card);

                    // The placeholder is a permanent sibling that the image covers on success.
                    // An earlier version appended it from an inline onerror, which stacked up a
                    // fresh copy on every 5 s refresh of an offline camera.
                    const img = document.getElementById(`thumb-${p.id}`);
                    if (img) {
                        const nocam = document.getElementById(`nocam-${p.id}`);
                        img.onload = () => { img.classList.add('loaded'); };
                        img.onerror = () => { img.classList.remove('loaded'); nocam.textContent = 'no signal'; };
                    }
                });

                startMainTablePolling(printers);
                startThumbnailRefresh(printers);

                // Deep link handled here rather than on DOMContentLoaded: the printer objects
                // have to be cached before the dashboard can be opened for one of them.
                if (isLivePage() && !livePageEntered) {
                    livePageEntered = true;
                    enterLivePage(location.hash.slice('#live/'.length));
                }
            });
    }

    let livePageEntered = false;

    let thumbnailInterval = null;

    /** Re-fetch each card's still every 5 s. A cache-buster is needed or the browser holds the frame. */
    function startThumbnailRefresh(printers) {
        if (thumbnailInterval) clearInterval(thumbnailInterval);
        thumbnailInterval = setInterval(() => {
            printers.forEach(p => {
                const img = document.getElementById(`thumb-${p.id}`);
                const snap = snapshotUrl(p.webcamUrl);
                if (!img || !snap || !p.enabled) return;
                img.src = snap + (snap.includes('?') ? '&' : '?') + '_t=' + Date.now();
            });
        }, 5000);
    }
    
    let mainTableInterval = null;

    // The only switch left on the card. Which defect classes are watched is a per-printer
    // configuration decision and now lives in Edit Printer, next to each class's threshold.
    window.togglePrinterEnabled = function(id, isEnabled) {
        const card = document.getElementById(`card-${id}`);
        if (card) card.classList.toggle('is-disabled', !isEnabled);

        const cached = findPrinter(id);
        if (cached) cached.enabled = isEnabled;

        fetch('/api/printers')
            .then(r => r.json())
            .then(printers => {
                const p = printers.find(x => x.id === id);
                if (p) {
                    p.enabled = isEnabled;
                    fetch(`/api/printers/${id}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(p)
                    }).then(() => loadPrinters());
                }
            });
    };

    // A baseline this high means the camera is looking at something the model reads as a defect
    // for the entire print, leaving a real failure no headroom to stand out. The fix is the camera
    // angle or a crop - no threshold setting helps - so this earns a row of its own.
    function updateSaturationBanner(printer, data) {
        const host = document.getElementById(`sat-${printer.id}`);
        if (!host) return;

        const saturated = (data.aiStatus || []).filter(s => s.saturated && s.reference != null);
        if (saturated.length === 0) {
            host.innerHTML = '';
            return;
        }

        const worst = saturated.reduce((a, b) => (b.reference > a.reference ? b : a));
        const classes = saturated.map(s => s.type).join(', ');
        const pct = (worst.reference * 100).toFixed(0);

        host.innerHTML = `
            <div class="saturation-banner">
                <span class="sb-icon">!</span>
                <div>
                    <b>Camera sees permanent scenery (baseline ${pct}% for ${classes})</b>
                    <span>The model reads something in frame as a defect for the whole print, so a real
                    failure has no headroom to stand out. Move or crop the camera view &mdash; raising the
                    threshold will not help.</span>
                </div>
            </div>
        `;
    }

    function setPill(id, cls, text) {
        const el = document.getElementById(`state-${id}`);
        if (el) {
            el.className = 'state-pill ' + cls;
            el.innerHTML = `<i></i>${text}`;
        }
    }

    /**
     * Remaining time extrapolated from elapsed time and progress. Moonraker reports no ETA on
     * the objects we query, so this is a linear estimate and is labelled with a tilde.
     */
    function remainingSeconds(data) {
        const pct = data.progress || 0;
        if (pct <= 1 || !data.printDuration) return null;
        return Math.max(0, data.printDuration / (pct / 100) - data.printDuration);
    }

    function shortDuration(seconds) {
        if (seconds == null) return '—';
        const h = Math.floor(seconds / 3600), m = Math.floor((seconds % 3600) / 60);
        if (h > 0) return `${h} h ${String(m).padStart(2, '0')} min`;
        return `${m} min`;
    }

    function stat(label, value) {
        return `<span class="pstat"><b>${label}</b>${value}</span>`;
    }

    function renderPrinterStats(printerId, data, printing) {
        const host = document.getElementById(`stats-${printerId}`);
        if (!host) return;

        const parts = [];
        if (printing) {
            parts.push(stat('Elapsed', formatDuration(data.printDuration)));
            parts.push(stat('Remaining', '~' + shortDuration(remainingSeconds(data))));
            parts.push(stat('Filament', `${(data.filamentUsed || 0).toFixed(0)} mm`));
            parts.push(stat('Speed', `${(data.printSpeed || 0).toFixed(0)} mm/s`));
            // liveZ is the real toolhead height, not the planned one - it reads as the layer
            // the printer is actually on.
            parts.push(stat('Height', `${(data.liveZ || 0).toFixed(2)} mm`));
        }
        parts.push(stat('Nozzle', `${(data.extruderTemp || 0).toFixed(0)} / ${(data.extruderTarget || 0).toFixed(0)} °C`));
        parts.push(stat('Bed', `${(data.bedTemp || 0).toFixed(0)} / ${(data.bedTarget || 0).toFixed(0)} °C`));
        parts.push(stat('Fan', `${(data.fanSpeed || 0).toFixed(0)} %`));
        parts.push(stat('Model', data.activeModelName || 'INBUILT'));

        host.innerHTML = parts.join('');
    }

    function renderCompactMeters(printerId, statuses) {
        const host = document.getElementById(`meters-${printerId}`);
        if (!host) return;
        if (!statuses || statuses.length === 0) { host.innerHTML = ''; return; }

        host.innerHTML = statuses.map(s => {
            const pct = Math.max(0, Math.min(100, (s.level / s.alarmAt) * 100));
            const name = s.type.charAt(0).toUpperCase() + s.type.slice(1);
            const hot = s.state === 'IMMINENT' ? ' imminent' : '';
            const note = s.state === 'SCENERY' ? ' · at scenery level' : '';
            return `<span class="ai-meter-compact${hot}" title="model score ${(s.confidence * 100).toFixed(0)} %${note}">
                        ${name}
                        <span class="mc-track"><span class="mc-fill ai-meter-fill ${s.type}" style="width: ${pct}%"></span></span>
                        <span class="mc-val">${s.level.toFixed(1)}</span>
                    </span>`;
        }).join('');
    }

    function startMainTablePolling(printers) {
        if (mainTableInterval) clearInterval(mainTableInterval);

        const updateCards = () => {
            printers.forEach(p => {
                const progressEl = document.getElementById(`progress-${p.id}`);
                const fileEl = document.getElementById(`file-${p.id}`);

                const statsEl = document.getElementById(`stats-${p.id}`);

                if (!p.enabled) {
                    setPill(p.id, 'disabled', 'DISABLED');
                    if (progressEl) progressEl.style.display = 'none';
                    if (fileEl) fileEl.textContent = '';
                    if (statsEl) statsEl.innerHTML = '';
                    renderCompactMeters(p.id, []);
                    return; // Skip telemetry fetch entirely
                }

                fetch(`/api/printers/${p.id}/telemetry`)
                    .then(r => r.json())
                    .then(data => {
                        updateSaturationBanner(p, data);
                        renderCompactMeters(p.id, data.aiStatus);

                        const offline = data.klipperState === 'error' || data.klipperState === 'shutdown'
                                     || data.klipperState === 'offline' || data.klipperMessage === 'Moonraker Unreachable';
                        const state = (data.printState || 'standby').toLowerCase();

                        if (offline) {
                            setPill(p.id, 'offline', 'OFFLINE');
                        } else if (state === 'printing') {
                            setPill(p.id, 'printing', 'PRINTING');
                        } else if (state === 'paused') {
                            setPill(p.id, 'paused', 'PAUSED');
                        } else {
                            setPill(p.id, 'standby', 'STANDBY');
                        }

                        if (fileEl) fileEl.textContent = (!offline && data.filename) ? data.filename : '';

                        const printing = !offline && (state === 'printing' || state === 'paused');
                        if (progressEl) {
                            progressEl.style.display = printing ? 'flex' : 'none';
                            if (printing) {
                                document.getElementById(`progress-fill-${p.id}`).style.width = (data.progress || 0) + '%';
                                document.getElementById(`progress-txt-${p.id}`).textContent = (data.progress || 0).toFixed(0) + ' %';
                            }
                        }

                        if (offline) {
                            if (statsEl) statsEl.innerHTML = '';
                        } else {
                            renderPrinterStats(p.id, data, printing);
                        }
                    })
                    .catch(() => {
                        setPill(p.id, 'offline', 'OFFLINE');
                        renderCompactMeters(p.id, []);
                        if (statsEl) statsEl.innerHTML = '';
                    });
            });
        };

        updateCards(); // Run immediately
        mainTableInterval = setInterval(updateCards, 5000); // Check every 5 seconds
    }

    // Modal Handling
    document.getElementById('btn-add-printer').addEventListener('click', () => {
        form.reset();
        document.getElementById('printer-id').value = '';
        document.getElementById('printer-enabled').value = 'true';
        document.getElementById('modal-title').textContent = 'Add Printer';
        
        document.getElementById('threshold-spaghetti').value = 0.60;
        document.getElementById('val-spaghetti').textContent = '0.60';
        document.getElementById('threshold-stringing').value = 0.70;
        document.getElementById('val-stringing').textContent = '0.70';
        document.getElementById('threshold-zits').value = 0.70;
        document.getElementById('val-zits').textContent = '0.70';
        
        document.getElementById('mqtt-broker').value = '';
        document.getElementById('mqtt-topic').value = '';
        document.getElementById('mqtt-client-id').value = '';
        document.getElementById('mqtt-username').value = '';
        document.getElementById('mqtt-password').value = '';
        document.getElementById('printer-webhook').value = '';
        document.getElementById('printer-webhook-telemetry').checked = false;
        document.getElementById('printer-mqtt-telemetry').checked = false;
        document.getElementById('printer-klipper-screen-telemetry').checked = false;

        switchTab('basic');
        modal.classList.remove('hidden');
    });

    // index.html has always called this from the Cancel button's inline onclick, but it was never
    // defined - the dialog only closed because of the listener below, while the inline handler
    // threw a ReferenceError into the console on every click.
    window.closePrinterModal = function() {
        modal.classList.add('hidden');
    };

    document.getElementById('btn-cancel').addEventListener('click', () => {
        modal.classList.add('hidden');
    });

    // Edit Printer
    window.editPrinter = function(id) {
        const p = findPrinter(id);
        if (!p) return;

        document.getElementById('printer-id').value = p.id;
        document.getElementById('printer-name').value = p.name || '';
        document.getElementById('printer-hostname').value = p.hostname || '';
        document.getElementById('printer-apikey').value = p.apiKey || '';
        document.getElementById('printer-webcam').value = p.webcamUrl || '';
        document.getElementById('printer-webhook').value = p.webhookUrl || '';
        document.getElementById('printer-webhook-telemetry').checked = !!p.webhookTelemetryEnabled;
        document.getElementById('printer-enabled').value = p.enabled ? 'true' : 'false';

        document.getElementById('threshold-spaghetti').value = p.thresholdSpaghetti || 0.60;
        document.getElementById('val-spaghetti').textContent = (p.thresholdSpaghetti || 0.60).toFixed(2);

        document.getElementById('threshold-stringing').value = p.thresholdStringing || 0.70;
        document.getElementById('val-stringing').textContent = (p.thresholdStringing || 0.70).toFixed(2);

        document.getElementById('threshold-zits').value = p.thresholdZits || 0.70;
        document.getElementById('val-zits').textContent = (p.thresholdZits || 0.70).toFixed(2);

        document.getElementById('detect-spaghetti').checked = p.detectSpaghetti !== false;
        document.getElementById('detect-stringing').checked = p.detectStringing !== false;
        document.getElementById('detect-zits').checked = p.detectZits !== false;

        document.getElementById('mqtt-broker').value = p.mqttBroker || '';
        document.getElementById('mqtt-topic').value = p.mqttTopic || '';
        document.getElementById('mqtt-client-id').value = p.mqttClientId || '';
        document.getElementById('mqtt-username').value = p.mqttUsername || '';
        document.getElementById('mqtt-password').value = p.mqttPassword || '';
        document.getElementById('printer-mqtt-telemetry').checked = !!p.mqttTelemetryEnabled;
        document.getElementById('printer-klipper-screen-telemetry').checked = !!p.klipperScreenTelemetryEnabled;
        document.getElementById('printer-ai-model').value = p.aiModel || 'INBUILT';

        document.getElementById('modal-title').textContent = 'Edit Printer';
        switchTab('basic');
        modal.classList.remove('hidden');
    };

    // Delete Printer
    window.deletePrinter = function(id) {
        if (confirm('Are you sure you want to delete this printer?')) {
            fetch(`/api/printers/${id}`, { method: 'DELETE' })
                .then(() => loadPrinters())
                .catch(err => alert('Failed to delete: ' + err));
        }
    };

    // Save Printer
    form.addEventListener('submit', (e) => {
        e.preventDefault();
        
        const printer = {
            id: document.getElementById('printer-id').value,
            name: document.getElementById('printer-name').value,
            hostname: document.getElementById('printer-hostname').value,
            apiKey: document.getElementById('printer-apikey').value,
            webcamUrl: document.getElementById('printer-webcam').value,
            webhookUrl: document.getElementById('printer-webhook').value,
            webhookTelemetryEnabled: document.getElementById('printer-webhook-telemetry').checked,
            enabled: document.getElementById('printer-enabled').value === 'true',
            thresholdSpaghetti: parseFloat(document.getElementById('threshold-spaghetti').value),
            thresholdStringing: parseFloat(document.getElementById('threshold-stringing').value),
            thresholdZits: parseFloat(document.getElementById('threshold-zits').value),
            mqttBroker: document.getElementById('mqtt-broker').value,
            mqttTopic: document.getElementById('mqtt-topic').value,
            mqttClientId: document.getElementById('mqtt-client-id').value,
            mqttUsername: document.getElementById('mqtt-username').value,
            mqttPassword: document.getElementById('mqtt-password').value,
            mqttTelemetryEnabled: document.getElementById('printer-mqtt-telemetry').checked,
            klipperScreenTelemetryEnabled: document.getElementById('printer-klipper-screen-telemetry').checked,
            aiModel: document.getElementById('printer-ai-model').value
        };
        
        printer.detectSpaghetti = document.getElementById('detect-spaghetti').checked;
        printer.detectStringing = document.getElementById('detect-stringing').checked;
        printer.detectZits = document.getElementById('detect-zits').checked;

        const isNew = !printer.id;
        const method = isNew ? 'POST' : 'PUT';
        const url = isNew ? '/api/printers' : `/api/printers/${printer.id}`;

        fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(printer)
        })
        .then(r => {
            if (r.ok) {
                modal.classList.add('hidden');
                loadPrinters();
            } else {
                alert('Failed to save printer');
            }
        })
        .catch(err => alert('Error: ' + err));
    });
    
    // Tab Switching
    window.switchTab = function(tabName) {
        ['basic', 'ai', 'webhook', 'mqtt'].forEach(t => {
            document.getElementById('tab-btn-' + t).classList.remove('active');
            document.getElementById('printer-tab-' + t).classList.remove('active');
        });
        
        document.getElementById('tab-btn-' + tabName).classList.add('active');
        document.getElementById('printer-tab-' + tabName).classList.add('active');
        
        const btnTestAlert = document.getElementById('btn-test-alert');
        if (btnTestAlert) {
            btnTestAlert.style.display = (tabName === 'webhook' || tabName === 'mqtt') ? 'inline-block' : 'none';
        }
    };

    // --- Dashboard Logic ---
    const dashboardModal = document.getElementById('dashboard-modal');
    let telemetryInterval = null;
    let currentDashboardPrinterId = null;

    // State for live charts
    let liveChartSpaghetti = null;
    let liveChartStringing = null;
    let liveChartZits = null;
    
    window.openDashboard = function(id) {
        const printer = findPrinter(id);
        if (!printer) return;
        
        currentDashboardPrinterId = printer.id;
        
        document.getElementById('dashboard-title').textContent = printer.name;
        document.getElementById('dashboard-modal').classList.remove('hidden');
        const camImg = document.getElementById('dashboard-cam');
        if (printer.webcamUrl) {
            camImg.src = printer.webcamUrl;
        } else {
            camImg.src = '';
        }

        dashboardModal.classList.remove('hidden');

        // Initial fetch
        initLiveCharts();
        fetchTelemetry(printer);
        
        // Setup polling
        telemetryInterval = setInterval(() => {
            fetchTelemetry(printer);
        }, 2500);
    };

    document.getElementById('btn-close-dashboard').addEventListener('click', () => {
        if (isLivePage()) {
            // In page mode there is nothing behind the view to return to; drop the route instead.
            location.hash = '';
            location.reload();
            return;
        }
        dashboardModal.classList.add('hidden');
        if (telemetryInterval) {
            clearInterval(telemetryInterval);
            telemetryInterval = null;
        }
        // Stop camera stream downloading in background
        document.getElementById('dashboard-cam').src = '';
    });

    /* ── Live View as a page ────────────────────────────────────────────────
       A monitoring view is something you leave open on a second screen, which a
       modal over the printer list cannot be. #live/<printerId> renders the same
       markup full-width with the list hidden - no router, no second HTML file. */
    function isLivePage() {
        return location.hash.startsWith('#live/');
    }

    document.getElementById('btn-popout-dashboard').addEventListener('click', () => {
        if (!currentDashboardPrinterId) return;
        window.open(`${location.pathname}#live/${currentDashboardPrinterId}`, '_blank');
    });

    function enterLivePage(printerId) {
        document.body.classList.add('live-page');
        document.querySelector('main > section.panel').style.display = 'none';
        openDashboard(printerId);
        document.getElementById('btn-popout-dashboard').style.display = 'none';
        document.getElementById('btn-close-dashboard').textContent = 'Back to printers';
    }

    function formatDuration(seconds) {
        if (!seconds || seconds <= 0) return '00:00:00';
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        return [h, m, s].map(v => v < 10 ? '0' + v : v).join(':');
    }

    function fetchTelemetry(printer) {
        fetch(`/api/printers/${printer.id}/telemetry`)
            .then(r => r.json())
            .then(data => {
                document.getElementById('tel-klipper-state').textContent = 
                    data.klipperMessage ? data.klipperMessage : data.klipperState;
                
                document.getElementById('tel-print-state').textContent = data.printState || '-';
                
                document.getElementById('tel-extruder').textContent = 
                    `${data.extruderTemp.toFixed(1)} / ${data.extruderTarget.toFixed(1)} °C`;
                
                document.getElementById('tel-bed').textContent = 
                    `${data.bedTemp.toFixed(1)} / ${data.bedTarget.toFixed(1)} °C`;
                
                document.getElementById('tel-pos').textContent = 
                    `X: ${data.x.toFixed(1)} Y: ${data.y.toFixed(1)} Z: ${data.z.toFixed(1)}`;
                
                document.getElementById('tel-fan').textContent = 
                    `${data.fanSpeed.toFixed(0)} %`;
                
                document.getElementById('tel-progress').textContent = 
                    `${data.progress.toFixed(1)} %`;
                
                document.getElementById('tel-speed').textContent = 
                    `${data.printSpeed.toFixed(1)} mm/s`;

                document.getElementById('tel-extrusion').textContent = 
                    `${data.filamentUsed.toFixed(1)} mm`;
                    
                document.getElementById('tel-time').textContent = formatDuration(data.printDuration);
                
                document.getElementById('tel-file').textContent = data.filename || '-';
                
                // Update AI values text
                updateAiText('spaghetti', data.aiSpaghettiConf || 0, printer.thresholdSpaghetti || 0.60);
                updateAiText('stringing', data.aiStringingConf || 0, printer.thresholdStringing || 0.70);
                updateAiText('zits', data.aiZitsConf || 0, printer.thresholdZits || 0.70);

                renderAiMeters(data.aiStatus);
                
                // Update live charts
                updateLiveCharts(data);
                
                // Update Active Model
                const modelEl = document.getElementById('tel-active-model');
                if (modelEl) {
                    modelEl.textContent = data.activeModelName || 'INBUILT';
                }
            })
            .catch(err => console.error("Telemetry fetch error", err));
    }
    
    // What the confirmation level is doing, and why. Wording differs per class on purpose:
    // only spaghetti pauses the print, so only spaghetti may promise a pause.
    function meterNote(s) {
        switch (s.state) {
            case 'SCENERY':
                return { text: 'at scenery level', cls: 'scenery' };
            case 'IMMINENT':
                return {
                    text: (s.type === 'spaghetti' ? 'pausing' : 'reporting') + ' in ~' + s.secondsToAlarm + ' s',
                    cls: 'imminent'
                };
            case 'SUPPRESSED':
                return { text: 'building, slowed', cls: '' };
            case 'BUILDING':
                return { text: 'building', cls: '' };
            default:
                return { text: 'idle', cls: '' };
        }
    }

    function renderAiMeters(statuses) {
        const host = document.getElementById('ai-meters');
        if (!host) return;

        if (!statuses || statuses.length === 0) {
            host.innerHTML = '<p style="font-size: 0.85rem; color: #94a3b8; margin: 0;">No detection classes enabled for this printer.</p>';
            return;
        }

        host.innerHTML = statuses.map(s => {
            const pct = Math.max(0, Math.min(100, (s.level / s.alarmAt) * 100));
            const note = meterNote(s);
            const name = s.type.charAt(0).toUpperCase() + s.type.slice(1);
            const raw = (s.confidence * 100).toFixed(0) + ' %';
            const ref = s.reference === null || s.reference === undefined
                ? 'no baseline yet'
                : 'baseline ' + (s.reference * 100).toFixed(0) + ' %';
            const tipParts = ['model score ' + raw, ref, 'threshold ' + (s.threshold * 100).toFixed(0) + ' %'];
            if (s.suppression < 1) tipParts.push('suppressors: ' + (s.rules || '-'));

            return `
                <div class="ai-meter" title="${tipParts.join(' · ').replace(/"/g, '&quot;')}">
                    <span class="ai-meter-name">${name}</span>
                    <span class="ai-meter-track"><span class="ai-meter-fill ${s.type}" style="width: ${pct}%"></span></span>
                    <span class="ai-meter-val">${s.level.toFixed(1)} / ${s.alarmAt.toFixed(0)}</span>
                    <span class="ai-meter-note ${note.cls}">${note.text} <span class="ai-meter-raw">(${raw})</span></span>
                </div>
            `;
        }).join('');
    }

    function updateAiText(type, conf, threshold) {
        const pct = (conf * 100).toFixed(2);
        const val = document.getElementById('ai-val-' + type);
        
        val.textContent = pct + ' %';
        
        if (conf >= threshold) {
            val.style.color = '#F44336';
            val.style.fontWeight = 'bold';
        } else {
            val.style.color = '#555';
            val.style.fontWeight = 'bold';
        }
    }

    function initLiveCharts() {
        if (liveChartSpaghetti) liveChartSpaghetti.destroy();
        if (liveChartStringing) liveChartStringing.destroy();
        if (liveChartZits) liveChartZits.destroy();

        const commonOptions = {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { display: false },
                y: { min: 0, max: 100, display: true, position: 'right' }
            }
        };

        const createChart = (ctxId, color, bg) => {
            return new Chart(document.getElementById(ctxId).getContext('2d'), {
                type: 'line',
                data: { labels: [], datasets: [{ data: [], borderColor: color, backgroundColor: bg, fill: true, tension: 0.2, pointRadius: 0 }] },
                options: commonOptions
            });
        };

        liveChartSpaghetti = createChart('chart-spaghetti', '#FF9800', 'rgba(255, 152, 0, 0.2)');
        liveChartStringing = createChart('chart-stringing', '#9C27B0', 'rgba(156, 39, 176, 0.2)');
        liveChartZits = createChart('chart-zits', '#4CAF50', 'rgba(76, 175, 80, 0.2)');
        
        // Try fetching history to pre-fill the charts
        if (currentDashboardPrinterId) {
            fetch(`/api/printers/${currentDashboardPrinterId}/history/telemetry?limit=30`)
                .then(r => r.json())
                .then(historyData => {
                    historyData.reverse(); // oldest first
                    historyData.forEach(d => {
                        const time = new Date(d.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit', second:'2-digit'});
                        appendDataToChart(liveChartSpaghetti, time, (d.confSpaghetti || 0) * 100);
                        appendDataToChart(liveChartStringing, time, (d.confStringing || 0) * 100);
                        appendDataToChart(liveChartZits, time, (d.confZits || 0) * 100);
                    });
                })
                .catch(err => console.log('Could not prefill live charts', err));
        }
    }

    function appendDataToChart(chart, label, value) {
        if (!chart) return;
        chart.data.labels.push(label);
        chart.data.datasets[0].data.push(value);
        if (chart.data.labels.length > 30) {
            chart.data.labels.shift();
            chart.data.datasets[0].data.shift();
        }
        chart.update();
    }

    function updateLiveCharts(data) {
        const time = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit', second:'2-digit'});
        appendDataToChart(liveChartSpaghetti, time, (data.aiSpaghettiConf || 0) * 100);
        appendDataToChart(liveChartStringing, time, (data.aiStringingConf || 0) * 100);
        appendDataToChart(liveChartZits, time, (data.aiZitsConf || 0) * 100);
    }

    // --- Profile Logic ---
    window.switchProfTab = function(tabName) {
        ['prof-basic', 'prof-retention', 'prof-ai'].forEach(t => {
            document.getElementById('tab-btn-' + t).classList.remove('active');
            document.getElementById('prof-tab-' + t).classList.remove('active');
        });
        
        document.getElementById('tab-btn-' + tabName).classList.add('active');
        document.getElementById('prof-tab-' + tabName).classList.add('active');
    };

    window.openProfileModal = function() {
        // Clear password field
        document.getElementById('prof-password').value = '';
        switchProfTab('prof-basic');
        profileModal.classList.remove('hidden');
    };

    window.closeProfileModal = function() {
        profileModal.classList.add('hidden');
    };

    profileForm.addEventListener('submit', (e) => {
        e.preventDefault();
        
        const profile = {
            displayName: document.getElementById('prof-display-name').value,
            username: document.getElementById('prof-username').value,
            password: document.getElementById('prof-password').value,
            authDisabled: document.getElementById('prof-auth-disabled').checked,
            retentionPrintCount: parseInt(document.getElementById('prof-ret-print-count').value, 10),
            fusionMode: document.getElementById('prof-fusion-mode').value
        };

        fetch('/api/profile', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(profile)
        })
        .then(r => {
            if (r.ok) {
                closeProfileModal();
                loadProfile();
                
                if (profile.password) {
                    alert('Password changed. Your browser will ask you to login again.');
                    // Force a re-auth by calling a protected endpoint with dummy creds
                    // or simply let the next fetch naturally fail and prompt (since browser caches auth).
                    // Best way to clear browser basic auth is to send a 401 via dummy call:
                    fetch('/api/profile', {
                        headers: { 'Authorization': 'Basic ' + btoa('logout:logout') }
                    }).then(() => window.location.reload());
                } else {
                    alert('Profile saved successfully.');
                }
            } else {
                alert('Failed to save profile.');
            }
        })
        .catch(err => alert('Error: ' + err));
    });

    // --- History Logic ---
    const historyModal = document.getElementById('history-modal');
    let currentHistoryPrinterId = null;
    let historyChart = null;
    let analyticsInterval = null;

    window.switchHistTab = function(tabName) {
        if (analyticsInterval) {
            clearInterval(analyticsInterval);
            analyticsInterval = null;
        }

        ['hist-jobs', 'hist-alarms', 'hist-analytics', 'hist-snapshots'].forEach(t => {
            document.getElementById('tab-btn-' + t).classList.remove('active');
            document.getElementById('hist-tab-' + t).classList.remove('active');
        });
        
        document.getElementById('tab-btn-' + tabName).classList.add('active');
        document.getElementById('hist-tab-' + tabName).classList.add('active');
        
        if (tabName === 'hist-analytics' && currentHistoryPrinterId) {
            loadHistoryTelemetry();
            analyticsInterval = setInterval(loadHistoryTelemetry, 10000);
        }
    };

    window.testNotifications = function() {
        const printerData = {
            id: document.getElementById('printer-id').value,
            name: document.getElementById('printer-name').value,
            hostname: document.getElementById('printer-hostname').value,
            apiKey: document.getElementById('printer-apikey').value,
            webcamUrl: document.getElementById('printer-webcam').value,
            webhookUrl: document.getElementById('printer-webhook').value,
            webhookTelemetryEnabled: document.getElementById('printer-webhook-telemetry').checked,
            enabled: document.getElementById('printer-enabled').value === 'true',
            thresholdSpaghetti: parseFloat(document.getElementById('threshold-spaghetti').value),
            thresholdStringing: parseFloat(document.getElementById('threshold-stringing').value),
            thresholdZits: parseFloat(document.getElementById('threshold-zits').value),
            mqttBroker: document.getElementById('mqtt-broker').value,
            mqttTopic: document.getElementById('mqtt-topic').value,
            mqttClientId: document.getElementById('mqtt-client-id').value,
            mqttUsername: document.getElementById('mqtt-username').value,
            mqttPassword: document.getElementById('mqtt-password').value,
            mqttTelemetryEnabled: document.getElementById('printer-mqtt-telemetry').checked
        };

        const btn = document.getElementById('btn-test-alert');
        btn.textContent = 'Testing...';
        btn.disabled = true;

        fetch('/api/test-alert', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(printerData)
        })
        .then(response => response.text().then(text => ({status: response.status, text: text})))
        .then(res => {
            if (res.status === 200) {
                alert('Test alert fired successfully! Check your Home Assistant or Node-RED.');
            } else {
                alert('Failed to fire test alert: ' + res.text);
            }
        })
        .catch(err => {
            alert('Error during test: ' + err);
        })
        .finally(() => {
            btn.textContent = 'Test Notifications';
            btn.disabled = false;
        });
    };

    window.openHistory = function(printerId, printerName) {
        currentHistoryPrinterId = printerId;
        document.getElementById('history-title').textContent = printerName;
        switchHistTab('hist-jobs');
        historyModal.classList.remove('hidden');
        
        loadHistoryJobs();
        loadHistoryAlarms();
    };

    document.getElementById('btn-close-history').addEventListener('click', () => {
        if (analyticsInterval) {
            clearInterval(analyticsInterval);
            analyticsInterval = null;
        }
        historyModal.classList.add('hidden');
        if (historyChart) {
            historyChart.destroy();
            historyChart = null;
        }
    });

    function loadHistoryJobs() {
        const tbody = document.getElementById('history-jobs-tbody');
        tbody.innerHTML = '<tr><td colspan="5">Loading...</td></tr>';
        
        fetch(`/api/printers/${currentHistoryPrinterId}/history/jobs`)
            .then(r => r.json())
            .then(jobs => {
                tbody.innerHTML = '';
                if (jobs.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5">No print jobs found.</td></tr>';
                    return;
                }
                
                jobs.forEach(job => {
                    const tr = document.createElement('tr');
                    
                    const startDate = new Date(job.startTime).toLocaleString();
                    const dur = formatDuration(job.durationSeconds);
                    const fil = job.extrudedFilament ? job.extrudedFilament.toFixed(1) : '0.0';
                    
                    tr.innerHTML = `
                        <td>${startDate}</td>
                        <td style="word-break: break-all;">${job.filename || 'Unknown'}</td>
                        <td>${dur}</td>
                        <td>${job.status}</td>
                        <td>${fil}</td>
                        <td><button class="btn" onclick="openSnapshots('${job.id}', '${job.filename || 'job'}')">Snapshots</button></td>
                    `;
                    tbody.appendChild(tr);
                });
            })
            .catch(err => {
                tbody.innerHTML = '<tr><td colspan="5">Error loading jobs.</td></tr>';
            });
    }

    // --- Snapshots Logic ---
    let currentSnapshots = [];
    let currentSnapshotIndex = 0;
    let timelapseInterval = null;

    window.openSnapshots = function(jobId, filename) {
        document.getElementById('tab-btn-hist-snapshots').style.display = 'inline-block';
        document.getElementById('snapshots-job-title').textContent = filename;
        switchHistTab('hist-snapshots');
        
        const img = document.getElementById('snapshots-img');
        const emptyMsg = document.getElementById('snapshots-empty');
        const counter = document.getElementById('snapshots-counter');
        const slider = document.getElementById('snapshots-slider');
        
        img.style.display = 'none';
        emptyMsg.style.display = 'block';
        emptyMsg.textContent = 'Loading snapshots...';
        counter.style.display = 'none';
        slider.style.display = 'none';
        
        if (timelapseInterval) {
            clearInterval(timelapseInterval);
            timelapseInterval = null;
        }
        
        fetch(`/api/printers/${currentHistoryPrinterId}/history/jobs/${jobId}/snapshots`)
            .then(r => r.json())
            .then(files => {
                currentSnapshots = files.map(f => `/api/printers/${currentHistoryPrinterId}/history/jobs/${jobId}/snapshots/${f}`);
                if (currentSnapshots.length > 0) {
                    emptyMsg.style.display = 'none';
                    img.style.display = 'block';
                    counter.style.display = 'block';
                    slider.style.display = 'block';
                    slider.max = currentSnapshots.length - 1;
                    
                    showSnapshot(0);
                } else {
                    emptyMsg.style.display = 'block';
                    emptyMsg.textContent = 'No snapshots available for this print job.';
                }
            })
            .catch(err => {
                emptyMsg.style.display = 'block';
                emptyMsg.textContent = 'Error loading snapshots.';
            });
    };

    function showSnapshot(index) {
        if (index < 0 || index >= currentSnapshots.length) return;
        currentSnapshotIndex = index;
        const img = document.getElementById('snapshots-img');
        img.src = currentSnapshots[index];
        document.getElementById('snapshots-counter').textContent = `${index + 1} / ${currentSnapshots.length}`;
        document.getElementById('snapshots-slider').value = index;
    }

    document.getElementById('snapshots-slider').addEventListener('input', (e) => {
        if (timelapseInterval) {
            clearInterval(timelapseInterval);
            timelapseInterval = null;
        }
        showSnapshot(parseInt(e.target.value));
    });

    window.playSnapshots = function() {
        if (currentSnapshots.length === 0) return;
        
        if (timelapseInterval) {
            clearInterval(timelapseInterval);
            timelapseInterval = null;
            return;
        }
        
        if (currentSnapshotIndex >= currentSnapshots.length - 1) {
            currentSnapshotIndex = 0;
        }
        
        timelapseInterval = setInterval(() => {
            showSnapshot(currentSnapshotIndex + 1);
            if (currentSnapshotIndex >= currentSnapshots.length - 1) {
                clearInterval(timelapseInterval);
                timelapseInterval = null;
            }
        }, 100); // 10 fps
    };

    // Operator verdict on an incident. These labels are the only ground truth this project has,
    // and they are what a future model refit will be fitted to - so they are worth asking for
    // even before anything consumes them.
    function reviewMarkup(alarm) {
        if (alarm.groundTruth === 'TRUE_POSITIVE') {
            return '<span class="review-done">&#10003; confirmed a real failure</span>';
        }
        if (alarm.groundTruth === 'FALSE_POSITIVE') {
            return '<span class="review-done fp">&#10003; marked a false alarm</span>';
        }
        return `<span class="review-question">Was this a real failure?</span>
                <div class="review-btns">
                    <button class="btn yes" onclick="reviewAlarm(${alarm.id}, 'TRUE_POSITIVE')">Yes</button>
                    <button class="btn no" onclick="reviewAlarm(${alarm.id}, 'FALSE_POSITIVE')">False alarm</button>
                </div>`;
    }

    window.reviewAlarm = function(alarmId, verdict) {
        const host = document.getElementById(`review-${alarmId}`);
        if (host) host.innerHTML = '<span class="review-question">Saving&hellip;</span>';

        fetch(`/api/alarms/${alarmId}/review`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ groundTruth: verdict })
        })
        .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            if (host) host.innerHTML = reviewMarkup({ id: alarmId, groundTruth: verdict });
        })
        .catch(err => {
            if (host) {
                host.innerHTML = reviewMarkup({ id: alarmId, groundTruth: null });
                alert('Could not save the verdict: ' + err.message);
            }
        });
    };

    function loadHistoryAlarms() {
        const grid = document.getElementById('history-alarms-grid');
        const oldHint = grid.parentElement.querySelector('.review-hint');
        if (oldHint) oldHint.remove();
        grid.innerHTML = '<p>Loading...</p>';
        
        fetch(`/api/printers/${currentHistoryPrinterId}/history/alarms`)
            .then(r => r.json())
            .then(alarms => {
                grid.innerHTML = '';
                if (alarms.length === 0) {
                    grid.innerHTML = '<p>No incidents recorded.</p>';
                    return;
                }

                const unreviewed = alarms.filter(a => !a.groundTruth).length;
                const hint = document.createElement('p');
                hint.className = 'review-hint';
                hint.textContent = unreviewed > 0
                    ? `${unreviewed} incident${unreviewed === 1 ? '' : 's'} awaiting your verdict. Marking them builds the dataset a future model refit will learn from, and a reviewed incident is never deleted by retention.`
                    : 'All incidents reviewed. Reviewed incidents are kept permanently as training data.';
                grid.parentElement.insertBefore(hint, grid);

                alarms.forEach(a => {
                    const card = document.createElement('div');
                    card.style.border = '1px solid #ccc';
                    card.style.borderRadius = '4px';
                    card.style.overflow = 'hidden';
                    card.style.background = '#fff';
                    
                    const date = new Date(a.timestamp).toLocaleString();
                    const conf = (a.confidence * 100).toFixed(0) + '%';
                    // Older records predate the notify-only split and were all pauses.
                    const paused = (a.action || 'PAUSED') === 'PAUSED';
                    const badge = paused
                        ? '<span style="background:#fde2e1;color:#b42318;border:1px solid #f7c9c6;border-radius:3px;padding:1px 6px;font-size:0.7rem;font-weight:600;">PAUSED</span>'
                        : '<span style="background:#e7f1ff;color:#1c4587;border:1px solid #b6d4fe;border-radius:3px;padding:1px 6px;font-size:0.7rem;font-weight:600;">NOTIFIED</span>';

                    card.innerHTML = `
                        <div style="height: 150px; background: #eee; display: flex; align-items: center; justify-content: center; overflow: hidden;">
                            <img src="/api/alarms/${a.id}/image" style="width: 100%; height: 100%; object-fit: cover;" alt="Alarm Image" onerror="this.style.display='none'">
                        </div>
                        <div style="padding: 10px;">
                            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 5px;">
                                <span style="font-weight: bold;">${a.triggerType.toUpperCase()} (${conf})</span>
                                ${badge}
                            </div>
                            <div style="font-size: 0.8rem; color: #666; margin-bottom: 5px;">${date}</div>
                            <div style="font-size: 0.8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${a.filename || ''}">${a.filename || 'Unknown file'}</div>
                            <div class="review-ask" id="review-${a.id}">${reviewMarkup(a)}</div>
                        </div>
                    `;
                    grid.appendChild(card);
                });
            })
            .catch(err => {
                grid.innerHTML = '<p>Error loading alarms.</p>';
            });
    }

    document.getElementById('history-chart-limit').addEventListener('change', () => {
        if (!currentHistoryPrinterId) return;
        loadHistoryTelemetry();
    });

    function loadHistoryTelemetry() {
        const limit = document.getElementById('history-chart-limit').value;
        Promise.all([
            fetch(`/api/printers/${currentHistoryPrinterId}/history/telemetry?limit=${limit}`).then(r => r.json()),
            fetch(`/api/printers/${currentHistoryPrinterId}/history/alarms?limit=200`).then(r => r.json())
        ])
        .then(([rows, alarms]) => {
            // Reverse data so it is chronological (oldest to newest)
            rows.reverse();
            const events = alarms.map(a => ({
                ts: new Date(a.timestamp).getTime(),
                type: (a.triggerType || '').toLowerCase(),
                action: a.action || 'PAUSED'
            }));
            renderFusionChart(rows, events);
            renderTemperatureChart(rows);
        })
        .catch(err => console.error('Failed to load telemetry', err));
    }

    function renderFusionChart(rows, events) {
        // The Analytics tab reloads every 10 s. Redrawing under the cursor would wipe the
        // crosshair and tooltip mid-read, so a pointer over the chart defers the refresh.
        if (fusionHovered) return;

        const printer = findPrinter(currentHistoryPrinterId) || {};
        const thresholds = {
            spaghetti: printer.thresholdSpaghetti || 0.60,
            stringing: printer.thresholdStringing || 0.70,
            zits: printer.thresholdZits || 0.70
        };

        const result = window.YoloFusionChart.render(
            document.getElementById('fusion-chart'),
            document.getElementById('fusion-tip'),
            rows, events, thresholds);

        fusionRedraw = result.redraw;
        renderVerdict(result, thresholds);
        renderFusionTable(result.rows);
    }

    /* The number shadow mode exists to produce: how often each decision path would have acted.
       Replayed client-side from the stored rows, so it mirrors FusionEngine rather than
       recording it - hence the wording "estimated from stored data". */
    function renderVerdict(result, thresholds) {
        const host = document.getElementById('fusion-verdict');
        const s = result.summary;
        const suppressed = Math.max(0, s.legacy - s.fused);

        const rules = Object.entries(s.byRule)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 4)
            .map(([name, n]) => `${name} <b>${n}</b>`)
            .join(' &middot; ');

        const demo = result.demo
            ? '<span class="demo-badge">DEMO DATA</span>'
            : '';
        const note = result.demo
            ? '<span class="fv-tally">No fusion data recorded yet &mdash; showing a sample so the view can be read. Real curves appear after a few days in shadow mode.</span>'
            : `<span class="fv-tally">${suppressed} frame-level suppressions &mdash; ${rules || 'none'}</span>`;

        host.innerHTML = `
            ${demo}
            <span class="fv-main">Old score would have acted <b class="fv-old">${s.legacy}&times;</b>,
                  fusion <b class="fv-new">${s.fused}&times;</b></span>
            ${note}
            <span class="fv-tally" style="opacity:.75">estimated from stored data</span>
        `;
    }

    function renderFusionTable(rows) {
        const body = document.getElementById('fusion-table-body');
        const f = v => (v == null ? '&mdash;' : (v * 100).toFixed(0) + ' %');
        const step = Math.max(1, Math.floor(rows.length / 40));
        body.innerHTML = rows.filter((_, i) => i % step === 0).map(r => `
            <tr>
                <td>${new Date(r.timestamp).toLocaleTimeString()}</td>
                <td>${f(r.confSpaghetti)}</td><td>${f(r.refSpaghetti)}</td>
                <td>${f(r.confStringing)}</td><td>${f(r.refStringing)}</td>
                <td>${f(r.confZits)}</td><td>${f(r.refZits)}</td>
                <td>${r.fusionRules || '&mdash;'}</td>
            </tr>
        `).join('');
    }

    /* Temperatures used to share the fusion canvas on a second y-axis. Two unrelated scales in
       one frame invite reading crossings that mean nothing, so they get their own chart, off by
       default because on a healthy print both lines are flat. */
    function renderTemperatureChart(data) {
        const panel = document.getElementById('temperature-panel');
        if (panel.style.display === 'none') return;

        const ctx = document.getElementById('history-chart').getContext('2d');
        if (historyChart) historyChart.destroy();

        historyChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: data.map(d => new Date(d.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })),
                datasets: [
                    {
                        label: 'Extruder Temp (\u00b0C)',
                        data: data.map(d => d.extruderTemp),
                        borderColor: '#F44336',
                        backgroundColor: '#F44336',
                        tension: 0.1,
                        pointRadius: 0
                    },
                    {
                        label: 'Bed Temp (\u00b0C)',
                        data: data.map(d => d.bedTemp),
                        borderColor: '#2196F3',
                        backgroundColor: '#2196F3',
                        tension: 0.1,
                        pointRadius: 0
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: {
                    x: { ticks: { maxTicksLimit: 20 } },
                    y: { title: { display: true, text: 'Temperature (\u00b0C)' }, min: 0 }
                }
            }
        });
    }

    document.getElementById('show-temperatures').addEventListener('change', (e) => {
        document.getElementById('temperature-panel').style.display = e.target.checked ? 'block' : 'none';
        if (e.target.checked && currentHistoryPrinterId) loadHistoryTelemetry();
    });

    // Canvas has to be told to repaint; CSS custom properties do not reach into it.
    let fusionRedraw = null;
    let fusionHovered = false;
    (() => {
        const shell = document.querySelector('.fusion-chart-shell');
        if (!shell) return;
        shell.addEventListener('pointerenter', () => { fusionHovered = true; });
        shell.addEventListener('pointerleave', () => { fusionHovered = false; });
    })();

    window.addEventListener('resize', () => { if (fusionRedraw) fusionRedraw(); });
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)')
              .addEventListener('change', () => { if (fusionRedraw) fusionRedraw(); });
    }

});
