package fr.messagerie.app.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import fr.messagerie.app.crypto.Chiffrement
import fr.messagerie.app.reseau.Reseau
import fr.messagerie.app.reseau.Statut

// Transport sans internet : le message chiffré part en SMS, par le réseau de l'opérateur.
// Préfixe RG1: pour reconnaître nos messages parmi les SMS ordinaires.
object Sms {
    const val PREFIXE = "RG1:"
    // Clé publique, envoyée par SMS. Elle n'est pas secrète, mais sa vérification se fait par le code de sécurité.
    const val PREFIXE_CLE = "RG1K:"
    // Accusé de lecture (chiffré).
    const val PREFIXE_LU = "RG1L:"
    // Liaison par code : demande (clé de A), réponse (clé de B + confirmation), confirmation (A).
    const val PREFIXE_P1 = "RG1P1:"
    const val PREFIXE_P2 = "RG1P2:"
    const val PREFIXE_P3 = "RG1P3:"

    const val ACTION_ENVOYE = "fr.messagerie.app.SMS_ENVOYE"
    const val ACTION_REMIS = "fr.messagerie.app.SMS_REMIS"

    internal fun suivi(context: Context, action: String, idMessage: String): PendingIntent {
        val intent = Intent(context, SuiviSms::class.java)
            .setAction(action)
            .setData(Uri.parse("messagerie://suivi/$action/$idMessage"))
            .putExtra("id", idMessage)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Renvoie faux si le SMS n'a pas pu partir (permission refusée, pas de carte SIM...).
    // Avec un identifiant de message, l'opérateur nous prévient quand le SMS est parti puis remis.
    private fun expedier(numero: String, texte: String, context: Context? = null, idMessage: String? = null): Boolean = try {
        val gestionnaire = SmsManager.getDefault()
        val morceaux = gestionnaire.divideMessage(texte)
        if (context != null && idMessage != null) {
            val envois = ArrayList<PendingIntent?>()
            val remises = ArrayList<PendingIntent?>()
            for (i in morceaux.indices) {
                val dernier = i == morceaux.size - 1
                envois.add(if (dernier) suivi(context, ACTION_ENVOYE, idMessage) else null)
                remises.add(if (dernier) suivi(context, ACTION_REMIS, idMessage) else null)
            }
            gestionnaire.sendMultipartTextMessage(numero, null, morceaux, envois, remises)
        } else {
            gestionnaire.sendMultipartTextMessage(numero, null, morceaux, null, null)
        }
        true
    } catch (e: Exception) {
        false
    }

    fun envoyerTexte(numero: String, texte: String): Boolean = expedier(numero, texte)

    // Invitation en clair à installer l'appli, pour un contact qui ne l'a pas encore.
    fun inviter(numero: String, texte: String): Boolean = expedier(numero, texte)

    fun envoyerCle(context: Context, numero: String): Boolean =
        expedier(numero, PREFIXE_CLE + Chiffrement.clePublique(context))

    fun envoyer(context: Context, numero: String, charge: String, idMessage: String): Boolean =
        expedier(numero, PREFIXE + charge, context, idMessage)
}

class RecepteurSms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // L'appli peut être fermée : on recharge contacts et messages avant de traiter le SMS.
        try {
            Reseau.initialiser(context)
        } catch (e: Exception) {
            return
        }
        // Un SMS long arrive en plusieurs morceaux : on les recolle par expéditeur.
        messages.groupBy { it.originatingAddress ?: "" }.forEach { (expediteur, morceaux) ->
            val corps = morceaux.joinToString("") { it.messageBody ?: "" }
            try {
                when {
                    corps.startsWith(Sms.PREFIXE_CLE) -> Reseau.recevoirCle(expediteur, corps.removePrefix(Sms.PREFIXE_CLE))
                    corps.startsWith(Sms.PREFIXE_LU) -> Reseau.recevoirLu(expediteur, corps.removePrefix(Sms.PREFIXE_LU))
                    corps.startsWith(Sms.PREFIXE_P1) -> Reseau.recevoirP1(expediteur, corps.removePrefix(Sms.PREFIXE_P1))
                    corps.startsWith(Sms.PREFIXE_P2) -> Reseau.recevoirP2(expediteur, corps.removePrefix(Sms.PREFIXE_P2))
                    corps.startsWith(Sms.PREFIXE_P3) -> Reseau.recevoirP3(expediteur, corps.removePrefix(Sms.PREFIXE_P3))
                    corps.startsWith(Sms.PREFIXE) -> Reseau.recevoirSms(context, expediteur, corps.removePrefix(Sms.PREFIXE))
                }
            } catch (e: Exception) {
                // SMS mal formé : ignoré, sans faire planter le récepteur.
            }
        }
    }
}

// Comptes rendus de l'opérateur : SMS parti, puis remis au téléphone du destinataire.
class SuiviSms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        try {
            Reseau.initialiser(context)
            when (intent.action) {
                Sms.ACTION_ENVOYE ->
                    Reseau.majStatut(id, if (resultCode == Activity.RESULT_OK) Statut.ENVOYE else Statut.ECHEC)
                Sms.ACTION_REMIS -> {
                    val pdu = intent.getByteArrayExtra("pdu")
                    val format = intent.getStringExtra("format")
                    // Sans détail lisible, le compte rendu lui-même vaut remise.
                    val remis = if (pdu == null) true else SmsMessage.createFromPdu(pdu, format).status == Telephony.Sms.STATUS_COMPLETE
                    if (remis) Reseau.majStatut(id, Statut.REMIS)
                }
            }
        } catch (e: Exception) {
            // Compte rendu illisible : l'état du message reste tel quel.
        }
    }
}
