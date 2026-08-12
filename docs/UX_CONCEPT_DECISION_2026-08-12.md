# Concepto UX pendiente de aprobación visual

Fecha: `2026-08-12`.  
Base: [`PRODUCT_SPEC_2026-08-12.md`](PRODUCT_SPEC_2026-08-12.md).

## Navegación propuesta

Existe una sola navegación principal, no una colección de pestañas:

```text
Home / día seleccionado
├── búsqueda
├── Chat local (secundario)
├── Agenda (entrada permanente)
├── grabaciones
├── ajustes
└── timeline
    ├── detalle de entrada
    ├── detalle de grabación
    └── detalle de lugar
```

Home conserva el control temporal inferior actual: día anterior/siguiente, apertura
del mes, vuelta a hoy y selección de la semana. Se permite reducir altura, ruido de
color y densidad tipográfica, sin cambiar esas acciones ni su modelo mental.

La Agenda tendrá una entrada permanente aunque esté vacía. Búsqueda y Chat seguirán
visibles como utilidades; grabaciones y Ajustes permanecerán en `Más`. Ningún destino
público dependerá de que exista contenido para poder encontrarse.

## Jerarquía de Home

1. Fecha seleccionada.
2. Estado principal: `Inactivo`, `En espera`, `Capturando`, `Procesando` o
   `Necesita atención`.
3. Explicación de una línea, micrófono en uso y acción contextual `Activar`, `Pausar`
   o `Resolver`.
4. Timeline único del día.
5. Acción `Grabar reunión` y entrada manual de respaldo.
6. Navegación temporal inferior persistente.

La palabra `Trama` activa una **orden breve** dentro del modo continuo; no inicia una
reunión. `Grabar reunión` es un caso bajo demanda separado, con inicio y parada
explícitos, transcripción diarizada, resumen y extracción de acciones.

El estado identifica siempre qué dispositivo posee el audio: `En espera · móvil`,
`En espera · reloj`, `Capturando orden · móvil`, `Grabando reunión · reloj`, etc. El
usuario transfiere la escucha continua entre móvil y reloj; no quedan ambos activos a
la vez por defecto.

## Jerarquía de detalle

1. Contenido editable.
2. Una acción dominante según estado: `Confirmar`, `Completar` o `Reabrir`.
3. Fecha, tipo y prioridad editables.
4. `Cómo se creó`, plegado.
5. Compartir y eliminar dentro de `Más`.
6. Diagnóstico solo en Ajustes avanzados.

El detalle de reunión tiene una jerarquía propia: resumen, acciones sugeridas y
transcripción diarizada; la reproducción solo aparece si el audio se conserva.

## Variantes visuales

Ambas usan la fuente del sistema, cuerpo mínimo habitual de 14 sp, paleta cálida y el
mismo contrato funcional.

### A — Editorial serena (recomendada)

- menos contenedores y bordes;
- estado como bloque editorial integrado en el lienzo;
- timeline con jerarquía tipográfica y línea temporal ligera;
- acción manual visible pero no dominante mientras la app está `En espera`;
- navegación inferior más baja, manteniendo todas sus acciones.

Ventaja: se parece a una memoria personal y no a un panel de control. Requiere una
ejecución rigurosa del espaciado para que el contenido siga siendo claramente táctil.

### B — Tarjetas suaves

- cada bloque importante vive en una superficie redondeada;
- estado muy reconocible y separación inmediata entre tipos de contenido;
- timeline algo más denso y convencional;
- navegación inferior contenida en una superficie elevada.

Ventaja: más familiar y explícita. Riesgo: volver a acumular cajas y controles a
medida que crezca el producto.

## Decisión pendiente

Elegir `A` o `B`. La variante seleccionada se convertirá en especificación visual con
medidas, componentes, estados vacíos, error, permiso y tamaños compacto/grande antes
de modificar la implementación.
