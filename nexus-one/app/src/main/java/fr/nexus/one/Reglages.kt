package fr.nexus.one

import android.content.Context

/** Réglages du launcher, sauvegardés localement. */
data class Reglages(
    val colonnes: Int = 4,
    val tailleIcone: Int = 56,
)

private const val PREFS_REGLAGES = "nexus_reglages"

fun chargerReglages(context: Context): Reglages {
    val p = context.getSharedPreferences(PREFS_REGLAGES, Context.MODE_PRIVATE)
    return Reglages(
        colonnes = p.getInt("colonnes", 4).coerceIn(3, 6),
        tailleIcone = p.getInt("tailleIcone", 56).coerceIn(40, 80),
    )
}

fun sauverReglages(context: Context, reglages: Reglages) {
    context.getSharedPreferences(PREFS_REGLAGES, Context.MODE_PRIVATE)
        .edit()
        .putInt("colonnes", reglages.colonnes)
        .putInt("tailleIcone", reglages.tailleIcone)
        .apply()
}
