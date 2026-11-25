"""Boucle de simulation : à chaque tour, les personnes vieillissent, se rencontrent, décident et agissent."""
import random

from .personne import AGE_MAX, SEUIL_FAIM, SEUIL_SOIF, Personne
from .culture import PROBA_TRANSMISSION, Culture
from collections import Counter

from .langue import CONCEPTS, PROBA_DERIVE, PROBA_EMPRUNT, creer as creer_langue, deriver, emprunter

PROBA_APPRENTISSAGE = 0.01  # chance par tour qu'un peuple observé fasse apprendre un concept au joueur
from .peuples import en_contact, peuples, relation
from .priere import (DUREE_PRIERE, EXAUCER, IGNORER, MODIFIER, REFUSER,
                     Priere, besoins_pressants)

USURE_FAIM_SOIF = 0.5  # jours de vie perdus par tour de faim ou de soif
USURE_ACCIDENT = 180  # jours de vie perdus lors d'un accident nucléaire
PERTE_SOIF = 0.8  # soif gagnée par tour (si la carte a des points d'eau)
EAU_BU = 50.0  # soif calmée par une gorgée complète
PORTEE_EAU = 80  # nombre de pas maximum pour chercher de l'eau
PERTE_GUERRE = 0.8  # énergie perdue par tour pendant une guerre entre peuples
PROBA_PRIERE = 0.05  # chance par tour de formuler une prière, multipliée par la foi
COOLDOWN_PRIERE = 40  # tours minimum entre deux prières d'une même personne
SEUIL_SCHISME = 3  # nombre de personnes en colère, proches, qui se séparent
COLERE_SCHISME = 0.6
FOI_MIN_PRIERE = 0.1
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
    def __init__(self, monde, personnes, graine=0, joueur=None):
        """`joueur` : fonction (priere, simulation) -> choix, appelée pour chaque prière.
        Sans fonction, les prières attendent une décision via `decider`."""
        self.monde = monde
        self.personnes = list(personnes)
        self.tour = 0
        self.graine = graine
        self.rng = random.Random(graine)
        self.morts = []  # liste de (tour, identifiant, cause)
        self.naissances = 0
        self.relations = {}  # (plus petit id, plus grand id) -> {"confiance", "affection"}
        self.joueur = joueur
        self.prieres = []  # toutes les prières formulées
        self.schismes = []  # liste de (tour, [identifiants])
        self._prochain_id = max((p.identifiant for p in self.personnes), default=-1) + 1
        self._prochaine_priere = 0
        self.culture = Culture()
        self.guerres = 0  # nombre de tours où deux peuples en contact étaient en guerre
        self.accidents = []  # (tour, identifiant du peuple) pour les accidents nucléaires
        self.langues = {}  # identifiant -> langue (voir terre/langue.py)
        self.dictionnaire = {}  # identifiant de langue -> concepts que le joueur a appris
        self._prochaine_langue = 0

    def vivantes(self):
        return [p for p in self.personnes if p.vivante]

    def tour_suivant(self):
        self.tour += 1
        for p in self.vivantes():
            if p.longevite is None:
                p.longevite = self._longevite_pour(p)
            p.age += 1
            if p.faim >= SEUIL_FAIM:
                p.longevite -= USURE_FAIM_SOIF
            if self._eau_active() and p.soif >= SEUIL_SOIF:
                p.longevite -= USURE_FAIM_SOIF
            p.faim += PERTE_FAIM
            if self._eau_active():
                p.soif += PERTE_SOIF
            p.oublier()
        for p in self.vivantes():
            self._agir(p)
        self._rencontres()
        self._prier()
        self._traiter_prieres()
        self._verifier_schismes()
        self._conflits()
        self._attribuer_langues()
        from .economie import verifier_accidents
        verifier_accidents(self)
        self.culture.mettre_a_jour(self.tour, self.personnes, self._groupes(), self.rng)
        for p in self.vivantes():
            if p.meurt_de_faim():
                self._mourir(p, "faim")
            elif self._eau_active() and p.meurt_de_soif():
                self._mourir(p, "soif")
            elif p.meurt_de_vieillesse():
                self._mourir(p, "vieillesse")
        self._naissances()
        self.monde.repousser()

    def _longevite_pour(self, p):
        """Durée de vie selon les savoirs de l'humanité : médecine, électricité, époque."""
        from .culture import EPOQUES
        facteur = 1.0
        if "medecine" in self.culture.connues:
            facteur += 0.10
        if "electricite" in self.culture.connues:
            facteur += 0.05
        epoque = self.culture.epoque()
        rang = [nom for nom, _ in EPOQUES].index(epoque)
        facteur += 0.03 * rang
        return AGE_MAX * min(1.6, max(0.5, facteur))

    def bilan(self):
        vivantes = self.vivantes()
        faim_moyenne = sum(p.faim for p in vivantes) / len(vivantes) if vivantes else 0.0
        foi_moyenne = sum(p.foi for p in vivantes) / len(vivantes) if vivantes else 0.0
        couples = sum(1 for p in vivantes if p.conjoint is not None) // 2
        return {
            "tour": self.tour,
            "vivantes": len(vivantes),
            "morts": len(self.morts),
            "naissances": self.naissances,
            "couples": couples,
            "faim_moyenne": round(faim_moyenne, 1),
            "foi_moyenne": round(foi_moyenne, 2),
            "prieres_en_attente": len(self.prieres_en_attente()),
            "schismes": len(self.schismes),
            "epoque": self.culture.epoque(),
            "techniques": sorted(self.culture.connues),
            "peuples": len(peuples(self._groupes())),
            "guerres": self.guerres,
            "langues": len(self.langues),
        }

    # Peuples

    def _conflits(self):
        """Deux peuples en contact en guerre s'épuisent et s'irritent mutuellement.
        Deux peuples en contact en paix peuvent emprunter des mots l'un à l'autre."""
        liste = peuples(self._groupes())
        for i, a in enumerate(liste):
            for b in liste[i + 1:]:
                # Deux peuples distincts sont séparés de plus de RAYON_VISION : le contact porte plus loin.
                if not en_contact(a, b, self._distance, 2 * RAYON_VISION):
                    continue
                if relation(a, b) == "guerre":
                    self.guerres += 1
                    for p in a.membres + b.membres:
                        p.energie = max(0.0, p.energie - PERTE_GUERRE)
                        p.colere = min(1.0, p.colere + 0.005)
                else:
                    self._emprunts(a, b)

    def _emprunts(self, a, b):
        langue_a = self.langues.get(a.membres[0].langue)
        langue_b = self.langues.get(b.membres[0].langue)
        if langue_a and langue_b and langue_a is not langue_b and self.rng.random() < PROBA_EMPRUNT:
            emprunter(langue_a, langue_b, self.rng)

    def _attribuer_langues(self):
        """Chaque peuple garde la langue de la majorité de ses membres, même si le groupe change.
        Les langues dérivent lentement. Le joueur entend parfois un nouveau mot."""
        for peuple in peuples(self._groupes()):
            existantes = Counter(p.langue for p in peuple.membres if p.langue)
            if existantes:
                identifiant = existantes.most_common(1)[0][0]
            else:
                identifiant = f"{self.graine}-{self._prochaine_langue}"
                self._prochaine_langue += 1
            if identifiant not in self.langues:
                self.langues[identifiant] = creer_langue(self.graine, identifiant)
            for p in peuple.membres:
                p.langue = identifiant
            if self.rng.random() < PROBA_APPRENTISSAGE:
                concept = self.rng.choice(CONCEPTS)
                self.dictionnaire.setdefault(identifiant, set()).add(concept)
        for langue in self.langues.values():
            if self.rng.random() < PROBA_DERIVE:
                deriver(langue, self.rng)

    # Prières

    def prieres_en_attente(self):
        return [pr for pr in self.prieres if pr.en_attente()]

    def decider(self, identifiant_priere, choix):
        """Le joueur répond à une prière. Renvoie False si elle n'est plus en attente."""
        if choix not in (EXAUCER, REFUSER, MODIFIER, IGNORER):
            raise ValueError(f"choix inconnu : {choix}")
        priere = next((pr for pr in self.prieres if pr.identifiant == identifiant_priere), None)
        if priere is None or not priere.en_attente():
            return False
        self._appliquer(priere, choix)
        return True

    def _prier(self):
        vivantes = self.vivantes()
        for p in vivantes:
            if p.foi < FOI_MIN_PRIERE or self.tour - p.derniere_priere < COOLDOWN_PRIERE:
                continue
            seul = any(q is not p and q.conjoint is None and q.est_adulte() for q in vivantes)
            besoins = besoins_pressants(p, est_seul_adulte=seul)
            if not besoins or self.rng.random() >= PROBA_PRIERE * p.foi:
                continue
            besoin, intensite = max(besoins, key=lambda bi: bi[1])
            if any(pr.en_attente() and pr.auteur == p.identifiant and pr.besoin == besoin
                   for pr in self.prieres):
                continue
            priere = Priere(self._prochaine_priere, p.identifiant, besoin, round(intensite, 2), self.tour)
            self._prochaine_priere += 1
            self.prieres.append(priere)
            p.derniere_priere = self.tour

    def _traiter_prieres(self):
        for priere in self.prieres_en_attente():
            if self.joueur is not None:
                self.decider(priere.identifiant, self.joueur(priere, self))
            elif self.tour - priere.tour >= DUREE_PRIERE:
                self.decider(priere.identifiant, IGNORER)

    def _appliquer(self, priere, choix):
        auteur = next((p for p in self.personnes if p.identifiant == priere.auteur), None)
        priere.statut = choix
        if auteur is None or not auteur.vivante:
            return
        if choix == EXAUCER:
            self._satisfaire(auteur, priere.besoin, 1.0)
            auteur.foi = min(1.0, auteur.foi + 0.1)
            auteur.ajouter_souvenir(f"sa prière a été exaucée ({priere.besoin})", 1.0)
        elif choix == MODIFIER:
            self._satisfaire(auteur, priere.besoin, 0.5)
            auteur.foi = min(1.0, auteur.foi + 0.03)
            auteur.ajouter_souvenir(f"sa prière a été partiellement exaucée ({priere.besoin})", 0.8)
        elif choix == REFUSER:
            auteur.foi = max(0.0, auteur.foi - 0.1)
            auteur.colere = min(1.0, auteur.colere + 0.3)
            auteur.ajouter_souvenir(f"sa prière a été refusée ({priere.besoin})", 1.0)
        elif choix == IGNORER:
            auteur.foi = max(0.0, auteur.foi - 0.02)
            auteur.ajouter_souvenir(f"sa prière est restée sans réponse ({priere.besoin})", 0.6)

    def _satisfaire(self, p, besoin, part):
        if besoin == "faim":
            p.faim = p.faim * (1.0 - part)
        elif besoin == "repos":
            p.energie = min(ENERGIE_MAX, p.energie + (ENERGIE_MAX - p.energie) * part)
        elif besoin == "compagnon":
            voisins = [q for q in self.vivantes() if q is not p and q.conjoint is None
                       and q.est_adulte() and self._distance(p.case, q.case) <= RAYON_VISION]
            if voisins:
                q = min(voisins, key=lambda q: (self._distance(p.case, q.case), q.identifiant))
                cle = (min(p.identifiant, q.identifiant), max(p.identifiant, q.identifiant))
                relation = self.relations.setdefault(cle, {"confiance": 0.0, "affection": 0.0})
                relation["affection"] = min(1.0, relation["affection"] + 0.4 * part)
                relation["confiance"] = min(1.0, relation["confiance"] + 0.4 * part)
                self._proposer_mariage(p, q, relation)

    def _verifier_schismes(self):
        """Des personnes en colère et proches se séparent du groupe."""
        colere = [p for p in self.vivantes() if p.colere >= COLERE_SCHISME and not p.schismatique]
        for p in colere:
            groupe = [q for q in colere if self._distance(p.case, q.case) <= RAYON_VISION]
            if len(groupe) >= SEUIL_SCHISME:
                for q in groupe:
                    q.schismatique = True
                    q.ajouter_souvenir("a rompu avec le groupe", 1.0)
                self.schismes.append((self.tour, [q.identifiant for q in groupe]))
                break
        for p in self.vivantes():
            p.colere = max(0.0, p.colere - 0.01)

    # Actions individuelles

    def _eau_active(self):
        return getattr(self.monde, "points_eau", None) is not None

    def _chercher_eau(self, p):
        """Va boire au point d'eau le plus proche, par un parcours en largeur sur la terre."""
        eau = self.monde.points_eau
        if p.case in eau:
            p.soif = max(0.0, p.soif - EAU_BU)
            p.ajouter_souvenir("a trouvé de l'eau", 1.0)
            return
        depart = p.case
        parent = {depart: None}
        file = [depart]
        cible = None
        for _ in range(PORTEE_EAU):
            if not file:
                break
            suivants = []
            for c in file:
                for v in self.monde.voisins_terre(*c):
                    if v in parent:
                        continue
                    parent[v] = c
                    if v in eau:
                        cible = v
                        break
                    suivants.append(v)
                if cible is not None:
                    break
            if cible is not None:
                break
            file = suivants
        if cible is None:
            self._errer(p)
            return
        premier = cible
        while parent[premier] != depart:
            premier = parent[premier]
        p.x, p.y = premier

    def _agir(self, p):
        if self._eau_active() and p.a_soif():
            self._chercher_eau(p)
            p.energie = max(0.0, p.energie - PERTE_ENERGIE)
            return
        if p.a_faim():
            self._chercher_nourriture(p)
        elif p.est_fatigue():
            p.energie = min(ENERGIE_MAX, p.energie + REPOS)
        else:
            conjoint = self._personne(p.conjoint) if p.conjoint is not None else None
            if conjoint is not None and conjoint.vivante and self._distance(p.case, conjoint.case) > 2:
                self._pas_vers(p, conjoint.case)  # un couple rejoint son foyer
            else:
                self._errer(p)
            p.energie = max(0.0, p.energie - PERTE_ENERGIE)

    def _personne(self, identifiant):
        return next((q for q in self.personnes if q.identifiant == identifiant), None)

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
        if not voisins:
            return
        altitude = getattr(self.monde, "altitude", None)
        if altitude:
            # Sur une carte avec relief, les montagnes attirent moins : on y marche moins souvent.
            poids = [1.0 / (1 + altitude.get(v, 0)) for v in voisins]
            p.x, p.y = self.rng.choices(voisins, weights=poids)[0]
        else:
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
        self._transmettre_savoirs(a, b)
        self._transmettre_savoirs(b, a)
        self._proposer_mariage(a, b, relation)

    def _transmettre_savoirs(self, source, cible):
        for nom in sorted(source.savoirs - cible.savoirs):
            if self.rng.random() < PROBA_TRANSMISSION:
                cible.savoirs.add(nom)
                cible.ajouter_souvenir(f"a appris {nom}", 1.0)

    def _groupes(self):
        """Ensembles de personnes vivantes reliées par une proximité (chaîne de voisins)."""
        restantes = {p.identifiant: p for p in self.vivantes()}
        groupes = []
        while restantes:
            _, depart = restantes.popitem()
            groupe, a_voir = [depart], [depart]
            while a_voir:
                courant = a_voir.pop()
                proches = [q for q in restantes.values()
                           if self._distance(courant.case, q.case) <= RAYON_VISION]
                for q in proches:
                    del restantes[q.identifiant]
                    groupe.append(q)
                    a_voir.append(q)
            groupes.append(groupe)
        return groupes

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
            # Un couple vit ensemble dès qu'il est à deux cases au plus.
            if (self._distance(p.case, q.case) <= 2 and p.faim < SEUIL_FAIM and q.faim < SEUIL_FAIM
                    and self.rng.random() < PROBA_NAISSANCE):
                enfant = Personne(self._prochain_id, p.x, p.y, langue=p.langue)
                enfant.longevite = self._longevite_pour(enfant)
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
