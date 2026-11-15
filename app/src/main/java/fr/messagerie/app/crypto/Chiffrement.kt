package fr.messagerie.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Chiffrement de transition : ECDH (P-256) puis AES-256-GCM.
// Ce n'est PAS encore le protocole Signal (pas de renouvellement des clés par message).
// La clé privée n'est jamais stockée en clair : elle est chiffrée par une clé
// conservée dans le coffre-fort Android (Keystore), que l'appli ne peut pas exporter.
object Chiffrement {
    private const val PREFS = "chiffrement"
    private const val ALIAS_MAITRE = "messagerie_cle_maitre"
    private const val TAILLE_IV = 12

    // Clé maîtresse, générée une fois et gardée dans le Keystore Android.
    private fun cleMaitre(): SecretKey {
        val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (magasin.getEntry(ALIAS_MAITRE, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generateur = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generateur.init(
            KeyGenParameterSpec.Builder(
                ALIAS_MAITRE,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generateur.generateKey()
    }

    private fun protegerLocal(clair: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cleMaitre())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(clair), Base64.NO_WRAP)
    }

    private fun ouvrirLocal(protege: String): ByteArray {
        val brut = Base64.decode(protege, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, cleMaitre(), GCMParameterSpec(128, brut.copyOfRange(0, TAILLE_IV)))
        return cipher.doFinal(brut.copyOfRange(TAILLE_IV, brut.size))
    }

    private fun paire(context: Context): KeyPair {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pub = prefs.getString("pub", null)
        val privProtegee = prefs.getString("privProtegee", null)
        val fabrique = KeyFactory.getInstance("EC")
        if (pub != null && privProtegee != null) {
            val clePublique = fabrique.generatePublic(X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP)))
            val clePrivee = fabrique.generatePrivate(PKCS8EncodedKeySpec(ouvrirLocal(privProtegee)))
            return KeyPair(clePublique, clePrivee)
        }
        val generateur = KeyPairGenerator.getInstance("EC")
        generateur.initialize(ECGenParameterSpec("secp256r1"))
        val nouvelle = generateur.generateKeyPair()
        prefs.edit()
            .putString("pub", Base64.encodeToString(nouvelle.public.encoded, Base64.NO_WRAP))
            .putString("privProtegee", protegerLocal(nouvelle.private.encoded))
            .remove("priv")
            .apply()
        return nouvelle
    }

    // Clé publique de cet appareil, à transmettre aux contacts. Elle n'est pas secrète.
    fun clePublique(context: Context): String =
        Base64.encodeToString(paire(context).public.encoded, Base64.NO_WRAP)

    private fun cleCommune(context: Context, cleAutre: String): SecretKeySpec {
        val moi: PrivateKey = paire(context).private
        val autre: PublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.decode(cleAutre, Base64.NO_WRAP)))
        val accord = KeyAgreement.getInstance("ECDH")
        accord.init(moi)
        accord.doPhase(autre, true)
        val secret = accord.generateSecret()
        val cle = MessageDigest.getInstance("SHA-256").digest(secret)
        return SecretKeySpec(cle, "AES")
    }

    // Retourne IV + texte chiffré, en Base64.
    fun chiffrer(context: Context, cleAutre: String, clair: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, cleCommune(context, cleAutre))
        val chiffre = cipher.doFinal(clair.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + chiffre, Base64.NO_WRAP)
    }

    // Retourne null si le déchiffrement échoue (mauvaise clé ou message altéré).
    fun dechiffrer(context: Context, cleAutre: String, codé: String): String? {
        return try {
            val brut = Base64.decode(codé, Base64.NO_WRAP)
            val iv = brut.copyOfRange(0, TAILLE_IV)
            val chiffre = brut.copyOfRange(TAILLE_IV, brut.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, cleCommune(context, cleAutre), GCMParameterSpec(128, iv))
            String(cipher.doFinal(chiffre), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
