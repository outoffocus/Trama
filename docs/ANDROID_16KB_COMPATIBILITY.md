# Compatibilidad con páginas de memoria de 16 KB

## Estado

TRAMA es compatible con dispositivos Android de páginas de memoria de 16 KB en
las arquitecturas afectadas por el requisito de Android: `arm64-v8a` y
`x86_64`.

Estado verificado el `2026-08-10`:

| Artefacto | ABI relevante | ELF `PT_LOAD` | Alineación ZIP | Resultado |
| --- | --- | --- | --- | --- |
| `app-debug.apk` | `arm64-v8a` | 16 KB o superior | 16 KB | Compatible |
| `wear-debug.apk` | `arm64-v8a` | 16 KB o superior | 16 KB | Compatible |

## Causa del aviso original

El proyecto utilizaba `com.alphacephei:vosk-android:0.3.47`. Su
`lib/arm64-v8a/libvosk.so` tenía segmentos ELF `PT_LOAD` alineados a 4 KB. El
APK sí estaba correctamente empaquetado, por lo que ejecutar `zipalign` de nuevo
no podía corregir el binario.

La solución aplicada es actualizar Vosk a `0.3.75`. Esta versión aporta un
`libvosk.so` ARM64 compatible con 16 KB y actualiza JNA a `5.18.1`, cuya
`libjnidispatch.so` ARM64 también es compatible.

No se ha añadido `android:pageSizeCompat` para ocultar el aviso ni se ha activado
el empaquetado nativo heredado. La compatibilidad se resuelve en los binarios
nativos y se valida sobre los APK finales.

## Decisión sobre Wear OS de 32 bits

El reloj conserva `armeabi-v7a` para no eliminar compatibilidad con dispositivos
Wear OS antiguos. El `libvosk.so` de esa ABI continúa usando páginas de 4 KB,
pero `armeabi-v7a` no forma parte de las ABI de páginas de 16 KB que Android exige
comprobar. La verificación oficial se aplica a `arm64-v8a` y `x86_64`.

No se debe retirar `armeabi-v7a` sin revisar antes la matriz real de relojes
soportados por el producto.

## Verificación local

Construir ambos APK y comprobar todos sus binarios relevantes:

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
./scripts/check-16kb-alignment.sh \
  app/build/outputs/apk/debug/app-debug.apk \
  wear/build/outputs/apk/debug/wear-debug.apk
```

El script realiza dos comprobaciones independientes:

1. Inspecciona cada segmento ELF `PT_LOAD` de `arm64-v8a` y `x86_64` mediante
   `llvm-objdump`; ninguna alineación puede ser inferior a `2**14`.
2. Ejecuta `zipalign -c -P 16 4` sobre el APK completo.

Una ejecución correcta muestra `ALIGNED` para cada `.so`, `ZIP_ALIGNED` para
cada APK y termina con código de salida `0`. El script localiza Android SDK/NDK
mediante `ANDROID_SDK_ROOT`, `ANDROID_HOME` o `sdk.dir` en `local.properties`.

## Integración continua

El workflow `.github/workflows/android-ci.yml` construye los APK y ejecuta el
mismo script. Una futura actualización de Vosk, JNA, sherpa-onnx, MediaPipe,
LiteRT u otra dependencia nativa no podrá integrarse si vuelve a introducir una
biblioteca de 4 KB en una ABI relevante.

## Si Android sigue mostrando el aviso

1. Sincronizar el proyecto con Gradle.
2. Ejecutar `Build > Clean Project` en Android Studio o reconstruir los APK.
3. Desinstalar del dispositivo la compilación anterior.
4. Instalar el APK recién generado.
5. Ejecutar el script anterior contra exactamente el APK que se va a instalar.

No debe diagnosticarse el problema mirando únicamente el nombre de la biblioteca
o el mensaje de `stripDebugDebugSymbols`: la prueba válida es la alineación de
los segmentos ELF y del APK final.

## Referencias

- [Android Developers: compatibilidad con páginas de 16 KB](https://developer.android.com/guide/practices/page-sizes)
- [Maven Central: Vosk Android 0.3.75](https://central.sonatype.com/artifact/com.alphacephei/vosk-android/0.3.75)
