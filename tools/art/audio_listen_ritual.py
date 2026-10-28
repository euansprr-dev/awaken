"""Generate a manual, no-autoplay preview of the shipped Ritual assets. Never opens or plays audio."""
from pathlib import Path
import html
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'logs/ritual-audio-preview/index.html'
SOUNDS = ROOT / 'src/main/resources/assets/awaken/sounds/ritual'
NAMES = ['bell','doors','footstep1','footstep2','footstep3','statue_rise','swing','impact',
         'gaze_charge','gaze_fire','gaze_travel','gaze_impact','eyes','pit','rumble','close','hall']
OUT.parent.mkdir(parents=True, exist_ok=True)
rows = ''.join(f'<section><h2>{html.escape(n.replace("_"," "))}</h2><audio controls preload="none" src="{(SOUNDS/(n+".ogg")).as_uri()}"></audio></section>' for n in NAMES)
OUT.write_text('<!doctype html><meta charset="utf-8"><title>Ritual audio review</title><style>body{max-width:760px;margin:40px auto;background:#151820;color:#eee;font:17px system-ui}section{padding:12px;border-top:1px solid #404550}h2{font-size:18px}audio{width:100%}</style><h1>Ritual audio review</h1><p>Manual playback only. These are source files at their encoded level; game gain, distance and guidance ducking apply in the scene. No live spatial listening has been performed.</p>'+rows)
print(OUT)
