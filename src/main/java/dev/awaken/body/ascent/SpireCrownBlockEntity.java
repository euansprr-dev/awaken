package dev.awaken.body.ascent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.above.ruin.RuinBuilder;
import dev.awaken.core.api.ArrivalHooks;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Tether Spire's crown (SPEC §17.11). Ten times a second it watches the plaza's ring at the spire's foot (whoever
 * stands in it is at the start; the climb is timed from the last moment they did) and the deck round itself (whoever
 * reaches it has climbed). A climb counts only by moving (ArrivalHooks.byMovement and the Ascent's own trail): then its
 * time, the climber's best and the spire's record are shown, the first climb gives 10 Tether mastery and every one a
 * couple more, and a faster time joins the spire's records (its best five, one each). Saved: the records and who has
 * climbed it (schema 1); the start times are not.
 */
public class SpireCrownBlockEntity extends BlockEntity {
    static final int SCHEMA = 2, KEEP = 5;
    /** The deck: within this of the crown block's centre. The plaza: within this of the column's foot (a hero tree's ground). */
    static final double DECK = 2.6, PLAZA = 7.0, HERO_PLAZA = 22.0;
    /** Which site it crowns (SPEC §17.11, §17.16): a Tether Spire, the Elder Colossus, the Lumen Spire. */
    public static final int SITE_SPIRE = 0, SITE_COLOSSUS = 1, SITE_LUMEN = 2;
    private int site = SITE_SPIRE, ground = Integer.MIN_VALUE;
    /** A climb longer than this (or with no start seen) is still a climb, just not timed. */
    static final int LONGEST = 20 * 120;

    /** One record: who, their name then, their time in ticks. */
    public record Run(UUID who, String name, int ticks) {
        static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("who").forGetter(Run::who),
                Codec.STRING.fieldOf("name").forGetter(Run::name),
                Codec.INT.fieldOf("ticks").forGetter(Run::ticks)
        ).apply(i, Run::new));
    }

    private final List<Run> records = new ArrayList<>();
    private final Set<UUID> climbed = new HashSet<>();
    private final Map<UUID, Long> leftPlaza = new HashMap<>(), arrived = new HashMap<>();

    public SpireCrownBlockEntity(BlockPos pos, BlockState state) {
        super(AscentContent.SPIRE_CROWN_BE.get(), pos, state);
    }

    /** The plaza's floor under this crown (a spire's blueprint levels; a hero tree's foot). */
    int plazaY() {
        return site != SITE_SPIRE && ground != Integer.MIN_VALUE ? ground : worldPosition.getY() - (RuinBuilder.SPIRE_CROWN + 1 - RuinBuilder.SPIRE_PLAZA);
    }

    double plaza() {
        return site == SITE_SPIRE ? PLAZA : HERO_PLAZA;
    }

    public int site() {
        return site;
    }

    /** A hero tree's crown: which tree, and its foot's height (HeroCrowns places it). */
    void site(int site, int ground) {
        this.site = site;
        this.ground = ground;
        setChanged();
        if (site != SITE_SPIRE && level != null && !level.isClientSide()) HeroCrowns.ward(worldPosition, ground, site);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (site != SITE_SPIRE && level != null && !level.isClientSide()) HeroCrowns.ward(worldPosition, ground, site);
    }

    /** Whether this player has reached this crown by a climb (the Horizon stops naming it then). */
    boolean hasClimbed(UUID who) {
        return climbed.contains(who);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (site != SITE_SPIRE && level != null && !level.isClientSide()) HeroCrowns.unward(worldPosition);
    }

    public List<Run> records() {
        return List.copyOf(records);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, SpireCrownBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 2 != 0 || !(level instanceof ServerLevel sl)) return;
        long now = level.getGameTime();
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        int floor = be.plazaY();
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - cx, dz = p.getZ() - cz, h = Math.sqrt(dx * dx + dz * dz);
            // a spire's plaza is flat; a tree's foot is roots and ground, a little up and down
            double lo = be.site == SITE_SPIRE ? 0.5 : -1.5, hi = be.site == SITE_SPIRE ? 3.5 : 4.5;
            if (h <= be.plaza() && p.getY() >= floor + lo && p.getY() < floor + hi) be.leftPlaza.put(p.getUUID(), now);
            else if (h <= DECK && Math.abs(p.getY() - pos.getY()) < 1.5) be.reached(p, now);
        }
    }

    /** A climber on the deck: once a visit (five seconds apart at least). */
    void reached(ServerPlayer p, long now) {
        Long last = arrived.put(p.getUUID(), now);
        if (last != null && now - last < 100) return;
        // the crown answers only the line (SPEC §17.25): a pearl, elytra or a Runner hang still earns the view, not this
        if (!ArrivalHooks.byLine(p)) {
            p.sendSystemMessage(Component.translatable("awaken.ascent.spire.built"), true);
            return;
        }
        if (!ArrivalHooks.byMovement(p) || !Ascent.arrivedByMovement(p, 20)) {
            p.sendSystemMessage(Component.translatable("awaken.ascent.spire.built"), true);
            return;
        }
        Long start = leftPlaza.remove(p.getUUID());
        int ticks = start == null || now - start > LONGEST ? -1 : (int) (now - start);
        boolean first = climbed.add(p.getUUID());
        Run mine = null;
        for (Run r : records) if (r.who().equals(p.getUUID())) mine = r;
        boolean best = ticks > 0 && (mine == null || ticks < mine.ticks());
        if (best) {
            if (mine != null) records.remove(mine);
            records.add(new Run(p.getUUID(), p.getPlainTextName(), ticks));
            records.sort((a, b) -> Integer.compare(a.ticks(), b.ticks()));
            while (records.size() > KEEP) records.removeLast();
        }
        setChanged();
        AscentServer.link(p, AscentMemory.Track.TETHER, first ? 10 : 2);
        dev.awaken.progression.Progression.count(p, site == SITE_SPIRE ? "awaken:ascent_spires_climbed" : "awaken:ascent_crowns_reached");
        AscentQuests.advance(p, site == SITE_SPIRE ? AscentQuests.SPIRE : AscentQuests.HERO_CROWN);
        if (first && site != SITE_SPIRE) {
            // a hero tree's crown the first time: a find, and a fifth of a level
            dev.awaken.progression.Progression.grantXp(p, dev.awaken.progression.Curve.xpToNext(dev.awaken.progression.Progression.profile(p).level()) * 0.2,
                    dev.awaken.progression.Progression.XpSource.DISCOVERY);
        }
        p.level().playSound(null, worldPosition, AscentContent.VOW_CALL.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1F, best ? 1.35F : 1.15F);
        Run top = records.isEmpty() ? null : records.getFirst();
        String time = ticks > 0 ? String.format("%.1f s", ticks / 20.0) : "";
        String line = mine == null && !best ? "" : best && mine == null ? "your first time up" : best ? String.format("a new best (was %.1f s)", mine.ticks() / 20.0)
                : String.format("your best %.1f s", mine.ticks() / 20.0);
        String what = site == SITE_SPIRE ? "spire" : "tree";
        String record = top == null ? "" : top.who().equals(p.getUUID()) ? "the " + what + "'s record is yours"
                : String.format("the %s's record %.1f s, %s", what, top.ticks() / 20.0, top.name());
        String key = switch (site) {
            case SITE_COLOSSUS -> "awaken.ascent.crown.colossus";
            case SITE_LUMEN -> "awaken.ascent.crown.lumen";
            default -> "awaken.ascent.spire.climbed";
        };
        Signal.toast(p, Component.translatable(key, time.isEmpty() ? "" : " · " + time));
        StringBuilder sub = new StringBuilder();
        for (String s : new String[]{line, record}) if (!s.isEmpty()) sub.append(sub.isEmpty() ? "" : " · ").append(s);
        if (!sub.isEmpty()) p.sendSystemMessage(Component.literal(sub.toString()), true);
        dev.awaken.core.DirectorLog.log(p, "ascent", (site == SITE_SPIRE ? "a Tether Spire climbed" : site == SITE_COLOSSUS
                ? "the Elder Colossus's crown reached" : "the Lumen Spire's crown reached") + (time.isEmpty() ? "" : " in " + time)
                + " at " + worldPosition.toShortString() + (first ? " (first time)" : ""));
    }

    /** Client: the crown breathes a ring of light now and then, like the Wings core. */
    static void clientTick(Level level, BlockPos pos, BlockState state, SpireCrownBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 80 == 0) dev.awaken.body.ascent.client.AscentFxBridge.coreRing(pos);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.putInt("site", site);
        if (ground != Integer.MIN_VALUE) out.putInt("ground", ground);
        out.store("records", Run.CODEC.listOf(), List.copyOf(records));
        out.store("climbed", UUIDUtil.CODEC.listOf(), List.copyOf(climbed));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        records.clear();
        climbed.clear();
        site = in.getIntOr("site", SITE_SPIRE);
        ground = in.getIntOr("ground", Integer.MIN_VALUE);
        in.read("records", Run.CODEC.listOf()).ifPresent(records::addAll);
        in.read("climbed", UUIDUtil.CODEC.listOf()).ifPresent(climbed::addAll);
    }

    /** Tests: as if this player stood on the plaza {@code ago} ticks ago. */
    void startedAgo(ServerPlayer p, int ago) {
        leftPlaza.put(p.getUUID(), p.level().getGameTime() - ago);
    }

    Vec3 centre() {
        return Vec3.atCenterOf(worldPosition);
    }
}
