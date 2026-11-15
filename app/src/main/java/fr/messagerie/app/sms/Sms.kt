package fr.messagerie.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.provider.Telephony
import fr.messagerie.app.crypto.Chiffrement
import fr.messagerie.app.reseau.Reseau

// Transport sans internet : le message chiffré part en SMS, par le réseau de l'opérateur.
// Préfixe RG1: pour reconnaître nos messages parmi les SMS ordinaires.
object Sms {
    const val PREFIXE = "RG1:"
    // Clé publique, envoyée par SMS. Elle n'est pas secrète, mais sa vérification se fait par le code de sécurité.
    const val PREFIXE_CLE = "RG1K:"

    // Invitation en clair à installer l'appli, pour un contact qui ne l'a pas encore.
    fun inviter(numero: String, texte: String) {
        val gestionnaire = SmsManager.getDefault()
        gestionnaire.sendMultipartTextMessage(numero, null, gestionnaire.divideMessage(texte), null, null)
    }

    fun envoyerCle(context: Context, numero: String) {
        val gestionnaire = SmsManager.getDefault()
        val charge = PREFIXE_CLE + Chiffrement.clePublique(context)
        gestionnaire.sendMultipartTextMessage(numero, null, gestionnaire.divideMessage(charge), null, null)
    }

    fun envoyer(context: Context, numero: String, charge: String) {
        val gestionnaire = SmsManager.getDefault()
        val morceaux = gestionnaire.divideMessage(PREFIXE + charge)
        gestionnaire.sendMultipartTextMessage(numero, null, morceaux, null, null)
    }
}

class RecepteurSms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Un SMS long arrive en plusieurs morceaux : on les recolle par expéditeur.
        messages.groupBy { it.originatingAddress ?: "" }.forEach { (expediteur, morceaux) ->
            val corps = morceaux.joinToString("") { it.messageBody ?: "" }
            when {
                corps.startsWith(Sms.PREFIXE_CLE) -> Reseau.recevoirCle(expediteur, corps.removePrefix(Sms.PREFIXE_CLE))
                corps.startsWith(Sms.PREFIXE) -> Reseau.recevoirSms(context, expediteur, corps.removePrefix(Sms.PREFIXE))
            }
        }
    }
}
