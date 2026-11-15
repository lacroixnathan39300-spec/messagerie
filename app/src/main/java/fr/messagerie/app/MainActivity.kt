package fr.messagerie.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.messagerie.app.reseau.Reseau
import fr.messagerie.app.ui.MessagerieApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Contenu sous les barres système, icônes claires sur fond sombre, sur toutes les versions d'Android.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Bloque les captures d'écran et l'aperçu dans le sélecteur d'applis.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Reseau.demarrer(applicationContext)
        requestPermissions(
            arrayOf(
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.RECEIVE_MMS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_CONTACTS,
                "android.permission.POST_NOTIFICATIONS",
            ),
            1,
        )
        setContent {
            MessagerieApp()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // La permission Contacts vient peut-être d'être accordée : on relit les noms.
        Reseau.viderNoms()
    }

    override fun onStart() {
        super.onStart()
        Reseau.surPremierPlan(true)
    }

    override fun onStop() {
        Reseau.surPremierPlan(false)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) Reseau.arreter()
        super.onDestroy()
    }
}
