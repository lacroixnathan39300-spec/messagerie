package fr.messagerie.app.reseau

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import fr.messagerie.app.crypto.Chiffrement
import fr.messagerie.app.sms.Sms
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

// Transports :
//  - Wi-Fi local (même réseau, sans internet) : texte non chiffré pour l'instant.
//  - SMS (réseau mobile, sans internet) : texte chiffré de bout en bout.
// La clé publique et le numéro sont échangés sur le Wi-Fi local, puis gardés pour le SMS.

data class Appareil(
    val nom: String,
    val adresse: InetAddress?,
    val port: Int,
    val numero: String?,
    val cle: String?,
)

data class Message(val avec: String, val de: String, val texte: String, val moi: Boolean)

object Reseau {
    private const val TYPE = "_messagerie._tcp."

    private val principal = Handler(Looper.getMainLooper())

    val appareils = mutableStateMapOf<String, Appareil>()
    val messages = mutableStateListOf<Message>()

    var monNom: String = ""
        private set

    var monNumero = mutableStateOf("")
        private set

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var serveur: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var enregistrement: NsdManager.RegistrationListener? = null
    private var decouverte: NsdManager.DiscoveryListener? = null

    fun demarrer(context: Context) {
        if (serveur != null) return
        val contexte = context.applicationContext
        appContext = contexte
        val p = contexte.getSharedPreferences("reseau", Context.MODE_PRIVATE)
        prefs = p
        monNumero.value = p.getString("numero", "") ?: ""
        chargerContacts(p)

        val gestionnaire = contexte.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = gestionnaire
        monNom = "${Build.MODEL} ${(1000..9999).random()}"

        val s = ServerSocket(0)
        serveur = s
        Thread { ecouter(s) }.start()

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
                        principal.post {
                            val connu = appareils[i.serviceName]
                            appareils[i.serviceName] = Appareil(
                                nom = i.serviceName,
                                adresse = hote,
                                port = i.port,
                                numero = connu?.numero,
                                cle = connu?.cle,
                            )
                        }
                    }
                })
            }

            override fun onServiceLost(perdu: NsdServiceInfo) {
                principal.post {
                    val connu = appareils[perdu.serviceName]
                    if (connu != null) appareils[perdu.serviceName] = connu.copy(adresse = null)
                }
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

    fun enregistrerNumero(numero: String) {
        monNumero.value = numero.trim()
        prefs?.edit()?.putString("numero", monNumero.value)?.apply()
    }

    fun envoyer(id: String, texte: String) {
        val a = appareils[id] ?: return
        val contexte = appContext ?: return
        messages.add(Message(id, monNom, texte, moi = true))

        val adresse = a.adresse
        if (adresse != null) {
            val contenu = enveloppe(contexte).put("texte", texte).toString()
            envoyerTcp(adresse, a.port, contenu)
            return
        }
        val numero = a.numero
        val cle = a.cle
        if (numero != null && cle != null) {
            val clair = JSONObject().put("de", monNumero.value).put("texte", texte).toString()
            Sms.envoyer(contexte, numero, Chiffrement.chiffrer(contexte, cle, clair))
        }
    }

    // Appelé par le récepteur de SMS : le numéro de l'expéditeur doit être un contact connu.
    fun recevoirSms(context: Context, expediteur: String, charge: String) {
        val contact = appareils.values.firstOrNull { it.numero == expediteur && it.cle != null } ?: return
        val clair = Chiffrement.dechiffrer(context, contact.cle!!, charge) ?: return
        try {
            val json = JSONObject(clair)
            val texte = json.getString("texte")
            principal.post { messages.add(Message(contact.nom, contact.nom, texte, moi = false)) }
        } catch (e: Exception) {
            // Message mal formé : ignoré.
        }
    }

    private fun enveloppe(context: Context): JSONObject =
        JSONObject()
            .put("de", monNom)
            .put("numero", monNumero.value)
            .put("cle", Chiffrement.clePublique(context))

    private fun envoyerTcp(adresse: InetAddress, port: Int, contenu: String) {
        Thread {
            try {
                Socket(adresse, port).use { socket ->
                    socket.getOutputStream().write((contenu + "\n").toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                // Échec silencieux pour l'instant.
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
                val numero = json.optString("numero", "").ifBlank { null }
                val cle = json.optString("cle", "").ifBlank { null }
                val texte = json.getString("texte")
                principal.post {
                    val connu = appareils[de]
                    appareils[de] = Appareil(
                        nom = de,
                        adresse = connu?.adresse,
                        port = connu?.port ?: 0,
                        numero = numero ?: connu?.numero,
                        cle = cle ?: connu?.cle,
                    )
                    sauverContacts()
                    messages.add(Message(de, de, texte, moi = false))
                }
            }
        } catch (e: Exception) {
            // Message mal formé ou connexion coupée : ignoré.
        }
    }

    private fun chargerContacts(p: SharedPreferences) {
        val brut = p.getString("contacts", null) ?: return
        try {
            val liste = JSONArray(brut)
            for (i in 0 until liste.length()) {
                val o = liste.getJSONObject(i)
                val nom = o.getString("nom")
                appareils[nom] = Appareil(nom, null, 0, o.optString("numero").ifBlank { null }, o.optString("cle").ifBlank { null })
            }
        } catch (e: Exception) {
            // Liste illisible : on repart de zéro.
        }
    }

    private fun sauverContacts() {
        val liste = JSONArray()
        appareils.values.filter { it.numero != null && it.cle != null }.forEach {
            liste.put(JSONObject().put("nom", it.nom).put("numero", it.numero).put("cle", it.cle))
        }
        prefs?.edit()?.putString("contacts", liste.toString())?.apply()
    }
}
