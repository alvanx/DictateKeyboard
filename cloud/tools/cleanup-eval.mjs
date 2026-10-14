#!/usr/bin/env node
/**
 * The real-model suite for `/v1/cleanup` (cleanup spec, section 8) — run before each release and
 * before pointing CLEANUP_MODEL at a cheaper model.
 *
 * The unit tests mock the model; this asks the deployed one. It needs a device token for an
 * account with Plus (a licence tester's is fine):
 *
 *   CLOUD_URL=https://api.example.com CLOUD_TOKEN=dk_… npm run eval:cleanup
 *
 * Each case says what shape the answer must have and which words must be in it. Exits non-zero if
 * any case fails, so it can gate a release.
 */
const base = (process.env.CLOUD_URL ?? '').replace(/\/+$/, '');
const token = process.env.CLOUD_TOKEN ?? '';
if (!base || !token) {
  console.error('Set CLOUD_URL and CLOUD_TOKEN.');
  process.exit(2);
}

const cases = [
  { name: 'ordinals → numbered list of three', body: { text: 'first passports second chargers third tickets', locale: 'en-US' },
    shape: 'list', items: 3, marker: /^1\. /m, has: ['passports', 'chargers', 'tickets'] },
  { name: 'comma series stays a sentence', body: { text: 'we need milk eggs and bread', locale: 'en-US' },
    shape: 'plain', has: ['milk', 'eggs', 'bread'] },
  { name: 'spoken bullets, cue words removed', body: { text: 'bullet milk bullet eggs bullet bread', locale: 'en-US' },
    shape: 'list', items: 3, marker: /^- (?!\[)/m, hasNot: ['bullet'] },
  { name: 'to-do with force → checklist of two', body: { text: 'to do call the bank book the dentist', locale: 'en-US', lists: 'force' },
    shape: 'list', items: 2, marker: /^- \[ \] /m, has: ['bank', 'dentist'] },
  { name: 'lists off → punctuation only', body: { text: 'to do call the bank book the dentist', locale: 'en-US', lists: 'off' },
    shape: 'plain', has: ['bank', 'dentist'] },
  { name: 'German list, German lead-in', body: { text: 'für die Reise brauchen wir erstens Pässe zweitens Ladegeräte drittens die Tickets', locale: 'de-DE', context: 'notes' },
    shape: 'list', items: 3, has: ['Pässe', 'Ladegeräte', 'Tickets'] },
  { name: 'Spanish list', body: { text: 'para el viaje necesitamos primero pasaportes segundo cargadores tercero los billetes', locale: 'es-ES' },
    shape: 'list', items: 3, has: ['pasaportes', 'cargadores', 'billetes'] },
  { name: 'French list', body: { text: 'pour le voyage il faut premièrement les passeports deuxièmement les chargeurs troisièmement les billets', locale: 'fr-FR' },
    shape: 'list', items: 3, has: ['passeports', 'chargeurs', 'billets'] },
  { name: 'Portuguese list', body: { text: 'para a viagem precisamos primeiro passaportes segundo carregadores terceiro os bilhetes', locale: 'pt-BR' },
    shape: 'list', items: 3, has: ['passaportes', 'carregadores', 'bilhetes'] },
  { name: 'Dutch list', body: { text: 'voor de reis hebben we nodig ten eerste paspoorten ten tweede opladers ten derde de tickets', locale: 'nl-NL' },
    shape: 'list', items: 3, has: ['paspoorten', 'opladers', 'tickets'] },
  { name: 'code context untouched except filler', body: { text: 'um git commit dash m fix the build', locale: 'en-US', context: 'code' },
    shape: 'plain', hasNot: ['um'] },
  { name: 'terms are spelled as given', body: { text: 'we land at shiphol and then fly to lisbon', locale: 'en-US', terms: ['Schiphol', 'Lisbon'] },
    shape: 'plain', has: ['Schiphol', 'Lisbon'] },
];

let failed = 0;
for (const c of cases) {
  const started = Date.now();
  const res = await fetch(`${base}/v1/cleanup`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify(c.body),
  });
  const body = await res.json().catch(() => ({}));
  const problems = [];
  if (res.status !== 200) problems.push(`HTTP ${res.status} ${body?.error?.code ?? ''}`);
  else {
    if (body.formatted_as !== c.shape) problems.push(`shape ${body.formatted_as}, wanted ${c.shape}`);
    if (c.items && body.items !== c.items) problems.push(`${body.items} items, wanted ${c.items}`);
    if (c.marker && !c.marker.test(body.text)) problems.push(`marker ${c.marker} missing`);
    for (const w of c.has ?? []) if (!body.text.includes(w)) problems.push(`"${w}" missing`);
    for (const w of c.hasNot ?? []) if (new RegExp(`\\b${w}\\b`, 'i').test(body.text)) problems.push(`"${w}" still there`);
  }
  const ms = Date.now() - started;
  if (problems.length) {
    failed++;
    console.log(`✖ ${c.name} (${ms} ms): ${problems.join('; ')}\n  ${JSON.stringify(body.text ?? body)}`);
  } else {
    console.log(`✔ ${c.name} (${ms} ms, ${body.usage?.seconds_charged ?? '?'} s charged)`);
  }
}
console.log(`\n${cases.length - failed}/${cases.length} passed`);
process.exit(failed ? 1 : 0);
