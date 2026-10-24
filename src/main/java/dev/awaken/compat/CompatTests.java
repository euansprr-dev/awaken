package dev.awaken.compat;

import dev.awaken.Awaken;
import dev.awaken.above.Above;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.AboveItems;
import dev.awaken.above.MachineType;
import dev.awaken.above.frame.FrameModule;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateState;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.veil.VeilContent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Companion policies, Awaken's side of them: which dimensions are instanced (far terrain off, Xaero's map shut); the
 * spoiler rule's record (exactly which items a player state counts as discovered) and its guard (what a viewer hides and
 * brings back as the record grows); the look-at facts follow the gate card's rule and read machines, creatures and
 * townsfolk right. None of this needs a companion mod installed.
 */
final class CompatTests {
    private CompatTests() {}

    static void register() {
        // Which dimensions are instanced (far terrain off, Xaero's map shut): every awaken: one but the Stillpoint.
        AwakenTests.register("compat_instanced_dimensions", 20, h -> {
            int closed = 0;
            // every dimension the data packs define (the GameTest world itself only has the overworld)
            var files = h.getLevel().getServer().getResourceManager().listResources("dimension", f -> f.getPath().endsWith(".json"));
            for (var file : files.keySet()) {
                String path = file.getPath();
                String id = file.getNamespace() + ":" + path.substring("dimension/".length(), path.length() - ".json".length());
                boolean awaken = id.startsWith(Awaken.MODID + ":");
                boolean open = Instanced.OPEN.contains(id);
                h.assertTrue(Instanced.is(id) == (awaken && !open), "instanced: " + id);
                h.assertTrue(FarView.allowed(id) == !Instanced.is(id), "far view for " + id);
                if (awaken && !open) closed++;
            }
            h.assertTrue(!Instanced.is("minecraft:overworld") && !Instanced.is("awaken:stillpoint") && Instanced.is("awaken:gates")
                    && !Instanced.is(null) && FarView.allowed(null), "the basics");
            h.assertTrue(closed >= 10, "instanced Awaken dimensions found: " + closed);
            h.succeed();
        });

        // Xaero's World Map is shut by its own effect while the player is in an instanced dimension. GameTests never load
        // Xaero, so the effect's upkeep is checked with a vanilla stand-in.
        AwakenTests.register("compat_world_map_rule", 20, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            h.assertTrue(WorldMaps.effect().isEmpty() && !WorldMaps.apply(sp), "without Xaero there is no effect and nothing to do");
            Holder<MobEffect> stand = MobEffects.LUCK;
            WorldMaps.keep(sp, stand, true);
            MobEffectInstance given = sp.getEffect(stand);
            h.assertTrue(given != null && WorldMaps.ours(given) && !given.isVisible() && !given.showIcon(), "shut: the effect, no particles, no icon");
            WorldMaps.keep(sp, stand, true);
            h.assertTrue(sp.getEffect(stand).getDuration() == WorldMaps.DURATION, "renewed, never stacked");
            WorldMaps.keep(sp, stand, false);
            h.assertTrue(!sp.hasEffect(stand), "open the moment the player leaves");
            // an operator's /effect (visible, longer) is never taken off or shortened
            sp.addEffect(new MobEffectInstance(stand, 600));
            WorldMaps.keep(sp, stand, false);
            WorldMaps.keep(sp, stand, true);
            MobEffectInstance theirs = sp.getEffect(stand);
            h.assertTrue(theirs != null && !WorldMaps.ours(theirs) && theirs.getDuration() > WorldMaps.DURATION, "an operator's effect stays as it was");
            sp.removeEffect(stand);
            h.succeed();
        });

        // The spoiler guard's rule (SPEC §14): held, a known recipe's result, or named by a record. Nothing else.
        AwakenTests.register("compat_spoiler_rule", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            var found = Discovered.of(sp);
            Discovered.check(sp);
            h.assertTrue(found.size() == 0, "a fresh player has discovered nothing: " + found.items());

            // 1. held: an Awaken item in the pack counts; a vanilla one is never guarded or recorded
            sp.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get()));
            sp.getInventory().add(new ItemStack(Items.IRON_INGOT));
            h.assertTrue(Discovered.check(sp) == 1, "one new item from the pack");
            h.assertTrue(found.has(CoreApi.ESSENCE_SHARD.get()) && !found.has(Items.IRON_INGOT), "the shard counts, iron is not Awaken's");
            h.assertTrue(!found.has(CoreApi.LORD_HEART.get()), "an item never held stays hidden");

            // 2. a recipe in the book names its result, and only its own
            sp.awardRecipesByKey(List.of(ResourceKey.create(Registries.RECIPE, Awaken.id("wildhide_blade"))));
            Discovered.check(sp);
            h.assertTrue(found.has(GearItems.get(HunterTier.E, Piece.BLADE)), "a known recipe's result is discovered");
            h.assertTrue(!found.has(GearItems.get(HunterTier.D, Piece.BLADE)) && !found.has(GearItems.get(HunterTier.E, Piece.CHESTPLATE)),
                    "other recipes' results stay hidden");

            // 3. a scanned blueprint names its machine; the Frame Forge's names its Frames and modules
            h.assertTrue(!found.has(AboveItems.machine(MachineType.GATE_SCANNER)), "an unscanned machine is hidden");
            Above.learn(sp, MachineType.GATE_SCANNER);
            Discovered.check(sp);
            h.assertTrue(found.has(AboveItems.machine(MachineType.GATE_SCANNER)), "a scanned blueprint's machine is discovered");
            h.assertTrue(!found.has(AboveItems.FRAME_HELMET.get()), "Frames wait for the Frame Forge's blueprint");
            Above.learn(sp, MachineType.FRAME_FORGE);
            Discovered.check(sp);
            h.assertTrue(found.has(AboveItems.machine(MachineType.FRAME_FORGE)) && found.has(AboveItems.FRAME_HELMET.get())
                    && found.has(AboveItems.MODULES.get(FrameModule.MAGNET).get()), "the Frame Forge names its Frames and modules");
            h.assertTrue(!found.has(AboveItems.machine(MachineType.LAUNCH_RING)), "other blueprints stay hidden");

            // it only grows: dropping everything forgets nothing
            int size = found.size();
            sp.getInventory().clearContent();
            h.assertTrue(Discovered.check(sp) == 0 && found.size() == size, "discoveries are for good");

            // saved with its schema version and read back the same
            var tag = Discovered.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, found).getOrThrow();
            var back = Discovered.MAP_CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.items().equals(found.items()) && back.schema == Discovered.SCHEMA, "the record round-trips");
            var buf = Unpooled.buffer();
            Discovered.STREAM_CODEC.encode(buf, found);
            h.assertTrue(Discovered.STREAM_CODEC.decode(buf).items().equals(found.items()), "and syncs intact");
            h.assertTrue(Discovered.isAwaken(CoreApi.LORD_HEART.get()) && !Discovered.isAwaken(Items.DIAMOND), "only Awaken's items are guarded");
            h.succeed();
        });

        // The guard (SPEC §14): what the rule lets a player see, and a viewer's hidden set following the record as it grows.
        AwakenTests.register("compat_spoiler_guard", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            sp.setGameMode(GameType.SURVIVAL);
            h.assertTrue(SpoilerRule.applies(sp, true) && !SpoilerRule.applies(sp, false), "on in survival, off with the setting off");
            Item shard = CoreApi.ESSENCE_SHARD.get(), heart = CoreApi.LORD_HEART.get(), iron = Items.IRON_INGOT;
            List<Item> all = List.of(shard, heart, iron);
            Reveal<Item> view = new Reveal<>();
            List<Item> hid = new ArrayList<>(), shown = new ArrayList<>();
            Runnable update = () -> {
                hid.clear();
                shown.clear();
                boolean guard = SpoilerRule.applies(sp, true);
                view.update(all, i -> SpoilerRule.shows(sp, i, guard), hid::add, shown::add);
            };

            update.run();
            h.assertTrue(hid.equals(List.of(shard, heart)) && shown.isEmpty(), "a fresh player: Awaken's items hidden, iron never: " + hid);
            update.run();
            h.assertTrue(hid.isEmpty() && shown.isEmpty(), "nothing changed, nothing reported");
            sp.getInventory().add(new ItemStack(shard));
            Discovered.check(sp);
            update.run();
            h.assertTrue(shown.equals(List.of(shard)) && hid.isEmpty() && view.isHidden(heart) && view.hiddenCount() == 1,
                    "the shard appears the moment it is discovered, the heart stays hidden");
            h.assertTrue(SpoilerRule.showsAny(sp, List.of(heart, shard), true) && !SpoilerRule.showsAny(sp, List.of(heart), true),
                    "a recipe shows once any of its key items does");

            // creative and spectator list everything; back in survival the undiscovered hides again
            sp.setGameMode(GameType.CREATIVE);
            update.run();
            h.assertTrue(shown.equals(List.of(heart)) && view.hiddenCount() == 0, "creative sees everything");
            sp.setGameMode(GameType.SPECTATOR);
            h.assertTrue(!SpoilerRule.applies(sp, true), "spectator too");
            sp.setGameMode(GameType.SURVIVAL);
            update.run();
            h.assertTrue(hid.equals(List.of(heart)), "survival again: only the undiscovered heart hides");

            // a creature's details wait for the Codex
            h.assertTrue(!SpoilerRule.knowsCreature(sp, "mossback", true) && SpoilerRule.knowsCreature(sp, "mossback", false),
                    "an unseen creature keeps its details while the guard is on");
            Progression.setCounter(sp, "awaken:discovered/creature/mossback", 1);
            h.assertTrue(SpoilerRule.knowsCreature(sp, "mossback", true), "known once the Codex has it");

            // disguises are never named: a dormant statue, the ritual's cast, Veil marks
            AwakenMob ghoul = Mobs.spawn(h.getLevel(), Bestiary.get("toll_ghoul"), h.absoluteVec(new Vec3(0.5, 1, 0.5)), 10,
                    new MobRole(MobRole.Role.NORMAL, 10, dev.awaken.mobs.def.Theme.BELL.id, Optional.empty(), ""), Set.of());
            h.assertTrue(ghoul != null && !SpoilerRule.disguised(ghoul), "an ordinary creature can be named");
            ghoul.setFlag(AwakenMob.F_DORMANT, true);
            h.assertTrue(SpoilerRule.disguised(ghoul), "a dormant one is a statue");
            ghoul.setFlag(AwakenMob.F_DORMANT, false);
            ghoul.configure(10, new MobRole(MobRole.Role.RITUAL, 10, dev.awaken.mobs.def.Theme.BELL.id, Optional.empty(), ""), Set.of());
            h.assertTrue(SpoilerRule.disguised(ghoul), "the ritual's cast is never named");
            ghoul.discard();
            h.assertTrue(SpoilerRule.disguised(VeilContent.VEIL_MARK.get().defaultBlockState())
                    && !SpoilerRule.disguised(Blocks.STONE.defaultBlockState()), "Veil marks are for spirit sight only");
            h.succeed();
        });

        // The look-at tooltip says what the gate card would: PER 20 layout, 40 modifiers, 60 floors; scouting shows all.
        AwakenTests.register("compat_look_at_gate_rule", 20, h -> {
            var preview = new Gates.GatePreview(7, Rank.D, Theme.BELL_CRYPT, Theme.BELL_CRYPT.omen, Layout.LABYRINTH,
                    List.of(Modifier.LIGHTLESS), 4, "bell_warden", "the Bell Warden", Twist.NONE, 2, List.of());
            var blind = LookAt.gateFor(Rank.D, GateState.READY, 2, false, preview, 5, false);
            h.assertTrue(blind.rank() == Rank.D.ordinal() && blind.breaksIn() == 2 && blind.omen().equals(Theme.BELL_CRYPT.omen),
                    "everyone reads the rank, the clock and the omen");
            h.assertTrue(blind.layout().isEmpty() && blind.modifiers().isEmpty() && blind.floors() < 0 && blind.theme().isEmpty()
                    && blind.lord().isEmpty(), "low Perception reads nothing more: " + blind);
            var per20 = LookAt.gateFor(Rank.D, GateState.READY, 2, false, preview, LookAt.PER_LAYOUT, false);
            h.assertTrue(per20.layout().equals("Labyrinth") && per20.modifiers().isEmpty(), "PER 20 reads the layout only");
            var per40 = LookAt.gateFor(Rank.D, GateState.READY, 2, false, preview, LookAt.PER_MODIFIERS, false);
            h.assertTrue(per40.modifiers().equals("Lightless") && per40.floors() < 0, "PER 40 reads the modifiers");
            var per60 = LookAt.gateFor(Rank.D, GateState.READY, 2, false, preview, LookAt.PER_FLOORS, false);
            h.assertTrue(per60.floors() == 4 && per60.theme().isEmpty(), "PER 60 counts the floors, still not the theme");
            var scouted = LookAt.gateFor(Rank.D, GateState.READY, 2, false, preview, 5, true);
            h.assertTrue(scouted.layout().equals("Labyrinth") && scouted.floors() == 4 && scouted.theme().equals("Bell Crypt")
                    && scouted.lord().equals("the Bell Warden"), "a scouted gate shows everything: " + scouted);
            h.assertTrue(LookAt.stateWord(GateState.BROKEN).equals("Broken open") && LookAt.stateWord(GateState.ACTIVE).equals("Hunters inside"),
                    "states in plain words");
            var buf = Unpooled.buffer();
            LookAt.Gate.STREAM_CODEC.encode(buf, blind);
            h.assertTrue(LookAt.Gate.STREAM_CODEC.decode(buf).equals(blind), "the gate's facts travel intact (hidden counts too)");
            h.succeed();
        });

        // Machines: a reactor's store, a Clone Vat's clone, a Launch Ring's missing frames.
        AwakenTests.register("compat_look_at_machines", 40, h -> {
            var level = h.getLevel();
            BlockPos reactorAt = h.absolutePos(new BlockPos(0, 1, 0));
            level.setBlockAndUpdate(reactorAt, AboveBlocks.MACHINES.get(MachineType.REACTOR).get().defaultBlockState());
            h.assertTrue(level.getBlockEntity(reactorAt) instanceof MachineBlockEntity, "the reactor has its block entity");
            MachineBlockEntity reactor = (MachineBlockEntity) level.getBlockEntity(reactorAt);
            reactor.addEssence(1500);
            var r = LookAt.machine(level, reactor);
            h.assertTrue(r.stored() == 1500 && r.status() == 0 && r.owner().isEmpty(), "a reactor reports its essence: " + r);

            BlockPos vatAt = h.absolutePos(new BlockPos(2, 1, 0));
            level.setBlockAndUpdate(vatAt, AboveBlocks.MACHINES.get(MachineType.CLONE_VAT).get().defaultBlockState());
            MachineBlockEntity vat = (MachineBlockEntity) level.getBlockEntity(vatAt);
            h.assertTrue(LookAt.machine(level, vat).status() == 2, "an empty vat is growing a clone");
            vat.setCloneReady(true);
            h.assertTrue(LookAt.machine(level, vat).status() == 1, "then it is ready");

            BlockPos ringAt = h.absolutePos(new BlockPos(0, 4, 0));
            level.setBlockAndUpdate(ringAt, AboveBlocks.MACHINES.get(MachineType.LAUNCH_RING).get().defaultBlockState());
            MachineBlockEntity ring = (MachineBlockEntity) level.getBlockEntity(ringAt);
            h.assertTrue(LookAt.machine(level, ring).status() == 10 + dev.awaken.above.machine.LaunchRing.FRAMES,
                    "a bare ring core lacks all its frames");
            for (BlockPos p : List.of(reactorAt, vatAt, ringAt)) level.removeBlock(p, false);
            h.succeed();
        });

        // Creatures: level and rank from where they spawned; the role in plain words; townsfolk's jobs from their ids.
        AwakenTests.register("compat_look_at_creatures", 40, h -> {
            var level = h.getLevel();
            AwakenMob ghoul = Mobs.spawn(level, Bestiary.get("toll_ghoul"), h.absoluteVec(new Vec3(0.5, 1, 0.5)), 35,
                    new MobRole(MobRole.Role.ELITE, 35, dev.awaken.mobs.def.Theme.BELL.id, Optional.empty(), ""), Set.of());
            h.assertTrue(ghoul != null, "toll ghoul spawned");
            var c = LookAt.creature(ghoul).orElseThrow();
            h.assertTrue(c.level() == 35 && c.rank() == Rank.C.ordinal() && c.role().equals("Elite"), "an elite at 35 reads C-rank elite: " + c);
            ghoul.discard();
            h.assertTrue(LookAt.rankFor(0) == Rank.E && LookAt.rankFor(17) == Rank.D && LookAt.rankFor(56) == Rank.C
                    && LookAt.rankFor(57) == Rank.B && LookAt.rankFor(200) == Rank.SOVEREIGN, "levels map to the rank whose content they are");
            h.assertTrue(LookAt.jobFromId("oren_blacksmith").equals("Blacksmith") && LookAt.jobFromId("teodor_archivist").equals("Archivist")
                    && LookAt.jobFromId("nobody").isEmpty(), "a townsperson's job from their id");
            h.assertTrue(LookAt.creature(h.makeMockServerPlayerInLevel()).isEmpty(), "players aren't creatures");
            var buf = Unpooled.buffer();
            LookAt.Creature.STREAM_CODEC.encode(buf, c);
            h.assertTrue(LookAt.Creature.STREAM_CODEC.decode(buf).equals(c), "creature facts travel intact");
            h.succeed();
        });
    }
}
