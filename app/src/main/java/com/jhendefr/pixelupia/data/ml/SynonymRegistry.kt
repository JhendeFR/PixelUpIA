package com.jhendefr.pixelupia.data.ml

import java.util.Locale

object SynonymRegistry {

    private val synonymMap: Map<String, List<String>> = mapOf(
        // Personas y Rostros
        "persona" to listOf("persona", "hombre", "mujer", "humano", "gente", "individuo", "sujeto", "rostro", "cara"),
        "personas" to listOf("personas", "hombres", "mujeres", "humanos", "gente", "individuos", "multitud", "grupo"),
        "rostro" to listOf("rostro", "cara", "facciones", "semblante", "persona", "perfil", "selfie", "retrato"),
        "hombre" to listOf("hombre", "varon", "chico", "senor", "muchacho", "persona", "caballero", "tipo"),
        "mujer" to listOf("mujer", "dama", "senora", "chica", "muchacha", "persona", "femenina"),
        "bebe" to listOf("bebe", "nino", "recien nacido", "infante", "criatura", "pequeno", "bebecito"),
        "nino" to listOf("nino", "chico", "pequeno", "infante", "menor", "muchacho", "chiquillo"),
        "selfie" to listOf("selfie", "autoretrato", "foto", "rostro", "primer plano", "retrato"),

        // Animales y Mascotas
        "perro" to listOf("perro", "can", "perrito", "cachorro", "canino", "sabueso", "lomito", "mascota", "dog"),
        "perros" to listOf("perros", "canes", "perritos", "cachorros", "caninos", "mascotas", "lomitos", "dogs"),
        "gato" to listOf("gato", "felino", "minino", "michi", "gatito", "mascota", "animal", "cat"),
        "gatos" to listOf("gatos", "felinos", "mininos", "michis", "gatitos", "mascotas", "cats"),
        "mascota" to listOf("mascota", "animal", "perro", "gato", "companero", "cachorro", "domestico"),
        "ave" to listOf("ave", "pajaro", "pajarito", "plumaje", "volador", "fauna", "alas"),
        "pajaro" to listOf("pajaro", "ave", "pajarito", "plumaje", "volador", "fauna", "alas"),
        "caballo" to listOf("caballo", "corcel", "yegua", "potro", "equino", "animal"),

        // Vehiculos y Transporte
        "automovil" to listOf("automovil", "auto", "coche", "carro", "vehiculo", "camioneta", "sedan", "movilidad", "transporte"),
        "automoviles" to listOf("automoviles", "autos", "coches", "carros", "vehiculos", "camionetas", "sedanes", "transporte"),
        "vehiculo" to listOf("vehiculo", "transporte", "auto", "coche", "carro", "movilidad", "camioneta"),
        "motocicleta" to listOf("motocicleta", "moto", "ciclomotor", "scooter", "biciclo", "vehiculo"),
        "bicicleta" to listOf("bicicleta", "bici", "cicla", "pedal", "vehiculo", "ciclismo"),
        "avion" to listOf("avion", "aeronave", "aeroplano", "vuelo", "aviacion", "transporte", "jet"),
        "barco" to listOf("barco", "embarcacion", "buque", "bote", "lancha", "navio", "velero", "maritimo"),

        // Vestimenta y Moda
        "camisa" to listOf("camisa", "remera", "playera", "blusa", "polo", "camiseta", "prenda", "ropa", "vestimenta"),
        "pantalones" to listOf("pantalones", "pantalon", "jeans", "vaqueros", "bermudas", "prenda", "ropa", "vestimenta"),
        "vestido" to listOf("vestido", "traje", "bata", "prenda", "ropa", "atuendo", "vestimenta", "moda"),
        "ropa" to listOf("ropa", "prenda", "vestimenta", "atuendo", "indumentaria", "moda", "textil"),
        "zapato" to listOf("zapato", "calzado", "zapatillas", "tenis", "botas", "sandalias", "calzados", "sneakers"),
        "calzado" to listOf("calzado", "zapato", "zapatillas", "tenis", "botas", "sandalias", "calzados"),
        "gafas" to listOf("gafas", "lentes", "anteojos", "gafas de sol", "cristales", "montura", "optica"),
        "sombrero" to listOf("sombrero", "gorra", "gorro", "boina", "pamela", "tocado", "visera"),

        // Documentos, Pagos y Codigos
        "documento" to listOf("documento", "credencial", "identificacion", "dni", "cedula", "pasaporte", "papel", "tramite", "certificado", "archivo"),
        "documentos" to listOf("documentos", "credenciales", "identificaciones", "dnis", "cedulas", "pasaportes", "papeles", "certificados"),
        "factura" to listOf("factura", "recibo", "comprobante", "pago", "ticket", "boleta", "invoice", "cobro", "transaccion", "cuenta"),
        "recibo" to listOf("recibo", "factura", "comprobante", "ticket", "boleta", "pago", "comprobante de pago", "invoice"),
        "dinero" to listOf("dinero", "plata", "efectivo", "billete", "moneda", "pago", "importe", "monto", "tarjeta", "saldo"),
        "tarjeta" to listOf("tarjeta", "credito", "debito", "plastico", "banco", "pago", "visa", "mastercard"),
        "codigos qr" to listOf("codigos qr", "codigo qr", "qr", "codigo", "barcode", "codigo de barras", "scan", "escaner"),
        "codigo qr" to listOf("codigo qr", "qr", "codigo", "barcode", "codigo de barras", "scan", "escaner"),

        // Tecnologia y Dispositivos
        "telefono inteligente" to listOf("telefono inteligente", "celular", "telefono", "smartphone", "movil", "dispositivo", "aparato"),
        "dispositivo" to listOf("dispositivo", "celular", "telefono", "smartphone", "aparato", "tecnologia", "gadget"),
        "computadora" to listOf("computadora", "laptop", "pc", "ordenador", "portatil", "equipo", "pantalla", "tecnologia"),
        "captura de pantalla" to listOf("captura de pantalla", "screenshot", "captura", "pantallazo", "pantalla", "screen"),
        "capturas" to listOf("capturas", "screenshots", "capturas de pantalla", "pantallazos", "screens"),

        // Naturaleza y Lugares
        "playa" to listOf("playa", "mar", "costa", "arena", "orilla", "oceano", "balneario", "verano"),
        "montana" to listOf("montana", "cerro", "cordillera", "colina", "cumbre", "pico", "naturaleza", "senderismo"),
        "naturaleza" to listOf("naturaleza", "paisaje", "campo", "aire libre", "verde", "flora", "bosque", "silvestre"),
        "flor" to listOf("flor", "flores", "planta", "petalo", "ramo", "jardin", "flora", "botanica"),
        "flores" to listOf("flores", "flor", "plantas", "petalos", "ramos", "jardin", "flora", "botanica"),
        "edificio" to listOf("edificio", "construccion", "arquitectura", "inmueble", "torre", "estructura", "casa"),
        "casa" to listOf("casa", "hogar", "vivienda", "residencia", "inmueble", "propiedad", "habitacion"),

        // Comida y Gastronomia
        "comida" to listOf("comida", "alimento", "plato", "gastronomia", "almuerzo", "cena", "receta", "manjar", "restaurante"),
        "cafe" to listOf("cafe", "cafecito", "capuchino", "infusion", "expreso", "taza", "desayuno"),
        "pizza" to listOf("pizza", "comida", "masa", "queso", "cena", "italiana", "porcion"),
        "pastel" to listOf("pastel", "torta", "tarta", "dulce", "postre", "bizcocho", "cumpleanos"),

        // Eventos y Celebraciones
        "boda" to listOf("boda", "casamiento", "matrimonio", "ceremonia", "fiesta", "nupcias", "celebracion", "pareja"),
        "fiesta" to listOf("fiesta", "evento", "celebracion", "reunion", "agasajo", "festejo", "salida", "diversion", "party"),
        "concierto" to listOf("concierto", "recital", "musica", "show", "espectaculo", "banda", "festival"),
        "navidad" to listOf("navidad", "nochebuena", "navideno", "arbolito", "regalos", "fiestas", "diciembre"),

        // Colores y Variantes
        "azul" to listOf("azul", "celeste", "marino", "turquesa", "blue"),
        "celeste" to listOf("celeste", "azul claro", "cian", "azul"),
        "rojo" to listOf("rojo", "carmesi", "escarlata", "bordo", "vinotinto", "red"),
        "verde" to listOf("verde", "oliva", "esmeralda", "green"),
        "amarillo" to listOf("amarillo", "dorado", "mostaza", "yellow"),
        "negro" to listOf("negro", "oscuro", "black"),
        "blanco" to listOf("blanco", "claro", "crema", "white"),
        "gris" to listOf("gris", "plateado", "plomo", "gray", "grey"),
        "marron" to listOf("marron", "cafe", "pardo", "castano", "brown"),
        "naranja" to listOf("naranja", "anaranjado", "orange"),
        "rosa" to listOf("rosa", "rosado", "fucsia", "pink"),
        "violeta" to listOf("violeta", "morado", "purpura", "lila", "purple")
    )

    private fun removeAccents(text: String): String {
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}"), "")
    }

    fun getSynonyms(term: String): List<String> {
        val lower = removeAccents(term.trim().lowercase(Locale.ROOT))
        return synonymMap[lower] ?: emptyList()
    }

    /**
     * Expande una cadena para el indice searchContent, inyectando de 4 a 10 sinonimos.
     */
    fun expandForSearchContent(label: String): String {
        val cleanTerm = removeAccents(
            label.substringBefore(" -").substringBefore("(").trim().lowercase(Locale.ROOT)
        )
        val synonyms = synonymMap[cleanTerm]
        return if (!synonyms.isNullOrEmpty()) {
            "$label ${synonyms.joinToString(" ")}"
        } else {
            label
        }
    }

    /**
     * Expande una consulta FTS del usuario buscando sinonimos de cada token.
     */
    fun expandQuery(tokens: List<String>): List<String> {
        val expandedTokens = mutableListOf<String>()
        for (token in tokens) {
            expandedTokens.add(token)
            val lower = removeAccents(token.lowercase(Locale.ROOT))
            synonymMap[lower]?.let { list ->
                expandedTokens.addAll(list.take(6))
            }
        }
        return expandedTokens.distinct()
    }

    /**
     * Expande un par compuesto objeto-color (ej. "camisa", "azul") generando variaciones
     * con los sinonimos de dicho objeto y el color para enriquecer searchContent e indexar
     * busquedas como "camisa azul", "remera azul", "playera azul", etc.
     */
    fun expandCompound(objectLabel: String, color: String): List<String> {
        val synonyms = getSynonyms(objectLabel).ifEmpty { listOf(objectLabel) }
        val cleanColor = color.trim().lowercase(Locale.ROOT)
        return synonyms.map { "$it $cleanColor" }
    }
}
