# Backend — Centinela

Node.js + Express + Firebase Admin (Firestore + FCM).

## Puesta en marcha

```bash
cd backend
npm install
# Credenciales de service account de Firebase:
export GOOGLE_APPLICATION_CREDENTIALS=/ruta/a/serviceAccount.json
npm run dev
```

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/devices` | Registrar dispositivo |
| GET  | `/devices` | Listar dispositivos del usuario |
| POST | `/devices/:id/fcm` | Actualizar token FCM |
| POST | `/devices/:id/commands` | Enviar comando (LOCATE, LOCK, ALARM, WIPE...) |
| POST | `/devices/:id/locations` | Subir ubicación (desde la app) |
| GET  | `/devices/:id/locations` | Historial de ubicaciones |
| POST | `/devices/:id/events` | Reportar evento (SIM_CHANGED, geocerca...) |
| POST | `/devices/:id/sos` | Iniciar sesión SOS |

## Seguridad

- Toda ruta exige `Authorization: Bearer <Firebase ID token>`.
- `WIPE` requiere `confirm: "CONFIRM_WIPE"` en el body (doble confirmación).
- Reglas de Firestore en `firestore.rules` para acceso directo desde clientes.
