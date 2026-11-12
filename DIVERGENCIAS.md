# Divergencias con Mihon

Archivos de Mihon que Tomo modifica, y por qué. Al traer cambios de Mihon (`git fetch upstream`, rama `sync/mihon-x`), revisar primero esta lista: son los únicos sitios donde puede haber conflictos. Los archivos nuevos de Tomo (`tomo/`, `home/`, `Tomo*.kt`, iconos, fuentes) no chocan.

Generar la lista actual: `git diff --name-status upstream/main...HEAD | grep -v '^A'`.

## Datos

- No hay migraciones SQL ni tablas nuevas en la base de Mihon.
- Los avisos de actualización (omitidas y errores) viven en `library_update_issues.json` (`data/library/LibraryUpdateIssues.kt`).
- Las obras de texto (fanfics) se marcan en `text_works.json` (`tomo/text/TextWorks.kt`) y viven en la fuente Local como un EPUB por obra.
- Las búsquedas de "Disponible en" se guardan 24 h en `alternatives_cache.json` (`tomo/alternatives/AlternativeFinder.kt`).
- Las preferencias nuevas usan claves con prefijo `tomo_`.

## Fase 0: base

| Archivo | Por qué |
| --- | --- |
| `.github/workflows/build.yml`, `release.yml` (y se borró `update_website.yml`) | CI y releases firmados en `pepematou/tomo` con `ubuntu-latest` |
| `README.md` | README de Tomo en español |
| `app/build.gradle.kts` | `applicationId app.tomo`, versión y Haze |
| `gradle/build-logic/.../BuildConfig.kt` | Telemetría apagada por defecto |
| `gradle/libs.versions.toml` | Haze 2.0.1 |
| `data/updater/AppUpdateChecker.kt` | Busca versiones en `pepematou/tomo` |
| `i18n/.../base/strings.xml`, `es/strings.xml`, `plurals.xml` | `app_name`, textos `tomo_*` y correcciones ("Sólo" → "Solo") |

## Fase 1: actualizaciones

| Archivo | Por qué |
| --- | --- |
| `domain/.../LibraryPreferences.kt` | Cada 12 h, cualquier red, sin filtros de omisión; filtro "Fuente caída" |
| `data/library/LibraryUpdateWorker.kt` | Las manuales ignoran los filtros, guarda los motivos, reintenta 429/5xx y revisa extensiones |
| `ui/updates/UpdatesViewModel.kt`, `UpdatesTab.kt`, `presentation/updates/UpdatesScreen.kt` | Bandeja de avisos y botón atrás |
| `presentation/more/settings/screen/SettingsLibraryScreen.kt` | Intervalo de 6 h |
| `mihon/app/di/AppGraph.kt` | Acceso a `LibraryUpdateIssues` |

## Fase 2: navegación y cristal oscuro

| Archivo | Por qué |
| --- | --- |
| `ui/home/HomeScreen.kt` | Cuatro secciones, barra flotante de cristal y enlaces directos |
| `ui/library/LibraryTab.kt`, `ui/browse/BrowseTab.kt`, `ui/more/MoreTab.kt`, `ui/history/HistoryTab.kt` | Encabezados de cristal, Descubrir, Tú y navegación |
| `domain/ui/UiPreferences.kt`, `domain/ui/model/AppTheme.kt` | Tema TOMO por defecto y "Abrir en Biblioteca" |
| `presentation/theme/TachiyomiTheme.kt`, `ui/base/delegate/ThemingDelegate.kt`, `res/values/themes.xml` | Colores, tipografía Onest y fondo de cristal |
| `presentation-core/.../material/Scaffold.kt` | Fondo de cristal por defecto y margen de la barra flotante |
| `presentation/components/AppBar.kt`, `TabbedScreen.kt` | Barras transparentes, botones redondos y buscador en píldora |
| `presentation/library/components/*` (`LazyLibraryGrid`, `LibraryTabs`) | Columnas e indicador dorado |
| `ui/library/LibraryViewModel.kt` | Filtro "Fuente caída" (lee `LibraryUpdateIssues`) |
| `domain/.../LibraryDisplayMode.kt` | Cuadrícula cómoda por defecto |
| `presentation/manga/MangaScreen.kt` | Ficha de cristal |
| `presentation/history/HistoryScreen.kt`, `history/components/HistoryItem.kt` | Tarjetas de cristal |
| `presentation/more/MoreScreen.kt` | Tú de cristal |
| `presentation/more/settings/screen/SettingsMainScreen.kt`, `SettingsAppearanceScreen.kt` | Ajustes agrupados por tarea |
| `domain/source/service/SourcePreferences.kt` | Búsquedas recientes (`tomo_recent_searches`) |
| `ui/browse/source/globalsearch/SearchViewModel.kt`, `GlobalSearchScreen.kt` | Reintentar la búsqueda y búsqueda global de cristal |

## Disponible en y fuentes caídas (v0.12)

| Archivo | Por qué |
| --- | --- |
| `ui/manga/MangaScreen.kt` | "Migrar" abre la hoja "Disponible en" con el tema de cristal |
| `ui/home/HomeTab.kt`, `ui/updates/UpdatesTab.kt` | "Migrar todas" abre la revisión en lote de fuentes caídas |

## Estante, tablet y bienvenida (v0.13)

| Archivo | Por qué |
| --- | --- |
| `presentation/more/onboarding/OnboardingScreen.kt`, `ui/more/OnboardingScreen.kt` | Bienvenida de cristal en 3 pasos y acceso a repositorios |

## Fanfics (v0.14)

| Archivo | Por qué |
| --- | --- |
| `ui/reader/ReaderActivity.kt` | `newIntent` abre el lector de texto si la serie es de texto |
| `ui/main/MainActivity.kt` | Un enlace de AO3 compartido abre la importación en vez de la búsqueda |
| `AndroidManifest.xml` | Actividad `TextReaderActivity` |
| `presentation/theme/TomoTypography.kt` | La familia Onest se comparte con el lector de texto |
