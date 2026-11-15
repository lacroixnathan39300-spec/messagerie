package fr.messagerie.app.ui

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
                placeholder = { Text("Écrire à un numéro", color = TexteDoux) },
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
                        val ouvert = Reseau.ouvrirNumero(numeroSaisi)
                        if (ouvert != null) {
                            numeroSaisi = ""
                            onOuvrir(ouvert)
                        } else {
                            erreur = true
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("OK", color = Fond, fontWeight = FontWeight.SemiBold)
            }
        }
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

@Composable
private fun Conversation(nom: String, onRetour: () -> Unit) {
    var saisie by remember { mutableStateOf("") }
    val fil = Reseau.messages.filter { it.avec == nom }

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
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(fil) { m -> Bulle(m) }
        }
        Row(
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
    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().padding(20.dp)) {
        Text(
            "← Retour",
            color = Accent,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onRetour).padding(bottom = 12.dp),
        )
        Text("Réglages", color = Texte, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("Ton numéro (pour le SMS)", color = TexteDoux, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = Reseau.monNumero.value,
            onValueChange = { Reseau.enregistrerNumero(it) },
            placeholder = { Text("06 00 00 00 00", color = TexteDoux) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
        Spacer(Modifier.height(20.dp))
        Ligne("Wi-Fi local", "Activé (non chiffré)", Ephemere)
        Ligne("SMS (sans internet)", "Activé (chiffré)", Accent)
        Ligne("Internet direct", "Bientôt", TexteDoux)
        Ligne("Oignon (3 sauts)", "Bientôt", TexteDoux)
        Ligne("Serveur de secours", "Bientôt", TexteDoux)
    }
}

@Composable
private fun Ligne(libelle: String, valeur: String, couleur: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(libelle, color = Texte, fontSize = 15.sp)
        Text(valeur, color = couleur, fontSize = 15.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Separateur))
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
