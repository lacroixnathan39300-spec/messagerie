package fr.messagerie.app.ui

import androidx.compose.animation.AnimatedVisibility
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

private val couleursContacts = listOf(Accent, Color(0xFF7AA7FF), Ephemere, Color(0xFFC594FF), Color(0xFFFF8FA3))

private fun couleurDe(nom: String): Color =
    couleursContacts[(nom.hashCode() and Int.MAX_VALUE) % couleursContacts.size]

@Composable
fun MessagerieApp() {
    var ecran by remember { mutableStateOf("discussions") }
    var appareilOuvert by remember { mutableStateOf<String?>(null) }

    MessagerieTheme {
        val ouvert = appareilOuvert
        when {
            ecran == "reglages" -> Reglages(onRetour = { ecran = "discussions" })
            ouvert != null -> Conversation(
                nom = ouvert,
                onRetour = { appareilOuvert = null },
                onReglages = { appareilOuvert = null; ecran = "reglages" },
            )
            else -> ListeDiscussions(
                onOuvrir = { appareilOuvert = it },
                onReglages = { ecran = "reglages" },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Liste des discussions
// ---------------------------------------------------------------------------------------------

@Composable
private fun ListeDiscussions(onOuvrir: (String) -> Unit, onReglages: () -> Unit) {
    val appareils = Reseau.appareils.values.filter { it.accepte }.sortedBy { Reseau.nomAffiche(it.nom).lowercase() }
    val demandes = Reseau.appareils.values.filter { !it.accepte }
    var saisie by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf(false) }

    fun ouvrirSaisie() {
        val propre = saisie.trim()
        if (propre.isEmpty()) return
        if (propre.any { it.isLetter() }) {
            Reseau.ouvrirPseudo(propre) { trouve ->
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
                            if (appareils.size > 1) "${appareils.size} contacts" else "${appareils.size} contact",
                            color = TexteDoux,
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
                if (erreur) {
                    Text(
                        "Introuvable. Vérifie le pseudo ou le numéro.",
                        color = Danger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
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
                            "Saisis un pseudo ou un numéro ci-dessus, ou ouvre l'appli sur un autre téléphone du même Wi-Fi.",
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
                Text("Sans serveur, par SMS", color = TexteDoux, fontSize = 13.sp)
            }
            Icone(if (ouvert) "chevronHaut" else "chevron", TexteDoux, 18.dp)
        }
        AnimatedVisibility(visible = ouvert) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Champ(numero, { numero = it }, "Numéro du contact", Modifier.fillMaxWidth(), fond = Fond, clavier = KeyboardType.Phone)
                Bouton("Créer un code", modifier = Modifier.fillMaxWidth()) { Reseau.creerLiaison(numero) }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Code reçu de ${Reseau.nomAffiche(numero)}", color = TexteDoux, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Champ(code, { code = it }, "12 chiffres", Modifier.weight(1f), fond = Fond, clavier = KeyboardType.Number)
            Bouton("Valider") { Reseau.validerLiaison(numero, code) }
        }
    }
}

@Composable
private fun LigneAppareil(a: Appareil, onClick: () -> Unit) {
    val nom = Reseau.nomAffiche(a.nom)
    val dernier = Reseau.messages.lastOrNull { it.avec == a.nom }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(nom, couleurDe(a.nom), 50.dp)
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
                if (dernier != null) "Message chiffré" else "Pas encore de message",
                color = TexteDoux,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icone("chevronDroit", Separateur, 16.dp)
    }
}

// ---------------------------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------------------------

// Fausse conversation affichée avec le code leurre : rien de réel, rien n'est envoyé.
private val LEURRE = listOf(
    Message("", "Moi", "Tu passes à la boulangerie ?", moi = true),
    Message("", "Moi", "Ok, je prends du pain complet.", moi = false),
    Message("", "Moi", "Merci, à ce soir.", moi = true),
)

@Composable
private fun Conversation(nom: String, onRetour: () -> Unit, onReglages: () -> Unit) {
    var saisie by remember { mutableStateOf("") }
    // Verrouillée à chaque ouverture : "verrouillee", "reelle" (vrais messages) ou "leurre" (fausse conversation).
    var mode by remember { mutableStateOf("verrouillee") }
    val filLeurre = remember { mutableStateListOf<Message>().apply { addAll(LEURRE) } }
    val fil = if (mode == "leurre") filLeurre else Reseau.messages.filter { it.avec == nom }
    val contact = Reseau.appareils[nom]
    val nomAffiche = Reseau.nomAffiche(nom)
    val etatListe = rememberLazyListState()

    LaunchedEffect(fil.size, mode) {
        if (mode != "verrouillee" && fil.isNotEmpty()) etatListe.animateScrollToItem(fil.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                val code = contact?.cle?.let { Reseau.codeSecurite(it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (code != null) Icone("cadenas", Accent, 11.dp)
                    Text(
                        if (code != null) "Code de sécurité $code" else "Clé pas encore échangée",
                        color = if (code != null) TexteDoux else Ephemere,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            if (contact?.cle == null && contact?.numero != null) {
                Bouton("Envoyer ma clé", plein = false) { Reseau.envoyerCle(nom) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))

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
                if (!Reseau.codeDefini()) {
                    Text("Aucun code défini", color = Texte, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Choisis un code d'accès dans les réglages pour lire et envoyer des messages.",
                        color = TexteDoux,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Bouton("Ouvrir les réglages", onClick = onReglages)
                } else {
                    Text("Conversation verrouillée", color = Texte, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Saisis ton code pour afficher les messages", color = TexteDoux, fontSize = 14.sp)
                    Spacer(Modifier.height(22.dp))
                    Clavier { code ->
                        when {
                            Reseau.verifierCode(code) -> { mode = "reelle"; true }
                            Reseau.verifierLeurre(code) -> { mode = "leurre"; true }
                            else -> false
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(fil) { m -> Bulle(m) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Champ(saisie, { saisie = it }, "Message", Modifier.weight(1f), uneLigne = false, rayon = 26)
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (saisie.isBlank()) SurfaceHaute else Accent)
                        .clickable {
                            val texte = saisie.trim()
                            if (texte.isNotEmpty()) {
                                // En mode leurre, le message reste à l'écran et ne part nulle part.
                                if (mode == "leurre") filLeurre.add(Message("", "Moi", texte, moi = true))
                                else Reseau.envoyer(nom, texte)
                                saisie = ""
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icone("envoyer", if (saisie.isBlank()) TexteDoux else Fond, 22.dp) }
            }
        }
    }
}

// Clavier à chiffres du verrou. `surCode` renvoie vrai si le code ouvre quelque chose.
@Composable
private fun Clavier(surCode: (String) -> Boolean) {
    var code by remember { mutableStateOf("") }
    var faux by remember { mutableStateOf(false) }

    fun valider() {
        if (code.length < 4) return
        if (!surCode(code)) faux = true
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
    Text(if (faux) "Code incorrect" else " ", color = Danger, fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("123", "456", "789").forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                rangee.forEach { c ->
                    Touche(onClick = { if (code.length < 8) { code += c; faux = false } }) {
                        Text(c.toString(), color = Texte, fontSize = 26.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Touche(fond = Color.Transparent, onClick = { code = code.dropLast(1) }) { Icone("effacer", TexteDoux, 24.dp) }
            Touche(onClick = { if (code.length < 8) { code += "0"; faux = false } }) {
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
private fun Bulle(m: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.moi) Arrangement.End else Arrangement.Start,
    ) {
        val forme = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomEnd = if (m.moi) 5.dp else 20.dp,
            bottomStart = if (m.moi) 20.dp else 5.dp,
        )
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
}

// ---------------------------------------------------------------------------------------------
// Réglages
// ---------------------------------------------------------------------------------------------

@Composable
private fun Reglages(onRetour: () -> Unit) {
    var pseudoSaisi by remember { mutableStateOf(Reseau.monPseudo.value) }
    var numeroSaisi by remember { mutableStateOf(Reseau.monNumero.value) }
    var codeNouveau by remember { mutableStateOf("") }
    var leurreNouveau by remember { mutableStateOf("") }
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
                val pseudo = Reseau.monPseudo.value
                Avatar(pseudo.ifBlank { "?" }, Accent, 60.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        pseudo.ifBlank { "Sans pseudo" },
                        color = Texte,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(Reseau.monNumero.value.ifBlank { "Numéro non renseigné" }, color = TexteDoux, fontSize = 14.sp)
                }
            }

            SectionReglage("Profil", "bulle") {
                Champ(pseudoSaisi, { pseudoSaisi = it }, "Pseudo, ex. camille_42", Modifier.fillMaxWidth(), fond = Fond)
                Bouton("Enregistrer le pseudo", modifier = Modifier.fillMaxWidth()) { Reseau.inscrirePseudo(pseudoSaisi) }
                if (Reseau.statutPseudo.value.isNotBlank()) Aide(Reseau.statutPseudo.value, Ephemere)
                Aide("Sert à te trouver sans donner ton numéro. Demande une connexion internet.")
            }

            SectionReglage("Code d'accès", "cadenas") {
                LigneEtat("Code d'accès", if (codePose) "Défini" else "À définir", if (codePose) Accent else Ephemere)
                Champ(
                    codeNouveau,
                    { codeNouveau = it.filter { c -> c.isDigit() }.take(8); infoCode = "" },
                    "4 à 8 chiffres",
                    Modifier.fillMaxWidth(),
                    fond = Fond,
                    clavier = KeyboardType.NumberPassword,
                )
                Bouton(if (codePose) "Changer le code" else "Définir le code", modifier = Modifier.fillMaxWidth()) {
                    infoCode = when {
                        codeNouveau.length < 4 -> "4 chiffres minimum."
                        leurrePose && Reseau.verifierLeurre(codeNouveau) -> "Ce code est déjà ton code leurre."
                        else -> {
                            Reseau.definirCode(codeNouveau)
                            codeNouveau = ""
                            codePose = true
                            "Code enregistré."
                        }
                    }
                }
                if (infoCode.isNotBlank()) Aide(infoCode, if (infoCode == "Code enregistré.") Accent else Danger)
                Aide("Demandé à chaque ouverture d'une conversation. Sans lui, les messages restent masqués.")
            }

            SectionReglage("Code leurre", "cadenas", Ephemere) {
                LigneEtat("Code leurre", if (leurrePose) "Défini" else "Non défini", if (leurrePose) Accent else TexteDoux)
                Champ(
                    leurreNouveau,
                    { leurreNouveau = it.filter { c -> c.isDigit() }.take(8); infoLeurre = "" },
                    "4 à 8 chiffres, différent du code",
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
                        leurreNouveau.length < 4 -> "4 chiffres minimum."
                        codePose && Reseau.verifierCode(leurreNouveau) -> "Il doit être différent du code d'accès."
                        else -> {
                            Reseau.definirLeurre(leurreNouveau)
                            leurreNouveau = ""
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

            SectionReglage("Connexions", "reglages") {
                LigneEtat("SMS", "Chiffré", Accent, "Fonctionne sans internet")
                Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
                LigneEtat("Wi-Fi local", "Non chiffré", Ephemere, "Entre téléphones du même réseau")
                Box(Modifier.fillMaxWidth().height(1.dp).background(Separateur))
                LigneEtat(
                    "Relais internet",
                    if (Reseau.monPseudo.value.isBlank()) "Pseudo requis" else "Chiffré",
                    if (Reseau.monPseudo.value.isBlank()) TexteDoux else Accent,
                    "Interrogé seulement quand l'appli est ouverte",
                )
            }

            Text(
                "Messagerie · version de test",
                color = TexteDoux,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )
        }
    }
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
