// Middleware de autenticación: valida el ID token de Firebase Auth (panel web) o el
// token de dispositivo (app). Adjunta req.uid.
import { auth } from "./firebase.js";

export async function requireUser(req, res, next) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (!token) return res.status(401).json({ error: "missing_token" });
  try {
    const decoded = await auth.verifyIdToken(token);
    req.uid = decoded.uid;
    next();
  } catch {
    return res.status(401).json({ error: "invalid_token" });
  }
}

// Verifica que el dispositivo pertenezca al usuario autenticado.
export async function requireDeviceOwner(db, req, res, next) {
  const { deviceId } = req.params;
  const snap = await db.collection("devices").doc(deviceId).get();
  if (!snap.exists) return res.status(404).json({ error: "device_not_found" });
  if (snap.data().ownerUid !== req.uid)
    return res.status(403).json({ error: "not_owner" });
  req.device = { id: deviceId, ...snap.data() };
  next();
}
