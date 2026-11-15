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
import java.nio.ByteBuffer
import java.security.MessageDigest
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
    // Un contact inconnu qui écrit reste en « demande » tant qu'on ne l'a pas accepté.
    val accepte: Boolean = true,
    // Boîte du relais internet, quand le contact a été trouvé par pseudo.
    val boite: String? = null,
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

    var monPseudo = mutableStateOf("")
        private set

    // Message affiché sous le champ pseudo dans les réglages.
    val statutPseudo = mutableStateOf("")

    private var premierPlan = false

    // Appelé par l'activité à l'ouverture et à la fermeture de l'appli.
    fun surPremierPlan(actif: Boolean) {
        premierPlan = actif
    }

    private var boiteMoi: String? = null
    private var jetonMoi: String? = null

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
        monPseudo.value = p.getString("pseudo", "") ?: ""
        boiteMoi = p.getString("boite", null) ?: Relais.aleatoire(16).also { p.edit().putString("boite", it).apply() }
        jetonMoi = p.getString("jeton", null) ?: Relais.aleatoire(32).also { p.edit().putString("jeton", it).apply() }
        // Relève les messages venus par internet, seulement quand l'appli est ouverte et qu'un pseudo existe.
        // En arrière-plan, rien n'est interrogé : Wi-Fi et SMS restent actifs, et la batterie est préservée.
        Thread {
            while (true) {
                try {
                    if (premierPlan && monPseudo.value.isNotBlank()) recevoirRelais()
                } catch (e: Exception) { }
                Thread.sleep(20000)
            }
        }.start()

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
                                accepte = connu?.accepte ?: true,
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

    const val LIEN_TELECHARGEMENT = "https://github.com/lacroixnathan39300-spec/messagerie/releases/latest/download/Messagerie.apk"

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
        // Contact trouvé par pseudo : le message passe par le relais internet, chiffré.
        val boiteDest = a.boite
        val cleDest = a.cle
        if (boiteDest != null && cleDest != null) {
            val clair = JSONObject().put("pseudo", monPseudo.value).put("texte", texte).toString()
            val enveloppe = JSONObject()
                .put("cle", Chiffrement.clePublique(contexte))
                .put("boite", boiteMoi)
                .put("c", Chiffrement.chiffrer(contexte, cleDest, clair))
            val corps = JSONObject().put("boite", boiteDest).put("charge", enveloppe.toString())
            Thread { Relais.requete("POST", "/v1/envoyer", corps) }.start()
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

    fun accepter(id: String) {
        val a = appareils[id] ?: return
        appareils[id] = a.copy(accepte = true)
        sauverContacts()
    }

    // Nom du carnet d'adresses si la permission Contacts est accordée, sinon le numéro ou le pseudo.
    fun nomAffiche(id: String): String {
        val a = appareils[id] ?: return id
        val numero = a.numero ?: return a.nom
        val contexte = appContext ?: return numero
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(numero),
            )
            contexte.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: numero
        } catch (e: Exception) {
            numero
        }
    }

    // Inscription du pseudo sur le relais : le pseudo est lié à la clé et à la boîte, jamais au numéro.
    fun inscrirePseudo(pseudo: String) {
        val contexte = appContext ?: return
        val propre = pseudo.trim().lowercase()
        val corps = JSONObject()
            .put("pseudo", propre)
            .put("cle", Chiffrement.clePublique(contexte))
            .put("boite", boiteMoi)
            .put("jeton", jetonMoi)
        Thread {
            val reponse = Relais.requete("POST", "/v1/inscrire", corps)
            principal.post {
                if (reponse != null) {
                    monPseudo.value = propre
                    prefs?.edit()?.putString("pseudo", propre)?.apply()
                    statutPseudo.value = "Pseudo enregistré : $propre"
                } else {
                    statutPseudo.value = "Pseudo invalide ou déjà pris (3 à 20 lettres, chiffres, _ . -)"
                }
            }
        }.start()
    }

    // Trouve un contact par pseudo sur le relais, puis ouvre la discussion. Renvoie l'identifiant via onResultat.
    fun ouvrirPseudo(pseudo: String, onResultat: (String?) -> Unit) {
        val propre = pseudo.trim().lowercase()
        Thread {
            val reponse = Relais.requete("GET", "/v1/chercher?pseudo=$propre")
            principal.post {
                if (reponse == null) {
                    onResultat(null)
                } else {
                    appareils[propre] = Appareil(
                        nom = propre,
                        adresse = null,
                        port = 0,
                        numero = null,
                        cle = reponse.getString("cle"),
                        accepte = true,
                        boite = reponse.getString("boite"),
                    )
                    sauverContacts()
                    onResultat(propre)
                }
            }
        }.start()
    }

    // Relève les messages du relais, les déchiffre, et les range (en demande si contact inconnu).
    private fun recevoirRelais() {
        val contexte = appContext ?: return
        val boite = boiteMoi ?: return
        val jeton = jetonMoi ?: return
        val reponse = Relais.requete("GET", "/v1/recevoir?boite=$boite&jeton=$jeton") ?: return
        val liste = reponse.optJSONArray("messages") ?: return
        for (i in 0 until liste.length()) {
            try {
                val enveloppe = JSONObject(liste.getString(i))
                val cleAutre = enveloppe.getString("cle")
                val boiteAutre = enveloppe.getString("boite")
                val clair = Chiffrement.dechiffrer(contexte, cleAutre, enveloppe.getString("c")) ?: continue
                val json = JSONObject(clair)
                val pseudo = json.getString("pseudo")
                val texte = json.getString("texte")
                principal.post {
                    val connu = appareils[pseudo]
                    appareils[pseudo] = Appareil(
                        nom = pseudo,
                        adresse = null,
                        port = 0,
                        numero = null,
                        cle = cleAutre,
                        accepte = connu?.accepte ?: false,
                        boite = boiteAutre,
                    )
                    sauverContacts()
                    messages.add(Message(pseudo, pseudo, texte, moi = false))
                }
            } catch (e: Exception) {
                // Paquet illisible ou mal formé : ignoré.
            }
        }
    }

    // Envoie notre clé publique par SMS à un contact dont on connaît le numéro.
    fun envoyerCle(id: String) {
        val a = appareils[id] ?: return
        val contexte = appContext ?: return
        val numero = a.numero ?: return
        Sms.envoyerCle(contexte, numero)
        messages.add(Message(id, "système", "Ta clé a été envoyée par SMS. Compare le code de sécurité de vive voix.", moi = false))
    }

    // Clé reçue par SMS : on l'enregistre, et on répond avec la nôtre si on ne la connaissait pas.
    fun recevoirCle(expediteur: String, cle: String) {
        val contexte = appContext ?: return
        val existant = appareils.values.firstOrNull { memeNumero(it.numero, expediteur) }
        if (existant?.cle == cle) return
        val nom = existant?.nom ?: expediteur
        principal.post {
            appareils[nom] = Appareil(nom, existant?.adresse, existant?.port ?: 0, expediteur, cle, accepte = existant?.accepte ?: false)
            sauverContacts()
        }
        Sms.envoyerCle(contexte, expediteur)
    }

    // Code à 8 chiffres, identique des deux côtés : à comparer de vive voix pour écarter un intermédiaire.
    fun codeSecurite(cleAutre: String): String? {
        val contexte = appContext ?: return null
        val moi = Chiffrement.clePublique(contexte)
        val empreinte = MessageDigest.getInstance("SHA-256")
            .digest(listOf(moi, cleAutre).sorted().joinToString("|").toByteArray(Charsets.UTF_8))
        val valeur = (ByteBuffer.wrap(empreinte, 0, 4).int and 0x7fffffff) % 100000000
        val texte = valeur.toString().padStart(8, '0')
        return texte.substring(0, 4) + " " + texte.substring(4)
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
                        accepte = connu?.accepte ?: false,
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
                appareils[nom] = Appareil(nom, null, 0, o.optString("numero").ifBlank { null }, o.optString("cle").ifBlank { null }, accepte = o.optBoolean("accepte", true), boite = o.optString("boite").ifBlank { null })
            }
        } catch (e: Exception) {
            // Liste illisible : on repart de zéro.
        }
    }

    private fun sauverContacts() {
        val liste = JSONArray()
        appareils.values.filter { it.cle != null }.forEach {
            liste.put(JSONObject().put("nom", it.nom).put("numero", it.numero ?: "").put("cle", it.cle).put("accepte", it.accepte).put("boite", it.boite ?: ""))
        }
        prefs?.edit()?.putString("contacts", liste.toString())?.apply()
    }
}
