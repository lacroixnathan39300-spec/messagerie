"""Boucle de simulation : à chaque tour, les personnes vieillissent, se rencontrent, décident et agissent."""
import random

from .personne import SEUIL_FAIM, Personne

RAYON_VISION = 6
PERTE_FAIM = 0.5
PERTE_ENERGIE = 0.3
REPOS = 1.0
REPAS = 30.0
ENERGIE_MAX = 100.0
PROBA_NAISSANCE = 0.002
PROBA_DEFORMATION = 0.2
FACTEUR_FIABILITE = 0.9
FIABILITE_MIN = 0.2
MAX_RUMEURS = 20
DIRECTIONS = ((1, 0), (-1, 0), (0, 1), (0, -1))


class Simulation:
    def __init__(self, monde, personnes, graine=0):
        self.monde = monde
        self.personnes = list(personnes)
        self.tour = 0
        self.rng = random.Random(graine)
        self.morts = []  # liste de (tour, identifiant, cause)
        self.naissances = 0
        self.relations = {}  # (plus petit id, plus grand id) -> {"confiance", "affection"}
        self._prochain_id = max((p.identifiant for p in self.personnes), default=-1) + 1

    def vivantes(self):
        return [p for p in self.personnes if p.vivante]

    def tour_suivant(self):
        self.tour += 1
        for p in self.vivantes():
            p.age += 1
            p.faim += PERTE_FAIM
            p.oublier()
        for p in self.vivantes():
            self._agir(p)
        self._rencontres()
        for p in self.vivantes():
            if p.meurt_de_faim():
                self._mourir(p, "faim")
            elif p.meurt_de_vieillesse():
                self._mourir(p, "vieillesse")
        self._naissances()
        self.monde.repousser()

    def bilan(self):
        vivantes = self.vivantes()
        faim_moyenne = sum(p.faim for p in vivantes) / len(vivantes) if vivantes else 0.0
        couples = sum(1 for p in vivantes if p.conjoint is not None) // 2
        return {
            "tour": self.tour,
            "vivantes": len(vivantes),
            "morts": len(self.morts),
            "naissances": self.naissances,
            "couples": couples,
            "faim_moyenne": round(faim_moyenne, 1),
        }

    # Actions individuelles

    def _agir(self, p):
        if p.a_faim():
            self._chercher_nourriture(p)
        elif p.est_fatigue():
            p.energie = min(ENERGIE_MAX, p.energie + REPOS)
        else:
            self._errer(p)
            p.energie = max(0.0, p.energie - PERTE_ENERGIE)

    def _chercher_nourriture(self, p):
        cible = self._buisson_proche(p)
        if cible is None:
            cible = self._buisson_connu(p)
        if cible is None:
            self._errer(p)
        elif cible == p.case:
            if self.monde.cueillir(cible):
                p.faim = max(0.0, p.faim - REPAS)
                self._retenir_buisson(p, cible)
                p.ajouter_souvenir(f"a trouvé à manger en {cible}", 1.0)
            else:
                self._oublier_buisson(p, cible)
        else:
            self._pas_vers(p, cible)
        p.energie = max(0.0, p.energie - PERTE_ENERGIE)

    def _buisson_proche(self, p):
        candidats = [
            case for case, quantite in self.monde.nourriture.items()
            if quantite >= 1 and self._distance(p.case, case) <= RAYON_VISION
        ]
        if not candidats:
            return None
        return min(candidats, key=lambda case: (self._distance(p.case, case), case))

    def _buisson_connu(self, p):
        """Va vers le buisson le plus proche dont on a entendu parler."""
        candidats = [
            r["valeur"] for r in p.rumeurs
            if r["sujet"] == "buisson" and r["fiabilite"] >= FIABILITE_MIN
        ]
        if not candidats:
            return None
        return min(candidats, key=lambda case: (self._distance(p.case, case), case))

    def _retenir_buisson(self, p, case):
        if any(r["sujet"] == "buisson" and r["valeur"] == case for r in p.rumeurs):
            return
        p.rumeurs.append({"sujet": "buisson", "valeur": case, "fiabilite": 1.0})
        if len(p.rumeurs) > MAX_RUMEURS:
            moins_fiable = min(range(len(p.rumeurs)), key=lambda i: p.rumeurs[i]["fiabilite"])
            del p.rumeurs[moins_fiable]

    def _oublier_buisson(self, p, case):
        """Le buisson annoncé était vide : on ne compte plus dessus."""
        p.rumeurs = [r for r in p.rumeurs if not (r["sujet"] == "buisson" and r["valeur"] == case)]

    def _pas_vers(self, p, cible):
        meilleurs = [
            voisin for voisin in self.monde.voisins_terre(p.x, p.y)
            if self._distance(voisin, cible) < self._distance(p.case, cible)
        ]
        if meilleurs:
            p.x, p.y = min(meilleurs)
        else:
            self._errer(p)

    def _errer(self, p):
        voisins = sorted(self.monde.voisins_terre(p.x, p.y))
        if voisins:
            p.x, p.y = self.rng.choice(voisins)

    # Relations entre personnes

    def _rencontres(self):
        vivantes = self.vivantes()
        par_case = {}
        for p in vivantes:
            par_case.setdefault(p.case, []).append(p)
        for p in vivantes:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for q in par_case.get((p.x + dx, p.y + dy), []):
                        if q.identifiant > p.identifiant:
                            self._echanger(p, q)

    def _echanger(self, a, b):
        cle = (min(a.identifiant, b.identifiant), max(a.identifiant, b.identifiant))
        relation = self.relations.setdefault(cle, {"confiance": 0.0, "affection": 0.0})
        relation["affection"] = min(1.0, relation["affection"] + 0.01)
        relation["confiance"] = min(1.0, relation["confiance"] + 0.005)
        self._transmettre(a, b)
        self._transmettre(b, a)
        self._proposer_mariage(a, b, relation)

    def _transmettre(self, source, cible):
        for rumeur in list(source.rumeurs):
            if any(r["sujet"] == rumeur["sujet"] and r["valeur"] == rumeur["valeur"] for r in cible.rumeurs):
                continue
            valeur = rumeur["valeur"]
            if self.rng.random() < PROBA_DEFORMATION:
                dx, dy = self.rng.choice(DIRECTIONS)
                valeur = (valeur[0] + dx, valeur[1] + dy)
            nouvelle = {
                "sujet": rumeur["sujet"],
                "valeur": valeur,
                "fiabilite": round(rumeur["fiabilite"] * FACTEUR_FIABILITE, 3),
            }
            if nouvelle["fiabilite"] >= FIABILITE_MIN and len(cible.rumeurs) < MAX_RUMEURS:
                cible.rumeurs.append(nouvelle)

    def _proposer_mariage(self, a, b, relation):
        if (a.conjoint is None and b.conjoint is None
                and a.est_adulte() and b.est_adulte()
                and relation["affection"] >= 0.8 and relation["confiance"] >= 0.5):
            a.conjoint = b.identifiant
            b.conjoint = a.identifiant
            a.ajouter_souvenir(f"s'est marié avec {b.identifiant}", 1.0)
            b.ajouter_souvenir(f"s'est marié avec {a.identifiant}", 1.0)

    def _naissances(self):
        index = {p.identifiant: p for p in self.vivantes()}
        deja_vus = set()
        for p in list(index.values()):
            if p.conjoint is None or p.identifiant in deja_vus or p.conjoint not in index:
                continue
            q = index[p.conjoint]
            deja_vus.update({p.identifiant, q.identifiant})
            if (p.case == q.case and p.faim < SEUIL_FAIM and q.faim < SEUIL_FAIM
                    and self.rng.random() < PROBA_NAISSANCE):
                enfant = Personne(self._prochain_id, p.x, p.y)
                self._prochain_id += 1
                self.personnes.append(enfant)
                p.enfants.append(enfant.identifiant)
                q.enfants.append(enfant.identifiant)
                self.naissances += 1
                p.ajouter_souvenir(f"est né(e) un enfant : {enfant.identifiant}", 1.0)
                q.ajouter_souvenir(f"est né(e) un enfant : {enfant.identifiant}", 1.0)

    def _mourir(self, p, cause):
        p.vivante = False
        self.morts.append((self.tour, p.identifiant, cause))

    @staticmethod
    def _distance(a, b):
        return abs(a[0] - b[0]) + abs(a[1] - b[1])
