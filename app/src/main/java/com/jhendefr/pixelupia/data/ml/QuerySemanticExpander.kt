package com.jhendefr.pixelupia.data.ml

import java.util.Locale

/**
 * Expansor y traductor semantico bilingue para MobileCLIP-S0.
 * Dado que MobileCLIP fue entrenado principalmente sobre datasets multimodales en ingles,
 * este componente transforma consultas en lenguaje natural (ej: "automovil rojo", "camisa azul", "perro negro")
 * en estructuras sintacticas optimas en ingles (ej: "red car", "a photo of a red car") para maximizar
 * la similitud coseno y la precision en la recuperacion cross-modal.
 */
object QuerySemanticExpander {

    private val colorMap: Map<String, String> = mapOf(
        "rojo" to "red", "roja" to "red", "rojos" to "red", "rojas" to "red", "carmesi" to "red", "escarlata" to "red",
        "azul" to "blue", "azules" to "blue", "celeste" to "light blue", "marino" to "dark blue", "turquesa" to "turquoise",
        "verde" to "green", "verdes" to "green", "oliva" to "olive green", "esmeralda" to "emerald green",
        "amarillo" to "yellow", "amarilla" to "yellow", "amarillos" to "yellow", "amarillas" to "yellow", "dorado" to "golden",
        "negro" to "black", "negra" to "black", "negros" to "black", "negras" to "black", "oscuro" to "dark",
        "blanco" to "white", "blanca" to "white", "blancos" to "white", "blancas" to "white", "claro" to "light",
        "gris" to "gray", "grises" to "gray", "plateado" to "silver", "plomo" to "gray",
        "marron" to "brown", "marrones" to "brown", "cafe" to "brown", "castano" to "brown", "pardo" to "brown",
        "naranja" to "orange", "anaranjado" to "orange", "anaranjada" to "orange",
        "rosa" to "pink", "rosado" to "pink", "rosada" to "pink", "fucsia" to "magenta",
        "morado" to "purple", "morada" to "purple", "violeta" to "purple", "purpura" to "purple", "lila" to "lilac"
    )

    private val objectMap: Map<String, String> = mapOf(
        // Vehiculos
        "automovil" to "car", "auto" to "car", "coche" to "car", "carro" to "car", "vehiculo" to "vehicle",
        "automoviles" to "cars", "autos" to "cars", "coches" to "cars", "carros" to "cars", "vehiculos" to "vehicles",
        "camioneta" to "suv truck", "camionetas" to "trucks", "camion" to "truck", "camiones" to "trucks",
        "motocicleta" to "motorcycle", "moto" to "motorcycle", "motos" to "motorcycles",
        "bicicleta" to "bicycle", "bici" to "bike", "bicicletas" to "bicycles",
        "avion" to "airplane", "aviones" to "airplanes", "barco" to "boat", "barcos" to "boats",

        // Ropa y Moda
        "camisa" to "shirt", "camisas" to "shirts", "remera" to "t-shirt", "remeras" to "t-shirts",
        "playera" to "t-shirt", "playeras" to "t-shirts", "camiseta" to "t-shirt", "camisetas" to "t-shirts",
        "polo" to "polo shirt", "blusa" to "blouse",
        "pantalon" to "pants", "pantalones" to "pants", "jeans" to "jeans", "vaqueros" to "jeans",
        "vestido" to "dress", "vestidos" to "dresses", "falda" to "skirt", "faldas" to "skirts",
        "zapato" to "shoes", "zapatos" to "shoes", "zapatilla" to "sneakers", "zapatillas" to "sneakers", "tenis" to "sneakers",
        "gorra" to "cap", "sombrero" to "hat", "chaqueta" to "jacket", "abrigo" to "coat", "sueter" to "sweater",
        "gafas" to "glasses", "lentes" to "glasses", "anteojos" to "glasses", "lentes de sol" to "sunglasses",

        // Personas
        "persona" to "person", "personas" to "people", "gente" to "people",
        "hombre" to "man", "hombres" to "men", "varon" to "man",
        "mujer" to "woman", "mujeres" to "women", "dama" to "woman",
        "chico" to "boy", "chicos" to "boys", "nino" to "boy", "ninos" to "children", "nino/a" to "child",
        "chica" to "girl", "chicas" to "girls", "nina" to "girl", "ninas" to "girls",
        "bebe" to "baby", "bebes" to "babies",

        // Animales
        "perro" to "dog", "perros" to "dogs", "perrito" to "puppy", "perritos" to "puppies", "cachorro" to "puppy",
        "gato" to "cat", "gatos" to "cats", "gatito" to "kitten", "gatitos" to "kittens", "michi" to "cat",
        "mascota" to "pet", "mascotas" to "pets", "ave" to "bird", "pajaro" to "bird", "caballo" to "horse",

        // Lugares y Naturaleza
        "playa" to "beach", "mar" to "sea ocean", "costa" to "coast",
        "montana" to "mountain", "montanas" to "mountains", "cerro" to "hill",
        "bosque" to "forest", "arbol" to "tree", "arboles" to "trees", "planta" to "plant", "plantas" to "plants",
        "flor" to "flower", "flores" to "flowers", "jardin" to "garden",
        "cielo" to "sky", "atardecer" to "sunset", "amanecer" to "sunrise", "nubes" to "clouds",
        "edificio" to "building", "edificios" to "buildings", "casa" to "house", "casas" to "houses",
        "ciudad" to "city street", "calle" to "street",

        // Comida
        "comida" to "food meal", "alimento" to "food", "plato" to "dish food", "cena" to "dinner", "almuerzo" to "lunch",
        "pizza" to "pizza", "hamburguesa" to "burger", "cafe" to "coffee", "pastel" to "cake", "postre" to "dessert",

        // Documentos y Finanzas
        "documento" to "document paper", "documentos" to "documents", "factura" to "invoice receipt", "recibo" to "receipt",
        "comprobante" to "payment receipt", "pago" to "payment receipt", "dinero" to "money cash", "tarjeta" to "credit card",

        // Tecnologia
        "telefono" to "phone smartphone", "celular" to "mobile phone", "smartphone" to "smartphone",
        "computadora" to "computer laptop", "laptop" to "laptop", "pantalla" to "screen monitor"
    )

    private fun removeAccents(text: String): String {
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}"), "")
    }

    /**
     * Genera variantes enriquecidas de consulta en espanol e ingles optimizadas para MobileCLIP.
     */
    fun generateClipPrompts(query: String): List<String> {
        val clean = query.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return emptyList()

        val cleanNoAccents = removeAccents(clean)
        val prompts = linkedSetOf<String>()

        // 1. Consulta original y sin tildes
        prompts.add(clean)
        if (cleanNoAccents != clean) {
            prompts.add(cleanNoAccents)
        }

        val words = cleanNoAccents.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }

        // 2. Deteccion de pares sintacticos [objeto] + [color] o [color] + [objeto]
        var detectedColorEn: String? = null
        var detectedObjectEn: String? = null

        for (w in words) {
            if (detectedColorEn == null && colorMap.containsKey(w)) {
                detectedColorEn = colorMap[w]
            }
            if (detectedObjectEn == null && objectMap.containsKey(w)) {
                detectedObjectEn = objectMap[w]
            }
        }

        if (detectedColorEn != null && detectedObjectEn != null) {
            val englishCompound = "$detectedColorEn $detectedObjectEn"
            prompts.add(englishCompound)
            prompts.add("a photo of a $englishCompound")
            prompts.add("a $englishCompound")
            prompts.add("a photo of $englishCompound")
        } else if (detectedObjectEn != null) {
            prompts.add(detectedObjectEn)
            prompts.add("a photo of a $detectedObjectEn")
            prompts.add("a photo of $detectedObjectEn")
        } else if (detectedColorEn != null) {
            prompts.add(detectedColorEn)
            prompts.add("a photo of something $detectedColorEn")
        }

        // 3. Traduccion palabra por palabra directa si hay multiples palabras
        if (words.size > 1) {
            val translatedWords = words.map { word ->
                colorMap[word] ?: objectMap[word] ?: word
            }
            val joined = translatedWords.joinToString(" ")
            if (joined != clean && joined != cleanNoAccents) {
                prompts.add(joined)
                prompts.add("a photo of $joined")
            }
        }

        return prompts.toList()
    }
}
