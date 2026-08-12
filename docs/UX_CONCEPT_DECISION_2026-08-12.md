# Concepto UX aprobado: Editorial serena

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

## Dirección visual elegida

El `2026-08-12` se aprueba la variante **A — Editorial serena**. Usará la fuente del
sistema, cuerpo mínimo habitual de 14 sp y una paleta cálida con pocos acentos
simultáneos.

- menos contenedores y bordes;
- estado como bloque editorial integrado en el lienzo;
- timeline con jerarquía tipográfica y línea temporal ligera;
- acción manual visible pero no dominante mientras la app está `En espera`;
- navegación inferior más baja, manteniendo todas sus acciones.

El resultado debe parecer una memoria personal y no un panel de control. La ejecución
mantendrá superficies táctiles de al menos 48 dp aunque visualmente use menos cajas.
La variante B — Tarjetas suaves queda descartada como dirección general; una tarjeta
solo se usará cuando aporte estructura o delimitación funcional real.

## Decisiones desde Home

Una sugerencia que todavía necesita revisión mostrará dos acciones directamente en
su fila de la timeline:

- `Confirmar` es la acción principal. Convierte la sugerencia en una entrada fiable,
  conserva su procedencia y oculta las acciones de revisión.
- `Eliminar` es secundaria y visible, nunca un gesto oculto. Descarta la sugerencia
  inmediatamente y muestra `Deshacer` durante unos segundos.

Estas acciones no aparecerán en eventos de calendario, lugares ni entradas ya
confirmadas. Así Home permite vaciar pendientes sin abrir el detalle y evita llenar
cada elemento con botones. Tocar el contenido seguirá abriendo el detalle para editar
antes de decidir.

Las tareas identificadas en una orden o reunión se presentarán como `Acciones
sugeridas`. No quedarán confinadas al detalle de la grabación: Home mostrará la
cantidad pendiente y permitirá decidir sobre cada una. Varias acciones del mismo
origen se agruparán visualmente, manteniendo `Confirmar` y `Eliminar` por acción y el
acceso a edición al tocar su contenido.

Cada propuesta mostrará su origen y, cuando esté disponible de forma explícita,
responsable y fecha. Trama no completará silenciosamente esos datos ni confundirá una
deducción con una instrucción real.

No se mostrará un diálogo de confirmación al descartar una sugerencia porque la acción
es reversible. El borrado definitivo de una grabación o de datos consolidados seguirá
un contrato específico y no se hará desde estos botones.

## Siguiente puerta de decisión

La dirección, la jerarquía y la posición de `Confirmar` y `Eliminar` quedan aprobadas.
Antes de modificar la implementación se cerrarán los estados visuales restantes:
vacío, error, permiso, procesado y tamaños compacto/grande.
