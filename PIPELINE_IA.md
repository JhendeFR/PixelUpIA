# Arquitectura y Pipeline Completo de Inteligencia Artificial en PixelUp IA

Este documento técnico detalla de forma exhaustiva el flujo de información, los modelos de Machine Learning involucrados, las transformaciones tensoriales, las heurísticas de filtrado y la arquitectura de almacenamiento y búsqueda implementada en **PixelUp IA**.

---

## 1. Visión General del Sistema

PixelUp IA implementa una arquitectura híbrida de **Edge AI** ejecutada 100% de manera local en el dispositivo (sin dependencias de nube ni servidores externos). El sistema combina:

1. **Modelos de Visión Fundacionales** (Google ML Kit): Detección de texto (OCR), códigos QR/barras, rostros humanos, clasificación de escenas y detección de objetos.
2. **Modelos Dual-Encoder Multimodales** (MobileCLIP-S0 de Apple/OpenAI): Espacio vectorial latente unificado de 512 dimensiones entre visión y lenguaje natural para búsqueda libre ("auto rojo", "perro en la playa").
3. **Modelos Biométricos y de Extracción de Características**: FaceNet (Inception-ResNet-v1) con alineación afín para clustering facial y MobileNetV3 Large para identificación y agrupación de mascotas.
4. **Motor de Búsqueda Híbrido**: Fusión de búsqueda léxica dispersa (*Sparse Lexical Search* con SQLite FTS5 y expansión ontológica de sinónimos) y búsqueda semántica densa (*Dense Vector Retrieval* con similitud coseno de embeddings).

---

## 2. Modelos Implementados: Especificaciones Técnicas

| Modelo | Archivo / Fuente | Tipo / Arquitectura | Tensor de Entrada | Normalización / Preproceso | Tensor de Salida | Propósito en el Pipeline |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **MobileCLIP-S0 Vision** | `mobileclip_s0_image.tflite` (45.5 MB) | FastViT-S0 híbrido (ConvNeXt + Transformer) | `[1, 3, 224, 224]` Float32 (NCHW/NHWC) | ImageNet / CLIP: Media `[0.481, 0.457, 0.408]`, Std `[0.268, 0.261, 0.275]` | `[1, 512]` Float32 (Normalizado L2) | Generación de vector semántico de la imagen completa para búsqueda multimodal y Zero-Shot classification |
| **MobileCLIP-S0 Text** | `mobileclip_s0_text.tflite` (169.8 MB) | Text Transformer (12 capas) | `[1, 77]` Int32 | Tokenizador BPE (Byte-Pair Encoding) con vocabulario de 49,408 tokens (`clip_vocab.json` + `clip_merges.txt`) | `[1, 512]` Float32 (Normalizado L2) | Proyección de la consulta textual del usuario al mismo espacio latente que las imágenes |
| **FaceNet** | `facenet.tflite` (93.9 MB) | Inception-ResNet-v1 | `[1, 160, 160, 3]` Float32 | Recorte facial centrado + Alineación rotacional afín (ojos) + `(pixel - 127.5) / 128.0` | `[1, 512]` Float32 (Normalizado L2) | Embeddings biométricos invariantes de rostros para agrupación no supervisada de personas |
| **MobileNetV3 Large** | `mobilenet_v3_large.tflite` (10.8 MB) | MobileNetV3 Large Feature Extractor | `[1, 224, 224, 3]` Float32 | Recorte de región de mascota + Normalización ImageNet | `[1, 1280]` Float32 (Normalizado L2) | Embeddings de cuerpo/rasgos de animales para agrupación no supervisada de mascotas |
| **ML Kit Text Recognition** | Bundled / On-device Google Play Services | CNN + CTC OCR | Bitmap / `InputImage` nativo | Escala de grises y corrección de orientación EXIF | `Text` (Bloques, Líneas, Caracteres, Cajas `Rect`) | Extracción de texto crudo de recibos, contratos, capturas y letreros |
| **ML Kit Face Detection** | Bundled (Accurate Mode) | Single Shot Detector Facial | Bitmap / `InputImage` nativo | Detección multiescala | Lista de `Face` con cajas `Rect` y puntos cardinales (`leftEye`, `rightEye`) | Detección de presencia humana y coordenadas para alineación afín |
| **ML Kit Image Labeling** | Bundled (Default Model) | MobileNet Quantized | Bitmap / `InputImage` nativo | Redimensionado dinámico interno | Lista de `ImageLabel` (etiqueta en inglés + confianza $[0.0 - 1.0]$) | Clasificación de escenas básicas (paisajes, comidas, deportes) |
| **ML Kit Object Detection** | Bundled | SSD MobileNet V2 Object Detector | Bitmap / `InputImage` nativo | Redimensionado dinámico interno | Lista de `DetectedObject` con caja `Rect` y categoría | Delimitación espacial precisa de mascotas para aislar su ROI |
| **ML Kit Barcode Scanning** | Bundled | 1D / 2D Scanner | Bitmap / `InputImage` nativo | Binarización adaptativa | Lista de `Barcode` con valores textuales y tipos | Detección de códigos QR, enlaces web, redes Wi-Fi y facturación digital |
| **ML Kit Entity Extraction** | Bundled (Spanish & International) | NER (Named Entity Recognition) | String (`textContent` de OCR) | Segmentación de tokens lingüísticos | Lista de `Entity` (Dinero, Teléfono, IBAN, Fecha, etc.) | Categorización de recibos bancarios, facturas y transferencias |
| **ESRGAN** | `ESRGAN.tflite` (4.9 MB) | Residual-in-Residual Dense Network | `[1, H, W, 3]` Float32 | Normalización $[0, 1]$ | `[1, 4H, 4W, 3]` Float32 | Superresolución y mejora de nitidez en visualizador |

---

## 3. Flujo de Datos del Pipeline de Indexación y Enriquecimiento

El pipeline principal es orquestado por [EnrichScreenshotUseCase.kt](file:///c:/Users/jhean/StudioProjects/PixelUpIA/app/src/main/java/com/jhendefr/pixelupia/domain/usecase/EnrichScreenshotUseCase.kt) y puede ejecutarse de forma proactiva mediante [MultimodalEnrichmentWorker.kt](file:///c:/Users/jhean/StudioProjects/PixelUpIA/app/src/main/java/com/jhendefr/pixelupia/data/worker/MultimodalEnrichmentWorker.kt) en segundo plano o de forma reactiva al explorar la galería.

```
+-------------------------------------------------------------------------------+
|                             IMAGEN DE ENTRADA (URI)                           |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
| FASE 1: OCR PREVIO Y REGISTRO EN BASE DE DATOS                                 |
| - TextRecognitionDataSource: procesa imagen con ML Kit Text Recognition       |
| - Deteccion de SourceApp (WhatsApp, Instagram, Banco, etc.)                   |
| - Insercion inicial o actualizacion en SnapVaultDatabase (SmartPhotoEntity)   |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
| FASE 2: INFERENCIA CONCURRENTE MULTIMODAL (Kotlin Coroutines async/await)      |
|                                                                               |
|   [T1: Face Detection]      -> Rostros detectados + BoundingBoxes + Ojos      |
|   [T2: Barcode Scanning]    -> Codigos QR / Codigos de barras                 |
|   [T3: Image Labeling]      -> Etiquetas iniciales (Ingles)                   |
|   [T4: Entity Extraction]   -> Entidades sobre el OCR (Moneda, IBAN, Fecha)   |
|   [T5: Object Detection]    -> Objetos fisicos delimitados con cajas Rect     |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
| FASE 3: SISTEMA DE REGLAS HEURÍSTICAS Y SUPRESIÓN DE FALSOS POSITIVOS          |
|                                                                               |
| 1. Evaluacion de Documento / Factura:                                         |
|    - Conteo de bloques OCR > 12 OR Entidad monetaria OR Palabras clave pago   |
|    - Si es documento: Suprimir categoricamente "perro", "gato", "comida",    |
|      "flor" provenientes de logos o membretes                                 |
| 2. Evaluacion de Comida vs Flores:                                            |
|    - Si confianza de comida > 0.70, suprimir "flor/planta" si conf < 0.85     |
| 3. Limpieza de Insectos en Mascotas:                                          |
|    - Si se detectan rasgos de mascotas, eliminar etiquetas residuales de      |
|      "insecto" o "artropodo" causadas por texturas de pelaje o hocicos        |
| 4. Exclusiones de Usuario:                                                    |
|    - Filtrar cualquier etiqueta presente en excludedTags de la entidad        |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
| FASE 4: INFERENCIA DE MODELOS PESADOS TFLITE (Carga de Bitmap orientado)      |
|                                                                               |
| 1. MobileCLIP-S0 Vision Encoder:                                              |
|    - Bitmap -> Redimension 224x224 -> Normalizacion ImageNet (Float32)       |
|    - Salida: Vector 512D -> L2 Normalize -> Base64                            |
|    - Clasificacion Zero-Shot: Similitud contra 17 conceptos canonicos         |
| 2. FaceClusteringService (Rostros Humanos):                                  |
|    - Recorte de cada rostro con alineacion rotacional afin basada en ojos     |
|    - FaceNet TFLite (160x160) -> Vector 512D normalizado L2                   |
|    - Busqueda de cluster existente (similitud coseno >= 0.65)                 |
|    - Asignacion a cluster o creacion de nuevo cluster con centroide ponderado |
| 3. FaceClusteringService (Mascotas):                                          |
|    - Recorte de objeto "animal" (ML Kit Object) o crop central adaptativo     |
|    - MobileNetV3 TFLite (224x224) -> Vector 1280D normalizado L2              |
|    - Busqueda de cluster mascota (similitud coseno >= 0.75)                   |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
| FASE 5: EXPANSION ONTOLOGICA Y CONSTRUCCION DE INDICE FTS5                     |
|                                                                               |
| - LabelTranslator: traduce etiquetas de ingles a espanol                      |
| - SynonymRegistry: expande cada termino con sinónimos e hiperónimos           |
|   (ej. "perro" -> "perro canino can canidae perro callejero perrito chucho")  |
| - Concatenacion estructurada:                                                 |
|   [OCR text] + [sourceApp] + [Face terms] + [Pet terms] + [Expanded Labels]   |
|   + [Colors] + [Barcodes] + [Entities] + [Zero-Shot Labels]                   |
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
| FASE 6: PERSISTENCIA Y SINCRONIZACION EN BASE DE DATOS                        |
|                                                                               |
| - smartGalleryDao.updateEnrichment():                                         |
|   * Guarda labels, barcodes, entities, hasFaces en SmartPhotoEntity           |
|   * Guarda semanticEmbedding en formato Base64 (512 floats)                   |
|   * Actualiza searchContent en SmartPhotoEntity                               |
|   * Dispara sincronizacion automatica con la tabla FTS5 SmartPhotoFtsEntity    |
+-------------------------------------------------------------------------------+
```

---

## 4. Pipeline de Búsqueda Semántica Híbrida (Dense + Sparse)

Cuando el usuario escribe una consulta en el buscador (por ejemplo: `"automovil rojo"` o `"perro corriendo"`), el flujo ejecutado en [SmartPhotoRepositoryImpl.kt](file:///c:/Users/jhean/StudioProjects/PixelUpIA/app/src/main/java/com/jhendefr/pixelupia/data/repository/SmartPhotoRepositoryImpl.kt) opera mediante **Score Fusion**:

```
                              CONSULTA DEL USUARIO
                            (Ej: "automovil rojo")
                                      |
                +---------------------+---------------------+
                |                                           |
                v                                           v
    [RAMA 1: BÚSQUEDA LÉXICA FTS5]              [RAMA 2: BÚSQUEDA VECTORIAL SEMÁNTICA]
                |                                           |
 - Limpieza de caracteres especiales         - QuerySemanticExpander:
 - Division en tokens ("automovil", "rojo")    Traduce y expande al ingles optimo:
 - SynonymRegistry: consulta sinonimos         * "a photo of a red car"
   para cada token                             * "a red car"
 - Construccion de query SQLite:               * "a photo of a red automobile"
   "(automovil* OR coche* OR auto*)          - ClipTokenizer (BPE 49408 vocab):
    AND (rojo* OR carmesi* OR colorado*)"      Genera tensores [1, 77] int32 con tokens
 - Ejecucion sobre SmartPhotoFtsEntity         - MobileCLIP-S0 Text Encoder:
 - Retorna coincidencias por prefijo FTS       Infiere embeddings 512D normalizados L2
                |                                           |
                |                            - Carga fotos con semanticEmbedding != null
                |                            - Decodifica Base64 -> FloatArray(512)
                |                            - Calcula similitud coseno (producto punto)
                |                            - Filtra fotos con Similitud >= 0.15
                |                            - Ordena descendentemente por relevancia
                |                                           |
                +---------------------+---------------------+
                                      |
                                      v
                     +---------------------------------+
                     | FUSION Y DESDUPLICACION         |
                     | 1. Coloca primero los aciertos  |
                     |    semanticos (MobileCLIP)      |
                     | 2. Agrega aciertos FTS5 que no  |
                     |    esten duplicados             |
                     | 3. Filtra excludedTags          |
                     | 4. Emite lista final a la UI    |
                     +---------------------------------+
```

---

## 5. Pipeline de Clustering Facial y Mascotas

El agrupamiento se realiza de forma incremental y adaptativa:

1. **Alineación Facial**:
   - Se obtienen las coordenadas de los ojos (`leftEye` y `rightEye`) entregadas por ML Kit.
   - Se calcula el ángulo $\theta = \arctan\left(\frac{y_2 - y_1}{x_2 - x_1}\right)$.
   - Se rota el bitmap para nivelar horizontalmente los ojos antes de recortar, garantizando que variaciones de pose no alteren el embedding de FaceNet.
2. **Extracción de Embeddings**:
   - **Rostros Humanos**: FaceNet infiere un vector de 512 floats normalizado por $L_2$.
   - **Mascotas**: MobileNetV3 infiere un vector de 1280 floats normalizado por $L_2$.
3. **Asignación Incremental**:
   - Se compara el vector contra todos los centroides existentes en `FaceClusterEntity` usando similitud coseno:
     $$\text{sim}(\vec{a}, \vec{b}) = \vec{a} \cdot \vec{b} = \sum_{i=1}^D a_i b_i$$
   - Si $\text{sim} \ge 0.65$ (humanos) o $\text{sim} \ge 0.75$ (mascotas):
     - La foto se vincula al cluster en `PhotoClusterCrossRefEntity`.
     - El centroide se actualiza mediante media móvil ponderada:
       $$\vec{C}_{new} = \text{L2\_Normalize}\left( \frac{\vec{C}_{old} \cdot N + \vec{E}}{N + 1} \right)$$
   - Si no alcanza el umbral:
     - Se crea un nuevo cluster con $\vec{C} = \vec{E}$ y se guarda el recorte del rostro en almacenamiento interno como imagen de portada.

---

## 6. Generación de Álbumes Inteligentes y Recuerdos

- **Álbumes Inteligentes ([GetSmartCollectionsUseCase.kt](file:///c:/Users/jhean/StudioProjects/PixelUpIA/app/src/main/java/com/jhendefr/pixelupia/domain/usecase/GetSmartCollectionsUseCase.kt))**:
  - Colecciones Fijas: Personas (`hasFaces = 1`), Documentos (`labels LIKE '%documento%' OR labels LIKE '%texto%'`), Códigos QR (`barcodes IS NOT EMPTY`), Pagos y Facturas (`entities LIKE '%dinero%' OR labels LIKE '%recibo%'`).
  - Colecciones Dinámicas por ML: Basadas en agrupamiento de etiquetas canónicas (Mascotas, Comida, Vehículos, Paisajes, Celebraciones).
  - Colecciones por Aplicación: Agrupamiento automático por `sourceApp` (WhatsApp, Telegram, Instagram, etc.).
- **Recuerdos Inteligentes ([GenerateSmartMemoriesUseCase.kt](file:///c:/Users/jhean/StudioProjects/PixelUpIA/app/src/main/java/com/jhendefr/pixelupia/domain/usecase/GenerateSmartMemoriesUseCase.kt))**:
  - Filtra fotos por ventanas temporales ("Hace 1 año", "Este mes el año pasado").
  - Identifica concentración de fotos con rostros del mismo cluster o momentos clave (vacaciones, viajes, fines de semana).
  - Almacena las historias destacadas en `MemoryStoryEntity`.
