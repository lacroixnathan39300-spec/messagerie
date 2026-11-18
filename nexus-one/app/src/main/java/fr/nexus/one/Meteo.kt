package fr.nexus.one

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** Résultat météo affiché par le widget. */
data class MeteoActuelle(val ville: String, val temperature: Double, val description: String)

private fun lireUrl(adresse: String): String {
    val connexion = URL(adresse).openConnection() as HttpURLConnection
    connexion.connectTimeout = 10_000
    connexion.readTimeout = 10_000
    return try {
        connexion.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connexion.disconnect()
    }
}

/**
 * Météo actuelle via Open-Meteo (gratuit, sans clé).
 * À appeler hors du fil principal. Renvoie null si la ville est introuvable ou sans réseau.
 */
fun chargerMeteo(ville: String): MeteoActuelle? = try {
    val nom = URLEncoder.encode(ville.trim(), "UTF-8")
    val geo = JSONObject(lireUrl("https://geocoding-api.open-meteo.com/v1/search?name=$nom&count=1&language=fr&format=json"))
    val resultats = geo.optJSONArray("results")
    if (resultats == null || resultats.length() == 0) {
        null
    } else {
        val lieu = resultats.getJSONObject(0)
        val lat = lieu.getDouble("latitude")
        val lon = lieu.getDouble("longitude")
        val prevision = JSONObject(lireUrl("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"))
        val actuel = prevision.getJSONObject("current")
        MeteoActuelle(
            ville = lieu.optString("name", ville),
            temperature = actuel.getDouble("temperature_2m"),
            description = decrireCode(actuel.getInt("weather_code")),
        )
    }
} catch (e: Exception) {
    null
}

/** Codes météo WMO utilisés par Open-Meteo. */
fun decrireCode(code: Int): String = when (code) {
    0 -> "Ciel dégagé"
    1, 2 -> "Peu nuageux"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    51, 53, 55, 56, 57 -> "Bruine"
    61, 63, 65, 66, 67 -> "Pluie"
    71, 73, 75, 77 -> "Neige"
    80, 81, 82 -> "Averses"
    85, 86 -> "Averses de neige"
    95, 96, 99 -> "Orage"
    else -> "Inconnu"
}
