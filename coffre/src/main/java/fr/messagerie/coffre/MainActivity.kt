package fr.messagerie.coffre

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Bloque les captures d'écran et l'aperçu dans le sélecteur d'applis : un coffre ne se photographie pas.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Coffre.initialiser(applicationContext)
        setContent { CoffreApp() }
    }

    // Le coffre se reverrouille dès qu'on quitte l'appli.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Coffre.verrouiller()
    }
}
