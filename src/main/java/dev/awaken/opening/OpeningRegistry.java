package dev.awaken.opening;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CitizenHooks;
import dev.awaken.core.api.HoloBand;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.story.Story;
import dev.awaken.story.StoryProgress.Prologue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Registry ceremony (SPEC §16.4; Euan, 2026-10-26: "they actually physically hand you the Holo band and the paper
 * to sign"). Bram walks you from the infirmary through the atrium to the Assessment Lens; it reads you (E); at the desk
 * you take the quill and sign the register in your own name, and he stamps it (the licence is yours); he hands you the
 * Holo band, which goes into your hands, not onto your wrist: the Holo stays dark until you open your inventory and put
 * it on the wrist slot. It boots there; he explains it; "Go on, open it" (the K tip shows only now); the K menu, then the
 * Atlas with the run you made this morning (a proposal: SPEC §16.4); the first power; the first Directive; "The city is
 * yours." Steps that wait for you (the mark, the desk, the band, the wrist, K) wait; the rest run on the clock. Without
 * a client to play them (a test player) every waiting step moves on by itself.
 */
final class OpeningRegistry {
    private OpeningRegistry() {}

    static final int WALK = 0, READ = 1, SIGN = 2, BAND = 3, WEAR = 4, BOOT = 5, BRIEF = 6, OPEN = 7, STATUS = 8, ATLAS = 9,
            POWER = 10, DIRECTIVE = 11, YOURS = 12, LETTERS = 13;
    /** How long each clocked step plays, and the most a screen or a scene waits. */
    static final int READ_TICKS = 110, BRIEF_TICKS = 20 * 28, SCREEN_MAX = 20 * 75, DIRECTIVE_TICKS = 220, YOURS_TICKS = 140,
            LETTERS_OUT = 150,
            SCENE_MAX = 20 * 20, BOOT_MAX = 20 * 12;
    /** Close enough to the Lens to be read; to the desk to sign; to Bram to take the band. */
    static final double MARK = 2.2, DESK = 2.0, HAND = 3.4;
    /** Bram walks on once you're this close to him; nudges you once you're this far behind. */
    static final double FOLLOW = 4.5, LAG = 9.0;
    /** A nudge again after this long (waiting for the desk, the band, the wrist, K). */
    static final int NUDGE = 20 * 30;

    /** One player's walk with Bram (transient: a relog plays the step again from where Bram stands). */
    private static final class Walk {
        long stepAt;
        int wp, deskWp = -1, nudges;
        long lagSince = -1, lastNudge, lastLine = -1000;
        boolean ahead, ready, stamped, archive;
    }

    private static final Map<UUID, Walk> WALKS = new HashMap<>();

    static void forget() {
        WALKS.clear();
    }

    private static Walk walk(ServerPlayer p) {
        return WALKS.computeIfAbsent(p.getUUID(), k -> {
            Walk w = new Walk();
            w.stepAt = OpeningDirector.now(p);
            return w;
        });
    }

    static {
        // the band going on is the wrist step's answer
        HoloBand.listen((p, worn) -> {
            if (worn && OpeningDirector.beat(p) == Prologue.REGISTRY && OpeningDirector.state(p).step == WEAR) step(p, BOOT);
        });
    }

    static void tick(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) {
            OpeningDirector.finish(p);
            return;
        }
        OpeningState st = OpeningDirector.state(p);
        Walk w = walk(p);
        long now = OpeningDirector.now(p);
        long age = now - w.stepAt;
        boolean client = AwakenNet.canReceive(p, OpeningPayloads.Scene.TYPE);
        boolean lead = lead(p);
        switch (st.step) {
            case WALK -> {
                if (age == 1) say(p, at, w, "This way. No need to rush, nobody's timing you today.");
                if (lead) escort(p, at, w, now);
                if (p.tickCount % 5 == 0 && p.position().distanceTo(at.lensMark()) < MARK) {
                    say(p, at, w, "Stand on that mark and look into the light. It won't hurt, it just reads you.");
                    step(p, READ);
                }
                // no client to walk it (a test player): Bram fetches you
                if (!client && age > 20 * 30) step(p, READ);
            }
            case READ -> {
                if (lead && age % 10 == 1) stage(p, at.bramLens(), yawTo(at.bramLens(), p.position()), "hands_behind", "");
                if (age == READ_TICKS - 20) say(p, at, w, "Rank E. That's where everyone starts, so don't take it personally.");
                if (age >= READ_TICKS) step(p, SIGN);
            }
            case SIGN -> {
                if (age == 30) say(p, at, w, "Now the register. Sign your name there and you're officially a hunter.");
                if (lead) toDesk(p, at, w, w.stamped ? "set_down" : "hands_behind", "");
                if (w.ready || !client && age > 60 || age > SCREEN_MAX) {
                    giveLicence(p);
                    step(p, BAND);
                } else nudge(p, at, w, now, "It's on the desk. The quill is in the pot next to it.");
            }
            case BAND -> {
                if (age == 10) say(p, at, w, "And this is yours. It's your Holo band.");
                if (lead) toDesk(p, at, w, "serve", "holo_band");
                if (w.ready || !client && age > 40 || age > SCREEN_MAX) {
                    if (!HoloBand.has(p)) HoloBand.give(p);
                    step(p, HoloBand.worn(p) ? BOOT : WEAR);
                } else nudge(p, at, w, now, "Go on, take it.");
            }
            case WEAR -> {
                if (age == 1) say(p, at, w, "Open your inventory and put it on your wrist. It'll feel cold for a second, then it settles.");
                if (lead) toDesk(p, at, w, "hands_behind", "");
                if (!HoloBand.has(p)) HoloBand.give(p); // dropped it: the Association has spares
                if (HoloBand.worn(p)) step(p, BOOT);
                else if (!client && age > 20) HoloBand.wear(p);
                else nudge(p, at, w, now, "There's a wrist slot next to you in your inventory. The band only works when you wear it there.");
            }
            case BOOT -> {
                if (lead) toDesk(p, at, w, "hands_behind", "");
                if (w.ready || !client || age > BOOT_MAX) step(p, BRIEF);
            }
            case BRIEF -> {
                if (lead) toDesk(p, at, w, "", "");
                if (age == 10) say(p, at, w, "The band keeps track of everything about you: what you can do, what you've done, and what you've earned.");
                if (age == 140) say(p, at, w, "You get better at whatever you practise. The more you run, the faster you get. The more you fight, the harder you hit.");
                if (age == 280) say(p, at, w, "Gates are how hunters get stronger. Every floor you clear makes you tougher, and once you've cleared enough, the Lens raises your rank.");
                // where new hunters go first: Ilse's camp, whose Ember founds a Hearth (SPEC §28, W1)
                if (age == 430) say(p, at, w, "The bells keep the city safe, but outside the walls it's up to hunters. Ilse runs a camp up the north road, and she looks after new hunters.");
                if (age >= BRIEF_TICKS) step(p, OPEN);
            }
            case OPEN -> {
                if (age == 1) say(p, at, w, "Go ahead and open it.");
                if (lead) toDesk(p, at, w, "hands_behind", "");
                if (!HoloBand.worn(p)) step(p, WEAR); // took it off again
                else if (w.ready || !client || age > SCREEN_MAX) step(p, w.ready ? STATUS : ATLAS);
                else nudge(p, at, w, now, "Try opening your band.");
            }
            case STATUS -> {
                if (age == 10) say(p, at, w, "That's your status, your Truths, the Codex and the gate map. Press the same key to close it.");
                if (lead) toDesk(p, at, w, "hands_behind", "");
                if (w.ready || !client || age > SCREEN_MAX) step(p, ATLAS);
            }
            case ATLAS -> {
                if (age == 14) say(p, at, w, "Your band keeps a map too. It fills in as you explore, so for now it only shows the parts of the city you've run through.");
                if (lead) toDesk(p, at, w, "", "");
                if (w.ready || !client || age > SCREEN_MAX) step(p, POWER);
            }
            case POWER -> {
                if (age == 1) {
                    say(p, at, w, "Now it'll offer you your first power. Everyone gets one, so pick the one you think you'll actually use.");
                    OpeningModule.offerFirstPower(p);
                }
                if (lead) toDesk(p, at, w, "hands_behind", "");
                if (!client || age >= SCREEN_MAX || age > 40 && OpeningModule.firstPowerChosen(p)) {
                    if (client) say(p, at, w, "Try it out once you're outside. Not on me, please.");
                    step(p, DIRECTIVE);
                }
            }
            case DIRECTIVE -> {
                if (age == 40) {
                    OpeningModule.offerDirective(p);
                    say(p, at, w, "The Signal will give you Directives. They're small jobs the Association pays you for, and you'll never have more than three at once. Think of them as suggestions, not orders.");
                }
                if (lead) toDesk(p, at, w, "", "");
                if (age >= DIRECTIVE_TICKS) step(p, LETTERS);
            }
            case LETTERS -> {
                // what a hunter is for, and where to go first: Ilse's camp, with letters he hands you like the band
                if (age == 10) say(p, at, w, "Oh, and one more thing. As a hunter, your job is to protect the city from whatever is out in the wilds, beyond the walls.");
                if (age == LETTERS_OUT) say(p, at, w, "Ilse runs a camp for new hunters up the north road. Take these letters to her, and she'll explain the rest.");
                if (lead) toDesk(p, at, w, age >= LETTERS_OUT ? "serve" : "hands_behind", age >= LETTERS_OUT ? "letters" : "");
                if (w.ready || !client && age > LETTERS_OUT + 20 || age > LETTERS_OUT + SCREEN_MAX) {
                    giveLetters(p);
                    step(p, YOURS);
                } else if (age > LETTERS_OUT) nudge(p, at, w, now, "Go on, take them.");
            }
            case YOURS -> {
                if (age == 1) say(p, at, w, "That's everything. Your room at the Roost is still yours whenever you need to rest. Good luck out there.");
                if (lead) toDesk(p, at, w, "wave", "");
                if (age >= YOURS_TICKS) {
                    WALKS.remove(p.getUUID());
                    OpeningDirector.finish(p);
                }
            }
            default -> step(p, YOURS);
        }
    }

    /** The client finished what only it can see end: the signature, the hand-over, the boot, the K menu, the Atlas. */
    static void onReady(ServerPlayer p, int n) {
        OpeningState st = OpeningDirector.state(p);
        if (OpeningDirector.beat(p) != Prologue.REGISTRY || st.step != n) return;
        Walk w = walk(p);
        switch (n) {
            case SIGN -> {
                if (!w.stamped) {
                    // the ink is down: Bram countersigns with his stamp, and the client shows the seal land
                    w.stamped = true;
                    return;
                }
                w.ready = true;
            }
            case BAND -> {
                if (p.position().distanceTo(bramOr(p)) > HAND + 1.5) return;
                w.ready = true;
                // his hand is empty the moment yours closes on it
                OpeningPlaces at = OpeningPlaces.get();
                if (at != null && lead(p) && !anyOtherAt(p, BAND)) stage(p, at.bramDesk(), yawTo(at.bramDesk(), p.position()), "hands_behind", "");
            }
            case LETTERS -> {
                if (p.position().distanceTo(bramOr(p)) > HAND + 1.5) return;
                w.ready = true;
                OpeningPlaces at = OpeningPlaces.get();
                if (at != null && lead(p) && !anyOtherAt(p, LETTERS)) stage(p, at.bramDesk(), yawTo(at.bramDesk(), p.position()), "hands_behind", "");
            }
            case BOOT, OPEN, STATUS, ATLAS -> w.ready = true;
            case POWER -> step(p, DIRECTIVE);
            default -> {}
        }
    }

    static void step(ServerPlayer p, int step) {
        OpeningState st = OpeningDirector.state(p);
        st.step = step;
        Walk w = walk(p);
        w.stepAt = OpeningDirector.now(p);
        w.ready = false;
        w.nudges = 0;
        w.lastNudge = w.stepAt;
        if (step != SIGN) w.stamped = false;
        OpeningDirector.sendScene(p);
        DirectorLog.log(p, "opening", "registry step " + step);
    }

    // ---- Bram ---------------------------------------------------------------------------------------------------------

    /** One player stages Bram (the first in the Registry): a party follows him together. */
    private static boolean lead(ServerPlayer p) {
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (OpeningDirector.beat(o) == Prologue.REGISTRY) return o == p;
        }
        return true;
    }

    /**
     * The walk: Bram goes on to the next point once you're close behind him; lag and he waits and turns ("Still with
     * me?"); run ahead of him and he says so once ("Habit, I know.") and walks on.
     */
    private static void escort(ServerPlayer p, OpeningPlaces at, Walk w, long now) {
        Vec3[] way = at.walk();
        Vec3 bram = CitizenHooks.where(p.level().getServer().overworld(), "bram");
        Vec3 me = p.position();
        int last = way.length - 1;
        Vec3 target = way[Math.min(w.wp, last)];
        double toBram = bram == null ? me.distanceTo(target) : me.distanceTo(bram);
        boolean there = bram == null || bram.distanceTo(target) < 1.2;
        if (w.wp < last && there && toBram < FOLLOW) w.wp++;
        // you're further along than he is: he skips on to keep in front
        double mine = me.distanceTo(at.lensMark()), his = (bram == null ? target : bram).distanceTo(at.lensMark());
        if (mine + 6 < his) {
            while (w.wp < last && way[w.wp].distanceTo(at.lensMark()) > mine) w.wp++;
            if (!w.ahead) {
                w.ahead = true;
                say(p, at, w, "Wrong way, Runner. Old habits, I know. It's this way.");
            }
        }
        if (toBram > LAG && there) {
            if (w.lagSince < 0) w.lagSince = now;
            if (now - w.lagSince > 60 && now - w.lastNudge > 20 * 12) {
                w.lastNudge = now;
                say(p, at, w, w.nudges++ % 2 == 0 ? "Still with me?" : "Take your time. I'll wait.");
            }
        } else w.lagSince = -1;
        if (now % 10 == 1) {
            Vec3 t = way[Math.min(w.wp, last)];
            // waiting, he turns to you; walking, the way he walks
            float yaw = there ? yawTo(t, me) : yawTo(bram == null ? t : bram, t);
            stage(p, t, yaw, there ? (now - w.lastLine < 60 ? "talk" : "hands_behind") : "", "");
        }
        if (w.wp >= 2 && !w.archive && !w.ahead && now - w.lastLine > 80) {
            // the atrium, past the archive's door
            w.archive = true;
            say(p, at, w, "A gate opened inside the city walls, right under the bells. The archive has been up all night trying to work out how.");
        }
    }

    /** Round the counter to behind it, then there with his act and prop (talking while a line is fresh). */
    private static void toDesk(ServerPlayer p, OpeningPlaces at, Walk w, String act, String prop) {
        long now = OpeningDirector.now(p);
        if (now % 10 != 1) return;
        Vec3[] way = at.toDesk();
        Vec3 bram = CitizenHooks.where(p.level().getServer().overworld(), "bram");
        // no body yet, or far off (a relog, a skip of the walk): straight behind the desk, never put down beside you
        if (bram == null || bram.distanceTo(at.bramLens()) > 6 && w.deskWp < 1) w.deskWp = way.length - 1;
        if (w.deskWp < 0) w.deskWp = 0;
        Vec3 t = way[Math.min(w.deskWp, way.length - 1)];
        if (bram != null && bram.distanceTo(t) < 1.0 && w.deskWp < way.length - 1) t = way[++w.deskWp];
        boolean behind = w.deskWp >= way.length - 1;
        // a friend still to be handed a band: he holds one out for them too
        if (prop.isEmpty() && anyAt(p, BAND)) {
            prop = "holo_band";
            act = "serve";
        }
        String a = behind ? (act.isEmpty() && now - w.lastLine < 60 ? "talk" : act) : "";
        stage(p, t, behind ? yawTo(t, p.position()) : yawTo(bram == null ? t : bram, t), a, behind ? prop : "");
    }

    private static boolean anyOtherAt(ServerPlayer p, int step) {
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o != p && OpeningDirector.beat(o) == Prologue.REGISTRY && OpeningDirector.state(o).step == step) return true;
        }
        return false;
    }

    private static boolean anyAt(ServerPlayer p, int step) {
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (OpeningDirector.beat(o) == Prologue.REGISTRY && OpeningDirector.state(o).step == step) return true;
        }
        return false;
    }

    private static void stage(ServerPlayer p, @Nullable Vec3 at, float yaw, String act, String prop) {
        CitizenHooks.stage(p.level().getServer().overworld(), "bram", at, yaw, act, prop);
    }

    /** Bram's hand-over spot: his body if it's here, else behind the desk. */
    private static Vec3 bramOr(ServerPlayer p) {
        Vec3 b = CitizenHooks.where(p.level().getServer().overworld(), "bram");
        OpeningPlaces at = OpeningPlaces.get();
        return b != null ? b : at != null ? at.bramDesk() : p.position();
    }

    /** The opening is over: Bram goes back to his day. */
    static void release(ServerPlayer p) {
        WALKS.remove(p.getUUID());
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o != p && OpeningDirector.beat(o) == Prologue.REGISTRY) return; // someone else still has him
        }
        stage(p, null, 0F, "", "");
    }

    /** Minecraft's yaw from one spot looking at another. */
    static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * Mth.RAD_TO_DEG);
    }

    /** A nudge when you've kept him waiting (every 30 s, at most three times). */
    private static void nudge(ServerPlayer p, OpeningPlaces at, Walk w, long now, String line) {
        if (now - w.lastNudge < NUDGE || w.nudges >= 3) return;
        w.lastNudge = now;
        w.nudges++;
        say(p, at, w, line);
    }

    /** A line from Bram, drawn as speech where he stands. */
    static void say(ServerPlayer p, OpeningPlaces at, Walk w, String line) {
        w.lastLine = OpeningDirector.now(p);
        Vec3 where = bramOr(p).add(0, 1.8, 0);
        AwakenNet.send(p, new OpeningPayloads.Cue(OpeningPayloads.CUE_LINE, where, 0F, 20 * 3 + line.length() * 2, "Bram|" + line));
    }

    // ---- the letters ------------------------------------------------------------------------------------------------

    /** Bram's letters for Ilse (SPEC §27.6), into the pack once; the first thread's errand carries them to the camp. */
    static void giveLetters(ServerPlayer p) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id("ilse_letters"));
        if (item == net.minecraft.world.item.Items.AIR || p.getInventory().contains(new ItemStack(item))) return;
        p.getInventory().placeItemBackInInventory(new ItemStack(item), net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- the licence -------------------------------------------------------------------------------------------------

    /** The Hunter's Licence, E-rank, signed in your name (once). */
    static void giveLicence(ServerPlayer p) {
        if (!Story.markSeen(p, "opening:licence")) return;
        ItemStack s = new ItemStack(OpeningModule.LICENCE.get());
        s.set(DataComponents.ITEM_NAME, Component.literal("Hunter's Licence · E-Rank"));
        long day = PlayDays.current(p.level().getServer());
        Style grey = Style.EMPTY.withColor(0xA8B4C4).withItalic(false);
        Style ink = Style.EMPTY.withColor(0x9EB6D8).withItalic(true);
        s.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Hunter Association of Carillon").withStyle(grey),
                Component.literal("Signed: " + p.getPlainTextName()).withStyle(ink),
                Component.literal("Rank E · day " + (day + 1) + " · witnessed: Bram").withStyle(grey))));
        CompoundTag tag = new CompoundTag();
        tag.putString("awaken", "licence");
        tag.putString("holder", p.getUUID().toString());
        tag.putString("signed", p.getPlainTextName());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // into the pack, not the hand: the band goes into your hand next
        var inv = p.getInventory();
        for (int i = net.minecraft.world.entity.player.Inventory.SELECTION_SIZE; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE && !s.isEmpty(); i++) {
            if (inv.getItem(i).isEmpty()) {
                inv.setItem(i, s);
                s = ItemStack.EMPTY;
            }
        }
        if (!s.isEmpty() && !p.addItem(s) && !s.isEmpty()) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }
}
