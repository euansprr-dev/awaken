package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizenBody;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.citizens.CitizensNet;
import dev.awaken.citizens.Roster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The city's signs of life on the client: chimney smoke by the hour (bakeries before dawn, houses at breakfast and
 * supper, taverns all evening, forges through the working day), and the lines citizens say (wrapped once when they
 * arrive, drawn over their heads by CitizenRenderer). No entities and almost no cost: emitters beyond 128 blocks
 * are skipped, nearer ones puff less often the further they are.
 */
public final class CitizenFx {
    private CitizenFx() {}

    private static long[] pos = new long[0];
    private static byte[] kind = new byte[0];
    private static int cursor;

    static void emitters(CitizensNet.Emitters msg, IPayloadContext ctx) {
        CityGlow.emittersChanged();
        if (msg.replace()) {
            pos = msg.pos().clone();
            kind = msg.kind().clone();
        } else {
            // a settlement sent again (it slept and woke, it stopped and started: SPEC §28.6) adds only what's new
            it.unimi.dsi.fastutil.longs.LongOpenHashSet have = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(pos);
            int n = pos.length, add = 0;
            long[] np = Arrays.copyOf(pos, n + msg.pos().length);
            byte[] nk = Arrays.copyOf(kind, n + msg.kind().length);
            for (int i = 0; i < msg.pos().length && i < msg.kind().length; i++) {
                if (!have.add(msg.pos()[i])) continue;
                np[n + add] = msg.pos()[i];
                nk[n + add] = msg.kind()[i];
                add++;
            }
            pos = Arrays.copyOf(np, n + add);
            kind = Arrays.copyOf(nk, n + add);
        }
    }

    private static final Style SAY = Style.EMPTY.withColor(TextColor.fromRgb(0xFFF1D6));
    private static final Style ALOUD = Style.EMPTY.withColor(TextColor.fromRgb(0xEBD9B8)).withItalic(true);
    private static final Style SHOUT = Style.EMPTY.withColor(TextColor.fromRgb(0xFFD27A)).withBold(true);

    static void speech(CitizensNet.Speech msg, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entity());
        if (!(e instanceof CitizenBody b)) return;
        Style style = msg.kind() == 0 ? SAY : msg.kind() == 1 ? ALOUD : SHOUT;
        b.speech = mc.font.split(Component.literal(msg.text()).withStyle(style), 150);
        b.speechStart = b.tickCount;
        b.speechUntil = b.tickCount + msg.ticks();
        b.speechKind = msg.kind();
    }

    /** The name over a citizen you look at (made once per body). */
    static FormattedCharSequence label(CitizenBody b) {
        if (b.label == null) {
            Roster r = Roster.get();
            int rec = b.record();
            String name, job;
            if (b.settlement() != 0) {
                // (SPEC §28.4) another settlement's roster isn't on the client: the server sends "name|job"
                String l = b.label();
                int bar = l.indexOf('|');
                name = bar < 0 ? l : l.substring(0, bar);
                job = bar < 0 ? "" : l.substring(bar + 1);
            } else {
                name = r == null || rec < 0 || !r.isCitizen(rec) ? "" : r.name(rec);
                job = r == null || rec < 0 || !r.isCitizen(rec) ? "" : Roster.JOBS[r.job[rec]];
            }
            Component c = Component.literal(name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xF4E6C8)))
                    .append(Component.literal(job.isEmpty() ? "" : "  " + job.toLowerCase(Locale.ROOT)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA8A090)).withItalic(true)));
            b.label = c.getVisualOrderText();
            b.labelWidth = Minecraft.getInstance().font.width(c);
        }
        return b.label;
    }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null || pos.length == 0 || mc.isPaused()) return;
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        long now = level.getGameTime();
        // a slice of emitters a tick, each puffing at its own pace
        int n = Math.min(pos.length, 64);
        for (int k = 0; k < n; k++) {
            int i = cursor;
            cursor = (cursor + 1) % pos.length;
            long pk = pos[i];
            int x = BlockPos.getX(pk), y = BlockPos.getY(pk), z = BlockPos.getZ(pk);
            double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ(), dist2 = dx * dx + dz * dz;
            if (dist2 > (kind[i] == 4 ? 48 * 48 : 128 * 128)) continue;
            int strength = strength(kind[i], tod, i);
            if (strength == 0) continue;
            // every emitter is visited every pos.length/64 ticks; puff when its turn comes round often enough
            int every = Math.max(1, (int) (Math.sqrt(dist2) / 24)) * (4 - Math.min(3, strength));
            if (Math.floorMod(now / Math.max(1, pos.length / 64) + i, (long) every) != 0) continue;
            if (kind[i] == 4) {
                // a fountain: water spilling over the upper bowl's lip into the basin
                var rnd = level.getRandom();
                for (int j = 0; j < 3; j++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, x + 0.5 + Math.cos(a) * 0.55, y - 0.15,
                            z + 0.5 + Math.sin(a) * 0.55, 0, 0, 0);
                }
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, x + 0.5 + (rnd.nextDouble() - 0.5) * 2.4, y - 2.0,
                        z + 0.5 + (rnd.nextDouble() - 0.5) * 2.4, 0, 0.05, 0);
                continue;
            }
            double ya = kind[i] == 3 ? 0.03 : 0.0; // forges smoke darker
            level.addParticle(CitizensContent.CHIMNEY_SMOKE.get(), x + 0.5, y + 0.15, z + 0.5,
                    CitizenParticles.windX(level), ya, CitizenParticles.windZ(level));
        }
    }

    /** How hard a chimney smokes at this hour: 0 none .. 3 thick. */
    private static int strength(byte k, int tod, int i) {
        return switch (k) {
            case 0 -> tod >= 21000 || tod < 2500 ? 3 : tod < 8000 ? 1 : 0;                  // bakery: before dawn, then low
            // houses at breakfast and supper and through the evening; one in four keeps a fire in all day (M4 B3a)
            case 1 -> (i % 3 != 0) && (tod < 1600 || tod >= 11400 && tod < 16500) || i % 4 == 0 ? 1 : 0;
            case 4 -> 3;                                                                   // a fountain
            case 5, 6, 7 -> 0;                                                             // a lamp (CityGlow draws its light); a mill, a well (sound only)
            case 2 -> tod >= 9000 && tod < 18500 ? 2 : 0;                                 // taverns all evening
            case 3 -> tod >= 900 && tod < 10800 ? 2 : 0;                                  // forges in working hours
            default -> 2;
        };
    }

    /** The emitters as sent (CityGlow reads the lamp heads, kind 5). */
    static long[] positions() {
        return pos;
    }

    static byte[] kinds() {
        return kind;
    }

    static void clear() {
        pos = new long[0];
        kind = new byte[0];
    }

    @SuppressWarnings("unused")
    private static List<FormattedCharSequence> none() {
        return List.of();
    }
}
