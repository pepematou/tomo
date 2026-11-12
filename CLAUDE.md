@AGENTS.md

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.

## Tomo

Tomo es un fork de Mihon (lector de manga y fanfics, diseño "cristal oscuro"). El plan completo está en `../plan-tomo2.md`.

- Compilar con JBR 21: `JAVA_HOME=~/.jdks/jbr-21.0.11 ./gradlew check`.
- Tocar lo mínimo los archivos de Mihon. Cada archivo de Mihon que cambies se anota en `DIVERGENCIAS.md`.
- Sin migraciones SQL en la base de Mihon. Los datos propios van en archivos aparte (por ejemplo `library_update_issues.json`) o en `tomo.db`.
- Textos nuevos con clave `tomo_*`, en `i18n/.../base/strings.xml` (inglés) y `es/strings.xml`. Preferencias nuevas con prefijo `tomo_`.
- Las pantallas de cristal solo se activan con `AppTheme.TOMO` (`LocalGlassTheme`). Reutiliza el kit `presentation/tomo/TomoKit.kt`.
- Release: subir `versionCode`/`versionName` en `app/build.gradle.kts`, commit y tag `vX.Y.Z`, y push del tag. GitHub Actions publica el APK. Nunca forzar el push; si un tag falla, usar el siguiente número.
- `git fetch upstream` se hace sin tags (`remote.upstream.tagOpt --no-tags`).
