package dev.awaken.opening;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.story.Story;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Registry, a stub per the lean plan (SPEC §16.4): you walk from the infirmary to the Registry hall and stand before
 * the Assessment Lens, a shard of the Choir. It reads you; the clerk stamps your licence (E-rank); the Holo wristband is
 * fitted and boots; your Status shows for the first time; the System offers your first power; your first Directive;
 * "The city is yours." Steps that open a screen wait for it to close (or a while); the rest run on the clock.
 */
final class OpeningRegistry {
    private OpeningRegistry() {}

    static final int WALK = 0, READ = 1, LICENCE = 2, WRISTBAND = 3, STATUS = 4, POWER = 5, DIRECTIVE = 6, YOURS = 7;
    /** How long each clocked step plays, and the most a screen step waits. */
    static final int READ_TICKS = 110, LICENCE_TICKS = 100, WRISTBAND_TICKS = 70, SCREEN_MAX = 20 * 75, DIRECTIVE_TICKS = 90, YOURS_TICKS = 90;
    /** Close enough to the Lens to be read. */
    static final double MARK = 2.2;

    /** When the current step began (transient: a relog plays the step again). */
    private static final Map<UUID, Long> STEP_AT = new HashMap<>();

    static void forget() {
        STEP_AT.clear();
    }

    static void tick(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) {
            OpeningDirector.finish(p);
            return;
        }
        OpeningState st = OpeningDirector.state(p);
        long now = OpeningDirector.now(p);
        long since = STEP_AT.computeIfAbsent(p.getUUID(), k -> now);
        long age = now - since;
        boolean client = AwakenNet.canReceive(p, OpeningPayloads.Scene.TYPE);
        switch (st.step) {
            case WALK -> {
                if (p.tickCount % 5 == 0 && p.position().distanceTo(at.lensMark()) < MARK) step(p, READ);
                // no client to walk it (a test player): the clerk fetches you
                if (!client && age > 20 * 30) step(p, READ);
            }
            case READ -> {
                if (age >= READ_TICKS) step(p, LICENCE);
            }
            case LICENCE -> {
                if (age == 1) {
                    giveLicence(p);
                    say(p, at, "Clerk", "E-rank. Everyone starts at E. Sign there; the Lens already has.");
                }
                if (age >= LICENCE_TICKS) step(p, WRISTBAND);
            }
            case WRISTBAND -> {
                if (age == 1) say(p, at, "Clerk", "Hold out your wrist. It's cold for a moment, then it's yours.");
                if (age >= WRISTBAND_TICKS) step(p, STATUS);
            }
            case STATUS -> {
                if (!client || age >= SCREEN_MAX) step(p, POWER);
            }
            case POWER -> {
                if (age == 1) OpeningModule.offerFirstPower(p);
                if (!client || age >= SCREEN_MAX || age > 40 && OpeningModule.firstPowerChosen(p)) step(p, DIRECTIVE);
            }
            case DIRECTIVE -> {
                if (age == 1) {
                    OpeningModule.offerDirective(p);
                    say(p, at, "Clerk", "The Signal sets your Directives. The Association pays for them. Off you go.");
                }
                if (age >= DIRECTIVE_TICKS) step(p, YOURS);
            }
            case YOURS -> {
                if (age >= YOURS_TICKS) {
                    STEP_AT.remove(p.getUUID());
                    OpeningDirector.finish(p);
                }
            }
            default -> step(p, YOURS);
        }
    }

    /** The client closed the step's screen (the Status, the first power). */
    static void onReady(ServerPlayer p, int n) {
        OpeningState st = OpeningDirector.state(p);
        if (OpeningDirector.beat(p) != dev.awaken.story.StoryProgress.Prologue.REGISTRY || st.step != n) return;
        if (n == STATUS || n == POWER) step(p, n + 1);
    }

    static void step(ServerPlayer p, int step) {
        OpeningState st = OpeningDirector.state(p);
        st.step = step;
        STEP_AT.put(p.getUUID(), OpeningDirector.now(p));
        OpeningDirector.sendScene(p);
        DirectorLog.log(p, "opening", "registry step " + step);
    }

    /** A line from someone in the room (the clerk), as the client draws speech. */
    static void say(ServerPlayer p, OpeningPlaces at, String who, String line) {
        Vec3 where = at.lens().add(0, -0.4, -2.0);
        AwakenNet.send(p, new OpeningPayloads.Cue(OpeningPayloads.CUE_LINE, where, 0F, 20 * 5, who + "|" + line));
    }

    /** The Hunter's Licence, E-rank (once). */
    static void giveLicence(ServerPlayer p) {
        if (!Story.markSeen(p, "opening:licence")) return;
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.ITEM_NAME, Component.literal("Hunter's Licence · E-Rank"));
        s.set(DataComponents.RARITY, Rarity.UNCOMMON);
        long day = PlayDays.current(p.level().getServer());
        Style grey = Style.EMPTY.withColor(0xA8B4C4).withItalic(false);
        s.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Hunter Association of Carillon").withStyle(grey),
                Component.literal("Registered: " + p.getPlainTextName()).withStyle(grey),
                Component.literal("Rank E · day " + (day + 1)).withStyle(grey))));
        CompoundTag tag = new CompoundTag();
        tag.putString("awaken", "licence");
        tag.putString("holder", p.getUUID().toString());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (!p.addItem(s) && !s.isEmpty()) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }
}
