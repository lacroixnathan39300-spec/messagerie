package fr.nexus.one

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Lecteur actif (Spotify, YouTube Music, radio…). Nécessite l'accès aux notifications. */
fun lecteurActif(context: Context): MediaController? = try {
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val sessions = msm.getActiveSessions(ComponentName(context, NotifService::class.java))
    sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
} catch (e: Exception) {
    null
}

data class EtatLecteur(val titre: String, val artiste: String, val enLecture: Boolean)

private fun lireEtat(context: Context): EtatLecteur? {
    val c = lecteurActif(context) ?: return null
    val meta = c.metadata
    return EtatLecteur(
        titre = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
        artiste = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
        enLecture = c.playbackState?.state == PlaybackState.STATE_PLAYING,
    )
}

@Composable
fun LecteurWidget(accent: Color, grand: Boolean = false) {
    val context = LocalContext.current
    var maj by remember { mutableStateOf(0) }
    val etat by produceState<EtatLecteur?>(initialValue = null, maj) {
        while (true) {
            value = lireEtat(context)
            delay(2_000L)
        }
    }
    val e = etat ?: return
    val style = if (grand) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(
            text = listOf(e.titre, e.artiste).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Lecture en cours" },
            color = Color.White,
            style = style,
            maxLines = 1,
        )
        Row {
            TextButton(onClick = { lecteurActif(context)?.transportControls?.skipToPrevious(); maj++ }) {
                Text("Précédent", color = accent, style = style)
            }
            TextButton(onClick = {
                val c = lecteurActif(context)
                if (e.enLecture) c?.transportControls?.pause() else c?.transportControls?.play()
                maj++
            }) { Text(if (e.enLecture) "Pause" else "Lecture", color = accent, style = style) }
            TextButton(onClick = { lecteurActif(context)?.transportControls?.skipToNext(); maj++ }) {
                Text("Suivant", color = accent, style = style)
            }
        }
    }
}

@Composable
private fun GrosBouton(texte: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .padding(6.dp)
            .height(96.dp)
            .border(2.dp, accent, RoundedCornerShape(16.dp)),
    ) { Text(texte, color = Color.White, style = MaterialTheme.typography.headlineSmall) }
}

/** Mode voiture : gros boutons, écran toujours allumé. */
@Composable
fun ModeVoiture(apps: List<AppEntry>, fond: Color, accent: Color, onGabriel: () -> Unit, onQuitter: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val fenetre = (context as? Activity)?.window
        fenetre?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Appli de navigation : Pilote / RoadGuard si présente, sinon Waze, sinon Maps.
    val navigation = listOf("pilote", "roadguard", "waze", "maps").firstNotNullOfOrNull { mot ->
        apps.firstOrNull { it.label.lowercase().contains(mot) || it.packageName.lowercase().contains(mot) }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Mode voiture", color = accent, style = MaterialTheme.typography.headlineMedium)
        HorlogeWidget(accent)
        LecteurWidget(accent, grand = true)
        Row(modifier = Modifier.fillMaxWidth()) {
            GrosBouton("Navigation", accent, Modifier.weight(1f)) {
                if (navigation != null) launchApp(context, navigation.packageName)
                else try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
            GrosBouton("Téléphone", accent, Modifier.weight(1f)) {
                try {
                    context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            GrosBouton("Gabriel", accent, Modifier.weight(1f), onGabriel)
            GrosBouton("Quitter", accent, Modifier.weight(1f), onQuitter)
        }
    }
}
