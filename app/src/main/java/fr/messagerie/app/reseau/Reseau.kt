package fr.messagerie.app.reseau

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

// Étape 1 : transport sur le réseau local (même Wi-Fi), sans serveur et sans chiffrement.
// Le chiffrement avec libsignal viendra à l'étape suivante, par-dessus ce transport.

data class Appareil(val nom: String, val adresse: InetAddress, val port: Int)

data class Message(val avec: String, val de: String, val texte: String, val moi: Boolean)

object Reseau {
    private const val TYPE = "_messagerie._tcp."

    private val principal = Handler(Looper.getMainLooper())

    // Observés par l'interface : se mettent à jour automatiquement.
    val appareils = mutableStateMapOf<String, Appareil>()
    val messages = mutableStateListOf<Message>()

    var monNom: String = ""
        private set

    private var serveur: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var enregistrement: NsdManager.RegistrationListener? = null
    private var decouverte: NsdManager.DiscoveryListener? = null

    fun demarrer(context: Context) {
        if (serveur != null) return
        val gestionnaire = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = gestionnaire
        monNom = "${Build.MODEL} ${(1000..9999).random()}"

        val s = ServerSocket(0)
        serveur = s
        Thread { ecouter(s) }.start()

        // Annonce de cet appareil sur le réseau local.
        val info = NsdServiceInfo().apply {
            serviceName = monNom
            serviceType = TYPE
            port = s.localPort
        }
        val inscription = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        enregistrement = inscription
        gestionnaire.registerService(info, NsdManager.PROTOCOL_DNS_SD, inscription)

        // Recherche des autres appareils qui ont l'appli ouverte.
        val recherche = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}

            override fun onServiceFound(trouve: NsdServiceInfo) {
                if (trouve.serviceName == monNom) return
                gestionnaire.resolveService(trouve, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val hote = i.host ?: return
                        principal.post { appareils[i.serviceName] = Appareil(i.serviceName, hote, i.port) }
                    }
                })
            }

            override fun onServiceLost(perdu: NsdServiceInfo) {
                principal.post { appareils.remove(perdu.serviceName) }
            }
        }
        decouverte = recherche
        gestionnaire.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, recherche)
    }

    fun arreter() {
        decouverte?.let { nsd?.stopServiceDiscovery(it) }
        enregistrement?.let { nsd?.unregisterService(it) }
        serveur?.close()
        serveur = null
    }

    fun envoyer(destinataire: String, texte: String) {
        val appareil = appareils[destinataire] ?: return
        val contenu = JSONObject().put("de", monNom).put("texte", texte).toString()
        messages.add(Message(destinataire, monNom, texte, moi = true))
        Thread {
            try {
                Socket(appareil.adresse, appareil.port).use { socket ->
                    socket.getOutputStream().write((contenu + "\n").toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                // Échec silencieux pour l'instant : la gestion d'erreur viendra avec le relais.
            }
        }.start()
    }

    private fun ecouter(s: ServerSocket) {
        while (!s.isClosed) {
            try {
                val client = s.accept()
                Thread { lire(client) }.start()
            } catch (e: Exception) {
                return
            }
        }
    }

    private fun lire(client: Socket) {
        try {
            client.use { c ->
                val ligne = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8)).readLine() ?: return
                val json = JSONObject(ligne)
                val de = json.getString("de")
                val texte = json.getString("texte")
                principal.post { messages.add(Message(de, de, texte, moi = false)) }
            }
        } catch (e: Exception) {
            // Message mal formé ou connexion coupée : ignoré.
        }
    }
}
