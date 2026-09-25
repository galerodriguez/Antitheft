// Panel web de Centinela — cliente mínimo del backend.
// Configurá estos valores:
const API_BASE = "https://api.centinela.example.com";
// Obtené el ID token con Firebase Auth en el navegador y guardalo acá:
let AUTH_TOKEN = localStorage.getItem("centinela_token") || "";

let devices = [];
let selectedId = null;
let map, marker;

function authHeaders() {
  return { "Content-Type": "application/json", Authorization: `Bearer ${AUTH_TOKEN}` };
}

async function loadDevices() {
  try {
    const res = await fetch(`${API_BASE}/devices`, { headers: authHeaders() });
    devices = await res.json();
    renderDevices();
    if (devices.length && !selectedId) selectDevice(devices[0].id);
  } catch (e) {
    document.getElementById("deviceList").innerHTML =
      '<div class="empty">No se pudo cargar. Revisá API_BASE y el token.</div>';
  }
}

function renderDevices() {
  const el = document.getElementById("deviceList");
  if (!devices.length) { el.innerHTML = '<div class="empty">Sin dispositivos registrados.</div>'; return; }
  el.innerHTML = devices.map((d) => `
    <div class="device ${d.id === selectedId ? "active" : ""}" onclick="selectDevice('${d.id}')">
      <h3>${d.name || "Dispositivo"}</h3>
      <small>${d.model || ""} · visto: ${d.lastSeen ? new Date(d.lastSeen).toLocaleString() : "—"}</small>
    </div>`).join("");
}

async function selectDevice(id) {
  selectedId = id;
  renderDevices();
  await loadLocations();
}

async function loadLocations() {
  if (!selectedId) return;
  const res = await fetch(`${API_BASE}/devices/${selectedId}/locations`, { headers: authHeaders() });
  const locs = await res.json();
  if (locs.length) {
    const { lat, lng } = locs[0];
    marker.setLatLng([lat, lng]);
    map.setView([lat, lng], 15);
  }
}

async function send(type, params = {}) {
  if (!selectedId) return alert("Elegí un dispositivo primero.");
  await fetch(`${API_BASE}/devices/${selectedId}/commands`, {
    method: "POST", headers: authHeaders(),
    body: JSON.stringify({ type, params }),
  });
  if (type === "LOCATE") setTimeout(loadLocations, 4000); // dar tiempo a que responda
}

function wipe() {
  if (!selectedId) return alert("Elegí un dispositivo primero.");
  const txt = prompt("Esto BORRARÁ el dispositivo de forma irreversible.\nEscribí BORRAR para confirmar:");
  if (txt !== "BORRAR") return;
  fetch(`${API_BASE}/devices/${selectedId}/commands`, {
    method: "POST", headers: authHeaders(),
    body: JSON.stringify({ type: "WIPE", confirm: "CONFIRM_WIPE" }),
  });
}

function initMap() {
  map = L.map("map").setView([-34.6037, -58.3816], 12); // Buenos Aires por defecto
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: "© OpenStreetMap",
  }).addTo(map);
  marker = L.marker([-34.6037, -58.3816]).addTo(map);
}

initMap();
loadDevices();
setInterval(loadLocations, 15000); // refresco de ubicación
