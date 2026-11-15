package fr.messagerie.app.reseau

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import fr.messagerie.app.crypto.Appairage
import fr.messagerie.app.crypto.Chiffrement
import fr.messagerie.app.sms.Sms
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Aucun passage par internet. Transports, dans l'ordre d'essai :
//  - Wi-Fi local (même réseau, sans internet) : chiffré dès que la clé du contact est connue.
//  - SMS (réseau mobile, sans internet) : chiffré de bout en bout.
// Les messages sont gardés sur le téléphone, chiffrés par une clé du Keystore Android.

data class Appareil(
    val nom: String,
    val adresse: InetAddress?,
    val port: Int,
    val numero: String?,
    val cle: String?,
    // Un contact inconnu qui écrit reste en « demande » tant qu'on ne l'a pas accepté.
    val accepte: Boolean = true,
)

data class Message(val avec: String, val de: String, val texte: String, val moi: Boolean)

object Reseau {
    private const val TYPE = "_messagerie._tcp."
    // Expéditeur des notes affichées dans le fil (envoi impossible, clé changée...).
    const val SYSTEME = "système"
    private const val MESSAGES_GARDES = 500

    private val principal = Handler(Looper.getMainLooper())

    val appareils = mutableStateMapOf<String, Appareil>()
    val messages = mutableStateListOf<Message>()

    var monNom: String = ""
        private set

    var monNumero = mutableStateOf("")
        private set

    private var premierPlan = false

    // Appelé par l'activité à l'ouverture et à la fermeture de l'appli.
    fun surPremierPlan(actif: Boolean) {
        premierPlan = actif
        if (actif) {
            viderNoms()
            try {
                (appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(1)
            } catch (e: Exception) { }
        }
    }

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var serveur: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var enregistrement: NsdManager.RegistrationListener? = null
    private var decouverte: NsdManager.DiscoveryListener? = null
    private var initialise = false

    // Exécute sur le fil principal : tout de suite si on y est déjà (réception d'un SMS appli fermée).
    private fun surPrincipal(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else principal.post(action)
    }

    // Charge réglages, contacts et messages. Sans réseau : sert aussi au récepteur de SMS, appli fermée.
    fun initialiser(context: Context) {
        if (initialise) return
        val contexte = context.applicationContext
        appContext = contexte
        val p = contexte.getSharedPreferences("reseau", Context.MODE_PRIVATE)
        prefs = p
        monNumero.value = p.getString("numero", "") ?: ""
        // Nom fixe sur le Wi-Fi local : sinon chaque lancement créerait un nouveau contact chez les autres.
        monNom = p.getString("nom", null)
            ?: "${Build.MODEL} ${(1000..9999).random()}".also { p.edit().putString("nom", it).apply() }
        chargerContacts(p)
        chargerMessages(p)
        initialise = true
    }

    fun demarrer(context: Context) {
        initialiser(context)
        if (serveur != null) return
        val contexte = context.applicationContext

        // Wi-Fi local : si la découverte échoue, le reste de l'appli (SMS) continue de marcher.
        try {
            val gestionnaire = contexte.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsd = gestionnaire

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
                    try {
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
                    } catch (e: Exception) {
                        // Résolution déjà en cours : l'appareil sera retrouvé à la prochaine annonce.
                    }
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
        } catch (e: Exception) {
            // Pas de Wi-Fi local sur cet appareil.
        }
    }

    fun arreter() {
        try { decouverte?.let { nsd?.stopServiceDiscovery(it) } } catch (e: Exception) { }
        try { enregistrement?.let { nsd?.unregisterService(it) } } catch (e: Exception) { }
        try { serveur?.close() } catch (e: Exception) { }
        decouverte = null
        enregistrement = null
        serveur = null
        // Les adresses Wi-Fi ne valent plus rien une fois la découverte arrêtée.
        appareils.values.toList().forEach { if (it.adresse != null) appareils[it.nom] = it.copy(adresse = null) }
    }

    fun enregistrerNumero(numero: String) {
        monNumero.value = numero.trim()
        prefs?.edit()?.putString("numero", monNumero.value)?.apply()
    }

    const val LIEN_TELECHARGEMENT = "https://github.com/lacroixnathan39300-spec/messagerie/releases/latest/download/Messagerie.apk"

    // Ouvre une discussion avec un numéro. Renvoie l'identifiant du contact (existant ou créé).
    fun ouvrirNumero(saisie: String): String? {
        val propre = saisie.filter { it.isDigit() || it == '+' }
        if (propre.length < 6) return null
        val existant = appareils.values.firstOrNull { memeNumero(it.numero, propre) }
        if (existant != null) {
            if (!existant.accepte) accepter(existant.nom)
            return existant.nom
        }
        appareils[propre] = Appareil(propre, null, 0, propre, null)
        sauverContacts()
        return propre
    }

    // ------------------------------------------------------------------------------------------
    // Envoi
    // ------------------------------------------------------------------------------------------

    private val invites = HashSet<String>()

    private fun ajouter(m: Message) {
        messages.add(m)
        sauverMessages()
    }

    private fun note(id: String, texte: String) = ajouter(Message(id, SYSTEME, texte, moi = false))

    fun envoyer(id: String, texte: String) {
        val a = appareils[id] ?: return
        val contexte = appContext ?: return
        ajouter(Message(id, monNom, texte, moi = true))

        val adresse = a.adresse
        if (adresse != null) {
            // Wi-Fi local : chiffré si la clé du contact est connue, sinon en clair (premier contact).
            val contenu = enveloppe(contexte)
            val cle = a.cle
            if (cle != null) contenu.put("c", Chiffrement.chiffrer(contexte, cle, texte)) else contenu.put("texte", texte)
            envoyerTcp(adresse, a.port, contenu.toString()) {
                // L'appareil n'est plus joignable en Wi-Fi : on tente les autres chemins.
                principal.post {
                    val actuel = appareils[id]
                    if (actuel != null) {
                        appareils[id] = actuel.copy(adresse = null)
                        envoyerHorsWifi(actuel, texte, contexte)
                    }
                }
            }
            return
        }
        envoyerHorsWifi(a, texte, contexte)
    }

    private fun envoyerHorsWifi(a: Appareil, texte: String, contexte: Context) {
        val id = a.nom
        val cle = a.cle
        val numero = a.numero
        when {
            numero != null && cle != null -> {
                val clair = JSONObject().put("de", monNumero.value).put("texte", texte).toString()
                if (!Sms.envoyer(contexte, numero, Chiffrement.chiffrer(contexte, cle, clair))) {
                    note(id, "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone.")
                }
            }
            // Numéro connu mais pas de clé : la personne n'a pas (encore) l'appli. On l'invite une fois.
            numero != null -> {
                if (invites.add(id)) {
                    val expediteur = monNumero.value.ifBlank { "Quelqu'un" }
                    val parti = Sms.inviter(numero, "$expediteur t'écrit sur Messagerie. Installe l'appli pour lire ses messages : $LIEN_TELECHARGEMENT")
                    note(
                        id,
                        if (parti) "Invitation envoyée par SMS. Ce message n'a pas été transmis : renvoie-le quand la clé sera échangée."
                        else "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone.",
                    )
                } else {
                    note(id, "Non remis : la clé n'est pas encore échangée avec ce contact.")
                }
            }
            else -> note(id, "Non remis : ce contact n'est plus à portée du Wi-Fi.")
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

    // Noms du carnet d'adresses déjà cherchés, pour ne pas interroger le carnet à chaque affichage.
    private val nomsCarnet = HashMap<String, String>()

    fun viderNoms() = nomsCarnet.clear()

    // Nom du carnet d'adresses si la permission Contacts est accordée, sinon le numéro.
    fun nomAffiche(id: String): String {
        val a = appareils[id] ?: return id
        val numero = a.numero ?: return a.nom
        nomsCarnet[numero]?.let { return it }
        val contexte = appContext ?: return numero
        val nom = try {
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
        nomsCarnet[numero] = nom
        return nom
    }

    // ------------------------------------------------------------------------------------------
    // Liaison par code (sans serveur, par SMS)
    // ------------------------------------------------------------------------------------------

    // Côté créateur : codes en attente. Côté destinataire : demandes reçues.
    val codesSortants = mutableStateMapOf<String, String>()
    val liaisonsEntrantes = mutableStateMapOf<String, String>()
    val statutLiaison = mutableStateOf("")
    private val liaisonsEnAttente = mutableMapOf<String, Pair<ByteArray, String>>()

    fun creerLiaison(numero: String) {
        val contexte = appContext ?: return
        val propre = numero.filter { it.isDigit() || it == '+' }
        if (propre.length < 6) {
            statutLiaison.value = "Numéro invalide."
            return
        }
        val code = Appairage.nouveauCode()
        if (!Sms.envoyerTexte(propre, Sms.PREFIXE_P1 + Chiffrement.clePublique(contexte))) {
            statutLiaison.value = "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone."
            return
        }
        codesSortants[propre] = code
        statutLiaison.value = "Donne ce code à la personne, de vive voix : ${Appairage.formater(code)}\nGarde l'appli ouverte jusqu'à la confirmation."
    }

    fun recevoirP1(expediteur: String, pubA: String) {
        surPrincipal {
            liaisonsEntrantes[expediteur] = pubA
            statutLiaison.value = "Une personne demande à se lier. Saisis le code qu'elle t'a donné."
            notifier()
        }
    }

    fun validerLiaison(numero: String, saisie: String) {
        val contexte = appContext ?: return
        val pubA = liaisonsEntrantes[numero] ?: return
        val code = Appairage.normaliser(saisie)
        if (code.length != 12) {
            statutLiaison.value = "Le code fait 12 chiffres."
            return
        }
        val pubB = Chiffrement.clePublique(contexte)
        val (k, etiquetteB) = Appairage.repondre(code, pubA, pubB)
        if (!Sms.envoyerTexte(numero, Sms.PREFIXE_P2 + pubB + "|" + etiquetteB)) {
            statutLiaison.value = "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone."
            return
        }
        // Le contact n'est ajouté qu'une fois la confirmation reçue : un mauvais code ne laisse aucune clé.
        liaisonsEnAttente[numero] = k to pubA
        liaisonsEntrantes.remove(numero)
        statutLiaison.value = "Réponse envoyée. Garde l'appli ouverte : la liaison sera confirmée dans un instant."
    }

    fun recevoirP2(expediteur: String, charge: String) {
        val contexte = appContext ?: return
        val cleCode = codesSortants.keys.firstOrNull { memeNumero(it, expediteur) } ?: return
        val morceaux = charge.split("|", limit = 2)
        if (morceaux.size != 2) return
        val pubA = Chiffrement.clePublique(contexte)
        val confirmation = Appairage.verifierEtConfirmer(codesSortants[cleCode] ?: return, pubA, morceaux[0], morceaux[1])
        surPrincipal {
            codesSortants.remove(cleCode)
            if (confirmation == null) {
                statutLiaison.value = "Code incorrect : la liaison n'a pas été faite. Crée un nouveau code."
            } else {
                Sms.envoyerTexte(expediteur, Sms.PREFIXE_P3 + confirmation)
                val existant = appareils.values.firstOrNull { memeNumero(it.numero, cleCode) }
                val nom = existant?.nom ?: cleCode
                appareils[nom] = Appareil(nom, existant?.adresse, existant?.port ?: 0, existant?.numero ?: cleCode, morceaux[0], accepte = true)
                sauverContacts()
                statutLiaison.value = "Liaison faite. Vous pouvez vous écrire."
            }
        }
    }

    fun recevoirP3(expediteur: String, etiquetteA: String) {
        val contexte = appContext ?: return
        val cleEntree = liaisonsEnAttente.keys.firstOrNull { memeNumero(it, expediteur) } ?: return
        val (k, pubA) = liaisonsEnAttente[cleEntree] ?: return
        val pubB = Chiffrement.clePublique(contexte)
        surPrincipal {
            if (Appairage.verifierConfirmation(k, pubA, pubB, etiquetteA)) {
                val existant = appareils.values.firstOrNull { memeNumero(it.numero, cleEntree) }
                val nom = existant?.nom ?: cleEntree
                appareils[nom] = Appareil(nom, existant?.adresse, existant?.port ?: 0, existant?.numero ?: cleEntree, pubA, accepte = true)
                sauverContacts()
                statutLiaison.value = "Liaison confirmée des deux côtés. Vous pouvez vous écrire."
            } else {
                statutLiaison.value = "Confirmation invalide : liaison annulée."
            }
            liaisonsEnAttente.remove(cleEntree)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Code d'accès et code leurre
    // ------------------------------------------------------------------------------------------

    // Code d'accès : demandé à chaque ouverture d'une conversation. Stocké sous forme d'empreinte (PBKDF2).
    fun codeDefini(): Boolean = prefs?.contains("codeHash") == true

    fun definirCode(code: String) {
        val p = prefs ?: return
        val sel = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val empreinte = hacher(code, sel)
        p.edit()
            .putString("codeSel", android.util.Base64.encodeToString(sel, android.util.Base64.NO_WRAP))
            .putString("codeHash", android.util.Base64.encodeToString(empreinte, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun verifierCode(saisie: String): Boolean = verifierEmpreinte(saisie, "codeSel", "codeHash")

    // Code leurre : ouvre une fausse conversation, sans rien révéler des vraies. Stocké comme le code d'accès.
    fun leurreDefini(): Boolean = prefs?.contains("leurreHash") == true

    fun definirLeurre(code: String) {
        val p = prefs ?: return
        val sel = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit()
            .putString("leurreSel", android.util.Base64.encodeToString(sel, android.util.Base64.NO_WRAP))
            .putString("leurreHash", android.util.Base64.encodeToString(hacher(code, sel), android.util.Base64.NO_WRAP))
            .apply()
    }

    fun verifierLeurre(saisie: String): Boolean = verifierEmpreinte(saisie, "leurreSel", "leurreHash")

    private fun verifierEmpreinte(saisie: String, cleSel: String, cleHash: String): Boolean {
        val p = prefs ?: return false
        return try {
            val sel = android.util.Base64.decode(p.getString(cleSel, null) ?: return false, android.util.Base64.NO_WRAP)
            val attendu = android.util.Base64.decode(p.getString(cleHash, null) ?: return false, android.util.Base64.NO_WRAP)
            MessageDigest.isEqual(hacher(saisie, sel), attendu)
        } catch (e: Exception) {
            false
        }
    }

    private fun hacher(code: String, sel: ByteArray): ByteArray {
        val fabrique = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return fabrique.generateSecret(PBEKeySpec(code.toCharArray(), sel, 200000, 256)).encoded
    }

    // Au 4e code faux d'affilée, les conversations sont effacées (voir noterEssai).
    // Anti-devinette : après 5 codes faux d'affilée, il faut attendre (30 s, puis le double à chaque
    // nouvel échec, jusqu'à 1 h). Le compteur survit à la fermeture de l'appli.
    fun attenteCode(): Int {
        val jusqua = prefs?.getLong("bloqueJusqua", 0L) ?: 0L
        val reste = (jusqua - System.currentTimeMillis() + 999) / 1000
        return reste.coerceIn(0L, 3600L).toInt()
    }

    fun noterEssai(reussi: Boolean) {
        val p = prefs ?: return
        if (reussi) {
            p.edit().remove("echecs").remove("bloqueJusqua").apply()
            return
        }
        val echecs = p.getInt("echecs", 0) + 1
        val edition = p.edit().putInt("echecs", echecs)
        // Plus de 3 codes faux d'affilée : toutes les conversations sont effacées, sans rien afficher.
        if (echecs > 3) {
            messages.clear()
            edition.remove("messages")
        }
        if (echecs >= 5) {
            val secondes = (30L shl (echecs - 5).coerceAtMost(7)).coerceAtMost(3600L)
            edition.putLong("bloqueJusqua", System.currentTimeMillis() + secondes * 1000)
        }
        edition.apply()
    }

    // ------------------------------------------------------------------------------------------
    // Clés par SMS
    // ------------------------------------------------------------------------------------------

    // Envoie notre clé publique par SMS à un contact dont on connaît le numéro.
    fun envoyerCle(id: String) {
        val a = appareils[id] ?: return
        val contexte = appContext ?: return
        val numero = a.numero ?: return
        if (Sms.envoyerCle(contexte, numero)) {
            note(id, "Ta clé a été envoyée par SMS. Quand la sienne arrive, compare le code de sécurité de vive voix.")
        } else {
            note(id, "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone.")
        }
    }

    // Clé reçue par SMS : on l'enregistre, et on répond avec la nôtre si on ne la connaissait pas.
    fun recevoirCle(expediteur: String, cle: String) {
        val contexte = appContext ?: return
        val existant = appareils.values.firstOrNull { memeNumero(it.numero, expediteur) }
        if (existant?.cle == cle) return
        val nom = existant?.nom ?: expediteur
        surPrincipal {
            appareils[nom] = Appareil(nom, existant?.adresse, existant?.port ?: 0, existant?.numero ?: expediteur, cle, accepte = existant?.accepte ?: false)
            sauverContacts()
            if (existant?.cle != null) {
                note(nom, "La clé de ce contact a changé. Compare à nouveau le code de sécurité de vive voix.")
            }
            notifier()
        }
        Sms.envoyerCle(contexte, expediteur)
    }

    // Code à 8 chiffres, identique des deux côtés : à comparer de vive voix pour écarter un intermédiaire.
    fun codeSecurite(cleAutre: String): String? {
        val contexte = appContext ?: return null
        return try {
            val moi = Chiffrement.clePublique(contexte)
            val empreinte = MessageDigest.getInstance("SHA-256")
                .digest(listOf(moi, cleAutre).sorted().joinToString("|").toByteArray(Charsets.UTF_8))
            val valeur = (ByteBuffer.wrap(empreinte, 0, 4).int and 0x7fffffff) % 100000000
            val texte = valeur.toString().padStart(8, '0')
            texte.substring(0, 4) + " " + texte.substring(4)
        } catch (e: Exception) {
            null
        }
    }

    // Appelé par le récepteur de SMS : le numéro de l'expéditeur doit être un contact connu.
    fun recevoirSms(context: Context, expediteur: String, charge: String) {
        val contact = appareils.values.firstOrNull { memeNumero(it.numero, expediteur) && it.cle != null } ?: return
        val clair = Chiffrement.dechiffrer(context, contact.cle ?: return, charge) ?: return
        val texte = try {
            JSONObject(clair).getString("texte")
        } catch (e: Exception) {
            return
        }
        surPrincipal {
            ajouter(Message(contact.nom, contact.nom, texte, moi = false))
            notifier()
        }
    }

    // Prévient d'un nouveau message quand l'appli n'est pas à l'écran. Ni l'expéditeur ni le contenu n'apparaissent.
    private fun notifier() {
        if (premierPlan) return
        val contexte = appContext ?: return
        try {
            val gestionnaire = contexte.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestionnaire.createNotificationChannel(
                NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val ouverture = contexte.packageManager.getLaunchIntentForPackage(contexte.packageName) ?: return
            val action = PendingIntent.getActivity(
                contexte, 0, ouverture, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(contexte, "messages")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Messagerie")
                .setContentText("Nouveau message")
                .setContentIntent(action)
                .setAutoCancel(true)
                .build()
            gestionnaire.notify(1, notification)
        } catch (e: Exception) {
            // Notifications refusées : le message reste visible à l'ouverture de l'appli.
        }
    }

    // ------------------------------------------------------------------------------------------
    // Wi-Fi local
    // ------------------------------------------------------------------------------------------

    private fun enveloppe(context: Context): JSONObject =
        JSONObject()
            .put("de", monNom)
            .put("numero", monNumero.value)
            .put("cle", Chiffrement.clePublique(context))

    private fun envoyerTcp(adresse: InetAddress, port: Int, contenu: String, surEchec: () -> Unit) {
        Thread {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(adresse, port), 5000)
                    socket.getOutputStream().write((contenu + "\n").toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                }
            } catch (e: Exception) {
                surEchec()
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
                c.soTimeout = 10000
                val contexte = appContext ?: return
                val ligne = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8)).readLine() ?: return
                val json = JSONObject(ligne)
                val de = json.getString("de")
                if (de.isBlank() || de == SYSTEME) return
                val numero = json.optString("numero", "").ifBlank { null }
                val cle = json.optString("cle", "").ifBlank { null }
                val chiffre = json.optString("c", "")
                val texte = if (chiffre.isNotEmpty()) {
                    Chiffrement.dechiffrer(contexte, cle ?: return, chiffre) ?: return
                } else {
                    json.getString("texte")
                }
                principal.post {
                    val connu = appareils[de]
                    // Une clé déjà enregistrée n'est jamais remplacée par le Wi-Fi : n'importe qui peut y prendre un nom.
                    val cleDifferente = connu?.cle != null && cle != null && connu.cle != cle
                    appareils[de] = Appareil(
                        nom = de,
                        adresse = connu?.adresse,
                        port = connu?.port ?: 0,
                        numero = connu?.numero ?: numero,
                        cle = connu?.cle ?: cle,
                        accepte = connu?.accepte ?: false,
                    )
                    sauverContacts()
                    if (cleDifferente) note(de, "Attention : le message suivant vient d'un appareil dont la clé ne correspond pas à celle enregistrée.")
                    ajouter(Message(de, de, texte, moi = false))
                    notifier()
                }
            }
        } catch (e: Exception) {
            // Message mal formé ou connexion coupée : ignoré.
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sauvegarde sur le téléphone
    // ------------------------------------------------------------------------------------------

    private fun chargerContacts(p: SharedPreferences) {
        val brut = p.getString("contacts", null) ?: return
        try {
            val liste = JSONArray(brut)
            for (i in 0 until liste.length()) {
                val o = liste.getJSONObject(i)
                val nom = o.getString("nom")
                appareils[nom] = Appareil(
                    nom, null, 0,
                    o.optString("numero").ifBlank { null },
                    o.optString("cle").ifBlank { null },
                    accepte = o.optBoolean("accepte", true),
                )
            }
        } catch (e: Exception) {
            // Liste illisible : on repart de zéro.
        }
    }

    private fun sauverContacts() {
        val liste = JSONArray()
        appareils.values.filter { it.cle != null || it.numero != null }.forEach {
            liste.put(
                JSONObject().put("nom", it.nom).put("numero", it.numero ?: "").put("cle", it.cle ?: "")
                    .put("accepte", it.accepte)
            )
        }
        prefs?.edit()?.putString("contacts", liste.toString())?.apply()
    }

    // Les messages sont gardés chiffrés par la clé du Keystore : illisibles hors de ce téléphone.
    private fun chargerMessages(p: SharedPreferences) {
        val brut = p.getString("messages", null) ?: return
        try {
            val liste = JSONArray(String(Chiffrement.ouvrirLocal(brut), Charsets.UTF_8))
            for (i in 0 until liste.length()) {
                val o = liste.getJSONObject(i)
                messages.add(Message(o.getString("a"), o.getString("d"), o.getString("t"), o.getBoolean("m")))
            }
        } catch (e: Exception) {
            // Historique illisible (clé du Keystore perdue) : on repart de zéro.
        }
    }

    private fun sauverMessages() {
        try {
            val liste = JSONArray()
            messages.takeLast(MESSAGES_GARDES).forEach {
                liste.put(JSONObject().put("a", it.avec).put("d", it.de).put("t", it.texte).put("m", it.moi))
            }
            val protege = Chiffrement.protegerLocal(liste.toString().toByteArray(Charsets.UTF_8))
            prefs?.edit()?.putString("messages", protege)?.apply()
        } catch (e: Exception) {
            // Sauvegarde impossible : le message reste affiché jusqu'à la fermeture de l'appli.
        }
    }
}
