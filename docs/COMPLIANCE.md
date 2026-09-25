# Cumplimiento legal y de Play Store

Una app antirrobo / de seguridad familiar es legítima, pero comparte capacidades técnicas con
el *stalkerware* (software de acoso). La diferencia está en **consentimiento, transparencia y
visibilidad**. Respetar esto no es solo ético: es lo que hace que la app sea aprobada en Google
Play y que no exponga a Sinaptic a responsabilidad legal.

## Reglas de oro (no negociables en el diseño)

1. **La app es visible.** El ícono está siempre en el cajón de apps. No se implementa ningún modo
   "oculto/stealth". Cerberus históricamente ofreció ocultarse; hoy eso viola las políticas de
   Play y no lo replicamos.
2. **Consentimiento en el primer arranque.** Pantalla de onboarding que explica en lenguaje claro:
   qué se recolecta (ubicación, cámara en caso de robo), con qué fin, quién puede verlo, y cómo
   revocarlo. El usuario debe aceptar activamente.
3. **Notificación persistente** mientras hay rastreo en segundo plano (además es requisito técnico
   de foreground services en Android 8+).
4. **Monitoreo de terceros con aviso.** En modo Familiar/Kids, el dispositivo monitoreado muestra
   una notificación persistente de que está siendo rastreado. Para menores, gestionarlo dentro del
   marco de control parental. Nunca para monitorear a un adulto sin su conocimiento.
5. **Borrado remoto = doble confirmación** y re-autenticación en el panel.

## Google Play — checklist de publicación

- [ ] Política de Privacidad publicada (URL requerida en la ficha).
- [ ] Sección **Data safety** completa y veraz.
- [ ] Declaración de uso de permisos de ubicación en segundo plano (video demo suele pedirse).
- [ ] Justificación del permiso `CAMERA` para captura antirrobo.
- [ ] Si usás `QUERY_ALL_PACKAGES` u otros permisos sensibles: evitarlos salvo necesidad real.
- [ ] Cumplir la política de **Aplicaciones de vigilancia**: la app debe presentarse como
      herramienta de seguridad del propio dispositivo o control parental, con avisos visibles.

## Decisiones de diseño (v0.1)

- **Notificación neutra, no encubierta.** Mientras hay rastreo se muestra "Antitheft · Protección
  activa" con `PRIORITY_MIN` (discreta). NO se disfraza de otra app ni se elimina: Android obliga
  a mostrarla con ubicación en background, y su presencia es la línea que separa antirrobo de
  stalkerware. Se puede editar el texto en `strings.xml`, pero no ocultar el propósito.
- **PIN maestro + anti-desinstalación.** El PIN protege abrir la app y desactivar el Device Admin
  (paso obligatorio antes de desinstalar). Es la misma protección que usan apps antirrobo y de
  control parental legítimas. Android no permite bloquear la desinstalación al 100%, pero sí
  advertir y exigir el PIN.

## Marco legal (referencia general, no asesoramiento legal)

- Rastrear un dispositivo propio: legal.
- Rastrear a un hijo menor bajo tu tutela: generalmente legal, con matices por jurisdicción.
- Rastrear a otro adulto sin su consentimiento: **ilegal** en la mayoría de jurisdicciones
  (incluida Argentina — puede constituir violación de la privacidad / delitos informáticos).

Recomendación: que un abogado revise la política de privacidad y los términos antes de publicar.
