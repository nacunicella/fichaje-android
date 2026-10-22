# Fichaje (Android)

App nativa de Android para fichar las horas de un equipo, calcular lo que se gana con la tarifa por hora y llevar lo cobrado. Tiene un widget para la pantalla de inicio. Reemplaza a fichaje.cunicella.com.

- **App:** Kotlin + Jetpack Compose, en `app/`.
- **Widget:** Jetpack Glance (`app/src/main/java/com/cunicella/fichaje/widget`).
- **Backend:** Supabase (auth + Postgres). El esquema, los permisos y la auditoría están en `supabase/migrations`.
- **Prototipo de referencia:** https://claude.ai/artifact/WumZY6htZkfMs8igrDyUPP

## Puesta en marcha

1. **Base de datos.** En Supabase, abrí *SQL Editor*, pegá el contenido de `supabase/migrations/20261022000000_init.sql` y ejecutalo una vez.
2. **Primer usuario.** La primera persona que crea su cuenta desde la app queda como **admin**. Después, el admin carga al resto del equipo con su email, y cuando cada uno crea su cuenta con ese email queda vinculado.
3. **Confirmación por mail.** Supabase pide confirmar el email por defecto. Para probar más rápido se puede apagar en *Authentication → Sign In / Providers → Email → Confirm email*.
4. **APK.** Cada push compila un APK de prueba en GitHub Actions (pestaña *Actions → Android → fichaje-debug-apk*). Se instala en el teléfono permitiendo "orígenes desconocidos".
5. **Widget.** Mantené apretada la pantalla de inicio → *Widgets* → *Fichaje*.

## Configuración

`SUPABASE_URL` y `SUPABASE_ANON_KEY` están en `gradle.properties`. La clave anon es pública por diseño (viaja dentro del APK): los datos los protegen las políticas RLS. Para apuntar a otro proyecto, ponelas en `local.properties`, que no se sube.

## Reglas de negocio

- Horas pagas = salida − entrada − pausas (o las horas cargadas a mano).
- Tarifa propia por persona o la general. La tarifa se congela al cerrar la jornada; mientras está abierta vale la de hoy.
- Feriado: multiplica la tarifa (×2 por defecto).
- Una sola jornada abierta por persona (lo garantiza la base).
- Las jornadas de menos de un minuto no se guardan.

## Auditoría

Cada alta, cambio o baja en configuración, integrantes, jornadas, pausas y cobros queda en `audit_log` con quién lo hizo, cuándo, a quién afecta y los datos de antes y después. La escriben triggers de la base: desde la app no se puede insertar, editar ni borrar. El admin ve todo y cada persona ve lo que la afecta.
