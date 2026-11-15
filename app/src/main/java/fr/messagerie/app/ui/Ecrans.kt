package fr.messagerie.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.app.reseau.Appareil
import fr.messagerie.app.reseau.Message
import fr.messagerie.app.reseau.Reseau

private val couleursContacts = listOf(Accent, Color(0xFF7AA7FF), Ephemere, Color(0xFFC594FF))

@Composable
fun MessagerieApp() {
    var ecran by remember { mutableStateOf("discussions") }
    var appareilOuvert by remember { mutableStateOf<String?>(null) }

    MessagerieTheme {
        val ouvert = appareilOuvert
        when {
            ecran == "reglages" -> Reglages(onRetour = { ecran = "discussions" })
            ouvert != null -> Conversation(nom = ouvert, onRetour = { appareilOuvert = null })
            else -> ListeDiscussions(
                onOuvrir = { appareilOuvert = it },
                onReglages = { ecran = "reglages" },
            )
        }
    }
}

@Composable
private fun ListeDiscussions(onOuvrir: (String) -> Unit, onReglages: () -> Unit) {
    val appareils = Reseau.appareils.values.filter { it.accepte }.sortedBy { it.nom }
    val demandes = Reseau.appareils.values.filter { !it.accepte }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Discussions", color = Texte, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Puce("Chiffrement à venir", Ephemere)
        }
        Text(
            "Appareils à proximité (même Wi-Fi) : ${appareils.size}",
            color = TexteDoux,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        var numeroSaisi by remember { mutableStateOf("") }
        var erreur by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = numeroSaisi,
                onValueChange = { numeroSaisi = it; erreur = false },
                placeholder = { Text("Pseudo ou numéro", color = TexteDoux) },
                isError = erreur,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedTextColor = Texte,
                    unfocusedTextColor = Texte,
                    cursorColor = Accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Accent)
                    .clickable {
                        val saisie = numeroSaisi.trim()
                        if (saisie.any { it.isLetter() }) {
                            Reseau.ouvrirPseudo(saisie) { trouve ->
                                if (trouve != null) {
                                    numeroSaisi = ""
                                    onOuvrir(trouve)
                                } else {
                                    erreur = true
                                }
                            }
                        } else {
                            val ouvert = Reseau.ouvrirNumero(saisie)
                            if (ouvert != null) {
                                numeroSaisi = ""
                                onOuvrir(ouvert)
                            } else {
                                erreur = true
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("OK", color = Fond, fontWeight = FontWeight.SemiBold)
            }
        }
        LiaisonParCode()
        if (demandes.isNotEmpty()) {
            Text(
                "Demandes (${demandes.size})",
                color = Ephemere,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            demandes.forEach { d ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(Reseau.nomAffiche(d.nom), color = Texte, fontSize = 15.sp)
                    Puce("Accepter", Accent, modifier = Modifier.clickable { Reseau.accepter(d.nom) })
                }
            }
        }
        if (appareils.isEmpty()) {
            Text(
                "Aucun appareil trouvé. Ouvre la messagerie sur l'autre téléphone, connecté au même Wi-Fi.",
                color = TexteDoux,
                fontSize = 14.sp,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(appareils) { a ->
                LigneAppareil(a, onClick = { onOuvrir(a.nom) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(Fond).navigationBarsPadding().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("Discussions", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "Réglages",
                color = TexteDoux,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onReglages),
            )
        }
    }
}

@Composable
private fun LiaisonParCode() {
    var numero by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lier quelqu'un par code (sans serveur, par SMS)", color = TexteDoux, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = numero,
                onValueChange = { numero = it },
                placeholder = { Text("Numéro", color = TexteDoux) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedTextColor = Texte,
                    unfocusedTextColor = Texte,
                    cursorColor = Accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Puce("Créer un code", Accent, modifier = Modifier.clickable { Reseau.creerLiaison(numero) })
        }
        if (Reseau.statutLiaison.value.isNotBlank()) {
            Text(Reseau.statutLiaison.value, color = Ephemere, fontSize = 13.sp)
        }
        Reseau.liaisonsEntrantes.keys.forEach { n -> LiaisonEntrante(n) }
    }
}

@Composable
private fun LiaisonEntrante(numero: String) {
    var code by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = code,
            onValueChange = { code = it },
            placeholder = { Text("12 chiffres de $numero", color = TexteDoux) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedTextColor = Texte,
                unfocusedTextColor = Texte,
                cursorColor = Accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Puce("Valider", Accent, modifier = Modifier.clickable { Reseau.validerLiaison(numero, code) })
    }
}

@Composable
private fun LigneAppareil(a: Appareil, onClick: () -> Unit) {
    val couleur = couleursContacts[(a.nom.hashCode() and Int.MAX_VALUE) % couleursContacts.size]
    val dernier = Reseau.messages.lastOrNull { it.avec == a.nom }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(couleur),
            contentAlignment = Alignment.Center,
        ) {
            Text(a.nom.take(1).uppercase(), color = Fond, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(Reseau.nomAffiche(a.nom), color = Texte, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                dernier?.texte ?: "Pas encore de message",
                color = TexteDoux,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Fausse conversation affichée avec le code leurre : rien de réel, aucune saisie possible.
private val LEURRE = listOf(
    Message("", "Moi", "Tu passes à la boulangerie ?", moi = true),
    Message("", "Moi", "Ok, je prends du pain complet.", moi = false),
    Message("", "Moi", "Merci, à ce soir.", moi = true),
)

@Composable
private fun Conversation(nom: String, onRetour: () -> Unit) {
    var saisie by remember { mutableStateOf("") }
    // Verrouillée à chaque ouverture : les messages restent masqués tant que le code n'est pas saisi.
    // "verrouillee", "reelle" (vrais messages) ou "leurre" (fausse conversation).
    var mode by remember { mutableStateOf("verrouillee") }
    var codeSaisi by remember { mutableStateOf("") }
    var codeFaux by remember { mutableStateOf(false) }
    val fil = if (mode == "leurre") LEURRE else Reseau.messages.filter { it.avec == nom }

    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("←", color = Texte, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onRetour))
            Column(modifier = Modifier.weight(1f)) {
                Text(Reseau.nomAffiche(nom), color = Texte, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val contact = Reseau.appareils[nom]
                val code = contact?.cle?.let { Reseau.codeSecurite(it) }
                Text(
                    if (code != null) "Code de sécurité : $code" else "Clé pas encore échangée",
                    color = TexteDoux,
                    fontSize = 12.sp,
                )
            }
            if (Reseau.appareils[nom]?.cle == null && Reseau.appareils[nom]?.numero != null) {
                Puce("Envoyer ma clé", Accent, modifier = Modifier.clickable { Reseau.envoyerCle(nom) })
            }
        }
        if (mode == "verrouillee") {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!Reseau.codeDefini()) {
                    Text("Définis d'abord un code dans Réglages pour lire les messages.", color = TexteDoux, fontSize = 14.sp)
                } else {
                    Text("Messages masqués. Saisis ton code pour les afficher.", color = TexteDoux, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = codeSaisi,
                        onValueChange = { codeSaisi = it; codeFaux = false },
                        placeholder = { Text("Code", color = TexteDoux) },
                        isError = codeFaux,
                        shape = RoundedCornerShape(50),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                            focusedTextColor = Texte,
                            unfocusedTextColor = Texte,
                            cursorColor = Accent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Puce("Déverrouiller", Accent, modifier = Modifier.clickable {
                        if (Reseau.verifierCode(codeSaisi)) {
                            mode = "reelle"
                            codeSaisi = ""
                        } else if (Reseau.verifierLeurre(codeSaisi)) {
                            mode = "leurre"
                            codeSaisi = ""
                        } else {
                            codeFaux = true
                        }
                    })
                    if (codeFaux) Text("Code incorrect", color = Ephemere, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(fil) { m -> Bulle(m) }
            }
        }
        if (mode == "reelle") Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = saisie,
                onValueChange = { saisie = it },
                placeholder = { Text("Message", color = TexteDoux) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedTextColor = Texte,
                    unfocusedTextColor = Texte,
                    cursorColor = Accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Accent)
                    .clickable {
                        if (saisie.isNotBlank()) {
                            Reseau.envoyer(nom, saisie.trim())
                            saisie = ""
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("Envoyer", color = Fond, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Reglages(onRetour: () -> Unit) {
    var pseudoSaisi by remember { mutableStateOf(Reseau.monPseudo.value) }
    var numeroSaisi by remember { mutableStateOf(Reseau.monNumero.value) }
    var codeNouveau by remember { mutableStateOf("") }
    var leurreNouveau by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fond)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("←", color = Texte, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onRetour))
            Text("Réglages", color = Texte, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        SectionReglage("Profil") {
            ChampReglage(pseudoSaisi, { pseudoSaisi = it }, "Pseudo, ex. camille_42")
            Aide("Visible par les autres. Ton numéro n'est jamais partagé avec le relais.")
            BoutonReglage("Enregistrer le pseudo", Accent, Fond) { Reseau.inscrirePseudo(pseudoSaisi) }
            if (Reseau.statutPseudo.value.isNotBlank()) Aide(Reseau.statutPseudo.value)
        }

        SectionReglage("Sécurité") {
            ChampReglage(codeNouveau, { codeNouveau = it.filter { c -> c.isDigit() }.take(8) }, "Code d'accès (4 à 8 chiffres)", chiffres = true)
            BoutonReglage(if (Reseau.codeDefini()) "Changer le code" else "Définir le code", Accent, Fond) {
                if (codeNouveau.length >= 4) {
                    Reseau.definirCode(codeNouveau)
                    codeNouveau = ""
                }
            }
            Aide("Demandé à chaque ouverture d'une conversation. Les messages restent masqués sans lui.")
            LigneSeparation()
            ChampReglage(leurreNouveau, { leurreNouveau = it.filter { c -> c.isDigit() }.take(8) }, "Code leurre (4 à 8 chiffres)", chiffres = true)
            BoutonReglage(if (Reseau.leurreDefini()) "Changer le leurre" else "Définir le leurre", Ephemere, Fond) {
                if (leurreNouveau.length >= 4) {
                    Reseau.definirLeurre(leurreNouveau)
                    leurreNouveau = ""
                }
            }
            Aide("Ouvre une fausse conversation. Utilise-le si quelqu'un te force à ouvrir l'appli. Il doit être différent du code.")
        }

        SectionReglage("Connexion") {
            LigneInfo("Wi-Fi local", "Actif", "non chiffré", Ephemere)
            LigneInfo("SMS (sans internet)", "Actif", "chiffré", Accent)
            LigneInfo("Relais internet", "Ouvert seulement", "pseudo requis", TexteDoux)
        }

        SectionReglage("Numéro") {
            ChampReglage(numeroSaisi, { numeroSaisi = it }, "Ton numéro, ex. 06 00 00 00 00", telephone = true)
            BoutonReglage("Enregistrer le numéro", Accent, Fond) { Reseau.enregistrerNumero(numeroSaisi) }
            Aide("Sert uniquement à l'envoi par SMS et à l'invitation de tes contacts.")
        }

        Text(
            "Messagerie · version de test",
            color = TexteDoux,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun SectionReglage(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            titre.uppercase(),
            color = TexteDoux,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenu,
        )
    }
}

@Composable
private fun ChampReglage(
    valeur: String,
    surChangement: (String) -> Unit,
    indication: String,
    chiffres: Boolean = false,
    telephone: Boolean = false,
) {
    TextField(
        value = valeur,
        onValueChange = surChangement,
        placeholder = { Text(indication, color = TexteDoux) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                chiffres -> KeyboardType.NumberPassword
                telephone -> KeyboardType.Phone
                else -> KeyboardType.Text
            }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Fond,
            unfocusedContainerColor = Fond,
            focusedTextColor = Texte,
            unfocusedTextColor = Texte,
            cursorColor = Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun BoutonReglage(texte: String, couleur: Color, couleurTexte: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(couleur)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texte, color = couleurTexte, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun Aide(texte: String) {
    Text(texte, color = TexteDoux, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun LigneSeparation() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Separateur))
}

@Composable
private fun LigneInfo(libelle: String, valeur: String, detail: String, couleur: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(libelle, color = Texte, fontSize = 15.sp)
            Text(detail, color = TexteDoux, fontSize = 12.sp)
        }
        Puce(valeur, couleur)
    }
}

@Composable
private fun Bulle(m: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.moi) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (m.moi) Color(0xFF1F3A2E) else Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(m.texte, color = Texte, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Puce(texte: String, couleur: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, couleur, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(texte, color = couleur, fontSize = 11.sp)
    }
}
