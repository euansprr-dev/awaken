"""Builds every texture from tools/art/specs/*.py. Each spec module exposes build(out)."""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
sys.path.insert(0, HERE)
from pixelart import Out  # noqa: E402


def main(only=None):
    out = Out(ROOT)
    specs = sorted(f for f in os.listdir(os.path.join(HERE, "specs")) if f.endswith(".py"))
    for f in specs:
        name = f[:-3]
        if only and name not in only:
            continue
        if not only and name.startswith("_"):
            continue
        spec = importlib.util.spec_from_file_location("spec_" + name, os.path.join(HERE, "specs", f))
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        before = len(out.written)
        mod.build(out)
        print(f"{name}: {len(out.written) - before} textures")


if __name__ == "__main__":
    main(sys.argv[1:] or None)
