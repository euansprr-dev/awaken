package dev.awaken.compat;

import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
import dev.awaken.core.AwakenConfig.Client.LensLook;
import dev.awaken.compat.Look.Tier;
import dev.awaken.compat.Look.DhSetting;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
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
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
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
import java.util.UUID;

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
            ServerPlayer sp = survivalPlayer(h);
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
            h.getLevel().getServer().getPlayerList().remove(sp);
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

        AwakenTests.register("compat_look_tiers", 20, h -> {
            h.assertTrue(Tier.SWIFT.lens == LensLook.SWIFT && !Tier.SWIFT.irisShaders, "Swift: Lens Swift, no shaders");
            h.assertTrue(Tier.CINEMATIC.lens == LensLook.CINEMATIC && !Tier.CINEMATIC.irisShaders, "Cinematic: Lens Cinematic, no shaders");
            h.assertTrue(Tier.ULTRA.lens == LensLook.ULTRA && !Tier.ULTRA.irisShaders, "Ultra: Lens Ultra, no shaders (SPEC §22)");
            for (Tier t : Tier.values()) {
                List<DhSetting> dh = Look.dh(t);
                h.assertTrue(dh.size() == Look.DH_KEYS.size(), t + " sets every owned DH key");
                for (int i = 0; i < dh.size(); i++) h.assertTrue(dh.get(i).key() == Look.DH_KEYS.get(i), t + " DH key order " + i);
            }
            // the switch owns only keys the tiers disagree on: the rest (threads, cloud layers) stay the player's
            for (int i = 0; i < Look.DH_KEYS.size(); i++) {
                h.assertFalse(Look.dh(Tier.SWIFT).get(i).value().equals(Look.dh(Tier.CINEMATIC).get(i).value()),
                        "owned key differs between Swift and Cinematic: " + Look.DH_KEYS.get(i).key());
            }
            h.assertTrue(Look.dh(Tier.ULTRA).equals(Look.dh(Tier.CINEMATIC)), "Ultra keeps Cinematic's horizon");
            Map<String, String> swift = Map.of("lodChunkRenderDistanceRadius", "128", "maxHorizontalResolution", "\"TWO_BLOCKS\"",
                    "verticalQuality", "\"LOW\"", "horizontalQuality", "\"LOW\"", "enableSsao", "false", "enableTexturedLods", "false",
                    "enableAntiAliasing", "false");
            Map<String, String> cinematic = Map.of("lodChunkRenderDistanceRadius", "192", "maxHorizontalResolution", "\"BLOCK\"",
                    "verticalQuality", "\"MEDIUM\"", "horizontalQuality", "\"MEDIUM\"", "enableSsao", "true", "enableTexturedLods", "true",
                    "enableAntiAliasing", "true");
            for (DhSetting s : Look.dh(Tier.SWIFT)) h.assertTrue(s.toml().equals(swift.get(s.key().key())), "Swift " + s.key().key() + " = " + s.toml());
            for (DhSetting s : Look.dh(Tier.CINEMATIC)) h.assertTrue(s.toml().equals(cinematic.get(s.key().key())), "Cinematic " + s.key().key() + " = " + s.toml());
            // the installer's presets (pack/dh/*.json, when the project folder is next to the test run) say the same
            for (Tier t : new Tier[]{Tier.SWIFT, Tier.CINEMATIC}) {
                Path preset = Path.of("..", "pack", "dh", t.name().toLowerCase(java.util.Locale.ROOT) + ".json");
                if (!Files.exists(preset)) continue;
                try {
                    JsonObject json = JsonParser.parseString(Files.readString(preset)).getAsJsonObject();
                    for (DhSetting s : Look.dh(t)) {
                        if (json.has(s.key().key())) h.assertTrue(json.get(s.key().key()).getAsString().equals(s.toml()),
                                preset + " agrees on " + s.key().key());
                    }
                } catch (java.io.IOException e) {
                    h.fail("can't read " + preset + ": " + e);
                }
            }
            h.assertTrue(Look.current(LensLook.SWIFT, false) == Tier.SWIFT && Look.current(LensLook.CINEMATIC, false) == Tier.CINEMATIC
                    && Look.current(LensLook.ULTRA, false) == Tier.ULTRA && Look.current(LensLook.OFF, false) == null
                    && Look.current(LensLook.SWIFT, true) == null, "current tier"); 
            h.assertTrue(Boolean.TRUE.equals(Look.fits(Tier.SWIFT, 8.0f, 14f)) && Boolean.FALSE.equals(Look.fits(Tier.SWIFT, 10f, 14f))
                    && Boolean.FALSE.equals(Look.fits(Tier.SWIFT, 8f, 20f)) && Boolean.TRUE.equals(Look.fits(Tier.CINEMATIC, 10.5f, 19f))
                    && Boolean.FALSE.equals(Look.fits(Tier.CINEMATIC, 12f, 19f)) && Boolean.TRUE.equals(Look.fits(Tier.ULTRA, 15f, 24f)),
                    "frame budgets");
            h.assertTrue(Look.dhMatches(Tier.SWIFT, k -> k == Look.RADIUS ? 128 : null), "DH matches Swift");
            h.assertFalse(Look.dhMatches(Tier.SWIFT, k -> k == Look.RADIUS ? 256 : null), "DH radius changed in its own menu");
            h.assertTrue(Look.dhMatches(Tier.CINEMATIC, k -> k == Look.MAX_RESOLUTION ? "BLOCK" : k == Look.SSAO ? Boolean.TRUE : null),
                    "DH matches Cinematic by enum name and boolean");
            h.succeed();
        });

        AwakenTests.register("compat_look_dh_file", 20, h -> {
            String toml = String.join("\n",
                    "_version = 5",
                    "[server]",
                    "\t[common.multiThreading]",
                    "\t\tnumberOfThreads = 5",
                    "[client]",
                    "\t[client.advanced.debugging]",
                    "\t\t# enableSsao = true is not this one",
                    "\t\tenableSsao = true",
                    "\t[client.advanced.graphics]",
                    "\t\t#",
                    "\t\t# Enabling Anti-Aliasing smooths edges.",
                    "\t\tenableAntiAliasing = true",
                    "\t\tenableSsao = true",
                    "\t\t[client.advanced.graphics.texture]",
                    "\t\t\tenableTexturedLods = true",
                    "\t\t[client.advanced.graphics.quality]",
                    "\t\t\tmaxHorizontalResolution = \"BLOCK\"",
                    "\t\t\tverticalQuality = \"MEDIUM\"",
                    "\t\t\t# lodChunkRenderDistanceRadius = 999",
                    "\t\t\tlodChunkRenderDistanceRadius = 256",
                    "\t\t\thorizontalQuality = \"MEDIUM\"",
                    "\t\t\tbrightnessMultiplier = \"1.0\"",
                    "");
            Look.Edit swift = Look.writeDh(toml, Look.dh(Tier.SWIFT));
            String[] before = toml.split("\n", -1), after = swift.text().split("\n", -1);
            h.assertTrue(before.length == after.length, "no lines added or lost: " + before.length + " vs " + after.length);
            int differ = 0;
            for (int i = 0; i < before.length; i++) if (!before[i].equals(after[i])) differ++;
            h.assertTrue(differ == 7, "exactly the seven owned keys changed: " + differ);
            h.assertTrue(swift.changed().size() == 7 && swift.added().isEmpty(), "changed " + swift.changed() + " added " + swift.added());
            h.assertTrue(after[7].equals("\t\tenableSsao = true"), "a same-named key in another table is untouched");
            h.assertTrue(after[6].equals(before[6]) && after[18].equals(before[18]), "comments untouched");
            h.assertTrue(after[3].equals("\t\tnumberOfThreads = 5") && after[21].equals("\t\t\tbrightnessMultiplier = \"1.0\""),
                    "keys the switch doesn't own are untouched");
            h.assertTrue(after[11].equals("\t\tenableAntiAliasing = false") && after[12].equals("\t\tenableSsao = false")
                    && after[14].equals("\t\t\tenableTexturedLods = false") && after[16].equals("\t\t\tmaxHorizontalResolution = \"TWO_BLOCKS\"")
                    && after[17].equals("\t\t\tverticalQuality = \"LOW\"") && after[19].equals("\t\t\tlodChunkRenderDistanceRadius = 128")
                    && after[20].equals("\t\t\thorizontalQuality = \"LOW\""), "Swift's values in place, indentation kept");
            Look.Edit again = Look.writeDh(swift.text(), Look.dh(Tier.SWIFT));
            h.assertTrue(again.text().equals(swift.text()) && again.changed().isEmpty(), "writing the same tier twice changes nothing");
            Look.Edit back = Look.writeDh(swift.text(), Look.dh(Tier.CINEMATIC));
            h.assertTrue(back.text().replace("lodChunkRenderDistanceRadius = 192", "lodChunkRenderDistanceRadius = 256").equals(toml),
                    "Swift then Cinematic gives back the original file (apart from Cinematic's 192-chunk horizon)");

            // a file missing keys and a table: keys go under their header, a missing table at the end; CRLF kept
            String partial = "[client]\r\n\t[client.advanced.graphics]\r\n\t\tenableSsao = true\r\n";
            Look.Edit filled = Look.writeDh(partial, Look.dh(Tier.CINEMATIC));
            h.assertTrue(filled.added().size() == 6, "added " + filled.added());
            h.assertTrue(filled.text().startsWith("[client]\r\n\t[client.advanced.graphics]\r\n\t\tenableAntiAliasing = true\r\n\t\tenableSsao = true\r\n"),
                    "missing key under its header: " + filled.text().replace("\r", "\\r").replace("\n", "\\n"));
            h.assertTrue(filled.text().contains("[client.advanced.graphics.quality]\r\n\tlodChunkRenderDistanceRadius = 192\r\n\tmaxHorizontalResolution = \"BLOCK\"\r\n")
                    && filled.text().contains("[client.advanced.graphics.texture]\r\n\tenableTexturedLods = true\r\n") && filled.text().endsWith("\r\n"),
                    "missing tables appended with their keys in order");
            Look.Edit refill = Look.writeDh(filled.text(), Look.dh(Tier.CINEMATIC));
            h.assertTrue(refill.text().equals(filled.text()) && refill.added().isEmpty(), "a filled file stays as it is");
            h.succeed();
        });

        AwakenTests.register("compat_look_ultra_gate", 20, h -> {
            // every reason says Iris needs OpenGL; until a newer Iris is here, also Iris's Sodium pin
            Look.Availability none = Look.ultra(null, "OpenGL");
            h.assertTrue(!none.available() && none.reason().startsWith("Needs Iris.") && none.reason().contains("pins Sodium 0.9.2")
                    && none.reason().contains("needs OpenGL"), "no Iris: " + none.reason());
            Look.Availability noneVulkan = Look.ultra(null, "Vulkan");
            h.assertTrue(!noneVulkan.available() && noneVulkan.reason().contains("pins Sodium 0.9.2")
                    && noneVulkan.reason().contains("needs OpenGL; this game runs on Vulkan"), "no Iris on Vulkan says which API runs: " + noneVulkan.reason());
            Look.Availability bad = Look.ultra("1.11.7+26.3-neoforge", "Vulkan");
            h.assertTrue(!bad.available() && bad.reason().startsWith("Iris 1.11.7 pins Sodium 0.9.2")
                    && bad.reason().contains("needs OpenGL; this game runs on Vulkan"), "Iris 1.11.7: " + bad.reason());
            h.assertTrue(!Look.ultra("1.10.2", "OpenGL").available(), "an older Iris either");
            h.assertTrue(Look.ultra("1.11.8+26.3-neoforge", "OpenGL").available(), "a newer Iris on OpenGL");
            Look.Availability vulkan = Look.ultra("1.12.0", "Vulkan");
            h.assertTrue(!vulkan.available() && vulkan.reason().startsWith("Iris needs OpenGL; this game runs on Vulkan")
                    && vulkan.reason().contains("Graphics API") && !vulkan.reason().contains("Sodium"), "a newer Iris on Vulkan: " + vulkan.reason());
            // the Look screen shows three note lines (about 60 characters each)
            for (Look.Availability a : List.of(none, noneVulkan, bad, vulkan, Look.ultra("1.11.7", "OpenGL"))) {
                h.assertTrue(a.reason().length() <= 180, "reason fits the screen (" + a.reason().length() + "): " + a.reason());
            }
            h.assertTrue(Look.compareVersions("1.11.7+26.3-neoforge", "1.11.7") == 0 && Look.compareVersions("1.11.10", "1.11.7") > 0
                    && Look.compareVersions("1.12", "1.11.7") > 0 && Look.compareVersions("1.9.9-beta.1", "1.11.7") < 0, "version order");
            h.succeed();
        });

        AwakenTests.register("compat_look_frame_stats", 20, h -> {
            FrameStats stats = new FrameStats(32768);
            float[] out = new float[4];
            h.assertFalse(stats.percentiles(60_000_000_000L, 30, out), "nothing measured yet");
            for (int i = 0; i < 990; i++) stats.record(8_000_000L);
            for (int i = 0; i < 10; i++) stats.record(30_000_000L);
            h.assertTrue(stats.percentiles(60_000_000_000L, 30, out), "a thousand frames");
            h.assertTrue(Math.abs(out[0] - 8.05f) < 0.01f, "p50 " + out[0]);
            h.assertTrue(Math.abs(out[1] - 8.05f) < 0.01f, "p99 is the 990th frame: " + out[1]);
            h.assertTrue(out[3] == 1000 && Math.abs(out[2] - 8.22f) < 0.01f, "frames " + out[3] + " seconds " + out[2]);
            for (int i = 0; i < 20; i++) stats.record(30_000_000L);
            stats.percentiles(60_000_000_000L, 30, out);
            h.assertTrue(Math.abs(out[1] - 30.05f) < 0.01f, "3% slow frames reach p99: " + out[1]);
            // only the last minute counts: 70 s of 16 ms frames hide the earlier ones
            for (int i = 0; i < 4375; i++) stats.record(16_000_000L);
            stats.percentiles(60_000_000_000L, 30, out);
            h.assertTrue(out[2] <= 60.0f && out[2] > 59.9f && Math.abs(out[0] - 16.05f) < 0.01f && Math.abs(out[1] - 16.05f) < 0.01f,
                    "a 60 s window: " + out[2] + " s, p50 " + out[0] + ", p99 " + out[1]);
            // a frame beyond the histogram's range still counts, as its last bin
            stats.clear();
            for (int i = 0; i < 99; i++) stats.record(5_000_000L);
            stats.record(900_000_000L);
            stats.percentiles(60_000_000_000L, 30, out);
            h.assertTrue(Math.abs(out[1] - 5.05f) < 0.001f && out[3] == 100, "p99 of 100 is the 99th: " + out[1]);
            h.succeed();
        });
    }

    /**
     * A survival player in the test level: the helper's mock player answers "creative" to every game-mode question, and
     * the guard is off in creative. A plain ServerPlayer on an embedded connection (as the fauna tests make theirs).
     */
    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "compat-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
