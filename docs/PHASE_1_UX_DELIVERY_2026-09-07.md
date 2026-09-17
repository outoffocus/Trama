# Trama — fase 1: primera entrega de experiencia

Fecha: 7 de septiembre de 2026.

## Resultado implementado

- Inicio centrado en la fecha, el estado real de escucha y el diario del día.
- Resumen persistente de próximos vencimientos y navegación temporal en la barra inferior.
- Dos acciones principales en cabecera: buscar y añadir. Grabaciones y ajustes quedan en el menú secundario.
- «Añadir» abre una única captura escrita sin categorías previas. La grabación se concentra en el botón principal de micrófono.
- Buscar reúne entradas, lugares y reuniones, además del acceso a «Preguntar a Trama».
- Pendientes arrastradas y elementos completados permanecen plegados y desaparecen cuando están vacíos.
- Las fechas históricas usan «Ese día» para no confundirlas con hoy.

## Mapa de navegación

```text
Inicio
├── Buscar ──► resultados ──► detalle
│           └► Preguntar a Trama
├── Añadir ──► guardar captura
├── Micrófono ──► grabar reunión ──► grabación en curso ──► detalle
├── Próximos ──► agenda
├── Diario ──► entrada / lugar / grabación
└── Más ──► grabaciones / ajustes
```

## Validación personal pendiente

Probar sin instrucciones previas y anotar cualquier duda o paso innecesario:

1. Identificar en menos de diez segundos qué ocurrió hoy y qué vence próximamente.
2. Añadir «No olvidar llamar al taller mañana» y encontrarlo después.
3. Iniciar y detener una reunión desde «Añadir»; abrir la grabación resultante.
4. Buscar un restaurante visitado por nombre o ciudad y abrir la fuente.
5. Llegar a «Preguntar a Trama» desde Buscar.
6. Activar, pausar y reanudar la escucha entendiendo siempre su estado.

## Verificación técnica

- Pruebas unitarias de `app`, `shared` y `wear`: correctas.
- APK de móvil y reloj: compiladas correctamente.
- Pruebas instrumentadas de migración: compilan.
- `lintDebug`: sin errores; conserva 105 avisos técnicos no bloqueantes del proyecto.
- `git diff --check`: correcto.

Esta entrega valida estructura y vocabulario en código. La comodidad, el aspecto en el S25+ y los recorridos que dependen de audio requieren la prueba física anterior.

Se intentó desplegar la entrega al terminar, pero ADB no detectó ningún dispositivo conectado.
