// Map State
let map;
let markers = {}; // Store markers by ID (t-{id} or p-{id})
let simulationState = null;
let isNightMode = false;

// Custom Icons
const taxiIcon = L.divIcon({
    className: 'custom-taxi-icon',
    html: '<div class="taxi-marker"><i class="fa-solid fa-taxi"></i></div>',
    iconSize: [32, 32],
    iconAnchor: [16, 16]
});

const passengerIconMale = L.divIcon({
    className: 'custom-passenger-icon',
    html: '<div class="passenger-marker male"><i class="fa-solid fa-user"></i></div>',
    iconSize: [24, 24],
    iconAnchor: [12, 12]
});

const passengerIconFemale = L.divIcon({
    className: 'custom-passenger-icon',
    html: '<div class="passenger-marker female"><i class="fa-solid fa-user"></i></div>',
    iconSize: [24, 24],
    iconAnchor: [12, 12]
});

const destinationIcon = L.divIcon({
    className: 'custom-dest-icon',
    html: '<div class="dest-marker"><i class="fa-solid fa-flag-checkered" style="color:red; font-size:24px;"></i></div>',
    iconSize: [24, 24],
    iconAnchor: [2, 22]
});

// Initialize Leaflet Map
function initMap() {
    // Center on Islamabad, Pakistan
    map = L.map('city-map').setView([33.7077, 73.0501], 14);

    // CartoDB Dark Matter for Night Mode, Positron for Day
    const tileUrl = isNightMode
        ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
        : 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';

    L.tileLayer(tileUrl, {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 19
    }).addTo(map);
}

// Coordinate Mapper (Backend 0-1000 Grid -> Lat/Lon)
function mapToLatLon(x, y) {
    // Map 1000x800 grid to Islamabad (F-6, F-7, G-6, G-7 area)
    // Top Left (North-West): 33.7460, 73.0360
    // Bottom Right (South-East): 33.6840, 73.0800
    const minLat = 33.6840;
    const maxLat = 33.7460;
    const minLon = 73.0360;
    const maxLon = 73.0800;

    // Invert Y for Latitude (0 is top in graphics, but maxLat is top in map)
    const lat = maxLat - (y / 800) * (maxLat - minLat);
    const lon = minLon + (x / 1200) * (maxLon - minLon);

    return [lat, lon];
}

// --- API CALLS ---
async function fetchState() {
    try {
        const res = await fetch('/api/state');
        if (res.ok) {
            simulationState = await res.json();
            updateUI();
            updateMapMarkers();
        }
    } catch (e) {
        console.error("Connection error", e);
    }
}

async function startSim() {
    const startButton = document.getElementById('start-btn');
    if (startButton) {
        startButton.disabled = true;
    }
    await fetch('/api/start', { method: 'POST' });
}
async function resetSim() {
    await fetch('/api/reset', { method: 'POST' });
    // Clear markers
    for (let id in markers) {
        map.removeLayer(markers[id]);
    }
    markers = {};
    // Re-enable Start button
    const startButton = document.getElementById('start-btn');
    if (startButton) {
        startButton.disabled = false;
    }
}
async function addTaxi() { await fetch('/api/addTaxi', { method: 'POST' }); }

function openPassengerModal() {
    const male = prompt("How many Male passengers?", "1");
    const female = prompt("How many Female passengers?", "1");
    if (male !== null && female !== null) {
        fetch('/api/addPassenger', {
            method: 'POST',
            body: JSON.stringify({ maleCount: parseInt(male), femaleCount: parseInt(female) })
        });
    }
}

// --- UI UPDATES (PHONES) ---
function updateUI() {
    if (!simulationState) return;

    document.getElementById('passenger-count').innerText = simulationState.passengers.length;
    document.getElementById('taxi-count').innerText = simulationState.taxis.length;
    document.getElementById('total-served').innerText = simulationState.totalPassengersServed;
    document.getElementById('queue-size').innerText = simulationState.queueSize;

    updateDriverPhones();
    updatePassengerPhones();
}

function updateDriverPhones() {
    const container = document.getElementById('driver-phones-container');
    const existingIds = new Set(simulationState.taxis.map(t => t.id));

    // Cleanup
    Array.from(container.children).forEach(child => {
        if (!existingIds.has(parseInt(child.dataset.id))) child.remove();
    });

    simulationState.taxis.forEach(taxi => {
        let el = container.querySelector(`[data-id="${taxi.id}"]`);
        if (!el) {
            el = createDriverPhone(taxi);
            container.appendChild(el);
        }
        updateDriverScreen(el, taxi);
    });
}

function createDriverPhone(taxi) {
    const el = document.createElement('div');
    el.className = 'phone-frame driver-app';
    el.dataset.id = taxi.id;
    el.innerHTML = `
        <div class="screen">
            <div class="app-header">
                <span><i class="fa-solid fa-bars"></i></span>
                <span>UBER DRIVER</span>
                <span><i class="fa-solid fa-user"></i></span>
            </div>
            <div class="app-body">
                <div class="profile-section" style="display:flex; align-items:center; gap:10px; padding:10px 0; border-bottom:1px solid #334155;">
                    <img src="${taxi.driverAvatarUrl}" class="avatar-img" style="width:40px; height:40px; border-radius:50%; border:2px solid #fff;">
                    <div>
                        <div style="font-weight:bold; font-size:0.9rem;">${taxi.driverName}</div>
                        <div style="font-size:0.7rem; color:#94a3b8;">4.9 ★</div>
                    </div>
                </div>
                <div class="earning-card" style="margin-top:10px;">
                    <span>TODAY'S EARNINGS</span>
                    <span class="earning-amount">Rs. 0.00</span>
                </div>
                <div style="text-align:center; margin: 10px 0;">
                    <span class="status-pill">ONLINE</span>
                </div>
                <div class="passenger-list"></div>
            </div>
        </div>`;
    return el;
}

function updateDriverScreen(el, taxi) {
    el.querySelector('.earning-amount').innerText = `Rs. ${taxi.earnings.toFixed(2)}`;
    const statusPill = el.querySelector('.status-pill');
    statusPill.className = `status-pill ${taxi.status === 'AVAILABLE' ? 'status-available' : taxi.status === 'PICKING_UP' ? 'status-warning' : 'status-busy'}`;
    statusPill.innerText = taxi.status.replace('_', ' ');

    const pList = el.querySelector('.passenger-list');
    pList.innerHTML = taxi.passengers.map(p => `
        <div class="p-item">
            <img src="${p.avatarUrl}" style="width:30px; height:30px; border-radius:50%;">
            <div>
                <div style="font-weight:bold;">${p.name}</div>
                <div style="font-size:0.7rem; color:#94a3b8;">Dest: ${p.destination}</div>
            </div>
        </div>
    `).join('') || '<div style="text-align:center; color:#64748b; font-size:0.8rem; margin-top:20px;">Searching for rides...</div>';
}

function updatePassengerPhones() {
    const container = document.getElementById('passenger-phones-container');
    const existingIds = new Set(simulationState.passengers.map(p => p.id));

    Array.from(container.children).forEach(child => {
        if (!existingIds.has(child.dataset.id)) child.remove();
    });

    simulationState.passengers.forEach(p => {
        let el = container.querySelector(`[data-id="${p.id}"]`);
        if (!el) {
            el = createPassengerPhone(p);
            container.appendChild(el);
        }
        updatePassengerScreen(el, p);
    });
}

function createPassengerPhone(p) {
    const el = document.createElement('div');
    el.className = 'phone-frame passenger-app';
    el.dataset.id = p.id;
    el.innerHTML = `
        <div class="screen">
            <div class="app-header" style="background:#fff; color:#333; border-bottom:1px solid #eee;">
                <span>9:41</span>
                <span><i class="fa-solid fa-signal"></i></span>
            </div>
            <div class="app-body" style="background:#fff;">
                <div style="display:flex; align-items:center; gap:15px; padding:15px 10px; border-bottom:1px solid #f0f0f0;">
                    <img src="${p.avatarUrl}" style="width:50px; height:50px; border-radius:50%;">
                    <div>
                        <div style="font-size:1.1rem; font-weight:700; color:#202124;">${p.name}</div>
                        <div style="font-size:0.85rem; color:#5f6368;">5.0 ★</div>
                    </div>
                </div>
                <div class="map-placeholder" style="margin-top:15px; border-radius:12px; border:1px solid #e0e0e0; background: url('https://maps.googleapis.com/maps/api/staticmap?center=40.7128,-74.0060&zoom=13&size=200x120&sensor=false') center/cover;"></div>
                <div class="ride-status" style="box-shadow:none; text-align:left; padding:10px 5px;">
                    <div style="margin-bottom:5px;">
                        <span style="font-size:0.75rem; color:#5f6368; font-weight:600; text-transform:uppercase;">Destination</span>
                        <div class="dest-text" style="font-size:1rem; color:#202124; margin-top:2px;">
                            <i class="fa-solid fa-location-dot" style="color:#d93025;"></i> ${p.destination}
                        </div>
                    </div>
                </div>
                <div class="action-btn" style="margin-top:auto; background:#1a73e8; color:white; padding:15px; border-radius:30px; text-align:center; font-weight:600;">Requesting</div>
            </div>
        </div>`;
    return el;
}

function updatePassengerScreen(el, p) {
    const btn = el.querySelector('.action-btn');
    if (p.status === 'IN_RIDE') {
        btn.innerText = "On Trip";
        btn.style.background = "#188038";
    } else {
        btn.innerText = "Finding Driver...";
        btn.style.background = "#ea4335";
    }
}

// --- MARKER UPDATES ---
function updateMapMarkers() {
    if (!map) return;

    // Taxis
    const taxiIds = new Set();
    simulationState.taxis.forEach(t => {
        taxiIds.add('t-' + t.id);
        const [lat, lon] = mapToLatLon(t.x, t.y);

        if (markers['t-' + t.id]) {
            // Update position (animate)
            const marker = markers['t-' + t.id];
            // Simple lerp is handled by Leaflet, just set LatLng
            marker.setLatLng([lat, lon]);
        } else {
            const m = L.marker([lat, lon], { icon: taxiIcon }).addTo(map);
            m.bindPopup(`<b>Taxi ${t.id}</b><br>${t.driverName}`);
            markers['t-' + t.id] = m;
        }

        // Show Destination Marker if ON_RIDE
        const destKey = 'd-' + t.id;
        if (t.status === 'ON_RIDE' || t.status === 'PICKING_UP') { // Show target for both pickup and dropoff
            const [dLat, dLon] = mapToLatLon(t.targetX, t.targetY);
            if (markers[destKey]) {
                markers[destKey].setLatLng([dLat, dLon]);
            } else {
                // Determine icon: If picking up, maybe show passenger location?
                // Actually, for PICKUP, the passenger marker is already there. So only show FLAG for destination (ON_RIDE).
                if (t.status === 'ON_RIDE') {
                    const dm = L.marker([dLat, dLon], { icon: destinationIcon }).addTo(map);
                    dm.bindPopup(`<b>Destination</b><br>Taxi ${t.id}`);
                    markers[destKey] = dm;
                }
            }
        } else {
            // Remove destination marker if not on ride
            if (markers[destKey]) {
                map.removeLayer(markers[destKey]);
                delete markers[destKey];
            }
        }

        // Handle Messages (Popup)
        if (t.message && t.message.length > 0) {
            markers['t-' + t.id].setPopupContent(`<b>Taxi ${t.id}</b><br>${t.driverName}<br><i>${t.message}</i>`);
        } else {
            markers['t-' + t.id].setPopupContent(`<b>Taxi ${t.id}</b><br>${t.driverName}`);
        }


    });

    // Passengers
    const passIds = new Set();
    simulationState.passengers.forEach(p => {
        if (p.status === 'IN_RIDE') return; // Don't show if picked up

        passIds.add('p-' + p.id);
        const [lat, lon] = mapToLatLon(p.x, p.y);

        if (markers['p-' + p.id]) {
            markers['p-' + p.id].setLatLng([lat, lon]);
        } else {
            const icon = p.gender === 'MALE' ? passengerIconMale : passengerIconFemale;
            const m = L.marker([lat, lon], { icon: icon }).addTo(map);
            m.bindPopup(`<b>${p.name}</b><br>Going to: ${p.destination}`);
            markers['p-' + p.id] = m;
        }
    });

    // Cleanup removed markers
    for (let id in markers) {
        if (id.startsWith('t-') && !taxiIds.has(id)) {
            map.removeLayer(markers[id]);
            delete markers[id];
        }
        if (id.startsWith('p-') && !passIds.has(id)) {
            map.removeLayer(markers[id]);
            delete markers[id];
        }
        if (id.startsWith('d-') && !taxiIds.has(id.replace('d-', 't-'))) {
            map.removeLayer(markers[id]);
            delete markers[id];
        }
    }
}

// Boot
window.onload = () => {
    initMap();
    setInterval(fetchState, 100);
};
