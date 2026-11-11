"""Builds the approval page (docs/boss-experience/plan.html) from plan.src.html: the data (data/durability.json from
harness/durability.py, data/fight.json), the real frames (img/*.jpg as data URIs) and, when img/frames.json lists them,
the CI tours' frame strips ({"ring": {"note": ..., "frames": [{"file", "alt", "caption"}]}, "death": ..., "finisher": ...})."""
import base64, json, os, re
HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "plan.src.html"), encoding="utf-8").read()
dur = open(os.path.join(HERE, "data/durability.json")).read()
fight = open(os.path.join(HERE, "data/fight.json")).read()
assert "/*DATA*/" in src
src = src.replace("/*DATA*/", "var DUR = " + dur + ";\nvar FIGHT = " + fight + ";")


def uri(name):
    return "data:image/jpeg;base64," + base64.b64encode(open(os.path.join(HERE, "img", name), "rb").read()).decode()


src = re.sub(r"\{\{IMG:([\w.-]+)\}\}", lambda m: uri(m.group(1)), src)
fj = os.path.join(HERE, "img/frames.json")
groups = json.load(open(fj)) if os.path.exists(fj) else {}
for key in re.findall(r"<!--FRAMES:(\w+)-->", src):
    grp = groups.get(key)
    if grp:
        out = ['<p>%s</p>' % grp["note"], '<div class="frames">']
        for f in grp["frames"]:
            out.append('<figure><img alt="%s" src="%s"><figcaption>%s</figcaption></figure>' % (f["alt"], uri(f["file"]), f["caption"]))
        out.append('</div>')
        html = "\n".join(out)
    else:
        html = ""
    src = src.replace("<!--FRAMES:%s-->" % key, html)
open(os.path.join(HERE, "plan.html"), "w", encoding="utf-8").write(src)
print("plan.html", len(src) // 1024, "KB")
