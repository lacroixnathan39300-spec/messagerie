package fr.messagerie.app.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import fr.messagerie.app.media.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.app.reseau.Appareil
import fr.messagerie.app.reseau.Message
import fr.messagerie.app.reseau.Reseau
import fr.messagerie.app.reseau.Statut
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay

private val couleursContacts = listOf(Accent, Color(0xFF7AA7FF), Ephemere, Color(0xFFC594FF), Color(0xFFFF8FA3))

private fun couleurDe(nom: String): Color =
    couleursContacts[(nom.hashCode() and Int.MAX_VALUE) % couleursContacts.size]

private fun heureCourte(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(t))

private fun memeJour(a: Long, b: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.FRANCE)
    return f.format(Date(a)) == f.format(Date(b))
}

private fun jourLisible(t: Long): String {
    val maintenant = System.currentTimeMillis()
    return when {
        memeJour(t, maintenant) -> "Aujourd'hui"
        memeJour(t, maintenant - 86_400_000L) -> "Hier"
        else -> SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(t))
    }
}

// Heure si c'est aujourd'hui, sinon la date courte.
private fun momentCourt(t: Long): String =
    if (memeJour(t, System.currentTimeMillis())) heureCourte(t) else SimpleDateFormat("d MMM", Locale.FRANCE).format(Date(t))

private fun dureeEphemere(secondes: Int): String = when {
    secondes <= 0 -> "Désactivés"
    secondes < 3600 -> "${secondes / 60} min"
    secondes < 86400 -> "${secondes / 3600} h"
    else -> "${secondes / 86400} j"
}

@Composable
fun MessagerieApp() {
    var ecran by remember { mutableStateOf("discussions") }
    var appareilOuvert by remember { mutableStateOf<String?>(null) }
    var pret by remember { mutableStateOf(Reseau.codeDefini()) }

    MessagerieTheme {
        if (!pret) {
            Bienvenue(onFini = { pret = true })
        } else {
            val ouvert = appareilOuvert
            val cible = when {
                ecran == "reglages" -> "reglages"
                ouvert != null -> "c:$ouvert"
                else -> "liste"
            }
            // Le bouton retour du téléphone revient à la liste au lieu de quitter l'appli.
            BackHandler(enabled = cible != "liste") {
                if (ecran == "reglages") ecran = "discussions" else appareilOuvert = null
            }
            Crossfade(targetState = cible, label = "ecran") { c ->
                when {
                    c == "reglages" -> Reglages(onRetour = { ecran = "discussions" })
                    c.startsWith("c:") -> Conversation(
                        nom = c.removePrefix("c:"),
                        onRetour = { appareilOuvert = null },
                    )
                    else -> ListeDiscussions(
                        onOuvrir = { appareilOuvert = it },
                        onReglages = { ecran = "reglages" },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bienvenue (premier lancement)
// ---------------------------------------------------------------------------------------------

@Composable
private fun Bienvenue(onFini: () -> Unit) {
    var numero by remember { mutableStateOf(Reseau.monNumero.value) }
    var code by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fond)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icone("cadenas", Accent, 42.dp) }
        Spacer(Modifier.height(22.dp))
        Text("Messagerie", color = Texte, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tes messages, chiffrés, sans internet.",
            color = TexteDoux,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Atout("cadenas", "Chiffré de bout en bout", "Par SMS et sur le Wi-Fi local")
            Atout("valider", "Un code pour chaque ouverture", "Sans lui, les messages restent masqués")
            Atout("bulle", "Aucun serveur, aucun compte", "Tout reste sur ton téléphone")
        }
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Champ(numero, { numero = it }, "Ton numéro de téléphone", Modifier.fillMaxWidth(), clavier = KeyboardType.Phone)
            Champ(
                code,
                { code = it.filter { c -> c.isDigit() }.take(8); erreur = "" },
                "Choisis un code (4 à 8 chiffres)",
                Modifier.fillMaxWidth(),
                clavier = KeyboardType.NumberPassword,
            )
            if (erreur.isNotEmpty()) Aide(erreur, Danger)
            Bouton("Commencer", modifier = Modifier.fillMaxWidth()) {
                if (code.length < 4) {
                    erreur = "Le code doit faire au moins 4 chiffres."
                } else {
                    if (numero.isNotBlank()) Reseau.enregistrerNumero(numero)
                    Reseau.definirCode(code)
                    onFini()
                }
            }
            Aide("Retiens bien ton code : il n'existe aucun moyen de le récupérer.")
        }
    }
}

@Composable
private fun Atout(icone: String, titre: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icone(icone, Accent, 19.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titre, color = Texte, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TexteDoux, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Liste des discussions
// ---------------------------------------------------------------------------------------------

@Composable
private fun ListeDiscussions(onOuvrir: (String) -> Unit, onReglages: () -> Unit) {
    // Les discussions les plus récentes en premier, puis l'ordre alphabétique.
    val appareils = Reseau.appareils.values.filter { it.accepte }
        .sortedBy { Reseau.nomAffiche(it.nom).lowercase() }
        .sortedByDescending { a -> Reseau.messages.lastOrNull { it.avec == a.nom }?.heure ?: 0L }
    val enAttente = appareils.sumOf { Reseau.nonLus(it.nom) }
    val demandes = Reseau.appareils.values.filter { !it.accepte }
    var saisie by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf(false) }

    var recherche by remember { mutableStateOf(false) }
    fun ouvrirSaisie() {
        val propre = saisie.trim()
        if (propre.isEmpty()) return
        // Des lettres → recherche d'un pseudo sur le relais internet ; sinon, un numéro.
        if (propre.any { it.isLetter() }) {
            recherche = true
            erreur = false
            Reseau.ouvrirPseudo(propre) { trouve ->
                recherche = false
                if (trouve != null) {
                    saisie = ""
                    onOuvrir(trouve)
                } else {
                    erreur = true
                }
            }
        } else {
            val ouvert = Reseau.ouvrirNumero(propre)
            if (ouvert != null) {
                saisie = ""
                onOuvrir(ouvert)
            } else {
                erreur = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding()) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
                    Text("Messagerie", color = Texte, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Accent))
                        Text(
                            when {
                                enAttente > 1 -> "$enAttente nouveaux messages"
                                enAttente == 1 -> "1 nouveau message"
                                appareils.size > 1 -> "${appareils.size} contacts · chiffré, sans internet"
                                else -> "Chiffré, sans internet"
                            },
                            color = if (enAttente > 0) Accent else TexteDoux,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Surface)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icone("recherche", TexteDoux, 20.dp)
                    Champ(
                        valeur = saisie,
                        surChangement = { saisie = it; erreur = false },
                        indication = "Pseudo ou numéro",
                        modifier = Modifier.weight(1f),
                        fond = Color.Transparent,
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (saisie.isBlank()) SurfaceHaute else Accent)
                            .clickable { ouvrirSaisie() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icone("suivant", if (saisie.isBlank()) TexteDoux else Fond, 20.dp)
                    }
                }
                if (erreur || recherche) {
                    Text(
                        if (recherche) "Recherche…" else "Introuvable. Vérifie le pseudo ou le numéro.",
                        color = if (recherche) TexteDoux else Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (!Reseau.smsAutorise.value) item { AideSms() }
            item { LiaisonParCode() }
            if (demandes.isNotEmpty()) {
                item { TitreSection("Demandes · ${demandes.size}", Ephemere) }
                items(demandes, key = { "d-" + it.nom }) { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(Reseau.nomAffiche(d.nom), Ephemere, 42.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                Reseau.nomAffiche(d.nom),
                                color = Texte,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("Souhaite t'écrire", color = TexteDoux, fontSize = 13.sp)
                        }
                        Bouton("Refuser", couleur = TexteDoux, plein = false) { Reseau.supprimerContact(d.nom) }
                        Bouton("Accepter", plein = false) { Reseau.accepter(d.nom) }
                    }
                }
            }
            item { TitreSection("Discussions", TexteDoux) }
            if (appareils.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(Surface),
                            contentAlignment = Alignment.Center,
                        ) { Icone("bulle", TexteDoux, 32.dp) }
                        Text("Aucune discussion", color = Texte, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Saisis un numéro ci-dessus, ou ouvre l'appli sur un autre téléphone du même Wi-Fi.",
                            color = TexteDoux,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            items(appareils, key = { it.nom }) { a ->
                LigneAppareil(a, onClick = { onOuvrir(a.nom) })
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        BarreBas(onReglages)
    }
}

// Android bloque l'autorisation SMS des applis installées hors du Play Store (« paramètre restreint »).
// L'appli ne peut pas lever ce blocage elle-même : cette carte guide pas à pas et ouvre le bon écran.
@Composable
private fun AideSms() {
    val contexte = LocalContext.current
    var essais by remember { mutableStateOf(0) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        essais++
        Reseau.verifierPermissions()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Ephemere.copy(alpha = 0.10f))
            .border(1.dp, Ephemere.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Autorise les SMS", color = Texte, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Sans cette autorisation, aucun message ne peut partir ni arriver.",
            color = TexteDoux,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Bouton("1. Autoriser les SMS", modifier = Modifier.fillMaxWidth(), couleur = Ephemere) {
            demande.launch(
                arrayOf(
                    android.Manifest.permission.SEND_SMS,
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.RECEIVE_MMS,
                    android.Manifest.permission.READ_SMS,
                )
            )
        }
        if (essais > 0) {
            Text(
                "Android a refusé ? C'est son blocage des applis installées hors Play Store. Pour le lever :",
                color = Texte,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            EtapeSms("2", "Appuie sur le bouton ci-dessous : la fiche de Messagerie s'ouvre.")
            EtapeSms("3", "En haut à droite, appuie sur ⋮ puis « Autoriser les paramètres restreints », et confirme avec ton code de téléphone.")
            EtapeSms("4", "Toujours dans la fiche : Autorisations → SMS → Autoriser. Puis reviens ici.")
            Bouton("Ouvrir la fiche de Messagerie", modifier = Modifier.fillMaxWidth(), couleur = Ephemere, plein = false) {
                try {
                    contexte.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + contexte.packageName),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { }
            }
            Aide("Le menu ⋮ n'apparaît qu'après un premier refus. Sur certains téléphones il s'appelle « Plus » ou se trouve tout en bas de la fiche.")
        }
    }
}

@Composable
private fun EtapeSms(numero: String, texte: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(Ephemere.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) { Text(numero, color = Ephemere, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Text(texte, color = Texte, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BarreBas(onReglages: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            OngletBas("bulle", "Discussions", actif = true, modifier = Modifier.weight(1f)) {}
            OngletBas("reglages", "Réglages", actif = false, modifier = Modifier.weight(1f), onClick = onReglages)
        }
    }
}

@Composable
private fun OngletBas(icone: String, libelle: String, actif: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val couleur = if (actif) Accent else TexteDoux
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icone(icone, couleur, 22.dp)
        Text(libelle, color = couleur, fontSize = 11.sp, fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun LiaisonParCode() {
    var numero by remember { mutableStateOf("") }
    var codeParSms by remember { mutableStateOf(true) }
    var deplie by remember { mutableStateOf(false) }
    val statut = Reseau.statutLiaison.value
    val entrantes = Reseau.liaisonsEntrantes.keys.toList()
    val ouvert = deplie || statut.isNotBlank() || entrantes.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { deplie = !deplie }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icone("cadenas", Accent, 20.dp) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Lier un contact par code", color = Texte, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Envoie le lien de l'appli et un code, par SMS", color = TexteDoux, fontSize = 13.sp)
            }
            Icone(if (ouvert) "chevronHaut" else "chevron", TexteDoux, 18.dp)
        }
        AnimatedVisibility(visible = ouvert) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Champ(numero, { numero = it }, "Numéro du contact", Modifier.fillMaxWidth(), fond = Fond, clavier = KeyboardType.Phone)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Envoyer le code par SMS", color = Texte, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Aide(
                            if (codeParSms) "Le plus simple. Moins sûr : qui lit ses SMS voit le code."
                            else "Le code s'affichera ici : donne-le de vive voix."
                        )
                    }
                    Bascule(codeParSms) { codeParSms = it }
                }
                Bouton("Envoyer l'invitation", modifier = Modifier.fillMaxWidth()) { Reseau.creerLiaison(numero, codeParSms) }
                if (statut.isNotBlank()) {
                    Text(
                        statut,
                        color = Ephemere,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ephemere.copy(alpha = 0.10f))
                            .padding(12.dp),
                    )
                }
                entrantes.forEach { n -> LiaisonEntrante(n) }
            }
        }
    }
}

@Composable
private fun LiaisonEntrante(numero: String) {
    var code by remember { mutableStateOf("") }
    // Code arrivé par SMS : il se remplit tout seul, il ne reste qu'à valider.
    val recu = Reseau.codeRecu(numero)
    LaunchedEffect(recu) {
        if (recu != null && code.isEmpty()) code = recu
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (recu != null) "${Reseau.nomAffiche(numero)} t'invite. Son code est arrivé par SMS."
            else "${Reseau.nomAffiche(numero)} t'invite. Saisis le code qu'il t'a donné.",
            color = Texte,
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Champ(code, { code = it }, "12 chiffres", Modifier.weight(1f), fond = Fond, clavier = KeyboardType.Number)
            Bouton("Valider") { Reseau.validerLiaison(numero, code) }
        }
    }
}

@Composable
private fun LigneAppareil(a: Appareil, onClick: () -> Unit) {
    val nom = Reseau.nomAffiche(a.nom)
    val dernier = Reseau.messages.lastOrNull { it.avec == a.nom && it.de != Reseau.SYSTEME }
    val nonLus = Reseau.nonLus(a.nom)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            Avatar(nom, couleurDe(a.nom), 50.dp)
            // Point vert : le contact est joignable sur le Wi-Fi local.
            if (a.adresse != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Fond)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(Accent)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    nom,
                    color = Texte,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (a.cle != null) Icone("cadenas", Accent, 13.dp)
            }
            // Le contenu reste masqué ici : il ne s'affiche qu'après le code, dans la conversation.
            Text(
                when {
                    nonLus > 1 -> "$nonLus nouveaux messages"
                    nonLus == 1 -> "Nouveau message"
                    dernier == null -> if (a.cle == null) "Clé pas encore échangée" else "Pas encore de message"
                    dernier.moi -> "Message envoyé"
                    else -> "Message chiffré"
                },
                color = if (nonLus > 0) Accent else TexteDoux,
                fontWeight = if (nonLus > 0) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (dernier != null) Text(momentCourt(dernier.heure), color = if (nonLus > 0) Accent else TexteDoux, fontSize = 12.sp)
            if (nonLus > 0) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (nonLus > 99) "99+" else "$nonLus", color = Fond, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------------------------

// Fausse conversation affichée avec le code leurre : rien de réel, rien n'est envoyé.
private fun filLeurreInitial(): List<Message> {
    val t = System.currentTimeMillis()
    return listOf(
        Message("", "Moi", "Tu passes à la boulangerie ?", moi = true, heure = t - 3 * 3_600_000L, statut = Statut.LU),
        Message("", "Moi", "Ok, je prends du pain complet.", moi = false, heure = t - 3 * 3_600_000L + 240_000L),
        Message("", "Moi", "Merci, à ce soir.", moi = true, heure = t - 3 * 3_600_000L + 300_000L, statut = Statut.LU),
    )
}

// Réponses automatiques de la fausse conversation, pour qu'elle paraisse vivante.
private val REPONSES_LEURRE = listOf(
    "Ok 👍",
    "D'accord.",
    "Ça marche.",
    "Je te redis ça tout à l'heure.",
    "Oui pas de souci.",
    "Je suis occupé là, je te rappelle.",
    "Haha",
    "Tu rentres à quelle heure ?",
    "Merci !",
    "On en parle ce soir.",
)

@Composable
private fun Conversation(nom: String, onRetour: () -> Unit) {
    var saisie by remember { mutableStateOf("") }
    // Verrouillée à chaque ouverture et à chaque retour dans l'appli :
    // "verrouillee", "reelle" (vrais messages) ou "leurre" (fausse conversation).
    var mode by remember(Reseau.session.value) { mutableStateOf("verrouillee") }
    var menu by remember(Reseau.session.value) { mutableStateOf(false) }
    val filLeurre = remember { mutableStateListOf<Message>().apply { addAll(filLeurreInitial()) } }
    var ephemereLeurre by remember { mutableStateOf(0) }
    val fil = if (mode == "leurre") filLeurre else Reseau.messages.filter { it.avec == nom }
    val contact = Reseau.appareils[nom]
    val nomAffiche = Reseau.nomAffiche(nom)
    val etatListe = rememberLazyListState()
    val duree = if (mode == "leurre") ephemereLeurre else (Reseau.ephemeres[nom] ?: 0)
    // Photo ou vidéo choisie, en attente d'envoi.
    var piece by remember { mutableStateOf<Uri?>(null) }
    var vueUnique by remember { mutableStateOf(false) }
    var visionne by remember(Reseau.session.value) { mutableStateOf<Message?>(null) }
    val selecteur = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) piece = uri
    }

    LaunchedEffect(fil.size, mode) {
        if (mode != "verrouillee" && fil.isNotEmpty()) etatListe.animateScrollToItem(fil.size)
        if (mode == "reelle") Reseau.marquerLu(nom)
    }

    // Fausse conversation : une réponse arrive après un délai différent à chaque fois (3 à 25 s),
    // et les messages envoyés passent à « Remis » puis « Lu » comme les vrais.
    LaunchedEffect(filLeurre.size, mode) {
        if (mode == "leurre" && filLeurre.isNotEmpty() && filLeurre.last().moi && filLeurre.last().statut == Statut.ENVOI) {
            val attente = Random.nextLong(3_000, 25_000)
            delay(1_200)
            filLeurre[filLeurre.size - 1] = filLeurre.last().copy(statut = Statut.REMIS)
            delay(attente)
            for (i in filLeurre.indices) if (filLeurre[i].moi) filLeurre[i] = filLeurre[i].copy(statut = Statut.LU)
            delay(Random.nextLong(1_500, 4_000))
            val derniere = filLeurre.lastOrNull { !it.moi }?.texte
            filLeurre.add(Message("", "Moi", REPONSES_LEURRE.filter { it != derniere }.random(), moi = false))
        }
    }

    // Photo ou vidéo ouverte en plein écran.
    val ouvert = visionne
    if (ouvert != null && mode != "verrouillee") {
        Visionneuse(ouvert) {
            // Une « vue unique » reçue est détruite dès qu'on la referme.
            if (ouvert.vueUnique && !ouvert.moi) Reseau.consommer(ouvert.id)
            visionne = null
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRetour),
                contentAlignment = Alignment.Center,
            ) { Icone("retour", Texte, 22.dp) }
            Avatar(nomAffiche, couleurDe(nom), 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    nomAffiche,
                    color = Texte,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // En ligne : le contact a l'appli ouverte sur le même Wi-Fi. Sinon, les messages partent par SMS.
                val enWifi = contact?.adresse != null
                val parInternet = contact?.boite != null
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (enWifi || parInternet) Accent else TexteDoux.copy(alpha = 0.5f)))
                    Text(
                        when {
                            contact?.cle == null && enWifi -> "En ligne · clé pas encore échangée"
                            contact?.cle == null -> "Clé pas encore échangée"
                            enWifi -> "En ligne · chiffré, Wi-Fi"
                            parInternet -> "Chiffré · par internet"
                            else -> "Chiffré · par SMS"
                        },
                        color = if (contact?.cle == null) Ephemere else if (enWifi) Accent else TexteDoux,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            if (contact?.cle == null && contact?.numero != null) {
                Bouton("Envoyer ma clé", plein = false) { Reseau.envoyerCle(nom) }
            }
            if (mode != "verrouillee") {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { menu = !menu },
                    contentAlignment = Alignment.Center,
                ) { Icone("menu", if (menu) Accent else Texte, 22.dp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))

        AnimatedVisibility(visible = menu && mode != "verrouillee") {
            MenuConversation(
                codeSecurite = contact?.cle?.let { Reseau.codeSecurite(it) },
                duree = duree,
                surDuree = {
                    val suivante = when (duree) { 0 -> 60; 60 -> 3600; 3600 -> 86400; else -> 0 }
                    if (mode == "leurre") ephemereLeurre = suivante else Reseau.definirEphemere(nom, suivante)
                },
                surEffacer = {
                    if (mode == "leurre") filLeurre.clear() else Reseau.effacerConversation(nom)
                    menu = false
                },
                surSupprimer = {
                    if (mode != "leurre") Reseau.supprimerContact(nom)
                    onRetour()
                },
            )
        }

        if (mode == "verrouillee") {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(68.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icone("cadenas", Accent, 30.dp) }
                Spacer(Modifier.height(16.dp))
                Text("Conversation verrouillée", color = Texte, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Saisis ton code pour afficher les messages", color = TexteDoux, fontSize = 14.sp)
                Spacer(Modifier.height(22.dp))
                Clavier { code ->
                    val attente = Reseau.attenteCode()
                    when {
                        attente > 0 -> "Trop d'essais. Réessaie dans ${dureeLisible(attente)}."
                        Reseau.verifierCode(code) -> { Reseau.noterEssai(true); mode = "reelle"; null }
                        Reseau.verifierLeurre(code) -> { Reseau.noterEssai(true); mode = "leurre"; null }
                        else -> {
                            Reseau.noterEssai(false)
                            val suite = Reseau.attenteCode()
                            if (suite > 0) "Code incorrect. Réessaie dans ${dureeLisible(suite)}." else "Code incorrect"
                        }
                    }
                }
            }
        } else {
            if (fil.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(Surface),
                        contentAlignment = Alignment.Center,
                    ) { Icone("bulle", TexteDoux, 28.dp) }
                    Spacer(Modifier.height(14.dp))
                    Text("Aucun message", color = Texte, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Écris le premier message ci-dessous.",
                        color = TexteDoux,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = etatListe,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                ) {
                    itemsIndexed(fil, key = { _, m -> m.id }) { i, m ->
                        Column(modifier = Modifier.animateItem()) {
                            if (i == 0 || !memeJour(fil[i - 1].heure, m.heure)) SeparateurJour(m.heure)
                            // Les messages d'une même personne à la suite sont resserrés.
                            val suiteDuMeme = i > 0 && fil[i - 1].moi == m.moi && fil[i - 1].de != Reseau.SYSTEME &&
                                memeJour(fil[i - 1].heure, m.heure)
                            Spacer(Modifier.height(if (suiteDuMeme) 3.dp else 10.dp))
                            Bulle(m) { visionne = it }
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
            if (duree > 0) {
                Text(
                    "Messages éphémères : effacés ${dureeEphemere(duree)} après l'envoi",
                    color = Ephemere,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            if (piece != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icone("photo", Accent, 20.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Photo ou vidéo prête", color = Texte, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (vueUnique) "Vue unique : visible une seule fois" else "Appuie sur envoyer",
                            color = if (vueUnique) Ephemere else TexteDoux,
                            fontSize = 12.sp,
                        )
                    }
                    Text("Vue unique", color = TexteDoux, fontSize = 12.sp)
                    Bascule(vueUnique) { vueUnique = it }
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { piece = null; vueUnique = false },
                        contentAlignment = Alignment.Center,
                    ) { Icone("fermer", TexteDoux, 16.dp) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            // Le choix se fait hors de l'appli : la conversation ne doit pas se reverrouiller.
                            Reseau.choixEnCours = true
                            try {
                                selecteur.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            } catch (e: Exception) {
                                Reseau.choixEnCours = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icone("plus", if (piece != null) Accent else TexteDoux, 24.dp) }
                Champ(saisie, { saisie = it }, "Message", Modifier.weight(1f), uneLigne = false, rayon = 26)
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (saisie.isBlank() && piece == null) SurfaceHaute else if (duree > 0) Ephemere else Accent)
                        .clickable {
                            val jointe = piece
                            if (jointe != null) {
                                // En mode leurre, la photo apparaît dans le fil mais ne part nulle part.
                                if (mode == "leurre") filLeurre.add(Message("", "Moi", "", moi = true, statut = Statut.ENVOI, type = Media.PHOTO))
                                else Reseau.envoyerMedia(nom, jointe, vueUnique)
                                piece = null
                                vueUnique = false
                            }
                            val texte = saisie.trim()
                            if (texte.isNotEmpty()) {
                                // En mode leurre, le message reste à l'écran et ne part nulle part.
                                if (mode == "leurre") filLeurre.add(Message("", "Moi", texte, moi = true, statut = Statut.ENVOI))
                                else Reseau.envoyer(nom, texte)
                                saisie = ""
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icone("envoyer", if (saisie.isBlank() && piece == null) TexteDoux else Fond, 22.dp) }
            }
        }
    }
}

@Composable
private fun MenuConversation(
    codeSecurite: String?,
    duree: Int,
    surDuree: () -> Unit,
    surEffacer: () -> Unit,
    surSupprimer: () -> Unit,
) {
    // Les actions définitives demandent un second appui.
    var aConfirmer by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (codeSecurite != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Code de sécurité  $codeSecurite", color = Texte, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Aide("Il doit être identique sur le téléphone de ton contact. Comparez-le de vive voix.")
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = surDuree),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Messages éphémères", color = Texte, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Aide("Les nouveaux messages s'effacent des deux téléphones après ce délai. Appuie pour changer.")
            }
            Bouton(dureeEphemere(duree), couleur = if (duree > 0) Ephemere else TexteDoux, plein = false, onClick = surDuree)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
        Text(
            if (aConfirmer == "effacer") "Appuie encore pour tout effacer" else "Effacer la conversation",
            color = Danger,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clickable {
                if (aConfirmer == "effacer") { aConfirmer = ""; surEffacer() } else aConfirmer = "effacer"
            },
        )
        Text(
            if (aConfirmer == "supprimer") "Appuie encore pour supprimer le contact" else "Supprimer le contact",
            color = Danger,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clickable {
                if (aConfirmer == "supprimer") { aConfirmer = ""; surSupprimer() } else aConfirmer = "supprimer"
            },
        )
    }
}

@Composable
private fun SeparateurJour(t: Long) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Text(
            jourLisible(t),
            color = TexteDoux,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

private fun dureeLisible(secondes: Int): String =
    if (secondes >= 60) "${(secondes + 59) / 60} min" else "$secondes s"

// Clavier à chiffres du verrou. `surCode` renvoie null si le code ouvre quelque chose, sinon le message d'erreur.
@Composable
private fun Clavier(surCode: (String) -> String?) {
    var code by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf("") }
    val faux = erreur.isNotEmpty()
    val vibration = LocalHapticFeedback.current
    fun toucher() = vibration.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    fun valider() {
        if (code.length < 4) return
        erreur = surCode(code) ?: ""
        code = ""
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(maxOf(4, code.length)) { i ->
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(if (i < code.length) Accent else if (faux) Danger.copy(alpha = 0.5f) else Separateur)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(if (faux) erreur else " ", color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("123", "456", "789").forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                rangee.forEach { c ->
                    Touche(onClick = { toucher(); if (code.length < 8) { code += c; erreur = "" } }) {
                        Text(c.toString(), color = Texte, fontSize = 26.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Touche(fond = Color.Transparent, onClick = { toucher(); code = code.dropLast(1) }) { Icone("effacer", TexteDoux, 24.dp) }
            Touche(onClick = { toucher(); if (code.length < 8) { code += "0"; erreur = "" } }) {
                Text("0", color = Texte, fontSize = 26.sp)
            }
            Touche(fond = if (code.length >= 4) Accent else SurfaceHaute, onClick = { valider() }) {
                Icone("valider", if (code.length >= 4) Fond else TexteDoux, 24.dp)
            }
        }
    }
}

@Composable
private fun Touche(fond: Color = Surface, onClick: () -> Unit, contenu: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(70.dp).clip(CircleShape).background(fond).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

@Composable
private fun Bulle(m: Message, surOuvrir: (Message) -> Unit = {}) {
    if (m.de == Reseau.SYSTEME) {
        Text(
            m.texte,
            color = TexteDoux,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.moi) Alignment.End else Alignment.Start,
    ) {
        val forme = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomEnd = if (m.moi) 5.dp else 20.dp,
            bottomStart = if (m.moi) 20.dp else 5.dp,
        )
        if (m.type != 0) {
            ContenuMedia(m, forme, surOuvrir)
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(forme)
                    .background(if (m.moi) Accent else Surface)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(m.texte, color = if (m.moi) Fond else Texte, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (m.expire > 0) Text("Éphémère ·", color = Ephemere, fontSize = 11.sp)
            Text(heureCourte(m.heure), color = TexteDoux, fontSize = 11.sp)
            if (m.moi) {
                val (libelle, couleur) = when (m.statut) {
                    Statut.ENVOI -> "Envoi…" to TexteDoux
                    Statut.ENVOYE -> "Envoyé" to TexteDoux
                    Statut.REMIS -> "Remis" to TexteDoux
                    Statut.LU -> "Lu" to Accent
                    Statut.ECHEC -> "Non remis" to Danger
                    Statut.ATTENTE -> "En attente de la clé" to Ephemere
                    else -> "" to TexteDoux
                }
                if (libelle.isNotEmpty()) Text("· $libelle", color = couleur, fontSize = 11.sp)
            }
        }
    }
}

// Photo (aperçu), vidéo ou « vue unique » dans le fil.
@Composable
private fun ContenuMedia(m: Message, forme: RoundedCornerShape, surOuvrir: (Message) -> Unit) {
    val video = m.type == Media.VIDEO
    val quoi = if (video) "Vidéo" else "Photo"
    val masquee = m.vueUnique && !m.moi
    if (m.media != null && !video && !masquee) {
        val image = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, m.id) {
            value = withContext(Dispatchers.IO) {
                Reseau.octetsMedia(m)?.let { Media.image(it, 640) }?.asImageBitmap()
            }
        }.value
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .heightIn(max = 330.dp)
                    .clip(forme)
                    .clickable { surOuvrir(m) },
            )
            return
        }
    }
    // Tuile : vidéo, vue unique, ou photo qui n'est plus (ou pas encore) affichable.
    val ouvrable = m.media != null
    val (titre, detail) = when {
        m.media == null && m.vueUnique && m.moi -> "$quoi à vue unique" to "Envoyée"
        m.media == null && m.vueUnique -> "$quoi vue" to "Vue unique : elle a été effacée"
        m.media == null -> quoi to ""
        masquee -> "$quoi à vue unique" to "Appuie pour voir, une seule fois"
        video -> "Vidéo" to "Appuie pour lire"
        else -> "Photo" to "Chargement…"
    }
    val accent = if (m.vueUnique) Ephemere else Accent
    Row(
        modifier = Modifier
            .widthIn(max = 290.dp)
            .clip(forme)
            .background(Surface)
            .clickable(enabled = ouvrable) { surOuvrir(m) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icone(if (video) "lecture" else "photo", accent, 20.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titre, color = Texte, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (detail.isNotEmpty()) Text(detail, color = TexteDoux, fontSize = 12.sp)
        }
    }
}

// Photo ou vidéo en plein écran. Les captures d'écran sont bloquées dans toute l'appli.
@Composable
private fun Visionneuse(m: Message, onFermer: () -> Unit) {
    BackHandler(onBack = onFermer)
    DisposableEffect(m.id) {
        onDispose { Reseau.finLecture() }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        if (m.type == Media.VIDEO) {
            val fichier = produceState<java.io.File?>(null, m.id) {
                value = withContext(Dispatchers.IO) { Reseau.fichierLecture(m) }
            }.value
            if (fichier != null) {
                AndroidView(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    factory = { contexte ->
                        VideoView(contexte).apply {
                            setVideoPath(fichier.absolutePath)
                            val commandes = MediaController(contexte)
                            commandes.setAnchorView(this)
                            setMediaController(commandes)
                            setOnPreparedListener { start() }
                        }
                    },
                )
            }
        } else {
            val image = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, m.id) {
                value = withContext(Dispatchers.IO) {
                    Reseau.octetsMedia(m)?.let { Media.image(it, 2048) }?.asImageBitmap()
                }
            }.value
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onFermer),
            contentAlignment = Alignment.Center,
        ) { Icone("fermer", Color.White, 20.dp) }
        if (m.vueUnique && !m.moi) {
            Text(
                "Vue unique : effacée dès que tu fermes",
                color = Ephemere,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Réglages
// ---------------------------------------------------------------------------------------------

@Composable
private fun Reglages(onRetour: () -> Unit) {
    var numeroSaisi by remember { mutableStateOf(Reseau.monNumero.value) }
    var pseudoSaisi by remember { mutableStateOf(Reseau.monPseudo.value) }
    var codeNouveau by remember { mutableStateOf("") }
    var leurreNouveau by remember { mutableStateOf("") }
    var codeActuel by remember { mutableStateOf("") }
    var codeActuelLeurre by remember { mutableStateOf("") }
    var confirmeEffacement by remember { mutableStateOf(false) }
    var infoCode by remember { mutableStateOf("") }
    var infoLeurre by remember { mutableStateOf("") }
    var infoNumero by remember { mutableStateOf("") }
    // Relus après chaque enregistrement pour rafraîchir les libellés.
    var codePose by remember { mutableStateOf(Reseau.codeDefini()) }
    var leurrePose by remember { mutableStateOf(Reseau.leurreDefini()) }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRetour),
                contentAlignment = Alignment.Center,
            ) { Icone("retour", Texte, 22.dp) }
            Text("Réglages", color = Texte, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Carte d'identité
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Surface)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icone("cadenas", Accent, 28.dp) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val pseudo = Reseau.monPseudo.value
                    Text(
                        pseudo.ifBlank { Reseau.monNumero.value.ifBlank { "À configurer" } },
                        color = Texte,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (pseudo.isBlank()) "Pas de pseudo" else Reseau.monNumero.value.ifBlank { "Pseudo : $pseudo" },
                        color = TexteDoux,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            }

            SectionReglage("Pseudo (mode à distance)", "bulle") {
                Champ(pseudoSaisi, { pseudoSaisi = it }, "Choisis un pseudo, ex. nath39", Modifier.fillMaxWidth(), fond = Fond)
                Bouton(
                    if (Reseau.monPseudo.value.isBlank()) "Activer le mode à distance" else "Changer de pseudo",
                    modifier = Modifier.fillMaxWidth(),
                ) { Reseau.inscrirePseudo(pseudoSaisi) }
                if (Reseau.statutPseudo.value.isNotBlank()) {
                    val ok = Reseau.statutPseudo.value.startsWith("Pseudo enregistré")
                    Aide(Reseau.statutPseudo.value, if (ok) Accent else Ephemere)
                }
                Aide("Avec un pseudo, tes contacts te trouvent et t'écrivent par internet, où qu'ils soient, sans connaître ton numéro. Les messages restent chiffrés : même le relais ne peut pas les lire.")
            }

            SectionReglage("Code d'accès", "cadenas") {
                LigneEtat("Code d'accès", if (codePose) "Défini" else "À définir", if (codePose) Accent else Ephemere)
                if (codePose) Champ(
                    codeActuel,
                    { codeActuel = it.filter { c -> c.isDigit() }.take(8); infoCode = "" },
                    "Code actuel",
                    Modifier.fillMaxWidth(),
                    fond = Fond,
                    clavier = KeyboardType.NumberPassword,
                )
                Champ(
                    codeNouveau,
                    { codeNouveau = it.filter { c -> c.isDigit() }.take(8); infoCode = "" },
                    if (codePose) "Nouveau code, 4 à 8 chiffres" else "4 à 8 chiffres",
                    Modifier.fillMaxWidth(),
                    fond = Fond,
                    clavier = KeyboardType.NumberPassword,
                )
                Bouton(if (codePose) "Changer le code" else "Définir le code", modifier = Modifier.fillMaxWidth()) {
                    infoCode = when {
                        codeNouveau.length < 4 -> "4 chiffres minimum."
                        codePose && !codeActuelBon(codeActuel) -> messageCodeActuel()
                        leurrePose && Reseau.verifierLeurre(codeNouveau) -> "Ce code est déjà ton code leurre."
                        else -> {
                            Reseau.definirCode(codeNouveau)
                            codeNouveau = ""
                            codeActuel = ""
                            codePose = true
                            "Code enregistré."
                        }
                    }
                }
                if (infoCode.isNotBlank()) Aide(infoCode, if (infoCode == "Code enregistré.") Accent else Danger)
                Aide("Demandé à chaque ouverture d'une conversation. Sans lui, les messages restent masqués. Il n'existe aucun moyen de le récupérer s'il est oublié.")
            }

            SectionReglage("Code leurre", "cadenas", Ephemere) {
                LigneEtat("Code leurre", if (leurrePose) "Défini" else "Non défini", if (leurrePose) Accent else TexteDoux)
                if (codePose) Champ(
                    codeActuelLeurre,
                    { codeActuelLeurre = it.filter { c -> c.isDigit() }.take(8); infoLeurre = "" },
                    "Code d'accès actuel",
                    Modifier.fillMaxWidth(),
                    fond = Fond,
                    clavier = KeyboardType.NumberPassword,
                )
                Champ(
                    leurreNouveau,
                    { leurreNouveau = it.filter { c -> c.isDigit() }.take(8); infoLeurre = "" },
                    "Code leurre, 4 à 8 chiffres",
                    Modifier.fillMaxWidth(),
                    fond = Fond,
                    clavier = KeyboardType.NumberPassword,
                )
                Bouton(
                    if (leurrePose) "Changer le leurre" else "Définir le leurre",
                    modifier = Modifier.fillMaxWidth(),
                    couleur = Ephemere,
                ) {
                    infoLeurre = when {
                        !codePose -> "Définis d'abord le code d'accès."
                        leurreNouveau.length < 4 -> "4 chiffres minimum."
                        !codeActuelBon(codeActuelLeurre) -> messageCodeActuel()
                        Reseau.verifierCode(leurreNouveau) -> "Il doit être différent du code d'accès."
                        else -> {
                            Reseau.definirLeurre(leurreNouveau)
                            leurreNouveau = ""
                            codeActuelLeurre = ""
                            leurrePose = true
                            "Leurre enregistré."
                        }
                    }
                }
                if (infoLeurre.isNotBlank()) Aide(infoLeurre, if (infoLeurre == "Leurre enregistré.") Accent else Danger)
                Aide("Ouvre une fausse conversation à la place de la vraie. À utiliser si quelqu'un te force à ouvrir l'appli.")
            }

            SectionReglage("Numéro", "bulle") {
                Champ(
                    numeroSaisi,
                    { numeroSaisi = it; infoNumero = "" },
                    "Ton numéro, ex. 06 00 00 00 00",
                    Modifier.fillMaxWidth(),
                    fond = Fond,
                    clavier = KeyboardType.Phone,
                )
                Bouton("Enregistrer le numéro", modifier = Modifier.fillMaxWidth()) {
                    Reseau.enregistrerNumero(numeroSaisi)
                    infoNumero = "Numéro enregistré."
                }
                if (infoNumero.isNotBlank()) Aide(infoNumero, Accent)
                Aide("Sert à l'envoi par SMS et à l'invitation de tes contacts.")
            }

            SectionReglage("Messages", "bulle") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Accusés de lecture", color = Texte, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Aide("Prévient ton contact quand tu as lu ses messages. Coûte un SMS à chaque lecture hors Wi-Fi.")
                    }
                    Bascule(Reseau.accuses.value) { Reseau.definirAccuses(it) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
                Text(
                    if (confirmeEffacement) "Appuie encore pour tout effacer" else "Effacer toutes les conversations",
                    color = Danger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (confirmeEffacement) {
                            Reseau.toutEffacer()
                            confirmeEffacement = false
                        } else {
                            confirmeEffacement = true
                        }
                    },
                )
                Aide("Les contacts et les clés sont gardés.")
            }

            SectionReglage("Connexions", "reglages") {
                LigneEtat(
                    "Internet (pseudo)",
                    if (Reseau.monPseudo.value.isBlank()) "Inactif" else "Chiffré",
                    if (Reseau.monPseudo.value.isBlank()) TexteDoux else Accent,
                    "Messages à distance. Active-le en choisissant un pseudo ci-dessus",
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
                LigneEtat("SMS", "Chiffré", Accent, "Fonctionne sans internet")
                Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
                LigneEtat("MMS", "Chiffré", Accent, "Photos à distance, avec les données mobiles")
                Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
                LigneEtat("Wi-Fi local", "Partiel", Ephemere, "Texte, photos et vidéos. Chiffré dès que la clé est connue")
            }

            Text(
                "Messagerie · version 1.0",
                color = TexteDoux,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
}

// Vérifie le code actuel avant un changement, avec la même limite d'essais que le verrou.
private fun codeActuelBon(saisie: String): Boolean {
    if (Reseau.attenteCode() > 0) return false
    val bon = saisie.length >= 4 && Reseau.verifierCode(saisie)
    Reseau.noterEssai(bon)
    return bon
}

private fun messageCodeActuel(): String {
    val attente = Reseau.attenteCode()
    return if (attente > 0) "Trop d'essais. Réessaie dans ${dureeLisible(attente)}." else "Code actuel incorrect."
}

@Composable
private fun SectionReglage(
    titre: String,
    icone: String,
    couleur: Color = Accent,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icone(icone, couleur, 15.dp)
            Text(titre.uppercase(), color = TexteDoux, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenu,
        )
    }
}

@Composable
private fun LigneEtat(libelle: String, valeur: String, couleur: Color, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(libelle, color = Texte, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, color = TexteDoux, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(couleur.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(couleur))
            Text(valeur, color = couleur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Aide(texte: String, couleur: Color = TexteDoux) {
    Text(texte, color = couleur, fontSize = 12.sp, lineHeight = 17.sp)
}

// ---------------------------------------------------------------------------------------------
// Briques communes
// ---------------------------------------------------------------------------------------------

@Composable
private fun TitreSection(texte: String, couleur: Color) {
    Text(
        texte.uppercase(),
        color = couleur,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Avatar(nom: String, couleur: Color, taille: Dp) {
    Box(
        modifier = Modifier
            .size(taille)
            .clip(CircleShape)
            .background(couleur.copy(alpha = 0.16f))
            .border(1.dp, couleur.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initiale = nom.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        Text(initiale, color = couleur, fontWeight = FontWeight.Bold, fontSize = (taille.value * 0.4f).sp)
    }
}

@Composable
private fun Champ(
    valeur: String,
    surChangement: (String) -> Unit,
    indication: String,
    modifier: Modifier = Modifier,
    fond: Color = Surface,
    clavier: KeyboardType = KeyboardType.Text,
    uneLigne: Boolean = true,
    rayon: Int = 14,
) {
    TextField(
        value = valeur,
        onValueChange = surChangement,
        placeholder = { Text(indication, color = TexteDoux, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        singleLine = uneLigne,
        maxLines = if (uneLigne) 1 else 4,
        shape = RoundedCornerShape(rayon.dp),
        keyboardOptions = KeyboardOptions(keyboardType = clavier),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fond,
            unfocusedContainerColor = fond,
            focusedTextColor = Texte,
            unfocusedTextColor = Texte,
            cursorColor = Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun Bascule(actif: Boolean, surChangement: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(if (actif) Accent else SurfaceHaute)
            .clickable { surChangement(!actif) }
            .padding(4.dp),
        contentAlignment = if (actif) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (actif) Fond else TexteDoux))
    }
}

// Bouton plein (action principale) ou teinté (action secondaire).
@Composable
private fun Bouton(
    texte: String,
    modifier: Modifier = Modifier,
    couleur: Color = Accent,
    plein: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (plein) couleur else couleur.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (plein) 14.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte,
            color = if (plein) Fond else couleur,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (plein) 15.sp else 13.sp,
            maxLines = 1,
        )
    }
}

// Icônes dessinées au trait : pas de dépendance d'icônes à embarquer.
@Composable
private fun Icone(type: String, couleur: Color, taille: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(taille)) {
        val w = size.width
        val h = size.height
        val ep = w * 0.09f
        val trait = Stroke(width = ep, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(couleur, p(x1, y1), p(x2, y2), strokeWidth = ep, cap = StrokeCap.Round)
        fun brise(vararg pts: Float) {
            val chemin = Path()
            chemin.moveTo(w * pts[0], h * pts[1])
            var i = 2
            while (i < pts.size) {
                chemin.lineTo(w * pts[i], h * pts[i + 1])
                i += 2
            }
            drawPath(chemin, couleur, style = trait)
        }
        when (type) {
            "retour" -> { ligne(0.2f, 0.5f, 0.8f, 0.5f); brise(0.45f, 0.25f, 0.2f, 0.5f, 0.45f, 0.75f) }
            "suivant" -> { ligne(0.2f, 0.5f, 0.8f, 0.5f); brise(0.55f, 0.25f, 0.8f, 0.5f, 0.55f, 0.75f) }
            "envoyer" -> { ligne(0.5f, 0.8f, 0.5f, 0.2f); brise(0.25f, 0.45f, 0.5f, 0.2f, 0.75f, 0.45f) }
            "effacer" -> { ligne(0.22f, 0.5f, 0.8f, 0.5f); brise(0.44f, 0.28f, 0.22f, 0.5f, 0.44f, 0.72f) }
            "valider" -> brise(0.2f, 0.52f, 0.42f, 0.74f, 0.8f, 0.28f)
            "chevron" -> brise(0.25f, 0.4f, 0.5f, 0.65f, 0.75f, 0.4f)
            "chevronHaut" -> brise(0.25f, 0.6f, 0.5f, 0.35f, 0.75f, 0.6f)
            "chevronDroit" -> brise(0.38f, 0.25f, 0.63f, 0.5f, 0.38f, 0.75f)
            "recherche" -> {
                drawCircle(couleur, radius = w * 0.26f, center = p(0.44f, 0.44f), style = trait)
                ligne(0.64f, 0.64f, 0.84f, 0.84f)
            }
            "cadenas" -> {
                drawRoundRect(
                    couleur,
                    topLeft = p(0.2f, 0.45f),
                    size = Size(w * 0.6f, h * 0.43f),
                    cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
                    style = trait,
                )
                drawArc(
                    couleur,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(0.32f, 0.12f),
                    size = Size(w * 0.36f, h * 0.66f),
                    style = trait,
                )
            }
            "bulle" -> {
                drawRoundRect(
                    couleur,
                    topLeft = p(0.12f, 0.16f),
                    size = Size(w * 0.76f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                    style = trait,
                )
                brise(0.3f, 0.7f, 0.3f, 0.88f, 0.5f, 0.7f)
            }
            "plus" -> { ligne(0.5f, 0.2f, 0.5f, 0.8f); ligne(0.2f, 0.5f, 0.8f, 0.5f) }
            "fermer" -> { ligne(0.25f, 0.25f, 0.75f, 0.75f); ligne(0.75f, 0.25f, 0.25f, 0.75f) }
            "lecture" -> brise(0.36f, 0.24f, 0.76f, 0.5f, 0.36f, 0.76f, 0.36f, 0.24f)
            "photo" -> {
                drawRoundRect(
                    couleur,
                    topLeft = p(0.1f, 0.22f),
                    size = Size(w * 0.8f, h * 0.58f),
                    cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                    style = trait,
                )
                drawCircle(couleur, radius = w * 0.15f, center = p(0.5f, 0.51f), style = trait)
            }
            "menu" -> {
                drawCircle(couleur, radius = w * 0.075f, center = p(0.5f, 0.22f))
                drawCircle(couleur, radius = w * 0.075f, center = p(0.5f, 0.5f))
                drawCircle(couleur, radius = w * 0.075f, center = p(0.5f, 0.78f))
            }
            "reglages" -> {
                ligne(0.15f, 0.25f, 0.85f, 0.25f)
                ligne(0.15f, 0.5f, 0.85f, 0.5f)
                ligne(0.15f, 0.75f, 0.85f, 0.75f)
                drawCircle(couleur, radius = w * 0.1f, center = p(0.65f, 0.25f))
                drawCircle(couleur, radius = w * 0.1f, center = p(0.35f, 0.5f))
                drawCircle(couleur, radius = w * 0.1f, center = p(0.6f, 0.75f))
            }
        }
    }
}
