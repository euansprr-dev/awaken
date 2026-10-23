package dev.awaken.gates.run;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.Overworld;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.Room;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the gate's blocks do when touched or used. Teleports are queued to the end of the tick. */
public final class Interactions {
    private Interactions() {}

    public static final String ACTION = "gates";

    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final List<Runnable> QUEUE = new ArrayList<>();

    /** Server start/stop: cooldowns are game times of the old world, queued answers belong to it. */
    public static void resetAll() {
        COOLDOWN.clear();
        QUEUE.clear();
    }

    private static boolean cooled(ServerPlayer p, int ticks) {
        long now = GateService.now(p.level().getServer());
        Long last = COOLDOWN.get(p.getUUID());
        if (last != null && now - last < ticks) return false;
        COOLDOWN.put(p.getUUID(), now);
        return true;
    }

    /** Queues an action for the end of the tick; it is skipped if the player left in the meantime (no claims after the save). */
    private static void queue(ServerPlayer p, Runnable r) {
        QUEUE.add(() -> {
            if (!p.isRemoved() && !p.hasDisconnected()) r.run();
        });
    }

    static void flush() {
        if (QUEUE.isEmpty()) return;
        List<Runnable> q = new ArrayList<>(QUEUE);
        QUEUE.clear();
        for (Runnable r : q) r.run();
    }

    // ---- surface gate -----------------------------------------------------------------------------------------------

    /** Walking into a gate's core. */
    public static void touchCore(ServerLevel level, BlockPos pos, ServerPlayer p) {
        if (level.dimension() != Level.OVERWORLD || p.isSpectator() || !cooled(p, 30)) return;
        GateRecord g = structureAt(level.getServer(), pos);
        if (g == null) return;
        if (!g.state.enterable()) {
            Signal.toast(p, Component.literal(switch (g.state) {
                case BUILDING -> "The gate is still forming.";
                case CLEARED -> "This gate is spent.";
                case BROKEN -> "This gate has broken open.";
                default -> "The gate won't open.";
            }));
            return;
        }
        queue(p, () -> Runs.enter(p, g));
    }

    /** The gate whose surface structure contains this block (big gates reach far from their foot: S stands 17 high). */
    public static GateRecord structureAt(MinecraftServer server, BlockPos pos) {
        for (GateRecord g : GateRegistry.get(server).near(pos, Overworld.REACH, false)) {
            if (!g.structurePlaced) continue;
            if (Overworld.isPart(g, pos)) return g;
        }
        return null;
    }

    // ---- inside -----------------------------------------------------------------------------------------------------

    public static void touchPortal(ServerLevel level, BlockPos pos, ServerPlayer p, GateBlocks.Portal.Kind kind) {
        if (!GateService.isGateLevel(level) || !cooled(p, 30)) return;
        GateRecord g = Runs.gateOf(p);
        if (g == null) return;
        switch (kind) {
            case EXIT -> queue(p, () -> Runs.exitPortal(p));
            case RIFT, INNER -> queue(p, () -> Twists.enterAnnex(p, g));
            case RETURN -> queue(p, () -> Twists.returnFromInner(p, g));
        }
    }

    public static void useShrine(ServerPlayer p, BlockPos pos) {
        RunState run = Runs.run(p);
        GateRecord g = Runs.gateOf(p);
        Where w = Where.at(p.level().getServer(), pos);
        if (g == null || w == null || w.gate().id != g.id || w.room() == null) return;
        if (!run.clearedFloor(w.area(), w.floorNo())) {
            Signal.toast(p, Component.literal("Rest here once the floor is yours."));
            return;
        }
        if (g.plan.is(Twist.RED_GATE) && !g.bossDead) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The exit is sealed"))
                    .body(Component.literal("A Red Gate lets no one out until its Lord is dead. Break the Seal and go on.")).ttl(20 * 5).build());
            return;
        }
        boolean last = w.area() == 0 && w.floorNo() >= g.plan.floors() - 1;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Rest Shrine"))
                .row("Satchel", Satchel.count(run.satchel) + " slots · " + Satchel.items(run.satchel) + " items")
                .row("Satchel XP", Long.toString(Math.round(run.satchelXp)))
                .row("Floor", w.floorNo() + (w.area() == 0 ? " of " + g.plan.floors() : " (second gate)"))
                .body(Component.literal(last ? "Below the Seal waits the Lord." : "Carry it out now, or break the Seal and go deeper for more."))
                .choice(ACTION + ":extract", "Leave with your loot", last ? "Stay and face the Lord" : "Stay and go deeper")
                .priority(5).build());
    }

    public static void useSeal(ServerPlayer p, BlockPos pos) {
        if (!cooled(p, 20)) return;
        queue(p, () -> Runs.descend(p));
    }

    public static void openCache(ServerPlayer p, BlockPos pos, BlockState state) {
        if (state.getValue(GateBlocks.Cache.OPEN)) {
            Signal.toast(p, Component.literal("Empty."));
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        GateRecord g = Runs.gateOf(p);
        Where w = Where.at(level.getServer(), pos);
        if (w != null && w.room() != null && w.room().type == Room.Type.PUZZLE) {
            Room.Feature vault = w.room().feature(Room.FeatureKind.VAULT_CACHE);
            if (vault != null && !w.gate().floor(w.area(), w.floorNo()).puzzlesSolved.contains(w.room().id)) {
                Signal.toast(p, Component.literal("The vault is caged. The runes must agree first."));
                return;
            }
        }
        level.setBlock(pos, state.setValue(GateBlocks.Cache.OPEN, true), Block.UPDATE_ALL);
        var rank = g != null ? g.plan.rank() : dev.awaken.progression.Rank.E;
        var theme = w != null && w.floor() != null ? w.floor().theme : g != null ? g.plan.theme() : dev.awaken.gates.content.Theme.BELL_CRYPT;
        boolean fortune = g != null && g.plan.has(Modifier.FORTUNE);
        List<ItemStack> loot = Loot.cache(rank, theme, p.getRandom(), fortune, Progression.perk(p, Perks.LOOT_BONUS));
        int items = 0;
        for (ItemStack s : loot) {
            items += s.getCount();
            if (g != null) Satchel.add(Runs.run(p).satchel, s);
            if (!s.isEmpty()) level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, s));
        }
        level.playSound(null, pos, GatesRegistry.SND_CACHE.get(), SoundSource.BLOCKS, 1F, 1F);
        level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 16, 0.4, 0.3, 0.4, 0.05);
        Signal.toast(p, Component.literal("Cache: " + items + " items into your Satchel" + (fortune ? " (Fortune)" : "")));
        Hud.send(p);
    }

    public static void pressRune(ServerPlayer p, BlockPos pos) {
        if (!cooled(p, 6)) return;
        Where w = Where.at(p.level().getServer(), pos);
        if (w == null || w.room() == null) return;
        for (Room.Feature f : w.room().features(Room.FeatureKind.RUNE)) {
            BlockPos fp = GateService.roomFloor(w.gate(), w.floor(), w.room(), f.x(), f.z()).above(f.y());
            if (fp.equals(pos)) {
                Rooms.pressRune((ServerLevel) p.level(), p, w.gate(), w.floor(), w.room(), f);
                return;
            }
        }
    }

    public static void useEntrance(ServerPlayer p, BlockPos pos) {
        RunState run = Runs.run(p);
        GateRecord g = Runs.gateOf(p);
        if (g == null) return;
        if (run.area != 0 || run.floor != 1) {
            Signal.toast(p, Component.literal("The way back is far above you. Find a Rest Shrine."));
            return;
        }
        if (g.plan.is(Twist.RED_GATE) && !g.bossDead) {
            Signal.toast(p, Component.literal("The way you came in has sealed."));
            return;
        }
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Retreat?"))
                .body(Component.literal("You can walk back out, but the gate keeps what's in your Satchel."))
                .row("Satchel", Satchel.items(run.satchel) + " items, " + Math.round(run.satchelXp) + " XP")
                .choice(ACTION + ":retreat", "Leave (the gate keeps your Satchel)", "Stay").build());
    }

    public static void takeLantern(ServerPlayer p, BlockPos pos) {
        Where w = Where.at(p.level().getServer(), pos);
        if (w == null || w.room() == null || Runs.gateOf(p) == null) return;
        for (Room.Feature f : w.room().features(Room.FeatureKind.LANTERN)) {
            BlockPos fp = GateService.roomFloor(w.gate(), w.floor(), w.room(), f.x(), f.z()).above(f.y());
            if (fp.equals(pos)) {
                Twists.takeLantern(p, w.gate(), w.floor(), w.room(), f, pos);
                return;
            }
        }
    }

    /** Signal answers: gates:extract, gates:retreat. */
    public static void answer(ServerPlayer p, String action, boolean accepted) {
        if (!accepted) return;
        RunState run = Runs.run(p);
        GateRecord g = Runs.gateOf(p);
        if (g == null) return;
        switch (action) {
            case ACTION + ":extract" -> {
                Where w = Where.at(p.level().getServer(), p.blockPosition());
                if (w == null || w.room() == null || w.room().type != Room.Type.SHRINE) {
                    Signal.toast(p, Component.literal("Stand by the shrine to extract."));
                    return;
                }
                queue(p, () -> Runs.extract(p, false));
            }
            case ACTION + ":retreat" -> {
                if (run.area != 0 || run.floor != 1) return;
                queue(p, () -> {
                    run.satchel.clear();
                    run.satchelXp = 0;
                    Progression.setCounter(p, dev.awaken.progression.Counters.FLOOR_STREAK_NO_EARLY_EXTRACT, 0);
                    Runs.endRun(p, g, true);
                    Signal.toast(p, Component.literal("You walked out. The gate kept what you carried."));
                });
            }
            default -> {}
        }
    }

    static boolean standing(GateRecord g) {
        return g.state.standing() || g.state == GateState.BROKEN;
    }
}
