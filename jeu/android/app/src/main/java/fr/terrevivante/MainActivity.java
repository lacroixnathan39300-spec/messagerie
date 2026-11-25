package fr.terrevivante;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * Terre vivante : affiche l'appli web embarquée dans l'APK (dossier assets/jeu).
 * Tout est local : aucune connexion n'est nécessaire pour jouer.
 */
public class MainActivity extends Activity {
    private static final String ACCUEIL = "https://appassets.androidplatform.net/assets/jeu/index.html";
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings reglages = web.getSettings();
        reglages.setJavaScriptEnabled(true);   // Pyodide (Python dans le navigateur)
        reglages.setDomStorageEnabled(true);   // sauvegarde automatique de la partie
        reglages.setAllowFileAccess(false);
        reglages.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView vue, WebResourceRequest requete) {
                return loader.shouldInterceptRequest(requete.getUrl());
            }
        });

        setContentView(web);
        web.loadUrl(ACCUEIL);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
