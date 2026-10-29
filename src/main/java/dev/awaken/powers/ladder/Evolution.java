package dev.awaken.powers.ladder;

import dev.awaken.core.api.Notices;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.Powers;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The moment a power climbs its ladder (SPEC 36.6). In a fight: a short sting (a small bloom on you, a rising chime) and the
 * moment waits; when calm (now, or once the fight ends): the EVOLVE bloom everyone near sees, a chord, and a Signal window
 * that names the new step and says what it does. Loadout {@code seen} keeps each power's moment to once per level.
 */
public final class Evolution {
    private Evolution() {}

    /** Mastery or a Power Core raised a power from one level to another. */
    public static void levelled(ServerPlayer player, PowerType type, int from, int to) {
        if (to <= from) return;
        long now = player.level().getServer().overworld().getGameTime();
        if (PowerState.of(player).inCombat(now)) {
            sting(player, type, to);
            return;
        }
        moment(player, type, to);
    }

    /** Once a second: a moment the fight held back plays when it is calm again; an older save's one window about ladders. */
    public static void tick(ServerPlayer player, Loadout l, long now) {
        if (now % 20 != 7) return;
        if (Powers.ladderIntroPending(l)) {
            Powers.clearLadderIntro(l);
            intro(player);
            return;
        }
        if (PowerState.of(player).inCombat(now)) return;
        for (Loadout.Owned o : l.owned().values()) {
            if (o.level() > l.seen(o.id())) {
                PowerType t = PowerRegistry.get(o.id());
                if (t != null) moment(player, t, o.level());
                else Powers.markSeen(l, o.id(), o.level());
                return; // one a second, never a pile
            }
        }
    }

    private static void sting(ServerPlayer player, PowerType type, int level) {
        Fx.at(player.level(), FxKind.EVOLVE, FxStyle.of(type.family), player.position()).caster(player).aux(0).level(level)
                .powerOf(type).flag(FxEvent.QUIET).send();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), PowerSounds.EVOLVE_STING.get(), SoundSource.PLAYERS, 0.8f, 1f);
    }

    public static void moment(ServerPlayer player, PowerType type, int level) {
        Loadout l = Powers.loadout(player);
        Powers.markSeen(l, type.id.toString(), level);
        boolean top = level >= Loadout.MAX_LEVEL;
        Fx.at(player.level(), FxKind.EVOLVE, FxStyle.of(type.family), player.position()).caster(player).aux(1).level(level)
                .powerOf(type).send();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), (top ? PowerSounds.EVOLVE_AWAKEN : PowerSounds.EVOLVE).get(),
                SoundSource.PLAYERS, 1f, 1f);
        String id = type.path;
        Component title = top
                ? Component.literal(type.name + " awakened: ").append(PowerLadder.name(id, level))
                : Component.literal(type.name + ": ").append(PowerLadder.name(id, level));
        var b = SignalWindow.builder(SignalWindow.Kind.NOTICE, title)
                .row(Component.literal("Level"), Component.literal(level + " of " + Loadout.MAX_LEVEL))
                .body(Component.empty().append(PowerLadder.does(id, level)).append(" ").append(PowerLadder.look(id, level)));
        if (!top) b.row(Component.literal("Next"), Component.empty().append(PowerLadder.name(id, level + 1)).append(" at level " + (level + 1)));
        else b.row(Component.literal("Cast"), PowerLadder.body(id));
        b.footer(Component.literal("Status · K shows the whole ladder").withStyle(ChatFormatting.GRAY));
        b.tier(Notices.Tier.IMPORTANT).topic("power_ladder_" + id);
        if (top) b.moment();
        Signal.send(player, b.ttl(20 * 10).build());
    }

    private static void intro(ServerPlayer player) {
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Your powers grow in steps now"))
                .body(Component.literal("Every power has a ladder. Each level adds a named step that changes how it looks and what it does, "
                        + "and at level 5 it awakens. The levels you already had keep their steps. Open Status with K and pick a power to see its ladder."))
                .tier(Notices.Tier.IMPORTANT).topic("power_ladder_intro").ttl(20 * 20).build());
    }
}
