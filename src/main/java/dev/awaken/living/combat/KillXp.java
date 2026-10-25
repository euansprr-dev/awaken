package dev.awaken.living.combat;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.Parties;
import dev.awaken.living.LivingModule;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Kill XP in the open world (SPEC §21.3): every creature pays by its own level and role, through the {@code hunt}
 * budget, then up to 40% more for creatures above you and less for those below. A sheep pays a little; an elite pays
 * like four hunters. Named beasts pay through their own first-kill rules, gates through their floors.
 */
public final class KillXp {
    private KillXp() {}

    /** Share of a level a kill of an on-level hunter pays before the budget. */
    public static final double BASE = 0.03;

    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || !(dead.level() instanceof ServerLevel level) || !Regions.on(level)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        CreatureTag tag = CreatureTag.of(dead);
        int lv = tag.present() ? tag.level() : Regions.creatureLevel(level, dead.blockPosition(), dead instanceof Enemy ? Regions.Role.HUNTER : Regions.Role.AMBIENT);
        double role = role(tag, dead);
        if (role <= 0) return;
        pay(killer, dead, lv, role);
        // the party nearby shares the kill (SPEC "Together"), each through their own budget and level
        for (ServerPlayer mate : Parties.sharing(killer, dead)) pay(mate, dead, lv, role);
    }

    private static void pay(ServerPlayer p, LivingEntity dead, int creatureLevel, double role) {
        int mine = Progression.profile(p).level();
        XpStream.origin(p, dead.position().add(0, dead.getBbHeight() * 0.6, 0), dev.awaken.living.LivingNet.XP_KILL);
        try {
            Progression.worldXp(p, LivingModule.HUNT, BASE * role, diff(creatureLevel - mine) * LivingModule.wildsRank(Progression.rank(p)));
        } finally {
            XpStream.clearOrigin(p);
        }
    }

    /** The role's weight (SPEC §21.3). */
    static double role(CreatureTag tag, LivingEntity dead) {
        if (tag.elite()) return 4.0;
        return switch (tag.kind()) {
            case PASSIVE, AMBIENT -> 0.15;
            case NEUTRAL -> 0.5;
            case HUNTER, MONSTER, GATE -> 1.0;
            case WARDEN, BOSS -> 6.0;
            case CAMP -> 8.0;
            case BEAST -> 0.0;
            case NONE -> dead instanceof Enemy ? 1.0 : 0.15;
        };
    }

    /** Creature level minus player level: +8% a level above (to +40%), 1 / (1 + levels below / 8) below. */
    public static double diff(int delta) {
        if (delta >= 0) return 1.0 + 0.08 * Math.min(5, delta);
        return 1.0 / (1.0 + (-delta) / 8.0);
    }
}
