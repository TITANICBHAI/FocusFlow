#!/usr/bin/env python3
"""FocusFlow string tooling.

English lives in app/src/main/res/values/strings.xml. Each other language lives in
tools/i18n/translations/<lang>.json ({key: text}) and is written out to res/values-<lang>/strings.xml.

  tool.py add-en new_keys.json     append {key: english} to values/strings.xml (skips existing keys)
  tool.py missing                  list keys each language still lacks (exit 1 if any)
  tool.py gen                      write every res/values-<lang>/strings.xml; fails on a missing key
                                   or a changed placeholder

Texts use Android format placeholders (%1$s, %1$d). Write a literal percent sign as %%.
"""
import json, os, re, sys
from xml.etree import ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")
EN_FILE = os.path.join(RES, "values", "strings.xml")
LANGS = ["de", "es", "fr", "ja", "ko", "zh"]
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")


def xml_escape(text):
    out = (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
           .replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n"))
    if out[:1] in "@?":
        out = "\\" + out
    return out


def read_english():
    """[(key, text, translatable)] in file order, text unescaped."""
    entries = []
    for el in ET.parse(EN_FILE).getroot().findall("string"):
        raw = el.text or ""
        entries.append((el.get("name"), unescape(raw), el.get("translatable") != "false"))
    return entries


def unescape(raw):
    return (raw.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"')
            .replace("\\@", "@").replace("\\?", "?").replace("\\\\", "\\"))


def load_lang(lang):
    path = os.path.join(HERE, "translations", f"{lang}.json")
    return json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}


def add_en(path):
    new = json.load(open(path, encoding="utf-8"))
    have = {k for k, _, _ in read_english()}
    lines = []
    for k, v in new.items():
        if k in have:
            continue
        lines.append(f'    <string name="{k}">{xml_escape(v)}</string>\n')
    s = open(EN_FILE, encoding="utf-8").read()
    s = s.replace("</resources>", "".join(lines) + "</resources>")
    open(EN_FILE, "w", encoding="utf-8").write(s)
    print(f"added {len(lines)} English strings")


def missing():
    bad = False
    keys = [k for k, _, t in read_english() if t]
    for lang in LANGS:
        tr = load_lang(lang)
        gone = [k for k in keys if k not in tr]
        if gone:
            bad = True
            print(f"{lang}: {len(gone)} missing: {', '.join(gone[:8])}{' ...' if len(gone) > 8 else ''}")
    return 1 if bad else 0


def gen():
    english = [(k, v) for k, v, t in read_english() if t]
    for lang in LANGS:
        tr = load_lang(lang)
        out = ['<?xml version="1.0" encoding="utf-8"?>\n', "<resources>\n"]
        for k, en in english:
            if k not in tr:
                sys.exit(f"{lang}: missing translation for {k}")
            text = tr[k]
            if sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(en)):
                sys.exit(f"{lang}: placeholders of {k} differ from English: {text!r}")
            out.append(f'    <string name="{k}">{xml_escape(text)}</string>\n')
        out.append("</resources>\n")
        d = os.path.join(RES, f"values-{lang}")
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, "strings.xml"), "w", encoding="utf-8").write("".join(out))
    print("generated", ", ".join(LANGS))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "add-en":
        add_en(sys.argv[2])
    elif cmd == "missing":
        sys.exit(missing())
    elif cmd == "gen":
        gen()
    else:
        print(__doc__)
