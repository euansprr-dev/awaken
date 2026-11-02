package dev.awaken.gates.run;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.net.GatePayloads.SpoilsView;
import dev.awaken.signal.Signal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gate spoils (SPEC §13, "Gate spoils"; docs/gate-spoils/PLAN.md): nothing a gate gives you is lost except by the
 * stake. Gifts go to your pockets and then to what is {@link Held held for you}, never the ground; tagged items never
 * go into the Satchel; a gate monster's drops go straight into its killer's Satchel; and the inventory screen's view of
 * both lists, kept fresh while it is open.
 */
public final class Spoils {
    private Spoils() {}

    public static final TagKey<Item> NEVER_IN_SATCHEL = TagKey.create(Registries.ITEM, Awaken.id("never_in_satchel"));
    private static final int TOLD_EVERY = 20 * 5;

    /** Players with the inventory screen open, and a signature of the view they were last sent. */
    private static final Map<UUID, Long> WATCHING = new HashMap<>();
    /** Game time each player was last told something went to "held for you" (one toast per burst). */
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    /** Server start/stop: nothing carries over to another world. */
    public static void resetAll() {
        WATCHING.clear();
        TOLD.clear();
    }

    public static void forget(UUID player) {
        WATCHING.remove(player);
        TOLD.remove(player);
    }

    public static Held held(ServerPlayer p) {
        return p.getData(GatesRegistry.HELD.get());
    }

    // ---- where things go ----------------------------------------------------------------------------------------------

    /** Holds a stack for you (it ends empty). */
    public static void hold(ServerPlayer p, ItemStack stack) {
        if (stack.isEmpty()) return;
        DirectorLog.log(p, "gate", "held for you: " + stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        Satchel.add(held(p).items, stack);
    }

    /** A gift: into your pockets, and what doesn't fit is held for you (with one toast saying so). Never the ground. */
    public static void give(Player p, ItemStack stack) {
        if (stack.isEmpty()) return;
        p.getInventory().add(stack);
        if (stack.isEmpty()) return;
        if (p instanceof ServerPlayer sp) {
            String name = stack.getHoverName().getString();
            hold(sp, stack);
            told(sp, "Your pockets are full, so " + name + " is held for you. Open your inventory (E) to take it.");
        } else {
            p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** Into the run's Satchel, which never refuses; what never goes there ({@link #NEVER_IN_SATCHEL}) is a gift instead. */
    static void toSatchel(ServerPlayer p, RunState run, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (stack.is(NEVER_IN_SATCHEL)) give(p, stack);
        else Satchel.add(run.satchel, stack);
        Hud.sendSoon(p);
    }

    private static void told(ServerPlayer p, String line) {
        long now = p.level().getGameTime();
        Long last = TOLD.get(p.getUUID());
        if (last != null && now - last < TOLD_EVERY) return;
        TOLD.put(p.getUUID(), now);
        Signal.toast(p, Component.literal(line));
    }

    /** In a gate, what the game drops for you with no thrower (pockets full) is held for you; what you throw still drops. */
    static void onToss(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || e.getEntity().getOwner() != null) return;
        if (!Runs.run(p).active() || !GateService.inGate(p)) return;
        ItemStack s = e.getEntity().getItem().copy();
        e.setCanceled(true);
        String name = s.getHoverName().getString();
        hold(p, s);
        told(p, "Your pockets are full, so " + name + " is held for you. Open your inventory (E) to take it.");
    }

    /** LOWEST (after Fortune): a gate monster's drops go into its killer's Satchel; everyone near sees them fly in. */
    static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || e.getDrops().isEmpty() || !(dead.level() instanceof ServerLevel level) || !GateService.inGate(dead)) return;
        ServerPlayer killer = e.getSource().getEntity() instanceof ServerPlayer sp ? sp
                : dead.getKillCredit() instanceof ServerPlayer sp ? sp : null; // finished by fire, a fall or an Echo after your hit
        if (killer == null || killer.level() != level) return;
        RunState run = Runs.run(killer);
        if (!run.active()) return;
        List<ItemStack> shown = new ArrayList<>();
        for (ItemEntity ie : e.getDrops()) {
            ItemStack s = ie.getItem().copy();
            if (s.isEmpty()) continue;
            if (shown.size() < GatePayloads.SpoilsFlight.MAX) shown.add(s.copy());
            toSatchel(killer, run, s);
        }
        e.getDrops().clear();
        if (shown.isEmpty()) return;
        var flight = new GatePayloads.SpoilsFlight(dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(), killer.getId(), shown);
        for (ServerPlayer viewer : level.players()) if (viewer.distanceToSqr(dead) < 64 * 64) AwakenNet.send(viewer, flight);
    }

    // ---- taking what is held ----------------------------------------------------------------------------------------

    /** A held stack (or every one) into your pockets, as much as fits. The index must still hold that item. */
    public static int take(ServerPlayer p, int index, Item item, boolean all) {
        if (!p.isAlive() || p.isSpectator()) return 0;
        List<ItemStack> list = held(p).items;
        int moved = 0;
        if (all) {
            for (ItemStack s : list) moved += intoPockets(p, s);
        } else if (index >= 0 && index < list.size() && list.get(index).is(item)) {
            moved = intoPockets(p, list.get(index));
        }
        list.removeIf(ItemStack::isEmpty);
        if (moved > 0) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.4F);
        } else if (!list.isEmpty()) {
            Signal.toast(p, Component.literal("Your pockets are full."));
        }
        sendView(p);
        return moved;
    }

    private static int intoPockets(ServerPlayer p, ItemStack s) {
        int before = s.getCount();
        p.getInventory().add(s);
        return before - s.getCount();
    }

    // ---- the inventory screen's view ----------------------------------------------------------------------------------

    /** The inventory screen opened (true) or closed. While it is open, {@link #tick} keeps its view fresh. */
    public static void watch(ServerPlayer p, boolean on) {
        if (!on) {
            WATCHING.remove(p.getUUID());
            return;
        }
        WATCHING.put(p.getUUID(), signature(p));
        sendView(p);
    }

    /** Every 5 ticks: a fresh view to each watcher whose Satchel or held list changed. */
    static void tick(MinecraftServer server) {
        if (WATCHING.isEmpty() || server.getTickCount() % 5 != 0) return;
        for (Iterator<Map.Entry<UUID, Long>> it = WATCHING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            long sig = signature(p);
            if (sig == en.getValue()) continue;
            en.setValue(sig);
            sendView(p);
        }
    }

    private static long signature(ServerPlayer p) {
        RunState run = Runs.run(p);
        long h = run.gateId * 31L + Math.round(run.satchelXp);
        for (ItemStack s : run.satchel) h = h * 31 + BuiltInRegistries.ITEM.getId(s.getItem()) * 64L + s.getCount();
        for (ItemStack s : held(p).items) h = h * 37 + BuiltInRegistries.ITEM.getId(s.getItem()) * 64L + s.getCount();
        GateRecord g = run.active() ? Runs.gateOf(p) : null;
        return h * 7 + (g == null ? 0 : deathLine(p, g));
    }

    public static void sendView(ServerPlayer p) {
        if (!AwakenNet.canReceive(p, SpoilsView.TYPE)) return;
        AwakenNet.send(p, view(p));
    }

    public static SpoilsView view(ServerPlayer p) {
        RunState run = Runs.run(p);
        GateRecord g = run.active() ? Runs.gateOf(p) : null;
        boolean inRun = g != null && !g.plan.ritual(); // the ritual's hall carries no Satchel
        List<ItemStack> held = held(p).items;
        return new SpoilsView(inRun, inRun ? g.plan.rank().label + "-rank · " + g.plan.theme().displayName : "",
                inRun ? copies(run.satchel) : List.of(), inRun ? Satchel.count(run.satchel) : 0,
                inRun ? (int) Math.round(run.satchelXp) : 0, inRun ? deathLine(p, g) : SpoilsView.DEATH_NONE,
                copies(held), Satchel.count(held));
    }

    /** What dying here costs, in the order the death rules apply (Runs.onDeath). */
    static int deathLine(ServerPlayer p, GateRecord g) {
        if (g.scripted) return SpoilsView.DEATH_FIRST;
        if (dev.awaken.above.Above.cloneSaveAvailable(p)) return SpoilsView.DEATH_CLONE;
        return AwakenConfig.DIFFICULTY.get() == AwakenConfig.Difficulty.STORY ? SpoilsView.DEATH_HALF : SpoilsView.DEATH_ALL;
    }

    private static List<ItemStack> copies(List<ItemStack> list) {
        List<ItemStack> out = new ArrayList<>(Math.min(list.size(), SpoilsView.MAX));
        for (ItemStack s : list) {
            if (out.size() >= SpoilsView.MAX) break;
            if (!s.isEmpty()) out.add(s.copy());
        }
        return out;
    }

    /** Tests and tours: {@code stacks} different full stacks into the Satchel (in a run; else held) or held for you. */
    public static void devFill(ServerPlayer p, int stacks, boolean held) {
        RunState run = Runs.run(p);
        int n = 0, i = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            if (n >= stacks) break;
            if (it == net.minecraft.world.item.Items.AIR || BuiltInRegistries.ITEM.getKey(it).getNamespace().equals(Awaken.MODID)) continue;
            if (i++ % 5 != 0) continue;
            ItemStack s = new ItemStack(it, it.getDefaultMaxStackSize());
            if (s.is(NEVER_IN_SATCHEL)) continue;
            if (held || !run.active()) hold(p, s);
            else Satchel.add(run.satchel, s);
            n++;
        }
        Hud.sendSoon(p);
    }

    /** On login, before claims pay: one line if something is still held for you. */
    static void remind(ServerPlayer p) {
        int n = Satchel.count(held(p).items);
        if (n > 0) Signal.toast(p, Component.literal(n + (n == 1 ? " stack is" : " stacks are") + " held for you. Open your inventory (E) to take "
                + (n == 1 ? "it." : "them.")));
    }
}
