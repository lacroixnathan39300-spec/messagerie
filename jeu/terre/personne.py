"""Une personne : faim, énergie et âge."""
from dataclasses import dataclass

FAIM_MORTELLE = 100.0
AGE_MAX = 2000  # en tours
SEUIL_FAIM = 60.0
SEUIL_FATIGUE = 30.0


@dataclass
class Personne:
    identifiant: int
    x: int
    y: int
    faim: float = 20.0
    energie: float = 80.0
    age: int = 0
    vivante: bool = True

    @property
    def case(self):
        return (self.x, self.y)

    def a_faim(self):
        return self.faim >= SEUIL_FAIM

    def est_fatigue(self):
        return self.energie < SEUIL_FATIGUE

    def meurt_de_faim(self):
        return self.faim >= FAIM_MORTELLE

    def meurt_de_vieillesse(self):
        return self.age >= AGE_MAX
