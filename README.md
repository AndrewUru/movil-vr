# Mural AR · de una hoja a una pared

Aplicación Android para superponer una imagen sobre **papel, lienzo o una pared plana**, usando la cámara y cuatro marcadores impresos. La pantalla inicial ya **no requiere Google AR / ARCore**. Incluye formatos A4, A3, lienzo de 50 × 70 cm, grafiti de 100 × 100 cm y medidas personalizadas.

**Estado: implementación y compilación local verificadas; funcionamiento de la cámara, rendimiento y precisión física pendientes de probar en el Redmi 13C 5G.** Una prueba de geometría o de reconocimiento de un PDF no valida el comportamiento real al dibujar.

## Instalar y empezar

- APK: [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk), versión `0.2.0-marker-drawing`. Es una APK de desarrollo firmada con la clave debug local.
- Plantillas: [`output/pdf/marcadores-dibujo.pdf`](output/pdf/marcadores-dibujo.pdf). También están incluidas en la app: **Guía → Guardar plantillas**.
- Android 7.0/API 24 o posterior, cámara trasera y permiso de cámara. El modo con marcadores no solicita instalar Servicios de Google Play para RA.
- Instalar esta actualización no garantiza que Play Protect retire un aviso: la comprobación de la APK y la compatibilidad ARCore son asuntos independientes.

1. Imprime al **100 %, sin ajustar a página**. Comprueba con una regla el lado negro: 25 mm para los pequeños; 160 mm para los grandes. Recorta conservando margen blanco, número y flecha.
2. Delimita el rectángulo que ocupará el dibujo. Los marcadores van **por fuera**, todos con la flecha hacia arriba: 0 arriba izquierda, 1 arriba derecha, 2 abajo derecha, 3 abajo izquierda.
3. Deja **5 mm entre el borde negro y cada uno de los dos bordes de su esquina**. Marcadores y dibujo deben estar en el mismo plano. Para una hoja A4, coloca los recortes en la mesa alrededor de la hoja, no dentro del espacio para dibujar.
4. En **Formato**, introduce el ancho y alto del rectángulo y el lado negro real del marcador, en centímetros. Puedes intercambiar ancho y alto para formatos apaisados.
5. Pulsa **Imagen** y elige un PNG/JPG/WebP compatible con Android. Se centra manteniendo sus proporciones; se respetan transparencia y orientación EXIF. No hay importación SVG/PDF de diseños en esta versión.
6. Abre la cámara y encuadra **al menos tres marcadores**. Ajusta la opacidad o pulsa **Ocultar** para ver el original. Sin imagen seleccionada se usa un patrón de prueba.

**El teléfono muestra la imagen en su pantalla, no la proyecta sobre el papel o la pared.** Un soporte permite tener las manos libres. La app no captura ni envía fotografías: procesa la cámara localmente y solo lee la imagen seleccionada con el selector del sistema.

## Cómo se fija el dibujo

OpenCV 4.12.0 reconoce `DICT_4X4_50`, IDs 0–3. `MarkerLayout` define un rectángulo físico en milímetros y las esquinas de cada marcador, con separación de 5 mm. Cada fotograma estima una **homografía del plano** con las esquinas observadas. La imagen se transforma con perspectiva sobre ese mismo fotograma, sin sensores de movimiento ni sesión ARCore.

Se exigen tres marcadores distintos con tamaño suficiente en pantalla, acuerdo geométrico entre sus esquinas y un polígono de dibujo convexo. Un marcador duplicado, una referencia insuficiente o una transformación inconsistente oculta el dibujo. No se reutiliza la última posición conocida, ni se inventa un seguimiento cuando las referencias desaparecen.

El contorno verde delimita la zona configurada, no necesariamente el borde de la imagen: imágenes con otra proporción quedan centradas dentro de esa zona. No hay persistencia espacial independiente de los marcadores. Las medidas, opacidad y última imagen se conservan localmente.

## Papel, lienzo y grafiti

| Uso | Punto de partida | Referencia impresa |
| --- | --- | --- |
| A4 | 21 × 29,7 cm | Cuatro marcadores de 2,5 cm |
| A3 | 29,7 × 42 cm | Cuatro marcadores de 2,5 cm |
| Lienzo | 50 × 70 cm | 2,5 cm, o mayores si se ven pequeños en cámara |
| Pared / grafiti | 100 × 100 cm, modificable | Cuatro marcadores de 16 cm |

Los tamaños son ajustes iniciales, no garantías de alcance. Las medidas admiten 5–1000 cm por lado y marcadores de 2–30 cm, pero el límite práctico depende de la cámara, distancia, iluminación y resolución aparente de las referencias.

- Al acercarte para pintar puedes perder los marcadores: la imagen se ocultará. En murales grandes, trabaja por secciones y prepara una referencia medida para cada una.
- Papel y marcadores deben estar planos. Un lienzo inclinado respecto a los marcadores, una pared curva o papel arrugado invalidan la correspondencia.
- No tapes, muevas ni pintes los marcadores. Todos deben tener el mismo lado negro y la orientación indicada.
- No se corrige la distorsión específica de la lente ni se promete precisión milimétrica. Comprueba el centro y las esquinas con trazos de prueba antes de pintar.
- No se detectan oclusiones: la imagen también se dibuja sobre una mano que pase por delante.
- Los cuatro marcadores impresos en la primera página deben **recortarse y recolocarse**: esa página es una hoja de recortes, no el plano de referencia final.

## Compilar y comprobar

JDK 21 de Android Studio, Android SDK 36 y Build Tools 36.0.0. AGP 9.2.1, Gradle 9.4.1. `local.properties` debe apuntar al SDK.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
.\gradlew.bat :app:installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.muralar.app/.MarkerActivity
```

Las pruebas unitarias cubren la posición métrica de los marcadores, el cambio a tamaños de mural, el ajuste de imágenes sin deformación y el rechazo de medidas inválidas; se conservan las cuatro pruebas del renderer ARCore anterior. El generador del PDF renderiza las cinco páginas y comprueba que OpenCV reconoce los IDs esperados en cada una.

Para regenerar plantillas y la copia incluida en la APK:

```powershell
python -m pip install opencv-python-headless==4.12.0.88 reportlab pymupdf pypdf
python tools/create_marker_templates.py
```

Después recompila la APK. El PDF tiene cinco páginas: una con cuatro recortes pequeños y cuatro páginas con un marcador grande por página. `tmp/pdfs` contiene las vistas de control visual y está excluido de Git.

Antes de considerar aceptado el modo, prueba en el teléfono: primer permiso, selector de imágenes, orientación, volver del segundo plano, exportar PDF, tapar uno/dos marcadores, cambiar de A4 a mural y comparar físicamente el dibujo al desplazar la cámara. Registra desviaciones; el build no sustituye estas pruebas.

## Código y referencias

- `MarkerActivity.kt`: cámara normal, permisos, selección de imágenes, medidas, ayuda y exportación de plantillas.
- `marker/MarkerLayout.kt`: geometría física compartida por todos los tamaños.
- `marker/MarkerRenderer.kt`: detección, validación geométrica y superposición con transparencia.
- `tools/create_marker_templates.py`: PDF vectorial y verificación de reconocimiento.
- [Documentación histórica ARCore](docs/ARCORE_LEGACY.md): la implementación anterior se conserva sin entrada en el lanzador. ARCore está declarado opcional y no participa en el modo nuevo.
- [OpenCV para Android](https://opencv.org/opencv4android-usage-models/).
- [Detector ArUco de OpenCV](https://docs.opencv.org/4.12.0/javadoc/org/opencv/objdetect/ArucoDetector.html).
