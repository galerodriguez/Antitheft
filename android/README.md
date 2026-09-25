# App Android — Centinela

Scaffold en Kotlin. Abrir la carpeta `android/` en Android Studio.

## Antes de compilar

1. Crear un proyecto en [Firebase Console](https://console.firebase.google.com), agregar una
   app Android con package `com.sinaptic.centinela` y descargar `google-services.json` a
   `android/app/`. (No incluido en el repo por seguridad.)
2. Habilitar **Cloud Messaging (FCM)** en Firebase.
3. Ajustar `API_BASE_URL` en `app/build.gradle.kts` a la URL de tu backend.

## Generar la APK (paso a paso)

1. Instalá **Android Studio** (incluye el SDK de Android).
2. **File → Open** y elegí la carpeta `android/`. Android Studio completa solo el Gradle
   wrapper (`gradlew`) la primera vez.
3. Copiá tu `google-services.json` (de Firebase) en `android/app/`.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**. El `.apk` queda en
   `app/build/outputs/apk/debug/app-debug.apk`.
5. Pasalo al teléfono e instalá activando "Instalar apps de orígenes desconocidos".

> Nota: sin `google-services.json` el build falla, porque el plugin de Google Services lo exige.
> Es gratis: creás el proyecto en Firebase, agregás una app Android con el package
> `com.sinaptic.centinela` y lo descargás.

## Flujo de pantallas

`OnboardingActivity` (consentimiento) → `PinActivity` (crear PIN maestro) → `MainActivity`
(estado, toggles, SOS). En arranques siguientes: `PinActivity` (desbloqueo) → `MainActivity`.

## Estructura

```
admin/      Device Admin (bloqueo, borrado, intentos fallidos)
fcm/        Recepción de comandos por push
commands/   Localizar, Bloquear, Alarma, Borrado, Foto intruso
location/   Servicio de ubicación en primer plano (notificación persistente)
sim/        Detección de cambio de SIM + BootReceiver
sos/        Botón de emergencia (Persona)
geofence/   Geocercas (Familiar/Kids)
data/       Cliente del backend + almacenamiento cifrado
ui/         Pantallas (onboarding con consentimiento, principal)
```

## Pendientes para producción

- Completar el pipeline de CameraX en `IntruderPhotoCommand`.
- UI real (layouts) con onboarding de consentimiento, toggles y botón SOS.
- Login con Firebase Auth y flujo de registro de dispositivo (guardar `device_id` y `auth_token`).
- Mover llamadas de red a corrutinas / WorkManager con reintentos.
- Manejo de permisos en runtime (ubicación background, cámara, notificaciones).
