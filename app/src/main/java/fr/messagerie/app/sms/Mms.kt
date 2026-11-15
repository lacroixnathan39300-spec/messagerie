package fr.messagerie.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import fr.messagerie.app.reseau.Reseau
import java.io.ByteArrayOutputStream
import java.io.File

// Photos à distance : le contenu chiffré part en MMS, dans une partie texte (celle que les opérateurs
// transmettent sans y toucher). À la réception, c'est l'appli SMS du téléphone qui télécharge le MMS ;
// Messagerie le relit ensuite dans la boîte de réception.
object Mms {
    const val PREFIXE = "RG1M:"

    fun envoyer(context: Context, numero: String, texte: String, idMessage: String): Boolean = try {
        val dossier = File(context.cacheDir, "mms").apply { mkdirs() }
        val fichier = File(dossier, "$idMessage.pdu")
        fichier.writeBytes(composer(numero.filter { it.isDigit() || it == '+' }, texte))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fichiers", fichier)
        // Le service téléphonique du système doit pouvoir lire le fichier à envoyer.
        context.grantUriPermission("com.android.phone", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        SmsManager.getDefault().sendMultimediaMessage(
            context, uri, null, null, Sms.suivi(context, Sms.ACTION_ENVOYE, idMessage),
        )
        true
    } catch (e: Throwable) {
        false
    }

    // Message MMS minimal (M-Send.req) : un destinataire, une partie texte UTF-8.
    private fun composer(numero: String, texte: String): ByteArray {
        val o = ByteArrayOutputStream()
        fun texteAscii(t: String) { o.write(t.toByteArray(Charsets.US_ASCII)); o.write(0) }
        o.write(0x8C); o.write(0x80)                       // type : m-send-req
        o.write(0x98); texteAscii("T" + java.lang.Long.toHexString(System.currentTimeMillis()))
        o.write(0x8D); o.write(0x92)                       // version MMS 1.2
        o.write(0x89); o.write(0x01); o.write(0x81)        // expéditeur : inséré par l'opérateur
        o.write(0x97); texteAscii("$numero/TYPE=PLMN")     // destinataire
        o.write(0x8A); o.write(0x80)                       // classe : personnel
        o.write(0x86); o.write(0x81)                       // pas d'accusé de remise
        o.write(0x90); o.write(0x81)                       // pas d'accusé de lecture
        o.write(0x84); o.write(0xA3)                       // contenu : multipart/mixed
        val donnees = texte.toByteArray(Charsets.UTF_8)
        val entetes = ByteArrayOutputStream()
        entetes.write(0x03); entetes.write(0x83); entetes.write(0x81); entetes.write(0xEA)   // text/plain; charset=utf-8
        entetes.write(0x8E); entetes.write("t.txt".toByteArray(Charsets.US_ASCII)); entetes.write(0)
        entierVariable(o, 1)
        entierVariable(o, entetes.size())
        entierVariable(o, donnees.size)
        o.write(entetes.toByteArray())
        o.write(donnees)
        return o.toByteArray()
    }

    // Entier à longueur variable (7 bits par octet, bit de poids fort = « il y a une suite »).
    private fun entierVariable(o: ByteArrayOutputStream, valeur: Int) {
        val groupes = ArrayList<Int>()
        var v = valeur
        do {
            groupes.add(v and 0x7F)
            v = v ushr 7
        } while (v > 0)
        for (i in groupes.indices.reversed()) o.write(if (i > 0) groupes[i] or 0x80 else groupes[i])
    }

    // Relit les MMS reçus depuis le dernier passage. Renvoie (numéro de l'expéditeur, contenu sans préfixe).
    fun relever(context: Context): List<Pair<String?, String>> {
        val trouves = ArrayList<Pair<String?, String>>()
        try {
            val prefs = context.getSharedPreferences("mms", Context.MODE_PRIVATE)
            val boite = Uri.parse("content://mms/inbox")
            if (!prefs.contains("vu")) {
                // Premier passage : on part du dernier MMS déjà présent, sans relire l'historique.
                var dernier = 0L
                context.contentResolver.query(boite, arrayOf("_id"), null, null, "_id DESC LIMIT 1")?.use { c ->
                    if (c.moveToFirst()) dernier = c.getLong(0)
                } ?: return trouves
                prefs.edit().putLong("vu", dernier).apply()
                return trouves
            }
            var vu = prefs.getLong("vu", 0L)
            val ids = ArrayList<Long>()
            // m_type 132 : MMS entièrement téléchargé.
            context.contentResolver.query(boite, arrayOf("_id"), "_id > ? AND m_type = 132", arrayOf(vu.toString()), "_id ASC")
                ?.use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
            for (id in ids) {
                var texte: String? = null
                context.contentResolver.query(
                    Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text", "_data"), "mid = ?", arrayOf(id.toString()), null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1) != "text/plain") continue
                        var t = c.getString(2)
                        if (t.isNullOrEmpty() && !c.isNull(3)) {
                            t = context.contentResolver.openInputStream(Uri.parse("content://mms/part/" + c.getLong(0)))
                                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        }
                        if (t != null && t.trim().startsWith(PREFIXE)) texte = t.trim().removePrefix(PREFIXE)
                    }
                }
                val contenu = texte
                if (contenu != null) {
                    var expediteur: String? = null
                    // type 137 : adresse de l'expéditeur.
                    context.contentResolver.query(
                        Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type = 137", null, null,
                    )?.use { c -> if (c.moveToFirst()) expediteur = c.getString(0) }
                    trouves.add(expediteur to contenu)
                }
                vu = id
            }
            prefs.edit().putLong("vu", vu).apply()
        } catch (e: Throwable) {
            // Lecture des MMS refusée ou indisponible : rien à relever.
        }
        return trouves
    }
}

// Un MMS vient d'être annoncé : on laisse à l'appli SMS le temps de le télécharger, puis on le relit.
class RecepteurMms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val suite = goAsync()
        val contexte = context.applicationContext
        Thread {
            try {
                for (i in 1..5) {
                    Thread.sleep(5000)
                    Reseau.releverMms(contexte)
                }
            } catch (e: Throwable) {
            } finally {
                suite.finish()
            }
        }.start()
    }
}
