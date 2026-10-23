package dev.awaken.gates.run;

import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.FeatureBlocks;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Hash;
import dev.awaken.gates.world.Room;
import dev.awaken.gates.world.RoomGeometry;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Rooms waking up: monsters appear when a player first steps into a room, arenas seal until empty, the Lord's arena
 * seals until the Lord falls, shrines open when the floor's condition is met. Server thread only.
 */
public final class Rooms {
    private Rooms() {}

    public static final String SPIRIT_TAG = "awaken_gate_spirit";
    public static final String LORD_TAG = "awaken_gate_friendly_lord";
    /** Hard cap on live gate monsters per gate (SPEC: ≤ 60 active combat entities overall). */
    public static final int GATE_CAP = 40;

    static Theme themeOf(GateRecord g, FloorLayout f) {
        return f.theme;
    }

    static Set<Forces.Tag> tags(GateRecord g, FloorLayout f) {
        Set<Forces.Tag> s = EnumSet.noneOf(Forces.Tag.class);
        s.addAll(themeOf(g, f).mobTags());
        for (Modifier m : g.plan.modifiers()) if (m.tag != null) s.add(m.tag);
        return s;
    }

    /** Spawns one gate monster, tracked in its room (room -1 = floor-wide: waves, wardens, listeners). */
    public static LivingEntity spawn(ServerLevel level, GateRecord g, FloorLayout f, int roomKey, MobRole.Role role, BlockPos pos) {
        if (GateMobs.inGate(g.id) >= GATE_CAP && role != MobRole.Role.BOSS) return null;
        int lv = Progression.contentLevel(g.plan.rank());
        LivingEntity e;
        if (role == MobRole.Role.BOSS) {
            String boss = f.area == 1 && g.plan.is(Twist.GATE_WITHIN) ? null : g.plan.boss();
            e = boss == null ? MobHooks.spawnThemed(level, themeOf(g, f).themeId(), MobRole.Role.WARDEN, pos, lv, tags(g, f), g.key())
                    : MobHooks.spawnBoss(level, boss, pos, lv, g.key());
        } else {
            e = MobHooks.spawnThemed(level, themeOf(g, f).themeId(), role, pos, lv, tags(g, f), g.key());
        }
        if (e == null) return null;
        GateMobs.register(e, new GateMobs.Key(g.id, f.area, f.floor, roomKey));
        Modifiers.onSpawn(g, e);
        return e;
    }

    /** World positions of open floor spots in a room, best first. */
    public static List<BlockPos> spots(ServerLevel level, GateRecord g, FloorLayout f, Room r) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] s : r.spawnSpots) {
            BlockPos p = GateService.roomFloor(g, f, r, s[0], s[1]);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) out.add(p);
        }
        if (out.isEmpty()) out.add(GateService.roomFloor(g, f, r, r.centerX(), r.centerZ()));
        return out;
    }

    static int spawnGroup(ServerLevel level, GateRecord g, FloorLayout f, Room r, int roomKey, int normals, int elites, int offset) {
        List<BlockPos> spots = spots(level, g, f, r);
        int n = 0;
        for (int i = 0; i < normals + elites; i++) {
            BlockPos p = spots.get((i + offset) % spots.size());
            if (spawn(level, g, f, roomKey, i < elites ? MobRole.Role.ELITE : MobRole.Role.NORMAL, p) != null) n++;
        }
        return n;
    }

    // ---- triggers ---------------------------------------------------------------------------------------------------

    /** A player stepped into room r (zone = slot index inside caverns). */
    static void enter(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, int floorX, int floorZ) {
        FloorState fs = g.floor(f.area, f.floor);
        int key = r.id;
        if (r.type == Room.Type.CAVERN) {
            int zone = (floorX >> 4) - r.gx + ((floorZ >> 4) - r.gz) * r.gw;
            key = cavernKey(r, zone);
            if (fs.triggered.add(key)) {
                int count = Hash.unit(r.seed, zone, 3) < 0.6 ? Math.max(1, Math.round(r.normals * 1.6F / r.area())) : 0;
                if (count > 0) spawnGroup(level, g, f, r, key, count, zone % 7 == 3 ? 1 : 0, zone * 3);
                GateRegistry.get(level.getServer()).setDirty();
            }
            return;
        }
        if (!fs.triggered.add(key)) return;
        GateRegistry.get(level.getServer()).setDirty();
        switch (r.type) {
            case ARENA -> {
                lock(level, g, f, r, true);
                fs.waves.put(r.id, 1);
                spawnGroup(level, g, f, r, r.id, r.normals, r.elites, 0);
                Signal.toast(p, Component.literal("The arena seals."));
            }
            case BOSS -> {
                lock(level, g, f, r, true);
                Twists.bossArena(level, p, g, f, r);
            }
            default -> {
                if (r.type.combat() && r.normals + r.elites > 0) spawnGroup(level, g, f, r, r.id, r.normals, r.elites, 0);
                else fs.clearedRooms.add(r.id);
            }
        }
        if (fs.triggered.contains(r.id) && GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, r.id)) == 0 && r.type.combat()
                && r.type != Room.Type.BOSS) {
            emptied(level, g, f, r);
        }
    }

    /** First time anyone sets foot on a floor: things that should already be there when you arrive. */
    static void floorFirstEntered(ServerLevel level, GateRecord g, FloorLayout f) {
        FloorState fs = g.floor(f.area, f.floor);
        if (g.plan.layout() == Layout.STEALTH_HALLS && f.area == 0 && !f.bossFloor && !g.plan.friendly()) {
            for (Room r : f.rooms) {
                if ((r.type == Room.Type.HALL || r.type == Room.Type.NORMAL || r.type == Room.Type.CACHE) && r.normals + r.elites > 0) {
                    fs.triggered.add(r.id);
                    List<BlockPos> spots = spots(level, g, f, r);
                    for (int i = 0; i < r.normals + r.elites; i++) {
                        LivingEntity e = spawn(level, g, f, r.id, i < r.elites ? MobRole.Role.ELITE : MobRole.Role.NORMAL, spots.get(i % spots.size()));
                        var fr = e == null ? null : e.getAttribute(Attributes.FOLLOW_RANGE);
                        if (fr != null) fr.addOrUpdateTransientModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_sentry"), -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                }
            }
        }
        if (g.plan.friendly() && f.area == 0) friendlyNpcs(level, g, f);
        Twists.floorFirstEntered(level, g, f);
        GateRegistry.get(level.getServer()).setDirty();
    }

    /** A tracked monster died or vanished; check its room. */
    static void mobGone(ServerLevel level, GateMobs.Key key) {
        if (key.room() < 0) return;
        if (GateMobs.alive(key) > 0) return;
        GateRecord g = GateRegistry.get(level.getServer()).gate(key.gate());
        if (g == null) return;
        FloorLayout f = GateLayout.of(g.plan).floor(key.area(), key.floor());
        if (f == null) return;
        if (key.room() >= 256) return; // cavern zones gate nothing
        Room r = f.room(key.room());
        if (r == null || r.type == Room.Type.BOSS) return;
        emptied(level, g, f, r);
    }

    /** Trigger key of one zone (slot) of a cavern: always ≥ 256, so it never collides with a room id. */
    static int cavernKey(Room r, int zone) {
        return ((r.id + 1) << 8) | Math.max(0, Math.min(255, zone));
    }

    static void emptied(ServerLevel level, GateRecord g, FloorLayout f, Room r) {
        FloorState fs = g.floor(f.area, f.floor);
        if (r.type == Room.Type.ARENA) {
            int wave = fs.waves.getOrDefault(r.id, 1);
            if (wave == 1 && r.wave2Normals + r.wave2Elites > 0) {
                fs.waves.put(r.id, 2);
                spawnGroup(level, g, f, r, r.id, r.wave2Normals, r.wave2Elites, 5);
                broadcast(level, g, f, Component.literal("Second wave."));
                GateRegistry.get(level.getServer()).setDirty();
                return;
            }
            lock(level, g, f, r, false);
        }
        if (fs.clearedRooms.add(r.id)) {
            GateRegistry.get(level.getServer()).setDirty();
            if (r.type == Room.Type.KEY) {
                boolean all = true;
                for (Room o : f.rooms) if (o.type == Room.Type.KEY && !fs.clearedRooms.contains(o.id)) all = false;
                broadcast(level, g, f, Component.literal(all ? "Three keys. The shrine opens." : "A key falls silent."));
                if (all) unsealShrine(level, g, f);
            }
        }
    }

    // ---- doors ------------------------------------------------------------------------------------------------------

    /** Bars every doorway of a room (arena and Lord fights) or opens them again. */
    public static void lock(ServerLevel level, GateRecord g, FloorLayout f, Room r, boolean on) {
        FloorState fs = g.floor(f.area, f.floor);
        if (on) fs.locked.add(r.id);
        else fs.locked.remove(r.id);
        BlockState bars = GatesRegistry.BARS.get().defaultBlockState();
        for (Room.Door d : r.doors) {
            for (BlockPos p : barsPositions(g, f, d)) {
                BlockState here = level.getBlockState(p);
                if (on && here.isAir()) level.setBlock(p, bars, Block.UPDATE_ALL);
                else if (!on && here.is(GatesRegistry.BARS.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockPos c = GateService.roomFloor(g, f, r, r.centerX(), r.centerZ());
        level.playSound(null, c, GatesRegistry.SND_BARS.get(), SoundSource.BLOCKS, 1.2F, on ? 0.8F : 1.3F);
        GateRegistry.get(level.getServer()).setDirty();
    }

    public static List<BlockPos> barsPositions(GateRecord g, FloorLayout f, Room.Door d) {
        List<BlockPos> out = new ArrayList<>();
        for (int lat = RoomGeometry.DOOR_LO; lat <= RoomGeometry.DOOR_HI; lat++) {
            int x, z;
            switch (d.side) {
                case 0 -> { x = d.gx * 16 + lat; z = d.gz * 16; }
                case 2 -> { x = d.gx * 16 + lat; z = d.gz * 16 + 15; }
                case 1 -> { x = d.gx * 16 + 15; z = d.gz * 16 + lat; }
                default -> { x = d.gx * 16; z = d.gz * 16 + lat; }
            }
            for (int y = 0; y < RoomGeometry.DOOR_HEIGHT; y++) out.add(GateService.world(g, f, x, d.y + y, z));
        }
        return out;
    }

    /** The shrine's sealed doorways open (keys, puzzles or the spirit's lanterns). */
    public static void unsealShrine(ServerLevel level, GateRecord g, FloorLayout f) {
        FloorState fs = g.floor(f.area, f.floor);
        if (fs.shrineOpen) return;
        fs.shrineOpen = true;
        Room end = f.room(f.end);
        for (Room.Door d : end.doors) {
            for (BlockPos p : barsPositions(g, f, d)) {
                if (level.getBlockState(p).is(GatesRegistry.BARS.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockPos c = GateService.roomFloor(g, f, end, end.centerX(), end.centerZ());
        level.playSound(null, c, GatesRegistry.SND_SOLVED.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
        GateRegistry.get(level.getServer()).setDirty();
    }

    // ---- puzzles ----------------------------------------------------------------------------------------------------

    /** A rune was pressed in a puzzle room. */
    static void pressRune(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, Room.Feature rune) {
        FloorState fs = g.floor(f.area, f.floor);
        if (fs.puzzlesSolved.contains(r.id)) return;
        List<Room.Feature> runes = r.features(Room.FeatureKind.RUNE);
        int n = runes.size();
        int mask = fs.runes.getOrDefault(r.id, dev.awaken.gates.world.Puzzles.initialMask(r, n));
        mask = dev.awaken.gates.world.Puzzles.press(mask, rune.index(), n);
        fs.runes.put(r.id, mask);
        for (Room.Feature ft : runes) {
            BlockPos bp = GateService.roomFloor(g, f, r, ft.x(), ft.z()).above(ft.y());
            BlockState st = level.getBlockState(bp);
            if (st.is(GatesRegistry.RUNE.get())) level.setBlock(bp, st.setValue(GateBlocks.Rune.LIT, (mask & (1 << ft.index())) != 0), Block.UPDATE_ALL);
        }
        level.playSound(null, p.blockPosition(), GatesRegistry.SND_RUNE.get(), SoundSource.BLOCKS, 1F, 0.8F + 0.1F * Integer.bitCount(mask));
        if (dev.awaken.gates.world.Puzzles.solved(mask, n)) {
            fs.puzzlesSolved.add(r.id);
            fs.clearedRooms.add(r.id);
            Room.Feature vault = r.feature(Room.FeatureKind.VAULT_CACHE);
            if (vault != null) {
                for (int[] b : FeatureBlocks.vaultBars(vault)) {
                    BlockPos bp = GateService.world(g, f, b[0], r.dy + b[1], b[2]);
                    if (level.getBlockState(bp).is(GatesRegistry.BARS.get())) level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, p.blockPosition(), GatesRegistry.SND_SOLVED.get(), SoundSource.BLOCKS, 1.4F, 1.2F);
            int total = 0, solved = 0;
            for (Room o : f.rooms) if (o.type == Room.Type.PUZZLE) { total++; if (fs.puzzlesSolved.contains(o.id)) solved++; }
            broadcast(level, g, f, Component.literal(solved >= total ? "The runes agree. The shrine opens." : "The runes agree. " + (total - solved) + " to go."));
            if (solved >= total && g.plan.layout() == Layout.PUZZLE_VAULT && !g.plan.friendly()) unsealShrine(level, g, f);
        }
        GateRegistry.get(level.getServer()).setDirty();
    }

    // ---- friendly gates ---------------------------------------------------------------------------------------------

    static void friendlyNpcs(ServerLevel level, GateRecord g, FloorLayout f) {
        for (Room r : f.rooms) {
            for (Room.Feature ft : r.features) {
                BlockPos p = GateService.roomFloor(g, f, r, ft.x(), ft.z());
                switch (ft.kind()) {
                    case SPIRIT_SPOT -> {
                        Allay a = EntityTypes.ALLAY.create(level, EntitySpawnReason.TRIGGERED);
                        if (a == null) break;
                        a.snapTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, 0);
                        npc(a, "A lost spirit");
                        a.getPersistentData().putInt(SPIRIT_TAG, g.id);
                        level.addFreshEntity(a);
                    }
                    case MARKET_SPOT -> {
                        WanderingTrader t = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.TRIGGERED);
                        if (t == null) break;
                        t.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ft.index() == 0 ? 90 : -90, 0);
                        npc(t, ft.index() == 0 ? "Lantern seller" : "Night trader");
                        market(t, g, ft.index());
                        level.addFreshEntity(t);
                    }
                    case BOSS_SPOT -> {
                        WanderingTrader t = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.TRIGGERED);
                        if (t == null) break;
                        t.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
                        npc(t, capital(g.plan.theme().bossName));
                        market(t, g, 2);
                        t.getPersistentData().putInt(LORD_TAG, g.id);
                        t.setGlowingTag(true);
                        level.addFreshEntity(t);
                    }
                    default -> {}
                }
            }
        }
    }

    private static void npc(Mob m, String name) {
        m.setNoAi(true);
        m.setPersistenceRequired();
        m.setPermanentlyInvulnerable(true);
        m.setCustomName(Component.literal(name));
        m.setCustomNameVisible(true);
    }

    public static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void market(WanderingTrader t, GateRecord g, int stall) {
        MerchantOffers offers = t.getOffers();
        offers.clear();
        var shard = CoreApi.ESSENCE_SHARD.get();
        switch (stall) {
            case 0 -> {
                offers.add(new MerchantOffer(new ItemCost(shard, 2), new ItemStack(Items.COOKED_BEEF, 8), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 3), new ItemStack(Items.GOLDEN_APPLE, 1), 6, 4, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 1), new ItemStack(Items.TORCH, 16), 16, 1, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(shard, 1), 16, 1, 0.05F));
            }
            case 1 -> {
                offers.add(new MerchantOffer(new ItemCost(shard, 5), new ItemStack(Items.DIAMOND, 1), 4, 6, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 2), new ItemStack(Items.ENDER_PEARL, 2), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 3), new ItemStack(Items.EXPERIENCE_BOTTLE, 6), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.GOLD_INGOT, 4), new ItemStack(shard, 1), 16, 1, 0.05F));
            }
            default -> {
                offers.add(new MerchantOffer(new ItemCost(shard, 12), new ItemStack(CoreApi.LORD_HEART.get(), 1), 1, 20, 0.0F));
                offers.add(new MerchantOffer(new ItemCost(shard, 6), new ItemStack(Items.NETHERITE_SCRAP, 1), 2, 10, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 4), new ItemStack(Items.GOLDEN_APPLE, 2), 4, 6, 0.05F));
            }
        }
    }

    // ---- misc -------------------------------------------------------------------------------------------------------

    static void broadcast(ServerLevel level, GateRecord g, FloorLayout f, Component msg) {
        for (ServerPlayer p : level.players()) {
            RunState run = Runs.run(p);
            if (run.gateId == g.id && run.area == f.area && run.floor == f.floor) p.sendOverlayMessage(msg);
        }
    }
}
