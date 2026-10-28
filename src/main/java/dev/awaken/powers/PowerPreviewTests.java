package dev.awaken.powers;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

final class PowerPreviewTests {
    private PowerPreviewTests() {}
    private static double value(java.util.List<PowerPreviews.Value> values, String key) {
        return values.stream().filter(v -> v.name().equals(key)).findFirst().orElseThrow().value();
    }
    static void register() {
        AwakenTests.register("powers_preview_core_runtime_parity", 40, h -> {
            var p = h.makeMockServerPlayerInLevel();
            p.setGameMode(GameType.SURVIVAL);
            var type = PowerRegistry.get("barrier");
            Powers.learnOrLevel(p, type.id, 2, false, false);
            Progression.addStat(p, Stat.INT, 15);
            var before = Cast.of(p, type);
            var predicted = new Cast(p, type, 2, 2, -1, false, 0, 0);
            var expected = PowerPreviews.values(predicted);
            float energy = Powers.energy(p);
            var reply = PowerPreviews.preview(p, type, 0); // a standard core upgrades the owned Overshield
            h.assertTrue(reply.ownedVariant() == 2 && reply.lines().getFirst().contains("Overshield"), "preview uses the owned variant, not the incoming core");
            h.assertTrue(Powers.loadout(p).get(type.id.toString()).level() == 1 && Powers.energy(p) == energy
                    && PowerState.of(p).running.isEmpty(), "preview never casts, spends energy or levels the power");
            var core = PowerContent.core(type, 0); core.setCount(2);
            p.setItemInHand(InteractionHand.MAIN_HAND, core);
            core.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(core.getCount() == 1 && Powers.loadout(p).get(type.id.toString()).level() == 2
                    && Powers.loadout(p).get(type.id.toString()).variant() == 2, "actual use consumes exactly one core and preserves the owned variant");
            var cast = Cast.of(p, type);
            h.assertTrue(type.behaviour.cast(cast).ok(), "the upgraded barrier really casts");
            h.assertTrue(Math.abs(p.getAbsorptionAmount() - value(expected, "Shield capacity")) < 0.0001,
                    "the actual shield equals the before-use preview");
            var running = Casting.running(p, type.id.toString());
            h.assertTrue(running != null && running.duration == Math.round(value(expected, "Effect duration") * 20), "the actual effect lifetime equals the preview");
            Casting.startCooldown(p, Powers.loadout(p), type, Powers.loadout(p).get(type.id.toString()), PowerState.of(p));
            h.assertTrue(Powers.loadout(p).cooldowns.get(type.id.toString()).total() == Math.round(value(expected, "Cooldown") * 20), "runtime rounded cooldown equals preview");
            h.assertTrue(Math.abs(cast.dmg() - value(expected, "Shield-break burst")) < 0.0001 && cast.dmg() > before.dmg(), "the actual damage formula equals the preview");
            Casting.endAll(p, Running.Why.CANCELLED);
            p.setShiftKeyDown(true);
            core.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(core.isEmpty() && Powers.loadout(p).get(type.id.toString()).level() == 2
                    && Powers.loadout(p).get(type.id.toString()).variant() == 0, "crouch-use swaps instead of levelling, as the tooltip explains");
            p.setShiftKeyDown(false);
            var l = Powers.loadout(p);
            l.owned.put(type.id.toString(), new Loadout.Owned(type.id.toString(), 0, Loadout.MAX_LEVEL));
            var maxCore = PowerContent.core(type, 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, maxCore);
            h.assertTrue(PowerPreviews.preview(p, type, 0).lines().getFirst().contains("kept"), "maxed same-variant preview says no effect");
            maxCore.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(maxCore.getCount() == 1, "maxed same-variant core is actually kept");
            h.succeed();
        });
        AwakenTests.register("powers_preview_caps_and_catalog", 40, h -> {
            var p = h.makeMockServerPlayerInLevel();
            p.setGameMode(GameType.SURVIVAL);
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            p.setHealth(99);
            var mend = PowerRegistry.get("mend");
            var old = new Cast(p, mend, 1, 1, -1, false, 0, 0);
            var next = new Cast(p, mend, 1, 2, -1, false, 0, 0);
            var changes = PowerPreviews.changes(old, next);
            var capped = changes.stream().filter(c -> c.name().equals("Self heal at current health")).findFirst().orElseThrow();
            h.assertTrue(!capped.changed() && capped.after() == 1, "a capped heal honestly stays unchanged despite more available healing");
            mend.behaviour.cast(next);
            h.assertTrue(p.getHealth() == 100, "actual instant Mend respects the previewed health cap");
            var kinetic = PowerRegistry.get("ground_slam");
            var sliding = new Cast(p, kinetic, 0, 2, -1, false, 0, 0);
            h.assertTrue(Math.abs(value(PowerPreviews.values(sliding), "If cast mid-slide: shockwave") - sliding.dmg(MoveTwists.PUNCH_DAMAGE)) < 0.0001,
                    "the conditional body-and-power combo uses the same runtime base damage");
            var passive = PowerRegistry.get("water_run");
            h.assertTrue(PowerPreviews.upgradeLines(p, passive, 0, 1, 2).getFirst().contains("No gameplay numbers change"), "non-scaling passive does not claim imaginary strength");
            var beam = PowerRegistry.get("beam");
            var state = PowerState.of(p);
            state.buff(PowerState.Buffs.OVERLOAD_COST, Casting.now(p) + 100, 1);
            state.buff(PowerState.Buffs.OVERLOAD, Casting.now(p) + 100, 0.25);
            state.cdrPerk = 2; state.perksAt = Casting.now(p);
            var boosted = new Cast(p, beam, 2, 5, -1, false, 0, 0);
            var boost = PowerPreviews.values(boosted);
            h.assertTrue(Math.abs(value(boost, "Energy per use") - beam.cost * 1.3 * 0.5) < 0.001,
                    "preview includes current variant and temporary cost reduction");
            h.assertTrue(value(boost, "Cooldown") == PowerMath.cooldown(beam.cooldown, 1, 5, 0.5) / 20.0, "cooldown reduction is capped and tick-rounded");
            h.assertTrue(Math.abs(value(boost, "Main beam each 0.2 s") - boosted.dmg()) < 0.0001,
                    "preview includes current cast damage buff and stats");
            h.assertTrue(PowerMath.capBossHit(boosted.dmg(1000), 200) == PowerMath.capBossHit(new Cast(p, beam, 2, 4, -1, false, 0, 0).dmg(1000), 200),
                    "boss damage at the cap really can remain unchanged");
            // Every registered variant has a finite, bounded response for every allowed upgrade; no behaviours run.
            for (var t : PowerRegistry.all()) for (int variant = 0; variant < t.variants.size(); variant++) for (int level = 1; level < Loadout.MAX_LEVEL; level++) {
                Powers.loadout(p).owned.put(t.id.toString(), new Loadout.Owned(t.id.toString(), variant, level));
                var reply = PowerPreviews.preview(p, t, variant);
                h.assertTrue(reply.lines().size() <= 32 && reply.lines().stream().allMatch(line -> line.length() <= 320 && !line.contains("NaN")), "bounded preview: " + t.path);
                var bytes = io.netty.buffer.Unpooled.buffer();
                PowerPreviews.Reply.STREAM_CODEC.encode(bytes, reply);
                h.assertTrue(PowerPreviews.Reply.STREAM_CODEC.decode(bytes).equals(reply), "all details survive the server-to-client payload");
                bytes.release();
            }
            var q = h.makeMockServerPlayerInLevel();
            Powers.learnOrLevel(q, beam.id, 0, false, false);
            h.assertTrue(PowerPreviews.preview(q, beam, 0).level() == 1 && Powers.loadout(q).owned.size() == 1, "another player's preview uses their own state");
            h.succeed();
        });
    }
}
