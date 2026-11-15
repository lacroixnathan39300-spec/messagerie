package fr.messagerie.hub

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Palette identique à Messagerie.
private val Fond = Color(0xFF0E1116)
private val Surface = Color(0xFF171B22)
private val Separateur = Color(0xFF1F2530)
private val Texte = Color(0xFFE8ECF2)
private val TexteDoux = Color(0xFF8A93A3)
private val Accent = Color(0xFF3DDC97)
private val Danger = Color(0xFFFF6B6B)

// Catalogue public des applis, lu en ligne pour que les futures apparaissent sans réinstaller le hub.
private const val CATALOGUE =
    "https://raw.githubusercontent.com/lacroixnathan39300-spec/messagerie/main/catalogue.json"

// Applis connues même sans réseau, pour que le hub serve à quelque chose au premier lancement.
private val PAR_DEFAUT = listOf(
    Appli(
        "Messagerie",
        "Messagerie chiffrée, sans serveur. Wi-Fi local et SMS.",
        "fr.messagerie.app",
        "https://github.com/lacroixnathan39300-spec/messagerie/releases/latest/download/Messagerie.apk",
    ),
)

data class Appli(val nom: String, val description: String, val paquet: String, val apk: String)

// État d'une ligne : repos, téléchargement en cours (0..100, -1 = indéterminé), ou erreur.
class EtatAppli(val appli: Appli) {
    var enCours by mutableStateOf(false)
    var progression by mutableStateOf(0)
    var message by mutableStateOf("")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Fond, surface = Surface, primary = Accent,
                    onPrimary = Fond, onBackground = Texte, onSurface = Texte,
                )
            ) { Ecran() }
        }
    }
}

@Composable
private fun Ecran() {
    val contexte = LocalContext.current
    val etats = remember { mutableStateListOf<EtatAppli>() }
    // Incrémenté après chaque retour dans l'appli pour réafficher l'état « installé ».
    var rafraichir by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        chargerCatalogue(etats)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Fond).statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text("Mes applis", color = Texte, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Télécharge et mets à jour tes applications", color = TexteDoux, fontSize = 13.sp)
        }

        if (etats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chargement…", color = TexteDoux, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(etats, key = { it.appli.paquet }) { etat ->
                    LigneAppli(etat, rafraichir) { rafraichir++ }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        Text(
            "Les applis s'installent depuis ton téléphone. Android peut demander une autorisation la première fois.",
            color = TexteDoux,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun LigneAppli(etat: EtatAppli, cle: Int, surAction: () -> Unit) {
    val contexte = LocalContext.current
    // Recalculé à chaque passage au premier plan (clé) : l'appli a pu être installée entre-temps.
    val versionInstallee = remember(cle) { versionInstallee(contexte, etat.appli.paquet) }
    val installee = versionInstallee != null

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(etat.appli.nom.take(1), color = Accent, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(etat.appli.nom, color = Texte, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(etat.appli.description, color = TexteDoux, fontSize = 13.sp, lineHeight = 17.sp)
                if (installee) Text("Installé · version $versionInstallee", color = Accent, fontSize = 12.sp)
            }
        }

        if (etat.enCours) {
            BarreProgression(etat.progression)
            Text(
                if (etat.progression >= 100) "Installation…" else "Téléchargement… ${if (etat.progression < 0) "" else "${etat.progression} %"}",
                color = TexteDoux,
                fontSize = 12.sp,
            )
        } else {
            Bouton(if (installee) "Mettre à jour / réinstaller" else "Installer", installee) {
                lancer(contexte, etat, surAction)
            }
            if (etat.message.isNotBlank()) Text(etat.message, color = Danger, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BarreProgression(progression: Int) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Separateur)) {
        val fraction = if (progression < 0) 1f else (progression.coerceIn(0, 100) / 100f)
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Accent)
        )
    }
}

@Composable
private fun Bouton(texte: String, secondaire: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (secondaire) Accent.copy(alpha = 0.16f) else Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texte, color = if (secondaire) Accent else Fond, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// --- Logique ---

private fun versionInstallee(contexte: android.content.Context, paquet: String): String? = try {
    contexte.packageManager.getPackageInfo(paquet, 0).versionName
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private fun chargerCatalogue(etats: SnapshotStateList<EtatAppli>) {
    Thread {
        val applis = try {
            val texte = (URL(CATALOGUE).openConnection() as HttpURLConnection).run {
                connectTimeout = 10000
                readTimeout = 10000
                inputStream.bufferedReader().use { it.readText() }
            }
            val tableau = JSONObject(texte).getJSONArray("applis")
            (0 until tableau.length()).map { i ->
                val o = tableau.getJSONObject(i)
                Appli(o.getString("nom"), o.optString("description"), o.getString("paquet"), o.getString("apk"))
            }
        } catch (e: Throwable) {
            PAR_DEFAUT
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            etats.clear()
            applis.forEach { etats.add(EtatAppli(it)) }
        }
    }.start()
}

private fun lancer(contexte: android.content.Context, etat: EtatAppli, surAction: () -> Unit) {
    // Android ≥ 8 : l'utilisateur doit autoriser « Mes applis » à installer. À faire une seule fois.
    if (Build.VERSION.SDK_INT >= 26 && !contexte.packageManager.canRequestPackageInstalls()) {
        try {
            contexte.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + contexte.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            etat.message = "Autorise « Installer des applications inconnues » pour Mes applis, puis réessaie."
        }
        return
    }
    telecharger(contexte, etat, surAction)
}

private fun telecharger(contexte: android.content.Context, etat: EtatAppli, surAction: () -> Unit) {
    etat.message = ""
    etat.enCours = true
    etat.progression = 0
    val principal = android.os.Handler(android.os.Looper.getMainLooper())
    Thread {
        try {
            val dossier = File(contexte.cacheDir, "apk").apply { mkdirs() }
            val fichier = File(dossier, etat.appli.paquet + ".apk")
            var connexion = URL(etat.appli.apk).openConnection() as HttpURLConnection
            connexion.instanceFollowRedirects = true
            connexion.connectTimeout = 15000
            connexion.readTimeout = 30000
            connexion.connect()
            // Suit un éventuel renvoi vers l'hébergeur du fichier (GitHub → stockage).
            var tours = 0
            while (connexion.responseCode in 300..399 && tours < 5) {
                val suivant = connexion.getHeaderField("Location") ?: break
                connexion.disconnect()
                connexion = URL(suivant).openConnection() as HttpURLConnection
                connexion.instanceFollowRedirects = true
                connexion.connectTimeout = 15000
                connexion.readTimeout = 30000
                connexion.connect()
                tours++
            }
            if (connexion.responseCode !in 200..299) throw Exception("HTTP ${connexion.responseCode}")
            val total = connexion.contentLength.toLong()
            connexion.inputStream.use { entree ->
                fichier.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var recu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        recu += n
                        val p = if (total > 0) ((recu * 100) / total).toInt() else -1
                        principal.post { etat.progression = p }
                    }
                }
            }
            principal.post {
                etat.progression = 100
                installer(contexte, fichier, etat)
                etat.enCours = false
                surAction()
            }
        } catch (e: Throwable) {
            principal.post {
                etat.enCours = false
                etat.message = "Échec du téléchargement. Vérifie ta connexion et réessaie."
            }
        }
    }.start()
}

private fun installer(contexte: android.content.Context, fichier: File, etat: EtatAppli) {
    try {
        val uri = FileProvider.getUriForFile(contexte, contexte.packageName + ".fichiers", fichier)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        contexte.startActivity(intent)
    } catch (e: Throwable) {
        etat.message = "Installation impossible sur ce téléphone."
    }
}

