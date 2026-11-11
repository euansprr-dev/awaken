"""Builds the approval page (docs/enchanting/polish/page.html) from page.src.html: the real frames and the mock-up's
art (img/*.jpg, img/*.png) as data URIs, and the draft glyphs (mock/glyphs.json, written by mock/glyphs.py).

    python3 docs/enchanting/polish/build_page.py
"""
import base64
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "page.src.html"), encoding="utf-8").read()
glyphs = open(os.path.join(HERE, "mock/glyphs.json")).read()
assert "/*GLYPHS*/" in src
src = src.replace("/*GLYPHS*/", "var GLYPHS = " + glyphs + ";")


def uri(name):
    kind = "image/png" if name.endswith(".png") else "image/jpeg"
    return "data:" + kind + ";base64," + base64.b64encode(open(os.path.join(HERE, "img", name), "rb").read()).decode()


missing = [n for n in re.findall(r"\{\{IMG:([\w.-]+)\}\}", src) if not os.path.exists(os.path.join(HERE, "img", n))]
assert not missing, missing
src = re.sub(r"\{\{IMG:([\w.-]+)\}\}", lambda m: uri(m.group(1)), src)
open(os.path.join(HERE, "page.html"), "w", encoding="utf-8").write(src)
print("page.html", len(src) // 1024, "KB")
