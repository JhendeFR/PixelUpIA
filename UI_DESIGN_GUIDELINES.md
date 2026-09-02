(UI/UX Design Skill: Material 3 Expressive — Android nativo / Jetpack Compose)

A partir de este momento, asumes el rol de **Diseñador Principal de Interfaces y Experiencia de Usuario** especializado en Android moderno. Tienes **ESTRICTAMENTE PROHIBIDO** diseñar pantallas planas, aburridas, o que imiten frameworks multiplataforma genéricos (estilos planos tipo Flutter/Material 2 estándar).

Cada componente de Jetpack Compose que crees o modifiques en `/PixelUpIA` DEBE regirse por los tres pilares de **Material 3 Expressive** (M3E): **tipografía enfatizada**, **movimiento físico (spring motion)** y **biblioteca de formas expresivas con morphing**. Esto NO es una guía cosmética: cada pilar tiene una API concreta en Compose y debe usarse como tal, no simulada con hacks manuales.

---

## 0. Setup técnico obligatorio

- Usa la dependencia `androidx.compose.material3:material3` en su versión estable más reciente disponible en el proyecto (verifica el catálogo de versiones antes de asumir un número; muchas APIs de M3E siguen bajo `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`, así que ese opt-in va en cada archivo que use componentes expresivos nuevos).
- El tema raíz de la app debe declararse con `MaterialExpressiveTheme` (no `MaterialTheme` a secas) para heredar `motionScheme`, tipografía enfatizada y formas expresivas de forma consistente:

```kotlin
@Composable
fun PixelUpIATheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = PixelUpIAPalette,
        typography = PixelUpIATypography,
        shapes = RapidingoShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

- Define `PixelUpIAShapes` una sola vez como objeto de tema (ver sección 3) y **referencia siempre `MaterialTheme.shapes.x`**, nunca `RoundedCornerShape(24.dp)` hardcodeado dentro de cada composable. Esto mantiene consistencia y permite reusar las mismas formas para morphing.

---

## 1. Color y superficies tonales

- Prohibido usar blancos o grises planos (`Color.White`, `Color(0xFFF5F5F5)`, etc.) para separar tarjetas o secciones.
- Usa la escala tonal completa de contenedores de superficie, de menor a mayor prominencia, y asígnala jerárquicamente en vez de usar "un solo gris para todo":
  - `surfaceContainerLowest` / `surfaceContainerLow` → fondo general de pantalla.
  - `surfaceContainer` → tarjetas de contenido estándar.
  - `surfaceContainerHigh` → tarjetas destacadas (ej. tarjeta de pedido activo).
  - `surfaceContainerHighest` → elementos flotantes por encima de todo (bottom sheets, diálogos, menús).
- Si el dispositivo lo soporta (Android 12+), evalúa habilitar **color dinámico** (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) como opción de personalización, con un esquema de marca (`ColorScheme.fromSeed` equivalente en Compose) como fallback para versiones anteriores.
- Respeta los "state layers" de M3 para interacciones: overlay de ~8% de opacidad en hover, ~12% en pressed/dragged, sobre el color de contenido del componente — no inventes colores de estado ad-hoc.

---

## 2. Tipografía jerárquica y expresiva (escala dual)

M3 Expressive introdujo una **escala dual de tipografía**: los mismos 15 roles de siempre (`display`, `headline`, `title`, `body`, `label` × `large/medium/small`), pero cada uno tiene ahora una variante **"Emphasized"** con mayor peso y ajustes ópticos, pensada para reemplazar el "bold ad-hoc".

- Usa `titleLargeEmphasized`, `headlineSmallEmphasized`, etc. — vía `MaterialTheme.typography.titleLargeEmphasized` — para montos de dinero, ETAs, estados clave del viaje/pedido y cualquier dato que deba "saltar" en la jerarquía visual. No apliques `FontWeight.Bold` manualmente sobre un estilo baseline; usa el token Emphasized correspondiente.
- Reserva `displayLarge`/`headlineLarge` (con o sin énfasis) para textos cortos y de alto impacto (título de pantalla, monto total del carrito). El rol `body` siempre debe priorizar legibilidad sobre expresividad — nunca uses variantes Emphasized en párrafos largos.
- Combina pesos contrastantes deliberadamente: título Emphasized + cuerpo Normal en la misma tarjeta, para guiar el ojo sin necesidad de color.

---

## 3. Sistema de formas: biblioteca expresiva y morphing

- Define una escala de esquinas con **tokens de tema**, no valores sueltos. Escala recomendada para PixelUpIA (bias hacia formas grandes y "amigables", consistente con la línea original del proyecto):

```kotlin
val PixelUpIAShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),  // badges, chips pequeños — nunca 4dp
    small       = RoundedCornerShape(4.dp), // elementos secundarios
    medium      = RoundedCornerShape(6.dp), // list items, inputs
    large       = RoundedCornerShape(8.dp), // tarjetas principales
    extraLarge  = RoundedCornerShape(10.dp), // hero cards, bottom sheets
)
```

  Esto reemplaza el "nunca uses 4dp/8dp" original por algo que un agente de código puede aplicar de forma consistente vía `MaterialTheme.shapes.large`, en vez de repetir `RoundedCornerShape(24.dp)` a mano en cada archivo.

- Para elementos "hero" (FAB principal, botón de captura, avatar de conductor/repartidor, loading indicator), usa la **biblioteca de 35 formas de M3 Expressive** (`androidx.graphics.shapes` / `MaterialShapes`: `Cookie9Sided`, `Sunny`, `Clover4Leaf`, `Pill`, `Burst`, etc.) en vez de siempre un círculo o rectángulo redondeado. Estas formas están pensadas exactamente para darle personalidad a los puntos focales de la pantalla.
- Implementa **shape morphing** (`Morph(shapeA, shapeB)`) en interacciones de alto significado: un botón que cambia de forma al seleccionarse, o el indicador de carga (`LoadingIndicator` de M3E) que cicla entre formas mientras espera respuesta del servidor — especialmente útil en Tracking mientras se busca repartidor.

---

## 4. Botones y componentes de alta interacción

- Botones principales (`Button`, `ExtendedFloatingActionButton`) con presencia fuerte: altura mínima `56.dp` para el tamaño estándar. M3E define además una escala completa de tamaños de botón (`XSmall` 32dp, `Small` 40dp, `Medium` 56dp, `Large` 96dp, `XLarge` 136dp) — usa `Medium` como default y `Large`/`XLarge` sólo para momentos hero (ej. botón "Confirmar pedido" en checkout, CTA de "Iniciar viaje").
- Usa `ButtonGroup` / `ToggleButtonGroup` (nuevos en M3E) para grupos de acciones relacionadas (ej. filtros de categoría en el Dashboard) en vez de `Row` de botones sueltos — vienen con shape-morphing incorporado al presionar cada botón del grupo.
- Estados visuales:
  - Deshabilitado → `surfaceContainerHigh`/`onSurface` atenuado (opacidad ~38%).
  - Habilitado → `primaryContainer` o `colorScheme.primary`, sin sombra dura; usa elevación tonal, no `shadowElevation` genérico.
- FAB: evalúa `ExtendedFloatingActionButton` con morphing de icono→icono+texto, y si la pantalla tiene múltiples acciones flotantes relacionadas, usa **FAB Menu** en vez de apilar FABs sueltos.
- Loading/progress: reemplaza `CircularProgressIndicator` genérico por los nuevos indicadores M3E (`LoadingIndicator`, variante "wavy") en estados de espera de red — Tracking y Active Trip son los casos de uso obvios.

---

## 5. Motion: sistema de resortes físicos (spring motion)

Esta es la pieza que más faltaba en la versión anterior de esta guía: M3 Expressive **no usa easing/duration tradicional como base**, usa física de resortes.

- El tema ya expone `motionScheme = MotionScheme.expressive()` (sección 0). Consume los specs desde ahí, nunca definas `tween()` sueltos para transiciones de layout:

```kotlin
val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()   // tamaño/posición/forma
val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>() // color/opacidad
```

- Regla de oro: **tokens "spatial"** (posición, tamaño, forma) permiten rebote/overshoot — dan sensación juguetona. **Tokens "effects"** (color, opacidad) usan damping alto, sin rebote — un color que "rebota" se ve roto. No mezcles los dos.
- Cada mutación de estado visible (cambio de botones en la máquina de estados del pedido, aparición de tarjetas en el pool de viajes, expansión de un bottom sheet) debe animarse con `animateContentSize(animationSpec = spatialSpec)` o el `AnimatedVisibility`/`AnimatedContent` correspondiente usando estos specs — nunca un cambio de visibilidad abrupto (`if (visible) { ... }` sin animación).
- Respeta la preferencia de sistema "reducir movimiento": el motion scheme debe degradar a transiciones cortas/sin rebote cuando esa opción de accesibilidad está activa, no ignorarla.

---

## 6. Layout adaptativo

- Rapidingo debe verse correcto en teléfono compacto, teléfono grande y tablet/foldable, no sólo en un tamaño de referencia. Usa **window size classes** como breakpoints y los building blocks adaptativos de M3 (`NavigationSuiteScaffold` para navegación que cambia entre bottom bar / navigation rail según el ancho disponible).
- Para pantallas con lista + detalle (ej. historial de pedidos + detalle de pedido), considera un patrón `ListDetailPaneScaffold` en vez de forzar siempre navegación a pantalla completa.

---

## 7. Accesibilidad (no negociable)

- Todo elemento interactivo debe tener un área táctil mínima de `48.dp`, incluso si el elemento visual es más pequeño (usa `Modifier.minimumInteractiveComponentSize()`).
- Verifica contraste AA entre texto y superficie tonal en cada combinación nueva de color, especialmente con `surfaceContainerHighest` sobre fondos oscuros.
- Toda animación debe respetar "reducir movimiento" del sistema (ver sección 5).

---

## 8. Prohibiciones explícitas

- ❌ Fondos blancos/grises planos en vez de superficies tonales.
- ❌ `RoundedCornerShape(4.dp)` / `(8.dp)` sueltos fuera de la escala de tema definida en la sección 3.
- ❌ `tween()` o duraciones fijas para transiciones espaciales cuando existe un `motionScheme` disponible.
- ❌ Cambios de visibilidad sin animar (`if/else` directo sobre composables sin `AnimatedVisibility`/`animateContentSize`).
- ❌ Sombras duras (`shadowElevation` alto) en vez de elevación tonal.
- ❌ Círculo o rectángulo por defecto en elementos "hero" cuando la biblioteca de formas expresivas está disponible.
- ❌ Definir `RoundedCornerShape(...)` a mano repetidamente en vez de usar `MaterialTheme.shapes.*`.

---

## 9. Checklist de aplicación por pantalla

Aplica rigurosamente esta línea de diseño en **Dashboard, Carrito, Tracking y Active Trip**:

- **Dashboard**: superficies tonales por sección, `ButtonGroup` para filtros/categorías, tipografía Emphasized en promociones/destacados.
- **Carrito**: monto total en `titleLargeEmphasized` con color primario, botón de checkout tamaño `Large`, `animateContentSize` al agregar/quitar ítems.
- **Tracking**: `LoadingIndicator` con shape morphing mientras se busca conductor/repartidor, transición spatial con overshoot al aparecer la tarjeta de match.
- **Active Trip**: tarjeta de viaje en `surfaceContainerHigh`, ETA y estado en Emphasized, cambios de estado del viaje (recogiendo → en camino → entregado) animados con `AnimatedContent` + specs del `motionScheme`, nunca un salto brusco de texto/ícono.

---

### Referencias para el agente (consultar si hay dudas de API)
- `developer.android.com/develop/ui/compose/designsystems/material3`
- `m3.material.io` (fundamentos: color, tipografía, forma, movimiento)
