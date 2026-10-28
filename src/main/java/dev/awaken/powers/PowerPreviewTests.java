package dev.awaken.powers;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.powers.ladder.PowerLadder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;

/** The Power Core hover (SPEC 26.3c): the next ladder step, numbers equal to the cast's, and nothing spent or levelled. */
final class PowerPreviewTests {
    private PowerPreviewTests() {}

    private static double value(java.util.List<PowerPreviews.Value> values, String key) {
        return values.stream().filter(v -> v.name().equals(key)).findFirst().orElseThrow().value();
    }

    static void register() {
        AwakenTests.register("powers_preview_core", 40, h -> {
            var p = h.makeMockServerPlayerInLevel();
            p.setGameMode(GameType.SURVIVAL);
            var type = PowerRegistry.get("barrier");
            Powers.learnOrLevel(p, type.id, 2, false, false);
            var before = Cast.of(p, type);
            var expected = PowerPreviews.values(new Cast(p, type, 2, 2, -1, false, 0, 0));
            float energy = Powers.energy(p);
            var reply = PowerPreviews.preview(p, type, 0); // a standard core levels the Overshield you own
            h.assertTrue(reply.ownedVariant() == 2 && reply.lines().getFirst().contains("Overshield"), "the hover reads the owned variant: " + reply.lines());
            h.assertTrue(PowerLadder.step(type.path, 2) == null || reply.lines().contains(PowerLadder.text(type.path, 2).getString()),
                    "the hover names the real next ladder step: " + reply.lines());
            h.assertTrue(Powers.loadout(p).get(type.id.toString()).level() == 1 && Powers.energy(p) == energy
                    && PowerState.of(p).running.isEmpty(), "a preview never casts, spends energy or levels the power");
            var core = PowerContent.core(type, 0);
            core.setCount(2);
            p.setItemInHand(InteractionHand.MAIN_HAND, core);
            core.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            var owned = Powers.loadout(p).get(type.id.toString());
            h.assertTrue(core.getCount() == 1 && owned.level() == 2 && owned.variant() == 2, "using it takes one core and keeps the owned variant");
            var cast = Cast.of(p, type);
            h.assertTrue(Math.abs(cast.dmg() - value(expected, "Damage when the shield breaks")) < 0.0001 && cast.dmg() > before.dmg(),
                    "the damage shown is the cast's own");
            Casting.startCooldown(p, Powers.loadout(p), type, owned, PowerState.of(p));
            h.assertTrue(Powers.loadout(p).cooldowns.get(type.id.toString()).total() == Math.round(value(expected, "Cooldown") * 20),
                    "the cooldown shown is the cast's own, ladder scale included");
            p.setShiftKeyDown(true);
            core.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            owned = Powers.loadout(p).get(type.id.toString());
            h.assertTrue(core.isEmpty() && owned.level() == 2 && owned.variant() == 0, "sneak and use changes the variant instead, as the hover says");
            p.setShiftKeyDown(false);
            Powers.loadout(p).owned.put(type.id.toString(), new Loadout.Owned(type.id.toString(), 0, Loadout.MAX_LEVEL));
            var maxCore = PowerContent.core(type, 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, maxCore);
            h.assertTrue(PowerPreviews.preview(p, type, 0).lines().getFirst().contains("you keep it"), "at the top a same-variant core says it does nothing");
            maxCore.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(maxCore.getCount() == 1, "and it really is kept");
            // every power, variant and level: a bounded hover that survives the trip to the client
            for (var t : PowerRegistry.all()) for (int variant = 0; variant < t.variants.size(); variant++) for (int level = 1; level <= Loadout.MAX_LEVEL; level++) {
                Powers.loadout(p).owned.put(t.id.toString(), new Loadout.Owned(t.id.toString(), variant, level));
                var r = PowerPreviews.preview(p, t, variant);
                h.assertTrue(r.lines().size() <= 32 && r.lines().stream().allMatch(line -> line.length() <= 320 && !line.contains("NaN")), "bounded preview: " + t.path);
                var bytes = io.netty.buffer.Unpooled.buffer();
                PowerPreviews.Reply.STREAM_CODEC.encode(bytes, r);
                h.assertTrue(PowerPreviews.Reply.STREAM_CODEC.decode(bytes).equals(r), "the hover survives the payload: " + t.path);
                bytes.release();
            }
            h.succeed();
        });
    }
}
