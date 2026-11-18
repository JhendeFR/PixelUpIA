# PixelUp IA · SnapVault IA

[![Android](https://img.shields.io/badge/Android-SDK%2024%E2%80%9336-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Edge AI](https://img.shields.io/badge/Edge%20AI-100%25%20On--Device-FF6F00?logo=tensorflow&logoColor=white)](https://www.tensorflow.org/lite)
[![Version](https://img.shields.io/badge/Release-v1.1.0-blue)](https://github.com/JhendeFR/PixelUpIA/releases)

**PixelUp IA (SnapVault)** es una galería inteligente nativa para Android orientada a la **organización automática y búsqueda semántica multimodal 100% offline (Edge AI)**. Procesa capturas de pantalla y fotografías directamente en el dispositivo sin enviar datos ni imágenes a servidores externos.

---

## Características Principales

- **Motor de Búsqueda Híbrida (Léxica + Semántica):**
  - **Rama Léxica (Room FTS4):** recuperación instantánea por texto OCR, etiquetas, entidades y aplicación de origen.
  - **Rama Semántica Multimodal (MobileCLIP-S0):** búsqueda libre en lenguaje natural sobre vectores latentes L2 de 512 dimensiones.
  - **Traducción Neuronal On-Device (ML Kit Translate):** traduce consultas en español a inglés localmente (`LightweightQueryTranslator`) antes de consultar MobileCLIP, con respaldo offline en `QuerySemanticExpander`.
- **Análisis Multimodal y Detección Documental:**
  - Extracción de texto interactiva (OCR v2) con detección automática de documentos, comprobantes y recibos.
  - Lectura de códigos QR / barras (`Barcode Scanning`) y entidades estructuradas (`Entity Extraction` + reglas locales para formatos de Bolivia como CI/NIT).
  - Análisis cromático perceptual en espacio **CIELAB ($\Delta E$)** para indexar colores dominantes en búsquedas sin generar ruido en álbumes.
- **Agrupamiento Biométrico No Supervisado:**
  - **Personas:** alineación ocular + **FaceNet Inception-ResNet-v1 (512D)** con clustering por similitud coseno ($\ge 0.65$).
  - **Mascotas:** detección animal + **MobileNetV3-Large (1280D)** con clustering por similitud coseno ($\ge 0.75$).
  - Gestión completa de clústeres (renombrar, fusionar grupos, elegir portada, añadir/quitar fotos y ocultar).
- **Álbumes Inteligentes, Recuerdos y Visor Interactivo:**
  - Colecciones automáticas por aplicación de origen (`ScreenshotSourceClassifier`) y categorías visuales, preservando etiquetas manuales del usuario al reanalizar.
  - Generación de historias y recuerdos temporales (*Smart Moments*) y collage.
  - Visor con proyección dinámica de *bounding boxes* (`UniversalAiOverlay` y `OcrBoundingBoxOverlay`) para copiar texto, abrir enlaces, llamar o marcar entidades.

---

## Arquitectura y Stack Tecnológico

El proyecto sigue **Clean Architecture + MVI** (`ui/`, `domain/`, `data/`):

| Capa / Tecnología | Implementación |
|---|---|
| **Lenguaje y UI** | Kotlin `2.0.21`, Jetpack Compose (`BOM 2026.06.01`), Material 3, Coil `2.7.0`, Navigation Compose |
| **Persistencia Local** | Room `2.6.1` (esquema v12 + `MIGRATION_10_11`, `MIGRATION_11_12`) con tabla virtual FTS4 (`smart_photos_fts`) |
| **Procesamiento Diferido** | AndroidX WorkManager `2.10.0` |
| **TensorFlow Lite (`2.17.0`)** | `mobileclip_s0_image.tflite`, `mobileclip_s0_text.tflite`, `facenet.tflite`, `mobilenet_v3_large.tflite`, `ESRGAN.tflite` |
| **Google ML Kit** | Text Recognition v2 (`19.0.1`), Face Detection (`16.1.7`), Object Detection (`17.0.2`), Image Labeling (`17.0.9`), Barcode (`17.3.0`), Entity Extraction (`16.0.0-beta5`), Translate (`17.0.3`) |
| **SDK / Build** | Gradle `9.5.0`, AGP `9.3.1`, Java 17, `minSdk 24`, `targetSdk 36`, `compileSdk 37` (`arm64-v8a`, `x86_64`) |

---

## Flujo del Pipeline Edge AI

1. **Ingesta e Indexación:** lectura de imágenes locales desde `MediaStore` y carpetas elegidas por el usuario.
2. **Inferencia Concurrente ML Kit:** OCR v2, detección facial, detección de objetos, etiquetado base, códigos QR y entidades lingüísticas.
3. **Modelos Especializados TFLite:** generación de embedding visual 512D (MobileCLIP-S0 con escalado `[0.0, 1.0]`), embeddings faciales 512D (FaceNet 160×160) y embeddings de mascotas 1280D (MobileNetV3).
4. **Expansión y Persistencia:** normalización de colores (CIELAB), traducción de etiquetas (`LabelTranslator`), expansión de sinónimos (`SynonymRegistry`) y guardado reactivo en `SnapVaultDatabase`.

---

## Requisitos para Clonar y Compilar

> **Importante:** Los modelos `.tflite` superan el límite estándar de GitHub (`mobileclip_s0_text.tflite` pesa `161.94 MB`), por lo que están versionados mediante **Git LFS**.

```bash
# 1. Instalar/activar Git LFS (solo la primera vez)
git lfs install

# 2. Clonar el repositorio (descarga automáticamente los modelos .tflite)
git clone https://github.com/JhendeFR/PixelUpIA.git
cd PixelUpIA

# Si ya habías clonado sin LFS, descarga los modelos reales con:
git lfs pull

# 3. Compilar APK de depuración y ejecutar tests unitarios
./gradlew testDebugUnitTest assembleDebug
```

El APK generado quedará en:
`app/build/outputs/apk/debug/app-debug.apk`