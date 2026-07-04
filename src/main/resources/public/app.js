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
                document.getElementById('display-name-header').textContent = data.displayName;
                document.getElementById('prof-display-name').value = data.displayName;
                document.getElementById('prof-username').value = data.username;
                document.getElementById('prof-auth-disabled').checked = data.authDisabled;
            })
            .catch(err => console.error("Failed to load profile", err));
    }

    // Check API Status
    fetch('/api/status')
        .then(r => r.json())
        .then(data => {
            statusIndicator.textContent = 'API OK';
            statusIndicator.className = 'status-indicator ok';
            loadPrinters();
        })
        .catch(err => {
            statusIndicator.textContent = 'API ERROR';
            statusIndicator.className = 'status-indicator error';
            console.error('API Error:', err);
        });

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
                        <td>${p.apiKey ? '***' : '-'}</td>
                        <td>${p.webcamUrl || '-'}</td>
                        <td>${p.webhookUrl || '-'}</td>
                        <td>${p.enabled ? 'Enabled' : 'Disabled'}</td>
                        <td>
                            <button class="btn primary" onclick="openDashboard('${p.id}')">Detail</button>
                            <button class="btn" onclick="editPrinter('${p.id}')">Edit</button>
                            <button class="btn" onclick="deletePrinter('${p.id}')">Delete</button>
                        </td>
                    `;
                    // Store full object for editing
                    tr.dataset.printer = JSON.stringify(p);
                    tbody.appendChild(tr);
                });
            });
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
                    
                    document.getElementById('modal-title').textContent = 'Edit Printer';
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
            thresholdZits: parseFloat(document.getElementById('threshold-zits').value)
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
    window.openProfileModal = function() {
        // Clear password field
        document.getElementById('prof-password').value = '';
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
            authDisabled: document.getElementById('prof-auth-disabled').checked
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
});
