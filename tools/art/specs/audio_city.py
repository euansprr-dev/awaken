"""Recorded city bells. Rebuild: python3 tools/art/specs/audio_city.py.
CC0 source WAVs and provenance live in tools/art/sources/city. No network or playback.
The attack and natural decay are preserved; no oscillator or synthetic reverb layer.
"""
from pathlib import Path
import hashlib
import json
import sys
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audiokit as K

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "tools/art/sources/city"
SOUNDS = ROOT / "src/main/resources/assets/awaken/sounds/city"
HASHES = {
    "3446": "f9f598a9236b709d4560cb9ce2067172a3eac8ec2189a26185c65d990251764a",
    "0292": "2566c1bd762eb7fd2acb0d85fa65a2e6c5ff53fe8cf287d3067a29ea4aff98ed",
    "0294": "587e9f9c33c59bf7cc55c4fad2212803e93c9020eff96a0f8b969a6355f20906",
}


def recorded(source, rate=1.0, seconds=None, peak=-7.5):
    file = SOURCE / (source + ".wav")
    assert hashlib.sha256(file.read_bytes()).hexdigest() == HASHES[source], file
    x = K.mono(K.read_audio(str(file)))
    if seconds is not None:
        x = x[:K.ns(seconds)]
    if rate != 1:
        x = np.interp(np.arange(0, len(x) - 1, rate), np.arange(len(x)), x)
    x = x - np.mean(x)
    x = K.highpass(x, 45, 2)
    fade = min(K.ns(.3), len(x) // 4)
    x[:K.ns(.003)] *= np.linspace(0, 1, K.ns(.003))
    x[-fade:] *= np.linspace(1, 0, fade) ** 2
    # Peak normalization leaves the genuine strike-to-tail dynamics intact.
    x *= 10 ** ((peak - K.true_peak(x)) / 20)
    return x


def bell_hour(seed=0): return recorded("3446")
def bell_district(seed=0): return recorded("3446", 1.06, peak=-9)
def bell_alarm(seed=0): return recorded("3446", peak=-7.5)
def handbell(seed=0): return recorded("0292", seconds=4.8, peak=-11)
def cart_bells(seed=0): return recorded("0294", rate=1 + .025 * (seed % 2), seconds=4.5, peak=-15)


def carillon(name, seed=0):
    # Legacy event ids remain valid; routine bells now come only from the server.
    strikes = {"dawn": 1, "noon": 2, "dusk": 3}[name]
    bell = bell_hour()
    x = np.zeros(len(bell) + K.ns(8) * (strikes - 1))
    for i in range(strikes): x[i * K.ns(8):i * K.ns(8) + len(bell)] += bell
    return x


def build(out): pass


def main():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    files = {"bell_hour": bell_hour(), "bell_district": bell_district(), "bell_alarm": bell_alarm(), "handbell": handbell(),
             "cart_bells1": cart_bells(0), "cart_bells2": cart_bells(1)}
    files.update({"carillon_" + name: carillon(name) for name in ("dawn", "noon", "dusk")})
    report = {}
    for name, x in files.items():
        path = SOUNDS / (name + ".ogg")
        K.write_ogg(str(path), x, 6, "AWAKEN - recorded city bell")
        decoded = K.read_audio(str(path))
        peak = K.true_peak(decoded)
        assert decoded.ndim == 1 and peak < -6, (name, peak)
        assert abs(decoded[0]) < .001 and abs(decoded[-1]) < .001, name
        report[name] = {"seconds": round(len(decoded) / K.SR, 3), "channels": 1,
                        "gated_rms_dbfs": round(K.gated_rms(decoded), 2), "true_peak_dbtp": round(peak, 2),
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        print(name, report[name])
    (SOURCE / "measurements.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__": main()
