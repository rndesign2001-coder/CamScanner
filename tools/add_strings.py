"""Yangi satrlarni uz/ru/en strings.xml fayllariga qo'shadi: python3 tools/add_strings.py <json>"""
import json, re, sys
RES = "app/src/main/res"
def esc(t):
    t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return t.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
new = json.load(open(sys.argv[1]))
for idx, folder in [(0, "values"), (1, "values-ru"), (2, "values-en")]:
    p = f"{RES}/{folder}/strings.xml"
    s = open(p, encoding="utf-8").read()
    for k, v in new.items():
        line = f'    <string name="{k}">{esc(v[idx])}</string>\n'
        if re.search(f'name="{k}"', s):
            s = re.sub(f'    <string name="{k}"[^\\n]*\\n', line.replace("\\", "\\\\"), s)
        else:
            s = s.replace("</resources>", line + "</resources>")
    open(p, "w", encoding="utf-8").write(s)
print("ok", len(new))
