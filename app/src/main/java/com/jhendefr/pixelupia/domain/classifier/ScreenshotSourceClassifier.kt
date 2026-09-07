package com.jhendefr.pixelupia.domain.classifier

import java.util.Locale

object ScreenshotSourceClassifier {

    // Patron estandar: Screenshot_YYYYMMDD_HHMMSS_AppName.ext o Screenshot_YYYYMMDD-HHMMSS_AppName.ext
    private val patternStandard = Regex(
        """^Screenshot[_-]\d{8}[_-]\d{6}[_-]([a-zA-Z0-9_\- ]+)\.[a-zA-Z0-9]+$""",
        RegexOption.IGNORE_CASE
    )

    // Patron con guiones largos: Screenshot_YYYY-MM-DD-HH-MM-SS_AppName.ext
    private val patternDashed = Regex(
        """^Screenshot[_-]\d{4}-\d{2}-\d{2}[_-]\d{2}-\d{2}-\d{2}[_-]([a-zA-Z0-9_\- ]+)\.[a-zA-Z0-9]+$""",
        RegexOption.IGNORE_CASE
    )

    // Patron generico de captura con sufijo de aplicacion
    private val patternGeneric = Regex(
        """^Screenshot.*?[-_]([A-Za-z][a-zA-Z0-9_ ]+)\.[a-zA-Z0-9]+$""",
        RegexOption.IGNORE_CASE
    )

    fun extractSourceApp(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val cleanName = fileName.trim()

        patternStandard.find(cleanName)?.let { match ->
            val rawApp = match.groupValues[1]
            return formatAppName(rawApp)
        }

        patternDashed.find(cleanName)?.let { match ->
            val rawApp = match.groupValues[1]
            return formatAppName(rawApp)
        }

        // Si contiene la palabra Screenshot y un delimitador antes de la extension
        if (cleanName.startsWith("Screenshot", ignoreCase = true)) {
            val nameWithoutExt = cleanName.substringBeforeLast(".")
            val parts = nameWithoutExt.split("_", "-")
            if (parts.size >= 4) {
                val candidate = parts.last().trim()
                // Descartar si el candidato es solo numerico (ej. hora o timestamp)
                if (candidate.isNotEmpty() && !candidate.all { it.isDigit() }) {
                    return formatAppName(candidate)
                }
            }
        }

        return null
    }

    private fun formatAppName(rawName: String): String {
        val cleaned = rawName.replace("_", " ").trim()
        if (cleaned.isBlank()) return "Desconocido"
        return cleaned.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }
}
