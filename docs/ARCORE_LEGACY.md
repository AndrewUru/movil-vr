# Archivo: Mural AR 0.1 · prueba de anclaje ARCore

Esta documentación describe la entrega anterior, que exigía ARCore. La entrada de la app actual es el modo de cámara con marcadores, descrito en el [README vigente](../README.md). El código ARCore se conserva como referencia, sin entrada en el lanzador.

Primera entrega Android para validar un dibujo fijado a una pared mediante **tracking espacial real de ARCore**. El contenido es un patrón de prueba de **1 × 1 metro**, con cuadrícula de 10 cm y una esquina naranja de referencia. La cámara ocupa la pantalla y las tarjetas muestran instrucciones y estado.

**Estado: implementado; la precisión física y el drift están pendientes de una prueba en dispositivo real.** Un build correcto, un test unitario o un emulador no validan el anclaje físico. No había un dispositivo conectado durante el desarrollo.

## Decisión técnica

| Opción | Evaluación para esta entrega |
| --- | --- |
| Android nativo + ARCore | Elegida. Acceso directo a planos verticales, hit tests, anchors, pose de cámara y motivos de pérdida de tracking. Permite diagnosticar el comportamiento sin capas adicionales. |
| Unity + AR Foundation + ARCore | Viable: puede utilizar el mismo tracking de ARCore. No se descarta por precisión inferior, sino porque el motor y su integración añaden peso a esta prueba Android con un solo plano. |
| WebXR | Puede ofrecer AR espacial real en combinaciones compatibles de navegador y dispositivo. No se ha validado aquí su cobertura ni equivalencia de control para paredes y diagnóstico. Para este requisito se prioriza el SDK Android directo. |
| HTML/CSS + getUserMedia | Una cámara con overlay 2D no satisface el tracking espacial requerido. |

Kotlin + Activity nativa + `GLSurfaceView`/OpenGL ES 2.0. ARCore proporciona cámara y tracking; OpenGL solo dibuja. El renderer no estima ni simula una pose alternativa. No hay backend, autenticación, base de datos ni servicios cloud propios.

Versiones fijadas: ARCore **1.56.0**, Android Gradle Plugin **9.2.1** (Kotlin integrado), Gradle **9.4.1**, compile/target SDK **36**, min SDK **24**, bytecode Java **17**. El proyecto se verifica con el JDK **21** de Android Studio.

## Qué incluye

- Cámara trasera gestionada por `Session` de ARCore.
- Detección exclusivamente **vertical** para aislar la prueba de paredes.
- Polígonos detectados con relleno y borde verde; los planos absorbidos se omiten.
- Pulsación → `Frame.hitTest` → plano vertical en `TRACKING` → `isPoseInPolygon` → `HitResult.createAnchor()`.
- Un único patrón métrico centrado en el punto pulsado, con perspectiva real.
- Fijación inmediata: tras colocar el patrón, las pulsaciones no lo desplazan. Se ocultan los planos de exploración.
- Estado independiente de cámara y anchor, contador de paredes y distancia estimada al centro.
- Mensajes para poca luz, movimiento excesivo, falta de textura y pérdida de tracking.
- Reinicio de colocación explícito con liberación del anchor anterior.
- Permiso de cámara, instalación/actualización de ARCore y ciclo pausa/reanudación.

En esta fase no hay selector de archivos ni controles de escala, rotación, opacidad o bloqueo editable: **primero se debe validar físicamente el anclaje**, como indica el encargo. La fijación automática permite ejecutar esa prueba sin depender de dichas funciones.

## Requisitos y compatibilidad

1. Android Studio compatible con AGP 9.2.1; JDK 17 o 21 (recomendado el 21 incluido en Android Studio).
2. SDK Android 36 y Build Tools 36.0.0, instalables desde SDK Manager.
3. Teléfono certificado en la [lista oficial de dispositivos ARCore](https://developers.google.com/ar/devices). Android 7.0/API 24 es el mínimo de la app; algunos modelos requieren una versión superior. No basta con tener cámara y giroscopio.
4. Servicios de Google Play para RA instalados/actualizados. La app solicita la instalación cuando corresponde.
5. Para instalar por USB: opciones de desarrollador, depuración USB y autorización ADB.

La app se declara **AR Required**. No necesita Depth, sensor ToF, cuenta propia, API key ni Cloud Anchors. Un emulador puede servir para comprobar arranque/interfaz, pero no para medir drift real.

## Ejecutar

Abre esta carpeta en Android Studio, deja que sincronice Gradle, selecciona el móvil y ejecuta `app`. Si Studio pide un JDK, selecciona su JBR 21. `local.properties` debe apuntar a tu SDK y está excluido del control de versiones.

PowerShell en Windows:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
# Ajusta esta ruta si tu SDK está instalado en otro lugar:
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
.\gradlew.bat :app:installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.muralar.app/.MainActivity
```

macOS/Linux, con JDK y SDK configurados:

```sh
sh gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
sh gradlew :app:installDebug
adb shell am start -n com.muralar.app/.MainActivity
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Es un APK de desarrollo firmado con la clave debug local, no una entrega publicada en Play Store. La primera compilación requiere Internet para resolver dependencias.

### Verificación de esta entrega

- `assembleDebug`: compilación correcta y APK generado.
- `testDebugUnitTest`: **4 pruebas correctas**, que cubren tamaño métrico tras rotación, orientación respecto a la gravedad, separación de cuadrícula y esquina de referencia.
- `lintDebug`: sin errores. Se conservan los avisos de versiones más recientes de Gradle y SDK; esta entrega fija la combinación de herramientas indicada arriba y usa el SDK instalado.
- Intento de revisión visual en un AVD Pixel 10/API 37: **no completado**. El emulador no terminó de arrancar y ADB rechazó instalar el APK con `device is still booting`. Se cerró el emulador temporal sin guardar cambios.
- Arranque de la app en dispositivo, revisión visual, cámara, detección y precisión física: **pendientes**. No se han presentado pruebas de geometría como evidencia de estabilidad espacial.

## Uso de la prueba

1. Pulsa **Usar patrón de prueba** y permite la cámara.
2. Apunta a una pared iluminada con detalles. Traslada el móvil despacio para observarla desde varios puntos; girar sin desplazamiento puede no ser suficiente.
3. Cuando aparezca un polígono verde, toca dentro de él. Conviene que la zona física disponible supere 1 × 1 m; la comprobación del hit garantiza el centro, no que todo el patrón esté dentro del polígono estimado.
4. El patrón queda fijado automáticamente. La esquina naranja es la referencia.
5. Muévete físicamente: la matriz de cámara cambia y el patrón conserva su relación con el anchor.
6. **Repetir colocación** descarta ese anchor; no lo uses durante una medición de drift.

Los permisos de cámara se solicitan al iniciar AR. Si se deniegan permanentemente, la interfaz abre los ajustes de la aplicación. No se solicita almacenamiento, micrófono ni ubicación. El código de la app no captura ni sube fotos/vídeo; la pantalla inicial incluye información y enlace sobre el tratamiento de datos por Servicios de Google Play para RA.

## Cómo funciona el tracking

ARCore combina información visual e inercial para estimar cámara y superficies. `PlaneFindingMode.VERTICAL` limita esta prueba a paredes. No se usa Instant Placement, profundidad estimada instantánea ni hit tests contra puntos sueltos. La configuración no puede garantizar que una pared blanca sin detalles se detecte.

`HitResult.createAnchor()` crea un anchor asociado al plano alcanzado. Su pose se lee **en cada frame**; no se conserva una matriz mundial estática y no se recrea el anchor al mover el teléfono. La cadena es:

```text
vértice local en metros → pose actual del anchor → vista ARCore → proyección ARCore
```

El patrón está en el plano local XZ, con 2 mm de separación hacia la normal. Una rotación local inicial lo orienta hacia arriba usando la gravedad; esta orientación no se recalcula con cada posición de cámara. Sus dimensiones no dependen de píxeles ni de distancia. No se aplica suavizado a la pose, que podría introducir retraso respecto a la cámara.

Si la cámara o el anchor dejan de estar en `TRACKING`, se oculta el patrón y se informa del problema. En `PAUSED` se conserva el mismo anchor para una posible recuperación. En `STOPPED` se pide repetir la colocación. Ocultar los planos tras colocar no desactiva la sesión ni congela la pose.

La sesión y los anchors se manipulan desde el hilo GL durante el uso. Al salir a segundo plano, primero se detiene el render y después se pausa ARCore. Al volver se intenta recuperar la misma sesión; no se garantiza recuperar la referencia. Al destruir la Activity/proceso se liberan anchor y sesión: no hay persistencia entre arranques.

## Estructura y ampliaciones

```text
app/src/main/java/com/muralar/app/
  MainActivity.kt            UI, permisos, instalación y ciclo de vida
  ar/ArRenderer.kt           Frame, detección, hit test y anchor
  ar/ArStatus.kt             Snapshot inmutable para la interfaz
  render/GlScene.kt          Textura de cámara y geometría OpenGL
  render/PatternGeometry.kt  Geometría métrica independiente de Android
app/src/test/                Pruebas de medidas y orientación
docs/PRUEBA_ANCLAJE.md       Protocolo y plantilla de resultados
```

Después de superar la prueba física, el siguiente paso es añadir un recurso de diseño (PNG/SVG rasterizado con canal alfa) y un renderizador texturizado. Escala, rotación y opacidad serán propiedades **locales al anchor**; el bloqueo impedirá su edición, sin sustituir el seguimiento. Mover requerirá una acción explícita y un nuevo hit válido. El modo normal/contorno podrá generar texturas distintas sin modificar la sesión AR.

La separación entre UI, seguimiento y render permite añadir más adelante proyectos, múltiples diseños, captura, calibración, marcadores, image tracking y otras funciones solicitadas. No se han creado implementaciones ficticias ni servicios para estas funciones futuras. Guardar una matriz de pose no bastará para restaurar un proyecto en el mismo punto real tras reiniciar: requerirá una estrategia de relocalización.

## Limitaciones conocidas

- **No se promete precisión milimétrica ni ausencia de drift.** ARCore puede refinar el mapa, ajustar la pose y producir saltos al relocalizar. La esquina debe medirse contra una referencia física, no contra coordenadas internas que también pueden cambiar.
- Paredes lisas, reflejos, penumbra, desenfoque y movimientos rápidos dificultan el seguimiento. La pintura que cambia la textura del entorno también puede afectar la referencia visual.
- Esta fase no detecta papel, lienzo o suelo como categorías semánticas. Solo geometría vertical; no hay seguimiento de un papel que se mueva.
- El estado `TRACKING` significa que existe una estimación, **no una medida de su error**. La distancia mostrada es estimada, no una certificación de precisión.
- No hay oclusión real: una persona u objeto delante de la pared no tapa virtualmente el patrón.
- Al cerrar la aplicación se pierde el anchor. No hay recuperación garantizada al cambiar de habitación o tras una pausa prolongada.
- Solo el punto tocado se valida dentro del plano. Un patrón puede sobresalir del límite detectado, de una puerta o de la pared física.
- Sin dispositivo físico conectado no se han podido comprobar cámara real, detección de pared, escala física, relocalización, rendimiento o drift. Véase el protocolo antes de aceptar la prueba técnica.

## Prueba de drift y aceptación

Ejecuta [el protocolo reproducible](PRUEBA_ANCLAJE.md), registra el móvil, condiciones y error en centímetros, y repite varias veces. Usa inicialmente el patrón métrico en lugar del SVG; repite el mismo ensayo con un SVG de 1 × 1 m cuando se implemente la importación. No se debe avanzar a los controles de edición dando por aprobada esta medición sin resultados físicos.

## Referencias de arquitectura

- [ARCore: anchors y su relación con trackables](https://developers.google.com/ar/develop/anchors).
- [HitResult: creación de anchor y orientación de la pose](https://developers.google.com/ar/reference/java/com/google/ar/core/HitResult).
- [Detección de superficies y dificultades en paredes](https://developers.google.com/ar/design/content/content-placement).
- [Instalación, permisos y AR Required](https://developers.google.com/ar/develop/java/enable-arcore).
- [Privacidad de ARCore](https://developers.google.com/ar/develop/privacy-requirements).
