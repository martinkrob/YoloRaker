document.addEventListener('DOMContentLoaded', () => {
    const statusIndicator = document.getElementById('api-status');
    const tbody = document.getElementById('printers-tbody');
    const modal = document.getElementById('printer-modal');
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
                
                document.getElementById('prof-ret-telemetry-count').value = data.retentionTelemetryCount || 10000;
                document.getElementById('prof-ret-alarms-count').value = data.retentionAlarmsCount || 500;
                document.getElementById('prof-ret-jobs-count').value = data.retentionJobsCount || 1000;
            })
            .catch(err => console.error("Failed to load profile", err));
    }

    // Check API Status periodically
    function checkApiStatus() {
        fetch('/api/status')
            .then(r => {
                if (!r.ok) throw new Error("API not ok");
                return r.json();
            })
            .then(data => {
                statusIndicator.textContent = 'API OK';
                statusIndicator.className = 'status-indicator ok';
            })
            .catch(err => {
                statusIndicator.textContent = 'API ERROR';
                statusIndicator.className = 'status-indicator error';
            });
    }

    checkApiStatus();
    setInterval(checkApiStatus, 5000);

    // Load Printers
    function loadPrinters() {
        fetch('/api/printers')
            .then(r => r.json())
            .then(printers => {
                tbody.innerHTML = '';
                if (printers.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="7">No printers configured.</td></tr>';
                    return;
                }
                
                printers.forEach(p => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td>${p.name}</td>
                        <td>${p.hostname}</td>
                        <td>${p.enabled ? 'Enabled' : 'Disabled'}</td>
                        <td id="conn-${p.id}" style="font-weight: 500; color: #999;">Checking...</td>
                        <td id="state-${p.id}" style="font-weight: 500; color: #999;">-</td>
                        <td>
                            <button class="btn primary" onclick="openDashboard('${p.id}')">Live View</button>
                            <button class="btn" onclick="openHistory('${p.id}', '${p.name}')">History</button>
                            <button class="btn" onclick="editPrinter('${p.id}')">Edit</button>
                            <button class="btn" onclick="deletePrinter('${p.id}')">Delete</button>
                        </td>
                    `;
                    // Store full object for editing
                    tr.dataset.printer = JSON.stringify(p);
                    tbody.appendChild(tr);
                });
                
                startMainTablePolling(printers);
            });
    }
    
    let mainTableInterval = null;

    function startMainTablePolling(printers) {
        if (mainTableInterval) clearInterval(mainTableInterval);
        
        const updateRows = () => {
            printers.forEach(p => {
                const connEl = document.getElementById(`conn-${p.id}`);
                const stateEl = document.getElementById(`state-${p.id}`);
                
                if (!p.enabled) {
                    if(connEl) connEl.innerHTML = `<span style="color: #999;">Disabled</span>`;
                    if(stateEl) stateEl.innerHTML = `-`;
                    return;
                }
                
                fetch(`/api/printers/${p.id}/telemetry`)
                    .then(r => r.json())
                    .then(data => {
                        if (connEl) {
                            if (data.klipperState === 'error' || data.klipperState === 'shutdown' || data.klipperState === 'offline' || data.klipperMessage === 'Moonraker Unreachable') {
                                connEl.innerHTML = `<span style="color: var(--danger-color);">OFF-LINE</span>`;
                            } else {
                                connEl.innerHTML = `<span style="color: var(--primary-color);">ON-LINE</span>`;
                            }
                        }
                        if (stateEl) {
                            let s = data.printState || '-';
                            if (s === 'printing') {
                                s = `<span style="color: var(--primary-color);">Printing</span>`;
                            } else if (s.toLowerCase() === 'paused') {
                                s = `<span style="color: #f59e0b;">Paused</span>`;
                            }
                            stateEl.innerHTML = s;
                        }
                    })
                    .catch(err => {
                        if (connEl) connEl.innerHTML = `<span style="color: var(--danger-color);">OFF-LINE</span>`;
                        if (stateEl) stateEl.innerHTML = `-`;
                    });
            });
        };
        
        updateRows(); // Run immediately
        mainTableInterval = setInterval(updateRows, 5000); // Check every 5 seconds
    }

    // Modal Handling
    document.getElementById('btn-add-printer').addEventListener('click', () => {
        form.reset();
        document.getElementById('printer-id').value = '';
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

        switchTab('basic');
        modal.classList.remove('hidden');
    });

    document.getElementById('btn-cancel').addEventListener('click', () => {
        modal.classList.add('hidden');
    });

    // Edit Printer
    window.editPrinter = function(id) {
        // Find the tr element to get the stored data
        const rows = tbody.querySelectorAll('tr');
        for (let row of rows) {
            if (row.dataset.printer) {
                const p = JSON.parse(row.dataset.printer);
                if (p.id === id) {
                    document.getElementById('printer-id').value = p.id;
                    document.getElementById('printer-name').value = p.name || '';
                    document.getElementById('printer-hostname').value = p.hostname || '';
                    document.getElementById('printer-apikey').value = p.apiKey || '';
                    document.getElementById('printer-webcam').value = p.webcamUrl || '';
                    document.getElementById('printer-webhook').value = p.webhookUrl || '';
                    document.getElementById('printer-enabled').checked = p.enabled;
                    
                    document.getElementById('threshold-spaghetti').value = p.thresholdSpaghetti || 0.60;
                    document.getElementById('val-spaghetti').textContent = (p.thresholdSpaghetti || 0.60).toFixed(2);
                    
                    document.getElementById('threshold-stringing').value = p.thresholdStringing || 0.70;
                    document.getElementById('val-stringing').textContent = (p.thresholdStringing || 0.70).toFixed(2);
                    
                    document.getElementById('threshold-zits').value = p.thresholdZits || 0.70;
                    document.getElementById('val-zits').textContent = (p.thresholdZits || 0.70).toFixed(2);
                    
                    document.getElementById('mqtt-broker').value = p.mqttBroker || '';
                    document.getElementById('mqtt-topic').value = p.mqttTopic || '';
                    document.getElementById('mqtt-client-id').value = p.mqttClientId || '';
                    document.getElementById('mqtt-username').value = p.mqttUsername || '';
                    document.getElementById('mqtt-password').value = p.mqttPassword || '';

                    document.getElementById('modal-title').textContent = 'Edit Printer';
                    switchTab('basic');
                    modal.classList.remove('hidden');
                    break;
                }
            }
        }
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
            enabled: document.getElementById('printer-enabled').checked,
            thresholdSpaghetti: parseFloat(document.getElementById('threshold-spaghetti').value),
            thresholdStringing: parseFloat(document.getElementById('threshold-stringing').value),
            thresholdZits: parseFloat(document.getElementById('threshold-zits').value),
            mqttBroker: document.getElementById('mqtt-broker').value,
            mqttTopic: document.getElementById('mqtt-topic').value,
            mqttClientId: document.getElementById('mqtt-client-id').value,
            mqttUsername: document.getElementById('mqtt-username').value,
            mqttPassword: document.getElementById('mqtt-password').value
        };

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
    };

    // --- Dashboard Logic ---
    const dashboardModal = document.getElementById('dashboard-modal');
    let telemetryInterval = null;

    window.openDashboard = function(id) {
        // Find printer data
        const rows = tbody.querySelectorAll('tr');
        let printer = null;
        for (let row of rows) {
            if (row.dataset.printer) {
                const p = JSON.parse(row.dataset.printer);
                if (p.id === id) {
                    printer = p;
                    break;
                }
            }
        }
        
        if (!printer) return;
        
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
        fetchTelemetry(printer);
        
        // Setup polling
        telemetryInterval = setInterval(() => {
            fetchTelemetry(printer);
        }, 2500);
    };

    document.getElementById('btn-close-dashboard').addEventListener('click', () => {
        dashboardModal.classList.add('hidden');
        if (telemetryInterval) {
            clearInterval(telemetryInterval);
            telemetryInterval = null;
        }
        // Stop camera stream downloading in background
        document.getElementById('dashboard-cam').src = '';
    });

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
                
                // Update AI Live Bars
                updateAiBar('spaghetti', data.aiSpaghettiConf || 0, printer.thresholdSpaghetti || 0.60);
                updateAiBar('stringing', data.aiStringingConf || 0, printer.thresholdStringing || 0.70);
                updateAiBar('zits', data.aiZitsConf || 0, printer.thresholdZits || 0.70);
            })
            .catch(err => console.error("Telemetry fetch error", err));
    }
    
    function updateAiBar(type, conf, threshold) {
        const pct = Math.round(conf * 100);
        const bar = document.getElementById('ai-bar-' + type);
        const val = document.getElementById('ai-val-' + type);
        
        bar.style.width = pct + '%';
        val.textContent = pct + ' %';
        
        if (conf >= threshold) {
            bar.style.backgroundColor = '#F44336'; // Red
            val.style.color = '#F44336';
            val.style.fontWeight = 'bold';
        } else {
            bar.style.backgroundColor = '#4CAF50'; // Green
            val.style.color = '#666';
            val.style.fontWeight = 'normal';
        }
    }

    // --- Profile Logic ---
    window.switchProfTab = function(tabName) {
        ['prof-basic', 'prof-retention'].forEach(t => {
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
            retentionTelemetryCount: parseInt(document.getElementById('prof-ret-telemetry-count').value),
            retentionAlarmsCount: parseInt(document.getElementById('prof-ret-alarms-count').value),
            retentionJobsCount: parseInt(document.getElementById('prof-ret-jobs-count').value)
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

    window.switchHistTab = function(tabName) {
        ['hist-jobs', 'hist-alarms', 'hist-analytics'].forEach(t => {
            document.getElementById('tab-btn-' + t).classList.remove('active');
            document.getElementById('hist-tab-' + t).classList.remove('active');
        });
        
        document.getElementById('tab-btn-' + tabName).classList.add('active');
        document.getElementById('hist-tab-' + tabName).classList.add('active');
        
        if (tabName === 'hist-analytics' && currentHistoryPrinterId) {
            loadHistoryTelemetry();
        }
    };

    window.testNotifications = function() {
        const printerData = {
            id: document.getElementById('printer-id').value,
            name: document.getElementById('printer-name').value,
            moonrakerUrl: document.getElementById('printer-moonraker').value,
            webcamUrl: document.getElementById('printer-webcam').value,
            
            webhookUrl: document.getElementById('printer-webhook-url').value,
            
            mqttBroker: document.getElementById('printer-mqtt-broker').value,
            mqttTopic: document.getElementById('printer-mqtt-topic').value,
            mqttUsername: document.getElementById('printer-mqtt-user').value,
            mqttPassword: document.getElementById('printer-mqtt-pass').value,
            mqttClientId: document.getElementById('printer-mqtt-clientid').value
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
                
                jobs.forEach(j => {
                    const tr = document.createElement('tr');
                    const startDate = new Date(j.startTime).toLocaleString();
                    const dur = formatDuration(j.durationSeconds);
                    const fil = j.extrudedFilament ? j.extrudedFilament.toFixed(1) : '0.0';
                    tr.innerHTML = `
                        <td>${startDate}</td>
                        <td style="word-break: break-all;">${j.filename || 'Unknown'}</td>
                        <td>${dur}</td>
                        <td>${j.status}</td>
                        <td>${fil}</td>
                    `;
                    tbody.appendChild(tr);
                });
            })
            .catch(err => {
                tbody.innerHTML = '<tr><td colspan="5">Error loading jobs.</td></tr>';
            });
    }

    function loadHistoryAlarms() {
        const grid = document.getElementById('history-alarms-grid');
        grid.innerHTML = '<p>Loading...</p>';
        
        fetch(`/api/printers/${currentHistoryPrinterId}/history/alarms`)
            .then(r => r.json())
            .then(alarms => {
                grid.innerHTML = '';
                if (alarms.length === 0) {
                    grid.innerHTML = '<p>No AI alarms found.</p>';
                    return;
                }
                
                alarms.forEach(a => {
                    const card = document.createElement('div');
                    card.style.border = '1px solid #ccc';
                    card.style.borderRadius = '4px';
                    card.style.overflow = 'hidden';
                    card.style.background = '#fff';
                    
                    const date = new Date(a.timestamp).toLocaleString();
                    const conf = (a.confidence * 100).toFixed(0) + '%';
                    
                    card.innerHTML = `
                        <div style="height: 150px; background: #eee; display: flex; align-items: center; justify-content: center; overflow: hidden;">
                            <img src="/api/alarms/${a.id}/image" style="width: 100%; height: 100%; object-fit: cover;" alt="Alarm Image" onerror="this.style.display='none'">
                        </div>
                        <div style="padding: 10px;">
                            <div style="font-weight: bold; margin-bottom: 5px;">${a.triggerType.toUpperCase()} (${conf})</div>
                            <div style="font-size: 0.8rem; color: #666; margin-bottom: 5px;">${date}</div>
                            <div style="font-size: 0.8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${a.filename || ''}">${a.filename || 'Unknown file'}</div>
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
        fetch(`/api/printers/${currentHistoryPrinterId}/history/telemetry?limit=${limit}`)
            .then(r => r.json())
            .then(data => {
                // Reverse data so it is chronological (oldest to newest)
                data.reverse();
                renderChart(data);
            })
            .catch(err => console.error('Failed to load telemetry', err));
    }

    function renderChart(data) {
        const ctx = document.getElementById('history-chart').getContext('2d');
        
        if (historyChart) {
            historyChart.destroy();
        }

        const labels = data.map(d => new Date(d.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}));
        
        const extruderData = data.map(d => d.extruderTemp);
        const bedData = data.map(d => d.bedTemp);
        const spaghettiData = data.map(d => d.confSpaghetti * 100);
        const stringingData = data.map(d => d.confStringing * 100);
        const zitsData = data.map(d => d.confZits * 100);

        historyChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Extruder Temp (°C)',
                        data: extruderData,
                        borderColor: '#F44336', // Red
                        backgroundColor: '#F44336',
                        yAxisID: 'yTemp',
                        tension: 0.1,
                        pointRadius: 0
                    },
                    {
                        label: 'Bed Temp (°C)',
                        data: bedData,
                        borderColor: '#2196F3', // Blue
                        backgroundColor: '#2196F3',
                        yAxisID: 'yTemp',
                        tension: 0.1,
                        pointRadius: 0
                    },
                    {
                        label: 'Spaghetti AI (%)',
                        data: spaghettiData,
                        borderColor: '#FF9800', // Orange
                        backgroundColor: 'rgba(255, 152, 0, 0.2)',
                        yAxisID: 'yConf',
                        fill: true,
                        tension: 0.2,
                        pointRadius: 0
                    },
                    {
                        label: 'Stringing AI (%)',
                        data: stringingData,
                        borderColor: '#9C27B0', // Purple
                        backgroundColor: 'rgba(156, 39, 176, 0.2)',
                        yAxisID: 'yConf',
                        fill: true,
                        tension: 0.2,
                        pointRadius: 0
                    },
                    {
                        label: 'Zits AI (%)',
                        data: zitsData,
                        borderColor: '#4CAF50', // Green
                        backgroundColor: 'rgba(76, 175, 80, 0.2)',
                        yAxisID: 'yConf',
                        fill: true,
                        tension: 0.2,
                        pointRadius: 0
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'index',
                    intersect: false,
                },
                scales: {
                    x: {
                        ticks: {
                            maxTicksLimit: 20
                        }
                    },
                    yTemp: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        title: { display: true, text: 'Temperature (°C)' },
                        min: 0
                    },
                    yConf: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        title: { display: true, text: 'AI Confidence (%)' },
                        min: 0,
                        max: 100,
                        grid: { drawOnChartArea: false }
                    }
                }
            }
        });
    }

});
