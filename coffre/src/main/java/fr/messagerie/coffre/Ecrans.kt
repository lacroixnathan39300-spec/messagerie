package fr.messagerie.coffre

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Fond = Color(0xFF0E1116)
val Surface = Color(0xFF171B22)
val SurfaceHaute = Color(0xFF1E242E)
val Separateur = Color(0xFF1F2530)
val Texte = Color(0xFFE8ECF2)
val TexteDoux = Color(0xFF8A93A3)
val Accent = Color(0xFF3DDC97)
val Ephemere = Color(0xFFF5A524)
val Danger = Color(0xFFFF6B6B)

@Composable
fun CoffreTheme(contenu: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Fond, surface = Surface, primary = Accent,
            onPrimary = Fond, onBackground = Texte, onSurface = Texte, secondary = Ephemere,
        ),
        content = contenu,
    )
}

@Composable
fun CoffreApp() {
    CoffreTheme {
        if (!Coffre.ouvert.value) {
            Verrou()
        } else {
            var detail by remember { mutableStateOf<Element?>(null) }
            var edition by remember { mutableStateOf<Element?>(null) }
            var nouveau by remember { mutableStateOf<String?>(null) }
            var importer by remember { mutableStateOf(false) }
            var reglages by remember { mutableStateOf(false) }

            when {
                reglages -> Reglages(onRetour = { reglages = false })
                importer -> Importer(onRetour = { importer = false })
                nouveau != null -> Edition(gabarit(nouveau!!), estNouveau = true, onRetour = { nouveau = null })
                edition != null -> Edition(edition!!, estNouveau = false, onRetour = { edition = null })
                detail != null -> {
                    // L'élément peut avoir été modifié : on relit la version à jour.
                    val actuel = Coffre.elements.firstOrNull { it.id == detail!!.id }
                    if (actuel == null) detail = null else Fiche(
                        actuel,
                        onRetour = { detail = null },
                        onModifier = { edition = actuel; detail = null },
                    )
                }
                else -> Liste(
                    onOuvrir = { detail = it },
                    onNouveau = { nouveau = it },
                    onImporter = { importer = true },
                    onReglages = { reglages = true },
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Verrou (création ou ouverture)
// ------------------------------------------------------------------------------------------

@Composable
private fun Verrou() {
    val existe = remember { Coffre.coffreExiste() }
    var code by remember { mutableStateOf("") }
    var confirme by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icone("bouclier", Accent, 40.dp) }
        Spacer(Modifier.height(20.dp))
        Text("Coffre", color = Texte, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (existe) "Saisis ton code maître pour ouvrir le coffre." else "Choisis un code maître. Il chiffre tout le coffre.",
            color = TexteDoux, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(26.dp))
        ChampSecret(code, { code = it; erreur = "" }, "Code maître")
        if (!existe) {
            Spacer(Modifier.height(10.dp))
            ChampSecret(confirme, { confirme = it; erreur = "" }, "Confirme le code")
        }
        if (erreur.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(erreur, color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Bouton(if (existe) "Ouvrir" else "Créer le coffre", modifier = Modifier.fillMaxWidth()) {
            if (existe) {
                val attente = Coffre.attente()
                if (attente > 0) {
                    erreur = "Trop d'essais. Réessaie dans ${dureeLisible(attente)}."
                } else if (!Coffre.ouvrir(code)) {
                    Coffre.noterEssai(false)
                    val a = Coffre.attente()
                    erreur = if (a > 0) "Code incorrect. Réessaie dans ${dureeLisible(a)}." else "Code incorrect."
                }
            } else {
                when {
                    code.length < 4 -> erreur = "Au moins 4 caractères."
                    code != confirme -> erreur = "Les deux codes ne correspondent pas."
                    !Coffre.creer(code) -> erreur = "Code trop court."
                }
            }
        }
        if (!existe) {
            Spacer(Modifier.height(14.dp))
            Aide("Retiens-le bien : il n'existe aucun moyen de le récupérer. Sans lui, le coffre est définitivement illisible.")
        }
    }
}

// ------------------------------------------------------------------------------------------
// Liste
// ------------------------------------------------------------------------------------------

@Composable
private fun Liste(onOuvrir: (Element) -> Unit, onNouveau: (String) -> Unit, onImporter: () -> Unit, onReglages: () -> Unit) {
    var recherche by remember { mutableStateOf("") }
    var menuAjout by remember { mutableStateOf(false) }
    val elements = Coffre.elements
        .filter { recherche.isBlank() || it.titre.contains(recherche, true) || Type.libelle(it.type).contains(recherche, true) }
        .sortedByDescending { it.maj }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Coffre", color = Texte, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${Coffre.elements.size} élément${if (Coffre.elements.size > 1) "s" else ""} · chiffré", color = TexteDoux, fontSize = 13.sp)
            }
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onImporter), contentAlignment = Alignment.Center) {
                Icone("importer", Texte, 22.dp)
            }
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onReglages), contentAlignment = Alignment.Center) {
                Icone("reglages", Texte, 22.dp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp)).background(Surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone("recherche", TexteDoux, 20.dp)
            Champ(recherche, { recherche = it }, "Rechercher", Modifier.weight(1f), fond = Color.Transparent)
        }

        if (elements.isEmpty()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Surface), contentAlignment = Alignment.Center) { Icone("bouclier", TexteDoux, 30.dp) }
                Spacer(Modifier.height(12.dp))
                Text(if (recherche.isBlank()) "Coffre vide" else "Aucun résultat", color = Texte, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (recherche.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ajoute un mot de passe, une carte ou une note avec le bouton +.", color = TexteDoux, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.height(2.dp)) }
                items(elements, key = { it.id }) { e -> LigneElement(e, onClick = { onOuvrir(e) }) }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Bouton + et menu de choix du type.
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 0.dp)) {
        Column(modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.End) {
            if (menuAjout) {
                listOf(Type.MDP, Type.CARTE, Type.VITALE, Type.NOTE).forEach { t ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(50)).background(Surface)
                            .clickable { menuAjout = false; onNouveau(t) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icone(iconeType(t), Accent, 18.dp)
                        Text(Type.libelle(t), color = Texte, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(Accent).clickable { menuAjout = !menuAjout },
                contentAlignment = Alignment.Center,
            ) { Icone(if (menuAjout) "fermer" else "plus", Fond, 26.dp) }
        }
    }
}

@Composable
private fun LigneElement(e: Element, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icone(iconeType(e.type), Accent, 20.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.titre.ifBlank { Type.libelle(e.type) }, color = Texte, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Type.libelle(e.type), color = TexteDoux, fontSize = 13.sp)
        }
        Icone("chevronDroit", Separateur, 16.dp)
    }
}

// ------------------------------------------------------------------------------------------
// Fiche (détail)
// ------------------------------------------------------------------------------------------

@Composable
private fun Fiche(e: Element, onRetour: () -> Unit, onModifier: () -> Unit) {
    BackHandler(onBack = onRetour)
    var partage by remember { mutableStateOf(false) }
    var confirmeSuppr by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        EnTete(e.titre.ifBlank { Type.libelle(e.type) }, onRetour) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onModifier), contentAlignment = Alignment.Center) { Icone("crayon", Texte, 20.dp) }
        }
        Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icone(iconeType(e.type), Accent, 16.dp)
            Text(Type.libelle(e.type), color = TexteDoux, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            e.champs.filter { it.valeur.isNotBlank() }.forEach { ChampFiche(it) }
        }
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Bouton("Partager par SMS", modifier = Modifier.fillMaxWidth(), plein = false) { partage = true }
            Text(
                if (confirmeSuppr) "Appuie encore pour supprimer" else "Supprimer",
                color = Danger, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable {
                    if (confirmeSuppr) { Coffre.supprimer(e.id); onRetour() } else confirmeSuppr = true
                },
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (partage) DialoguePartage(e, onFermer = { partage = false })
}

@Composable
private fun ChampFiche(c: Champ) {
    val contexte = LocalContext.current
    var visible by remember { mutableStateOf(!c.secret) }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(c.libelle, color = TexteDoux, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (visible) c.valeur else "•".repeat(c.valeur.length.coerceAtMost(14)),
                color = Texte, fontSize = 16.sp, modifier = Modifier.weight(1f),
                maxLines = if (visible) 8 else 1, overflow = TextOverflow.Ellipsis,
            )
            if (c.secret) Box(modifier = Modifier.size(34.dp).clip(CircleShape).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
                Icone(if (visible) "oeilBarre" else "oeil", TexteDoux, 20.dp)
            }
            Box(modifier = Modifier.size(34.dp).clip(CircleShape).clickable { copier(contexte, c.libelle, c.valeur, c.secret) }, contentAlignment = Alignment.Center) {
                Icone("copier", Accent, 18.dp)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Édition / création
// ------------------------------------------------------------------------------------------

@Composable
private fun Edition(depart: Element, estNouveau: Boolean, onRetour: () -> Unit) {
    BackHandler(onBack = onRetour)
    var titre by remember { mutableStateOf(depart.titre) }
    val champs = remember { depart.champs.map { mutableStateOf(it) }.toMutableList() }
    var tick by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        EnTete(if (estNouveau) "Nouveau · ${Type.libelle(depart.type)}" else "Modifier", onRetour) {
            Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent).clickable {
                val liste = champs.map { it.value }.filter { it.libelle.isNotBlank() || it.valeur.isNotBlank() }
                val e = depart.copy(titre = titre.trim(), champs = liste)
                if (estNouveau) Coffre.ajouter(e) else Coffre.modifier(e)
                onRetour()
            }.padding(horizontal = 16.dp, vertical = 9.dp)) { Text("Enregistrer", color = Fond, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TITRE", color = TexteDoux, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Champ(titre, { titre = it }, "Ex. Gmail, Carte Crédit Agricole…", Modifier.fillMaxWidth())
            }
            key(tick) {
                champs.forEachIndexed { i, etat ->
                    ChampEdition(
                        etat.value,
                        surChangement = { etat.value = it },
                        surGenerer = if (etat.value.secret) { { etat.value = etat.value.copy(valeur = Coffre.genererMotDePasse()) } } else null,
                        surSupprimer = { champs.removeAt(i); tick++ },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Separateur, RoundedCornerShape(12.dp))
                    .clickable { champs.add(mutableStateOf(Champ("", "", false))); tick++ }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone("plus", Accent, 18.dp); Spacer(Modifier.width(6.dp)); Text("Ajouter un champ", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ChampEdition(c: Champ, surChangement: (Champ) -> Unit, surGenerer: (() -> Unit)?, surSupprimer: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Champ(c.libelle, { surChangement(c.copy(libelle = it)) }, "Libellé (ex. Mot de passe)", Modifier.weight(1f), fond = Fond)
            Box(modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = surSupprimer), contentAlignment = Alignment.Center) { Icone("corbeille", TexteDoux, 18.dp) }
        }
        Champ(c.valeur, { surChangement(c.copy(valeur = it)) }, "Valeur", Modifier.fillMaxWidth(), fond = Fond, uneLigne = !c.secret && c.libelle.lowercase().contains("note").not())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { surChangement(c.copy(secret = !c.secret)) }) {
                Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (c.secret) Accent else SurfaceHaute), contentAlignment = Alignment.Center) {
                    if (c.secret) Icone("valider", Fond, 14.dp)
                }
                Text("Masquer", color = TexteDoux, fontSize = 13.sp)
            }
            if (surGenerer != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clickable(onClick = surGenerer)) {
                Icone("des", Accent, 16.dp); Text("Générer", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Partage et import
// ------------------------------------------------------------------------------------------

@Composable
private fun DialoguePartage(e: Element, onFermer: () -> Unit) {
    val contexte = LocalContext.current
    var phrase by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onFermer), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Surface).clickable(enabled = false) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Partager par SMS", color = Texte, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Aide("L'élément part chiffré. Donne la phrase secrète à la personne de vive voix : elle en a besoin pour l'ouvrir. Ne l'envoie pas dans le même message.")
            ChampSecret(phrase, { phrase = it }, "Phrase secrète de partage")
            Bouton("Préparer le SMS", modifier = Modifier.fillMaxWidth()) {
                if (phrase.length < 4) return@Bouton
                val blob = Coffre.emballerPartage(e, phrase)
                try {
                    contexte.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", blob).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ex: Exception) {
                    copier(contexte, "partage", blob, false)
                    Toast.makeText(contexte, "Message copié : colle-le dans ton appli SMS.", Toast.LENGTH_LONG).show()
                }
                onFermer()
            }
            Text("Annuler", color = TexteDoux, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp).clickable(onClick = onFermer), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Importer(onRetour: () -> Unit) {
    BackHandler(onBack = onRetour)
    var texte by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        EnTete("Importer un élément reçu", onRetour) {}
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Aide("Colle le message reçu (il commence par CFR1:) puis saisis la phrase secrète que la personne t'a donnée.")
            Champ(texte, { texte = it; message = "" }, "Colle le message reçu ici", Modifier.fillMaxWidth(), uneLigne = false)
            ChampSecret(phrase, { phrase = it; message = "" }, "Phrase secrète")
            Bouton("Ouvrir et ajouter", modifier = Modifier.fillMaxWidth()) {
                val e = Coffre.ouvrirPartage(texte, phrase)
                if (e == null) {
                    message = "Impossible d'ouvrir : phrase incorrecte ou message abîmé."
                } else {
                    Coffre.ajouter(e.copy(id = java.util.UUID.randomUUID().toString()))
                    message = "Ajouté au coffre : ${e.titre.ifBlank { Type.libelle(e.type) }}."
                }
            }
            if (message.isNotEmpty()) Aide(message, if (message.startsWith("Ajouté")) Accent else Danger)
        }
    }
}

// ------------------------------------------------------------------------------------------
// Réglages
// ------------------------------------------------------------------------------------------

@Composable
private fun Reglages(onRetour: () -> Unit) {
    BackHandler(onBack = onRetour)
    var ancien by remember { mutableStateOf("") }
    var neuf by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding().imePadding()) {
        EnTete("Réglages", onRetour) {}
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Changer le code maître", color = Texte, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                ChampSecret(ancien, { ancien = it; message = "" }, "Code actuel")
                ChampSecret(neuf, { neuf = it; message = "" }, "Nouveau code (4 caractères min.)")
                Bouton("Changer le code", modifier = Modifier.fillMaxWidth()) {
                    message = if (neuf.length < 4) "Nouveau code trop court."
                    else if (Coffre.changerCode(ancien, neuf)) "Code changé."
                    else "Code actuel incorrect."
                }
                if (message.isNotEmpty()) Aide(message, if (message == "Code changé.") Accent else Danger)
            }
            Bouton("Verrouiller le coffre", modifier = Modifier.fillMaxWidth(), plein = false) { Coffre.verrouiller() }
            Text("Coffre · version 1.0", color = TexteDoux, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ------------------------------------------------------------------------------------------
// Briques communes
// ------------------------------------------------------------------------------------------

private fun gabarit(type: String): Element {
    val champs = when (type) {
        Type.MDP -> listOf(Champ("Identifiant", ""), Champ("Mot de passe", "", true), Champ("Site / note", ""))
        Type.CARTE -> listOf(Champ("Titulaire", ""), Champ("Numéro", "", true), Champ("Expiration", ""), Champ("Cryptogramme (CVV)", "", true), Champ("Code PIN", "", true))
        Type.VITALE -> listOf(Champ("Titulaire", ""), Champ("Numéro de sécurité sociale", "", true))
        else -> listOf(Champ("Contenu", "", true))
    }
    return Element(type = type, titre = "", champs = champs)
}

private fun iconeType(t: String) = when (t) {
    Type.MDP -> "cle"; Type.CARTE -> "carte"; Type.VITALE -> "croix"; else -> "note"
}

private fun dureeLisible(s: Int) = if (s >= 60) "${(s + 59) / 60} min" else "$s s"

private fun copier(contexte: Context, libelle: String, valeur: String, secret: Boolean) {
    val cm = contexte.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(libelle, valeur)
    if (secret) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    cm.setPrimaryClip(clip)
    Toast.makeText(contexte, "$libelle copié", Toast.LENGTH_SHORT).show()
}

@Composable
private fun EnTete(titre: String, onRetour: () -> Unit, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRetour), contentAlignment = Alignment.Center) { Icone("retour", Texte, 22.dp) }
        Text(titre, color = Texte, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun Champ(valeur: String, surChangement: (String) -> Unit, indication: String, modifier: Modifier = Modifier, fond: Color = Surface, uneLigne: Boolean = true) {
    TextField(
        value = valeur, onValueChange = surChangement,
        placeholder = { Text(indication, color = TexteDoux, fontSize = 15.sp) },
        modifier = modifier, singleLine = uneLigne, maxLines = if (uneLigne) 1 else 8, shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fond, unfocusedContainerColor = fond, focusedTextColor = Texte, unfocusedTextColor = Texte,
            cursorColor = Accent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ChampSecret(valeur: String, surChangement: (String) -> Unit, indication: String) {
    var visible by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = valeur, onValueChange = surChangement,
            placeholder = { Text(indication, color = TexteDoux, fontSize = 15.sp) },
            modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
            visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedTextColor = Texte, unfocusedTextColor = Texte,
                cursorColor = Accent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
            Icone(if (visible) "oeilBarre" else "oeil", TexteDoux, 20.dp)
        }
    }
}

@Composable
private fun Bouton(texte: String, modifier: Modifier = Modifier, plein: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(if (plein) Accent else Accent.copy(alpha = 0.14f)).clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texte, color = if (plein) Fond else Accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable
private fun Aide(texte: String, couleur: Color = TexteDoux) {
    Text(texte, color = couleur, fontSize = 12.sp, lineHeight = 17.sp)
}
