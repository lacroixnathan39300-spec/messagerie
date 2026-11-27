// Interface de Terre vivante : le moteur Python tourne dans le navigateur (Pyodide).
import { loadPyodide } from "./pyodide/pyodide.mjs";

const $ = (id) => document.getElementById(id);
const CLE_BASE = "terre-vivante-partie";
const cle = () => CLE_BASE + "-" + $("emplacement").value;
let LARGEUR_TERRE = 720, HAUTEUR_TERRE = 360;
const COULEURS_ALTITUDE = [
  [124,204,96],[140,212,100],[168,218,108],[196,214,118],[222,200,120],
  [200,164,110],[178,140,100],[150,122,98],[210,206,200],[250,250,250]];
const COULEURS_ZONE = {
  0: [232,232,220], 1: [90,90,95], 2: [210,120,80], 3: [150,150,170], 4: [110,110,130],
  5: [200,220,140], 6: [60,60,60], 7: [240,200,60], 8: [150,120,100] };
const MER = [74, 163, 223];

let py, etat = null, enCours = true, zoomCarte = 1, decalage = { x: 0, y: 0 };
let fondTerre = null, dernierClic = null, fondIle = null, survol = null;

function appeler(expression) {
  return JSON.parse(py.runPython(`import json, moteur\njson.dumps(${expression})`));
}

async function demarrer() {
  py = await loadPyodide({ indexURL: "./pyodide/" });
  py.FS.mkdirTree("/app");
  const fichiers = await (await fetch("fichiers.json")).json();
  for (const f of fichiers) {
    const pos = f.lastIndexOf("/");
    if (pos > 0) py.FS.mkdirTree("/app/" + f.slice(0, pos));
    py.FS.writeFile("/app/" + f, await (await fetch(f)).text());
  }
  py.runPython("import sys\nsys.path.insert(0, '/app')\nimport moteur");
  fondTerre = construireFond(py.FS.readFile("/app/terre/donnees/terre_0_5deg.txt", { encoding: "utf8" }));

  const sauve = localStorage.getItem(cle());
  if (sauve) {
    py.globals.set("texte_entree", sauve);
    etat = JSON.parse(py.runPython("import json, moteur\njson.dumps(moteur.charger_texte(texte_entree))"));
  } else {
    etat = appeler("moteur.nouvelle_partie(1)");
  }
  window.terreVivante = { etat: () => etat, avancerJours: (n) => { etat = appeler(`moteur.avancer(${n})`); return etat; }, vue: (z, dx, dy) => { zoomCarte = z; decalage = { x: dx, y: dy }; rendre(); } };  // pour les tests automatiques
  $("pret").style.display = "none";
  $("jeu").style.display = "block";
  ajusterCarte();
  rendre();
  lier();
  if ("serviceWorker" in navigator) {
    // Refusé dans certains hébergements : l'appli marche quand même, seulement sans cache hors connexion.
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
  window.addEventListener("resize", () => { ajusterCarte(); rendre(); });
  // Le canevas suit la taille réelle de sa zone, même si la barre du haut change de hauteur.
  new ResizeObserver(() => { ajusterCarte(); rendre(); }).observe($("carte-wrap"));
  setTimeout(() => { ajusterCarte(); rendre(); }, 500);
  setInterval(() => { if (enCours) avancer(); }, 1000);  // 1 seconde = 1 tour (1 jour) à la vitesse ×1
  setInterval(() => { localStorage.setItem(cle(), py.runPython("moteur.sauver()")); }, 30000);
}

function construireFond(texte) {
  const lignes = texte.split("\n").filter((l) => l.length);
  const c = document.createElement("canvas");
  c.width = LARGEUR_TERRE; c.height = HAUTEUR_TERRE;
  const ctx = c.getContext("2d");
  const img = ctx.createImageData(LARGEUR_TERRE, HAUTEUR_TERRE);
  for (let j = 0; j < HAUTEUR_TERRE; j++) {
    for (let i = 0; i < LARGEUR_TERRE; i++) {
      const car = lignes[j][i];
      let [r, g, b] = car === "." ? MER : COULEURS_ALTITUDE[Number(car)];
      // Plage : terre basse (altitude 0 ou 1) qui touche la mer.
      if (car !== "." && Number(car) <= 1) {
        const cotiere = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => {
          const ligne = lignes[j + dy];
          return ligne !== undefined && ligne[i + dx] === ".";
        });
        if (cotiere) [r, g, b] = [240, 213, 140];
      }
      const k = (j * LARGEUR_TERRE + i) * 4;
      img.data[k] = r; img.data[k + 1] = g; img.data[k + 2] = b; img.data[k + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  return c;
}

function ajusterCarte() {
  const canvas = $("carte");
  const largeur = $("carte-wrap").clientWidth, hauteur = $("carte-wrap").clientHeight;
  if (!etat || !largeur || !hauteur) return;   // mise en page pas encore calculée : on attend
  canvas.style.width = largeur + "px";
  canvas.style.height = hauteur + "px";
  canvas.width = largeur * devicePixelRatio;
  canvas.height = hauteur * devicePixelRatio;
  LARGEUR_TERRE = etat.largeur || 720; HAUTEUR_TERRE = etat.hauteur || 360;
  zoomCarte = canvas.width / LARGEUR_TERRE;
  decalage = { x: 0, y: (canvas.height - HAUTEUR_TERRE * zoomCarte) / 2 };
}

function rendre() {
  if (!etat) return;
  const b = etat.bilan;
  $("tour").textContent = b.tour;
  $("epoque").textContent = b.epoque;
  $("vivants").textContent = b.vivantes;
  $("morts").textContent = b.morts;
  $("peuples").textContent = b.peuples;
  $("lecture").textContent = enCours ? "⏸ Pause" : "▶ Lancer";
  dessinerCarte();
  afficherPrieres();
  afficherEnergie();
  afficherChronique();
}

// Les bords de la carte : on ne peut pas glisser au-delà, sauf si l'option « bords ouverts » est cochée.
function bornerVue() {
  const canvas = $("carte");
  const largeur = LARGEUR_TERRE * zoomCarte, hauteur = HAUTEUR_TERRE * zoomCarte;
  if ($("reglage-bords").checked) return;
  decalage.x = largeur <= canvas.width ? (canvas.width - largeur) / 2 : Math.min(0, Math.max(canvas.width - largeur, decalage.x));
  decalage.y = hauteur <= canvas.height ? (canvas.height - hauteur) / 2 : Math.min(0, Math.max(canvas.height - hauteur, decalage.y));
}

function dessinerCarte() {
  bornerVue();
  const km = KM_PAR_CASE / zoomCarte;   // km représentés par un pixel de l'écran
  $("echelle").textContent = km >= 10 ? `1 px ≈ ${Math.round(km)} km` : `1 px ≈ ${km.toFixed(1)} km`;
  const canvas = $("carte");
  const ctx = canvas.getContext("2d");
  const s = zoomCarte;
  ctx.fillStyle = "#0b1220";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(fondCourant(), decalage.x, decalage.y, LARGEUR_TERRE * s, HAUTEUR_TERRE * s);
  for (const [x, y] of etat.montagnes) {
    ctx.fillStyle = "rgb(150,140,128)";
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.ceil(s), Math.ceil(s));
  }
  for (const [x, y, terre] of etat.modifs) {
    ctx.fillStyle = terre ? "rgb(124,204,96)" : "rgb(74,163,223)";
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.ceil(s), Math.ceil(s));
  }
  // Forêts approximatives (zones tropicales et boréales), dessinées par case.
  ctx.fillStyle = "rgba(20,70,30,0.4)";
  for (const [x, y] of etat.forets) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s), Math.max(1, s));
  }
  // Forêt : les endroits où les buissons sont serrés (au moins 4 buissons dans un rayon de 2 cases).
  const buissons = new Set(etat.buissons.map(([x, y]) => x + "," + y));
  ctx.fillStyle = "rgba(20,70,30,0.55)";
  for (const [x, y] of etat.buissons) {
    let voisins = 0;
    for (let dx = -2; dx <= 2; dx++) for (let dy = -2; dy <= 2; dy++) if (buissons.has((x + dx) + "," + (y + dy))) voisins++;
    if (voisins >= 5) ctx.fillRect(decalage.x + (x - 2) * s, decalage.y + (y - 2) * s, 5 * s, 5 * s);
  }
  ctx.fillStyle = "#3d8b37";
  for (const [x, y] of etat.buissons) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s * 0.8), Math.max(1, s * 0.8));
  }
  const enPriere = new Set(etat.prieres.map((p) => p.auteur));
  for (const [x, y, nom] of ($("reglage-villes").checked ? etat.villes : [])) {
    const cx = decalage.x + x * s, cy = decalage.y + y * s;
    ctx.fillStyle = "#fef08a";
    ctx.beginPath(); ctx.arc(cx, cy, Math.max(1.5, s * 0.6), 0, 2 * Math.PI); ctx.fill();
    if (zoomCarte >= 4) {
      ctx.fillStyle = "#e2e8f0"; ctx.font = `${Math.max(10, s * 1.2)}px sans-serif`;
      ctx.fillText(nom, cx + s, cy - s);
    }
  }
  ctx.fillStyle = "rgba(226,232,240,0.85)";
  for (const [x, y] of etat.routes) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s * 0.6), Math.max(1, s * 0.6));
  }
  ctx.fillStyle = "#facc15";
  for (const [x, y] of etat.vehicules) {
    if (zoomCarte >= SEUIL_SPRITES) { dessinerSprite(ctx, "voiture", x, y, s, null, 0.7); continue; }
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(2, s * 0.9), Math.max(2, s * 0.9));
  }
  ctx.strokeStyle = "rgba(220,38,38,0.9)"; ctx.lineWidth = Math.max(1.5, s * 0.5); ctx.setLineDash([s * 2, s]);
  for (const [x1, y1, x2, y2] of etat.guerres) {
    ctx.beginPath(); ctx.moveTo(decalage.x + x1 * s, decalage.y + y1 * s); ctx.lineTo(decalage.x + x2 * s, decalage.y + y2 * s); ctx.stroke();
  }
  ctx.setLineDash([]);
  ctx.fillStyle = "#ea580c";
  for (const [x, y] of etat.laves) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s), Math.max(1, s));
  }
  ctx.fillStyle = "#3b82f6";
  for (const [x, y] of etat.lacs) {
    ctx.fillRect(decalage.x + (x - 1) * s, decalage.y + (y - 1) * s, Math.max(1, s * 3), Math.max(1, s * 3));
  }
  ctx.fillStyle = "#60a5fa";
  for (const [x, y] of etat.rivieres) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s), Math.max(1, s));
  }
  ctx.fillStyle = "rgb(236,200,120)";
  for (const [x, y] of etat.deserts) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s), Math.max(1, s));
  }
  const POSITIONS_MAISONS = [[-2, 0], [2, 0], [0, -2], [0, 2], [-2, -2], [2, 2], [-2, 2], [2, -2]];
  for (const [x, y, maisons] of etat.villages) {
    if (zoomCarte >= SEUIL_SPRITES) {
      dessinerSprite(ctx, "maison", x, y, s, null, 1);
      for (const [dx, dy] of POSITIONS_MAISONS.slice(0, maisons - 1)) {
        dessinerSprite(ctx, "maison", x + dx, y + dy, s, null, 0.8);
      }
      continue;
    }
    const cx = decalage.x + x * s, cy = decalage.y + y * s, taille = Math.max(3, s * 2);
    ctx.fillStyle = "#92400e";
    ctx.fillRect(cx - taille / 2, cy - taille / 2, taille, taille);
    ctx.fillStyle = "#a8a29e";
    for (const [dx, dy] of POSITIONS_MAISONS.slice(0, maisons - 1)) {
      ctx.fillRect(cx + dx * s - taille / 4, cy + dy * s - taille / 4, taille / 2, taille / 2);
    }
  }
  ctx.fillStyle = "#f97316";
  for (const [x, y] of etat.feux) {
    ctx.fillRect(decalage.x + x * s, decalage.y + y * s, Math.max(1, s), Math.max(1, s));
  }
  for (const [x, y, espece] of etat.animaux) {
    if (zoomCarte >= SEUIL_SPRITES) { dessinerSprite(ctx, espece, x, y, s); continue; }
    ctx.fillStyle = {loup: "#6b7280", poisson: "#38bdf8", dragon: "#dc2626", zombie: "#65a30d"}[espece] || "#f5f5f4";
    ctx.beginPath(); ctx.arc(decalage.x + (x + 0.5) * s, decalage.y + (y + 0.5) * s, 3 * (window.devicePixelRatio || 1), 0, 2 * Math.PI); ctx.fill();
    ctx.strokeStyle = "#111827"; ctx.lineWidth = 1; ctx.stroke();   // contour sombre : lisible sur toutes les couleurs
  }
  for (const p of etat.personnes) {
    const cx = decalage.x + p.x * s, cy = decalage.y + p.y * s;
    if (enPriere.has(p.id)) {
      ctx.strokeStyle = "#f59e0b"; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(cx, cy, Math.max(4, s * 2.5), 0, 2 * Math.PI); ctx.stroke();
    }
    const couleur = couleurPersonne(p);
    if (zoomCarte >= SEUIL_SPRITES) { dessinerSprite(ctx, "humain", p.x, p.y, s, couleur); continue; }
    ctx.fillStyle = couleur;
    ctx.beginPath(); ctx.arc(cx, cy, 3.5 * (window.devicePixelRatio || 1), 0, 2 * Math.PI); ctx.fill();
    ctx.strokeStyle = "#111827"; ctx.lineWidth = 1; ctx.stroke();
  }
  // Cercle du pinceau, au survol du curseur ou sous le doigt.
  if (survol && $("outil").value !== "voir") {
    const r = Number($("taille").value) + 0.5;
    ctx.strokeStyle = "rgba(255,255,255,0.9)"; ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(decalage.x + (survol.i + 0.5) * s, decalage.y + (survol.j + 0.5) * s, r * s, 0, 2 * Math.PI);
    ctx.stroke();
  }
}

// Petits personnages en pixels, dessinés quand on zoome assez pour qu'un pixel reste visible.
// Une grille de 8 x 8 par espèce ; un personnage fait environ une case de large.
const SEUIL_SPRITES = 0;   // les êtres sont toujours dessinés en symboles de taille fixe
// Échelle : une case de la grille fait environ 55,6 km ; à ce zoom, 1 pixel représente 1 km.
const KM_PAR_CASE = 55.6, ZOOM_REEL = KM_PAR_CASE, ZOOM_MIN = 0.5, ZOOM_MAX = 120;
// Chaque peuple a sa couleur ; les schismatiques sont en rouge, les personnes sans peuple en gris.
const COULEURS_PEUPLES = ["#2563eb", "#16a34a", "#ca8a04", "#9333ea", "#0891b2", "#db2777", "#65a30d", "#ea580c"];
function couleurPersonne(p) {
  if (p.schismatique) return "#b91c1c";
  return p.peuple >= 0 ? COULEURS_PEUPLES[p.peuple % COULEURS_PEUPLES.length] : "#64748b";
}
// Lettres : b = corps, c = peau, w = blanc, g = gris ou vert, r = rouge, k = contour sombre, y = jaune.
const PALETTES = {
  humain: { b: "#1d4ed8", c: "#fcd9b6", k: "#1c1917" },
  mouton: { w: "#f5f5f4", k: "#292524" },
  loup: { g: "#6b7280", w: "#e5e7eb", k: "#1f2937" },
  dragon: { r: "#dc2626", k: "#450a0a", y: "#facc15" },
  zombie: { g: "#65a30d", k: "#1a2e05" },
  poisson: { b: "#38bdf8", k: "#0c4a6e" },
  maison: { r: "#b91c1c", m: "#d6b88a", k: "#292524", y: "#fde68a" },
  voiture: { y: "#facc15", k: "#1c1917", w: "#bae6fd" },
};
const SPRITES = {
  humain: ["..cccc..", "..cccc..", ".bbbbbb.", "bbbbbbbb", ".bbbbbb.", ".bbbbbb.", ".bb..bb.", ".kk..kk."],
  mouton: ["..wwww..", ".wwwwwww", "wwwwwwww", "wwwwwwww", ".wwwwww.", "k.k..k.k", "........", "........"],
  loup: ["k.....k.", "kk...kk.", "gggggggg", "gggggggg", "gggggggw", ".gggggg.", ".g.gg.g.", ".k.kk.k."],
  dragon: ["kk....kk", "krk..krk", "krrrrrrk", ".krrrrk.", "kryrrryk", ".rrrrrr.", "..r..r..", "..k..k.."],
  zombie: ["..gggg..", ".gkggkg.", "..gggg..", "gggggggg", "..gggg..", "..gggg..", "..g..g..", "..k..k.."],
  poisson: ["........", "...bbb..", ".bbbbbb.", "bbbbbbbb", ".bbbbbb.", "..bbb...", "........", "........"],
  maison: ["...rr...", "..rrrr..", ".rrrrrr.", "rrrrrrrr", "mmmmmmmm", "myymmkmm", "mmmmmkmm", "mmmmmkmm"],
  voiture: ["........", "..kkkk..", ".kwwwwk.", "yyyyyyyy", "yyyyyyyy", "kkk..kkk", "........", "........"],
};
function dessinerSprite(ctx, espece, x, y, s, corps, echelle = 1) {
  const grille = SPRITES[espece];
  if (!grille) return;
  const pal = { ...PALETTES[espece] };
  if (corps) pal.b = corps;
  const q = echelle * 1.6 * (window.devicePixelRatio || 1);   // symbole de taille fixe, quel que soit le zoom
  const cx = decalage.x + x * s + s / 2;
  const cy = decalage.y + y * s + s / 2;
  const gauche = cx - 4 * q, haut = cy - 6 * q;
  grille.forEach((ligne, r) => {
    [...ligne].forEach((c, col) => {
      if (c !== "." && pal[c]) {
        ctx.fillStyle = pal[c];
        ctx.fillRect(gauche + col * q, haut + r * q, Math.ceil(q), Math.ceil(q));
      }
    });
  });
}

function afficherPrieres() {
  const zone = $("prieres");
  if (!etat.prieres.length) { zone.textContent = "Aucune prière pour l'instant."; return; }
  zone.innerHTML = "";
  for (const p of etat.prieres) {
    const div = document.createElement("div");
    div.className = "priere";
    div.innerHTML = `<div>Personne n°${p.auteur} : « ${p.texte} »</div><div class="boutons"></div>`;
    for (const choix of ["exaucer", "modifier", "refuser", "ignorer"]) {
      const bouton = document.createElement("button");
      bouton.textContent = { exaucer: "Exaucer", modifier: "Modifier", refuser: "Refuser", ignorer: "Ignorer" }[choix];
      bouton.onclick = () => {
        const r = appeler(`moteur.decider(${p.id}, '${choix}')`);
        etat = r.etat; rendre();
      };
      div.querySelector(".boutons").appendChild(bouton);
    }
    zone.appendChild(div);
  }
}

function afficherEnergie() {
  const zone = $("energie");
  if (!etat.energie.length) { zone.textContent = "Pas encore de peuple."; return; }
  zone.innerHTML = etat.energie.map((e) =>
    `<div class="ligne">${e.membres} personnes · besoin ${e.besoin} · production ${e.production} · <b>${e.statut}</b>` +
    `<div class="petit">Sources : ${e.sources.join(", ") || "aucune"}</div></div>`).join("");
}

function afficherChronique() {
  const zone = $("chronique");
  if (!etat.chronique.length) { zone.textContent = "Rien d'historique encore."; return; }
  zone.innerHTML = etat.chronique.slice().reverse().map((e) =>
    `<div class="ligne">Tour ${e.tour} : ${e.texte}</div>`).join("");
}

function fondCourant() {
  if (!etat.terre) return fondTerre;
  const cle = `${etat.largeur}x${etat.hauteur}:${etat.terre.length}:${etat.terre[0]}`;
  if (!fondIle || fondIle.cle !== cle) {
    const c = document.createElement("canvas");
    c.width = etat.largeur; c.height = etat.hauteur;
    const ctx = c.getContext("2d");
    ctx.fillStyle = "rgb(74,163,223)"; ctx.fillRect(0, 0, c.width, c.height);
    ctx.fillStyle = "rgb(124,204,96)";
    for (const [x, y] of etat.terre) ctx.fillRect(x, y, 1, 1);
    // Plages : les cases de terre qui touchent la mer.
    const terre = new Set(etat.terre.map(([x, y]) => x + "," + y));
    ctx.fillStyle = "rgb(240,213,140)";
    for (const [x, y] of etat.terre) {
      if (![[1, 0], [-1, 0], [0, 1], [0, -1]].every(([dx, dy]) => terre.has((x + dx) + "," + (y + dy)))) ctx.fillRect(x, y, 1, 1);
    }
    fondIle = { cle, canvas: c };
  }
  return fondIle.canvas;
}

let delaiMessage = null;
function message(texte) {
  $("message").textContent = texte;
  clearTimeout(delaiMessage);
  delaiMessage = setTimeout(() => { $("message").textContent = ""; }, 3000);
}

// Petit bip d'action, joué seulement si le réglage « son » est coché.
let contexteAudio = null;
function bip(frequence = 660, duree = 0.06) {
  if (!$("reglage-son").checked) return;
  try {
    contexteAudio = contexteAudio || new (window.AudioContext || window.webkitAudioContext)();
    const osc = contexteAudio.createOscillator(), gain = contexteAudio.createGain();
    osc.frequency.value = frequence;
    gain.gain.value = 0.05;
    osc.connect(gain).connect(contexteAudio.destination);
    osc.start();
    osc.stop(contexteAudio.currentTime + duree);
  } catch (e) { /* pas de son disponible : on ignore */ }
}

function agir(i, j) {
  const r = appeler(`moteur.agir('${$("outil").value}', ${i}, ${j}, ${$("taille").value})`);
  etat = r.etat;
  message(r.message);
  if (r.message && !/Il faut|déjà|C'est la mer/.test(r.message)) bip(520 + Math.random() * 200);
  rendre();
}

function avancer() {
  etat = appeler(`moteur.avancer(${$("vitesse").value})`);
  rendre();
}

function lier() {
  const select = $("outil");
  for (const bouton of document.querySelectorAll("#dock .onglets button")) {
    bouton.onclick = () => {
      document.querySelectorAll("#dock .onglets button").forEach((b) => b.classList.toggle("actif", b === bouton));
      document.querySelectorAll("#dock .outils").forEach((d) => { d.hidden = d.dataset.categorie !== bouton.dataset.categorie; });
      document.querySelector(`#dock .outils[data-categorie="${bouton.dataset.categorie}"] button`).click();
    };
  }
  for (const bouton of document.querySelectorAll("#dock .outils button")) {
    bouton.onclick = () => {
      select.value = bouton.dataset.outil;
      document.querySelectorAll("#dock .outils button").forEach((b) => b.classList.toggle("actif", b === bouton));
    };
  }
  $("lecture").onclick = () => { enCours = !enCours; rendre(); };
  // Les boutons zooment autour du centre de l'écran.
  const centreEcran = () => ({ x: $("carte").width / 2, y: $("carte").height / 2 });
  $("echelle-reelle").onclick = () => { const c = centreEcran(); zoomAutour(ZOOM_REEL / zoomCarte, c.x, c.y); };
  $("zoom+").onclick = () => { const c = centreEcran(); zoomAutour(1.4, c.x, c.y); };
  $("zoom-").onclick = () => { const c = centreEcran(); zoomAutour(1 / 1.4, c.x, c.y); };
  $("sauver").onclick = () => { localStorage.setItem(cle(), py.runPython("moteur.sauver()")); message("Partie sauvegardée sur ce téléphone."); };
  $("charger").onclick = () => {
    const sauve = localStorage.getItem(cle());
    if (!sauve) { message("Aucune sauvegarde."); return; }
    py.globals.set("texte_entree", sauve);
    etat = JSON.parse(py.runPython("import json, moteur\njson.dumps(moteur.charger_texte(texte_entree))"));
    rendre();
  };
  $("nouvelle").onclick = () => {
    const graine = Math.max(1, parseInt($("graine").value || "1", 10));
    etat = appeler(`moteur.nouvelle_partie(${graine}, '${$("carte-type").value}')`);
    ajusterCarte();
    rendre();
  };
  $("fermer-zone").onclick = () => { $("zone-wrap").style.display = "none"; };

  const carte = $("carte");
  let glisse = null;
  carte.addEventListener("pointerdown", (e) => {
    if (doigts.size >= 1) { glisse = null; return; }   // pas de glisser ni de toucher pendant un pincement
    glisse = { x: e.clientX, y: e.clientY, dx: decalage.x, dy: decalage.y, moved: false };
  });
  carte.addEventListener("pointermove", (e) => {
    if (!glisse) { survol = caseSous(e); if (survol) dessinerCarte(); return; }
    if (doigts.size > 1) return;
    const dx = (e.clientX - glisse.x) * devicePixelRatio, dy = (e.clientY - glisse.y) * devicePixelRatio;
    if (Math.abs(dx) + Math.abs(dy) > 4) glisse.moved = true;
    decalage = { x: glisse.dx + dx, y: glisse.dy + dy };
    dessinerCarte();
  });
  carte.addEventListener("pointerup", (e) => {
    if (glisse && !glisse.moved && doigts.size <= 1) toucher(e);
    glisse = null;
  });
  carte.style.touchAction = "none";
  // Pincement à deux doigts : on suit les doigts pour zoomer autour de leur milieu.
  const doigts = new Map();
  let pincement = null;
  const milieuEtDistance = () => {
    const [a, b] = [...doigts.values()];
    return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, d: Math.hypot(a.x - b.x, a.y - b.y) };
  };
  carte.addEventListener("pointerdown", (e) => {
    doigts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (doigts.size === 2) pincement = milieuEtDistance();
  });
  carte.addEventListener("pointermove", (e) => {
    if (!doigts.has(e.pointerId)) return;
    doigts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (doigts.size === 2 && pincement) {
      const m = milieuEtDistance();
      const rect = carte.getBoundingClientRect();
      zoomAutour(m.d / pincement.d, (m.x - rect.left) * devicePixelRatio, (m.y - rect.top) * devicePixelRatio);
      decalage.x += (m.x - pincement.x) * devicePixelRatio;
      decalage.y += (m.y - pincement.y) * devicePixelRatio;
      pincement = m;
      dessinerCarte();
    }
  });
  const relacher = (e) => {
    doigts.delete(e.pointerId);
    if (doigts.size < 2) pincement = null;
  };
  carte.addEventListener("pointerup", relacher);
  carte.addEventListener("pointercancel", relacher);
  carte.addEventListener("wheel", (e) => {
    e.preventDefault();
    const rect = carte.getBoundingClientRect();
    zoomAutour(e.deltaY < 0 ? 1.15 : 1 / 1.15, (e.clientX - rect.left) * devicePixelRatio, (e.clientY - rect.top) * devicePixelRatio);
    dessinerCarte();
  }, { passive: false });
}

// Case de la carte sous le curseur (ou le doigt).
function caseSous(e) {
  const rect = $("carte").getBoundingClientRect();
  const px = (e.clientX - rect.left) * devicePixelRatio, py_ = (e.clientY - rect.top) * devicePixelRatio;
  return { i: Math.floor((px - decalage.x) / zoomCarte), j: Math.floor((py_ - decalage.y) / zoomCarte) };
}

// Zoom autour d'un point de l'écran (en pixels du canevas) : ce point reste sous le doigt ou le curseur.
function zoomAutour(facteur, x, y) {
  const nouveau = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, zoomCarte * facteur));
  const k = nouveau / zoomCarte;
  decalage = { x: x - (x - decalage.x) * k, y: y - (y - decalage.y) * k };
  zoomCarte = nouveau;
  rendre();
}

function toucher(e) {
  const rect = $("carte").getBoundingClientRect();
  const px = (e.clientX - rect.left) * devicePixelRatio, py_ = (e.clientY - rect.top) * devicePixelRatio;
  const i = Math.round((px - decalage.x) / zoomCarte), j = Math.round((py_ - decalage.y) / zoomCarte);
  if ($("outil").value !== "voir") { agir(i, j); return; }
  let meilleure = null, distance = Infinity;
  for (const p of etat.personnes) {
    const d = Math.hypot(p.x - i, p.y - j);
    if (d < distance) { distance = d; meilleure = p; }
  }
  if (!meilleure || distance * zoomCarte > 30) { $("info").style.display = "none"; return; }
  dernierClic = meilleure;
  $("info").style.display = "block";
  $("info").innerHTML =
    `<h3>Personne n°${meilleure.id}</h3>` +
    `<div>Soif ${meilleure.soif} · Faim ${meilleure.faim} · Foi ${meilleure.foi}` +
    `${meilleure.schismatique ? " · schismatique" : ""}</div>` +
    `<div>Âge ${meilleure.age} ans · ${meilleure.marie ? "marié(e)" : "célibataire"} · ${meilleure.enfants} enfant(s)</div>` +
    `<div>Langue : ${meilleure.langue} · Savoirs : ${meilleure.savoirs.length ? meilleure.savoirs.join(", ") : "aucun"}</div>` +
    `<div class="petit">Position : ${meilleure.x}, ${meilleure.y}</div>` +
    `<button id="voir-zone" class="principal" style="margin-top:6px">Voir la zone à 5 m par case</button>`;
  $("voir-zone").onclick = voirZone;
}

function voirZone() {
  const z = appeler(`moteur.zone(${dernierClic.x}, ${dernierClic.y})`);
  const canvas = $("zone-canvas");
  canvas.width = z.cote; canvas.height = z.cote;
  const ctx = canvas.getContext("2d");
  const img = ctx.createImageData(z.cote, z.cote);
  for (let k = 0; k < z.cote * z.cote; k++) {
    const [r, g, b] = COULEURS_ZONE[z.cases[k]];
    img.data[k * 4] = r; img.data[k * 4 + 1] = g; img.data[k * 4 + 2] = b; img.data[k * 4 + 3] = 255;
  }
  ctx.putImageData(img, 0, 0);
  for (const v of z.vehicules) {
    ctx.fillStyle = v.type === "bus" ? "#ffc800" : v.type === "avion" ? "#ef4444" : "#ffffff";
    ctx.fillRect(v.x - 1, v.y - 1, 2, 2);
  }
  $("zone-wrap").style.display = "block";
  $("zone-wrap").scrollIntoView({ behavior: "smooth" });
}

demarrer().catch((err) => {
  $("pret").textContent = "Impossible de démarrer : " + err;
  console.error(err);
});
