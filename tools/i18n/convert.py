#!/usr/bin/env python3
"""Move display text in Compose code into string resources.

  convert.py <prefix> <path> [<path> ...]       rewrite files in place, write /tmp/<prefix>_en.json
  convert.py --scan <path> [<path> ...]         only list literals that would be left (no changes)

Only the safe, common shapes are rewritten, and only inside @Composable functions:
    Text("...")   text = "..."   label = "..."   placeholder = "..."   contentDescription = "..."
    title / subtitle / description / hint / supportingText = "..."
A template such as "Hello $name, ${a.b} left" becomes a string with %1$s, %2$s placeholders.
Everything else (plurals, text built in several pieces, text outside composables) is reported for
hand conversion. Identical English text reuses the key it already has.
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
EN_FILE = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "values", "strings.xml")
NAMED = r"(?:text|label|placeholder|contentDescription|title|subtitle|description|hint|supportingText)"
LIT = r'"((?:[^"\\\n]|\\.)*)"'
PATTERNS = [
    re.compile(r"(\bText\(\s*)" + LIT),
    re.compile(r"(\b" + NAMED + r"\s*=\s*)" + LIT),
]
KEEP = {"FocusFlow", "TBTechs", "OK"}
FUN = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|inline|override|suspend)\s+)*fun\s")


def unescape_kt(body):
    out, i = [], 0
    while i < len(body):
        c = body[i]
        if c == "\\" and i + 1 < len(body):
            n = body[i + 1]
            if n in "\"'\\$":
                out.append(n); i += 2; continue
            if n == "n":
                out.append("\n"); i += 2; continue
            if n == "t":
                out.append("\t"); i += 2; continue
            if n == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", body[i + 2:i + 6] or ""):
                out.append(chr(int(body[i + 2:i + 6], 16))); i += 6; continue
            return None
        out.append(c); i += 1
    return "".join(out)


def split_template(text):
    """-> (format_text, [kotlin expressions]) or None when the template is too complex."""
    args, out, i = [], [], 0
    while i < len(text):
        c = text[i]
        if c == "$" and i + 1 < len(text):
            if text[i + 1] == "{":
                depth, j = 1, i + 2
                while j < len(text) and depth:
                    depth += {"{": 1, "}": -1}.get(text[j], 0)
                    j += 1
                expr = text[i + 2:j - 1]
                if '"' in expr or "{" in expr or "}" in expr or "\n" in expr:
                    return None
                args.append(expr.strip()); out.append(f"%{len(args)}$s"); i = j; continue
            m = re.match(r"[A-Za-z_]\w*", text[i + 1:])
            if m:
                args.append(m.group(0)); out.append(f"%{len(args)}$s"); i += 1 + len(m.group(0)); continue
        out.append("%%" if c == "%" else c); i += 1
    return "".join(out), args


def key_for(prefix, text, taken):
    words = re.findall(r"[a-z0-9]+", re.sub(r"%\d\$s", " ", text.lower()))[:5]
    base = f"{prefix}_{'_'.join(words) or 'text'}"[:60].rstrip("_")
    k, n = base, 2
    while k in taken and taken[k] != text:
        k = f"{base}_{n}"; n += 1
    return k


def english_index():
    import xml.etree.ElementTree as ET
    unesc = lambda s: (s or "").replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"').replace("\\\\", "\\")
    return {el.get("name"): unesc(el.text) for el in ET.parse(EN_FILE).getroot().findall("string")}


def composable_map(lines):
    """For each line, whether its innermost enclosing function is a @Composable (not a @Preview)."""
    result = [False] * len(lines)
    depth = 0
    stack = []  # [depth_at_start, is_composable, opened, paren_depth]
    in_block_comment = False
    for n, line in enumerate(lines):
        code = re.sub(r'"(?:[^"\\]|\\.)*"', '""', line)
        code = re.sub(r"'(?:[^'\\]|\\.)*'", "''", code)
        code = re.sub(r"//.*", "", code)
        if in_block_comment:
            if "*/" in code:
                code = code.split("*/", 1)[1]; in_block_comment = False
            else:
                code = ""
        if "/*" in code:
            head, _, tail = code.partition("/*")
            if "*/" in tail:
                code = head + tail.split("*/", 1)[1]
            else:
                code = head; in_block_comment = True
        if FUN.match(code) or re.search(r"\bfun\s+[\w<]", code):
            window = "\n".join(lines[max(0, n - 3):n + 1])
            stack.append([depth, "@Composable" in window and "@Preview" not in window, False, 0])
        result[n] = next((f[1] for f in reversed(stack) if f[2] or f[3] > 0 or True), False) if stack else False
        for ch in code:
            if ch == "(" and stack and not stack[-1][2]:
                stack[-1][3] += 1
            elif ch == ")" and stack and not stack[-1][2]:
                stack[-1][3] -= 1
            elif ch == "{":
                if stack and not stack[-1][2] and stack[-1][3] <= 0:
                    stack[-1][2] = True
                depth += 1
            elif ch == "}":
                depth -= 1
                while stack and stack[-1][2] and stack[-1][0] == depth:
                    stack.pop()
        # a bodiless / expression-bodied function ends when we return to its depth with no body opened
        while stack and not stack[-1][2] and stack[-1][3] <= 0 and re.search(r"\)\s*(:\s*[\w<>?., ]+)?\s*$", code) is None and depth <= stack[-1][0] and n > 0 and code.strip() == "":
            stack.pop()
    return result


def process(path, prefix, known, new, scan_only):
    src = open(path, encoding="utf-8").read()
    lines = src.split("\n")
    comp = composable_map(lines)
    left, changed = [], False
    for n, line in enumerate(lines):
        if line.lstrip().startswith(("//", "*", "/*")):
            continue
        new_line = line
        for pat in PATTERNS:
            def sub(m):
                body = m.group(2)
                if "stringResource(" in line[:m.start()]:
                    return m.group(0)
                text = unescape_kt(body)
                if text is None or text in KEEP or not re.search(r"[A-Za-z]{2}", re.sub(r"\$\{[^}]*\}|\$\w+", "", text)):
                    return m.group(0)
                if not comp[n]:
                    left.append((n + 1, "not in a composable", line.strip()))
                    return m.group(0)
                tpl = split_template(text)
                if tpl is None:
                    left.append((n + 1, "complex template", line.strip()))
                    return m.group(0)
                fmt, args = tpl
                if scan_only:
                    left.append((n + 1, "would convert", line.strip()))
                    return m.group(0)
                reuse = next((k for k, v in {**known, **new}.items() if v == fmt), None)
                key = reuse or key_for(prefix, fmt, {**known, **new})
                new[key] = fmt
                call = f"stringResource(R.string.{key}" + "".join(f", {a}" for a in args) + ")"
                return m.group(1) + call
            replaced = pat.sub(sub, new_line)
            new_line = replaced
        if new_line != line:
            lines[n] = new_line; changed = True
        # report literals still left on the line
        for m in re.finditer(LIT, new_line):
            t = m.group(1)
            if re.search(r"[A-Za-z]{3,}.*\s|^[A-Z][a-z]{3,}", t) and "stringResource(" not in new_line and len(t) > 3:
                left.append((n + 1, "left", new_line.strip()))
                break
    if changed and not scan_only:
        out = "\n".join(lines)
        adds = []
        for imp in ("androidx.compose.ui.res.stringResource", "com.tbtechs.focusflow.R"):
            if f"import {imp}\n" not in out:
                adds.append(f"import {imp}\n")
        if adds:
            m = re.search(r"^import .*\n", out, re.M)
            out = out[:m.start()] + "".join(adds) + out[m.start():]
        open(path, "w", encoding="utf-8").write(out)
    return left, changed


def main():
    args = sys.argv[1:]
    scan_only = args and args[0] == "--scan"
    if scan_only:
        args = args[1:]
        prefix = "x"
    else:
        prefix, args = args[0], args[1:]
    files = []
    for a in args:
        if os.path.isdir(a):
            for r, _, fs in os.walk(a):
                files += [os.path.join(r, f) for f in fs if f.endswith(".kt")]
        else:
            files.append(a)
    known, new, total_left, total_conv = english_index(), {}, 0, 0
    for f in sorted(files):
        left, changed = process(f, prefix, known, new, scan_only)
        for ln, why, code in left:
            if why != "would convert" or scan_only:
                print(f"{os.path.relpath(f)}:{ln}: [{why}] {code[:150]}")
        total_left += sum(1 for _, w, _ in left if w != "would convert")
        total_conv += changed
    if not scan_only:
        json.dump(new, open(f"/tmp/{prefix}_en.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"\n{len(new)} new keys, {total_conv} files changed, {total_left} literals left for hand conversion", file=sys.stderr)


if __name__ == "__main__":
    main()
