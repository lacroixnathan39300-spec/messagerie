package fr.messagerie.coffre

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Types d'éléments rangés dans le coffre.
object Type {
    const val MDP = "mdp"
    const val CARTE = "carte"
    const val VITALE = "vitale"
    const val NOTE = "note"

    fun libelle(t: String) = when (t) {
        MDP -> "Mot de passe"
        CARTE -> "Carte bancaire"
        VITALE -> "Carte Vitale"
        else -> "Note sécurisée"
    }
}

// Un champ d'un élément : un libellé, une valeur, et si elle doit rester masquée (secret).
data class Champ(val libelle: String, val valeur: String, val secret: Boolean = false)

data class Element(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val titre: String,
    val champs: List<Champ>,
    val maj: Long = System.currentTimeMillis(),
)

// Tout le coffre vit en mémoire une fois déverrouillé, et sur le disque uniquement chiffré.
// La clé de chiffrement est DÉRIVÉE du code maître (PBKDF2). Sans le code, les données sont
// illisibles, même en sortant le fichier du téléphone. Rien n'est envoyé nulle part.
object Coffre {
    private const val TOURS = 310_000
    private const val IV = 12
    private const val FICHIER = "coffre.dat"
    const val PREFIXE_PARTAGE = "CFR1:"

    private val alea = SecureRandom()
    private var prefs: SharedPreferences? = null
    private var contexte: Context? = null
    private var cle: SecretKeySpec? = null

    val elements = mutableStateListOf<Element>()
    val ouvert = mutableStateOf(false)

    fun initialiser(c: Context) {
        contexte = c.applicationContext
        prefs = c.applicationContext.getSharedPreferences("coffre", Context.MODE_PRIVATE)
    }

    fun coffreExiste(): Boolean = prefs?.contains("sel") == true

    private fun fichier(): File = File(contexte!!.filesDir, FICHIER)

    private fun deriver(code: String, sel: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(PBEKeySpec(code.toCharArray(), sel, TOURS, 256)).encoded, "AES")
    }

    private fun chiffrer(k: SecretKeySpec, clair: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k)
        return c.iv + c.doFinal(clair)
    }

    private fun dechiffrer(k: SecretKeySpec, brut: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, brut, 0, IV))
        c.doFinal(brut, IV, brut.size - IV)
    } catch (e: Throwable) {
        null
    }

    // Crée le coffre avec un code maître. Renvoie faux si le code est trop court.
    fun creer(code: String): Boolean {
        if (code.length < 4) return false
        val sel = ByteArray(16).also { alea.nextBytes(it) }
        val k = deriver(code, sel)
        prefs?.edit()?.putString("sel", Base64.encodeToString(sel, Base64.NO_WRAP))?.apply()
        cle = k
        elements.clear()
        sauver()
        ouvert.value = true
        return true
    }

    // Tente d'ouvrir le coffre avec un code. Renvoie vrai si le code est bon.
    fun ouvrir(code: String): Boolean {
        val sel = Base64.decode(prefs?.getString("sel", null) ?: return false, Base64.NO_WRAP)
        val k = deriver(code, sel)
        val f = fichier()
        if (!f.exists()) {
            // Coffre déclaré mais fichier absent : on repart sur un coffre vide avec ce code.
            cle = k
            elements.clear()
            sauver()
            ouvert.value = true
            return true
        }
        val clair = dechiffrer(k, f.readBytes()) ?: return false
        cle = k
        elements.clear()
        charger(clair)
        ouvert.value = true
        noterEssai(true)
        return true
    }

    fun verrouiller() {
        cle = null
        elements.clear()
        ouvert.value = false
    }

    private fun charger(clair: ByteArray) {
        try {
            val tableau = JSONArray(String(clair, Charsets.UTF_8))
            for (i in 0 until tableau.length()) elements.add(depuisJson(tableau.getJSONObject(i)))
        } catch (e: Throwable) {
            // Contenu illisible : on garde un coffre vide plutôt que de planter.
        }
    }

    private fun sauver() {
        val k = cle ?: return
        val tableau = JSONArray()
        elements.sortedByDescending { it.maj }.forEach { tableau.put(versJson(it)) }
        fichier().writeBytes(chiffrer(k, tableau.toString().toByteArray(Charsets.UTF_8)))
    }

    fun ajouter(e: Element) {
        elements.add(e)
        sauver()
    }

    fun modifier(e: Element) {
        val i = elements.indexOfFirst { it.id == e.id }
        if (i >= 0) elements[i] = e.copy(maj = System.currentTimeMillis()) else elements.add(e)
        sauver()
    }

    fun supprimer(id: String) {
        elements.removeAll { it.id == id }
        sauver()
    }

    // Change le code maître : on re-chiffre tout le coffre avec la nouvelle clé.
    fun changerCode(ancien: String, nouveau: String): Boolean {
        if (nouveau.length < 4) return false
        val sel = Base64.decode(prefs?.getString("sel", null) ?: return false, Base64.NO_WRAP)
        if (dechiffrer(deriver(ancien, sel), fichier().readBytes()) == null) return false
        val selNeuf = ByteArray(16).also { alea.nextBytes(it) }
        cle = deriver(nouveau, selNeuf)
        prefs?.edit()?.putString("sel", Base64.encodeToString(selNeuf, Base64.NO_WRAP))?.apply()
        sauver()
        return true
    }

    // --- Anti-devinette : délai qui s'allonge après plusieurs codes faux. Jamais d'effacement. ---
    fun attente(): Int {
        val jusqua = prefs?.getLong("bloque", 0L) ?: 0L
        return ((jusqua - System.currentTimeMillis() + 999) / 1000).coerceIn(0L, 3600L).toInt()
    }

    fun noterEssai(reussi: Boolean) {
        val p = prefs ?: return
        if (reussi) {
            p.edit().remove("echecs").remove("bloque").apply()
            return
        }
        val echecs = p.getInt("echecs", 0) + 1
        val e = p.edit().putInt("echecs", echecs)
        if (echecs >= 5) e.putLong("bloque", System.currentTimeMillis() + (15L shl (echecs - 5).coerceAtMost(8)).coerceAtMost(3600L) * 1000)
        e.apply()
    }

    // --- Partage d'un élément par SMS : chiffré avec une phrase secrète convenue entre les deux. ---
    fun emballerPartage(e: Element, phrase: String): String {
        val sel = ByteArray(16).also { alea.nextBytes(it) }
        val k = deriver(phrase, sel)
        val charge = chiffrer(k, versJson(e).toString().toByteArray(Charsets.UTF_8))
        return PREFIXE_PARTAGE + Base64.encodeToString(sel, Base64.NO_WRAP) + "." + Base64.encodeToString(charge, Base64.NO_WRAP)
    }

    // Déchiffre un élément reçu. Renvoie null si la phrase est mauvaise ou le texte abîmé.
    fun ouvrirPartage(texte: String, phrase: String): Element? = try {
        val corps = texte.trim().removePrefix(PREFIXE_PARTAGE)
        val (selB64, chargeB64) = corps.split(".", limit = 2)
        val sel = Base64.decode(selB64, Base64.NO_WRAP)
        val clair = dechiffrer(deriver(phrase, sel), Base64.decode(chargeB64, Base64.NO_WRAP)) ?: return null
        depuisJson(JSONObject(String(clair, Charsets.UTF_8)))
    } catch (e: Throwable) {
        null
    }

    private fun versJson(e: Element): JSONObject {
        val champs = JSONArray()
        e.champs.forEach { champs.put(JSONObject().put("l", it.libelle).put("v", it.valeur).put("s", it.secret)) }
        return JSONObject().put("id", e.id).put("type", e.type).put("titre", e.titre).put("maj", e.maj).put("champs", champs)
    }

    private fun depuisJson(o: JSONObject): Element {
        val champs = ArrayList<Champ>()
        val t = o.getJSONArray("champs")
        for (i in 0 until t.length()) {
            val c = t.getJSONObject(i)
            champs.add(Champ(c.getString("l"), c.getString("v"), c.optBoolean("s", false)))
        }
        return Element(o.optString("id", UUID.randomUUID().toString()), o.getString("type"), o.getString("titre"), champs, o.optLong("maj", System.currentTimeMillis()))
    }

    // Générateur de mot de passe fort.
    fun genererMotDePasse(longueur: Int = 16): String {
        val jeux = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%&*?-_=+"
        return (1..longueur).map { jeux[alea.nextInt(jeux.length)] }.joinToString("")
    }

    fun empreinte(texte: String): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(texte.toByteArray()), Base64.NO_WRAP).take(8)
}
