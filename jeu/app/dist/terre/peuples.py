"""Peuples : groupes de personnes assez nombreux. Deux peuples en contact sont en échange ou en guerre."""
from dataclasses import dataclass, field

TAILLE_PEUPLE = 5  # nombre minimal de personnes pour former un peuple
SEUIL_GUERRE = 1.2  # somme des colères moyennes de deux peuples au-delà de laquelle ils se battent


@dataclass
class Peuple:
    identifiant: int  # plus petit identifiant de ses membres (à l'instant du calcul)
    membres: list = field(default_factory=list)

    def colere_moyenne(self):
        return sum(p.colere for p in self.membres) / len(self.membres) if self.membres else 0.0


def peuples(groupes, taille_min=TAILLE_PEUPLE):
    return [Peuple(min(p.identifiant for p in g), list(g)) for g in groupes if len(g) >= taille_min]


def relation(a, b):
    """'guerre' si la colère est trop forte des deux côtés, sinon 'échange'."""
    return "guerre" if a.colere_moyenne() + b.colere_moyenne() > SEUIL_GUERRE else "echange"


def en_contact(a, b, distance, rayon):
    return any(distance(p.case, q.case) <= rayon for p in a.membres for q in b.membres)
