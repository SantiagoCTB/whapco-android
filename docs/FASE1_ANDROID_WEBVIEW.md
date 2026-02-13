# FASE 1 Android (WebView) - Base de proyecto Kotlin

## 1) Decisión técnica: WebView (no TWA)

**Elección: WebView.**

### Justificación breve
- Tu autenticación actual depende de **cookies/sesión web** y navegación server-rendered (Flask + Jinja). WebView preserva esto sin cambio mayor.
- Necesitas soporte controlado de **cámara/mic/adjuntos**, validación de dominios permitidos, y control de logout; WebView te da control fino.
- TWA exige una relación más estricta con Chrome + Digital Asset Links, y da menos control sobre interceptar/bloquear navegación y manejo fino de sesión.

## 2) Arquitectura mínima productiva

- **Single Activity (`MainActivity`) + WebView**.
- `WebViewClient` para:
  - forzar HTTPS,
  - bloquear dominios no permitidos,
  - mostrar estados de error/offline,
  - detectar `logout` y limpiar cookies de sesión.
- `WebChromeClient` para:
  - file chooser (adjuntos),
  - permisos runtime (cámara/mic).
- `network_security_config.xml` para **HTTPS-only** y dominios explícitos.
- Deep links `https://app.whapco.example.com/*` hacia la misma actividad.

## 3) Plan por etapas

### Día 1-2 (MVP funcional)
1. Base Android Studio (este repo): WebView, URL productiva, back navigation.
2. Login persistente vía cookies (`CookieManager`).
3. Envío/recepción chat desde web ya existente (incluye Socket.IO del frontend web).
4. Estados mínimos: loading + offline + retry.
5. Deep links básicos.

### Semana 1 (hardening)
1. Telemetría base (Crashlytics/Sentry + logs estructurados).
2. Lista de hosts por `BuildConfig` (debug/staging/prod).
3. Pull-to-refresh opcional.
4. Mejorar UX de permisos (copy claro cuando usuario rechaza).
5. Validar comportamiento en background/foreground y lock/unlock.

### Semana 2 (internal testing en Play)
1. Firma release + `versionCode` incremental.
2. QA checklist completa (abajo).
3. Cargar AAB en Play Console Internal testing.
4. Recoger feedback de testers y cerrar bugs críticos.

## 4) Cambios backend mínimos recomendados (opcionales, no bloqueantes)

1. Endpoint `GET /mobile/health` (200 simple) para monitoreo de disponibilidad.
2. Endpoint `POST /api/mobile/logout` que invalide sesión en servidor.
3. Ajustes cookie sesión:
   - `Secure=true`
   - `HttpOnly=true`
   - `SameSite=Lax` (o `None` solo si realmente requiere cross-site).
4. Si usan multi-tenant por query/header, estandarizar `?tenant=<id>` en la URL móvil inicial.

## 5) Seguridad mínima obligatoria aplicada

- HTTPS only en `network_security_config`.
- Lista de dominios permitidos (allowlist) en `MainActivity`.
- Bloqueo de navegación externa no autorizada (`shouldOverrideUrlLoading`).
- Mixed content bloqueado (`MIXED_CONTENT_NEVER_ALLOW`).

## 6) Observabilidad

- Logs `Log.i/w/e` con tag único `WhapcoWebView`.
- Estados visuales:
  - loading bar,
  - offline screen,
  - botón retry.
- Recomendación inmediata: integrar Crashlytics o Sentry en Semana 1.

## 7) QA checklist

### Login y sesión
- [ ] Login exitoso con credenciales válidas.
- [ ] Cerrar/reabrir app conserva sesión si cookie sigue vigente.
- [ ] Expirar sesión en backend redirige a login.
- [ ] Logout limpia sesión local y remota.

### Chat y Socket.IO
- [ ] Carga histórico de chat.
- [ ] Envío de mensaje funciona.
- [ ] Recepción en tiempo real funciona.
- [ ] Reconexión tras cortar/restaurar red funciona.

### Adjuntos y permisos
- [ ] Adjuntar imagen/archivo desde input file web.
- [ ] Cámara funciona cuando la web la solicita.
- [ ] Micrófono funciona cuando la web lo solicita.
- [ ] Rechazar permisos no rompe la app.

### Ciclo de vida y red inestable
- [ ] Bloquear/desbloquear pantalla no pierde estado crítico.
- [ ] Ir a background/foreground reanuda web correctamente.
- [ ] Modo avión muestra estado offline y permite retry.
- [ ] Con red lenta, no crashea y mantiene feedback visual.

## 8) Build, APK/AAB y Play Console

## Comandos build
```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
```

## Salidas esperadas
- APK debug: `app/build/outputs/apk/debug/app-debug.apk`
- APK release: `app/build/outputs/apk/release/app-release.apk`
- AAB release: `app/build/outputs/bundle/release/app-release.aab`

## Internal testing (Play Console)
1. Crear app en Play Console.
2. Configurar firma (Play App Signing recomendado).
3. Ir a **Testing > Internal testing**.
4. Crear release y subir `app-release.aab`.
5. Agregar testers (emails o Google Group).
6. Publicar track interno.
7. Compartir URL de opt-in y validar instalación.

## Estructura del proyecto

```text
whapco-android/
  app/
    src/main/
      java/com/whapco/mobile/MainActivity.kt
      res/layout/activity_main.xml
      res/xml/network_security_config.xml
      AndroidManifest.xml
    build.gradle.kts
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  docs/FASE1_ANDROID_WEBVIEW.md
```
