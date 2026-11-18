package fr.nexus.one

import android.content.Context

/** Réglages du launcher, sauvegardés localement. */
data class Reglages(
    val colonnes: Int = 4,
    val tailleIcone: Int = 56,
    val accent: Int = 0xFFE53935.toInt(),
    val afficherNoms: Boolean = true,
    val fond: Int = 0xFF000000.toInt(),
    val ordreAZ: Boolean = true,
)

/** Couleurs d'accent proposées. */
val COULEURS_ACCENT: List<Pair<String, Int>> = listOf(
    "Rouge" to 0xFFE53935.toInt(),
    "Bleu" to 0xFF2979FF.toInt(),
    "Gris" to 0xFF9E9E9E.toInt(),
    "Blanc" to 0xFFFFFFFF.toInt(),
)

/** Couleurs de fond proposées pour l'accueil. */
val COULEURS_FOND: List<Pair<String, Int>> = listOf(
    "Noir" to 0xFF000000.toInt(),
    "Gris" to 0xFF1C1C1E.toInt(),
    "Bleu nuit" to 0xFF0B1A2E.toInt(),
)

/** Thèmes prêts à l'emploi : fond et couleur d'accent réglés ensemble. */
val THEMES: List<Triple<String, Int, Int>> = listOf(
    Triple("Sombre", 0xFF000000.toInt(), 0xFFE53935.toInt()),
    Triple("Nuit", 0xFF0B1A2E.toInt(), 0xFF2979FF.toInt()),
    Triple("Gris", 0xFF1C1C1E.toInt(), 0xFF9E9E9E.toInt()),
)

private const val PREFS_REGLAGES = "nexus_reglages"

fun chargerReglages(context: Context): Reglages {
    val p = context.getSharedPreferences(PREFS_REGLAGES, Context.MODE_PRIVATE)
    return Reglages(
        colonnes = p.getInt("colonnes", 4).coerceIn(3, 6),
        tailleIcone = p.getInt("tailleIcone", 56).coerceIn(40, 80),
        accent = p.getInt("accent", 0xFFE53935.toInt()),
        afficherNoms = p.getBoolean("afficherNoms", true),
        fond = p.getInt("fond", 0xFF000000.toInt()),
        ordreAZ = p.getBoolean("ordreAZ", true),
    )
}

fun sauverReglages(context: Context, reglages: Reglages) {
    context.getSharedPreferences(PREFS_REGLAGES, Context.MODE_PRIVATE)
        .edit()
        .putInt("colonnes", reglages.colonnes)
        .putInt("tailleIcone", reglages.tailleIcone)
        .putInt("accent", reglages.accent)
        .putBoolean("afficherNoms", reglages.afficherNoms)
        .putInt("fond", reglages.fond)
        .putBoolean("ordreAZ", reglages.ordreAZ)
        .apply()
}
