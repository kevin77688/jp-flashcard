#!/usr/bin/env python3
"""Build the compact offline dictionary bundled with the app.

Input: the English JMdict JSON from jmdict-simplified
       (https://github.com/scriptin/jmdict-simplified/releases, jmdict-eng-*.json).
Output: a gzipped SQLite file at app/src/main/assets/jmdict.db.gz
        (the app unpacks it into internal storage on first launch).

Usage:
    python3 -I tools/build_dictionary.py <jmdict-eng.json> app/src/main/assets/jmdict.db.gz

JMdict is © the Electronic Dictionary Research and Development Group, used under CC BY-SA 4.0.
"""
import gzip
import json
import shutil
import tempfile
import os
import sqlite3
import sys

SKIP_KANJI_TAGS = {"sK", "iK", "oK", "rK", "ateji"}
SKIP_KANA_TAGS = {"sk", "ik", "ok", "rk"}
MAX_SENSES = 3
MAX_GLOSSES = 3


def is_katakana_word(text: str) -> bool:
    return all("ァ" <= c <= "ヺ" or c == "ー" for c in text)


def to_hiragana(text: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in text)


def applies(item_list, kanji_text):
    return "*" in item_list or kanji_text in item_list


def main(src: str, dst_gz: str) -> None:
    with open(src, encoding="utf-8") as f:
        data = json.load(f)

    dst = os.path.join(tempfile.mkdtemp(), "jmdict.db")
    db = sqlite3.connect(dst)
    db.executescript(
        """
        PRAGMA page_size = 4096;
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
        -- reading is hiragana-normalised for lookup; kata=1 means it is written in katakana,
        -- reading_orig holds mixed-script readings that cannot be restored from the flag.
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY,
            kanji TEXT,
            reading TEXT NOT NULL,
            kata INTEGER NOT NULL,
            reading_orig TEXT,
            gloss TEXT NOT NULL,
            pos TEXT NOT NULL,
            common INTEGER NOT NULL,
            uk INTEGER NOT NULL
        );
        """
    )

    row_id = 0
    entries = []
    for word in data["words"]:
        senses = word["sense"][:MAX_SENSES]
        if not senses:
            continue
        gloss = "\n".join(
            "; ".join(g["text"] for g in s["gloss"][:MAX_GLOSSES]) for s in senses if s["gloss"]
        )
        if not gloss:
            continue
        pos = ",".join(senses[0]["partOfSpeech"])
        uk = int("uk" in senses[0]["misc"])
        kanji_forms = [k for k in word["kanji"] if not SKIP_KANJI_TAGS.intersection(k["tags"])]
        kana_forms = [k for k in word["kana"] if not SKIP_KANA_TAGS.intersection(k["tags"])]
        if not kana_forms:
            continue

        for kana in kana_forms:
            if kana.get("appliesToKanji") == [] or not kanji_forms:
                best = None  # reading is only ever written in kana
            else:
                candidates = [k for k in kanji_forms if applies(kana.get("appliesToKanji", ["*"]), k["text"])]
                candidates.sort(key=lambda k: not k["common"])
                best = candidates[0]["text"] if candidates else None
            common = int(kana["common"] or bool(best and any(k["common"] and k["text"] == best for k in kanji_forms)))
            row_id += 1
            text = kana["text"]
            hira = to_hiragana(text)
            kata = int(is_katakana_word(text))
            orig = None if (text == hira or kata) else text
            entries.append((row_id, best, hira, kata, orig, gloss, pos, common, uk))

    db.executemany("INSERT INTO entries VALUES (?,?,?,?,?,?,?,?,?)", entries)
    db.execute("CREATE INDEX entries_reading ON entries(reading)")
    db.execute("CREATE INDEX entries_kanji ON entries(kanji) WHERE kanji IS NOT NULL")
    db.executemany(
        "INSERT INTO meta VALUES (?,?)",
        [("source", "JMdict (EDRDG) via jmdict-simplified"), ("version", data.get("version", "")),
         ("dictDate", data.get("dictDate", "")), ("rows", str(row_id))],
    )
    db.commit()
    db.execute("VACUUM")
    db.close()
    raw = os.path.getsize(dst)
    os.makedirs(os.path.dirname(dst_gz), exist_ok=True)
    with open(dst, "rb") as fin, gzip.open(dst_gz, "wb", compresslevel=9) as fout:
        shutil.copyfileobj(fin, fout)
    shutil.rmtree(os.path.dirname(dst))
    print(f"wrote {row_id} rows: {raw / 1e6:.1f} MB raw, {os.path.getsize(dst_gz) / 1e6:.1f} MB gzipped -> {dst_gz}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
