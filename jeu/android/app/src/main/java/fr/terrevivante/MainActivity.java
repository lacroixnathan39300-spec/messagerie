package fr.terrevivante;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Écran principal : barre du haut, carte native, barre d'outils en bas. Le calcul est fait par Python. */
public class MainActivity extends Activity implements CarteView.Ecouteur {

    private static final String[] CARTES = {"terre", "ile", "archipel"};
    private static final int[] VITESSES = {1, 2, 5, 10};
    private static final String[] CATEGORIES = {"voir", "etres", "nature", "terrain", "pouvoirs"};
    private static final String[] NOMS_CATEGORIES = {"👁 Voir", "🧍 Êtres", "🌿 Nature", "⛰ Terrain", "⚡ Pouvoirs"};

    /** Outils par catégorie : identifiant puis nom affiché. */
    private static final String[][][] OUTILS = {
        {{"voir", "Voir"}},
        {{"humain", "Personne"}, {"mouton", "Mouton"}, {"loup", "Loup"}, {"poisson", "Poisson"},
         {"dragon", "Dragon"}, {"zombie", "Zombie"}},
        {{"buisson", "Buisson"}},
        {{"terre", "Terre"}, {"mer", "Mer"}, {"lave", "Lave"}, {"desert", "Désert"}, {"montagne", "Montagne"}},
        {{"foudre", "Foudre"}, {"pluie", "Pluie"}, {"feu", "Feu"}, {"seisme", "Séisme"}, {"peste", "Peste"},
         {"tornade", "Tornade"}, {"meteorite", "Météorite"}, {"bombe", "Bombe"}},
    };

    private PyObject pont;
    private final Handler horloge = new Handler(Looper.getMainLooper());
    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (enCours) {
                etat = lire(pont.callAttr("avancer", 1).toString());
                rafraichir();
            }
            horloge.postDelayed(this, 1000L / VITESSES[indiceVitesse]);
        }
    };

    private CarteView carte;
    private TextView infos, echelleTexte, messageTexte;
    private Button boutonPause, boutonVitesse, boutonCarte, boutonPinceau, boutonVilles, boutonBords;
    private LinearLayout rangeOutils;
    private final Button[] boutonsCategories = new Button[CATEGORIES.length];

    private JSONObject etat;
    private boolean enCours = true;
    private int indiceVitesse = 0;
    private int indiceCarte = 0;
    private int taille = 2;
    private String categorie = "voir";
    private String outil = "voir";
    private int graine = 42;

    @Override
    protected void onCreate(Bundle sauvegarde) {
        super.onCreate(sauvegarde);
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
        pont = Python.getInstance().getModule("pont");
        construireInterface();

        File autosave = new File(getFilesDir(), "autosave.json");
        if (autosave.exists()) {
            etat = lire(pont.callAttr("charger", lireFichier(autosave)).toString());
        } else {
            etat = lire(pont.callAttr("nouvelle", graine, CARTES[indiceCarte]).toString());
        }
        chargerFond();
        rafraichir();
    }

    @Override
    protected void onPause() {
        super.onPause();
        horloge.removeCallbacks(tic);
        ecrireFichier(new File(getFilesDir(), "autosave.json"), pont.callAttr("sauver").toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        horloge.removeCallbacks(tic);
        horloge.postDelayed(tic, 1000L);
    }

    // ---- Interface ----

    private void construireInterface() {
        float densite = getResources().getDisplayMetrics().density;
        LinearLayout racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);
        racine.setBackgroundColor(Color.rgb(11, 18, 32));
        setContentView(racine);

        infos = new TextView(this);
        infos.setTextColor(Color.rgb(226, 232, 240));
        infos.setTextSize(15);
        infos.setPadding((int) (10 * densite), (int) (8 * densite), (int) (10 * densite), 0);
        racine.addView(infos);

        HorizontalScrollView barreHaut = new HorizontalScrollView(this);
        LinearLayout lignesHaut = new LinearLayout(this);
        lignesHaut.setOrientation(LinearLayout.HORIZONTAL);
        lignesHaut.setPadding((int) (6 * densite), (int) (4 * densite), (int) (6 * densite), (int) (4 * densite));
        barreHaut.addView(lignesHaut);
        racine.addView(barreHaut);

        boutonPause = bouton(lignesHaut, "⏸ Pause", v -> {
            enCours = !enCours;
            boutonPause.setText(enCours ? "⏸ Pause" : "▶ Lancer");
        });
        boutonVitesse = bouton(lignesHaut, "×1", v -> {
            indiceVitesse = (indiceVitesse + 1) % VITESSES.length;
            boutonVitesse.setText("×" + VITESSES[indiceVitesse]);
        });
        boutonCarte = bouton(lignesHaut, "▲ Terre", v -> {
            indiceCarte = (indiceCarte + 1) % CARTES.length;
            etat = lire(pont.callAttr("nouvelle", graine, CARTES[indiceCarte]).toString());
            boutonCarte.setText(nomCarte());
            chargerFond();
            rafraichir();
        });
        boutonPinceau = bouton(lignesHaut, "● 2", v -> {
            taille = taille % 5 + 1;
            boutonPinceau.setText("● " + taille);
        });
        boutonVilles = bouton(lignesHaut, "Villes ✓", v -> {
            boolean estOn = boutonVilles.getText().toString().endsWith("✓");
            boutonVilles.setText(estOn ? "Villes ✗" : "Villes ✓");
            carte.setVilles(!estOn);
        });
        boutonBords = bouton(lignesHaut, "Bords fermés", v -> {
            boolean ouverts = boutonBords.getText().toString().endsWith("ouverts");
            boutonBords.setText(ouverts ? "Bords fermés" : "Bords ouverts");
            carte.setBordsOuverts(!ouverts);
        });
        bouton(lignesHaut, "1 px = 1 km", v -> carte.echelleReelle());
        bouton(lignesHaut, "Sauver", v -> {
            ecrireFichier(new File(getFilesDir(), "partie-1.json"), pont.callAttr("sauver").toString());
            message("Partie sauvegardée.");
        });
        bouton(lignesHaut, "Charger", v -> {
            File f = new File(getFilesDir(), "partie-1.json");
            if (!f.exists()) { message("Aucune partie sauvegardée."); return; }
            etat = lire(pont.callAttr("charger", lireFichier(f)).toString());
            chargerFond();
            rafraichir();
            message("Partie chargée.");
        });
        bouton(lignesHaut, "Nouvelle", v -> {
            graine = (int) (System.currentTimeMillis() % 100000);
            etat = lire(pont.callAttr("nouvelle", graine, CARTES[indiceCarte]).toString());
            chargerFond();
            rafraichir();
            message("Nouveau monde.");
        });

        LinearLayout ligneEchelle = new LinearLayout(this);
        ligneEchelle.setPadding((int) (10 * densite), 0, (int) (10 * densite), 0);
        echelleTexte = new TextView(this);
        echelleTexte.setTextColor(Color.rgb(148, 163, 184));
        echelleTexte.setTextSize(12);
        ligneEchelle.addView(echelleTexte);
        messageTexte = new TextView(this);
        messageTexte.setTextColor(Color.rgb(251, 191, 36));
        messageTexte.setTextSize(13);
        messageTexte.setGravity(Gravity.END);
        messageTexte.setPadding((int) (10 * densite), 0, 0, 0);
        ligneEchelle.addView(messageTexte, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        racine.addView(ligneEchelle);

        carte = new CarteView(this, null);
        carte.setEcouteur(this);
        racine.addView(carte, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout barreBas = new LinearLayout(this);
        barreBas.setOrientation(LinearLayout.VERTICAL);
        barreBas.setBackgroundColor(Color.rgb(17, 24, 39));
        barreBas.setPadding((int) (6 * densite), (int) (6 * densite), (int) (6 * densite), (int) (8 * densite));
        LinearLayout categories = new LinearLayout(this);
        for (int k = 0; k < CATEGORIES.length; k++) {
            final String cat = CATEGORIES[k];
            Button b = new Button(this);
            b.setText(NOMS_CATEGORIES[k]);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setOnClickListener(v -> choisirCategorie(cat));
            categories.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            boutonsCategories[k] = b;
        }
        barreBas.addView(categories);
        HorizontalScrollView defilement = new HorizontalScrollView(this);
        rangeOutils = new LinearLayout(this);
        defilement.addView(rangeOutils);
        barreBas.addView(defilement);
        racine.addView(barreBas);

        choisirCategorie("voir");
    }

    private Button bouton(LinearLayout parent, String texte, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(texte);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(action);
        parent.addView(b);
        return b;
    }

    private void choisirCategorie(String cat) {
        categorie = cat;
        for (int k = 0; k < CATEGORIES.length; k++) {
            boutonsCategories[k].setBackgroundColor(CATEGORIES[k].equals(cat) ? Color.rgb(56, 189, 248) : Color.rgb(30, 41, 59));
        }
        rangeOutils.removeAllViews();
        for (int k = 0; k < CATEGORIES.length; k++) {
            if (!CATEGORIES[k].equals(cat)) continue;
            for (String[] o : OUTILS[k]) {
                final String identifiant = o[0];
                Button b = new Button(this);
                b.setText(o[1]);
                b.setAllCaps(false);
                b.setTextSize(13);
                b.setBackgroundColor(identifiant.equals(outil) ? Color.rgb(12, 74, 110) : Color.rgb(30, 41, 59));
                b.setOnClickListener(v -> {
                    outil = identifiant;
                    choisirCategorie(categorie);
                });
                rangeOutils.addView(b);
            }
        }
    }

    // ---- État et rafraîchissement ----

    private void chargerFond() {
        if (etat == null) return;
        JSONArray terre = etat.optJSONArray("terre");
        if (terre != null) {
            carte.setFondListe(terre, etat.optInt("largeur", 720), etat.optInt("hauteur", 360));
        } else {
            carte.setFondTerrestre(pont.callAttr("terre_texte").toString());
        }
    }

    private void rafraichir() {
        if (etat == null) return;
        JSONObject b = etat.optJSONObject("bilan");
        if (b != null) {
            infos.setText("Tour " + b.optInt("tour") + " · " + b.optString("epoque")
                    + " · Vivants " + b.optInt("vivantes") + " · Morts " + b.optInt("morts")
                    + " · Peuples " + b.optInt("peuples"));
        }
        carte.setEtat(etat);
    }

    private String nomCarte() {
        switch (CARTES[indiceCarte]) {
            case "ile": return "🏝 Île";
            case "archipel": return "🏝 Archipel";
            default: return "▲ Terre";
        }
    }

    private void message(String texte) {
        messageTexte.setText(texte);
    }

    @Override
    public void caseTouchee(int i, int j) {
        if (outil.equals("voir")) return;
        JSONObject r = lireObjet(pont.callAttr("agir", outil, i, j, taille).toString());
        if (r == null) return;
        message(r.optString("message"));
        JSONObject e = r.optJSONObject("etat");
        if (e != null) {
            etat = e;
            rafraichir();
        }
    }

    @Override
    public void zoomChange(float kmParPixel) {
        echelleTexte.setText(kmParPixel >= 10
                ? String.format("1 px ≈ %d km", Math.round(kmParPixel))
                : String.format("1 px ≈ %.1f km", kmParPixel));
    }

    // ---- Fichiers et JSON ----

    private JSONObject lire(String json) {
        return lireObjet(json);
    }

    private JSONObject lireObjet(String json) {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            message("Erreur de données : " + e.getMessage());
            return null;
        }
    }

    private static String lireFichier(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] octets = new byte[(int) f.length()];
            int lus = 0;
            while (lus < octets.length) {
                int n = in.read(octets, lus, octets.length - lus);
                if (n < 0) break;
                lus += n;
            }
            return new String(octets, 0, lus, "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private static void ecrireFichier(File f, String texte) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(texte.getBytes("UTF-8"));
        } catch (IOException ignore) { }
    }
}
