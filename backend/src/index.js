import express from "express";
import cors from "cors";
import { db, messaging } from "./firebase.js";
import { requireUser, requireDeviceOwner } from "./auth.js";

const app = express();
app.use(cors());
app.use(express.json());

const owner = (req, res, next) => requireDeviceOwner(db, req, res, next);

app.get("/health", (_req, res) => res.json({ ok: true }));

// --- Registro de dispositivos ---------------------------------------------
app.post("/devices", requireUser, async (req, res) => {
  const { name, model, fcmToken } = req.body;
  const ref = await db.collection("devices").add({
    ownerUid: req.uid, name, model, fcmToken,
    status: "active", createdAt: Date.now(), lastSeen: Date.now(),
  });
  res.json({ deviceId: ref.id });
});

app.get("/devices", requireUser, async (req, res) => {
  const snap = await db.collection("devices").where("ownerUid", "==", req.uid).get();
  res.json(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
});

app.post("/devices/:deviceId/fcm", requireUser, owner, async (req, res) => {
  await db.collection("devices").doc(req.params.deviceId)
    .update({ fcmToken: req.body.fcmToken, lastSeen: Date.now() });
  res.json({ ok: true });
});

// --- Envío de comandos (panel -> dispositivo vía FCM) ----------------------
const DESTRUCTIVE = new Set(["WIPE"]);

app.post("/devices/:deviceId/commands", requireUser, owner, async (req, res) => {
  const { type, params = {}, confirm } = req.body;

  // Comandos destructivos exigen confirmación explícita en el request.
  if (DESTRUCTIVE.has(type) && confirm !== "CONFIRM_WIPE")
    return res.status(400).json({ error: "confirmation_required" });

  const cmdRef = await db.collection("devices").doc(req.params.deviceId)
    .collection("commands").add({
      type, params, status: "pending", createdAt: Date.now(), byUid: req.uid,
    });

  // El push solo transporta la señal; los datos sensibles viajan luego por HTTPS.
  await messaging.send({
    token: req.device.fcmToken,
    data: { command: type, requestId: cmdRef.id, ...stringifyParams(params) },
    android: { priority: "high" },
  });

  await cmdRef.update({ status: "sent" });
  res.json({ commandId: cmdRef.id });
});

// --- Reportes desde el dispositivo -----------------------------------------
app.post("/devices/:deviceId/locations", requireUser, owner, async (req, res) => {
  const { lat, lng, accuracy, requestId } = req.body;
  await db.collection("devices").doc(req.params.deviceId)
    .collection("locations").add({ lat, lng, accuracy, requestId, createdAt: Date.now() });
  await db.collection("devices").doc(req.params.deviceId).update({ lastSeen: Date.now() });
  res.json({ ok: true });
});

app.get("/devices/:deviceId/locations", requireUser, owner, async (req, res) => {
  const snap = await db.collection("devices").doc(req.params.deviceId)
    .collection("locations").orderBy("createdAt", "desc").limit(50).get();
  res.json(snap.docs.map((d) => d.data()));
});

app.post("/devices/:deviceId/events", requireUser, owner, async (req, res) => {
  const { type, detail } = req.body;
  await db.collection("devices").doc(req.params.deviceId)
    .collection("events").add({ type, detail, createdAt: Date.now() });
  // TODO: si type es SIM_CHANGED o SOS, notificar al dueño / círculo (push/email/SMS).
  res.json({ ok: true });
});

// --- SOS / Persona ----------------------------------------------------------
app.post("/devices/:deviceId/sos", requireUser, owner, async (req, res) => {
  await db.collection("devices").doc(req.params.deviceId)
    .collection("events").add({ type: "SOS", detail: req.body.status, createdAt: Date.now() });
  // TODO: notificar a los contactos del círculo del usuario.
  res.json({ ok: true });
});

function stringifyParams(params) {
  // FCM data solo admite strings.
  const out = {};
  for (const [k, v] of Object.entries(params)) out[k] = String(v);
  return out;
}

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Centinela backend en :${PORT}`));
