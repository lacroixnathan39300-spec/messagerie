package fr.messagerie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Exemples uniquement : aucune donnée réelle n'est affichée.
private data class Discussion(
    val nom: String,
    val initiales: String,
    val couleur: Color,
    val apercu: String,
    val heure: String,
    val trajet: String,
    val ephemere: String?,
)

private val discussions = listOf(
    Discussion("Camille", "C", Accent, "Message éphémère · ouvert", "09:12", "Direct", "Expire 1 h"),
    Discussion("Équipe test", "É", Color(0xFF7AA7FF), "Relais : 3 sauts, livré", "08:47", "Oignon", null),
    Discussion("Marc", "M", Ephemere, "Photo vue unique", "Hier", "Secours", "Vue unique"),
    Discussion("Maison", "M", Color(0xFFC594FF), "En attente de relais", "Lun.", "File d'attente", null),
)

@Composable
fun MessagerieApp() {
    var ecran by remember { mutableStateOf("discussions") }
    var discussionOuverte by remember { mutableStateOf<String?>(null) }

    MessagerieTheme {
        when {
            ecran == "reglages" -> Reglages(onRetour = { ecran = "discussions" })
            discussionOuverte != null -> Conversation(
                nom = discussionOuverte!!,
                onRetour = { discussionOuverte = null },
            )
            else -> ListeDiscussions(
                onOuvrir = { discussionOuverte = it },
                onReglages = { ecran = "reglages" },
            )
        }
    }
}

@Composable
private fun ListeDiscussions(onOuvrir: (String) -> Unit, onReglages: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Discussions", color = Texte, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Puce("Chiffré", Accent)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(12.dp),
        ) {
            Text("Rechercher", color = TexteDoux, fontSize = 14.sp)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(discussions) { d ->
                LigneDiscussion(d, onClick = { onOuvrir(d.nom) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(Fond).navigationBarsPadding().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("Discussions", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Relais", color = TexteDoux, fontSize = 13.sp)
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
private fun LigneDiscussion(d: Discussion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(d.couleur),
            contentAlignment = Alignment.Center,
        ) {
            Text(d.initiales, color = Fond, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(d.nom, color = Texte, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(d.heure, color = TexteDoux, fontSize = 12.sp)
            }
            Text(d.apercu, color = TexteDoux, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Puce(d.trajet, TexteDoux)
                d.ephemere?.let { Puce(it, Ephemere) }
            }
        }
    }
}

@Composable
private fun Conversation(nom: String, onRetour: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("←", color = Texte, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onRetour))
            Column(modifier = Modifier.weight(1f)) {
                Text(nom, color = Texte, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Chiffré · Direct", color = Accent, fontSize = 12.sp)
            }
            Puce("1 h", Ephemere)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Bulle("Tu es dispo demain ?", Surface, Texte, aGauche = true)
            Spacer(Modifier.height(8.dp))
            Bulle("Oui, je te confirme ce soir.", Color(0xFF1F3A2E), Texte, aGauche = false)
            Spacer(Modifier.height(8.dp))
            Bulle("Message éphémère : disparaît dans 58 min", Surface, Ephemere, aGauche = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Message", color = TexteDoux, fontSize = 15.sp)
            }
            Puce("Envoyer", Accent)
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
        Ligne("Local (proximité)", "Activé", Accent)
        Ligne("Internet direct", "Activé", Accent)
        Ligne("Oignon (3 sauts)", "Par défaut", Accent)
        Ligne("Serveur de secours", "Dernier recours", TexteDoux)
        Spacer(Modifier.height(20.dp))
        Ligne("Durée par défaut", "1 h", Ephemere)
        Ligne("Effacer après lecture", "Oui", Accent)
        Ligne("Bloquer les captures", "Oui", Accent)
        Spacer(Modifier.height(20.dp))
        Text(
            "Aucun numéro de téléphone requis. Les clés restent sur cet appareil.",
            color = TexteDoux,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Ligne(libelle: String, valeur: String, couleur: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(0.dp))
            .padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(libelle, color = Texte, fontSize = 15.sp)
        Text(valeur, color = couleur, fontSize = 15.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Separateur))
}

@Composable
private fun Bulle(texte: String, fond: Color, couleurTexte: Color, aGauche: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (aGauche) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(fond)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(texte, color = couleurTexte, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Puce(texte: String, couleur: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, couleur, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(texte, color = couleur, fontSize = 11.sp)
    }
}
