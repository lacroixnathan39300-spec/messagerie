// Relais de la messagerie : annuaire par pseudo et boîtes de messages chiffrés.
// Le serveur ne reçoit jamais de numéro de téléphone, et ne peut pas lire les messages.

export interface Env {
  ANNUAIRE: KVNamespace;
  BOITES: KVNamespace;
}

const PSEUDO = /^[a-z0-9_.-]{3,20}$/;
const BOITE = /^[A-Za-z0-9_-]{22,64}$/;
const CLE = /^[A-Za-z0-9+/=]{60,200}$/;
const TAILLE_MAX = 20000;
const DUREE_MESSAGE = 7 * 24 * 3600;

function json(corps: unknown, statut = 200): Response {
  return new Response(JSON.stringify(corps), {
    status: statut,
    headers: {
      "content-type": "application/json",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "content-type",
      "access-control-allow-methods": "GET,POST,OPTIONS",
    },
  });
}

async function sha256(texte: string): Promise<string> {
  const octets = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texte));
  return Array.from(new Uint8Array(octets), (b) => b.toString(16).padStart(2, "0")).join("");
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    if (req.method === "OPTIONS") return json({}, 204);

    // Inscription : le pseudo est lié à une clé publique et à une boîte. Le jeton de boîte
    // n'est jamais publié : seule son empreinte est gardée, pour que personne d'autre ne lise la boîte.
    if (url.pathname === "/v1/inscrire" && req.method === "POST") {
      const corps = (await req.json()) as { pseudo?: string; cle?: string; boite?: string; jeton?: string };
      const pseudo = (corps.pseudo ?? "").toLowerCase();
      if (!PSEUDO.test(pseudo) || !CLE.test(corps.cle ?? "") || !BOITE.test(corps.boite ?? "") || !corps.jeton) {
        return json({ erreur: "invalide" }, 400);
      }
      const existant = await env.ANNUAIRE.get(`p:${pseudo}`);
      if (existant) {
        const deja = JSON.parse(existant) as { boite: string };
        if (deja.boite !== corps.boite) return json({ erreur: "pseudo_pris" }, 409);
      }
      const empreinte = await sha256(corps.jeton);
      await env.ANNUAIRE.put(`p:${pseudo}`, JSON.stringify({ cle: corps.cle, boite: corps.boite }));
      await env.ANNUAIRE.put(`b:${corps.boite}`, empreinte);
      return json({ ok: true });
    }

    // Recherche par pseudo : renvoie la clé publique et la boîte, rien de plus.
    if (url.pathname === "/v1/chercher" && req.method === "GET") {
      const pseudo = (url.searchParams.get("pseudo") ?? "").toLowerCase();
      if (!PSEUDO.test(pseudo)) return json({ erreur: "invalide" }, 400);
      const trouve = await env.ANNUAIRE.get(`p:${pseudo}`);
      return trouve ? json({ pseudo, ...JSON.parse(trouve) }) : json({ erreur: "introuvable" }, 404);
    }

    // Envoi : on dépose un paquet chiffré dans la boîte du destinataire.
    if (url.pathname === "/v1/envoyer" && req.method === "POST") {
      const corps = (await req.json()) as { boite?: string; charge?: string };
      if (!BOITE.test(corps.boite ?? "") || typeof corps.charge !== "string" || corps.charge.length > TAILLE_MAX) {
        return json({ erreur: "invalide" }, 400);
      }
      if (!(await env.ANNUAIRE.get(`b:${corps.boite}`))) return json({ erreur: "introuvable" }, 404);
      const id = `${Date.now().toString().padStart(15, "0")}-${crypto.randomUUID()}`;
      await env.BOITES.put(`m:${corps.boite}:${id}`, corps.charge, { expirationTtl: DUREE_MESSAGE });
      return json({ ok: true });
    }

    // Réception : seul le détenteur du jeton lit la boîte, et les messages sont effacés après lecture.
    if (url.pathname === "/v1/recevoir" && req.method === "GET") {
      const boite = url.searchParams.get("boite") ?? "";
      const jeton = url.searchParams.get("jeton") ?? "";
      if (!BOITE.test(boite)) return json({ erreur: "invalide" }, 400);
      const attendu = await env.ANNUAIRE.get(`b:${boite}`);
      if (!attendu || attendu !== (await sha256(jeton))) return json({ erreur: "refuse" }, 403);
      const liste = await env.BOITES.list({ prefix: `m:${boite}:`, limit: 50 });
      const messages: string[] = [];
      for (const cle of liste.keys) {
        const valeur = await env.BOITES.get(cle.name);
        if (valeur !== null) messages.push(valeur);
        await env.BOITES.delete(cle.name);
      }
      return json({ messages });
    }

    return json({ service: "messagerie", version: 1 });
  },
};
