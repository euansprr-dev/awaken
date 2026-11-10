"""Builds the approval page (docs/weapon-feel/plan.html) from plan.src.html: the game's own blade keys for the player
(harness/keys.json, written by harness/export_keys.py) and the film's frames (frames/frames.json: file, caption)."""
import base64, json, os
HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "plan.src.html")).read()
keys = open(os.path.join(HERE, "harness/keys.json")).read()
assert "/*KEYS*/" in src and "<!--FRAMES-->" in src
src = src.replace("/*KEYS*/", "var KEYS = " + keys + ";")
fj = os.path.join(HERE, "frames/frames.json")
if os.path.exists(fj):
    out = []
    for group in json.load(open(fj)):
        out.append('<h3>%s</h3>' % group["title"])
        if group.get("note"):
            out.append('<p>%s</p>' % group["note"])
        out.append('<div class="frames">')
        for f in group["frames"]:
            b = base64.b64encode(open(os.path.join(HERE, "frames", f["file"]), "rb").read()).decode()
            out.append('<figure><img alt="%s" src="data:image/jpeg;base64,%s"><figcaption>%s</figcaption></figure>' % (f["alt"], b, f["caption"]))
        out.append('</div>')
    frames = "\n".join(out)
else:
    frames = '<p class="note">The film has not come back yet.</p>'
src = src.replace("<!--FRAMES-->", frames)
open(os.path.join(HERE, "plan.html"), "w").write(src)
print("plan.html", len(src) // 1024, "KB")
