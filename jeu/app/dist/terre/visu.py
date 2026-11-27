"""Visualisation : une image SVG de l'île à un instant donné (terre, buissons, personnes, prières)."""

TAILLE_CASE = 12
MARGE = 10


def carte_svg(sim):
    """Renvoie le SVG de la carte de la simulation, sans dépendance externe."""
    m = sim.monde
    largeur = m.largeur * TAILLE_CASE + 2 * MARGE
    hauteur = m.hauteur * TAILLE_CASE + 2 * MARGE + 20
    elements = []
    for x, y in m.cases_terre():
        elements.append(
            f'<rect x="{MARGE + x * TAILLE_CASE}" y="{MARGE + y * TAILLE_CASE}" '
            f'width="{TAILLE_CASE}" height="{TAILLE_CASE}" fill="#d6cfa8"/>')
    for (x, y), quantite in m.nourriture.items():
        if quantite <= 0:
            continue
        cx = MARGE + x * TAILLE_CASE + TAILLE_CASE / 2
        cy = MARGE + y * TAILLE_CASE + TAILLE_CASE / 2
        rayon = 2 + 2 * quantite / 3
        elements.append(f'<circle cx="{cx}" cy="{cy}" r="{rayon:.1f}" fill="#3d8b37"/>')
    prieres_en_attente = {pr.auteur for pr in sim.prieres_en_attente()}
    for p in sim.vivantes():
        cx = MARGE + p.x * TAILLE_CASE + TAILLE_CASE / 2
        cy = MARGE + p.y * TAILLE_CASE + TAILLE_CASE / 2
        couleur = "#1d4ed8" if not p.schismatique else "#b91c1c"
        elements.append(f'<circle cx="{cx}" cy="{cy}" r="3.5" fill="{couleur}"/>')
        if p.identifiant in prieres_en_attente:
            elements.append(f'<circle cx="{cx}" cy="{cy}" r="6" fill="none" stroke="#f59e0b" stroke-width="1.5"/>')
    b = sim.bilan()
    legende = (f"tour {b['tour']} · vivantes {b['vivantes']} · morts {b['morts']} · "
               f"époque {b['epoque']}")
    elements.append(f'<text x="{MARGE}" y="{hauteur - 6}" font-size="11" '
                    f'font-family="sans-serif" fill="#222">{legende}</text>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{largeur}" height="{hauteur}" '
            f'viewBox="0 0 {largeur} {hauteur}">\n<rect width="100%" height="100%" fill="#eef6ff"/>\n'
            + "\n".join(elements) + "\n</svg>\n")
