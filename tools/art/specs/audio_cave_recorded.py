"""Recorded cave sounds (SPEC §34.5): the band's room tone, the breath of air from a way out, stone settling and "the
cave answers" when a heart breaks. Rebuild: python3 tools/art/specs/audio_cave_recorded.py (Python, NumPy, ffmpeg).

CC0 sources from BigSoundBank (Joseph Sardin), listed with their pages in tools/art/sources/delve/README.md. They are
not committed (the cave recording alone is 2.8 MB): fetch them into that folder first; their hashes are asserted. The
events are registered by tools/art/specs/audio_caves.py (merge/delve_sense), which names these files.

  delve/band_room          a loop: 12 s of a real cave's air, its hiss eased, seamed by a crossfade (the game pitches
                           it by band: lower the deeper)
  delve/cave_breath        a loop: 10 s of the same cave, only its lows: air moving in a passage toward a way out
  delve/settle_1..4        the four loudest separate falls of stone, the silence round each trimmed
  delve/cave_answers       a hammer's knock on stone, then an anvil's last strike slowed to 0.6 so it rings low and
                           long, in a convolved cave room (the game tunes it to the ore)
"""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audiokit as K

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "tools/art/sources/delve"
SOUNDS = ROOT / "src/main/resources/assets/awaken/sounds/delve"
HASHES = {
    "1022": "6a6dc3a460ac04616c2d038f04a6416a76821018b5024960bcd32cf0b43350bb",
    "1108": "9b2735c9bf887c28ece6db3181b3300e78783c5937296c0ef7d2cdba70f570d4",
    "2135": "d52d2d8ffd317ac2880d50731cc52c27bc1abc8de6634bbfe26aa6051e620807",
    "3589": "1f8ec376fb1e98b8abea74252420422eaeb3a4c6b47ee46549c346d4d549c9ae",
}


def decode(path):
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-f", "f32le", "-ar", str(K.SR), "-"],
                       capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype="<f4").astype(np.float64)


def recording(source):
    """The source, checked, decoded to mono at the kit's rate, DC and rumble under 30 Hz gone."""
    file = SOURCE / (source + ".mp3")
    assert hashlib.sha256(file.read_bytes()).hexdigest() == HASHES[source], f"{file}: fetch it (README.md)"
    x = decode(file)
    return K.highpass(x - np.mean(x), 30, 2)


def edges(x, attack=.004, release=.25):
    x = x.copy()
    a, r = min(K.ns(attack), len(x) // 4), min(K.ns(release), len(x) // 3)
    x[:a] *= np.linspace(0, 1, a)
    x[-r:] *= np.linspace(1, 0, r) ** 2
    return x


def peak_to(x, db):
    return x * 10 ** ((db - K.true_peak(x)) / 20)


def rms_to(x, db, ceiling=-4.0):
    y = x * 10 ** ((db - K.gated_rms(x)) / 20)
    return peak_to(y, ceiling) if K.true_peak(y) > ceiling else y


def loop(x, start, seconds, fade):
    """A seamless loop: the tail past the loop's end is crossfaded (equal power) into its start."""
    s, n, f = K.ns(start), K.ns(seconds), K.ns(fade)
    seg = x[s:s + n + f].copy()
    t = np.linspace(0, np.pi / 2, f)
    out = seg[:n].copy()
    out[:f] = seg[:f] * np.sin(t) + seg[n:n + f] * np.cos(t)
    return out


def slow(x, rate):
    return np.interp(np.arange(0, len(x) - 1, rate), np.arange(len(x)), x)


def envelope(x, frame=.01):
    n = K.ns(frame)
    m = len(x) // n
    return 20 * np.log10(np.sqrt(np.mean(x[:m * n].reshape(m, n) ** 2, axis=1)) + 1e-9), n


def falls(x, count):
    """The loudest separate falls: each starts out of 0.2 s of quiet and ends once it has been quiet for 0.15 s."""
    db, n = envelope(x)
    found, i = [], 20
    while i < len(db):
        if db[i] > -40 and np.all(db[i - 20:i] < -55):
            j = i
            while j < len(db) and not np.all(db[j:j + 15] < -62) and j - i < 160:
                j += 1
            found.append((float(np.max(db[i:j])), max(0, i - 1) * n, min(len(x), (j + 10) * n)))
            i = j
        i += 1
    found.sort(reverse=True)
    return sorted(found[:count], key=lambda f: f[1])


def band_room():
    x = K.lowpass(recording("2135"), 5200, 2)
    return rms_to(loop(x, 20.0, 12.0, 2.0), -28.0)


def cave_breath():
    x = K.highpass(K.lowpass(recording("2135"), 700, 2), 60, 2)
    return rms_to(loop(x, 44.0, 10.0, 2.0), -28.5)


def settles():
    x = recording("1022")
    return [peak_to(edges(x[a:b], .002, .12), -6.0) for _, a, b in falls(x, 4)]


def cave_answers():
    knock = recording("1108")
    anvil = recording("3589")
    # the anvil's last strike: the loudest rise in its final four seconds, kept to the recording's end
    db, n = envelope(anvil[-K.ns(4):])
    at = len(anvil) - K.ns(4) + int(np.argmax(np.diff(db))) * n
    ring = K.lowpass(slow(anvil[at - K.ns(.01):], .6), 5000, 2)
    dry = np.zeros(K.ns(.35) + len(ring))
    dry[:len(knock)] += peak_to(knock, -4) * .7
    dry[K.ns(.35):] += peak_to(ring, -4)
    room = K.mono(K.convolve(dry, K.reverb_ir(t60=3.2, predelay=.03, bright=.35, seed=7)))[:len(dry) + K.ns(2.5)]
    wet = np.zeros(len(room))
    wet[:len(dry)] += dry * .65
    wet += room / (np.max(np.abs(room)) + 1e-9) * .5
    return peak_to(edges(wet, .002, 1.2), -6.0)


def main():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    files = {"band_room": band_room(), "cave_breath": cave_breath(), "cave_answers": cave_answers()}
    files.update({f"settle_{k + 1}": s for k, s in enumerate(settles())})
    assert len(files) == 7, sorted(files)
    report = {}
    for name, x in files.items():
        path = SOUNDS / (name + ".ogg")
        K.write_ogg(str(path), x, 4, "AWAKEN - recorded cave sound")
        y = decode(path)
        peak = K.true_peak(y)
        assert peak < -3, (name, peak)
        assert path.stat().st_size < 400_000, (name, path.stat().st_size)
        report[name] = {"seconds": round(len(y) / K.SR, 3), "channels": 1, "kb": path.stat().st_size // 1024,
                        "gated_rms_dbfs": round(K.gated_rms(y), 2), "true_peak_dbtp": round(peak, 2),
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        print(name, report[name])
    (SOURCE / "measurements.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
