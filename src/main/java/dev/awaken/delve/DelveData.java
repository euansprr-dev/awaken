package dev.awaken.delve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/**
 * What the deep remembers, per dimension (schemaVersion 2): the lodes whose heart has been broken (a heart gives once,
 * for everyone: lodes are shared), the 16-block sections where a player has built with solid blocks underground
 * (a roof you built or propped never caves in), and (2, SPEC §34.4/34.6) the Kindle embers burning in walls (their
 * light block, the face they're on, when they go out, whose) and each lit Delver lamp's lights.
 */
public final class DelveData extends SavedData {
    static final int SCHEMA = 2;
    /** Building above this is never under a lode's roof: no need to remember it. */
    static final int BUILT_BELOW_Y = 64;

    public static final Codec<DelveData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(d -> SCHEMA),
            Codec.LONG.listOf().optionalFieldOf("brokenHearts", List.of()).forGetter(d -> new LongArrayList(d.broken)),
            Codec.LONG.listOf().optionalFieldOf("builtSections", List.of()).forGetter(d -> new LongArrayList(d.built)),
            Ember.CODEC.listOf().optionalFieldOf("embers", List.of()).forGetter(d -> List.copyOf(d.embers)),
            Lamp.CODEC.listOf().optionalFieldOf("lamps", List.of()).forGetter(d -> List.copyOf(d.lamps.values()))
    ).apply(i, DelveData::new));

    /** A Kindle ember: its light block, the face of the rock it's stuck on (a Direction index), when it goes out, whose. */
    public record Ember(long light, int face, long until, java.util.UUID owner) {
        static final Codec<Ember> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("light").forGetter(Ember::light), Codec.INT.fieldOf("face").forGetter(Ember::face),
                Codec.LONG.fieldOf("until").forGetter(Ember::until), net.minecraft.core.UUIDUtil.CODEC.fieldOf("owner").forGetter(Ember::owner)
        ).apply(i, Ember::new));
    }

    /** A lit Delver lamp: where, the light blocks its wave set, its hall's name. */
    public record Lamp(long pos, List<Long> lights, String name) {
        static final Codec<Lamp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Lamp::pos), Codec.LONG.listOf().fieldOf("lights").forGetter(Lamp::lights),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Lamp::name)
        ).apply(i, Lamp::new));
    }

    public static final SavedDataType<DelveData> TYPE = new SavedDataType<>(Awaken.id("delve"), DelveData::new, CODEC);

    private final LongOpenHashSet broken = new LongOpenHashSet();
    private final LongOpenHashSet built = new LongOpenHashSet();
    private final List<Ember> embers = new java.util.ArrayList<>();
    private final java.util.Map<Long, Lamp> lamps = new java.util.LinkedHashMap<>();

    public DelveData() {}

    private DelveData(int schema, List<Long> broken, List<Long> built, List<Ember> embers, List<Lamp> lamps) {
        this.broken.addAll(broken);
        this.built.addAll(built);
        this.embers.addAll(embers);
        for (Lamp l : lamps) this.lamps.put(l.pos(), l);
    }

    // ---- embers and lamps ---------------------------------------------------------------------------------------------

    List<Ember> embers() {
        return embers;
    }

    void addEmber(Ember e) {
        embers.add(e);
        setDirty();
    }

    void removeEmber(Ember e) {
        if (embers.remove(e)) setDirty();
    }

    public java.util.Collection<Lamp> lamps() {
        return lamps.values();
    }

    public @org.jspecify.annotations.Nullable Lamp lamp(long pos) {
        return lamps.get(pos);
    }

    void putLamp(Lamp l) {
        lamps.put(l.pos(), l);
        setDirty();
    }

    @org.jspecify.annotations.Nullable Lamp removeLamp(long pos) {
        Lamp l = lamps.remove(pos);
        if (l != null) setDirty();
        return l;
    }

    public static DelveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Records that this lode's heart broke; false if it already had. */
    boolean breakHeart(long lode) {
        if (!broken.add(lode)) return false;
        setDirty();
        return true;
    }

    public boolean heartBroken(long lode) {
        return broken.contains(lode);
    }

    void built(BlockPos pos) {
        if (pos.getY() >= BUILT_BELOW_Y) return;
        if (built.add(SectionPos.asLong(pos))) setDirty();
    }

    /** Whether a player has built with solid blocks in the section holding {@code pos}. */
    public boolean builtAt(BlockPos pos) {
        return built.contains(SectionPos.asLong(pos));
    }

    /** Tests: forget a lode's heart and the building near a spot. */
    void forget(long lode, BlockPos near) {
        broken.remove(lode);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            built.remove(SectionPos.asLong(near.offset(dx * 16, dy * 16, dz * 16)));
        }
    }
}
