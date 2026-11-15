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
    // Liaison par code : demande (clé de A), réponse (clé de B + confirmation), confirmation (A).
    const val PREFIXE_P1 = "RG1P1:"
    const val PREFIXE_P2 = "RG1P2:"
    const val PREFIXE_P3 = "RG1P3:"

    // Renvoie faux si le SMS n'a pas pu partir (permission refusée, pas de carte SIM...).
    private fun expedier(numero: String, texte: String): Boolean = try {
        val gestionnaire = SmsManager.getDefault()
        gestionnaire.sendMultipartTextMessage(numero, null, gestionnaire.divideMessage(texte), null, null)
        true
    } catch (e: Exception) {
        false
    }

    fun envoyerTexte(numero: String, texte: String): Boolean = expedier(numero, texte)

    // Invitation en clair à installer l'appli, pour un contact qui ne l'a pas encore.
    fun inviter(numero: String, texte: String): Boolean = expedier(numero, texte)

    fun envoyerCle(context: Context, numero: String): Boolean =
        expedier(numero, PREFIXE_CLE + Chiffrement.clePublique(context))

    fun envoyer(context: Context, numero: String, charge: String): Boolean = expedier(numero, PREFIXE + charge)
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
