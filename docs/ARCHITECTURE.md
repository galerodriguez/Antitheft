# Centinela — Arquitectura

App de seguridad antirrobo y seguridad personal para Android, inspirada en Cerberus,
diseñada como herramienta **transparente y con consentimiento** (instalada por el dueño
del dispositivo, o por un familiar con consentimiento explícito).

## 1. Componentes

```
┌──────────────────┐         FCM push          ┌──────────────────┐
│   Panel Web      │  ──── comando ────►        │  App Android     │
│  (dashboard)     │                            │  (Kotlin)        │
│                  │  ◄─── ubicación/estado ──   │                  │
└────────┬─────────┘                            └────────┬─────────┘
         │  REST/HTTPS                                    │  REST/HTTPS
         │                                                │
         └───────────────┬────────────────────────────────┘
                         ▼
              ┌──────────────────────┐
              │   Backend (Node)     │
              │  Express + Firebase  │
              │  - Auth (usuarios)   │
              │  - Registro devices  │
              │  - Cola de comandos  │
              │  - Historial ubic.   │
              │  - Envío FCM         │
              └──────────┬───────────┘
                         ▼
              ┌──────────────────────┐
              │  Firebase / DB       │
              │  Firestore + FCM     │
              └──────────────────────┘
```

## 2. Flujo de un comando remoto (ej. "localizar")

1. El usuario, autenticado en el **panel web**, pulsa "Localizar" sobre uno de sus dispositivos.
2. El **backend** valida que el dispositivo pertenece a ese usuario y envía un mensaje **FCM data**
   al `fcmToken` del dispositivo: `{ "command": "LOCATE", "requestId": "..." }`.
3. La **app** recibe el push en `CentinelaMessagingService`, despacha a `CommandDispatcher`,
   que ejecuta `LocateCommand`: obtiene la ubicación y la sube al backend
   (`POST /devices/:id/locations`).
4. El backend guarda la ubicación y el panel la muestra en el mapa (polling o Firestore realtime).

FCM es solo el **transporte de la señal**; los datos sensibles (ubicación, fotos) viajan por
HTTPS al backend, no dentro del push. Si el dispositivo está offline, FCM entrega el mensaje
cuando vuelve (mensajes con prioridad alta y TTL configurable).

## 3. Funciones y cómo se implementan

| Función | Mecanismo Android | Permiso / API |
|---|---|---|
| Localizar | FusedLocationProvider | `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` |
| Bloquear pantalla | `DevicePolicyManager.lockNow()` | Device Admin |
| Mensaje en bloqueo | Overlay / cambiar keyguard message | Device Admin (`setDeviceOwnerLockScreenInfo`) |
| Alarma sonora | `MediaPlayer` + `AudioManager` máximo | ninguno especial |
| Borrado de datos | `DevicePolicyManager.wipeData()` | Device Admin |
| Foto del intruso | `CameraX` frontal en background | `CAMERA` |
| Alerta cambio de SIM | Comparar `subscriberId`/`simSerial` guardado al boot | `READ_PHONE_STATE` |
| SOS / Persona | Botón → sube ubicación en vivo + notifica contactos | ubicación + backend |
| Familiar / Kids | Compartir ubicación periódica + geocercas | `Geofencing API` |

> **Foto del intruso y funcionamiento en segundo plano**: la app NO se oculta del cajón de
> aplicaciones ni opera de forma encubierta. Muestra una notificación persistente de servicio
> en primer plano (obligatoria en Android 8+ para trabajo en background) y declara su propósito.
> Esto es lo que separa una app antirrobo legítima de un *stalkerware*.

## 4. Modelo de datos (Firestore)

```
users/{uid}
  email, displayName, createdAt

devices/{deviceId}
  ownerUid, name, model, fcmToken, lastSeen, simSerial, status

devices/{deviceId}/locations/{ts}
  lat, lng, accuracy, battery, source, createdAt

devices/{deviceId}/commands/{cmdId}
  type, params, status(pending|sent|done|failed), createdAt, resultAt

devices/{deviceId}/events/{ts}          # cambio de SIM, PIN fallido, SOS, etc.
  type, detail, createdAt

circles/{circleId}                       # grupo familiar
  ownerUid, members[], name

geofences/{deviceId}/{fenceId}
  name, lat, lng, radiusMeters, notifyOnEnter, notifyOnExit
```

## 5. Seguridad

- **Autenticación**: Firebase Auth (email/password o Google) para el panel. Cada dispositivo
  se registra con un token de dispositivo firmado; los comandos exigen que el `ownerUid`
  del dispositivo coincida con el usuario autenticado.
- **Comandos destructivos** (borrado): confirmación en dos pasos en el panel + re-autenticación.
- **Transporte**: todo por HTTPS/TLS. Nada de credenciales en los pushes.
- **Cifrado**: la base de datos de contactos/config sensible en el teléfono usa
  `EncryptedSharedPreferences` (Jetpack Security).
- **Reglas Firestore**: un usuario solo lee/escribe sus propios devices/circles.

## 6. Cumplimiento (Google Play)

Google Play tiene una política estricta sobre apps de rastreo/monitoreo. Para pasar revisión:

1. **No ocultar el ícono** de la app (prohibido desde Android por políticas de stalkerware).
2. **Notificación persistente** siempre que se rastree ubicación en background.
3. **Consentimiento explícito** en el primer arranque, con pantalla que explica qué datos se
   recolectan y por qué. Para la función Familiar/Kids: consentimiento del titular del
   dispositivo monitoreado (o gestión por control parental para menores).
4. Declarar la app en la categoría correcta y completar la sección **Data safety**.
5. Para monitoreo de otra persona, mostrar una **notificación persistente en el dispositivo
   monitoreado** indicando que está siendo rastreado (requisito de la política de Play).
6. Justificar permisos sensibles (`ACCESS_BACKGROUND_LOCATION`, `CAMERA`) en el
   formulario de declaración de permisos.

Ver `docs/COMPLIANCE.md` para el detalle.
