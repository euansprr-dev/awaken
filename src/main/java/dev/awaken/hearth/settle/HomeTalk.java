package dev.awaken.hearth.settle;

import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Offers;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.town.Town;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Talking to the people who live with you (SPEC §28.8). A home's settlement answers first (the people engine asks every
 * talker; villages answer for theirs): a Wayfarer chain that waits on them goes first, then their own trade (Oren's
 * commissions and tempering, Ilse's tonics, the miner's shaft and haul, Hettie's fields, Mira's rumours, Teodor's reading,
 * Sun's mending, Wren's lines, Pell's letter); what they kept for you comes with it. The household is "kin" to them (the
 * villages' warmest greetings, with the home's name for the village's), anyone else "known".
 */
public final class HomeTalk {
    private HomeTalk() {}

    public static void init() {
        Settlements.onTalk(HomeTalk::talk);
    }

    /** Wraps whatever voice the villages installed, so a home's people speak of it as theirs (called once the server starts). */
    public static void installVoice() {
        Settlements.Voice prev = Settlements.voice();
        if (prev instanceof HomeVoice) return;
        Settlements.installVoice(new HomeVoice(prev));
    }

    static @Nullable HearthRecord hearthOf(ServerLevel level, Settlement s) {
        if (!s.key().startsWith("hearth:")) return null;
        try {
            return HearthData.get(level.getServer()).of(UUID.fromString(s.key().substring(7))).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean talk(ServerPlayer p, Settlement s, int rec) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        HearthRecord h = hearthOf(level, s);
        if (h == null) return false;
        Folk.Settler st = HomePeople.settlerOf(h, rec);
        if (st == null) return true;
        Folk.Person who = st.who;
        Entity body = s.body(rec);
        BlockPos at = body == null ? p.blockPosition() : body.blockPosition();
        if (!h.belongs(p.getUUID())) {
            s.say(p, rec, "I'm needed here. This is " + h.ownerName + "'s hearth.", 80);
            return true;
        }
        // a chain waiting on them by name goes first (The Last Delivery, Moth Lights, Wren's Rope...)
        String chainName = switch (who.role()) {
            case "oren", "ilse", "hettie", "wren", "pell" -> who.role();
            default -> "";
        };
        // ...unless you came to Oren with the forge's business in hand (a piece to temper, the makings of one)
        boolean business = ("oren".equals(who.role()) || "smith".equals(who.role())) && forgeBusiness(p);
        if (!chainName.isEmpty() && !business && dev.awaken.living.api.Quests.talk(p, chainName)) return true;
        switch (who.role()) {
            case "oren", "smith" -> oren(p, level, h, s, rec, who, at);
            case "mira", "teodor", "sun", "vael" -> Town.serve(p, level, who.id(), at, h.owner);
            case "ilse" -> ilse(p, h, s, rec);
            case "hettie", "farmer" -> hettie(p, h, s, rec, who);
            case "miner" -> miner(p, level, h, s, rec);
            case "wren" -> wren(p, h, s, rec);
            case "pell" -> pell(p, level, h, s, rec);
            default -> s.say(p, rec, hand(p), 80);
        }
        return true;
    }

    // ---- Oren: tempering, commissions -------------------------------------------------------------------------------

    /** In the main hand: something to temper, or an ingredient of the piece Oren would make from what you carry. */
    static boolean forgeBusiness(ServerPlayer p) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) return false;
        if (Upgrades.isTemperable(hand)) return true;
        RecipeHolder<CraftingRecipe> r = Work.commissionFor(p);
        if (r == null) return false;
        for (var ing : r.value().placementInfo().ingredients()) if (ing.test(hand)) return true;
        return false;
    }

    static void oren(ServerPlayer p, ServerLevel level, HearthRecord h, Settlement s, int rec, Folk.Person who, BlockPos at) {
        int got = Work.handOver(p, h, "oren");
        if (got > 0) {
            s.say(p, rec, "Here. Tempered once, on the house. Don't make me regret it.", 90);
            return;
        }
        if (Upgrades.isTemperable(p.getMainHandItem())) {
            Upgrades.offerTemper(p, level, at, InteractionHand.MAIN_HAND, "oren");
            return;
        }
        Folk.Commission waiting = Work.commissionOf(h, p.getUUID());
        if (waiting != null) {
            s.say(p, rec, "It's on the anvil. Morning. Go and sleep, you're in my light.", 90);
            return;
        }
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(who.given() + " · the forge"));
        if (!h.hasRoom(RoomType.ARMORY)) {
            s.say(p, rec, "Build me a forge and I'll take commissions. Anvil, a Forge, a stand for the armour. One room.", 110);
            return;
        }
        RecipeHolder<CraftingRecipe> r = Work.commissionFor(p);
        if (r == null) {
            s.say(p, rec, "Bring me the metal and the hide for a piece. I'll have it by morning, a temper up.", 110);
            return;
        }
        ItemStack piece = Work.result(r.value());
        w.row(Component.literal("The piece"), piece.getHoverName());
        w.row(Component.literal("Ready"), Component.literal("by morning, tempered I"));
        w.body(Component.literal("“You've the makings of a " + piece.getHoverName().getString() + " there. Leave them with me.”"));
        w.choice(Offers.offer(p, "commission", 20 * 30, pl -> {
            HearthRecord again = HearthService.data(pl.level().getServer()).of(h.owner).orElse(null);
            if (again == null || Work.commissionOf(again, pl.getUUID()) != null) return;
            if (!Work.take(pl, r.value())) return;
            Work.commission(pl, again, r.value());
            Signal.toast(pl, Component.literal("Oren takes the makings · " + piece.getHoverName().getString() + " by morning"));
            pl.level().playSound(null, at, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.2f);
        }), "Commission it (he takes the makings)", "Not now");
        Signal.send(p, w.ttl(20 * 15).build());
    }

    // ---- Ilse: tonics --------------------------------------------------------------------------------------------

    static void ilse(ServerPlayer p, HearthRecord h, Settlement s, int rec) {
        if (Work.handOver(p, h, "ilse") > 0) {
            s.say(p, rec, "Yours. Drink them warm, and not all at once.", 90);
            return;
        }
        if (Work.brew(p, h)) {
            s.say(p, rec, "Leave them with me. By morning they'll be tonics, and better than what you'd make.", 110);
            return;
        }
        for (Folk.Brew b : h.folk.brews) {
            if (b.player().equals(p.getUUID())) {
                s.say(p, rec, "They're steeping. Morning.", 70);
                return;
            }
        }
        s.say(p, rec, h.hasRoom(RoomType.GREENHOUSE) ? "Bring me herbs, I'll make you something worth carrying. The glass keeps them ripening."
                : "Moths know where the ground is generous. So do I.", 110);
    }

    // ---- Hettie: fields and the pen ------------------------------------------------------------------------------

    static void hettie(ServerPlayer p, HearthRecord h, Settlement s, int rec, Folk.Person who) {
        int got = Work.handOver(p, h, "field");
        if (got > 0) {
            s.say(p, rec, "The chest by the field was full, so here. " + got + " of it. Find me a bigger chest.", 100);
            return;
        }
        String line = h.yard.fields().isEmpty() ? "No field? I'll keep the pen, then. Wipe your boots."
                : h.cropsHarvested > 0 ? "Brought in " + h.cropsHarvested + " since I came. It's in the chest by the field."
                : "Nothing ripe yet. The ewes judge me for it.";
        s.say(p, rec, line, 100);
    }

    // ---- the miner ----------------------------------------------------------------------------------------------

    static void miner(ServerPlayer p, ServerLevel level, HearthRecord h, Settlement s, int rec) {
        int got = Work.handOver(p, h, "mine");
        List<MineHead.Shaft> shafts = MineHead.shafts(level, h);
        if (shafts.isEmpty()) {
            s.say(p, rec, "No shaft to work. A hatch, a ladder, sixteen down. I'll do the rest.", 100);
            return;
        }
        MineHead.Shaft sh = shafts.getFirst();
        String lode = sh.lode().map(v -> " It cut a " + v.ore().word() + " lode down there. Good digging.").orElse(" Go deeper and we might cut a lode.");
        String line = (got > 0 ? "No chest by the shaft, so here's the haul. " : "") + "Shaft's " + sh.depth() + " deep, into " + MineHead.band(sh.foot()) + "." + lode;
        s.say(p, rec, line, 120);
    }

    // ---- Wren: the lines, and a course ------------------------------------------------------------------------------

    static void wren(ServerPlayer p, HearthRecord h, Settlement s, int rec) {
        if (h.folk.wrenGave.add(p.getUUID())) {
            give(p, new ItemStack(HearthContent.START_GATE_ITEM.get()));
            give(p, new ItemStack(HearthContent.CHECKPOINT_RING_ITEM.get(), 4));
            give(p, new ItemStack(HearthContent.FINISH_GATE_ITEM.get()));
            HearthService.dirty(p.level().getServer());
            s.say(p, rec, "Here. Lay a line over the roofs: start, rings, finish. I'll race your ghost. I'll lose. Probably.", 130);
            return;
        }
        int n = h.folk.ropes.size();
        s.say(p, rec, n == 0 ? "No two roofs close enough for a line yet. Build closer. I like closer."
                : "Strung " + n + " line" + (n == 1 ? "" : "s") + " so far. Go across. Hand over hand, don't look down. Look down a bit.", 110);
    }

    // ---- Pell: a letter and a rumour each play-day -------------------------------------------------------------------

    static void pell(ServerPlayer p, ServerLevel level, HearthRecord h, Settlement s, int rec) {
        long today = Settlers.today(level.getServer());
        Long last = h.folk.letters.get(p.getUUID());
        if (last != null && last == today) {
            s.say(p, rec, "That's your post for today. Roads aren't roofs: they take their time.", 90);
            return;
        }
        h.folk.letters.put(p.getUUID(), today);
        HearthService.dirty(level.getServer());
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(Letters.title(level, p)));
        w.body(Component.literal(Letters.body(level, p, h)));
        Signal.send(p, w.ttl(20 * 16).build());
        Town.rumour(p, level);
        s.say(p, rec, "Post. And something I heard on the road.", 80);
    }

    static String hand(ServerPlayer p) {
        String[] lines = {"Home's quieter than the road. I don't mind it.", "If you need a hand, I've two.", "The fire's good tonight. Sit, if you've time."};
        return lines[Math.floorMod(p.getRandom().nextInt(), lines.length)];
    }

    private static void give(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st)) p.drop(st, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- the household's voice ----------------------------------------------------------------------------------

    /** How a home's people know a player (the household is kin) and the home's name for the village lines' places. */
    static final class HomeVoice implements Settlements.Voice {
        private final Settlements.@Nullable Voice prev;

        HomeVoice(Settlements.@Nullable Voice prev) {
            this.prev = prev;
        }

        @Override
        public String standing(Settlement s, ServerPlayer p) {
            if (!s.key().startsWith("hearth:")) return prev == null ? "stranger" : prev.standing(s, p);
            HearthRecord h = p.level() instanceof ServerLevel l ? hearthOf(l, s) : null;
            return h != null && h.belongs(p.getUUID()) ? "kin" : "known";
        }

        @Override
        public String fill(Settlement s, ServerPlayer p, String line) {
            if (!s.key().startsWith("hearth:")) return prev == null ? line : prev.fill(s, p, line);
            HearthRecord h = p.level() instanceof ServerLevel l ? hearthOf(l, s) : null;
            String name = h == null ? s.name() : h.displayName();
            String owner = h == null ? "you" : h.ownerName;
            return line.replace("{village}", name).replace("{region}", "these parts").replace("{elder}", owner).replace("{gate}", "out there")
                    .replace("{landmark}", "the old stones").replace("{name}", p.getName().getString());
        }
    }
}
