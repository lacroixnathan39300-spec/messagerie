"""Langues inventées : chaque peuple crée ses mots, qui dérivent et s'empruntent.

Hors connexion, sans modèle : un lexique est un dictionnaire concept -> mot. Le joueur ne voit
la traduction d'un mot que pour les concepts qu'il a appris (voir `traduire`).
"""
import random

CONCEPTS = ("manger", "faim", "repos", "ami", "ennemi", "priere", "mort", "dieu",
            "terre", "eau", "guerre", "enfant", "feu", "chasser", "maison")
CONSONNES = "ptkmnlsrvz"
VOYELLES = "aeiou"
PROBA_DERIVE = 0.005  # chance par tour qu'un mot d'une langue change d'une syllabe
PROBA_EMPRUNT = 0.02  # chance par tour de contact qu'un peuple emprunte un mot à son voisin


def _mot(rng):
    syllabes = rng.choice((2, 2, 3))
    return "".join(rng.choice(CONSONNES) + rng.choice(VOYELLES) for _ in range(syllabes))


def creer(graine, identifiant):
    """Une langue, toujours la même pour une graine et un identifiant donnés."""
    rng = random.Random(f"{graine}-{identifiant}")
    lexique, utilises = {}, set()
    for concept in CONCEPTS:
        mot = _mot(rng)
        while mot in utilises:
            mot = _mot(rng)
        utilises.add(mot)
        lexique[concept] = mot
    return {"id": identifiant, "lexique": lexique}


def deriver(langue, rng):
    """Un mot change d'une consonne : la langue évolue lentement."""
    concept = rng.choice(CONCEPTS)
    mot = langue["lexique"][concept]
    i = rng.randrange(len(mot) // 2) * 2
    langue["lexique"][concept] = mot[:i] + rng.choice(CONSONNES) + mot[i + 1:]


def emprunter(receveuse, donneuse, rng):
    """La langue receveuse adopte le mot de la donneuse pour un concept."""
    concept = rng.choice(CONCEPTS)
    receveuse["lexique"][concept] = donneuse["lexique"][concept]


def mot_pour(langue, concept):
    return langue["lexique"][concept]


def traduire(mot, langue, concepts_connus):
    """Traduit un mot pour le joueur, seulement si il a appris le concept."""
    for concept, m in langue["lexique"].items():
        if m == mot:
            if concept in concepts_connus:
                return concept
            return f"« {mot} » (sens inconnu)"
    return f"« {mot} » (mot inconnu)"
