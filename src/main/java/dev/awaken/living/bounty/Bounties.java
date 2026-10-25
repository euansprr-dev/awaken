package dev.awaken.living.bounty;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.living.LivingContent;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Association's bounties (SPEC §21.0.9): the first slice of Carillon's contracts board. Three notices for the
 * player's level band and rank at a time, at Quartermaster Hale's post (and the Association board once Carillon builds
 * it); one taken, a new notice comes next play-day; no expiry. Turned in at Hale; each pays once (claim ids).
 */
public final class Bounties {
    private Bounties() {}

    public static final String ACTION = "living_bounty";
    private static volatile Map<String, BountyDef> defs = Map.of();

    public static Map<String, BountyDef> all() {
        return defs;
    }

    public static void replace(Map<String, BountyDef> next) {
        defs = Collections.unmodifiableMap(new TreeMap<>(next));
    }

    public static BountyState state(ServerPlayer p) {
        return p.getData(LivingContent.BOUNTIES.get());
    }

    private static void save(ServerPlayer p, BountyState s) {
        p.setData(LivingContent.BOUNTIES.get(), s);
        QuestEngine.journal(p);
    }

    /** A taken bounty's Journal line (the title says what; this says how far). */
    public static String task(BountyDef d) {
        return d.objective().type().equals("collect") ? "Gather " + d.objective().count() + ", then see Hale" : "Hunt them, then see Hale";
    }

    /** The player's level band (0: 1-8 ... 4: 80+), as the region bands. */
    static int band(ServerPlayer p) {
        int lv = Progression.profile(p).level();
        return lv < 8 ? 0 : lv < 20 ? 1 : lv < 45 ? 2 : lv < 80 ? 3 : 4;
    }

    /** Fills the board to three notices: at once when empty, else one a play-day (a taken notice is replaced tomorrow). */
    static void refill(ServerPlayer p, BountyState s) {
        long day = PlayDays.current(p.level().getServer());
        int want = BountyState.NOTICES - s.offered.size();
        if (want <= 0) return;
        if (s.refilled == day && !s.offered.isEmpty()) return;
        int band = band(p);
        List<String> pool = new ArrayList<>();
        for (var e : defs.entrySet()) {
            BountyDef d = e.getValue();
            if (Math.abs(d.band() - band) > 0 || !Requirements.met(p, d.requires())) continue;
            if (s.offered.contains(e.getKey()) || s.taken.stream().anyMatch(t -> t.id().equals(e.getKey()))) continue;
            pool.add(e.getKey());
        }
        Collections.shuffle(pool, new java.util.Random(p.getUUID().getLeastSignificantBits() ^ day * 31 + s.done));
        int add = s.offered.isEmpty() ? want : 1;
        for (int i = 0; i < Math.min(add, pool.size()); i++) s.offered.add(pool.get(i));
        s.refilled = day;
    }

    /** Hale's post: turn in what's done, else read the next notice. False when there are no bounties at all. */
    public static boolean board(ServerPlayer p) {
        if (defs.isEmpty()) return false;
        BountyState s = state(p);
        // turn in first
        for (int i = 0; i < s.taken.size(); i++) {
            BountyState.Taken t = s.taken.get(i);
            BountyDef d = defs.get(t.id());
            if (d == null) {
                s.taken.remove(i--);
                continue;
            }
            if (finished(p, d, t)) {
                turnIn(p, s, i, d);
                save(p, s);
                return true;
            }
        }
        refill(p, s);
        save(p, s);
        if (s.offered.isEmpty()) {
            QuestEngine.sayLine(p, s.taken.isEmpty() ? "Hale: Nothing on the board for you today. Come back tomorrow." : "Hale: Finish what you took first.");
            return true;
        }
        String id = s.offered.getFirst();
        BountyDef d = defs.get(id);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Bounty · " + d.title()))
                .body(Component.literal((d.text().isEmpty() ? "" : d.text() + " ") + reward(d)))
                .choice(ACTION + ":" + id, "Take it", "Next notice").ttl(20 * 30).build());
        return true;
    }

    static String reward(BountyDef d) {
        return "Pays " + Math.round(d.levels() * 100) + "% of a level" + (d.essence() > 0 ? " and " + d.essence() + " essence." : ".");
    }

    /** The choice card: take the notice, or put it at the back of the board. */
    public static void answer(ServerPlayer p, String action, boolean accepted) {
        String id = action.substring(ACTION.length() + 1);
        BountyState s = state(p);
        if (!s.offered.contains(id)) return;
        if (accepted && s.taken.size() >= BountyState.NOTICES) {
            QuestEngine.sayLine(p, "Hale: Three at once is the limit. Finish one first.");
            return;
        }
        s.offered.remove(id);
        if (accepted) {
            s.taken.add(new BountyState.Taken(id, 0));
            BountyDef d = defs.get(id);
            if (d != null) Signal.toast(p, Component.literal("Bounty taken · " + d.title()));
        } else {
            s.offered.add(id);
        }
        save(p, s);
    }

    static boolean finished(ServerPlayer p, BountyDef d, BountyState.Taken t) {
        return switch (d.objective().type()) {
            case "collect" -> count(p, d.objective().target()) >= d.objective().count();
            default -> t.progress() >= d.objective().count();
        };
    }

    private static void turnIn(ServerPlayer p, BountyState s, int i, BountyDef d) {
        BountyState.Taken t = s.taken.remove(i);
        if (d.objective().type().equals("collect")) take(p, d.objective().target(), d.objective().count());
        String claim = "awaken:claim/bounty/" + t.id() + "/" + s.done;
        if (Progression.profile(p).counter(claim) == 0) {
            Progression.setCounter(p, claim, 1);
            if (d.levels() > 0) Progression.grantLevels(p, d.levels(), Progression.XpSource.WORLD);
            if (d.essence() > 0) {
                ItemStack ess = new ItemStack(CoreApi.ESSENCE_SHARD.get(), d.essence());
                p.getInventory().placeItemBackInInventory(ess, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        s.done++;
        Progression.count(p, "awaken:bounties_done");
        QuestEngine.sayLine(p, "Hale: " + (s.done == 1 ? "First one. Not bad. The board likes you now." : "Good. Here."));
        Signal.toast(p, Component.literal("Bounty done · " + d.title()));
    }

    // ---- progress -----------------------------------------------------------------------------------------------------

    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        BountyState s = state(p);
        if (s.taken.isEmpty()) return;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType()).toString();
        boolean changed = false;
        for (int i = 0; i < s.taken.size(); i++) {
            BountyState.Taken t = s.taken.get(i);
            BountyDef d = defs.get(t.id());
            if (d == null || !d.objective().type().equals("kill") || !matches(d.objective().target(), type)) continue;
            if (t.progress() >= d.objective().count()) continue;
            s.taken.set(i, new BountyState.Taken(t.id(), t.progress() + 1));
            if (t.progress() + 1 >= d.objective().count()) Signal.toast(p, Component.literal("Bounty ready · " + d.title() + " · turn in at the High Gate"));
            changed = true;
        }
        if (changed) save(p, s);
    }

    static boolean matches(String want, String got) {
        if (want.endsWith("*")) return got.startsWith(want.substring(0, want.length() - 1));
        return want.equals(got);
    }

    static int count(ServerPlayer p, String item) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(item)) n += st.getCount();
        }
        return n;
    }

    static void take(ServerPlayer p, String item, int count) {
        int left = count;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(item)) continue;
            int n = Math.min(left, st.getCount());
            st.shrink(n);
            left -= n;
        }
    }

    // ---- data ---------------------------------------------------------------------------------------------------------

    public static void addReload(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("living_bounties"), new Loader());
    }

    static final class Loader extends SimpleJsonResourceReloadListener<BountyDef> {
        Loader() {
            super(BountyDef.CODEC, FileToIdConverter.json("awaken/bounty"));
        }

        @Override
        protected void apply(Map<Identifier, BountyDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            TreeMap<String, BountyDef> ok = new TreeMap<>();
            loaded.forEach((k, v) -> {
                if (v.objective().count() > 0 && (v.objective().type().equals("kill") || v.objective().type().equals("collect"))) ok.put(k.toString(), v);
                else Awaken.LOG.warn("[living] bounty {} skipped: objective must be kill or collect with a positive count", k);
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} Association bounties", ok.size());
        }
    }
}
