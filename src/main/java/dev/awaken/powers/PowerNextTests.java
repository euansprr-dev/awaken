package dev.awaken.powers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.anims.FpClipSet;
import dev.awaken.core.api.FightMoments;
import dev.awaken.core.api.Goals;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.catalog.EarnedPowers;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.Voice;
import dev.awaken.powers.fx.Ways;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Powers, the next level (SPEC §32.N, §50.8 to §50.11): one test a mechanism. The scores and the takes are whole and name only
 * what exists; a way changes a power's shape; a duet and a finisher answer the blade; each earned power does its one thing, and
 * is given and shown on Tasks the way the SPEC says.
 */
final class PowerNextTests {
    private PowerNextTests() {}

    static void register() {
        AwakenTests.register("power_next_voice_scores", 20, PowerNextTests::scores);
        AwakenTests.register("power_next_takes", 20, PowerNextTests::takes);
        AwakenTests.registerIsolated("power_next_way_shape", 100, PowerNextTests::wayShape);
        AwakenTests.registerIsolated("power_next_duet_icebreaker", 100, PowerNextTests::icebreaker);
        AwakenTests.registerIsolated("power_next_break_finisher", 100, PowerNextTests::finisher);
        AwakenTests.registerIsolated("power_next_brand", 100, PowerNextTests::brand);
        AwakenTests.registerIsolated("power_next_lances", 100, PowerNextTests::lances);
        AwakenTests.registerIsolated("power_next_updraft", 100, PowerNextTests::updraft);
        AwakenTests.registerIsolated("power_next_backdraft", 100, PowerNextTests::backdraft);
        AwakenTests.registerIsolated("power_next_thornwake", 100, PowerNextTests::thornwake);
        AwakenTests.registerIsolated("power_next_earned", 100, PowerNextTests::earned);
    }

    private static JsonObject json(GameTestHelper h, String path) {
        try (InputStream in = PowerNextTests.class.getResourceAsStream(path)) {
            h.assertTrue(in != null, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Every score names a power and beats that exist, layers that are events in sounds.json, within the limits; every scored power has its subtitle. */
    private static void scores(GameTestHelper h) {
        JsonObject voices = json(h, "/assets/awaken/power_voices.json");
        JsonObject sounds = json(h, "/assets/awaken/sounds.json");
        JsonObject lang = json(h, "/assets/awaken/lang/en_us.json");
        h.assertTrue(voices.get("schemaVersion").getAsInt() == 1, "power_voices.json is schema 1");
        List<String> bad = new ArrayList<>();
        int scored = 0;
        for (Map.Entry<String, JsonElement> p : voices.getAsJsonObject("voices").entrySet()) {
            String power = p.getKey();
            scored++;
            if (PowerRegistry.get(power) == null) bad.add(power + ": no such power");
            for (Map.Entry<String, JsonElement> b : p.getValue().getAsJsonObject().entrySet()) {
                boolean beat = false;
                for (Voice.Beat k : Voice.Beat.values()) beat |= k.key.equals(b.getKey());
                if (!beat) bad.add(power + "." + b.getKey() + ": no such beat");
                for (JsonElement set : b.getValue().getAsJsonObject().getAsJsonArray("sets")) {
                    if (set.getAsJsonArray().size() > 12) bad.add(power + "." + b.getKey() + ": more than 12 layers");
                    for (JsonElement l : set.getAsJsonArray()) {
                        var layer = l.getAsJsonArray();
                        String event = layer.get(0).getAsString();
                        float volume = layer.get(2).getAsFloat(), pitch = layer.get(3).getAsFloat();
                        if (!sounds.has(event)) bad.add(power + "." + b.getKey() + ": " + event + " is not in sounds.json");
                        if (volume <= 0 || volume > 1 || pitch < 0.5f || pitch > 2f) bad.add(power + "." + b.getKey() + ": " + event + " out of range");
                    }
                }
            }
            if (!sounds.has("voice.say." + power) || !lang.has("subtitles.awaken.voice." + power)) bad.add(power + ": no subtitle");
        }
        // every recording a voice event names is Minecraft's own, by name: nothing of ours, so nothing of Mojang's is in the mod
        for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
            if (!e.getKey().startsWith("voice.")) continue;
            for (JsonElement s : e.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                if (!name.startsWith("minecraft:")) bad.add(e.getKey() + " plays " + name);
            }
        }
        h.assertTrue(bad.isEmpty(), "power voices: " + bad);
        h.assertTrue(scored >= 60, "most powers have a score (" + scored + ")");
        h.succeed();
    }

    /** Every re-authored cast has its three takes, a way's clip has them too, and a name for a way or take that isn't authored is never invented. */
    private static void takes(GameTestHelper h) {
        StringBuilder errors = new StringBuilder();
        Map<String, FpClipSet.Clip> clips = FpClipSet.load(errors);
        h.assertTrue(errors.isEmpty(), "first-person clips read: " + errors);
        List<String> bad = new ArrayList<>();
        int authored = 0;
        for (CastProfiles.Profile p : CastProfiles.all()) {
            FpClipSet.Clip base = clips.get(p.begin1p());
            if (base == null || !base.arm) continue; // still on its earlier clip
            authored++;
            if (base.toStance) continue; // a held power's gather hands over to its hold: one way in, the takes are its let-go's
            for (int take = 1; take <= 2; take++) {
                FpClipSet.Clip t = clips.get(Ways.clip(p.begin1p(), Ways.STAND, take));
                if (t == null) bad.add(p.power() + ": no take " + (take + 1));
                else if (t.length != base.length || t.eventAt.length != base.eventAt.length) bad.add(p.power() + ": take " + (take + 1) + " changed the timing");
            }
            for (int way : new int[]{Ways.RUN, Ways.AIR}) {
                if (clips.containsKey(Ways.clip(p.begin1p(), way, 0)) && !clips.containsKey(Ways.clip(p.begin1p(), way, 2))) bad.add(p.power() + ": a way without its takes");
            }
        }
        h.assertTrue(bad.isEmpty(), "takes: " + bad);
        h.assertTrue(authored >= 50, "most casts are authored for the arm (" + authored + ")");
        h.assertTrue(Ways.clip("fp_p_nova", Ways.AIR, 2).equals("fp_p_nova__air_t3") && Ways.clip("x", Ways.STAND, 0).equals("x"), "clip names for a way and a take");
        int keys = Ways.pack(0b1010, Ways.AIR, 2);
        h.assertTrue(Ways.wayOf(keys) == Ways.AIR && Ways.takeOf(keys) == 2 && (keys & 15) == 0b1010, "the way and the take ride the cast's keys");
        h.assertTrue(Ways.wayOfFlags(Ways.flags(Ways.RUN, 1)) == Ways.RUN && Ways.takeOfFlags(Ways.flags(Ways.RUN, 1)) == 1, "and an effect's flags");
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR); // whatever an earlier test left standing here
            }
        }
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(rel));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        sp.setGameMode(GameType.SURVIVAL);
        return sp;
    }

    private static Zombie zombie(GameTestHelper h, BlockPos pos) {
        Zombie z = h.spawn(EntityTypes.ZOMBIE, pos);
        z.setNoAi(true);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // the sun can't light it
        return z;
    }

    private static PowerType ready(GameTestHelper h, ServerPlayer sp, String id) {
        PowerType t = PowerRegistry.get(id);
        h.assertTrue(t != null, id + " is a power");
        Powers.learn(sp, t.id, 0);
        Powers.resetCooldowns(sp);
        Powers.fillEnergy(sp);
        return t;
    }

    /** A claimed way the body is really in changes the shape: Shockwave stamped out of the air is a ring all round, shorter; unclaimed, a cone. */
    private static void wayShape(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "shockwave");
        List<FxEvent> seen = new ArrayList<>();
        Fx.testSink = seen::add;
        try {
            PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
            h.assertTrue(r.ok(), "shockwave casts: " + r.reason());
            Fx.testTick(sp, 6);
            FxEvent cone = seen.stream().filter(e -> e.kind() == FxKind.CONE && e.has(FxEvent.REACH)).findFirst().orElse(null);
            h.assertTrue(cone != null && cone.aux() < 90, "a standing Shockwave is a cone");
            float standing = cone.scale();
            Casting.endAll(sp, Running.Why.CANCELLED);
            Powers.resetCooldowns(sp);
            Powers.fillEnergy(sp);
            seen.clear();
            // off the ground, and saying so
            Vec3 at = sp.position();
            sp.snapTo(at.x, at.y + 3, at.z, 0f, 0f);
            sp.setOnGround(false);
            r = Casting.cast(sp, t, 0, false, 0f, 0f, 0f, 0f, Ways.AIR, 1);
            h.assertTrue(r.ok(), "shockwave casts in the air: " + r.reason());
            Fx.testTick(sp, 6);
            cone = seen.stream().filter(e -> e.kind() == FxKind.CONE && e.has(FxEvent.REACH)).findFirst().orElse(null);
            h.assertTrue(cone != null && cone.aux() >= 179 && cone.scale() < standing, "out of the air it is a ring all round, shorter: " + cone);
            h.assertTrue(Ways.wayOfFlags(cone.flags()) == Ways.AIR && Ways.takeOfFlags(cone.flags()) == 1, "and its events say the way and the take");
            // a way the body isn't in is not believed
            Casting.endAll(sp, Running.Why.CANCELLED);
            sp.snapTo(at.x, at.y, at.z, 0f, 0f);
            sp.setOnGround(true);
            PowerState.of(sp).airAt = Long.MIN_VALUE / 2;
            h.assertTrue(Casting.way(sp, Ways.AIR, Casting.now(sp)) != Ways.AIR, "a claim the body can't back is dropped");
        } finally {
            Fx.testSink = null;
        }
        h.succeed();
    }

    /** Icebreaker: a heavy blow with a maul on a Frost Nova's ground throws spikes ahead, and the duet is counted. */
    private static void icebreaker(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE)); // a mace is a maul to the powers
        h.assertTrue("maul".equals(ArmsDuets.style(sp)), "a mace reads as a maul");
        PowerType t = ready(h, sp, "frost_nova");
        Zombie far = zombie(h, new BlockPos(0, 1, 6)); // outside the nova's own ring? no: inside it, so it is hurt once by the nova
        PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
        h.assertTrue(r.ok(), "frost nova casts: " + r.reason());
        Fx.testTick(sp, 6);
        float after = far.getHealth();
        FightMoments.report(sp, FightMoments.Moment.PERFECT_HEAVY, null);
        h.runAfterDelay(9, () -> {
            h.assertTrue(far.getHealth() < after, "the spikes reach the body six blocks ahead");
            h.assertTrue(Progression.profile(sp).counter(ArmsDuets.Duet.ICEBREAKER.counter()) == 1 && ArmsDuets.found(sp) == 1, "and the duet is found once");
            h.succeed();
        });
    }

    /** A cast at a body whose stance just broke is a finisher: it lands again, and says so. */
    private static void finisher(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "spirit_palm");
        Zombie z = zombie(h, new BlockPos(0, 1, 3));
        List<FxEvent> seen = new ArrayList<>();
        Fx.testSink = seen::add;
        try {
            FightMoments.report(sp, FightMoments.Moment.BREAK, z);
            PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
            h.assertTrue(r.ok(), "spirit palm casts: " + r.reason());
            h.assertTrue(seen.stream().anyMatch(e -> e.kind() == FxKind.FINISHER && e.target() == z.getId()), "the cast is a finisher on the broken body");
            h.assertTrue(Progression.profile(sp).counter("awaken:break_finishers/spirit") == 1, "counted for its family");
            seen.clear();
            Powers.resetCooldowns(sp);
            Powers.fillEnergy(sp);
            Casting.cast(sp, t, 0, false, 0f, 0f);
            h.assertTrue(seen.stream().noneMatch(e -> e.kind() == FxKind.FINISHER), "one break, one finisher");
        } finally {
            Fx.testSink = null;
        }
        h.succeed();
    }

    /** Brand: the palm marks, any blow sets it off, and the fire reaches the body beside it. */
    private static void brand(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "brand");
        Zombie marked = zombie(h, new BlockPos(0, 1, 3)), beside = zombie(h, new BlockPos(2, 1, 3));
        PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
        h.assertTrue(r.ok(), "brand casts: " + r.reason());
        h.assertTrue(marked.getHealth() < marked.getMaxHealth(), "the press itself stings");
        h.assertTrue(beside.getHealth() == beside.getMaxHealth(), "and touches nothing else");
        float before = marked.getHealth();
        EarnedPowers.meleeHit(sp, marked);
        h.runAfterDelay(4, () -> {
            h.assertTrue(marked.getHealth() < before || !marked.isAlive(), "the blow sets the mark off on its body");
            h.assertTrue(beside.getHealth() < beside.getMaxHealth(), "and the burst reaches the body beside it");
            h.assertTrue(Progression.profile(sp).counter("awaken:brands_set_off") == 1, "counted");
            float settled = beside.getHealth();
            EarnedPowers.meleeHit(sp, marked);
            h.runAfterDelay(4, () -> {
                h.assertTrue(beside.getHealth() == settled || beside.isOnFire(), "a mark goes off once");
                h.succeed();
            });
        });
    }

    /** Hoarfrost Lances: a blow looses one at what it hit; the second press throws the rest. */
    private static void lances(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "hoarfrost_lances");
        Zombie z = zombie(h, new BlockPos(0, 1, 3));
        PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
        h.assertTrue(r.ok(), "the lances form: " + r.reason());
        h.assertTrue(z.getHealth() == z.getMaxHealth(), "forming them hurts nothing");
        EarnedPowers.meleeHit(sp, z);
        h.runAfterDelay(4, () -> {
            float one = z.getHealth();
            h.assertTrue(one < z.getMaxHealth() && z.hasEffect(MobEffects.SLOWNESS), "a blow looses a lance: hurt and chilled");
            h.assertTrue(Progression.profile(sp).counter("awaken:lances_loosed") == 1, "one lance, one blow");
            // the key again: what is left goes at once
            PowerType.Result again = Casting.cast(sp, t, 0, false, 0f, 0f);
            h.assertTrue(again.ok(), "the second press is free: " + again.reason());
            h.runAfterDelay(8, () -> {
                h.assertTrue(z.getHealth() < one || !z.isAlive(), "the rest fly at what you aim at");
                h.assertTrue(Progression.profile(sp).counter("awaken:lances_loosed") == 3, "all three flew");
                h.succeed();
            });
        });
    }

    /** Updraft: the column carries its caster and throws what is caught in it. */
    private static void updraft(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "updraft");
        Zombie in = zombie(h, new BlockPos(1, 1, 0)), out = zombie(h, new BlockPos(6, 1, 0));
        PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
        h.assertTrue(r.ok(), "updraft casts: " + r.reason());
        Fx.testTick(sp, 3);
        h.assertTrue(sp.hasEffect(MobEffects.LEVITATION), "you rise in your own column (at " + sp.position() + ", effects " + sp.getActiveEffects() + ")");
        h.assertTrue(in.hasEffect(MobEffects.LEVITATION) && in.getHealth() < in.getMaxHealth(), "what is caught in it is hit and thrown up");
        h.assertTrue(!out.hasEffect(MobEffects.LEVITATION) && out.getHealth() == out.getMaxHealth(), "and nothing outside it");
        h.assertTrue(Progression.profile(sp).counter("awaken:updraft_rides") == 1, "the ride is counted once");
        Casting.endAll(sp, Running.Why.CANCELLED);
        h.succeed();
    }

    /** Backdraft: a burning body near you goes out, and the breath that comes back is bigger for it. */
    private static void backdraft(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "backdraft");
        Zombie z = zombie(h, new BlockPos(0, 1, 3));
        z.igniteForSeconds(30);
        h.setBlock(new BlockPos(3, 1, 0), Blocks.FIRE);
        PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
        h.assertTrue(r.ok(), "backdraft casts: " + r.reason());
        Fx.testTick(sp, 8);
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 0)).isAir(), "the fire on the ground is taken");
        h.assertTrue(Progression.profile(sp).counter("awaken:fires_breathed") >= 2, "two fires taken: " + Progression.profile(sp).counter("awaken:fires_breathed"));
        float before = z.getHealth();
        h.runAfterDelay(5, () -> {
            h.assertTrue(z.getHealth() < before || !z.isAlive(), "and given back down the cone");
            h.succeed();
        });
    }

    /** Thornwake: green ground gives roots that hold; bare stone gives one thin one. */
    private static void thornwake(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType t = ready(h, sp, "thornwake");
        Zombie a = zombie(h, new BlockPos(0, 1, 3)), b = zombie(h, new BlockPos(3, 1, 0));
        PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
        h.assertTrue(r.ok(), "thornwake casts: " + r.reason());
        Fx.testTick(sp, 6);
        h.runAfterDelay(6, () -> {
            int held = (a.hasEffect(MobEffects.SLOWNESS) ? 1 : 0) + (b.hasEffect(MobEffects.SLOWNESS) ? 1 : 0);
            h.assertTrue(held == 1, "bare stone gives one root (" + held + ")");
            // the same place, green
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            h.setBlock(new BlockPos(1, 1, 1), Blocks.DANDELION);
            h.setBlock(new BlockPos(-1, 1, 1), Blocks.POPPY);
            a.removeAllEffects();
            b.removeAllEffects();
            Powers.resetCooldowns(sp);
            Powers.fillEnergy(sp);
            Casting.cast(sp, t, 0, false, 0f, 0f);
            Fx.testTick(sp, 6);
            h.runAfterDelay(6, () -> {
                h.assertTrue(a.hasEffect(MobEffects.SLOWNESS) && b.hasEffect(MobEffects.SLOWNESS), "on green ground both are held");
                h.succeed();
            });
        });
    }

    /** An earned power is given when its count is reached and its rank is held, with one step on Tasks before and one after. */
    private static void earned(GameTestHelper h) {
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        Progression.awaken(sp);
        h.assertTrue(Earned.steps(sp).isEmpty(), "nothing is announced before you have made a start");
        Progression.setCounter(sp, Earned.Gift.UPDRAFT.counter, 60);
        Earned.check(sp);
        h.assertTrue(!Powers.owns(sp, "awaken:updraft"), "three seconds is not enough");
        List<Goals.Step> steps = Earned.steps(sp);
        h.assertTrue(steps.size() == 1 && steps.get(0).key().equals("powers/earned/updraft") && steps.get(0).voice() == Goals.Voice.LEAD
                && steps.get(0).needs().get(0).have() == 3 && steps.get(0).needs().get(0).need() == 6, "once started, a lead with the count in seconds: " + steps);
        Progression.setCounter(sp, Earned.Gift.UPDRAFT.counter, Earned.Gift.UPDRAFT.need);
        Earned.check(sp);
        h.assertTrue(Powers.owns(sp, "awaken:updraft"), "six seconds in the air gives Updraft");
        steps = Earned.steps(sp);
        h.assertTrue(steps.size() == 1 && steps.get(0).key().equals("powers/earned/updraft/first"), "then one step says how it is used: " + steps);
        Progression.count(sp, Earned.Gift.UPDRAFT.used);
        h.assertTrue(Earned.steps(sp).isEmpty(), "until it has been used once");
        // a rank it doesn't hold yet keeps a power back, however much has been done
        Progression.setCounter(sp, Earned.Gift.LANCES.counter, 50);
        Earned.check(sp);
        PowerType lances = PowerRegistry.get("awaken:hoarfrost_lances");
        h.assertTrue(lances != null && Powers.owns(sp, lances.id.toString()) == (Progression.rank(sp).ordinal() >= lances.unlock.ordinal()), "the rank it asks is held to");
        h.succeed();
    }
}
