"""Culture : des techniques apparaissent quand le groupe est assez grand, se transmettent,
et se perdent quand personne ne les garde. Les époques suivent les techniques acquises."""
from dataclasses import dataclass

PROBA_INVENTION = 0.02  # chance par tour qu'un groupe éligible invente une technique
PROBA_OUBLI = 0.01  # chance par tour qu'un petit groupe oublie une technique qu'il ne pratique plus
PROBA_TRANSMISSION = 0.3  # chance qu'une technique passe lors d'une rencontre


@dataclass(frozen=True)
class Technique:
    nom: str
    taille_min: int  # taille de groupe nécessaire pour la pratiquer et l'inventer
    prerequis: tuple = ()


TECHNIQUES = {
    t.nom: t for t in (
        Technique("outil_pierre", 1),
        Technique("feu", 2),
        Technique("arc", 8, ("outil_pierre",)),
        Technique("poterie", 15, ("feu",)),
        Technique("agriculture", 25, ("feu", "outil_pierre")),
        Technique("ecriture", 60, ("agriculture",)),
        Technique("imprimerie", 120, ("ecriture",)),
    )
}

# Époques : chacune est atteinte quand toutes ses techniques sont connues.
EPOQUES = (
    ("Paléolithique", ()),
    ("Néolithique", ("agriculture",)),
    ("Antiquité", ("ecriture",)),
    ("Époque moderne", ("imprimerie",)),
)


class Culture:
    def __init__(self):
        self.connues = set()
        self.historique = []  # (tour, "inventée" | "perdue", nom)
        self.epoques = []  # (tour, nom de l'époque) à chaque changement
        self._epoque = EPOQUES[0][0]

    def epoque(self):
        actuelle = EPOQUES[0][0]
        for nom, requis in EPOQUES:
            if all(r in self.connues for r in requis):
                actuelle = nom
        return actuelle

    def mettre_a_jour(self, tour, personnes, groupes, rng, proba_invention=PROBA_INVENTION,
                      proba_oubli=PROBA_OUBLI):
        """`groupes` : liste de listes de personnes vivantes, proches les unes des autres."""
        for groupe in groupes:
            for nom, technique in TECHNIQUES.items():
                if len(groupe) < technique.taille_min:
                    # Un petit groupe oublie les techniques qu'il ne pratique plus.
                    for p in groupe:
                        if nom in p.savoirs and rng.random() < proba_oubli:
                            p.savoirs.discard(nom)
                    continue
                if nom in self.connues or not all(r in self.connues for r in technique.prerequis):
                    continue
                if rng.random() < proba_invention:
                    inventeur = rng.choice(groupe)
                    inventeur.savoirs.add(nom)
                    self.connues.add(nom)
                    self.historique.append((tour, "inventée", nom))
        for nom in sorted(self.connues):
            if not any(nom in p.savoirs for p in personnes if p.vivante):
                self.connues.discard(nom)
                self.historique.append((tour, "perdue", nom))
        epoque = self.epoque()
        if epoque != self._epoque:
            self._epoque = epoque
            self.epoques.append((tour, epoque))
