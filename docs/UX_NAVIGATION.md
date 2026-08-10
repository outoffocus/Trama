# Navegación y jerarquía de ajustes

## Decisiones de producto

La navegación de TRAMA conserva su modelo temporal:

- `CalendarScreen` sigue siendo Home.
- La navegación por día, selector de mes, vuelta a hoy y acceso a Agenda siguen
  en la barra temporal inferior.
- El timeline mantiene tareas, compromisos, calendarios, grabaciones y lugares.
- Chat, búsqueda, grabaciones y ajustes son utilidades accesibles desde Home, no
  pestañas que compiten con el calendario.
- Wear OS continúa como grabadora y control remoto ocasional.

La prioridad cotidiana es detectar tareas, compromisos y agenda mediante escucha
automática. La aplicación conserva todos sus controles técnicos porque su usuario
principal es avanzado, pero deja de mostrarlos al mismo nivel que las decisiones
habituales.

## Home

La cabecera usa el día seleccionado como título principal. Búsqueda y Chat están
visibles; el menú de desbordamiento contiene `Añadir nota`, `Ver grabaciones` y
`Ajustes`. Esto mantiene accesibles todas las rutas públicas sin añadir una barra
de pestañas.

El día y mes seleccionados se guardan mediante estado restaurable, de modo que
abrir un detalle y volver no debe enviar al usuario a otra fecha.

Contrato de accesibilidad desde Home:

| Destino | Entrada |
| --- | --- |
| Búsqueda | Icono de búsqueda |
| Chat | Icono de asistente |
| Agenda | Barra temporal inferior |
| Grabaciones | Menú `Más opciones` |
| Ajustes | Menú `Más opciones` |
| Entrada, grabación o lugar | Elemento correspondiente del timeline |

## Ajustes básicos

La raíz de Ajustes muestra primero el estado de las funciones y cuatro destinos:

1. `Captura y frases`: perfil de tolerancia, categorías, frases y correcciones
   aprendidas.
2. `Agenda y calendarios`: resumen diario, fuentes de Google Calendar y aviso
   semanal.
3. `Privacidad y copias`: reconocimiento de la propia voz, copia automática,
   exportación e importación.
4. `Apariencia`: tema, legibilidad y colores del timeline.

El perfil `STRICT` se presenta como `Preciso`, `BALANCED` como `Equilibrado` y
`SENSITIVE` como `Exhaustivo`. Solo cambia el nombre mostrado; el contrato de
sincronización y los valores persistidos permanecen intactos.

La configuración completa de categorías y frases continúa en el nivel básico.
Las correcciones aprendidas aparecen plegadas para no desplazar esa función
principal.

## Opciones avanzadas

`Mostrar opciones avanzadas` es persistente y está desactivado inicialmente. Al
activarlo aparecen:

- `IA local`: modelo local, umbral de aceptación y prompts. No admite claves ni procesamiento cloud.
- `Audio y diagnóstico`: duración manual, contexto anterior/posterior, motores,
  métricas y ubicación con intervalos y radios exactos.

La clave de Google Places no se muestra porque todavía no tiene un efecto de
producto terminado. Su preferencia se conserva para compatibilidad, pero no se
ofrece un control que prometa una función futura.

## Lenguaje

Los textos visibles describen consecuencias:

- `Avisarme para reactivar la escucha`, no `Recordar escucha al reiniciar`.
- `Tolerancia al ruido`, no `Precisión de captura`.
- `Conservar el inicio` y `Conservar contexto posterior`, con explicación de
  coste y batería.
- `Exigencia para crear una tarea`, no un umbral técnico sin contexto.
- `Correcciones aprendidas`, separado de categorías y frases manuales.

## Validación pendiente

La estructura está cubierta por tests de rutas, valores predeterminados,
compilación y lint. Sigue siendo necesaria una revisión visual en teléfonos
compactos y grandes y una sesión real para medir:

- comprensión de los iconos de Home;
- tiempo para localizar una frase de captura;
- capacidad de volver al mismo día y posición;
- descubrimiento de grabaciones y backup;
- frecuencia con la que se habilitan opciones avanzadas.
