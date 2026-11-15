package fr.messagerie.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette de la maquette : fond sombre, accent vert (chiffrement), orange (éphémère).
val Fond = Color(0xFF0E1116)
val Surface = Color(0xFF171B22)
val Separateur = Color(0xFF1F2530)
val Texte = Color(0xFFE8ECF2)
val TexteDoux = Color(0xFF8A93A3)
val Accent = Color(0xFF3DDC97)
val Ephemere = Color(0xFFF5A524)
val SurfaceHaute = Color(0xFF1E242E)
val Danger = Color(0xFFFF6B6B)

private val Couleurs = darkColorScheme(
    background = Fond,
    surface = Surface,
    primary = Accent,
    onPrimary = Fond,
    onBackground = Texte,
    onSurface = Texte,
    secondary = Ephemere,
)

@Composable
fun MessagerieTheme(contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Couleurs, content = contenu)
}
