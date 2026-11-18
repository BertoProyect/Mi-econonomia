// Lee capturas (Bizum, Revolut, tickets…) con Gemini y devuelve movimientos listos para guardar.
// La clave de Gemini vive solo aquí, como secreto GEMINI_API_KEY. La web nunca la ve.
// Solo responde a usuarios con sesión iniciada en tu Supabase.

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const API = "https://generativelanguage.googleapis.com/v1beta";
let cachedModel: string | null = null;

async function pickModel(key: string): Promise<string> {
  if (cachedModel) return cachedModel;
  const fixed = Deno.env.get("GEMINI_MODEL");
  if (fixed) return (cachedModel = fixed);
  // Busca el Flash estable más nuevo que lea imágenes; si falla, usa el alias de Google.
  try {
    const r = await fetch(`${API}/models?pageSize=200`, { headers: { "x-goog-api-key": key } });
    const d = await r.json();
    const names: string[] = (d.models || [])
      .filter((m: any) => (m.supportedGenerationMethods || []).includes("generateContent"))
      .map((m: any) => String(m.name).replace("models/", ""))
      .filter((n: string) => /^gemini-\d+(\.\d+)?-flash$/.test(n));
    const ver = (n: string) => parseFloat(n.split("-")[1]);
    names.sort((a, b) => ver(b) - ver(a));
    if (names[0]) return (cachedModel = names[0]);
  } catch (_) { /* sigue con el alias */ }
  return (cachedModel = "gemini-flash-latest");
}

const SCHEMA = {
  type: "OBJECT",
  properties: {
    aviso: { type: "STRING", description: "Vacío si todo va bien. Si la imagen no tiene movimientos o algo es dudoso, explícalo en una frase." },
    movimientos: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          type: { type: "STRING", enum: ["gasto", "ingreso", "traspaso"] },
          amount: { type: "NUMBER", description: "Importe en euros, siempre positivo" },
          currency: { type: "STRING", description: "Código de divisa que aparece, p. ej. EUR o USD" },
          date: { type: "STRING", description: "AAAA-MM-DD" },
          note: { type: "STRING", description: "Comercio o concepto corto, como lo escribiría una persona" },
          catId: { type: "STRING" },
          projectId: { type: "STRING" },
          accountId: { type: "STRING" },
          toAccountId: { type: "STRING" },
          client: { type: "STRING", description: "Solo en ingresos de clientes" },
          recurring: { type: "BOOLEAN" },
          confidence: { type: "NUMBER", description: "0 a 1" },
        },
        required: ["type", "amount", "date", "note", "accountId", "confidence"],
      },
    },
  },
  required: ["movimientos"],
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "method" }, 405);

  // Autenticación propia: el token debe ser de un usuario real de este proyecto.
  const auth = req.headers.get("authorization") || "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") || "";
  const u = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, { headers: { authorization: auth, apikey: anon } });
  if (!u.ok) return json({ error: "auth" }, 401);

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return json({ error: "nokey" }, 503);

  let body: any;
  try { body = await req.json(); } catch (_) { return json({ error: "body" }, 400); }
  const images: { mime: string; data: string }[] = (body.images || []).slice(0, 6);
  if (!images.length) return json({ error: "noimage" }, 400);
  if (images.some((i) => !/^image\//.test(i.mime) || typeof i.data !== "string" || i.data.length > 6_000_000)) return json({ error: "image" }, 400);
  const ctx = body.context || {};

  const prompt = [
    "Eres el asistente de finanzas personales de una app. Lee las capturas (pueden ser de Bizum, Revolut, un ticket, una factura o un chat) y extrae los movimientos de dinero.",
    "Reglas:",
    "- amount siempre positivo y en euros. type: gasto si sale dinero, ingreso si entra, traspaso si va entre dos cuentas del usuario.",
    "- Si la divisa no es EUR, pon currency con su código y amount con la cifra tal cual aparece.",
    "- Usa SOLO los id de las listas del contexto para catId, projectId, accountId y toAccountId. Si dudas de la categoría, deja catId vacío.",
    "- Fechas: si la captura no trae año, usa el año de hoy; si dice «hoy» o «ayer», calcúlalo desde la fecha de hoy.",
    "- Bizum: si el dinero llega a otra cuenta que no es del usuario, usa la cuenta de tipo «ajena».",
    "- No inventes movimientos. No repitas los que ya están en «ultimos» (misma fecha, importe y concepto).",
    "- Aplica las reglas automáticas del contexto si la nota contiene su palabra.",
    "- Respeta lo que diga «sobre_mi».",
    "CONTEXTO:",
    JSON.stringify(ctx),
  ].join("\n");

  const model = await pickModel(key);
  const call = (m: string) => fetch(`${API}/models/${m}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({
      contents: [{ role: "user", parts: [{ text: prompt }, ...images.map((i) => ({ inline_data: { mime_type: i.mime, data: i.data } }))] }],
      generationConfig: { temperature: 0.1, responseMimeType: "application/json", responseSchema: SCHEMA },
    }),
  });
  let r = await call(model);
  if (r.status === 404 && model !== "gemini-flash-latest") { cachedModel = "gemini-flash-latest"; r = await call(cachedModel); }
  if (r.status === 429) return json({ error: "quota" }, 429);
  if (r.status === 400 || r.status === 403) { const t = await r.text(); return json({ error: /API key|API_KEY|permission/i.test(t) ? "badkey" : "gemini", detail: t.slice(0, 300) }, 502); }
  if (!r.ok) return json({ error: "gemini", detail: (await r.text()).slice(0, 300) }, 502);
  const d = await r.json();
  const text = d?.candidates?.[0]?.content?.parts?.map((p: any) => p.text || "").join("") || "";
  try {
    const out = JSON.parse(text);
    return json({ model, aviso: out.aviso || "", movimientos: Array.isArray(out.movimientos) ? out.movimientos : [] });
  } catch (_) {
    return json({ error: "parse", detail: text.slice(0, 300) }, 502);
  }
});
