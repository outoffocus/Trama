# Fase 3 — Inicio útil y anticipación

## Resultado implementado

- Inicio muestra como máximo los dos próximos compromisos, mezclando tareas con fecha y eventos de los calendarios elegidos. Incluye eventos que ya empezaron y siguen en curso.
- Las propuestas y posibles duplicados se agrupan plegados bajo «Por revisar». No desplazan el diario del día ni se presentan como compromisos confirmados.
- Los eventos de día completo mantienen la fecha local que aparece en Google Calendar y se muestran sin una hora ficticia.
- Cambiar la selección de calendarios actualiza los compromisos futuros. Los eventos eliminados, movidos o pertenecientes a un calendario deseleccionado desaparecen de la copia futura de Trama. El historial anterior a hoy se conserva.
- La memoria diaria se genera en segundo plano sobre el día ya cerrado y no se muestra ni notifica. El aviso semanal se realinea al reiniciar el teléfono, cambiar la hora o cambiar de zona horaria.
- La app detecta si las notificaciones están desactivadas cuando el aviso semanal está activo. Los avisos concretos creados en Google Calendar siguen usando los recordatorios del proveedor de calendario.
- En el detalle de un lugar se puede añadir una visita omitida o corregir lugar, llegada y salida de una visita terminada. La identidad del lugar y sus estadísticas se actualizan dentro de una transacción.
- «Posponer» se ha sustituido por «Cambiar fecha de la tarea» para distinguir esta operación de aplazar una notificación.

## Integridad y límites

No se cambia el esquema de Room. Los calendarios continúan siendo la fuente externa y Trama conserva una copia local conciliada para el Inicio y la búsqueda. Una lectura fallida del proveedor no elimina los eventos importados.

El resumen diario usa WorkManager y Android puede retrasarlo algunos minutos por ahorro de batería. Una alarma con hora concreta se representa como un evento de calendario con recordatorio, cuya entrega corresponde a Google Calendar y a sus permisos. Trama no presenta el resumen diario como una alarma exacta.

Una visita nueva solo puede asignarse por ahora a un lugar ya conocido. La creación manual de un lugar nuevo queda fuera de esta entrega.

## Verificación

- 385 pruebas unitarias de móvil, 173 compartidas y 66 del reloj: 624 correctas.
- APK de móvil compilada correctamente.
- Lint de móvil sin errores.
- Las pruebas instrumentadas de Room compilan e incluyen eventos de calendario solapados con el inicio del rango.
- APK instalada conservando los datos en un Samsung S25+ (SM-S936B). Arranque en frío correcto en 754 ms; Inicio quedó visible y estable, con próximos compromisos, agenda diaria y controles cargados. No se observaron cierres ni errores funcionales de Trama durante esta comprobación básica.

## Validación personal

1. Abrir Inicio y comprobar que se entienden los dos siguientes compromisos sin abrir Agenda.
2. Deseleccionar un calendario y verificar que sus eventos futuros dejan de aparecer; volver a seleccionarlo y comprobar que regresan.
3. Probar un evento de día completo y otro que ya haya empezado.
4. Abrir un lugar conocido, añadir una visita pasada y corregir sus horas.
5. Desactivar las notificaciones de Trama y comprobar que Ajustes refleja la limitación antes de confiar en el resumen.
