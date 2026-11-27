"""Pont entre l'application Android (Java) et le moteur Python.

Chaque fonction renvoie du texte JSON, ou du texte brut pour la sauvegarde.
Les fichiers moteur.py et le paquet terre/ sont copiés ici au moment de la compilation.
"""
import json
import os

import moteur
import terre


def nouvelle(graine, carte):
    return json.dumps(moteur.nouvelle_partie(graine, carte))


def avancer(tours):
    return json.dumps(moteur.avancer(tours))


def agir(outil, i, j, rayon):
    return json.dumps(moteur.agir(outil, i, j, rayon))


def decider(identifiant, choix):
    return json.dumps(moteur.decider(identifiant, choix))


def etat():
    return json.dumps(moteur.etat())


def sauver():
    return moteur.sauver()


def charger(texte):
    return json.dumps(moteur.charger_texte(texte))


def terre_texte():
    chemin = os.path.join(os.path.dirname(terre.__file__), "donnees", "terre_0_5deg.txt")
    with open(chemin, encoding="utf-8") as f:
        return f.read()
