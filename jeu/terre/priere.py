"""Prières : une personne demande quelque chose, le joueur exauce, refuse, modifie ou ignore."""
from dataclasses import dataclass

EXAUCER = "exaucer"
REFUSER = "refuser"
MODIFIER = "modifier"
IGNORER = "ignorer"
CHOIX = (EXAUCER, REFUSER, MODIFIER, IGNORER)

DUREE_PRIERE = 20  # tours avant qu'une prière non traitée soit ignorée
SEUIL_PRIERE = 0.6  # intensité minimale pour qu'un besoin devienne une prière

TEXTES = {
    "faim": "Donne-nous à manger, nous avons faim.",
    "repos": "Fais que nous puissions nous reposer.",
    "compagnon": "Fais que je trouve quelqu'un qui partage ma vie.",
}


@dataclass
class Priere:
    identifiant: int
    auteur: int
    besoin: str
    intensite: float
    tour: int
    statut: str = "en_attente"  # en_attente, ou le choix retenu

    @property
    def texte(self):
        return TEXTES[self.besoin]

    def en_attente(self):
        return self.statut == "en_attente"


def besoins_pressants(personne, est_seul_adulte=False):
    """Liste (besoin, intensité) des besoins assez forts pour devenir une prière."""
    besoins = []
    faim = personne.faim / 100.0
    if faim >= SEUIL_PRIERE:
        besoins.append(("faim", min(1.0, faim)))
    if personne.energie < 30:
        besoins.append(("repos", min(1.0, (100 - personne.energie) / 100.0)))
    if personne.conjoint is None and personne.est_adulte() and est_seul_adulte:
        besoins.append(("compagnon", 0.7))
    return [(b, i) for b, i in besoins if i >= SEUIL_PRIERE]
