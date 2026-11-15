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
import fr.messagerie.app.media.Media
import fr.messagerie.app.sms.Mms
import fr.messagerie.app.sms.Sms
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
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

data class Message(
    val avec: String,
    val de: String,
    val texte: String,
    val moi: Boolean,
    val heure: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
    val statut: Int = Statut.AUCUN,
    // Message éphémère : instant où il s'efface (0 = jamais).
    val expire: Long = 0,
    // Photo ou vidéo : 0 = texte, sinon Media.PHOTO ou Media.VIDEO.
    val type: Int = 0,
    // Fichier chiffré gardé sur le téléphone, et sa clé. Absent une fois une « vue unique » regardée.
    val media: String? = null,
    val cleMedia: String? = null,
    val vueUnique: Boolean = false,
)

// États d'un message envoyé.
object Statut {
    const val AUCUN = 0
    const val ENVOI = 1
    const val ENVOYE = 2
    const val REMIS = 3
    const val ECHEC = 4
    // Le contact n'a pas encore de clé : le message partira tout seul dès qu'elle arrive.
    const val ATTENTE = 5
    const val LU = 6
}

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

    // Augmente à chaque mise en arrière-plan : les conversations ouvertes se reverrouillent.
    val session = mutableStateOf(0)

    // Dernière lecture par contact (pour les non-lus) et durée des messages éphémères par contact, en secondes.
    val lus = mutableStateMapOf<String, Long>()
    val ephemeres = mutableStateMapOf<String, Int>()

    // Accusés de lecture : coûtent un SMS par lecture, donc désactivés par défaut.
    val accuses = mutableStateOf(false)

    // Appelé par l'activité à l'ouverture et à la fermeture de l'appli.
    fun surPremierPlan(actif: Boolean) {
        premierPlan = actif
        // Le choix d'une photo fait sortir de l'appli un instant : la conversation reste ouverte.
        if (!actif && !choixEnCours) session.value = session.value + 1
        if (actif) {
            choixEnCours = false
            verifierPermissions()
            purger()
            releverMmsEnFond()
            viderNoms()
            try {
                (appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(1)
            } catch (e: Exception) { }
        }
    }

    @Volatile var choixEnCours = false

    // Vrai quand Android a accordé l'envoi et la réception des SMS. Relu à chaque retour dans l'appli.
    val smsAutorise = mutableStateOf(true)

    fun verifierPermissions() {
        val contexte = appContext ?: return
        smsAutorise.value = listOf(android.Manifest.permission.SEND_SMS, android.Manifest.permission.RECEIVE_SMS).all {
            contexte.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var serveur: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var enregistrement: NsdManager.RegistrationListener? = null
    private var decouverte: NsdManager.DiscoveryListener? = null
    private var initialise = false
    private var ticLance = false

    // Efface les messages éphémères arrivés à échéance, toutes les 5 secondes.
    private val tic = object : Runnable {
        private var tours = 0
        override fun run() {
            purger()
            tours++
            if (premierPlan && tours % 3 == 0) releverMmsEnFond()
            principal.postDelayed(this, 5000)
        }
    }

    fun purger() {
        val maintenant = System.currentTimeMillis()
        if (messages.any { it.expire in 1..maintenant }) retirer { it.expire in 1..maintenant }
    }

    // Retire des messages et supprime leurs photos et vidéos.
    private fun retirer(lesquels: (Message) -> Boolean) {
        val contexte = appContext
        if (contexte != null) messages.filter(lesquels).forEach { m -> m.media?.let { Media.supprimer(contexte, it) } }
        messages.removeAll(lesquels)
        sauverMessages()
    }

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
        accuses.value = p.getBoolean("accuses", false)
        chargerContacts(p)
        chargerMessages(p)
        chargerCarte(p, "lus") { nom, o -> lus[nom] = o.optLong(nom, 0L) }
        chargerCarte(p, "ephemeres") { nom, o -> ephemeres[nom] = o.optInt(nom, 0) }
        initialise = true
        purger()
        // Fichiers orphelins (messages trop anciens, lecture interrompue) : supprimés.
        Media.nettoyer(contexte, messages.mapNotNull { it.media }.toSet())
    }

    fun demarrer(context: Context) {
        initialiser(context)
        if (serveur != null) return
        val contexte = context.applicationContext
        if (!ticLance) {
            ticLance = true
            principal.postDelayed(tic, 5000)
        }

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
        val duree = ephemeres[id] ?: 0
        val m = Message(
            id, monNom, texte, moi = true,
            statut = Statut.ENVOI,
            expire = if (duree > 0) System.currentTimeMillis() + duree * 1000L else 0,
        )
        ajouter(m)
        acheminer(a, m, contexte, duree)
    }

    private fun acheminer(a: Appareil, m: Message, contexte: Context, duree: Int) {
        val adresse = a.adresse
        if (adresse != null) {
            // Wi-Fi local : chiffré si la clé du contact est connue, sinon en clair (premier contact).
            val contenu = enveloppe(contexte).put("ttl", duree)
            val cle = a.cle
            if (cle != null) contenu.put("c", Chiffrement.chiffrer(contexte, cle, m.texte)) else contenu.put("texte", m.texte)
            envoyerTcp(
                adresse, a.port, contenu.toString(),
                surSucces = { majStatut(m.id, Statut.REMIS) },
                surEchec = {
                    // L'appareil n'est plus joignable en Wi-Fi : on tente le SMS.
                    principal.post {
                        val actuel = appareils[a.nom]
                        if (actuel != null) {
                            appareils[a.nom] = actuel.copy(adresse = null)
                            envoyerHorsWifi(actuel, m, contexte, duree)
                        }
                    }
                },
            )
            return
        }
        envoyerHorsWifi(a, m, contexte, duree)
    }

    private fun envoyerHorsWifi(a: Appareil, m: Message, contexte: Context, duree: Int) {
        val id = a.nom
        val cle = a.cle
        val numero = a.numero
        when {
            numero != null && cle != null -> {
                val clair = JSONObject().put("de", monNumero.value).put("texte", m.texte).put("ttl", duree).toString()
                if (!Sms.envoyer(contexte, numero, Chiffrement.chiffrer(contexte, cle, clair), m.id)) {
                    majStatut(m.id, Statut.ECHEC)
                    note(id, "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone.")
                }
            }
            // Numéro connu mais pas de clé : la personne n'a pas (encore) l'appli. On l'invite une fois,
            // et le message attend : il partira tout seul dès que la clé sera échangée.
            numero != null -> {
                majStatut(m.id, Statut.ATTENTE)
                if (invites.add(id)) {
                    val expediteur = monNumero.value.ifBlank { "Quelqu'un" }
                    val parti = Sms.inviter(numero, "$expediteur t'écrit sur Messagerie. Installe l'appli pour lire ses messages : $LIEN_TELECHARGEMENT")
                    note(
                        id,
                        if (parti) "Invitation envoyée par SMS. Tes messages partiront tout seuls dès que la clé sera échangée."
                        else "SMS non envoyé. Autorise les SMS pour Messagerie dans les paramètres du téléphone.",
                    )
                }
            }
            else -> {
                majStatut(m.id, Statut.ECHEC)
                note(id, "Non remis : ce contact n'est plus à portée du Wi-Fi.")
            }
        }
    }

    // Change l'état d'un message envoyé. Un état plus avancé n'est jamais remplacé par un moins avancé.
    fun majStatut(idMessage: String, statut: Int) = surPrincipal {
        val i = messages.indexOfFirst { it.id == idMessage }
        if (i >= 0) {
            val actuel = messages[i].statut
            if (statut != actuel && rang(statut) >= rang(actuel)) {
                messages[i] = messages[i].copy(statut = statut)
                sauverMessages()
            }
        }
    }

    private fun rang(statut: Int): Int = when (statut) {
        Statut.LU -> 4
        Statut.REMIS -> 3
        Statut.ENVOYE, Statut.ECHEC -> 2
        else -> 1
    }

    // La clé d'un contact vient d'arriver : les messages qui l'attendaient partent.
    private fun envoyerEnAttente(nom: String) {
        val a = appareils[nom] ?: return
        val contexte = appContext ?: return
        if (a.cle == null) return
        messages.filter { it.avec == nom && it.moi && it.type == 0 && it.statut == Statut.ATTENTE }.forEach { m ->
            majStatut(m.id, Statut.ENVOI)
            val reste = if (m.expire > 0) ((m.expire - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(1) else 0
            acheminer(a, m, contexte, reste)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Photos et vidéos
    // ------------------------------------------------------------------------------------------

    // Contenu envoyé : [taille de l'en-tête][en-tête JSON : type, vue unique, durée][photo ou vidéo].
    private fun emballer(type: Int, vueUnique: Boolean, duree: Int, octets: ByteArray): ByteArray {
        val entete = JSONObject().put("type", type).put("vue", vueUnique).put("ttl", duree).toString().toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + entete.size + octets.size).putInt(entete.size).put(entete).put(octets).array()
    }

    // Envoie une photo ou une vidéo choisie dans la galerie. Wi-Fi local si le contact est à portée, sinon MMS.
    fun envoyerMedia(id: String, uri: android.net.Uri, vueUnique: Boolean) {
        val contexte = appContext ?: return
        val depart = appareils[id] ?: return
        val cle = depart.cle
        if (cle == null) {
            note(id, "Échange d'abord la clé avec ce contact pour envoyer une photo ou une vidéo.")
            return
        }
        val duree = ephemeres[id] ?: 0
        Thread {
            try {
                val video = Media.estVideo(contexte, uri)
                val type = if (video) Media.VIDEO else Media.PHOTO
                val enWifi = depart.adresse != null
                // Qualité complète en Wi-Fi ; version allégée pour tenir dans un MMS.
                val octets = when {
                    video && enWifi -> Media.brut(contexte, uri, Media.MAX_VIDEO)
                    video -> Media.brut(contexte, uri, Media.MAX_MMS)
                    enWifi -> Media.photo(contexte, uri, 1600, 82, 3 * 1024 * 1024)
                    else -> Media.photo(contexte, uri, 1024, 70, Media.MAX_MMS)
                }
                if (octets == null) {
                    principal.post {
                        note(
                            id,
                            when {
                                video && enWifi -> "Vidéo trop lourde (15 Mo au maximum)."
                                video -> "Vidéo trop lourde pour un MMS. Elle peut partir quand vous êtes sur le même Wi-Fi."
                                else -> "Cette photo n'a pas pu être lue."
                            },
                        )
                    }
                    return@Thread
                }
                // Une « vue unique » n'est pas gardée chez l'expéditeur.
                val garde = if (vueUnique) null else Media.garder(contexte, octets)
                val m = Message(
                    id, monNom, "", moi = true,
                    statut = Statut.ENVOI,
                    expire = if (duree > 0) System.currentTimeMillis() + duree * 1000L else 0,
                    type = type, media = garde?.first, cleMedia = garde?.second, vueUnique = vueUnique,
                )
                principal.post { ajouter(m) }
                val chiffre = Chiffrement.chiffrerOctets(contexte, cle, emballer(type, vueUnique, duree, octets))
                val adresse = depart.adresse
                if (adresse != null) {
                    val entete = enveloppe(contexte).put("m", chiffre.size).toString()
                    envoyerTcp(
                        adresse, depart.port, entete,
                        surSucces = { majStatut(m.id, Statut.REMIS) },
                        surEchec = {
                            // Plus à portée du Wi-Fi : une photo peut encore partir en MMS, allégée.
                            val leger = if (video) (if (octets.size <= Media.MAX_MMS) octets else null)
                            else Media.photo(contexte, uri, 1024, 70, Media.MAX_MMS)
                            val numero = depart.numero
                            if (leger != null && numero != null) {
                                envoyerParMms(contexte, id, numero, cle, m.id, emballer(type, vueUnique, duree, leger))
                            } else {
                                majStatut(m.id, Statut.ECHEC)
                                principal.post { note(id, "Non remis : ce contact n'est plus à portée du Wi-Fi.") }
                            }
                        },
                        piece = chiffre,
                    )
                } else {
                    val numero = depart.numero
                    if (numero == null) {
                        majStatut(m.id, Statut.ECHEC)
                        principal.post { note(id, "Non remis : ce contact n'est plus à portée du Wi-Fi.") }
                    } else if (!Mms.envoyer(contexte, numero, Mms.PREFIXE + android.util.Base64.encodeToString(chiffre, android.util.Base64.NO_WRAP), m.id)) {
                        majStatut(m.id, Statut.ECHEC)
                        principal.post { note(id, "MMS non envoyé. Vérifie les données mobiles et l'autorisation SMS de Messagerie.") }
                    }
                }
            } catch (e: Throwable) {
                principal.post { note(id, "Envoi impossible : fichier trop lourd pour ce téléphone.") }
            }
        }.start()
    }

    private fun envoyerParMms(contexte: Context, id: String, numero: String, cle: String, idMessage: String, clair: ByteArray) {
        val chiffre = Chiffrement.chiffrerOctets(contexte, cle, clair)
        val texte = Mms.PREFIXE + android.util.Base64.encodeToString(chiffre, android.util.Base64.NO_WRAP)
        if (!Mms.envoyer(contexte, numero, texte, idMessage)) {
            majStatut(idMessage, Statut.ECHEC)
            principal.post { note(id, "MMS non envoyé. Vérifie les données mobiles et l'autorisation SMS de Messagerie.") }
        }
    }

    // Photo ou vidéo reçue et déjà déchiffrée : on la range, chiffrée, sur le téléphone.
    private fun recevoirMedia(nom: String, clair: ByteArray, cleSiNouveau: String? = null, numeroSiNouveau: String? = null) {
        val contexte = appContext ?: return
        try {
            val tampon = ByteBuffer.wrap(clair)
            val tailleEntete = tampon.int
            if (tailleEntete <= 0 || tailleEntete > 4096 || tailleEntete > clair.size - 4) return
            val entete = JSONObject(String(clair, 4, tailleEntete, Charsets.UTF_8))
            val octets = clair.copyOfRange(4 + tailleEntete, clair.size)
            val type = if (entete.optInt("type", Media.PHOTO) == Media.VIDEO) Media.VIDEO else Media.PHOTO
            val (fichier, cleFichier) = Media.garder(contexte, octets)
            if (appareils[nom] == null) {
                appareils[nom] = Appareil(nom, null, 0, numeroSiNouveau, cleSiNouveau, accepte = false)
                sauverContacts()
            }
            ajouter(
                Message(
                    nom, nom, "", moi = false,
                    expire = echeance(entete.optInt("ttl", 0)),
                    type = type, media = fichier, cleMedia = cleFichier, vueUnique = entete.optBoolean("vue", false),
                )
            )
            notifier()
        } catch (e: Throwable) {
            // Contenu illisible : ignoré.
        }
    }

    // MMS : relus dans la boîte de réception du téléphone, hors du fil principal.
    @Volatile private var releveEnCours = false

    private fun releverMmsEnFond() {
        val contexte = appContext ?: return
        Thread { releverMms(contexte) }.start()
    }

    fun releverMms(context: Context) {
        if (releveEnCours) return
        releveEnCours = true
        try {
            val trouves = Mms.relever(context.applicationContext)
            if (trouves.isEmpty()) return
            principal.post {
                try {
                    initialiser(context)
                    trouves.forEach { (expediteur, contenu) -> recevoirMms(expediteur, contenu) }
                } catch (e: Throwable) { }
            }
        } catch (e: Throwable) {
        } finally {
            releveEnCours = false
        }
    }

    private fun recevoirMms(expediteur: String?, contenu: String) {
        val contexte = appContext ?: return
        val brut = try {
            android.util.Base64.decode(contenu, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            return
        }
        // Le contact est celui dont la clé ouvre le contenu : d'abord le numéro annoncé, puis les autres.
        val candidats = appareils.values.filter { it.cle != null }
            .sortedByDescending { memeNumero(it.numero, expediteur) }
        for (contact in candidats) {
            val clair = Chiffrement.dechiffrerOctets(contexte, contact.cle ?: continue, brut) ?: continue
            recevoirMedia(contact.nom, clair)
            return
        }
    }

    // Contenu déchiffré d'une photo ou d'une vidéo, pour l'affichage.
    fun octetsMedia(m: Message): ByteArray? {
        val contexte = appContext ?: return null
        return Media.lire(contexte, m.media ?: return null, m.cleMedia ?: return null)
    }

    fun fichierLecture(m: Message): File? {
        val contexte = appContext ?: return null
        return Media.fichierLecture(contexte, m.media ?: return null, m.cleMedia ?: return null)
    }

    // Efface les copies provisoires déchiffrées (vidéo en cours de lecture).
    fun finLecture() {
        val contexte = appContext ?: return
        try { File(contexte.cacheDir, "lecture").listFiles()?.forEach { it.delete() } } catch (e: Exception) { }
    }

    // Une « vue unique » vient d'être regardée : le fichier est détruit, il reste la trace « vue ».
    fun consommer(idMessage: String) {
        val i = messages.indexOfFirst { it.id == idMessage }
        if (i < 0) return
        val m = messages[i]
        val contexte = appContext
        if (contexte != null && m.media != null) Media.supprimer(contexte, m.media)
        messages[i] = m.copy(media = null, cleMedia = null)
        sauverMessages()
    }

    // ------------------------------------------------------------------------------------------
    // Lecture, non-lus, éphémères, suppression
    // ------------------------------------------------------------------------------------------

    fun nonLus(id: String): Int {
        val depuis = lus[id] ?: 0L
        return messages.count { it.avec == id && !it.moi && it.de != SYSTEME && it.heure > depuis }
    }

    // Appelé quand la conversation est affichée déverrouillée.
    fun marquerLu(id: String) {
        val avait = nonLus(id) > 0
        if (!avait && lus[id] != null) return
        lus[id] = System.currentTimeMillis()
        sauverCarte("lus") { o -> lus.forEach { (nom, t) -> o.put(nom, t) } }
        if (avait && accuses.value) envoyerAccuse(id)
    }

    fun definirAccuses(actif: Boolean) {
        accuses.value = actif
        prefs?.edit()?.putBoolean("accuses", actif)?.apply()
    }

    private fun envoyerAccuse(id: String) {
        val a = appareils[id] ?: return
        val contexte = appContext ?: return
        val cle = a.cle ?: return
        val preuve = Chiffrement.chiffrer(contexte, cle, "lu")
        val adresse = a.adresse
        val numero = a.numero
        if (adresse != null) {
            val contenu = enveloppe(contexte).put("lu", preuve).toString()
            envoyerTcp(adresse, a.port, contenu, surSucces = {}, surEchec = {
                if (numero != null) Sms.envoyerTexte(numero, Sms.PREFIXE_LU + preuve)
            })
        } else if (numero != null) {
            Sms.envoyerTexte(numero, Sms.PREFIXE_LU + preuve)
        }
    }

    // Accusé de lecture reçu par SMS.
    fun recevoirLu(expediteur: String, charge: String) {
        val contexte = appContext ?: return
        val contact = appareils.values.firstOrNull { memeNumero(it.numero, expediteur) && it.cle != null } ?: return
        if (Chiffrement.dechiffrer(contexte, contact.cle ?: return, charge) == "lu") surPrincipal { marquerLusPar(contact.nom) }
    }

    private fun marquerLusPar(nom: String) {
        var change = false
        for (i in messages.indices) {
            val m = messages[i]
            if (m.avec == nom && m.moi && (m.statut == Statut.ENVOI || m.statut == Statut.ENVOYE || m.statut == Statut.REMIS)) {
                messages[i] = m.copy(statut = Statut.LU)
                change = true
            }
        }
        if (change) sauverMessages()
    }

    // Durée des messages éphémères pour ce contact, en secondes (0 = désactivé).
    fun definirEphemere(id: String, secondes: Int) {
        if (secondes > 0) ephemeres[id] = secondes else ephemeres.remove(id)
        sauverCarte("ephemeres") { o -> ephemeres.forEach { (nom, d) -> o.put(nom, d) } }
    }

    fun effacerConversation(id: String) {
        retirer { it.avec == id }
    }

    fun supprimerContact(id: String) {
        effacerConversation(id)
        appareils.remove(id)
        lus.remove(id)
        ephemeres.remove(id)
        invites.remove(id)
        sauverContacts()
        sauverCarte("lus") { o -> lus.forEach { (nom, t) -> o.put(nom, t) } }
        sauverCarte("ephemeres") { o -> ephemeres.forEach { (nom, d) -> o.put(nom, d) } }
    }

    fun toutEffacer() = retirer { true }

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

    fun viderNoms() {
        nomsCarnet.clear()
        verifierPermissions()
    }

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
                envoyerEnAttente(nom)
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
                envoyerEnAttente(nom)
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
            appContext?.let { Media.nettoyer(it, emptySet()) }
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
        surPrincipal { envoyerEnAttente(nom) }
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
        val json = try {
            JSONObject(clair)
        } catch (e: Exception) {
            return
        }
        val texte = json.optString("texte", "")
        if (texte.isEmpty()) return
        val duree = json.optInt("ttl", 0)
        surPrincipal {
            ajouter(Message(contact.nom, contact.nom, texte, moi = false, expire = echeance(duree)))
            notifier()
        }
    }

    private fun echeance(duree: Int): Long =
        if (duree > 0) System.currentTimeMillis() + duree.coerceAtMost(7 * 86400) * 1000L else 0

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

    private fun envoyerTcp(
        adresse: InetAddress, port: Int, contenu: String,
        surSucces: () -> Unit, surEchec: () -> Unit, piece: ByteArray? = null,
    ) {
        Thread {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(adresse, port), 5000)
                    val sortie = socket.getOutputStream()
                    sortie.write((contenu + "\n").toByteArray(Charsets.UTF_8))
                    if (piece != null) sortie.write(piece)
                    sortie.flush()
                }
                surSucces()
            } catch (e: Throwable) {
                surEchec()
            }
        }.start()
    }

    private fun lireLigne(flux: InputStream): String? {
        val tampon = ByteArrayOutputStream()
        while (true) {
            val b = flux.read()
            if (b < 0 || b == '\n'.code) break
            tampon.write(b)
            if (tampon.size() > 400_000) return null
        }
        return if (tampon.size() == 0) null else tampon.toString("UTF-8")
    }

    private fun lireOctets(flux: InputStream, taille: Int): ByteArray? {
        val tampon = ByteArray(taille)
        var lus = 0
        while (lus < taille) {
            val n = flux.read(tampon, lus, taille - lus)
            if (n < 0) return null
            lus += n
        }
        return tampon
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
                val flux = BufferedInputStream(c.getInputStream())
                val ligne = lireLigne(flux) ?: return
                val json = JSONObject(ligne)
                val de = json.getString("de")
                if (de.isBlank() || de == SYSTEME) return
                val numero = json.optString("numero", "").ifBlank { null }
                val cle = json.optString("cle", "").ifBlank { null }
                // Accusé de lecture : vérifié avec la clé déjà enregistrée, jamais celle du paquet.
                val preuve = json.optString("lu", "")
                if (preuve.isNotEmpty()) {
                    val cleConnue = appareils[de]?.cle ?: return
                    if (Chiffrement.dechiffrer(contexte, cleConnue, preuve) == "lu") principal.post { marquerLusPar(de) }
                    return
                }
                // Photo ou vidéo : l'en-tête annonce la taille, le contenu chiffré suit.
                val taillePiece = json.optInt("m", 0)
                if (taillePiece > 0) {
                    if (taillePiece > Media.MAX_VIDEO + 1024 * 1024) return
                    val cleConnue = appareils[de]?.cle ?: cle ?: return
                    val brut = lireOctets(flux, taillePiece) ?: return
                    val clair = Chiffrement.dechiffrerOctets(contexte, cleConnue, brut) ?: return
                    principal.post { recevoirMedia(de, clair, cleSiNouveau = cle, numeroSiNouveau = numero) }
                    return
                }
                val duree = json.optInt("ttl", 0)
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
                    ajouter(Message(de, de, texte, moi = false, expire = echeance(duree)))
                    notifier()
                    envoyerEnAttente(de)
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

    // Petites tables nom -> valeur (dernière lecture, durée éphémère), gardées en JSON.
    private fun chargerCarte(p: SharedPreferences, cle: String, lire: (String, JSONObject) -> Unit) {
        try {
            val o = JSONObject(p.getString(cle, null) ?: return)
            o.keys().forEach { nom -> lire(nom, o) }
        } catch (e: Exception) { }
    }

    private fun sauverCarte(cle: String, remplir: (JSONObject) -> Unit) {
        try {
            val o = JSONObject()
            remplir(o)
            prefs?.edit()?.putString(cle, o.toString())?.apply()
        } catch (e: Exception) { }
    }

    // Les messages sont gardés chiffrés par la clé du Keystore : illisibles hors de ce téléphone.
    private fun chargerMessages(p: SharedPreferences) {
        val brut = p.getString("messages", null) ?: return
        try {
            val liste = JSONArray(String(Chiffrement.ouvrirLocal(brut), Charsets.UTF_8))
            for (i in 0 until liste.length()) {
                val o = liste.getJSONObject(i)
                messages.add(
                    Message(
                        o.getString("a"), o.getString("d"), o.getString("t"), o.getBoolean("m"),
                        heure = o.optLong("h", System.currentTimeMillis()),
                        id = o.optString("i", "").ifBlank { UUID.randomUUID().toString() },
                        statut = o.optInt("s", Statut.AUCUN),
                        expire = o.optLong("e", 0L),
                        type = o.optInt("y", 0),
                        media = o.optString("f", "").ifBlank { null },
                        cleMedia = o.optString("k", "").ifBlank { null },
                        vueUnique = o.optBoolean("v", false),
                    )
                )
            }
        } catch (e: Exception) {
            // Historique illisible (clé du Keystore perdue) : on repart de zéro.
        }
    }

    private fun sauverMessages() {
        try {
            val liste = JSONArray()
            messages.takeLast(MESSAGES_GARDES).forEach {
                liste.put(
                    JSONObject().put("a", it.avec).put("d", it.de).put("t", it.texte).put("m", it.moi)
                        .put("h", it.heure).put("i", it.id).put("s", it.statut).put("e", it.expire)
                        .put("y", it.type).put("f", it.media ?: "").put("k", it.cleMedia ?: "").put("v", it.vueUnique)
                )
            }
            val protege = Chiffrement.protegerLocal(liste.toString().toByteArray(Charsets.UTF_8))
            prefs?.edit()?.putString("messages", protege)?.apply()
        } catch (e: Exception) {
            // Sauvegarde impossible : le message reste affiché jusqu'à la fermeture de l'appli.
        }
    }
}
