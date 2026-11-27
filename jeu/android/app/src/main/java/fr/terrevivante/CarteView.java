package fr.terrevivante;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** La carte : terrain, êtres et bâtiments, dessinés en natif sur le canevas Android. */
public class CarteView extends View {

    /** Une case de la grille fait environ 55,6 km : à ce zoom, un pixel de l'écran vaut 1 km. */
    public static final float KM_PAR_CASE = 55.6f;
    private static final float ZOOM_MAX = 120f;

    public interface Ecouteur {
        void caseTouchee(int i, int j);
        void zoomChange(float kmParPixel);
    }

    private Ecouteur ecouteur;
    private JSONObject etat;
    private Bitmap fond;
    private int largeur = 720, hauteur = 360;
    private float zoom = 1f;
    private float offX = 0f, offY = 0f;
    private boolean ajustee = false;
    private boolean afficherVilles = true;
    private boolean bordsOuverts = false;
    private float density = 1f;

    private final Paint peinture = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint contour = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointille = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fondPaint = new Paint();

    private final ScaleGestureDetector zoomGeste;
    private final GestureDetector glisseGeste;

    public CarteView(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        density = contexte.getResources().getDisplayMetrics().density;
        contour.setStyle(Paint.Style.STROKE);
        contour.setColor(Color.rgb(17, 24, 39));
        contour.setStrokeWidth(1.2f * density);
        pointille.setStyle(Paint.Style.STROKE);
        pointille.setColor(Color.argb(230, 220, 38, 38));
        pointille.setStrokeWidth(2f * density);
        pointille.setPathEffect(new DashPathEffect(new float[]{8 * density, 4 * density}, 0));
        fondPaint.setFilterBitmap(false);

        zoomGeste = new ScaleGestureDetector(contexte, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomAutour(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        glisseGeste = new GestureDetector(contexte, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent debut, MotionEvent fin, float dx, float dy) {
                if (zoomGeste.isInProgress()) return false;
                offX -= dx;
                offY -= dy;
                bornerVue();
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (ecouteur != null) {
                    int i = (int) Math.floor((e.getX() - offX) / zoom);
                    int j = (int) Math.floor((e.getY() - offY) / zoom);
                    ecouteur.caseTouchee(i, j);
                }
                return true;
            }
        });
    }

    public void setEcouteur(Ecouteur e) { ecouteur = e; }
    public void setVilles(boolean v) { afficherVilles = v; invalidate(); }
    public void setBordsOuverts(boolean v) { bordsOuverts = v; bornerVue(); invalidate(); }

    /** Fond de la carte terrestre, à partir du fichier d'altitudes (une ligne par rangée de cases). */
    public void setFondTerrestre(String texte) {
        String[] lignes = texte.split("\n");
        int l = 720, h = 360;
        Bitmap b = Bitmap.createBitmap(l, h, Bitmap.Config.ARGB_8888);
        for (int j = 0; j < h && j < lignes.length; j++) {
            String ligne = lignes[j];
            for (int i = 0; i < l && i < ligne.length(); i++) {
                b.setPixel(i, j, couleurCase(lignes, i, j, ligne.charAt(i)));
            }
        }
        fond = b;
        largeur = l;
        hauteur = h;
        ajustee = false;
    }

    /** Fond d'une île ou d'un archipel : la liste des cases de terre. */
    public void setFondListe(JSONArray terre, int l, int h) {
        Bitmap b = Bitmap.createBitmap(l, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.rgb(74, 163, 223));
        for (int k = 0; k < terre.length(); k++) {
            try {
                JSONArray c = terre.getJSONArray(k);
                b.setPixel(c.getInt(0), c.getInt(1), Color.rgb(124, 204, 96));
            } catch (JSONException ignore) { }
        }
        fond = b;
        largeur = l;
        hauteur = h;
        ajustee = false;
    }

    public void setEtat(JSONObject e) {
        etat = e;
        invalidate();
    }

    public float kmParPixel() {
        return KM_PAR_CASE / zoom;
    }

    /** Zoom à l'échelle réelle : un pixel de l'écran vaut un kilomètre. */
    public void echelleReelle() {
        zoomAutour(KM_PAR_CASE / zoom, getWidth() / 2f, getHeight() / 2f);
    }

    private void ajusterInitialement() {
        if (ajustee || getWidth() == 0 || fond == null) return;
        zoom = getWidth() / (float) largeur;
        offX = 0;
        offY = (getHeight() - hauteur * zoom) / 2f;
        ajustee = true;
        if (ecouteur != null) ecouteur.zoomChange(kmParPixel());
    }

    private void zoomAutour(float facteur, float x, float y) {
        float minimum = Math.min(getWidth() / (float) largeur, getHeight() / (float) hauteur) * 0.8f;
        float nouveau = Math.max(minimum, Math.min(ZOOM_MAX, zoom * facteur));
        float k = nouveau / zoom;
        offX = x - (x - offX) * k;
        offY = y - (y - offY) * k;
        zoom = nouveau;
        bornerVue();
        if (ecouteur != null) ecouteur.zoomChange(kmParPixel());
        invalidate();
    }

    private void bornerVue() {
        if (bordsOuverts) return;
        float w = largeur * zoom, h = hauteur * zoom;
        offX = w <= getWidth() ? (getWidth() - w) / 2f : Math.min(0, Math.max(getWidth() - w, offX));
        offY = h <= getHeight() ? (getHeight() - h) / 2f : Math.min(0, Math.max(getHeight() - h, offY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        zoomGeste.onTouchEvent(e);
        glisseGeste.onTouchEvent(e);
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        ajustee = false;
        ajusterInitialement();
    }

    // ---- Couleurs du terrain : mêmes teintes que la version web ----

    private static final int[][] COULEURS_ALTITUDE = {
        {124, 204, 96}, {140, 212, 100}, {168, 218, 108}, {196, 214, 118}, {222, 200, 120},
        {200, 164, 110}, {178, 140, 100}, {150, 122, 98}, {210, 206, 200}, {250, 250, 250}
    };

    private int couleurCase(String[] lignes, int i, int j, char car) {
        if (car == '.') return Color.rgb(74, 163, 223);
        int alt = car - '0';
        if (alt < 0 || alt > 9) return Color.rgb(74, 163, 223);
        if (alt <= 1 && cotiere(lignes, i, j)) return Color.rgb(240, 213, 140);
        int[] c = COULEURS_ALTITUDE[alt];
        return Color.rgb(c[0], c[1], c[2]);
    }

    /** Une case basse qui touche la mer est une plage. */
    private boolean cotiere(String[] lignes, int i, int j) {
        int[][] voisins = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] v : voisins) {
            int jj = j + v[1];
            if (jj < 0 || jj >= lignes.length) continue;
            String l = lignes[jj];
            int ii = i + v[0];
            if (ii >= 0 && ii < l.length() && l.charAt(ii) == '.') return true;
        }
        return false;
    }

    private static int couleurPeuple(int p, boolean schismatique) {
        if (schismatique) return Color.rgb(185, 28, 28);
        if (p < 0) return Color.rgb(100, 116, 139);
        int[] palette = {0x2563eb, 0x16a34a, 0xca8a04, 0x9333ea, 0x0891b2, 0xdb2777, 0x65a30d, 0xea580c};
        return palette[p % palette.length] | 0xFF000000;
    }

    private static int couleurEspece(String espece) {
        switch (espece) {
            case "loup": return Color.rgb(107, 114, 128);
            case "poisson": return Color.rgb(56, 189, 248);
            case "dragon": return Color.rgb(220, 38, 38);
            case "zombie": return Color.rgb(101, 163, 13);
            default: return Color.rgb(245, 245, 244);
        }
    }

    // ---- Dessin ----

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.rgb(11, 18, 32));
        ajusterInitialement();
        if (fond == null) return;
        RectF cadre = new RectF(offX, offY, offX + largeur * zoom, offY + hauteur * zoom);
        c.drawBitmap(fond, null, cadre, fondPaint);
        if (etat == null) return;

        float s = zoom;
        float r = 3f * density;

        // Terrain et couches du terrain
        peinture.setStyle(Paint.Style.FILL);
        peinture.setColor(Color.rgb(150, 140, 128));
        for (JSONArray p : paires("montagnes")) carre(c, p, s, 1f, peinture);
        for (JSONArray m : paires("modifs")) {
            try {
                peinture.setColor(m.getBoolean(2) ? Color.rgb(124, 204, 96) : Color.rgb(74, 163, 223));
                carre(c, m, s, 1f, peinture);
            } catch (JSONException ignore) { }
        }
        peinture.setColor(Color.argb(102, 236, 200, 120));
        for (JSONArray p : paires("deserts")) carre(c, p, s, 1f, peinture);
        peinture.setColor(Color.argb(102, 20, 70, 30));
        for (JSONArray p : paires("forets")) carre(c, p, s, 1f, peinture);
        peinture.setColor(Color.rgb(61, 139, 55));
        for (JSONArray p : paires("buissons")) carre(c, p, s, 0.8f, peinture);
        peinture.setColor(Color.rgb(96, 165, 250));
        for (JSONArray p : paires("rivieres")) carre(c, p, s, 1f, peinture);
        peinture.setColor(Color.rgb(59, 130, 246));
        for (JSONArray p : paires("lacs")) carre(c, p, s, 3f, peinture, -1f);
        peinture.setColor(Color.rgb(234, 88, 12));
        for (JSONArray p : paires("laves")) carre(c, p, s, 1f, peinture);
        peinture.setColor(Color.rgb(249, 115, 22));
        for (JSONArray p : paires("feux")) carre(c, p, s, 1f, peinture);

        // Villes, routes et villages
        if (afficherVilles) {
            peinture.setColor(Color.rgb(254, 240, 138));
            for (JSONArray p : paires("villes")) {
                c.drawCircle(offX + (p.optInt(0) + 0.5f) * s, offY + (p.optInt(1) + 0.5f) * s, Math.max(1.5f * density, s * 0.6f), peinture);
            }
        }
        peinture.setColor(Color.argb(230, 226, 232, 240));
        for (JSONArray p : paires("routes")) carre(c, p, s, 0.6f, peinture);
        for (JSONArray v : paires("villages")) {
            float x = offX + v.optInt(0) * s, y = offY + v.optInt(1) * s;
            float taille = Math.max(2 * density, s * 1.5f);
            peinture.setColor(Color.rgb(185, 28, 28));
            c.drawRect(x - taille / 2, y - taille / 2, x + taille / 2, y, peinture);
            peinture.setColor(Color.rgb(214, 184, 138));
            c.drawRect(x - taille / 2, y, x + taille / 2, y + taille / 2, peinture);
        }
        peinture.setColor(Color.rgb(250, 204, 21));
        for (JSONArray p : paires("vehicules")) carre(c, p, s, 0.9f, peinture);

        // Guerres
        JSONArray guerres = etat.optJSONArray("guerres");
        if (guerres != null) {
            for (int k = 0; k < guerres.length(); k++) {
                try {
                    JSONArray g = guerres.getJSONArray(k);
                    c.drawLine(offX + g.getInt(0) * s, offY + g.getInt(1) * s,
                            offX + g.getInt(2) * s, offY + g.getInt(3) * s, pointille);
                } catch (JSONException ignore) { }
            }
        }

        // Animaux
        JSONArray animaux = etat.optJSONArray("animaux");
        if (animaux != null) {
            for (int k = 0; k < animaux.length(); k++) {
                try {
                    JSONArray a = animaux.getJSONArray(k);
                    float cx = offX + (a.getInt(0) + 0.5f) * s, cy = offY + (a.getInt(1) + 0.5f) * s;
                    peinture.setColor(couleurEspece(a.getString(2)));
                    c.drawCircle(cx, cy, r, peinture);
                    c.drawCircle(cx, cy, r, contour);
                } catch (JSONException ignore) { }
            }
        }

        // Personnes
        java.util.HashSet<Integer> enPriere = new java.util.HashSet<>();
        JSONArray prieres = etat.optJSONArray("prieres");
        if (prieres != null) {
            for (int k = 0; k < prieres.length(); k++) enPriere.add(prieres.optJSONObject(k) == null ? -1 : prieres.optJSONObject(k).optInt("auteur", -1));
        }
        JSONArray personnes = etat.optJSONArray("personnes");
        if (personnes != null) {
            for (int k = 0; k < personnes.length(); k++) {
                JSONObject p = personnes.optJSONObject(k);
                if (p == null) continue;
                float cx = offX + (p.optInt("x") + 0.5f) * s, cy = offY + (p.optInt("y") + 0.5f) * s;
                if (enPriere.contains(p.optInt("id", -2))) {
                    peinture.setStyle(Paint.Style.STROKE);
                    peinture.setStrokeWidth(2 * density);
                    peinture.setColor(Color.rgb(245, 158, 11));
                    c.drawCircle(cx, cy, Math.max(4 * density, s * 2.5f), peinture);
                    peinture.setStyle(Paint.Style.FILL);
                }
                peinture.setColor(couleurPeuple(p.optInt("peuple", -1), p.optBoolean("schismatique", false)));
                c.drawCircle(cx, cy, r + density, peinture);
                c.drawCircle(cx, cy, r + density, contour);
            }
        }
    }

    /** Dessine un carré d'une case (ou de `taille` cases), à partir d'une entrée [x, y, ...]. */
    private void carre(Canvas c, JSONArray p, float s, float facteur, Paint peinture) {
        carre(c, p, s, facteur, peinture, 1f);
    }

    private void carre(Canvas c, JSONArray p, float s, float facteur, Paint peinture, float decalage) {
        float x = offX + p.optInt(0) * s, y = offY + p.optInt(1) * s;
        float cote = Math.max(1f, s * facteur);
        if (decalage < 0) {
            x -= s; y -= s;
        }
        c.drawRect(x, y, x + Math.max(1f, cote), y + Math.max(1f, cote), peinture);
    }

    private java.util.List<JSONArray> paires(String cle) {
        java.util.List<JSONArray> liste = new java.util.ArrayList<>();
        JSONArray tableau = etat.optJSONArray(cle);
        if (tableau == null) return liste;
        for (int k = 0; k < tableau.length(); k++) {
            JSONArray entree = tableau.optJSONArray(k);
            if (entree != null) liste.add(entree);
        }
        return liste;
    }
}
