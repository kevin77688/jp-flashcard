// Romaji ⇄ kana and furigana splitting, ported from the Android app (domain/Romaji.kt, Furigana.kt)
// so the web editor converts and displays words exactly like the phone and tablet.

const VOWELS = "aiueo";

const table = (() => {
  const m = new Map();
  const row = (c, kana) => [...kana].forEach((k, i) => { if (k !== "・") m.set(c + VOWELS[i], k); });
  row("", "あいうえお");
  row("k", "かきくけこ"); row("g", "がぎぐげご");
  row("s", "さしすせそ"); row("z", "ざじずぜぞ");
  row("t", "たちつてと"); row("d", "だぢづでど");
  row("n", "なにぬねの"); row("h", "はひふへほ");
  row("b", "ばびぶべぼ"); row("p", "ぱぴぷぺぽ");
  row("m", "まみむめも"); row("r", "らりるれろ");
  row("y", "や・ゆ・よ"); row("w", "わ・・・を");
  Object.entries({ shi: "し", chi: "ち", tsu: "つ", fu: "ふ", ji: "じ", ye: "いぇ", wi: "うぃ", we: "うぇ", wo: "を" })
    .forEach(([k, v]) => m.set(k, v));
  const youon = { ky: "き", gy: "ぎ", sy: "し", zy: "じ", ty: "ち", dy: "ぢ", ny: "に", hy: "ひ", by: "び", py: "ぴ",
    my: "み", ry: "り", sh: "し", ch: "ち", cy: "ち", jy: "じ" };
  const smallY = { a: "ゃ", u: "ゅ", o: "ょ", e: "ぇ" };
  for (const [c, base] of Object.entries(youon)) for (const [v, s] of Object.entries(smallY)) m.set(c + v, base + s);
  Object.entries({ ja: "じゃ", ju: "じゅ", jo: "じょ", je: "じぇ", she: "しぇ", che: "ちぇ",
    fa: "ふぁ", fi: "ふぃ", fe: "ふぇ", fo: "ふぉ", fyu: "ふゅ",
    va: "ゔぁ", vi: "ゔぃ", vu: "ゔ", ve: "ゔぇ", vo: "ゔぉ",
    thi: "てぃ", thu: "てゅ", dhi: "でぃ", dhu: "でゅ", twu: "とぅ", dwu: "どぅ",
    tsa: "つぁ", tsi: "つぃ", tse: "つぇ", tso: "つぉ" }).forEach(([k, v]) => m.set(k, v));
  for (const p of ["x", "l"]) {
    Object.entries({ a: "ぁ", i: "ぃ", u: "ぅ", e: "ぇ", o: "ぉ", ya: "ゃ", yu: "ゅ", yo: "ょ", wa: "ゎ",
      tu: "っ", tsu: "っ", ka: "ゕ", ke: "ゖ" }).forEach(([k, v]) => m.set(p + k, v));
  }
  m.set("nn", "ん"); m.set("n'", "ん"); m.set("xn", "ん");
  return m;
})();
const maxKey = Math.max(...[...table.keys()].map((k) => k.length));
const isLetter = (c) => c !== undefined && /[a-z]/.test(c);

/** Converts romaji to kana. With final=false a trailing n / unfinished consonant stays as typed. */
export function toKana(input, { katakana = false, final = true } = {}) {
  const s = input.toLowerCase();
  let out = "";
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    const next = s[i + 1];
    if (c === "-") { out += "ー"; i++; continue; }
    if (isLetter(c) && !VOWELS.includes(c) && c !== "n" && next === c) { out += "っ"; i++; continue; }
    if (c === "t" && next === "c" && s[i + 2] === "h") { out += "っ"; i++; continue; }
    if (c === "n") {
      if (next === "n") {
        const after = s[i + 2];
        out += "ん";
        i += after !== undefined && (VOWELS.includes(after) || after === "y") ? 1 : 2;
        continue;
      }
      if (next === undefined) {
        if (final) { out += "ん"; i++; continue; }
      } else if (next !== "y" && next !== "'" && !VOWELS.includes(next)) {
        out += "ん"; i++; continue;
      }
    }
    let matched = false;
    for (let len = Math.min(maxKey, s.length - i); len >= 1; len--) {
      const kana = table.get(s.substr(i, len));
      if (kana === undefined) continue;
      out += kana; i += len; matched = true; break;
    }
    if (!matched) {
      out += { ".": "。", ",": "、", "?": "？", "!": "！", "~": "〜" }[c] ?? input[i];
      i++;
    }
  }
  return katakana ? toKatakana(out) : out;
}

export const toKatakana = (t) => t.replace(/[ぁ-ゖ]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) + 0x60));
export const toHiragana = (t) => t.replace(/[ァ-ヶ]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0x60));
export const isKana = (t) => t.length > 0 && /^[ぁ-ゖァ-ヺーゝゞヽヾ]+$/.test(t);
export const hasLatin = (t) => /[A-Za-z]/.test(t);

const reverse = (() => {
  const m = new Map();
  for (const [k, v] of table) {
    if (k.startsWith("x") || k.startsWith("l") || k === "n'" || k === "nn") continue;
    const existing = m.get(v);
    if (existing === undefined || k.length < existing.length) m.set(v, k);
  }
  Object.entries({ し: "shi", ち: "chi", つ: "tsu", ふ: "fu", じ: "ji", ぢ: "ji", づ: "zu", を: "o",
    しゃ: "sha", しゅ: "shu", しょ: "sho", ちゃ: "cha", ちゅ: "chu", ちょ: "cho", じゃ: "ja", じゅ: "ju", じょ: "jo",
    ぢゃ: "ja", ぢゅ: "ju", ぢょ: "jo", しぇ: "she", ちぇ: "che", じぇ: "je", ん: "n", ゔ: "vu",
    ぁ: "a", ぃ: "i", ぅ: "u", ぇ: "e", ぉ: "o", ゃ: "ya", ゅ: "yu", ょ: "yo" }).forEach(([k, v]) => m.set(k, v));
  return m;
})();

/** Hepburn romaji for a kana string. */
export function fromKana(kana) {
  const s = toHiragana(kana);
  let out = "";
  let i = 0;
  let geminate = false;
  let prevN = false;
  while (i < s.length) {
    const ch = s[i];
    if (ch === "っ") { geminate = true; i++; continue; }
    if (ch === "ー") {
      const v = [...out].reverse().find((c) => VOWELS.includes(c));
      if (v) out += v;
      prevN = false; i++; continue;
    }
    let roman = i + 1 < s.length ? reverse.get(s.substr(i, 2)) : undefined;
    if (roman !== undefined) i += 2;
    else { roman = reverse.get(ch) ?? ch; i++; }
    if (geminate) { out += roman.startsWith("ch") ? "t" : roman[0]; geminate = false; }
    if (prevN && (VOWELS.includes(roman[0]) || roman[0] === "y")) out += "'";
    out += roman;
    prevN = roman === "n";
  }
  if (geminate) out += "'";
  return out;
}

/** Splits 食べる/たべる into [{base:"食",ruby:"た"},{base:"べる"}] so okurigana are not covered by ruby. */
export function furigana(kanji, reading) {
  if (!kanji) return [{ base: reading, ruby: null }];
  const hira = toHiragana(reading);
  const runs = [];
  for (const ch of kanji) {
    const k = isKana(ch);
    const last = runs[runs.length - 1];
    if (last && last.kana === k) last.text += ch; else runs.push({ text: ch, kana: k });
  }
  const acc = [];
  const match = (pi, ri) => {
    if (pi === runs.length) return ri === hira.length;
    const part = runs[pi];
    if (part.kana) {
      const p = toHiragana(part.text);
      if (!hira.startsWith(p, ri)) return false;
      acc.push({ base: part.text, ruby: null });
      if (match(pi + 1, ri + p.length)) return true;
      acc.pop();
      return false;
    }
    const last = pi === runs.length - 1;
    for (let end = ri + 1; end <= (last ? hira.length : hira.length - 1); end++) {
      if (last && end !== hira.length) continue;
      acc.push({ base: part.text, ruby: reading.slice(ri, end) });
      if (match(pi + 1, end)) return true;
      acc.pop();
    }
    return false;
  };
  return match(0, 0) ? acc : [{ base: kanji, ruby: reading }];
}
