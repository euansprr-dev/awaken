"""Build a quiet, local audition page. No playback, server, uploads or browser launch.
Run: python3 tools/art/audio_listen_city.py [output directory, default logs/city-audio-preview]
The files are current shipping recordings; the page does not simulate game acoustics.
"""
from pathlib import Path
import html
import json
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]

def main():
    out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / "logs/city-audio-preview"
    out.mkdir(parents=True, exist_ok=True)
    report = json.loads((ROOT / "tools/art/sources/city/measurements.json").read_text())
    rows = []
    for key, title in [("bell_hour", "Tower: one recorded stroke"), ("bell_district", "District tower"),
                       ("bell_alarm", "Alarm stroke: same real tower, urgency from the peal"),
                       ("handbell", "Watch and crier"), ("cart_bells1", "Cart harness"),
                       ("carillon_noon", "Noon spacing: two strokes, eight seconds apart")]:
        shutil.copy2(ROOT / ("src/main/resources/assets/awaken/sounds/city/" + key + ".ogg"), out)
        m = report[key]
        rows.append(f'<article><h2>{html.escape(title)}</h2><audio controls preload="none" src="{key}.ogg"></audio>'
                    f'<p>{m["seconds"]} s · {m["true_peak_dbtp"]} dBTP · mono</p></article>')
    page = '<!doctype html><meta charset="utf-8"><title>AWAKEN city sound</title>'
    page += '<style>body{font:17px system-ui;max-width:800px;margin:48px auto;background:#15191b;color:#eee}article{margin:32px 0;padding:24px;background:#242b2e}h2{font-size:20px}p{line-height:1.55}</style>'
    page += '<h1>City bell audition</h1><p>Press Play to listen. Nothing starts automatically. These are the shipping files, before the game’s distance, indoor attenuation and Ambient slider.</p>'
    page += '<p>Routine city bells: dawn once, noon twice, dusk three times, from one staffed tower. No hourly chorus or extra tune. The market and birds now leave much longer quiet gaps.</p>'
    page += ''.join(rows)
    page += '<p>Sources: BigSoundBank CC0 recordings by Joseph SARDIN &amp; Axeline T. and GlaneurDeSons. Full provenance accompanies the source WAVs. Offline technical checks passed; in-game listening has not been performed.</p>'
    (out / "index.html").write_text(page)
    print(out / "index.html")

if __name__ == "__main__": main()
