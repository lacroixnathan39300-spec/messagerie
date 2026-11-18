package fr.nexus.one

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Une notification affichée dans la liste du launcher. */
data class NotifResume(val cle: String, val paquet: String, val titre: String, val texte: String)

/**
 * Service qui lit les notifications (avec l'accord de l'utilisateur dans les paramètres Android).
 * Il ne fait que compter et lister : rien n'est envoyé ni enregistré.
 */
class NotifService : NotificationListenerService() {
    override fun onListenerConnected() = rafraichir()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = rafraichir()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = rafraichir()
    override fun onListenerDisconnected() {
        etat.value = emptyList()
        instance = null
    }

    private fun rafraichir() {
        instance = this
        val actives = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        etat.value = actives
            .filter { it.isClearable && it.packageName != packageName }
            .map {
                val extras = it.notification.extras
                NotifResume(
                    cle = it.key,
                    paquet = it.packageName,
                    titre = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    texte = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                )
            }
    }

    companion object {
        private val etat = MutableStateFlow<List<NotifResume>>(emptyList())
        val notifications: StateFlow<List<NotifResume>> = etat
        private var instance: NotifService? = null

        fun effacer(cle: String) {
            try { instance?.cancelNotification(cle) } catch (e: Exception) { }
        }

        fun toutEffacer() {
            try { instance?.cancelAllNotifications() } catch (e: Exception) { }
        }
    }
}

/** Vrai si l'utilisateur a autorisé le launcher à lire les notifications. */
fun accesNotificationsAccorde(context: Context): Boolean {
    val liste = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return liste.contains(context.packageName)
}

fun ouvrirReglageAccesNotifications(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
