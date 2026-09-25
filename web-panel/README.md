# Panel web — Centinela

Dashboard estático (HTML + JS + Leaflet) para controlar los dispositivos.

## Uso

1. Editar `app.js`: `API_BASE` a la URL de tu backend.
2. Autenticar con Firebase Auth en el navegador y guardar el ID token:
   `localStorage.setItem("centinela_token", "<idToken>")`.
   (En producción: integrar el SDK de Firebase Auth y hacer login con email/Google.)
3. Servir la carpeta con cualquier servidor estático:
   ```bash
   npx serve web-panel
   ```

## Funciones

- Lista de dispositivos del usuario.
- Mapa con la última ubicación (OpenStreetMap).
- Botones: Localizar, Alarma, Bloquear, Foto, Borrar (con confirmación).
- Refresco automático de ubicación cada 15 s.
