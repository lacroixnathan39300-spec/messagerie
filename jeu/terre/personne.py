"""Une personne : besoins, souvenirs, rumeurs et liens familiaux."""
from dataclasses import dataclass, field

FAIM_MORTELLE = 100.0
SOIF_MORTELLE = 100.0
SEUIL_SOIF = 50.0
AGE_MAX = 70 * 365  # en tours (1 tour = 1 jour) : environ 70 ans
AGE_ADULTE = 15 * 365  # en tours : 15 ans
SEUIL_FAIM = 60.0
SEUIL_FATIGUE = 30.0
MAX_SOUVENIRS = 30
FACTEUR_OUBLI = 0.995
POIDS_MINIMUM = 0.05


@dataclass
class Personne:
    identifiant: int
    x: int
    y: int
    faim: float = 20.0
    soif: float = 0.0
    energie: float = 80.0
    age: int = 0
    vivante: bool = True
    conjoint: int | None = None
    enfants: list = field(default_factory=list)
    souvenirs: list = field(default_factory=list)  # [texte, poids]
    rumeurs: list = field(default_factory=list)  # {"sujet", "valeur", "fiabilite"}
    foi: float = 0.5  # 0 = incroyant, 1 = très croyant
    colere: float = 0.0  # monte quand une prière est refusée
    schismatique: bool = False  # True une fois entrée dans un groupe qui s'est séparé
    derniere_priere: int = -1000  # tour de la dernière prière formulée
    savoirs: set = field(default_factory=set)  # techniques que cette personne connaît
    longevite: float = None  # durée de vie en tours, fixée selon les conditions de vie
    langue: str = ""  # identifiant de la langue parlée par son peuple

    @property
    def case(self):
        return (self.x, self.y)

    def a_faim(self):
        return self.faim >= SEUIL_FAIM

    def a_soif(self):
        return self.soif >= SEUIL_SOIF

    def meurt_de_soif(self):
        return self.soif >= SOIF_MORTELLE

    def est_fatigue(self):
        return self.energie < SEUIL_FATIGUE

    def est_adulte(self):
        return self.age >= AGE_ADULTE

    def meurt_de_faim(self):
        return self.faim >= FAIM_MORTELLE

    def meurt_de_vieillesse(self):
        duree = AGE_MAX if self.longevite is None else self.longevite
        return self.age >= duree

    def ajouter_souvenir(self, texte, poids=1.0):
        self.souvenirs.append([texte, poids])
        if len(self.souvenirs) > MAX_SOUVENIRS:
            plus_faible = min(range(len(self.souvenirs)), key=lambda i: self.souvenirs[i][1])
            del self.souvenirs[plus_faible]

    def oublier(self):
        """Chaque souvenir perd un peu de poids, et disparaît une fois presque effacé."""
        for souvenir in self.souvenirs:
            souvenir[1] *= FACTEUR_OUBLI
        self.souvenirs = [s for s in self.souvenirs if s[1] >= POIDS_MINIMUM]
