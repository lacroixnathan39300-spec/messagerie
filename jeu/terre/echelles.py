"""Zoom : l'échelle choisie décide ce que le joueur voit et combien de temps passe par seconde."""
from dataclasses import dataclass

@dataclass(frozen=True)
class Echelle:
    nom: str
    jours_par_seconde: float  # 0 = en pause, on regarde une personne
    suivante: str | None = None  # zoom avant (vers le détail)
    precedente: str | None = None  # dézoom (vers le global)


PLANETE = Echelle("planete", 365.0, suivante="region")
REGION = Echelle("region", 30.0, suivante="territoire", precedente="planete")
TERRITOIRE = Echelle("territoire", 1.0, suivante="personne", precedente="region")
PERSONNE = Echelle("personne", 0.0, precedente="territoire")

ECHELLES = {e.nom: e for e in (PLANETE, REGION, TERRITOIRE, PERSONNE)}


def zoomer(echelle):
    return ECHELLES[echelle.suivante] if echelle.suivante else echelle


def dezoomer(echelle):
    return ECHELLES[echelle.precedente] if echelle.precedente else echelle


def jours_pour(echelle, secondes):
    """Nombre de jours simulés pendant `secondes` secondes réelles."""
    return int(echelle.jours_par_seconde * secondes)
