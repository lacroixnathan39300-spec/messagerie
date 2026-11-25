"""Sauvegarde locale de la partie dans un fichier JSON.

Hors connexion, sans serveur : le fichier reste sur l'appareil. L'écriture passe par un fichier
temporaire, pour qu'une coupure (batterie, fermeture) ne corrompe pas la sauvegarde.
Sur Android, il suffit de donner le chemin du dossier privé de l'appli (voir README).
"""
import json
import os

from .culture import Culture
from .monde import Monde
from .monde_infini import MondeInfini
from .personne import Personne
from .priere import Priere
from .simulation import Simulation

VERSION = 1


def _cle(texte):
    """'3,4' -> (3, 4)"""
    return tuple(int(v) for v in texte.split(","))


def _vers_cle(case):
    return f"{case[0]},{case[1]}"


def _personne_vers_dict(p):
    return {
        "identifiant": p.identifiant, "x": p.x, "y": p.y, "faim": p.faim, "energie": p.energie,
        "age": p.age, "vivante": p.vivante, "conjoint": p.conjoint, "enfants": list(p.enfants),
        "souvenirs": [list(s) for s in p.souvenirs],
        "rumeurs": [{"sujet": r["sujet"], "valeur": list(r["valeur"]), "fiabilite": r["fiabilite"]}
                    for r in p.rumeurs],
        "foi": p.foi, "colere": p.colere, "schismatique": p.schismatique,
        "derniere_priere": p.derniere_priere, "savoirs": sorted(p.savoirs), "langue": p.langue, "soif": p.soif,
    }


def _personne_depuis_dict(d):
    p = Personne(d["identifiant"], d["x"], d["y"], faim=d["faim"], energie=d["energie"],
                 age=d["age"], vivante=d["vivante"], conjoint=d["conjoint"],
                 enfants=list(d["enfants"]), foi=d["foi"], colere=d["colere"],
                 schismatique=d["schismatique"], derniere_priere=d["derniere_priere"],
                 savoirs=set(d["savoirs"]), langue=d["langue"],
                 soif=d.get("soif", 0.0))
    p.souvenirs = [[texte, poids] for texte, poids in d["souvenirs"]]
    p.rumeurs = [{"sujet": r["sujet"], "valeur": tuple(r["valeur"]), "fiabilite": r["fiabilite"]}
                 for r in d["rumeurs"]]
    return p


def sauvegarder(sim, chemin):
    """Écrit l'état complet de la simulation dans `chemin`."""
    donnees = {
        "version": VERSION,
        "monde": _monde_vers_dict(sim.monde),
        "personnes": [_personne_vers_dict(p) for p in sim.personnes],
        "simulation": {
            "tour": sim.tour,
            "rng": _etat_rng_vers_json(sim.rng.getstate()),
            "morts": [list(m) for m in sim.morts],
            "naissances": sim.naissances,
            "relations": {f"{a},{b}": r for (a, b), r in sim.relations.items()},
            "schismes": [[t, ids] for t, ids in sim.schismes],
            "prochain_id": sim._prochain_id,
            "prochaine_priere": sim._prochaine_priere,
            "guerres": sim.guerres,
            "accidents": [list(a) for a in sim.accidents],
            "langues": sim.langues,
            "prochaine_langue": sim._prochaine_langue,
            "dictionnaire": {k: sorted(v) for k, v in sim.dictionnaire.items()},
            "prieres": [{"identifiant": pr.identifiant, "auteur": pr.auteur, "besoin": pr.besoin,
                         "intensite": pr.intensite, "tour": pr.tour, "statut": pr.statut}
                        for pr in sim.prieres],
        },
        "culture": {
            "connues": sorted(sim.culture.connues),
            "historique": [list(h) for h in sim.culture.historique],
            "epoques": [list(e) for e in sim.culture.epoques],
            "epoque": sim.culture._epoque,
        },
    }
    dossier = os.path.dirname(os.path.abspath(chemin))
    os.makedirs(dossier, exist_ok=True)
    temporaire = chemin + ".tmp"
    with open(temporaire, "w", encoding="utf-8") as f:
        json.dump(donnees, f, ensure_ascii=False)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporaire, chemin)


def charger(chemin, joueur=None):
    """Recrée la simulation telle qu'elle était. `joueur` est la fonction de réponse du joueur."""
    with open(chemin, encoding="utf-8") as f:
        d = json.load(f)
    if d.get("version") != VERSION:
        raise ValueError(f"version de sauvegarde non prise en charge : {d.get('version')}")

    monde = _monde_depuis_dict(d["monde"])

    personnes = [_personne_depuis_dict(p) for p in d["personnes"]]
    sim = Simulation(monde, personnes, graine=0, joueur=joueur)

    s = d["simulation"]
    sim.tour = s["tour"]
    sim.rng.setstate(_etat_rng_depuis_json(s["rng"]))
    sim.morts = [tuple(m) for m in s["morts"]]
    sim.naissances = s["naissances"]
    sim.relations = {tuple(int(v) for v in k.split(",")): r for k, r in s["relations"].items()}
    sim.schismes = [(t, ids) for t, ids in s["schismes"]]
    sim._prochain_id = s["prochain_id"]
    sim._prochaine_priere = s["prochaine_priere"]
    sim.guerres = s["guerres"]
    sim.accidents = [tuple(a) for a in s.get("accidents", [])]
    sim.langues = s["langues"]
    sim._prochaine_langue = s["prochaine_langue"]
    sim.dictionnaire = {k: set(v) for k, v in s["dictionnaire"].items()}
    sim.prieres = [Priere(pr["identifiant"], pr["auteur"], pr["besoin"], pr["intensite"],
                          pr["tour"], pr["statut"]) for pr in s["prieres"]]

    c = d["culture"]
    culture = Culture()
    culture.connues = set(c["connues"])
    culture.historique = [tuple(h) for h in c["historique"]]
    culture.epoques = [tuple(e) for e in c["epoques"]]
    culture._epoque = c["epoque"]
    sim.culture = culture
    return sim


def _monde_vers_dict(monde):
    nourriture = {_vers_cle(c): q for c, q in monde.nourriture.items()}
    if isinstance(monde, MondeInfini):
        # Un monde infini se régénère à partir de sa graine : seuls les buissons modifiés sont stockés.
        return {"type": "infini", "graine": monde.graine, "forme": monde.forme,
                "seuil": monde.seuil, "limite": list(monde.limite) if monde.limite else None,
                "nourriture": nourriture,
                "morceaux": [list(c) for c in sorted(monde._chunks_charges)]}
    return {"type": "fini", "largeur": monde.largeur, "hauteur": monde.hauteur,
            "terre": [list(c) for c in monde.cases_terre()], "nourriture": nourriture,
            "altitude": [[x, y, a] for (x, y), a in getattr(monde, "altitude", {}).items()],
            "population": [[x, y, c] for (x, y), c in getattr(monde, "population", {}).items()],
            "eau": [list(c) for c in monde.points_eau] if monde.points_eau is not None else None,
            "grille": getattr(monde, "grille", None)}


def _monde_depuis_dict(m):
    if m.get("type") == "infini":
        monde = MondeInfini(m["graine"], m["forme"], m["seuil"],
                            tuple(m["limite"]) if m["limite"] else None)
        monde.nourriture = {_cle(c): q for c, q in m["nourriture"].items()}
        monde._chunks_charges = {tuple(c) for c in m["morceaux"]}
        return monde
    monde = Monde(m["largeur"], m["hauteur"], [tuple(c) for c in m["terre"]])
    monde.nourriture = {_cle(c): q for c, q in m["nourriture"].items()}
    monde.altitude = {(x, y): a for x, y, a in m.get("altitude", [])}
    monde.population = {(x, y): c for x, y, c in m.get("population", [])}
    monde.points_eau = {tuple(c) for c in m["eau"]} if m.get("eau") is not None else None
    if m.get("grille"):
        monde.grille = m["grille"]
    return monde


def _etat_rng_vers_json(etat):
    version, interne, gauss = etat
    return [version, list(interne), gauss]


def _etat_rng_depuis_json(etat):
    version, interne, gauss = etat
    return (version, tuple(interne), gauss)
