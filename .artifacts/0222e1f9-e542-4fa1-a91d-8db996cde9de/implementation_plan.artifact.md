# Plan de corrección de advertencias y optimización de Gradle

Este plan detalla los pasos para solucionar las advertencias detectadas en el proyecto, incluyendo mejoras en la configuración de dependencias y correcciones de formato en el código.

## User Review Required

> [!IMPORTANT]
> Se actualizarán las versiones de TensorFlow Lite a la **2.17.0** y Coil a la **2.7.0** para resolver problemas de alineación de 16 KB y mejorar la compatibilidad con Android 15+.
> Se migrarán las dependencias hardcodeadas al **Version Catalog** (`libs.versions.toml`).

## Proposed Changes

### [Componente: Datos]

#### [MODIFY] [MediaStoreLocalDataSource.kt](file:///C:/Users/jhean/StudioProjects/PixelUpIA/app/src/main/java/com/jhendefr/pixelupia/data/media/MediaStoreLocalDataSource.kt)
- Añadir la coma final faltante en la llamada a `ContentUris.withAppendedId`.

### [Componente: Configuración de Build]

#### [MODIFY] [libs.versions.toml](file:///C:/Users/jhean/StudioProjects/PixelUpIA/gradle/libs.versions.toml)
- Añadir versiones y librerías para TensorFlow Lite y Coil.

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/jhean/StudioProjects/PixelUpIA/app/build.gradle.kts)
- Reemplazar las dependencias de texto por referencias al Version Catalog (`libs.tensorflow...`, `libs.coil...`).
- Eliminar la posible duplicidad en la declaración de la BOM de Compose.
- Ajustar el bloque `android` para mitigar la advertencia de depreciación si es posible (verificación de compatibilidad con AGP 9.0).

## Verification Plan

### Automated Tests
- Ejecutar `./gradlew :app:assembleDebug` para verificar que la compilación sea exitosa y las advertencias hayan desaparecido.
- Ejecutar `analyze_file` en los archivos modificados.

### Manual Verification
- Sincronizar el proyecto en Android Studio y verificar que no haya errores de sincronización de Gradle.
