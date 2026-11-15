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

    const val LIEN_TELECHARGEMENT = "https://github.com/lacroixnathan39300-spec/messagerie/releases"

    // Ouvre une discussion avec un numéro qui n'est pas encore dans la liste.
    fun ouvrirNumero(saisie: String): String? {
        val propre = saisie.filter { it.isDigit() || it == '+' }
        if (propre.length < 6) return null
        if (appareils.values.none { memeNumero(it.numero, propre) }) {
            appareils[propre] = Appareil(propre, null, 0, propre, null)
        }
        return propre
    }

    fun envoyer(id: String, texte: String) {
        val a = appareils[id] ?: return
        val contexte = appContext ?: return
        messages.add(Message(id, monNom, texte, moi = true))

        // Contact sans l'appli (pas de clé connue) : on envoie une invitation, pas le message.
        if (a.adresse == null && a.cle == null) {
            val numero = a.numero ?: return
            val expediteur = monNumero.value.ifBlank { "Quelqu'un" }
            Sms.inviter(numero, "$expediteur t'écrit sur Messagerie. Installe l'appli pour lire le message : $LIEN_TELECHARGEMENT")
            messages.add(Message(id, "système", "Invitation envoyée par SMS. Le message arrivera quand la personne aura installé l'appli.", moi = false))
            return
        }

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

    // Un même numéro peut s'écrire 06..., +33 6... ou 0033 6... : on compare les 9 derniers chiffres.
    fun memeNumero(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val x = a.filter { it.isDigit() }.takeLast(9)
        val y = b.filter { it.isDigit() }.takeLast(9)
        return x.length == 9 && x == y
    }

    // Appelé par le récepteur de SMS : le numéro de l'expéditeur doit être un contact connu.
    fun recevoirSms(context: Context, expediteur: String, charge: String) {
        val contact = appareils.values.firstOrNull { memeNumero(it.numero, expediteur) && it.cle != null } ?: return
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
