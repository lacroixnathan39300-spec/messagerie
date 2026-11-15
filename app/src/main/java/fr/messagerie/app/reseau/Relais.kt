package fr.messagerie.app.reseau

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

// Client du relais internet : annuaire par pseudo et boîtes de messages chiffrés.
// Aucun numéro de téléphone n'est envoyé au serveur.
object Relais {
    private const val BASE = "https://messagerie.roadguard-app.workers.dev"

    // Retourne la réponse JSON, ou null en cas d'échec (réseau, pseudo pris, introuvable...).
    fun requete(methode: String, chemin: String, corps: JSONObject? = null): JSONObject? {
        return try {
            val connexion = URL(BASE + chemin).openConnection() as HttpURLConnection
            connexion.requestMethod = methode
            connexion.connectTimeout = 15000
            connexion.readTimeout = 15000
            if (corps != null) {
                connexion.doOutput = true
                connexion.setRequestProperty("content-type", "application/json")
                connexion.outputStream.use { it.write(corps.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connexion.responseCode
            val flux = if (code in 200..299) connexion.inputStream else connexion.errorStream
            val texte = flux?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
            if (code in 200..299) JSONObject(texte) else null
        } catch (e: Exception) {
            null
        }
    }

    // Identifiant de boîte et jeton secret : générés une fois, sur l'appareil.
    fun aleatoire(octets: Int): String {
        val tirage = ByteArray(octets)
        SecureRandom().nextBytes(tirage)
        return Base64.encodeToString(tirage, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
