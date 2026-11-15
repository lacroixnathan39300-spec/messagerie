package fr.messagerie.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import fr.messagerie.app.crypto.Chiffrement
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.UUID

// Photos et vidéos : préparation avant envoi et stockage chiffré sur le téléphone.
// Chaque fichier a sa propre clé, gardée avec le message (lui-même protégé par le Keystore).
object Media {
    const val PHOTO = 1
    const val VIDEO = 2

    // Taille maximale d'une vidéo en Wi-Fi local, et d'un contenu envoyé par MMS.
    const val MAX_VIDEO = 15 * 1024 * 1024
    const val MAX_MMS = 190 * 1024

    private fun dossier(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    // Enregistre le contenu chiffré. Renvoie (identifiant du fichier, clé en Base64).
    fun garder(context: Context, octets: ByteArray): Pair<String, String> {
        val id = UUID.randomUUID().toString()
        val cle = ByteArray(32).also { SecureRandom().nextBytes(it) }
        File(dossier(context), id).writeBytes(Chiffrement.chiffrerAvecCle(cle, octets))
        return id to Base64.encodeToString(cle, Base64.NO_WRAP)
    }

    fun lire(context: Context, id: String, cle: String): ByteArray? = try {
        Chiffrement.dechiffrerAvecCle(Base64.decode(cle, Base64.NO_WRAP), File(dossier(context), id).readBytes())
    } catch (e: Throwable) {
        null
    }

    fun supprimer(context: Context, id: String) {
        try { File(dossier(context), id).delete() } catch (e: Exception) { }
    }

    // Supprime les fichiers qui ne correspondent plus à aucun message.
    fun nettoyer(context: Context, gardes: Set<String>) {
        try {
            dossier(context).listFiles()?.forEach { if (it.name !in gardes) it.delete() }
            File(context.cacheDir, "lecture").listFiles()?.forEach { it.delete() }
            File(context.cacheDir, "mms").listFiles()?.forEach { it.delete() }
        } catch (e: Exception) { }
    }

    // Copie déchiffrée et provisoire d'une vidéo, le temps de la lire.
    fun fichierLecture(context: Context, id: String, cle: String): File? = try {
        val octets = lire(context, id, cle)
        if (octets == null) null else {
            val dossierLecture = File(context.cacheDir, "lecture").apply { mkdirs() }
            File(dossierLecture, "$id.mp4").also { it.writeBytes(octets) }
        }
    } catch (e: Throwable) {
        null
    }

    fun estVideo(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video/") == true

    // Lit un fichier entier, ou null s'il dépasse la taille permise.
    fun brut(context: Context, uri: Uri, maximum: Int): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { flux ->
            val sortie = ByteArrayOutputStream()
            val tampon = ByteArray(64 * 1024)
            var trop = false
            while (true) {
                val n = flux.read(tampon)
                if (n < 0) break
                sortie.write(tampon, 0, n)
                if (sortie.size() > maximum) { trop = true; break }
            }
            if (trop) null else sortie.toByteArray()
        }
    } catch (e: Throwable) {
        null
    }

    // Photo réduite et compressée en JPEG : plus grand côté `cote`, poids inférieur à `maximum`.
    fun photo(context: Context, uri: Uri, cote: Int, qualite: Int, maximum: Int): ByteArray? = try {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bornes) }
        var pas = 1
        while (maxOf(bornes.outWidth, bornes.outHeight) / (pas * 2) >= cote) pas *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = pas }
        var image = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (image == null) null else {
            // Les photos gardent leur sens : on applique la rotation notée par l'appareil photo.
            val angle = try {
                context.contentResolver.openInputStream(uri)?.use { flux ->
                    when (ExifInterface(flux).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            image = ajuster(image, cote, angle)
            var q = qualite
            var octets = compresser(image, q)
            var essais = 0
            while (octets.size > maximum && essais < 12) {
                if (q > 40) q -= 10 else image = ajuster(image, (maxOf(image!!.width, image.height) * 3) / 4, 0f)
                octets = compresser(image!!, q)
                essais++
            }
            if (octets.size > maximum) null else octets
        }
    } catch (e: Throwable) {
        null
    }

    private fun ajuster(image: Bitmap, cote: Int, angle: Float): Bitmap {
        val plusGrand = maxOf(image.width, image.height)
        val echelle = if (plusGrand > cote) cote.toFloat() / plusGrand else 1f
        if (echelle == 1f && angle == 0f) return image
        val matrice = Matrix().apply {
            postScale(echelle, echelle)
            postRotate(angle)
        }
        return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrice, true)
    }

    private fun compresser(image: Bitmap, qualite: Int): ByteArray {
        val sortie = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, qualite, sortie)
        return sortie.toByteArray()
    }

    // Image décodée pour l'affichage, réduite pour ne pas saturer la mémoire.
    fun image(octets: ByteArray, cote: Int): Bitmap? = try {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
        var pas = 1
        while (maxOf(bornes.outWidth, bornes.outHeight) / (pas * 2) >= cote) pas *= 2
        BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = pas })
    } catch (e: Throwable) {
        null
    }
}
