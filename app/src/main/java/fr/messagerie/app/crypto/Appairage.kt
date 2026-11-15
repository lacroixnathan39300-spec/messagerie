package fr.messagerie.app.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Liaison par code à 12 chiffres, échangée par SMS.
// Le code n'est jamais envoyé : il sert à dériver une clé (PBKDF2, 200 000 tours) qui sert à
// confirmer les clés publiques. Une personne qui écoute ne peut pas tester le code hors ligne
// sans refaire ces 200 000 tours par essai. Un intermédiaire actif ne peut tenter qu'un code par échange.
// Ce n'est pas un protocole PAKE audité : il est à faire vérifier avant tout usage sensible.
object Appairage {
    private const val TOURS = 200_000
    private val aleatoire = SecureRandom()

    // Code de 12 chiffres, affiché tel quel par groupes de 4.
    fun nouveauCode(): String {
        val chiffres = StringBuilder()
        repeat(12) { chiffres.append(aleatoire.nextInt(10)) }
        return chiffres.toString()
    }

    fun formater(code: String): String = code.chunked(4).joinToString(" ")

    fun normaliser(saisie: String): String = saisie.filter { it.isDigit() }

    private fun derive(code: String, pubA: String, pubB: String): ByteArray {
        val sel = listOf(pubA, pubB).sorted().joinToString("|").toByteArray(Charsets.UTF_8)
        val fabrique = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(code.toCharArray(), sel, TOURS, 256)
        return fabrique.generateSecret(spec).encoded
    }

    // Empreinte de confirmation : prouve la connaissance du code et des deux clés publiques.
    private fun etiquette(k: ByteArray, role: String, pubA: String, pubB: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(k, "HmacSHA256"))
        val octets = mac.doFinal("$role|$pubA|$pubB".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(octets, Base64.NO_WRAP)
    }

    // Côté B (qui a reçu la demande) : calcule la clé et l'étiquette à renvoyer.
    fun repondre(code: String, pubA: String, pubB: String): Pair<ByteArray, String> {
        val k = derive(code, pubA, pubB)
        return k to etiquette(k, "B", pubA, pubB)
    }

    // Côté A (qui a créé le code) : vérifie l'étiquette de B, puis produit la sienne.
    fun verifierEtConfirmer(code: String, pubA: String, pubB: String, etiquetteB: String): String? {
        val k = derive(code, pubA, pubB)
        if (!MessageDigest.isEqual(etiquette(k, "B", pubA, pubB).toByteArray(), etiquetteB.toByteArray())) return null
        return etiquette(k, "A", pubA, pubB)
    }

    // Côté B : vérifie la confirmation finale de A.
    fun verifierConfirmation(k: ByteArray, pubA: String, pubB: String, etiquetteA: String): Boolean =
        MessageDigest.isEqual(etiquette(k, "A", pubA, pubB).toByteArray(), etiquetteA.toByteArray())

    fun cleDerivee(code: String, pubA: String, pubB: String): ByteArray = derive(code, pubA, pubB)
}
