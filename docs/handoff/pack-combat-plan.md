# pack/combat-plan: the weapons and combat plan ("Arms"), hand-off (cloud session, 2026-10-25)

Branch `pack/combat-plan`, from `build/awaken-v1` at `d12ea34`. **Plan only: no production code, no SPEC change.**
Not merged into build/awaken-v1: the orchestrator does that and publishes the approval page for Euan.

## What's here
- `docs/combat/PLAN.md`: the plan, in the order Euan's process asks for (understanding and success contract → verified
  audit and diagnosis → alternatives and recommendation → behaviour and architecture → demonstrations → evaluation
  and delivery → decisions → research notes with citations and the licence table).
- `docs/combat/plan.html`: the self-contained approval page (inline CSS and SVG, light and dark, phone width; the plan
  pages' own type and tokens, Google Fonts with fallbacks like the other plan pages). Checked once in Chromium at 1280
  and 400 px, light and dark: no horizontal overflow, no script errors.
- `docs/combat/audit/`: the five full reports behind the plan (read-only snapshots at `d12ea34`):
  `audit-player-combat.md` (damage pipeline, every listener, feel, HUD, the key table, networking),
  `audit-enemies-loot.md` (all 41 bosses with every attack's wind-up and damage factor, the diamond-sword repro
  arithmetic, weapons, drops, discovery), `audit-pipeline.md` (tools/art, item models, PAL, Hands, trails, audio),
  `api-verify.md` (26.3 jar + NeoForge 26.3.0.95 sources + PAL 1.2.8, read with JDK 21 `javap`), and
  `research-external.md` (Better Combat, Epic Fight, Combat Roll, Simply Swords, Shield Expansion, ReArm, vanilla's
  combat components, Sekiro, Elden Ring, Smash, Celeste, Minecraft Dungeons; licences read from the LICENSE files).

## The plan in ten lines
1. Root cause: a Lord's basic melee is vanilla's untold swing (~74% of the damage that kills you), and the player has
   no defensive verb. Diamond sword vs the D Gearwright Colossus: 103 hits / 61.8 s to kill it; you die in ~6 s at
   11% of its health (numbers from `mobs/MobScaling.java`, `progression/Progression.java`, the bestiary).
2. Recommended: a new module **Arms** (`dev.awaken.arms`), staged; the targeted repair ships inside its first slice.
3. Three verbs: left mouse (tap strike chain, hold heavy, Art at full Resonance), right mouse (deflect in the first 4
   ticks, then block), sprint + back/side/none (dodge, 5 evade ticks). No new key.
4. Every enemy attack gets a class (LIGHT, HEAVY, PERILOUS, GRAB) with its own tell and sound; enemies get posture and
   Break; deflect grace (≤ 3 ticks) keeps deflects fair at 150 ms.
5. One feel layer (`core.api.CombatFeedback`, from powers' CombatFeel), one camera voice (Feel Director), one comfort
   source; hit-stop by weight; a two-edge blade trail; a synthesised combat sound palette.
6. Animation through Hands (first person; its scope grows to weapons) and PAL (third person) with an ArmPose fallback.
7. Sixteen archetypes; ~290 distinct 3D weapons (96 forged, ~120 named, 41 Lord-forged, ~30 legendary); Marks, Arts,
   mastery; 36 Weaves fusing a power cast with a weapon opening; attunement at the Forge.
8. A generated content pipeline (`arms_catalog/models/icons/moves/sounds/preview`) on `fauna_rig.py`, `ladder3d.py`,
   `power_casts.py` and audiokit, with per-weapon checks mirrored as GameTests.
9. Phases S0–S10, ~45–70 builder hours; the slice (S0–S2, 11–16 h) is the only part committed before Euan's go/no-go.
10. No outside code or art is copied (AWAKEN is All Rights Reserved; the repo is public).

## Decisions waiting for Euan (PLAN §7)
Dodge i-frames (amends SPEC's "no invincibility frames anywhere"), the dodge input, guard on right mouse, Hands growing
to weapons, vanilla swords' look, PAL optional or required, weapon item levels, Lord-forged weapons in hand on the kill,
the order (S0 and S1 first).

## Found while auditing (for their owners; not fixed here)
- **mobs:** three Lords hold vanilla weapons whose damage is very likely added and then scaled (Ember Tyrant at D:
  13.4 → 37.6 ATTACK_DAMAGE, a one-shot Magma Fist). SUSPECTED; a 10-line GameTest settles it. Plan step S0.
- **mobs/gates:** a gate Lord drops two Lord's Hearts and rolls two Power Cores on two pity timers
  (`mobs/MobLoot.java:123-143`, `gates/run/Loot.java:89-99`); Insight counted twice per Lord death. SUSPECTED.
- **body:** BodyCamera's 32° roll dip and slide roll ignore reduced motion (`body/client/BodyCamera.java:41-89`).
- **powers/body:** Dash is unreachable from Left Alt once the Tether tier owns it (`ClientCasting.java:51`).
- **gear:** the `GearItems` header says "28 items, E to B" but registers all 6 tiers × 7 pieces.
- **gates:** `key.awaken.satchel` lang line has no KeyMapping.
- No key-conflict audit exists against the pack's mods (Iris, Simple Voice Chat, Xaero's): plan step S10.

## How it was verified
Read-only. No Gradle run (the container has JDK 21; the project needs 25), no game client. 26.3 APIs were checked in
Mojang's unobfuscated 26.3 client jar and NeoForge's sources, downloaded to a scratch folder outside the repo and never
executed; repo code that is green on CI was the strongest evidence. Nothing in `docs/combat/plan.html` was measured in
game: every "proposed" number is a starting point for the feel session.

## Next
Euan reads the page and answers PLAN §7. On a yes: S0 (mobs fix) and S1 (the feel spike) on a new `pack/arms` branch,
one builder (Opus, effort xhigh: it is feel work), ending in feel session G1 on Euan's Mac.

---------------------------------------------------------------------------------------------------------------------

## Euan's brief, word for word

Massively level up the weapons and combat system for variety, animations, rare drops, and hundreds of different REALLY cool weapons and gameplay styles. Things that merge with the magic, etc. But in a way that can still be discovered natively over time as well, or intentionally somehow. When I say massively, I really mean like massively, massively, like the most incredible, like top one weapons and combat system for MC. Like it should genuinely nearly feel like a different game again, like not just in terms of weapons and different sizes and different shapes and everything, and just not flat 2D things, that's for sure. It has to be like 3D. Very cool and unique weapons that all have different like abilities. Some are like more normal, and they have like custom animations as well, because the usual just like MC hits with like a sword look very boring. Here I want custom animations as well. And I mean, obviously, if we can find any other mods that we can get inspired by and use their code and stuff to like the same way we've been doing some other stuff, that's great. And then I also want like parrying, so that like, because right now it's like in a boss fight, for example, it's going to be very hard to survive if you're using a diamond sword. But if we add to all weapons some kind of parrying system, so that you can actually like block and par and stuff, different things like that, and also a dodging system, that would be really, really cool, because right now it's like the movement is great, but the combat feeling, I think, can be massively improved. Like the feeling of hitting something, the feeling of getting hit, the feeling of blocking, the feeling of the sounds of all of this, and really basically making fights really epic. I mean, obviously, like with a boss, it's going to be very epic and have music and everything, but for main stuff, I still want it to feel extremely satisfying. Like similar to kind of when you're playing Diablo, you know, it's very satisfying. Or even MC Dungeons is very satisfying. But I want to take it to the next level where we really build a very in-depth but intuitive. Like I don't want it to become super complex with 50 keybinds and everything. I more so want it to become extremely satisfying with maybe automatic combos, or obviously not all automatic. There has to be some kind of skill ceiling and skill gap as well that you can learn over time. I think that would be really cool. But overall, I think you get what I'm trying to say. Like really make it as good as it can be and really extrapolate this to the highest level, that like almost magical feeling. And this goes for every single other thing we build. Like that's the kind of bar I want, is like the feeling of, oh shit, I didn't even know this was possible. Like I want people to really feel like this is magical because it hasn't been done before. And that's the level and the bar that I want here. the process below is more so for single features, but I'd like basically extrapolate the process below into how we're going to build this and then like give all of this to a cloud agent. So it has this entire prompt that I just said, like word for word, plus the instructions below, so that it actually starts this off itself, but basically send this to a cloud agent.

## Euan's process instructions, word for word

Treat my explanation above as the brief for this assignment. Act as a principal engineer and product architect responsible for the feature's actual usefulness, correctness, reliability, and integration with the rest of the product.
My explanation may be informal, repetitive, incomplete, or mixed with possible solutions. Your first job is to understand the outcome I am reaching for—not merely implement the technical ideas I happened to mention.
Be ambitious about the improvement and disciplined about the mechanism. Diagnose what prevents the desired experience, explore meaningful alternatives, and produce a concrete, testable plan before production implementation.
Do not default to a rewrite, a new model, more embeddings, more agents, more settings, or more automation. Any of those may help, but they must earn their place through evidence.
1. Turn my explanation into a precise improvement brief
Extract and distinguish:
- The job: what I am trying to accomplish, in what working context.
- The pain: what currently fails, interrupts me, or forces me to do work the feature should handle.
- The desired experience: what should become easier, more useful, or newly possible.
- My proposed solutions: ideas to investigate, not automatically the correct architecture.
- Hard constraints and preferences: what must be preserved versus what is negotiable.
- Unknowns: what you need to verify rather than assume.
Preserve the substance of my intent. Do not quietly replace a difficult requirement with an easier adjacent problem. For example, "help me find the exact material that advances this section" is not equivalent to "return more documents about the topic."
Translate words such as "magical," "smarter," "instant," and "production-grade" into observable behavior. Define what a strong result, a weak result, and an unacceptable result would look like.
State your interpretation concisely before developing the plan. Resolve uncertainties through inspection where possible. Ask only when the answer materially changes scope, safety, or the core direction; otherwise label a reasonable assumption and continue.
2. Permission boundary: investigate, demonstrate, then stop
This assignment authorizes a plan—not production implementation.
Inspect authorized code, documentation, screenshots, representative data, and existing tests. Prefer read-only snapshots and sanitized fixtures. You may create isolated planning artifacts or disposable diagnostic harnesses when they can run safely without changing production behavior.
Do not alter production code or data, run migrations, bulk reprocess material, install substantial dependencies, start paid jobs, expose secrets, deploy, or publish without approval. Do not reset branches or disturb unrelated or concurrent changes.
Do not run tests against live services or writable production data merely because a test command exists. Inspect side effects first. Obtain approval for necessary paid or consequential experiments.
Discover actual paths, instructions, permissions, and available tools. If access is missing, explain what could and could not be inspected. Never invent file paths, trace results, test outcomes, or implementation details.
3. Audit the real feature and its dependencies
Inspect the working implementation, not just its description or earlier plans. Trace the feature's entry points, core components, data sources, shared services, persistence, background jobs, and outputs.
Identify which capabilities are implemented, partially implemented, planned, unused, duplicated, or unreliable.
Check whether useful information already exists but is not reaching the decision that needs it. Inspect ignored fields, incompatible representations, stale caches, legacy routes, conflicting instructions, truncated inputs, and tools that bypass newer infrastructure.
Also inspect the product's strongest adjacent workflows. Reuse their established object model, actions, permission rules, and interaction conventions where appropriate.
Produce a concise map:
Component or stage → what it actually does → evidence → consequence → keep / extend / replace / investigate.
Distinguish verified findings from suspicions. A stored field does not prove it is consumed; an impressive output does not establish why it occurred.
4. Reproduce the disappointment end to end
Build a small set of representative cases from my examples and authorized real usage. Include a disappointing result, a strong existing result, an ordinary case, an ambiguous case, and a meaningful failure or missing-data case.
For each, capture the relevant path:
Input and active context → routing → data acquisition → processing or retrieval → decisions → returned payload → displayed or applied result → saved state.
Adapt that path to the actual feature rather than forcing every system into a retrieval architecture.
Determine where the useful information or intended behavior first disappears. Inspect what was available, what was selected, what was discarded, what was changed, and what reached the user.
Preserve reproducible fixtures with sensitive information removed. If the required traces do not exist, propose the smallest safe diagnostic instrumentation needed. Do not pretend a hypothesis was reproduced.
A feature can retrieve the correct information and still fail because it organizes, explains, or applies it badly. Inspect the final usefulness, not just intermediate success.
5. Define success before choosing the solution
Create an outcome contract covering:
Usefulness: what task becomes easier or what decision becomes better.
Correctness: what must be accurate, source-grounded, consistent, or complete.
Effort: what searching, rereading, reformulating, correcting, or manual coordination should disappear.
Responsiveness: when the first useful result should appear and what remains usable while work continues.
Control: what happens automatically, what is suggested, and what requires approval.
Reliability: what the system does when it cannot produce a strong result.
Cost: relevant setup, recurring, and per-operation limits.
Establish baselines before inventing precise improvement targets. Propose thresholds and explain their rationale. Keep measurements such as latency, task completion, manual correction, source accuracy, and user preference distinct.
List non-goals. Identify working behavior that must not regress.
Do not shift effort from one form to another and call it a win. Fewer clicks with more prompting, more reading, or harder recovery can be worse.
6. Diagnose root causes, not just symptoms
Build a prioritized hypothesis table:
Symptom → possible cause → supporting evidence → disconfirming evidence → smallest test → expected impact if fixed.
Consider missing data, incorrect intent, insufficient context, wrong granularity, poor ranking, bad grouping, faulty business rules, stale state, weak validation, processing limits, and presentation-to-action mismatches where relevant.
Separate a source-data problem from a processing problem, an infrastructure problem from a product-policy problem, and an isolated defect from an architectural limitation.
Do not confidently attribute a failure to a model or algorithm simply because that component is fashionable to replace. Likewise, do not assume more context improves a decision if the relevant context is already present but ignored or poorly structured.
Fixes should address demonstrated causes. Clearly label the remaining causal hypotheses and how they would be tested.
7. Research the important unresolved decisions
Research current primary documentation, original research, and relevant engineering guidance when it helps resolve a concrete uncertainty.
For each significant externally supported claim, record:
Claim → source and date → applicability → limitations → architectural implication → validation required here.
Verify current capabilities, API behavior, provider support, prices, limits, and privacy terms before relying on them. Separate vendor claims from measured performance in this product.
Investigate alternative approaches, including simpler ones. Do not attach a bibliography to a design you already decided to build.
Avoid research for its own sake. State which open question each investigation answers and stop when the decision is sufficiently supported or requires an experiment rather than more reading.
8. Explore improvements beyond my initial ideas
Develop a small number of materially different approaches. For a substantial change, compare:
Targeted repair: correct or connect the existing system with minimal disruption.
Structural improvement: change the relevant contracts, representation, or decision pipeline while reusing the product's foundations.
More ambitious approach: only where it provides a meaningful benefit the simpler options cannot deliver.
Look for opportunities to eliminate a step, infer safe defaults, prepare useful information earlier, reuse prior work, improve the output unit, or give the user a better decision instead of more information.
Compare expected quality, friction, reliability, latency, cost, complexity, data requirements, migration risk, and maintenance burden.
Recommend one approach. Explain why the alternatives lose for this context and what evidence would change your recommendation. Combine approaches only when the combination has a clear purpose.
The most advanced solution is not necessarily the one with the most components. Protect the desired experience, not a preferred technology.
9. Specify the behavior as a contract
For the recommended design, define:
Trigger → required inputs and context → processing → validated output → permitted actions → persistence → failure/recovery.
Make context explicit: active object and version, selected section, task intent, workspace/client/project scope, user instructions, relevant history, and permissions where applicable.
Define what takes priority when these conflict. Specify when context refreshes and when it must remain fixed so the user can inspect or use a result.
Give the output a clear purpose and shape. It might be an exact fact, a useful passage group, an executable plan, a ranked set, a media segment, a staged change, or a completed operation. Do not return a generic paragraph when the workflow needs an actionable object.
Define invariants: properties that must remain true across every path. Include negative requirements such as no unauthorized mutation, no source misattribution, and no result applied to the wrong object.
Specify low-confidence, partial, and no-result behavior as deliberately as success.
10. Design data, state, and lifecycle explicitly
Map proposed changes onto real models and storage boundaries. Separate canonical user data from indexes, caches, summaries, analyses, and other derived representations.
Preserve stable identity, source references, attribution, versions, ownership, and edit history where required. Explain schema additions, compatibility, and whether existing records need backfilling.
Define creation, update, invalidation, archive, deletion, rebuild, and recovery behavior. A cache or index must not become an undocumented competing source of truth.
Handle concurrent windows, overlapping requests, changing selections, edits during processing, and results arriving after the user moves on. Bind work to the correct input snapshot; discard or revalidate stale results before applying them.
Explain transaction boundaries, idempotency, conflict handling, and reconciliation for operations that mutate state. Do not promise exactly-once effects merely because a request is retried carefully.
Keep one-time enrichment distinct from incremental updates. Do not reprocess an entire library when a targeted change is sufficient. Deleted or excluded information must not reappear during rebuilding or sync.
11. Apply AI and retrieval discipline where relevant
If this feature uses AI, recommendations, retrieval, or automation, address the following. Otherwise mark this section not applicable; do not add AI to satisfy the prompt.
Task decomposition: identify which work is deterministic, which needs retrieval, and which genuinely needs model judgment. Explain what happens at ingestion, request time, background refresh, and user interaction.
Information selection: use the right granularity and scope. Preserve neighboring context, dates, speakers, negation, and qualifications. More results are not automatically more helpful results.
Retrieval policy: distinguish exact lookup, exploratory recommendations, multi-source synthesis, and exhaustive enumeration. A top-ranked shortlist cannot establish that nothing else exists. Show partial coverage honestly.
Grounding: separate original words, extracted facts, user beliefs, interpretations, and new proposals. Labels and explanations must be supported by the actual evidence or decision record. Do not invent a persuasive justification after a weak selection.
Prompt and tool contracts: define inputs, bounded outputs, available actions, source handling, and validation. Inspect conflicting instructions and redundant tools. Schema-valid output is not enough; check its substance.
Uncertainty: model scores are not automatically calibrated probabilities. Allow abstention, partial results, alternative interpretations, and useful fallback behavior without pretending certainty.
Learning: distinguish explicit preference, user approval, actual usage, and measured outcome. A click does not prove correctness. Keep learning scoped, inspectable, reversible, and resistant to reinforcing its own mistakes.
Adaptability: version important prompts, models, indexes, and policies. Evaluate upgrades before switching. Do not assume a newer model improves this workload automatically.
12. Make the improvement fit the user's working moment
Even for a backend-focused assignment, describe how the changed behavior becomes useful at the surface.
Show what the user receives, why it is timely, how much they must inspect, what they can do with it, and how they correct it. Prefer reuse of existing controls and actions over another destination, dashboard, or assistant.
For recommendations, distinguish "related to this topic" from "helps accomplish this specific job now." Explain how the system avoids duplicates, irrelevant detail, excessive result volume, and forced recommendations when nothing is strong enough.
Preserve authorship and intent. Helping a user think, retrieving their material, drafting on request, and changing their work automatically are different permissions.
Do not disrupt an active task with background updates, reorder results while they are being inspected, or require repeated explanation of context the app already possesses.
If a visible change is necessary, include a small annotated mockup or interaction storyboard. Do not turn a system-improvement request into an unrelated visual redesign.
13. Engineer graceful failure and recovery
Build a feature-specific failure register. Consider relevant cases involving:
- Missing, malformed, contradictory, duplicated, outdated, or unusually large inputs.
- Ambiguous requests, wrong scope, partial indexes, unsupported content, and unavailable dependencies.
- Slow networks, rate limits, timeouts, provider errors, expired credentials, and offline operation.
- Cancellation, partial completion, repeated input, duplicate jobs, retries, app restarts, and interrupted writes.
- Multiple users/windows, concurrent edits, deleted sources, stale caches, and ordering conflicts.
For each important case, specify detection, containment, preserved state, user-visible behavior, recovery, and a regression test.
Prioritize by severity, likelihood, detectability, and reversibility. Scale the analysis to the actual feature; do not perform an irrelevant enterprise checklist.
Keep failure distinct from a legitimate empty result. Never silently replace a failed capability with an inferior one and label it equivalent.
Useful degradation beats either confident fabrication or making the entire feature unusable.
14. Protect privacy, ownership, and action boundaries
Apply authorization before private information reaches retrieval, ranking, external processing, or the user. Enforce workspace/client/user boundaries in code, not only through prompt instructions.
Define where sensitive content can be processed, what is logged, how long derived data persists, and what happens on revocation or deletion. Keep credentials out of prompts, traces, fixtures, and reports.
Treat external content as data, not instructions. Where AI can act, give it only the actions and scope needed; separate read-only assistance from changes and external side effects.
Consider caches, search snippets, thumbnails, historical records, background jobs, and exports in permission enforcement. A restriction must not be bypassed because a result was prepared earlier.
Preserve review, confirmation, undo, and auditability proportional to the consequence of an action.
15. Make performance, cost, and observability concrete
Measure current behavior before promising a target. Account for the whole experience, not just a fast inner function.
Specify relevant latency budgets, including typical and slow cases, cold and warm paths, first useful result, and complete result. Identify what can be cached, prefetched, batched, computed locally, or prepared incrementally without wasting resources or violating privacy.
Cache keys and invalidation must account for relevant source versions, scope, permissions, context, preferences, and policy changes—not just the text of a query.
Estimate initial processing and recurring costs from actual workload assumptions. Include retries, storage, transfer, background work, and operational maintenance where applicable. State the budget and stop conditions for experiments.
Propose enough observability to locate a failure by stage without collecting unnecessary private content. Correlate a result with the inputs, versions, candidate decisions, validation, timing, and actions needed to reproduce it.
Keep instrumentation proportionate. Do not introduce a separate infrastructure project unless the problem justifies it.
16. Evaluate improvement before scaling it
Create the evaluation approach before tuning the solution. Use representative real cases, difficult negatives, and clearly labeled synthetic edge cases. Preserve a held-out set for validation.
For each case, record the input/context, desired behavior, acceptable alternatives, unacceptable behavior, and how success will be judged. Do not invent objective ground truth for inherently subjective judgments.
Compare the current baseline, the smallest viable fix, and the recommended approach on the same cases. Isolate major additions where possible so we know what earned the improvement.
Test individual stages and complete user workflows. Correct intermediate output is not enough if the final result still requires substantial manual work.
Measure relevant correctness, source fidelity, useful coverage, manual correction, task effort, latency, cost, and regression rates. For variable model behavior, test repeated runs and report meaningful variation rather than selecting the best example.
Use human review where creative usefulness or intent matters. A model grading its own response is not the only quality gate. Distinguish actual user testing from an engineer's or agent's walkthrough.
Set acceptance gates before rollout. Include important cases the current feature already handles well. Maintain regression coverage for every confirmed failure that motivated the change.
The result must be better in use, not merely easier to praise in a demo.
17. Show the proposed behavior concretely
Provide before-and-after walkthroughs using the same inputs. For a substantial change, include an ordinary case, the disappointing case I described, a difficult or ambiguous case, and a missing-data or dependency-failure case.
For each, show:
What I do → what the system receives → what it changes about the processing → what I receive → what I do next → why this is less work.
Use real inspected records when authorized. Label illustrative outputs, hypothetical measurements, and unimplemented behavior explicitly.
Include a clear current/proposed system-flow diagram or equivalent structured map. Show component responsibilities, decision points, data movement, persistent state, and recovery boundaries. Provide representative input/output contracts where they clarify the design.
Where a small isolated proof of concept can resolve uncertainty, propose it or run it only within the allowed safety and cost boundary. State what the demonstration does and does not validate.
Do not claim that a plausible sample answer proves the retrieval, reasoning, or backend implementation can produce it reliably.
18. Plan a staged, reversible implementation
Identify actual affected components and interfaces. Preserve compatibility with existing clients, tools, shared services, saved records, and expert workflows.
Prefer a narrow vertical improvement first:
Reproduce and measure → fix the highest-leverage cause → demonstrate the complete improved path → validate → expand only where justified.
Separate core requirements from optional enhancements and later experiments. Do not make the first useful improvement depend on rebuilding the entire application.
Include feature flags, versioned derived data, dry runs, migration/backfill checks, staged rollout, and rollback where appropriate. Define what happens to in-flight work and previously saved results during the transition.
Keep the working path available until replacement gates pass. Mark go/no-go checkpoints and concrete rollback triggers.
Estimate effort with assumptions and uncertainty. Avoid false precision. Explain which discoveries could change the scope.
19. Deliver an approvable plan—not a generic essay
Return the relevant findings in this order:
1. Understanding and success contract: what I actually need, what is not working, constraints, non-goals, and how improvement will be recognized.
2. Verified audit and diagnosis: real evidence, root-cause findings, unresolved hypotheses, and existing capabilities to reuse.
3. Alternatives and recommendation: meaningful approaches, tradeoffs, overlooked opportunities, and why the chosen design fits this product.
4. Behavior and architecture: contracts, components, data/state lifecycle, integration, validation, failure handling, privacy, performance, and cost.
5. Concrete demonstrations: before/after examples, system flows, and any necessary interaction mockups or isolated prototype findings.
6. Evaluation and delivery: baseline, tests, acceptance gates, phases, dependencies, rollout, rollback, and risks.
7. Decisions for me: only approvals and questions that genuinely require my judgment.
For every major proposed change, use:
Evidence/problem → change → expected benefit → what it preserves → tradeoff → how we will test it.
Keep a clear distinction between observed facts, research-supported claims, hypotheses, and proposals. Cite external factual claims. Do not bury unresolved risks under confident language.
Think comprehensively, but present the relevant conclusions rather than a ritual recital of every heading. Mark irrelevant dimensions briefly. Scale the work to the stakes and size of the feature.
Final standard and stop condition
Challenge the proposed plan before presenting it:
Are we solving the user's real frustration or merely improving an internal metric?
Does the result reduce decisions and effort rather than relocate them?
Can a simpler change deliver most of the benefit?
What evidence could show that our explanation is wrong?
What happens when the system is uncertain, unavailable, or operating on stale context?
Can the user inspect, correct, resume, and trust the result?
Can we prove improvement without sacrificing current capability?
Revise the highest-impact weaknesses. Avoid endless speculative refinement and unsupported promises of perfection.
The objective is not a more elaborate system. It is a system that gives the user the right help at the right moment, with less effort and more control.
Present the complete plan and supporting demonstrations, then stop and wait for my approval before production implementation.

## The orchestrator's instructions for this session (as given)

**Repo.** You're on `build/awaken-v1` of euansprr-dev/awaken, a NeoForge mod for Minecraft 26.3 (Java 25). Read `CLAUDE.md`, `docs/SPEC.md` (the source of truth), `docs/HANDOFF.md` and `docs/api/`. Version 26.3 is not 1.21.1, so never guess a signature. `.ref/` is local-only: use Gradle sources if JDK 25 is present, otherwise say what you couldn't verify.

**The "users" and "data"** in his process are players and game state: server and client, Codecs with schemaVersion, multiplayer, and the SPEC perf budgets. Mark the AI and privacy sections as not applicable, briefly.

**Audit the real combat.** Trace damage, hits, blocking, i-frames, sound, FX, HUD and drops through:
- `powers/` (CastAnims, FxRender);
- `gear/`, `mobs/` and `gates/` (bosses and Lords);
- `body/` (slide, momentum hits, MovementHooks);
- `holo/` (plates, damage numbers);
- `living/` (elites, loot, relics, Smithing);
- `lens/`, `progression/` and `hearth/`.

Reuse the Ascent's feel (SPEC §17.13–17.15) and the Holo look. Combat animations must share the first-person system planned on branch `pack/fp-anims` (`docs/anims/PLAN.md`). Reproduce a diamond sword against a Lord, with numbers from the code.

**Learning from other mods.** Look at Better Combat, Epic Fight, Simply Swords, Diablo, MC Dungeons, Elden Ring and others. **The repo is public: only copy code or assets where the licence allows it, with attribution.** Record each mod's licence. No Mojang assets.

**Scale.** Hundreds of truly 3D weapons need a content pipeline at his AAA bar: data-driven definitions, archetypes, and generated models, textures, animations and sounds through `tools/art/`. Cover rarity, discovery and fusion with the powers, plus a quality check for each weapon.

**Staging.** Start with a vertical slice of one weapon family that feels incredible end to end: parry, dodge, combos with a skill ceiling and few keys, hit-stop, sound, camera and FX. Then scale. Give each phase a gate: what CI GameTests can prove, and what needs Euan or a tour on his Mac. Estimate at AI pace: all of Awaken was built in about 30 hours.

**Deliverables (plan only, no production code).**
1. `docs/combat/PLAN.md`.
2. A self-contained `docs/combat/plan.html` approval page: inline CSS and SVG, light and dark mode.
3. `docs/handoff/pack-combat-plan.md`.

Push all three to a new branch, `pack/combat-plan`. Never push to `build/awaken-v1` and never force-push. End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply＠anthropic.com>`. Run subagents at extra-high effort. Then stop.
