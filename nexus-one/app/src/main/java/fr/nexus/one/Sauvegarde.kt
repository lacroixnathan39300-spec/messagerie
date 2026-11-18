package fr.nexus.one

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import java.security.MessageDigest
import org.json.JSONObject

/** Fichiers de réglages qui composent la configuration (le PIN n'en fait pas partie). */
private val FICHIERS_CONFIG = listOf("nexus", "nexus_reglages", "nexus_dossiers", "nexus_routines")
private const val PREFS_PROFILS = "nexus_profils"
private const val PREFS_SECURITE = "nexus_securite"

fun exporterConfig(context: Context): JSONObject {
    val tout = JSONObject()
    FICHIERS_CONFIG.forEach { nom ->
        val o = JSONObject()
        context.getSharedPreferences(nom, Context.MODE_PRIVATE).all.forEach { (cle, valeur) ->
            when (valeur) {
                is String, is Int, is Boolean, is Long, is Float -> o.put(cle, JSONObject().put("t", valeur::class.simpleName).put("v", valeur))
            }
        }
        tout.put(nom, o)
    }
    return tout
}

fun importerConfig(context: Context, tout: JSONObject) {
    FICHIERS_CONFIG.forEach { nom ->
        val o = tout.optJSONObject(nom) ?: return@forEach
        val ed = context.getSharedPreferences(nom, Context.MODE_PRIVATE).edit().clear()
        o.keys().forEach { cle ->
            val e = o.getJSONObject(cle)
            when (e.getString("t")) {
                "String" -> ed.putString(cle, e.getString("v"))
                "Int" -> ed.putInt(cle, e.getInt("v"))
                "Boolean" -> ed.putBoolean(cle, e.getBoolean("v"))
                "Long" -> ed.putLong(cle, e.getLong("v"))
                "Float" -> ed.putFloat(cle, e.getDouble("v").toFloat())
            }
        }
        ed.apply()
    }
}

fun nomsProfils(context: Context): List<String> =
    context.getSharedPreferences(PREFS_PROFILS, Context.MODE_PRIVATE).all.keys.sorted()

fun enregistrerProfil(context: Context, nom: String) {
    context.getSharedPreferences(PREFS_PROFILS, Context.MODE_PRIVATE).edit()
        .putString(nom, exporterConfig(context).toString()).apply()
}

fun appliquerProfil(context: Context, nom: String): Boolean {
    val texte = context.getSharedPreferences(PREFS_PROFILS, Context.MODE_PRIVATE).getString(nom, null) ?: return false
    importerConfig(context, JSONObject(texte))
    return true
}

fun supprimerProfil(context: Context, nom: String) {
    context.getSharedPreferences(PREFS_PROFILS, Context.MODE_PRIVATE).edit().remove(nom).apply()
}

// ---------- Sécurité : PIN et applis masquées ----------

private fun empreinte(pin: String): String =
    MessageDigest.getInstance("SHA-256").digest(("nexus:" + pin).toByteArray()).joinToString("") { "%02x".format(it) }

fun pinDefini(context: Context): Boolean =
    !context.getSharedPreferences(PREFS_SECURITE, Context.MODE_PRIVATE).getString("pin", null).isNullOrEmpty()

fun verifierPin(context: Context, pin: String): Boolean =
    !pinDefini(context) ||
        context.getSharedPreferences(PREFS_SECURITE, Context.MODE_PRIVATE).getString("pin", "") == empreinte(pin)

fun definirPin(context: Context, pin: String) {
    val ed = context.getSharedPreferences(PREFS_SECURITE, Context.MODE_PRIVATE).edit()
    if (pin.isEmpty()) ed.remove("pin") else ed.putString("pin", empreinte(pin))
    ed.apply()
}

fun chargerMasquees(context: Context): Set<String> =
    context.getSharedPreferences("nexus", Context.MODE_PRIVATE).getString("masquees", "")!!
        .split(",").filter { it.isNotBlank() }.toSet()

fun sauverMasquees(context: Context, paquets: Set<String>) {
    context.getSharedPreferences("nexus", Context.MODE_PRIVATE).edit().putString("masquees", paquets.joinToString(",")).apply()
}

/** Demande le PIN s'il y en a un. Appelle onOk si le code est bon. */
@Composable
fun DemandePin(accent: Color, onOk: () -> Unit, onFermer: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Code PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }; erreur = "" },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("PIN") },
                )
                if (erreur.isNotEmpty()) Text(erreur, color = accent)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (verifierPin(context, pin)) onOk() else erreur = "Code incorrect" }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

/** Sauvegarde, profils et sécurité. Après un import ou un changement de profil, l'écran est rechargé. */
@Composable
fun SauvegardeDialog(apps: List<AppEntry>, accent: Color, onFermer: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var nomProfil by remember { mutableStateOf("") }
    var profils by remember { mutableStateOf(nomsProfils(context)) }
    var nouveauPin by remember { mutableStateOf("") }
    var masquees by remember { mutableStateOf(chargerMasquees(context)) }

    fun recharger() = (context as? Activity)?.recreate()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) message = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exporterConfig(context).toString(2).toByteArray()) }
            "Sauvegarde enregistrée."
        } catch (e: Exception) { "Échec de la sauvegarde." }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) try {
            val texte = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            importerConfig(context, JSONObject(texte))
            recharger()
        } catch (e: Exception) { message = "Fichier illisible." }
    }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Profils, sauvegarde, sécurité", color = accent) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (message.isNotEmpty()) Text(message, color = accent)

                Text("Sauvegarde", color = accent)
                Row {
                    TextButton(onClick = { exporter.launch("nexus-one-sauvegarde.json") }) { Text("Exporter", color = Color.White) }
                    TextButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Importer", color = Color.White) }
                }

                Text("Profils", color = accent)
                profils.forEach { p ->
                    Row {
                        TextButton(onClick = { if (appliquerProfil(context, p)) recharger() }) { Text("Activer « $p »", color = Color.White) }
                        TextButton(onClick = { supprimerProfil(context, p); profils = nomsProfils(context) }) { Text("Supprimer", color = accent) }
                    }
                }
                OutlinedTextField(value = nomProfil, onValueChange = { nomProfil = it }, singleLine = true,
                    label = { Text("Nom du profil") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    val n = nomProfil.trim()
                    if (n.isEmpty()) message = "Donne un nom au profil."
                    else { enregistrerProfil(context, n); profils = nomsProfils(context); nomProfil = ""; message = "Profil « $n » enregistré." }
                }) { Text("Enregistrer la configuration actuelle", color = Color.White) }

                Text("Sécurité", color = accent)
                OutlinedTextField(
                    value = nouveauPin,
                    onValueChange = { nouveauPin = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("Nouveau PIN (vide = aucun)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    if (nouveauPin.isNotEmpty() && nouveauPin.length < 4) message = "Le PIN doit avoir au moins 4 chiffres."
                    else { definirPin(context, nouveauPin); nouveauPin = ""; message = if (pinDefini(context)) "PIN enregistré." else "PIN supprimé." }
                }) { Text("Enregistrer le PIN", color = Color.White) }

                Text("Applis masquées", color = accent)
                if (masquees.isEmpty()) Text("Aucune. Appui long sur une appli pour la masquer.", color = Color.White)
                masquees.forEach { paquet ->
                    val nom = apps.firstOrNull { it.packageName == paquet }?.label ?: paquet
                    Row {
                        TextButton(onClick = { launchApp(context, paquet) }) { Text(nom, color = Color.White) }
                        TextButton(onClick = {
                            masquees = masquees - paquet
                            sauverMasquees(context, masquees)
                        }) { Text("Afficher", color = accent) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onFermer(); recharger() }) { Text("Fermer") } },
    )
}
