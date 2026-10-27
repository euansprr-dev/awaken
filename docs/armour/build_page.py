"""Builds docs/armour/page.html from page.src.html: every src="proof/renders/x.png" becomes a data: URI so the page is one file
(the Artifact viewer only allows images embedded or published beside it).  python3 docs/armour/build_page.py"""
import base64
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "page.src.html"), encoding="utf-8").read()


def inline(m):
    path = os.path.join(HERE, m.group(1))
    data = base64.b64encode(open(path, "rb").read()).decode("ascii")
    return 'src="data:image/png;base64,%s"' % data


out = re.sub(r'src="(proof/renders/[^"]+\.png)"', inline, src)
open(os.path.join(HERE, "page.html"), "w", encoding="utf-8").write(out)
print("page.html", len(out) // 1024, "KB")
