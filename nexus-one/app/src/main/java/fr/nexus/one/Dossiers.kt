package fr.nexus.one

import android.content.Context

/** Dossier de l'accueil : un nom et une liste de paquets d'applis. */
data class Dossier(val nom: String, val paquets: List<String>)

private const val PREFS_DOSSIERS = "nexus_dossiers"

/** Format de stockage : "nom|pkg1,pkg2;nom2|pkg3". Les séparateurs sont retirés des noms. */
fun chargerDossiers(context: Context): List<Dossier> {
    val brut = context.getSharedPreferences(PREFS_DOSSIERS, Context.MODE_PRIVATE)
        .getString("liste", "").orEmpty()
    return brut.split(";")
        .filter { it.contains("|") }
        .map { entree ->
            val (nom, paquets) = entree.split("|", limit = 2)
            Dossier(nom, paquets.split(",").filter { it.isNotBlank() })
        }
        .filter { it.paquets.isNotEmpty() }
}

fun sauverDossiers(context: Context, dossiers: List<Dossier>) {
    val brut = dossiers
        .filter { it.paquets.isNotEmpty() }
        .joinToString(";") { d ->
            d.nom.replace("|", " ").replace(";", " ") + "|" + d.paquets.joinToString(",")
        }
    context.getSharedPreferences(PREFS_DOSSIERS, Context.MODE_PRIVATE)
        .edit()
        .putString("liste", brut)
        .apply()
}
