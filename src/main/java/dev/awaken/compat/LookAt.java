package dev.awaken.compat;

import dev.awaken.above.MachineType;
import dev.awaken.above.machine.LaunchRing;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.GateState;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Twist;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.town.Townsfolk;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;

/**
 * What a look-at tooltip (Jade) may say about Awaken things, worked out on the server, where the facts live. Each
 * method follows the same spoiler rule as the System's own cards: a gate says what its gate card would say to this
 * player (rank, state, the break clock, the omen; layout from PER 20, modifiers from 40, floors from 60, everything
 * once scouted; the story's ritual hall only its bell). Plain Awaken code with no companion-mod types, so GameTests
 * check it without Jade.
 */
public final class LookAt {
    private LookAt() {}

    /** A gate as its card would describe it to this player. Unknown parts are empty strings, hidden counts -1. */
    public record Gate(int rank, String state, int breaksIn, boolean scripted, boolean ritual, String omen, String theme,
                       String layout, String modifiers, int floors, String lord) {
        public static final StreamCodec<ByteBuf, Gate> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Gate decode(ByteBuf b) {
                return new Gate(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.VAR_INT.decode(b) - 1,
                        ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                        ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                        ByteBufCodecs.VAR_INT.decode(b) - 1, ByteBufCodecs.STRING_UTF8.decode(b));
            }

            @Override
            public void encode(ByteBuf b, Gate g) {
                ByteBufCodecs.VAR_INT.encode(b, g.rank);
                ByteBufCodecs.STRING_UTF8.encode(b, g.state);
                ByteBufCodecs.VAR_INT.encode(b, g.breaksIn + 1);
                ByteBufCodecs.BOOL.encode(b, g.scripted);
                ByteBufCodecs.BOOL.encode(b, g.ritual);
                ByteBufCodecs.STRING_UTF8.encode(b, g.omen);
                ByteBufCodecs.STRING_UTF8.encode(b, g.theme);
                ByteBufCodecs.STRING_UTF8.encode(b, g.layout);
                ByteBufCodecs.STRING_UTF8.encode(b, g.modifiers);
                ByteBufCodecs.VAR_INT.encode(b, g.floors + 1);
                ByteBufCodecs.STRING_UTF8.encode(b, g.lord);
            }
        };
    }

    /** PER needed to read a gate's layout, modifiers and floor count from outside (SPEC §7, the gate card). */
    public static final int PER_LAYOUT = 20, PER_MODIFIERS = 40, PER_FLOORS = 60;

    public static Optional<Gate> gate(ServerPlayer p, ServerLevel level, BlockPos pos) {
        var info = Gates.gateAt(level, pos);
        if (info.isEmpty()) return Optional.empty();
        var g = info.get();
        MinecraftServer s = level.getServer();
        if (Gates.isRitual(s, g.id())) {
            // the story's gate: no clock, no layout. Only a bell, somewhere far down
            return Optional.of(new Gate(g.rank().ordinal(), stateWord(g.state()), -1, true, true, "", "", "", "", -1, ""));
        }
        var preview = Gates.preview(s, g.id());
        int per = Progression.stat(p, Stat.PER);
        boolean scouted = Gates.isScouted(p, g.id());
        int breaksIn = g.state().standing() && !g.scripted() ? (int) Math.min(Integer.MAX_VALUE - 1, Math.max(0, g.breaksInDays())) : -1;
        return Optional.of(gateFor(g.rank(), g.state(), breaksIn, g.scripted(), preview.orElse(null), per, scouted));
    }

    /** The card's rule on its own, for tests: what a player with this PER (and scouting) learns of a gate. */
    static Gate gateFor(Rank rank, GateState state, int breaksIn, boolean scripted, Gates.GatePreview preview, int per, boolean scouted) {
        if (preview == null) return new Gate(rank.ordinal(), stateWord(state), breaksIn, scripted, false, "", "", "", "", -1, "");
        String layout = per >= PER_LAYOUT || scouted ? preview.layout().displayName : "";
        String mods = "";
        if (per >= PER_MODIFIERS || scouted) {
            mods = preview.modifiers().isEmpty() ? "None"
                    : String.join(", ", preview.modifiers().stream().map((Modifier m) -> m.displayName).toList());
        }
        int floors = per >= PER_FLOORS || scouted ? preview.floors() : -1;
        String theme = scouted ? preview.theme().displayName : "";
        String lord = scouted ? preview.bossName() : "";
        if (scouted && preview.twist() != Twist.NONE) lord = lord + " · " + preview.twist().displayName;
        return new Gate(rank.ordinal(), stateWord(state), breaksIn, scripted, false, preview.omen(), theme, layout, mods, floors, lord);
    }

    /** Plain words for a gate's state, as the System says them. */
    static String stateWord(GateState s) {
        return switch (s) {
            case BUILDING -> "Forming";
            case READY -> "Open";
            case ACTIVE -> "Hunters inside";
            case OPEN -> "Open";
            case CLEARED -> "Spent";
            case BROKEN -> "Broken open";
            case RELEASED -> "Gone";
        };
    }

    /** A Precursor machine: who built it, its power, and the one thing it is doing (a clone growing, a ring short of frames). */
    public record Machine(String owner, int stored, int powered, int reactors, int status) {
        /** status: 0 nothing to say, 1 clone ready, 2 clone growing; for the Launch Ring, frames still missing + 10. */
        public static final StreamCodec<ByteBuf, Machine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Machine::owner,
                ByteBufCodecs.VAR_INT, Machine::stored,
                ByteBufCodecs.VAR_INT, Machine::powered,
                ByteBufCodecs.VAR_INT, Machine::reactors,
                ByteBufCodecs.VAR_INT, Machine::status,
                Machine::new);
    }

    public static Machine machine(ServerLevel level, MachineBlockEntity be) {
        MachineType t = be.type();
        int status = 0;
        if (t == MachineType.CLONE_VAT) status = be.cloneReady() ? 1 : 2;
        else if (t == MachineType.LAUNCH_RING) status = 10 + LaunchRing.missing(level, be.getBlockPos()).size();
        boolean reactor = be.isReactor();
        return new Machine(nameOf(level.getServer(), be.owner()), reactor ? be.essence() : 0,
                reactor ? be.essence() : be.poweredEssence(), reactor ? 0 : be.reactorsInRange(), status);
    }

    /** An Awaken creature's level and rank (its content level, from where and for whom it spawned) and its part in a fight. */
    public record Creature(int level, int rank, String role) {
        public static final StreamCodec<ByteBuf, Creature> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Creature::level,
                ByteBufCodecs.VAR_INT, Creature::rank,
                ByteBufCodecs.STRING_UTF8, Creature::role,
                Creature::new);
    }

    public static Optional<Creature> creature(Entity e) {
        if (!(e instanceof AwakenMob mob)) return Optional.empty();
        MobRole role = MobRole.of(mob);
        int lv = role.role() == MobRole.Role.NONE ? mob.contentLevel() : role.contentLevel();
        return Optional.of(new Creature(lv, rankFor(lv).ordinal(), roleWord(role.role())));
    }

    /** The rank whose content level a level belongs to: the highest rank at or below it. */
    static Rank rankFor(int contentLevel) {
        Rank best = Rank.E;
        for (Rank r : Rank.values()) if (r.contentLevel <= contentLevel) best = r;
        return best;
    }

    static String roleWord(MobRole.Role r) {
        return switch (r) {
            case ELITE -> "Elite";
            case WARDEN -> "Warden";
            case BOSS -> "Gate Lord";
            case BREAK -> "Escaped from a broken gate";
            case NIGHTMARE -> "Nightmare";
            case SPIRIT -> "Spirit";
            default -> "";
        };
    }

    /** A townsperson: who they are, what they do, whose town, and the rank they came at. */
    public record Townsperson(String name, String job, String owner, int arrivedAt) {
        public static final StreamCodec<ByteBuf, Townsperson> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Townsperson::name,
                ByteBufCodecs.STRING_UTF8, Townsperson::job,
                ByteBufCodecs.STRING_UTF8, Townsperson::owner,
                ByteBufCodecs.VAR_INT, Townsperson::arrivedAt,
                Townsperson::new);
    }

    public static Townsperson townsperson(ServerLevel level, TownNpc npc) {
        String id = npc.kind();
        var def = Townsfolk.get(id);
        String name = def.map(d -> d.name()).orElse(Townsfolk.skin(id));
        String job = def.map(d -> d.role()).orElse(jobFromId(id));
        int arrived = def.map(d -> d.arrivesAt().ordinal()).orElse(-1);
        return new Townsperson(name, job, nameOf(level.getServer(), npc.hearthOwner()), arrived);
    }

    /** "oren_blacksmith" → "Blacksmith": the id's second word (the client's fallback when it has no town data). */
    public static String jobFromId(String id) {
        int u = id.indexOf('_');
        if (u < 0 || u == id.length() - 1) return "";
        String w = id.substring(u + 1).replace('_', ' ');
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    /** A Hearth Anchor: whose, its tier and reach, and the Forge's tier. */
    public record Hearth(String owner, int tier, int radius, int forgeTier) {
        public static final StreamCodec<ByteBuf, Hearth> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Hearth::owner,
                ByteBufCodecs.VAR_INT, Hearth::tier,
                ByteBufCodecs.VAR_INT, Hearth::radius,
                ByteBufCodecs.VAR_INT, Hearth::forgeTier,
                Hearth::new);
    }

    public static Optional<Hearth> hearth(ServerLevel level, BlockPos anchor) {
        Optional<HearthRecord> r = HearthService.data(level.getServer()).at(anchor, level.dimension().identifier().toString());
        return r.map(h -> new Hearth(h.ownerName, h.tier, h.radius(), h.forgeTier));
    }

    static String nameOf(MinecraftServer s, UUID id) {
        if (id == null) return "";
        ServerPlayer online = s.getPlayerList().getPlayer(id);
        if (online != null) return online.getScoreboardName();
        return s.services().nameToIdCache().get(id).map(NameAndId::name).orElse("");
    }
}
