// Mac web editor for the flashcard words. Reads and writes the same Firestore documents as the
// Android app (/users/{uid}/words/{id}); live listeners make changes show up on every device within seconds.
// Content edits only touch content fields, so editing here never resets review progress (srs).
import { initializeApp } from "https://www.gstatic.com/firebasejs/13.4.0/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithPopup, GoogleAuthProvider, signOut,
} from "https://www.gstatic.com/firebasejs/13.4.0/firebase-auth.js";
import {
  initializeFirestore, persistentLocalCache, persistentMultipleTabManager,
  collection, doc, onSnapshot, addDoc, updateDoc, deleteDoc, setDoc,
} from "https://www.gstatic.com/firebasejs/13.4.0/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";
import { toKana, toHiragana, toKatakana, fromKana, isKana, hasLatin, furigana } from "./kana.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = initializeFirestore(app, {
  localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
});

const $ = (id) => document.getElementById(id);
const DEFAULT_EASE = 2.5; // Srs.DEFAULT_EASE in the app
const DAY = 86_400_000;

const state = {
  uid: null,
  words: [],
  editingId: null,
  display: "FURIGANA",
  displayTouched: false,
  katakana: false,
  filter: "all",
  query: "",
  unsubscribe: null,
};

// ---------- Auth ----------

onAuthStateChanged(auth, (user) => {
  $("loading").hidden = true;
  state.unsubscribe?.();
  state.unsubscribe = null;
  state.uid = user?.uid ?? null;
  $("signin").hidden = !!user;
  $("app").hidden = !user;
  if (!user) return;
  $("avatar").src = user.photoURL ?? "";
  $("avatar").title = user.displayName ?? "";
  listen();
  $("kana").focus();
});

$("signin-btn").addEventListener("click", async () => {
  $("signin-error").hidden = true;
  try {
    await signInWithPopup(auth, new GoogleAuthProvider());
  } catch (e) {
    if (e.code === "auth/popup-closed-by-user" || e.code === "auth/cancelled-popup-request") return;
    $("signin-error").textContent = `登入失敗：${e.code ?? e.message}`;
    $("signin-error").hidden = false;
  }
});
$("signout").addEventListener("click", () => signOut(auth));

// ---------- Data ----------

const wordsCol = () => collection(db, "users", state.uid, "words");

function listen() {
  state.unsubscribe = onSnapshot(wordsCol(), { includeMetadataChanges: true }, (snap) => {
    state.words = snap.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((w) => typeof w.kana === "string")
      .sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));
    setSync(snap.metadata);
    renderList();
    // The word being edited was deleted on another device.
    if (state.editingId && !state.words.some((w) => w.id === state.editingId)) {
      resetForm();
      toast("這個單字已在其他裝置刪除");
    }
  }, (err) => {
    console.error(err);
    setSyncText("off", "讀取失敗");
  });
}

function setSync(meta) {
  if (meta.hasPendingWrites) setSyncText("busy", "上傳中");
  else if (meta.fromCache) setSyncText(navigator.onLine ? "busy" : "off", navigator.onLine ? "連線中" : "離線");
  else setSyncText("ok", "已同步");
}
function setSyncText(cls, text) {
  $("sync").className = `sync ${cls}`;
  $("sync-text").textContent = text;
}
window.addEventListener("offline", () => setSyncText("off", "離線"));

function contentFields() {
  const kanji = $("kanji").value.trim();
  return {
    kanji: kanji || null,
    kana: finalKana(),
    meaning: $("meaning").value.trim(),
    meaningEn: $("meaningEn").value.trim(),
    display: kanji ? state.display : "KANA",
    note: $("note").value.trim(),
  };
}

async function save() {
  const fields = contentFields();
  const err = validate(fields);
  if (err) return showFormError(err);
  showFormError(null);
  const now = Date.now();
  const savingId = state.editingId;
  // Writes go to the local cache first and sync in the background, so the UI does not wait on them.
  let pending;
  if (savingId) {
    pending = updateDoc(doc(wordsCol(), savingId), { ...fields, updatedAt: now });
    toast(`已儲存「${fields.kanji ?? fields.kana}」`);
  } else {
    const ref = doc(wordsCol());
    pending = setDoc(ref, {
      ...fields,
      pos: [],
      createdAt: now,
      updatedAt: now,
      srs: { due: now, intervalDays: 0, ease: DEFAULT_EASE, reps: 0, lapses: 0, lastRating: 0, lastReviewed: 0 },
    });
    state.flashId = ref.id;
    toast(`已新增「${fields.kanji ?? fields.kana}」`);
  }
  resetForm();
  pending.catch((e) => toast(`沒存成功：${e.code ?? e.message}`));
}

function removeCurrent() {
  const word = state.words.find((w) => w.id === state.editingId);
  if (!word) return;
  const { id, ...data } = word;
  deleteDoc(doc(wordsCol(), id)).catch((e) => toast(`刪除失敗：${e.code ?? e.message}`));
  resetForm();
  // Undo puts the exact document back, review progress included.
  toast(`已刪除「${word.kanji ?? word.kana}」`, "復原", () => {
    setDoc(doc(wordsCol(), id), data);
    state.flashId = id;
  });
}

function validate(f) {
  if (!f.kana) return "請輸入讀音。";
  if (hasLatin(f.kana)) return "讀音裡還有英文字母，請檢查拼音（例如結尾的 n 要打 nn）。";
  if (!isKana(f.kana.replace(/[\s・〜]/g, ""))) return "讀音只能是平假名或片假名。";
  return null;
}

// ---------- Form ----------

function finalKana() {
  const raw = $("kana").value.trim();
  return hasLatin(raw) ? toKana(raw, { katakana: state.katakana, final: true }) : raw;
}

$("kana").addEventListener("input", (e) => {
  const el = e.target;
  if (e.isComposing || !hasLatin(el.value)) return updatePreview();
  // Convert finished syllables as they are typed; keep an unfinished tail (e.g. "ky") as letters.
  const atEnd = el.selectionStart === el.value.length;
  const converted = toKana(el.value, { katakana: state.katakana, final: false });
  if (converted !== el.value) {
    const fromEnd = el.value.length - el.selectionEnd;
    el.value = converted;
    const pos = atEnd ? converted.length : Math.max(0, converted.length - fromEnd);
    el.setSelectionRange(pos, pos);
  }
  updatePreview();
});
$("kana").addEventListener("blur", () => {
  const el = $("kana");
  if (hasLatin(el.value)) { el.value = finalKana(); updatePreview(); }
});

document.querySelectorAll("[data-script]").forEach((b) => b.addEventListener("click", () => {
  state.katakana = b.dataset.script === "kata";
  document.querySelectorAll("[data-script]").forEach((x) => x.classList.toggle("on", x === b));
  const el = $("kana");
  el.value = state.katakana ? toKatakana(el.value) : toHiragana(el.value);
  updatePreview();
  el.focus();
}));

$("kanji").addEventListener("input", () => {
  if (!state.displayTouched) state.display = "FURIGANA";
  updatePreview();
});
for (const id of ["meaning", "meaningEn", "note"]) $(id).addEventListener("input", updatePreview);

document.querySelectorAll("[data-display]").forEach((b) => b.addEventListener("click", () => {
  state.display = b.dataset.display;
  state.displayTouched = true;
  updatePreview();
}));

$("form").addEventListener("submit", (e) => { e.preventDefault(); save(); });
$("cancel-btn").addEventListener("click", resetForm);
$("new-btn").addEventListener("click", resetForm);
$("delete-btn").addEventListener("click", removeCurrent);

function updatePreview() {
  const f = contentFields();
  const hasKanji = !!f.kanji;
  document.querySelectorAll("[data-display]").forEach((b) => {
    b.disabled = !hasKanji && b.dataset.display !== "KANA";
    b.classList.toggle("on", b.dataset.display === (hasKanji ? state.display : "KANA"));
  });

  const word = $("pv-word");
  if (!f.kana && !f.kanji) word.innerHTML = '<span class="placeholder">還沒輸入</span>';
  else word.replaceChildren(wordNode(f));
  $("pv-romaji").textContent = f.kana && !hasLatin(f.kana) ? fromKana(f.kana) : "";
  $("pv-meaning").textContent = f.meaning || f.meaningEn;

  const kanaHint = $("kana-hint");
  const bad = f.kana && !hasLatin($("kana").value) && !isKana(f.kana.replace(/[\s・〜]/g, ""));
  kanaHint.textContent = bad ? "讀音裡有漢字或其他符號；漢字請寫在下一欄。" : "英文字母會自動轉成假名；結尾的 n 請打 nn。";
  kanaHint.classList.toggle("bad", !!bad);
  $("kana").classList.toggle("invalid", !!bad);

  // Same word already saved?
  const dup = f.kana && state.words.find((w) =>
    w.id !== state.editingId && toHiragana(w.kana) === toHiragana(f.kana) && (w.kanji ?? null) === f.kanji);
  const dupEl = $("dup");
  dupEl.hidden = !dup;
  if (dup) {
    dupEl.textContent = "這個單字已經有了。";
    const open = document.createElement("button");
    open.type = "button";
    open.textContent = "打開它";
    open.onclick = () => edit(dup.id);
    dupEl.append(open);
  }
}

function edit(id) {
  const w = state.words.find((x) => x.id === id);
  if (!w) return;
  state.editingId = id;
  state.display = w.display ?? "FURIGANA";
  state.displayTouched = true;
  state.katakana = false;
  document.querySelectorAll("[data-script]").forEach((x) => x.classList.toggle("on", x.dataset.script === "hira"));
  $("kana").value = w.kana;
  $("kanji").value = w.kanji ?? "";
  $("meaning").value = w.meaning ?? "";
  $("meaningEn").value = w.meaningEn ?? "";
  $("note").value = w.note ?? "";
  setEditing(true);
  showFormError(null);
  updatePreview();
  renderSelection();
  $("meaning").focus();
}

function resetForm() {
  state.editingId = null;
  state.display = "FURIGANA";
  state.displayTouched = false;
  $("form").reset();
  setEditing(false);
  showFormError(null);
  updatePreview();
  renderSelection();
  $("kana").focus();
}

function setEditing(on) {
  $("editor-title").textContent = on ? "編輯單字" : "新增單字";
  $("save-btn").firstChild.textContent = on ? "儲存" : "新增";
  $("delete-btn").hidden = !on;
  $("cancel-btn").hidden = !on;
  $("new-btn").hidden = !on;
  document.querySelector(".editor").classList.toggle("editing", on);
}

function showFormError(msg) {
  $("form-error").hidden = !msg;
  $("form-error").textContent = msg ?? "";
}

// ---------- List ----------

function wordNode(w) {
  const span = document.createElement("span");
  const display = w.kanji ? (w.display ?? "FURIGANA") : "KANA";
  if (display === "KANA") { span.textContent = w.kana; return span; }
  if (display === "KANJI") { span.textContent = w.kanji; return span; }
  for (const seg of furigana(w.kanji, w.kana)) {
    if (!seg.ruby) { span.append(seg.base); continue; }
    const ruby = document.createElement("ruby");
    ruby.append(seg.base);
    const rt = document.createElement("rt");
    rt.textContent = seg.ruby;
    ruby.append(rt);
    span.append(ruby);
  }
  return span;
}

function status(w, now) {
  const srs = w.srs ?? {};
  if (!srs.reps) return { cls: "new", text: "還沒學" };
  if ((srs.due ?? 0) <= now) return { cls: "due", text: "該複習" };
  const days = Math.ceil(((srs.due ?? 0) - now) / DAY);
  return { cls: "later", text: days <= 1 ? "明天複習" : `${days} 天後` };
}

function relative(ms) {
  if (!ms) return "";
  const diff = Date.now() - ms;
  if (diff < 60_000) return "剛剛";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分鐘前`;
  if (diff < DAY) return `${Math.floor(diff / 3_600_000)} 小時前`;
  if (diff < 30 * DAY) return `${Math.floor(diff / DAY)} 天前`;
  const d = new Date(ms);
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
}

const FILTERS = {
  all: () => true,
  due: (w, now) => status(w, now).cls === "due",
  new: (w, now) => status(w, now).cls === "new",
  blank: (w) => !(w.meaning ?? "").trim(),
};

function matches(w, q) {
  if (!q) return true;
  const needle = q.toLowerCase();
  const kanaQ = toHiragana(hasLatin(q) ? toKana(q) : q);
  const hay = [w.kanji, w.meaning, w.meaningEn, w.note].filter(Boolean).join("\n").toLowerCase();
  return hay.includes(needle)
    || toHiragana(w.kana).includes(kanaQ)
    || fromKana(w.kana).includes(needle.replace(/\s/g, ""));
}

function renderList() {
  const now = Date.now();
  const all = state.words;
  for (const [key, fn] of Object.entries(FILTERS)) {
    document.querySelector(`[data-filter="${key}"] span`).textContent = all.filter((w) => fn(w, now)).length;
  }
  const due = all.filter((w) => FILTERS.due(w, now)).length;
  $("stats").textContent = `${all.length} 個單字 · ${due} 個該複習`;

  const shown = all.filter((w) => FILTERS[state.filter](w, now) && matches(w, state.query));
  const list = $("list");
  list.replaceChildren(...shown.map((w) => rowNode(w, now)));

  const empty = $("empty");
  empty.hidden = shown.length > 0;
  if (!shown.length) {
    empty.innerHTML = all.length === 0
      ? '<div class="big">空</div>還沒有單字，從左邊新增第一個吧。'
      : state.query ? `<div class="big">無</div>找不到「${escapeHtml(state.query)}」。` : '<div class="big">無</div>這一類目前沒有單字。';
  }

  if (state.flashId) {
    const el = list.querySelector(`[data-id="${state.flashId}"]`);
    if (el) { el.classList.add("flash"); el.scrollIntoView({ block: "nearest", behavior: "smooth" }); state.flashId = null; }
  }
  updatePreview(); // refreshes the duplicate warning against the new list
}

function rowNode(w, now) {
  const li = document.createElement("li");
  li.className = "row" + (w.id === state.editingId ? " selected" : "");
  li.dataset.id = w.id;
  li.tabIndex = 0;

  const left = document.createElement("div");
  const word = document.createElement("div");
  word.className = "word";
  word.lang = "ja";
  word.append(wordNode(w));
  const romaji = document.createElement("div");
  romaji.className = "romaji";
  romaji.textContent = fromKana(w.kana);
  left.append(word, romaji);

  const mid = document.createElement("div");
  const meaning = document.createElement("div");
  const m = (w.meaning ?? "").trim();
  meaning.className = "meaning" + (m ? "" : " missing");
  meaning.textContent = m || (w.meaningEn ? w.meaningEn : "還沒有中文意思");
  mid.append(meaning);
  const extra = [m && w.meaningEn, w.note].filter(Boolean).join(" · ");
  if (extra) {
    const sub = document.createElement("div");
    sub.className = "sub";
    sub.textContent = extra;
    sub.title = extra;
    mid.append(sub);
  }

  const meta = document.createElement("div");
  meta.className = "meta";
  const s = status(w, now);
  const badge = document.createElement("span");
  badge.className = `badge ${s.cls}`;
  badge.textContent = s.text;
  const when = document.createElement("span");
  when.className = "when";
  when.textContent = `加入 ${relative(w.createdAt)}`;
  meta.append(badge, when);

  li.append(left, mid, meta);
  li.addEventListener("click", () => edit(w.id));
  li.addEventListener("keydown", (e) => { if (e.key === "Enter") edit(w.id); });
  return li;
}

function renderSelection() {
  document.querySelectorAll(".row").forEach((r) => r.classList.toggle("selected", r.dataset.id === state.editingId));
}

$("search").addEventListener("input", (e) => { state.query = e.target.value.trim(); renderList(); });
document.querySelectorAll("[data-filter]").forEach((b) => b.addEventListener("click", () => {
  state.filter = b.dataset.filter;
  document.querySelectorAll("[data-filter]").forEach((x) => x.classList.toggle("on", x === b));
  renderList();
}));

// ---------- Keyboard ----------

document.addEventListener("keydown", (e) => {
  if ($("app").hidden) return;
  if ((e.metaKey || e.ctrlKey) && e.key === "Enter") { e.preventDefault(); save(); return; }
  if (e.key === "Escape") {
    if (document.activeElement === $("search") && $("search").value) {
      $("search").value = ""; state.query = ""; renderList();
    } else if (state.editingId) resetForm();
    return;
  }
  const typing = ["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName);
  if (e.key === "/" && !typing) { e.preventDefault(); $("search").focus(); }
});

// ---------- Toast ----------

let toastTimer;
function toast(text, actionLabel, action) {
  clearTimeout(toastTimer);
  $("toast-text").textContent = text;
  const btn = $("toast-action");
  btn.hidden = !actionLabel;
  btn.textContent = actionLabel ?? "";
  btn.onclick = () => { $("toast").hidden = true; action?.(); };
  $("toast").hidden = false;
  toastTimer = setTimeout(() => { $("toast").hidden = true; }, actionLabel ? 6000 : 2600);
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// Refresh "N 天後" / "剛剛" labels now and then.
setInterval(() => { if (state.uid) renderList(); }, 60_000);
