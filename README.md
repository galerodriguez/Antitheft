# Antitheft

App de seguridad antirrobo y seguridad personal para Android, inspirada en Cerberus.
Diseñada como herramienta **transparente y con consentimiento**: instalada por el dueño del
dispositivo (o por un familiar con consentimiento), nunca como app espía oculta.

> Nombre interno del paquete: `com.sinaptic.centinela` (no cambia lo que ve el usuario, que es
> "Antitheft"). Se puede renombrar el package más adelante si querés, pero es invasivo.

## Funciones incluidas

- **Antirrobo**: localizar en mapa, alarma, bloqueo remoto, mensaje en pantalla de bloqueo.
- **Protección de datos**: borrado remoto (doble confirmación), alerta de cambio de SIM,
  foto del intruso tras intentos de desbloqueo fallidos.
- **SOS / Persona**: botón de emergencia que comparte ubicación en vivo y avisa a contactos.
- **Familiar / Kids**: compartir ubicación con consentimiento + geocercas (entrar/salir de zonas).
- **PIN maestro**: se define en el primer arranque; se pide para abrir la app y para desactivar
  la protección antirrobo (paso previo a desinstalar → anti-desinstalación).
- **Notificación neutra**: mientras rastrea muestra "Antitheft · Protección activa" con prioridad
  mínima (discreta, sin alarmar), pero sin disfrazarse de otra app.

## Estructura del proyecto

```
android/     App Android nativa (Kotlin)
backend/     API Node.js + Express + Firebase Admin (FCM, Firestore)
web-panel/   Dashboard web (HTML + Leaflet)
docs/        Arquitectura y cumplimiento
```

## Por dónde empezar

1. Leé `docs/ARCHITECTURE.md` (diseño y flujo de comandos) y `docs/COMPLIANCE.md`
   (requisitos de Play Store y marco legal — importante antes de publicar).
2. Backend: `backend/README.md`.
3. App Android: `android/README.md`.
4. Panel web: `web-panel/README.md`.

## Estado

Es un **scaffold funcional**: la arquitectura, los flujos de comandos, el backend y el panel
están cableados de punta a punta. Quedan pendientes para producción los detalles marcados con
`TODO` (pipeline de CameraX, UI/onboarding, login con Firebase Auth, reintentos de red).

## Principio de diseño no negociable

Esta app no se oculta ni permite monitorear a un adulto sin su conocimiento. La visibilidad
(ícono siempre presente, notificación persistente al rastrear, consentimiento explícito) es lo
que la mantiene del lado legítimo y aprobable en Google Play. No lo quites.
