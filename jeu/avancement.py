"""Calcule l'avancement à partir des cases cochées dans PROGRESS.md.

Met à jour les pourcentages dans PROGRESS.md et écrit avancement.svg (barre visible sur GitHub).
Usage : python3 avancement.py
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
PROGRESS = os.path.join(HERE, "PROGRESS.md")
SORTIE_SVG = os.path.join(HERE, "avancement.svg")

RE_PHASE = re.compile(r"^## (Phase \d[^(\n]*?)\s*(\(\d+ %\))?\s*$")
RE_TACHE = re.compile(r"^- \[( |x)\] ")
RE_GLOBAL = re.compile(r"^## Avancement global : \d+ %")


def lire_phases(lignes):
    phases = []
    for ligne in lignes:
        m = RE_PHASE.match(ligne)
        if m:
            phases.append({"nom": m.group(1).strip(), "faites": 0, "total": 0})
        elif RE_TACHE.match(ligne) and phases:
            phases[-1]["total"] += 1
            if ligne.startswith("- [x] "):
                phases[-1]["faites"] += 1
    for phase in phases:
        phase["pourcentage"] = round(100 * phase["faites"] / phase["total"]) if phase["total"] else 0
    return phases


def mettre_a_jour_markdown(lignes, phases):
    sortie = []
    index = 0
    for ligne in lignes:
        if RE_GLOBAL.match(ligne):
            moyenne = round(sum(p["pourcentage"] for p in phases) / len(phases)) if phases else 0
            sortie.append(f"## Avancement global : {moyenne} %\n")
        elif RE_PHASE.match(ligne) and index < len(phases):
            nom = phases[index]["nom"]
            sortie.append(f"## {nom} ({phases[index]['pourcentage']} %)\n")
            index += 1
        else:
            sortie.append(ligne)
    return sortie


def barre_texte(pourcentage, largeur=30):
    pleins = round(largeur * pourcentage / 100)
    return "█" * pleins + "░" * (largeur - pleins)


def ecrire_svg(phases, moyenne, chemin):
    largeur_piste = 420
    hauteur_ligne = 34
    lignes_svg = []
    y = 46
    for phase in [{"nom": "Ensemble", "pourcentage": moyenne}] + phases:
        largeur_remplie = largeur_piste * phase["pourcentage"] / 100
        lignes_svg.append(
            f'<text x="16" y="{y}" font-size="13" font-family="sans-serif" fill="#222">{phase["nom"]}</text>'
            f'<rect x="210" y="{y - 13}" width="{largeur_piste}" height="14" rx="7" fill="#e5e7eb"/>'
            f'<rect x="210" y="{y - 13}" width="{largeur_remplie:.1f}" height="14" rx="7" fill="#2563eb"/>'
            f'<text x="{210 + largeur_piste + 10}" y="{y}" font-size="13" font-family="sans-serif" fill="#222">{phase["pourcentage"]} %</text>'
        )
        y += hauteur_ligne
    hauteur = y + 10
    contenu = "\n  ".join(lignes_svg)
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="720" height="{hauteur}" viewBox="0 0 720 {hauteur}">\n'
        f'  <rect width="100%" height="100%" fill="#ffffff"/>\n'
        f'  <text x="16" y="24" font-size="16" font-family="sans-serif" font-weight="bold" fill="#111">'
        f'Terre vivante : avancement</text>\n'
        f'  {contenu}\n'
        f'</svg>\n'
    )
    with open(chemin, "w", encoding="utf-8") as f:
        f.write(svg)


def main():
    with open(PROGRESS, encoding="utf-8") as f:
        lignes = f.readlines()
    phases = lire_phases(lignes)
    moyenne = round(sum(p["pourcentage"] for p in phases) / len(phases)) if phases else 0
    with open(PROGRESS, "w", encoding="utf-8") as f:
        f.writelines(mettre_a_jour_markdown(lignes, phases))
    ecrire_svg(phases, moyenne, SORTIE_SVG)
    print(f"Ensemble  {barre_texte(moyenne)}  {moyenne} %")
    for phase in phases:
        print(f"{phase['nom'][:12]:<12}  {barre_texte(phase['pourcentage'])}  "
              f"{phase['pourcentage']} % ({phase['faites']}/{phase['total']})")


if __name__ == "__main__":
    main()
