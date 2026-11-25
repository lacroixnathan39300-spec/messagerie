"""Figures : quelques personnages clés qui décident et parlent à partir de leur mémoire.

Entièrement hors connexion : pas de modèle de langage. Les décisions viennent de la mémoire
pondérée du personnage, et les phrases viennent de modèles simples remplis par ses souvenirs.
"""
from dataclasses import dataclass, field

MOTS_REFUS = ("refusée", "sans réponse")
MOTS_BIENFAIT = ("exaucée", "a trouvé à manger", "partiellement exaucée", "a appris")
PRUDENCE_DEFAUT = 0.5


@dataclass
class Figure:
    personne: object  # une Personne de la simulation
    nom: str
    traits: dict = field(default_factory=dict)  # ex. {"prudence": 0.8}

    def peser(self, mots):
        """Somme des poids des souvenirs qui contiennent un de ces mots."""
        return sum(poids for texte, poids in self.personne.souvenirs
                   if any(mot in texte for mot in mots))

    def souvenir_dominant(self):
        if not self.personne.souvenirs:
            return None
        return max(self.personne.souvenirs, key=lambda s: (s[1], s[0]))[0]

    def choix_priere(self):
        """Décide à partir de ce qu'elle a vécu : les refus pèsent, la prudence freine le refus."""
        refus = self.peser(MOTS_REFUS)
        bienfait = self.peser(MOTS_BIENFAIT)
        prudence = self.traits.get("prudence", PRUDENCE_DEFAUT)
        if refus > bienfait + prudence:
            return "refuser"
        if bienfait > refus:
            return "exaucer"
        return "modifier"

    def dire(self):
        souvenir = self.souvenir_dominant()
        if souvenir is None:
            return f"{self.nom} n'a rien à dire."
        return f"{self.nom} : « Je me souviens : {souvenir}. »"


def politique(figures):
    """Politique de réponse du joueur simulé : les figures décident, les autres ignorent."""
    par_auteur = {f.personne.identifiant: f for f in figures}

    def repondre(priere, sim):
        figure = par_auteur.get(priere.auteur)
        return figure.choix_priere() if figure else "ignorer"

    return repondre
