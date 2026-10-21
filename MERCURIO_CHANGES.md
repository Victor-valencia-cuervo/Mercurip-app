# Mercurio — Cambios respecto a BitChord

## Renombre
- App renombrada de **BitChord** a **Mercurio** (`app_name` en todos los `strings.xml`).

## Nuevo (añadido)
- **Tipografía conmutable** en Ajustes: SF Pro (BitChord) ↔ Cormorant Garamond (Hermes), con fuente Cormorant incluida en `res/font`.
- **Clima (Mercurio Weather)**:
  - `WeatherRepository` (Open-Meteo + WMO).
  - `WeatherScreen`.
  - Tarjeta de acceso en Home.
  - Notificación nativa periódica vía `AlarmManager` (`WeatherAlarmReceiver`).
- **Notas**:
  - `NotesRepository` (SharedPreferences + JSON) con colores.
  - `NotesScreen` con alta/baja.
  - Recordatorios con `NoteReminderReceiver` y botón “1h”.
- **Mascotas**:
  - `PetsScreen` con asset portado (`mercurio_pet.webp`).
- **Nube (Supabase)**:
  - `MercurioSupabase` (Auth + Postgrest) con credenciales provistas.
  - `AuthRepository` (signIn/signUp/signOut).
  - `CloudPlaylistsRepository` (list/create/addTrack).
  - `CloudScreen` (login/registro, crear playlist, listar).
- **Toasts estilo Hermes**:
  - Infraestructura `MercurioToasts` + `MercurioToastHost` (overlay en MainActivity).

## Modificado
- `BitChordTheme` acepta `fontFamily` para aplicar la fuente elegida globalmente.
- `MainActivity`: estados y overlays para Weather/Notes/Pets/Cloud; host de toasts.
- `HomeScreen`: tarjetas de acceso a Clima, Notas, Mascotas y Nube.
- `SettingsSheet`: selector de tipografía.

## Dejado sin modificar (se conserva de BitChord)
- **Full Screen Player** (diseño y comportamiento originales).
- Reproducción musical, letras, fuentes, playlists de YouTube Music/Spotify, equalizador, scrobbling, etc.
- Navegación y tabs principales.

## Notas técnicas
- Dependencias añadidas: Supabase (`bom 3.9.0-beta-1`, `auth-kt`, `postgrest-kt`) y Ktor client Android.
- Verificado: `:app:compileDevDebugKotlin` BUILD SUCCESSFUL (entorno con NDK/SDK disponible).

## Sesión 2 (logo, Supabase)
- **Logo nuevo** (`mercurio-logo.png`) aplicado en: icono adaptativo (fondo `#080808`, foreground y monocromo en `mipmap-*`), iconos legacy `ic_launcher*.webp`, las 12 variantes de icono alternativo (glass), `ic_logo.png` (app, sharedUi, desktopUi), icono de notificación (silueta blanca por densidad) y `Logo.png`.
- **Supabase**: esquema completo en `mercurio_supabase.sql` (users, playlists, playlist_tracks, rooms, messages, room_tracks, message_reactions, follows + RLS + realtime + trigger de perfil) y verificación en `mercurio_supabase_verify.sql`.
- `CloudPlaylistsRepository`: los inserts ya no envían `id` vacío (lo genera Postgres).
- `MercurioSupabase.checkConnection()` + botón "Probar conexión" en `CloudScreen`; campo de contraseña oculto.
- Pendiente: mascotas/poderes y toasts completos estilo Hermes (ver preguntas).

## Sesión 3 (mascotas, toasts, Home, limpieza, CI)
- **Mascotas estilo Hermes** (`ui/pets/`): 47 mascotas (sprites en `assets/pets/`), catálogo y frases exportados de Hermes (`catalog.json`, `phrases.json`) y 44 poderes como guiones (`specials.json`) ejecutados por un intérprete (`PetEngine.kt`). Caminan por toda la pantalla, se tocan, se arrastran y se lanzan; reaccionan a la música y al clima; se ocultan con el full reproductor. Galería y ajustes en la pantalla Mascotas (hasta 5, tamaño, hablar, flotante, probar poder).
- **Toasts** estilo Hermes/Sileo (`MercurioToastHost`): título + descripción + tipo, con frases del clima y recordatorios.
- **Avisos del clima** (`WeatherAlerts`): consulta cada 30 min con la app abierta, avisa a las mascotas y muestra toasts (máx. cada 3 h).
- **Home**: saludo + accesos rápidos (Clima, Notas, Mascotas, Nube); los estantes de artistas y playlists recientes siguen con el diseño de BitChord. **Antes** las pantallas Mercurio estaban declaradas pero NO conectadas a la app: ahora sí (MainActivity).
- **Logo**: `ic_logo` ahora es una silueta blanca cuadrada (se pinta con tint) y se ajustaron las proporciones en Home, Wear y TV.
- **Limpieza**: eliminados `web/`, `backend/`, `desktopApp/`, `desktopUi/` y sus workflows. Se mantienen `app`, `wearos` y `tv`.
- **CI**: `android.yml` válido (+ APK sin firmar si no hay keystore), `check.yml` solo Android, `tv.yml`; `tv` con dependencias de Supabase; traducciones zh-CN y zh-TW completadas.
- No cambió nada de la lógica de música ni del full reproductor.

## Sesión 4 (diseño Hermes completo)
- **Navegación por menú (hamburguesa)** estilo Hermes: Inicio, Música, Clima, Notas y recordatorios, Mascotas, Nube y perfil, Ajustes. La app arranca en el Inicio de Hermes; "Música" muestra la interfaz de BitChord (inicio de música, búsqueda, biblioteca, álbumes y artistas) con su diseño y el full reproductor intactos. La tab Inicio de BitChord tiene su botón de menú.
- **Inicio de Hermes** (`ui/hermes/HermesHome.kt`): MERCURIO, día y hora, saludo, carita + frase (según música, hora, inactividad y clima), cita del día, canción actual y próximos recordatorios. El fondo y el acento cambian con la carátula de la canción que suena.
- **Caritas**: las 12 PNG (teñidas con el acento) y las 9 pixel-art; lógica de ánimo portada de `faceMood.js`; tócala para una sorpresa.
- **Mismo diseño en todas las pantallas**: `HermesFrame` (fondo, encabezado y menú) para Clima, Notas, Mascotas y Nube; mini reproductor propio en esas pantallas.
- **Clima rediseñado**: hero con degradado según el clima, sensación, lluvia, UV.
- **Pet Studio (versión simple)**: crear una mascota con tu imagen, nombre y frases; se puede eliminar.
- **Recordatorios**: ahora guardan su hora para aparecer en el Inicio.
- **Traducciones**: zh-HK y zh-Hant completadas (a partir de zh-TW).
