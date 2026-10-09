# Ensayo físico del anchor · primera entrega

**Resultado actual: PENDIENTE.** No hay mediciones físicas realizadas. Las pruebas unitarias comprueban geometría, no precisión del tracking.

## Preparación

- Móvil certificado ARCore, batería suficiente y cámara limpia.
- Pared inmóvil con textura, luz uniforme y una zona libre de al menos 1 × 1 m.
- Cinta removible, cinta métrica y una referencia para repetir posición y altura del teléfono.
- Espacio libre para alejarse 3 m y desplazarse 2 m lateralmente; realizar el recorrido sin obstáculos.
- Anota modelo exacto, versión de Android, versión de Servicios de Google Play para RA, versión de la app, luz, distancia inicial y textura de pared. Registra si se calienta el dispositivo.

## Ensayo principal

1. Abre la app. Pulsa **Usar patrón de prueba**, concede cámara y escanea lentamente la pared.
2. Espera a que el plano verde abarque suficiente superficie y toca su interior. El patrón nominal mide **1 × 1 m** y queda fijado automáticamente.
3. Marca la esquina naranja con cinta removible; si es posible marca las otras tres para distinguir traslación, rotación y error de escala. No marques solo el centro.
4. Mide el ancho/alto aparentes sobre la pared usando una cinta y una vista lo más frontal posible. Registra discrepancias iniciales; no asumas que el metro virtual equivale exactamente al físico.
5. Marca en el suelo la posición inicial del teléfono y registra su altura y orientación.
6. Aléjate **3 m desde la posición inicial** y observa la esquina. Registra vibración, saltos, desapariciones y estados de tracking.
7. Desplázate **2 m lateralmente**, manteniendo visible la pared. Comprueba la perspectiva también desde un ángulo oblicuo.
8. Vuelve a la posición, altura y orientación originales. Espera 5 s sin recolocar el patrón.
9. Estima o mide la diferencia horizontal y vertical entre la esquina virtual y la marca física, en cm. Anota también la desviación máxima observada durante el recorrido. La posición de ARCore por sí sola no es una referencia independiente de drift.
10. Repite el recorrido **5 veces con el mismo anchor**. Nunca pulses Repetir colocación durante estas cinco repeticiones.
11. Repite el experimento con una colocación nueva para comprobar reproducibilidad. Si se pierde el tracking definitivamente, registra fallo; no reemplaces la medida con un resultado de otro anchor.

En esta entrega el patrón sustituye al SVG del ensayo propuesto. Cuando se añada el importador, repetir exactamente el ensayo con un SVG cuadrado configurado a 1 m de ancho.

## Ensayos adicionales

| Ensayo | Comportamiento esperado |
| --- | --- |
| Tocar fuera de un polígono | No crea anchor; pide tocar la pared verde. |
| Tocar varias veces tras colocar | El patrón no se mueve ni se crean anchors adicionales. |
| Girar el teléfono, incluyendo 180° | Imagen y geometría mantienen alineación, sin estiramientos. |
| Tapar brevemente la cámara | Se informa si ARCore pierde tracking; se oculta el patrón mientras no existe seguimiento válido. |
| Destapar y apuntar al entorno original | Puede recuperarse el mismo anchor; registrar tiempo y cualquier salto. No se considera recuperado solo por mostrar TRACKING. |
| Segundo plano 10 s y volver | Intenta reanudar la sesión y recuperar el anchor. Registrar si vuelve al punto físico. |
| Cerrar completamente y abrir | No restaura el anchor; comienza otra prueba. |
| Pared blanca lisa / poca luz | Puede no detectar o perder tracking; se informa del estado, sin colocación aproximada automática. |
| Denegar cámara | Explica el permiso y permite reintentar o abrir ajustes. |
| Dispositivo no certificado / ARCore ausente | Informa de incompatibilidad o solicita instalación; no simula AR. |
| Repetir colocación | Confirma, libera el anchor y permite una nueva colocación. |

## Criterio provisional de aceptación

El usuario aún no ha fijado una tolerancia. Como **umbral de ingeniería propuesto, no como resultado ni garantía**, evaluar: desviación de retorno ≤ 3 cm en las cinco repeticiones, error inicial de ancho/alto ≤ 5 %, ningún cambio involuntario de anchor y ningún salto sostenido > 5 cm. Para trazado fino estos umbrales pueden ser insuficientes; habrá que acordar y medir tolerancias más exigentes según el uso.

Un ensayo sin tracking durante el retorno se marca fallido/no medible; no se elimina de la muestra. Reportar errores máximos y fallos, no solo la media. Si el comportamiento no es útil para muralismo, investigar textura/iluminación, distancia, modelo del móvil y posibles marcadores/calibración antes de implementar más interfaz.

## Hoja de resultados

- Fecha, persona y versión de APK:
- Modelo / Android / Servicios de Google Play para RA:
- Pared, textura e iluminación:
- Distancia inicial / altura / orientación:
- Ancho y alto físicos estimados del patrón:
- Método de medición e incertidumbre:

| Recorrido | Error X (cm) | Error Y (cm) | Error máximo (cm) | Pérdidas de tracking / tiempo de recuperación | Saltos / observaciones |
| --- | --- | --- | --- | --- | --- |
| 1 | Pendiente | Pendiente | Pendiente | Pendiente | |
| 2 | Pendiente | Pendiente | Pendiente | Pendiente | |
| 3 | Pendiente | Pendiente | Pendiente | Pendiente | |
| 4 | Pendiente | Pendiente | Pendiente | Pendiente | |
| 5 | Pendiente | Pendiente | Pendiente | Pendiente | |

**Decisión: pendiente / aceptado / rechazado.** Adjuntar mediciones antes de avanzar al importador.

## Diagnóstico

La pantalla muestra estado de cámara y anchor por separado, número de planos verticales activos y distancia al centro. El contador no garantiza que todos los planos estén dentro del encuadre. La cuadrícula permanece oculta durante el tracking insuficiente; no se interpola una colocación ficticia.

Para recoger eventos de la aplicación desde un terminal:

```powershell
adb logcat -s MuralAR
```

El log registra creación de anchors y errores de sesión/render. Para estados transitorios usa también la información en pantalla y el registro manual. No se implementa captura ni grabación dentro de la app.
