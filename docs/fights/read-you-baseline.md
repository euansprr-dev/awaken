# Fights That Read You: the baseline (stage 0)

Measured on 2026-11-09 before any of the mind is built, on the branch with the aimed blows (SPEC §47.15). Each stage is
compared with this. Plan: docs/fights/read-you.html. To make the table again:

```bash
tools/gradle.sh 590 runGameTestServer -Ptests=fights_record_
python3 tools/fights/record_table.py logs/gradle-<stamp>-runGameTestServer.log --gates
```

## The recorder: six kinds of player

72 seconds each, fixed seeds, a rank E player kept at full health. play = mid-move, open = in an opening, side = sideways.

```
player               fights begun rep% | play open idle walk side |    gap   near ranged   area  combo behind summon |  LIGHT  HEAVY PERILO   GRAB   NONE
behind                    6   211    9 |   77   22    1    1    0 |      0     45      4     31     33     11      2 |     57     23     14      0      6
circle                    6   164   27 |   73   21    2    4    0 |      1     27      7     44     30      0      2 |     43     29     15      0     13
guard                     6   180   15 |   72   24    3    1    0 |      3     38     16     41     28      1      2 |     43     29     17      1      9
kite                      6   146   23 |   79   19    1    0    0 |     36      5     26     35     10      0      3 |     16     40     23     10     10
retreat                   6   115   56 |   70   18    2    8    2 |     17      3     44     53      4      0      3 |     17     19     44      2     18
stand                     6   177   12 |   77   21    1    0    0 |      6     32     15     40     25      4      2 |     46     29     15      1      8
ALL                      36   993   21 |   75   21    2    2    1 |      9     28     17     40     24      3      2 |     40     28     20      2     10
```

## Per boss

```
boss                 fights begun rep% | play open idle walk side |    gap   near ranged   area  combo behind summon |  LIGHT  HEAVY PERILO   GRAB   NONE
bell_warden               6   161   22 |   77   20    3    0    0 |      5     19     16     53     17      2      4 |     34     40     18      0      7
ember_tyrant              6   160   20 |   77   21    2    0    0 |      7     38     24     59      7      8      4 |     27     26     33      7      8
gearwright_colossus       6   157   25 |   71   21    2    5    0 |      6     17     31     44     36      3      4 |     38     33     15      6      8
glass_serpent             6   183   19 |   75   19    2    3    2 |     11      0      4     37     45      3      0 |     48     33     16      0      3
hive_regent               6   160   21 |   73   22    0    4    1 |     15     41     17     11      0      4      4 |     34     15     28      0     23
sky_leviathan             6   172   20 |   75   22    1    1    1 |      9     53     10     34     34      0      0 |     53     23     10      0     13
ALL                      36   993   21 |   75   21    2    2    1 |      9     28     17     40     24      3      2 |     40     28     20      2     10
```

## The two pilots against the plan's gates

```
hive_regent:
  same move twice in a row, worst fight    45.5%   gate 10%
  mid-move against someone standing still  76.9%   gate 50-60%
  walking or stepping                       5.0%   gate 20%
  guard-beaters: turtling / standing       39.3% / 13.8%   gate double
gearwright_colossus:
  same move twice in a row, worst fight    47.1%   gate 10%
  mid-move against someone standing still  74.3%   gate 50-60%
  walking or stepping                       5.1%   gate 20%
  guard-beaters: turtling / standing       16.7% / 7.1%   gate double
```

## A pack of 12 (gate creatures of 12 kinds, on one player standing still, 30 s)

Most winding up at once: **9**. On average 2.73 at any moment, and three or more at once for 318 of 600 ticks.

## Fight length (tools/sim/combat_sim.py, 100 seeds, as built; the sim doesn't know the aim yet)

| Lord | profile | wins | median time to kill, s |
|---|---|---|---|
| Hive Regent (E) | tank | 19% | 91 |
| | dodger | 99% | 88 |
| | parrier | 97% | 83 |
| | expert | 100% | 69 |
| Gearwright Colossus (D) | tank | 0% | none |
| | dodger | 87% | 108 |
| | parrier | 89% | 79 |
| | expert | 100% | 68 |

## After stage 1 (the mind on the two pilots, 2026-11-09)

Same recorder, same seeds, from the full CI run on cfef690ca (42568231254). A run differs from the last by a point or two. Hive Regent / Gearwright Colossus:

| | baseline | stage 1 | gate |
|---|---|---|---|
| same move twice, worst of the six players | 45.5% / 47.1% | 7.1% / 0% | at most 10% |
| guard-beaters (PERILOUS, GRAB), turtling against standing | 39.3 v 13.8 / 16.7 v 7.1 | 46.2 v 21.9 / 22.2 v 11.1 | at least double |
| drinks in reach answered before they finish (28 drinks each, near and further off) | none | 79% / 79% | at least 60% |
| mid-move against someone standing still | 76.9% / 74.3% | 76.1% / 73.4% | 50-60%, stage 2 |
| walking or stepping | 5.0% / 5.1% | 12.3% / 8.1% | 20%, stage 2 |
