"""Disposable: every arms sound file's ding (the powers plan's checker), where its energy sits, how long it is."""
import sys, os, glob, collections
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ding_check as D
files = sorted(glob.glob(os.path.join(sys.argv[1], "src/main/resources/assets/awaken/sounds/arms/*.ogg")))
by = collections.OrderedDict()
for f in files:
    name = os.path.basename(f).rsplit("_", 1)[0].replace("arms_", "")
    y = D.decode(f)
    r = D.check(y)
    spec = np.abs(np.fft.rfft(y)) ** 2
    hz = np.fft.rfftfreq(len(y), 1 / D.SR)
    tot = spec.sum() + 1e-12
    low = spec[hz < 300].sum() / tot
    mid = spec[(hz >= 300) & (hz < 1000)].sum() / tot
    pres = spec[(hz >= 1000) & (hz < 6000)].sum() / tot
    by.setdefault(name, []).append((r["ding"], r["hz"], r["stand"], low, mid, pres, len(y) / D.SR))
print("%-16s %2s %5s %10s %6s %6s %6s %6s" % ("event", "n", "dings", "ding Hz", "<300", "300-1k", "1-6k", "secs"))
dn = 0; tot = 0
for k, v in by.items():
    d = [x for x in v if x[0]]
    dn += len(d); tot += len(v)
    print("%-16s %2d %5d %10s %5.0f%% %5.0f%% %5.0f%% %6.2f" % (k, len(v), len(d), "/".join(str(int(x[1])) for x in d) or "-",
          100 * np.mean([x[3] for x in v]), 100 * np.mean([x[4] for x in v]), 100 * np.mean([x[5] for x in v]), np.mean([x[6] for x in v])))
print(dn, "of", tot, "files have a held narrow tone above 800 Hz")
