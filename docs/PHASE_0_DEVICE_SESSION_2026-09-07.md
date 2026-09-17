# Fase 0 — conexión inicial del S25+

## Actualización: copia autorizada e inspección completadas

Esta sección actualiza las restricciones iniciales descritas más abajo; se conservan como historial de la sesión.

- Copia realizada con la app detenida y sin grabación activa. Archivo local fuera del repositorio Git: `/Users/pabmon/Documents/Projects/TRAMA/private-backups/phase0-20260907/trama-data.tar`, directorio con permisos 700 y archivo 600.
- Tamaño: 116876800 bytes; 45 archivos. Incluye bases SQLite/WAL, ajustes, datastore, páginas diarias, grabación y diagnósticos. Excluye modelos descargables y cachés. No sustituye un paquete completo de instalación ni prueba restauración en otro dispositivo.
- SHA-256: `c031f0a0ce56d7f3aae09c9dad3ed5d1f75f16d816af9fa66b69b62d294f6820`.
- Verificación sobre copia extraída: SQLite v16, `integrity_check=ok`, `foreign_key_check` sin incidencias declaradas. 2 entradas, 1 reunión, 275 eventos de timeline y 70 lugares. El archivo de audio referenciado por la reunión está presente. Restauración en Android aún no probada.
- Trama reabierta: arranque COLD reportado por Activity Manager de 692 ms; no representa tiempo hasta todos los datos/modelos listos. Servicio de ubicación recuperado y escucha desactivada. Sin tareas ni eventos de prueba creados.

### Observación de la interfaz instalada

1. Home visible tras desbloqueo. Fecha superior truncada por los accesos grandes de búsqueda, asistente y menú. Estilo cálido claro existente aprovechable.
2. Estado «Inactivo» junto a «Ubicación activa»: puede parecer contradictorio al no explicar que el primero corresponde a escucha.
3. En la vista observada de hoy predominan eventos de calendario; no se infiere asistencia por su presencia. El control «Hecho» requiere probar comprensión porque su apariencia puede confundirse con un estado ya realizado.
4. Menú «Añadir nota» abre «Nueva tarea», con seis categorías. Confirmado en dispositivo; formulario cancelado sin guardar.
5. Día anterior navegable con estancias reales. Las secciones mantienen «HOY» y «COMPLETADO HOY» aunque el día seleccionado es ayer: error de referencia temporal confirmado.
6. Tocar una estancia abre ficha con nombre, visitas y opinión. Volver conserva el día histórico, comprobado por el título y el selector. No se editaron nombre ni valoración.
7. Se utilizó «Hoy» para regresar al presente. El servicio de ubicación sigue activo.

La revisión visual de estos recorridos está completada; captura de voz, alarmas, evaluación de diarización, batería y restauración siguen pendientes. No se guardan capturas de pantalla con datos personales en el repositorio.

## Observado por ADB

- Samsung SM-S936B (S25+), Android 16 / API 36; parche de seguridad 2026-07-05.
- Trama instalada: versionName 1.0, versionCode 1, debug/test-only, targetSdk 35. Última actualización: 2026-08-11 07:30:50 según Package Manager.
- No se ha demostrado identidad binaria entre la APK instalada y el commit local `6176b92`; no atribuir los 585 tests del repositorio a esta APK como si fueran la misma compilación.
- Permisos concedidos: micrófono, lectura/escritura de calendario, ubicación precisa/aproximada. Notificaciones y ubicación en segundo plano: no concedidos. No se cambiaron permisos.
- Servicio de ubicación en foreground presente. No aparece servicio de escucha ni de grabación en la consulta inicial. No se activó el micrófono.
- Apertura con `am start -W`: correcta, WARM, TotalTime 154 ms. Pantalla bloqueada/AOD después de la llamada: esta cifra no demuestra tiempo hasta Home utilizable ni arranque en frío.
- Archivos de base de datos presentes; directorio de 664 KiB según `du`. No se inspeccionaron registros ni se realizó copia.
- Medición puntual de memoria: RSS 158568 KiB, SwapPss 1001520 KiB en un proceso de larga vida. Es una observación para repetir bajo carga controlada; no prueba fuga ni consumo de una reunión.
- USB conectado y cargando, batería 92%, temperatura 33,2 °C. Esta sesión no sirve para medir autonomía de escucha.

## Restricciones de esta sesión

La revisión automática rechazó la exportación preventiva de los archivos de base de datos al ordenador por falta de autorización específica para copiar información personal. No se ejecutó esa exportación. Las pruebas de restauración y migración de datos reales siguen pendientes; se pueden preparar pruebas sintéticas por separado.

La pantalla está bloqueada; se pidió al usuario desbloquear y dejar Trama abierta. No se ha realizado todavía una inspección visual de Home en esta sesión. No se instaló ninguna APK, no se crearon tareas/eventos y no se borraron datos. Abrir la app puede ejecutar sus propios procesos normales de sincronización; no se ha medido si estos modificaron registros.

## Siguiente paso

Actualización: el usuario autorizó explícitamente guardar una copia local de los datos de Trama en el ordenador. Al intentar continuar, `adb devices -l` devolvió una lista vacía. La copia sigue sin realizarse por desconexión del dispositivo; no es necesaria una nueva autorización para esa misma copia al reconectar.

Con pantalla desbloqueada: inspeccionar Home, navegación por fechas, acceso manual, búsqueda y ficha de reunión sin guardar cambios. Antes de restaurar o migrar datos reales, disponer de una copia autorizada y verificada. Verificar notificaciones antes de evaluar avisos propios; los permisos del calendario y los de notificaciones de Trama son independientes.
