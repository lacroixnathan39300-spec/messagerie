package fr.messagerie.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.messagerie.app.reseau.Reseau
import fr.messagerie.app.ui.MessagerieApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bloque les captures d'écran et l'aperçu dans le sélecteur d'applis.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Reseau.demarrer(applicationContext)
        requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS, android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_CONTACTS), 1)
        setContent {
            MessagerieApp()
        }
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
