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
 * Rooms waking up: nearby monsters wait visibly before entry, arenas seal until empty, the Lord's arena
 * seals until the Lord falls, shrines open when the floor's condition is met. Server thread only.
 */
public final class Rooms {
    private Rooms() {}

    public static final String SPIRIT_TAG = "awaken_gate_spirit";
    public static final String LORD_TAG = "awaken_gate_friendly_lord";
    /** Party scaling (SPEC "Together"): monsters per extra player, the Lord's health per extra player, the party counted up to. */
    public static final float PARTY_MOBS = 0.35F;
    public static final double PARTY_LORD_HEALTH = 0.5;
    public static final int PARTY_MAX = 4;

    /**
     * A party meets a bigger company (SPEC "Together"): +35% monsters per extra player (rounded), +1 elite per two extra
     * players (rooms with any monsters). Returns {normals, elites}.
     */
    public static int[] partyCompany(int normals, int elites, int party) {
        int extra = Math.max(0, Math.min(PARTY_MAX, party) - 1);
        if (extra == 0 || normals + elites == 0) return new int[]{normals, elites};
        return new int[]{Math.round(normals * (1 + PARTY_MOBS * extra)), elites + extra / 2};
    }

    /** Players of this gate on this floor right now (1 at least, {@link #PARTY_MAX} at most). */
    public static int partyOnFloor(ServerLevel level, GateRecord g, FloorLayout f) {
        int n = 0;
        for (java.util.UUID u : g.inside) {
            ServerPlayer o = level.getServer().getPlayerList().getPlayer(u);
            if (o == null || !o.isAlive() || o.isSpectator()) continue;
            RunState run = Runs.run(o);
            if (run.gateId == g.id && run.area == f.area && run.floor == f.floor) n++;
        }
        return Math.max(1, Math.min(PARTY_MAX, n));
    }

    /** Hard cap on live gate monsters per gate (SPEC: ≤ 60 active combat entities overall). */
    public static final int GATE_CAP = 40;
    /** Arena waves may go over GATE_CAP up to the SPEC budget: an arena seals you in, its waves must come. */
    static final int ARENA_CAP = 60;

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
        Room home = roomKey >= 0 && roomKey < 256 ? f.room(roomKey) : null;
        int cap = home != null && home.type == Room.Type.ARENA ? ARENA_CAP : GATE_CAP;
        if (GateMobs.inGate(g.id) >= cap && role != MobRole.Role.BOSS) return null;
        // the story's first gate is fought at level 1 with ordinary gear and one power: content level 0, softened below
        int lv = g.scripted ? 0 : Progression.contentLevel(g.plan.rank());
        LivingEntity e;
        if (role == MobRole.Role.BOSS) {
            String boss = f.area == 1 && g.plan.is(Twist.GATE_WITHIN) ? null : g.plan.lordId();
            e = boss == null ? MobHooks.spawnThemed(level, themeOf(g, f).themeId(), MobRole.Role.WARDEN, pos, lv, tags(g, f), g.key())
                    : MobHooks.spawnBoss(level, boss, pos, lv, g.key());
        } else {
            e = MobHooks.spawnThemed(level, themeOf(g, f).themeId(), role, pos, lv, tags(g, f), g.key());
        }
        if (e == null) return null;
        if (g.scripted && role != MobRole.Role.BOSS && e.isNoGravity()) {
            // no flyers in the first gate: diving wisps were the deaths in the first playtest. Ask the roster again.
            e.discard();
            e = MobHooks.spawnThemed(level, themeOf(g, f).themeId(), role, pos, lv, tags(g, f), g.key());
            if (e != null && e.isNoGravity()) {
                e.discard();
                return null;
            }
            if (e == null) return null;
        }
        if (g.scripted) firstGate(e, role);
        else if (role == MobRole.Role.BOSS) partyLord(e, partyOnFloor(level, g, f));
        GateMobs.register(e, new GateMobs.Key(g.id, f.area, f.floor, roomKey));
        dev.awaken.gates.worlds.WorldsLive.dress(e, f, home);
        Modifiers.onSpawn(g, e);
        Encounters.hold(e, new GateMobs.Key(g.id, f.area, f.floor, roomKey), role == MobRole.Role.BOSS);
        if (role == MobRole.Role.BOSS && f.area == 0) dev.awaken.core.api.ArmsApi.lordWields(e);
        if (role != MobRole.Role.BOSS && !g.scripted) dev.awaken.core.api.ArmsApi.gateSpawn(e, g.key(), f.area, f.floor, role == MobRole.Role.ELITE); // the bearer of the gate's pattern (docs/arms-progression/PLAN.md §3.4)
        return e;
    }

    /** A Lord met by a party: +50% health per extra player (the hoard is already one per player). */
    static void partyLord(LivingEntity e, int party) {
        if (party <= 1) return;
        var h = e.getAttribute(Attributes.MAX_HEALTH);
        if (h != null) h.addOrReplacePermanentModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_party_health"),
                PARTY_LORD_HEALTH * (party - 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        e.setHealth(e.getMaxHealth());
    }

    /** First-gate tuning: a Lord you can beat with iron and one power (about 190 health), monsters a little softer. */
    private static void firstGate(LivingEntity e, MobRole.Role role) {
        double hp = role == MobRole.Role.BOSS ? -0.55 : -0.3;
        var h = e.getAttribute(Attributes.MAX_HEALTH);
        if (h != null) h.addOrReplacePermanentModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_first_gate_health"), hp, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        var d = e.getAttribute(Attributes.ATTACK_DAMAGE);
        if (d != null) d.addOrReplacePermanentModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_first_gate_damage"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        e.setHealth(e.getMaxHealth());
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
        FloorState fs = g.floor(f.area, f.floor);
        if (!fs.pendingCompanies.containsKey(roomKey)) {
            if (g.scripted) {
                normals = (normals + 1) / 2;
                elites = Math.min(elites, 1);
            } else {
                int[] company = partyCompany(normals, elites, partyOnFloor(level, g, f));
                normals = company[0];
                elites = company[1];
            }
            // Freeze this wave's party scaling and progress once, even if capacity becomes available over several visits.
            fs.pendingCompanies.put(roomKey, new FloorState.Company(normals, elites, offset, 0));
            GateRegistry.get(level.getServer()).setDirty();
        }
        return resumeGroup(level, g, f, r, roomKey);
    }

    private static int resumeGroup(ServerLevel level, GateRecord g, FloorLayout f, Room r, int roomKey) {
        FloorState fs = g.floor(f.area, f.floor);
        FloorState.Company company = fs.pendingCompanies.get(roomKey);
        if (company == null) return 0;
        int cap = r.type == Room.Type.ARENA ? ARENA_CAP : GATE_CAP;
        if (company.next < company.normals + company.elites && GateMobs.inGate(g.id) >= cap) return 0;
        List<BlockPos> spots;
        if (roomKey >= 256) {
            int zone = roomKey & 255, gx = r.gx + zone % r.gw, gz = r.gz + zone / r.gw;
            spots = new ArrayList<>();
            for (int[] at : r.spawnSpots) if ((at[0] >> 4) == gx && (at[1] >> 4) == gz) {
                BlockPos pos = GateService.roomFloor(g, f, r, at[0], at[1]);
                if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) spots.add(pos);
            }
            if (spots.isEmpty()) spots.add(GateService.roomFloor(g, f, r, gx * 16 + 8, gz * 16 + 8));
        } else spots = spots(level, g, f, r);
        int n = 0;
        while (company.next < company.normals + company.elites && GateMobs.inGate(g.id) < cap) {
            int i = company.next++;
            BlockPos at = spots.get((i + company.offset) % spots.size());
            if (spawn(level, g, f, roomKey, i < company.elites ? MobRole.Role.ELITE : MobRole.Role.NORMAL, at) != null) n++;
        }
        if (company.next == company.normals + company.elites) fs.pendingCompanies.remove(roomKey);
        GateRegistry.get(level.getServer()).setDirty();
        return n;
    }

    // ---- triggers ---------------------------------------------------------------------------------------------------

    /** Populate the current room and its connected neighbours before a player reaches their doorways. */
    static void prepareAround(ServerLevel level, GateRecord g, FloorLayout f, Room current) {
        prepare(level, g, f, current);
        for (Room.Door door : current.doors) {
            Room next = f.room(door.toRoom);
            // a neighbour waits for its chunks: a preview never reads or loads one (SPEC §38.1); entry prepares it anyway
            if (next != null && loaded(level, g, f, next)) prepare(level, g, f, next);
        }
    }

    /** Whether every spot a room's company could take (its spawn spots, centre and Lord's spot) is in a loaded chunk. */
    static boolean loaded(ServerLevel level, GateRecord g, FloorLayout f, Room r) {
        var chunks = level.getChunkSource();
        BlockPos c = GateService.roomFloor(g, f, r, r.centerX(), r.centerZ());
        if (chunks.getChunkNow(c.getX() >> 4, c.getZ() >> 4) == null) return false;
        Room.Feature lord = r.feature(Room.FeatureKind.BOSS_SPOT);
        if (lord != null) {
            BlockPos at = GateService.roomFloor(g, f, r, lord.x(), lord.z());
            if (chunks.getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) return false;
        }
        for (int[] spot : r.spawnSpots) {
            BlockPos at = GateService.roomFloor(g, f, r, spot[0], spot[1]);
            if (chunks.getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) return false;
        }
        return true;
    }

    static void prepare(ServerLevel level, GateRecord g, FloorLayout f, Room r) {
        FloorState fs = g.floor(f.area, f.floor);
        if (fs.clearedRooms.contains(r.id)) return;
        // Later arena waves can also wait for the shared budget; resume their unspawned members, never the whole wave.
        if (fs.prepared.contains(r.id)) {
            if (fs.pendingCompanies.containsKey(r.id)) {
                resumeGroup(level, g, f, r, r.id);
                if (GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, r.id)) == 0) emptied(level, g, f, r);
            }
            return;
        }
        if (r.type == Room.Type.CAVERN) {
            boolean complete = true;
            for (int zone = 0; zone < r.area(); zone++) {
                int key = cavernKey(r, zone);
                if (fs.prepared.contains(key) || fs.clearedRooms.contains(key)) continue;
                int count = Hash.unit(r.seed, zone, 3) < 0.6 ? Math.max(1, Math.round(r.normals * 1.6F / r.area())) : 0;
                spawnGroup(level, g, f, r, key, count, count > 0 && zone % 7 == 3 ? 1 : 0, zone * 3);
                if (fs.pendingCompanies.containsKey(key)) complete = false;
                else fs.prepared.add(key);
            }
            if (complete) fs.prepared.add(r.id);
        } else {
            if (r.type == Room.Type.BOSS) {
                Twists.prepareBoss(level, g, f, r);
                resumeGroup(level, g, f, r, r.id);
            } else if (r.type.combat()) {
                if (r.type == Room.Type.ARENA) fs.waves.putIfAbsent(r.id, 1);
                spawnGroup(level, g, f, r, r.id, r.normals, r.elites, 0);
            }
            if (!fs.pendingCompanies.containsKey(r.id)) {
                fs.prepared.add(r.id);
                if (fs.triggered.contains(r.id) && r.type.combat() && r.type != Room.Type.BOSS
                        && GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, r.id)) == 0) emptied(level, g, f, r);
            }
        }
        GateRegistry.get(level.getServer()).setDirty();
    }

    /** A player stepped into room r (zone = slot index inside caverns). Preparation never seals a doorway. */
    static void enter(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, int floorX, int floorZ) {
        prepare(level, g, f, r);
        FloorState fs = g.floor(f.area, f.floor);
        int key = r.type == Room.Type.CAVERN
                ? cavernKey(r, (floorX >> 4) - r.gx + ((floorZ >> 4) - r.gz) * r.gw) : r.id;
        boolean first = fs.triggered.add(key);
        if (first) Encounters.wake(level, g, f, key);
        if (r.type == Room.Type.CAVERN) {
            if (!fs.pendingCompanies.containsKey(key) && GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, key)) == 0) fs.clearedRooms.add(key);
            if (first) GateRegistry.get(level.getServer()).setDirty();
            return;
        }
        if (!first) {
            if (!fs.locked.contains(r.id)) resealUnfinished(level, p, g, f, r, fs);
            return;
        }
        GateRegistry.get(level.getServer()).setDirty();
        switch (r.type) {
            case ARENA -> {
                if (GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, r.id)) == 0 && fs.pendingCompanies.containsKey(r.id)) break;
                gatherParty(level, p, g, f, r);
                lock(level, g, f, r, true);
                Signal.toast(p, Component.literal("The arena seals."));
            }
            case BOSS -> {
                gatherParty(level, p, g, f, r);
                lock(level, g, f, r, true);
                skyEdge(level, g, f);
                Twists.bossArena(level, p, g, f, r);
            }
            default -> {
                if (!r.type.combat()) fs.clearedRooms.add(r.id);
            }
        }
        if (GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, r.id)) == 0 && r.type.combat() && r.type != Room.Type.BOSS) {
            emptied(level, g, f, r);
        }
    }

    /** Walking back into an unfinished fight re-seals it. An empty runtime cache never creates another company. */
    private static void resealUnfinished(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r, FloorState fs) {
        if (r.type == Room.Type.ARENA && !fs.clearedRooms.contains(r.id)
                && GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, r.id)) > 0) {
            gatherParty(level, p, g, f, r);
            lock(level, g, f, r, true);
            Signal.toast(p, Component.literal("The arena seals."));
        } else if (r.type == Room.Type.BOSS && !(g.plan.friendly() && f.area == 0) && !(f.area == 0 ? g.bossDead : g.annexBossDead)) {
            gatherParty(level, p, g, f, r);
            lock(level, g, f, r, true);
            skyEdge(level, g, f);
            Twists.bossArena(level, p, g, f, r);
        }
    }

    /** Party members on this floor within this many blocks are drawn in when a room seals behind someone. */
    static final int GATHER_RADIUS = 48;

    /**
     * An arena or the Lord's room seals: the party isn't locked out (SPEC "Together"). Party members on the same floor
     * nearby who aren't in the room yet step through with the one who triggered it.
     */
    /** The Sky Leviathan's trial: everyone on the floor reaches the open sky's edge (the Ascent gives flight there). */
    private static void skyEdge(ServerLevel level, GateRecord g, FloorLayout f) {
        if (!g.plan.skybornTrial() || f.area != 0) return;
        for (ServerPlayer o : level.players()) {
            RunState run = Runs.run(o);
            if (run.gateId == g.id && run.area == f.area && run.floor == f.floor) dev.awaken.core.api.TrialHooks.atEdge(o, dev.awaken.core.api.TrialHooks.SKYBORN);
        }
    }

    static void gatherParty(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        for (java.util.UUID u : List.copyOf(g.inside)) {
            if (u.equals(p.getUUID())) continue;
            ServerPlayer mate = level.getServer().getPlayerList().getPlayer(u);
            if (mate == null || mate.level() != level || !mate.isAlive() || mate.isSpectator()) continue;
            if (!dev.awaken.core.api.PartyHooks.sameParty(p, mate)) continue;
            RunState run = Runs.run(mate);
            if (run.gateId != g.id || run.area != f.area || run.floor != f.floor) continue;
            if (mate.distanceToSqr(p) > GATHER_RADIUS * GATHER_RADIUS) continue;
            if (inRoom(level, mate, f, r)) continue;
            mate.teleportTo(level, p.getX(), p.getY(), p.getZ(), java.util.Set.of(), p.getYRot(), mate.getXRot(), true);
            Signal.toast(mate, Component.literal("You step through with " + p.getGameProfile().name() + " as the bars come down."));
        }
    }

    /**
     * A party member who reaches the bars of a sealed room their party is fighting in is let through (every 10 ticks,
     * from the run tick). Returns whether they were moved in.
     */
    static boolean admitAtBars(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f) {
        FloorState fs = g.floor(f.area, f.floor);
        if (fs.locked.isEmpty()) return false;
        for (int id : fs.locked) {
            Room r = f.room(id);
            if (r == null || (r.type != Room.Type.ARENA && r.type != Room.Type.BOSS)) continue;
            boolean atBars = false;
            for (Room.Door d : r.doors) {
                for (BlockPos b : barsPositions(g, f, d)) {
                    if (p.blockPosition().distManhattan(b) <= 2) { atBars = true; break; }
                }
                if (atBars) break;
            }
            if (!atBars) continue;
            if (inRoom(level, p, f, r)) return false;
            for (java.util.UUID u : g.inside) {
                ServerPlayer mate = level.getServer().getPlayerList().getPlayer(u);
                if (mate == null || mate == p || mate.level() != level || !mate.isAlive()) continue;
                if (!inRoom(level, mate, f, r) || !dev.awaken.core.api.PartyHooks.sameParty(p, mate)) continue;
                p.teleportTo(level, mate.getX(), mate.getY(), mate.getZ(), java.util.Set.of(), p.getYRot(), p.getXRot(), true);
                Signal.toast(p, Component.literal("The bars let you through to " + mate.getGameProfile().name() + "."));
                return true;
            }
        }
        return false;
    }

    static boolean inRoom(ServerLevel level, ServerPlayer p, FloorLayout f, Room r) {
        return inRoomAt(level, p.blockPosition(), f, r);
    }

    static boolean inRoomAt(ServerLevel level, BlockPos pos, FloorLayout f, Room r) {
        Where w = Where.at(level.getServer(), pos);
        return w != null && w.floor() != null && w.area() == f.area && w.floorNo() == f.floor && w.room() != null && w.room().id == r.id;
    }

    /** Nobody is inside any more: every barred room opens (it seals again when someone walks back in). */
    static void unsealAll(ServerLevel level, GateRecord g) {
        GateLayout l = GateLayout.of(g.plan);
        for (var e : g.floors.entrySet()) {
            FloorState fs = e.getValue();
            if (fs.locked.isEmpty()) continue;
            String[] af = e.getKey().split(":");
            FloorLayout f = l.floor(Integer.parseInt(af[0]), Integer.parseInt(af[1]));
            if (f == null) continue;
            for (int id : List.copyOf(fs.locked)) {
                Room r = f.room(id);
                if (r != null) lock(level, g, f, r, false);
                else fs.locked.remove(id);
            }
        }
    }

    /** First time anyone sets foot on a floor: things that should already be there when you arrive. */
    static void floorFirstEntered(ServerLevel level, GateRecord g, FloorLayout f) {
        FloorState fs = g.floor(f.area, f.floor);
        if (g.plan.layout() == Layout.STEALTH_HALLS && f.area == 0 && !f.bossFloor && !g.plan.friendly()) {
            for (Room r : f.rooms) {
                if ((r.type == Room.Type.HALL || r.type == Room.Type.NORMAL || r.type == Room.Type.CACHE) && r.normals + r.elites > 0) {
                    fs.triggered.add(r.id);
                    fs.prepared.add(r.id);
                    List<BlockPos> spots = spots(level, g, f, r);
                    for (int i = 0; i < r.normals + r.elites; i++) {
                        LivingEntity e = spawn(level, g, f, r.id, i < r.elites ? MobRole.Role.ELITE : MobRole.Role.NORMAL, spots.get(i % spots.size()));
                        var fr = e == null ? null : e.getAttribute(Attributes.FOLLOW_RANGE);
                        if (fr != null) fr.addOrReplacePermanentModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_sentry"), -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
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
        if (key.room() >= 256) {
            if (g.floor(key.area(), key.floor()).pendingCompanies.containsKey(key.room())) return;
            if (g.floor(key.area(), key.floor()).clearedRooms.add(key.room())) GateRegistry.get(level.getServer()).setDirty();
            return;
        }
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
        if (fs.clearedRooms.contains(r.id)) return;
        if (fs.pendingCompanies.containsKey(r.id)) {
            if (r.type == Room.Type.ARENA && fs.locked.contains(r.id)) lock(level, g, f, r, false);
            return;
        }
        if (r.type == Room.Type.ARENA) {
            int wave = fs.waves.getOrDefault(r.id, 1);
            if (wave == 1 && r.wave2Normals + r.wave2Elites > 0) {
                fs.waves.put(r.id, 2);
                GateRegistry.get(level.getServer()).setDirty();
                // Capacity-delayed members remain pending; a wave with no spawnable bodies must never leave an empty sealed arena.
                int spawned = spawnGroup(level, g, f, r, r.id, r.wave2Normals, r.wave2Elites, 5);
                if (spawned > 0 || fs.pendingCompanies.containsKey(r.id)) {
                    if (spawned > 0) broadcast(level, g, f, Component.literal("Second wave."));
                    else lock(level, g, f, r, false);
                    return;
                }
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
        dev.awaken.gates.worlds.WorldLive.progress(level, g, f);
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
            dev.awaken.gates.worlds.WorldLive.progress(level, g, f);
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
                // no diamonds (SPEC §28.0 decision 8: metal comes from the deep); a coil of Runner's rope instead
                var rope = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id("runner_rope"));
                if (rope != Items.AIR) offers.add(new MerchantOffer(new ItemCost(shard, 2), new ItemStack(rope, 1), 6, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 2), new ItemStack(Items.ENDER_PEARL, 2), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(shard, 3), new ItemStack(Items.EXPERIENCE_BOTTLE, 6), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.GOLD_INGOT, 4), new ItemStack(shard, 1), 16, 1, 0.05F));
            }
            default -> {
                offers.add(new MerchantOffer(new ItemCost(shard, 12), new ItemStack(CoreApi.LORD_HEART.get(), 1), 1, 20, 0.0F));
                var pitons = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id("piton"));
                if (pitons != Items.AIR) offers.add(new MerchantOffer(new ItemCost(shard, 2), new ItemStack(pitons, 4), 4, 3, 0.05F));
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
