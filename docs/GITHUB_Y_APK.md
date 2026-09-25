# Subir a GitHub y obtener la APK (sin instalar nada)

La APK se compila sola en GitHub gracias a GitHub Actions (`.github/workflows/build-apk.yml`).

## 1. Subir el proyecto a GitHub

1. Creá un repo vacío en https://github.com/new (ej. `antitheft`, privado).
2. En tu compu, dentro de la carpeta del proyecto:

```bash
git remote add origin https://github.com/TU_USUARIO/antitheft.git
git branch -M main
git push -u origin main
```

(El repo ya viene inicializado con los commits hechos.)

## 2. Descargar la APK compilada

- Apenas hacés el `push`, GitHub compila la APK automáticamente.
- Andá a la pestaña **Actions** del repo → entrá al último run "Build APK".
- Al terminar (unos minutos), abajo en **Artifacts** vas a ver **`antitheft-debug-apk`**.
- Descargalo, descomprimí el `.zip` y adentro está **`app-debug.apk`**.
- Pasala al teléfono e instalá (activando "Instalar apps de orígenes desconocidos").

> Esta primera APK **instala y abre** (vas a ver el onboarding, el PIN y la pantalla principal),
> pero SIN push remoto todavía. Para activar el control remoto, hacé el paso 3.

## 3. (Después) Activar Firebase / push

1. Creá un proyecto en https://console.firebase.google.com
2. Agregá una app Android con el package `com.sinaptic.centinela`.
3. Descargá `google-services.json`.
4. En GitHub: **Settings → Secrets and variables → Actions → New repository secret**
   - Nombre: `GOOGLE_SERVICES_JSON`
   - Valor: pegá **todo el contenido** del archivo `google-services.json`.
5. Volvé a **Actions → Build APK → Run workflow**. La nueva APK ya viene con push habilitado.

## ¿Y compilar en Android Studio?

Es la alternativa local: File → Open la carpeta `android/`, poné `google-services.json` en
`android/app/`, y Build → Build APK(s). Ver `android/README.md`.
