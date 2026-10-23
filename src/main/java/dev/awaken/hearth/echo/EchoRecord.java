package dev.awaken.hearth.echo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * One bound Echo in a player's roster. {@code data} is the fallen mob's saved entity data (position, health and UUID
 * stripped) so a summon is a faithful copy of the original. Immutable: replace the record to change it.
 *
 * @param field     true = marches with the player; false = on duty at home (fills a role pad)
 * @param downUntil game time before which a fallen Echo cannot reform
 */
public record EchoRecord(UUID id, String type, CompoundTag data, String name, int level, int xp, boolean field,
                         long downUntil, String origin, int contentLevel, long boundDay) {

    public static final Codec<EchoRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(EchoRecord::id),
            Codec.STRING.fieldOf("type").forGetter(EchoRecord::type),
            CompoundTag.CODEC.fieldOf("data").forGetter(EchoRecord::data),
            Codec.STRING.fieldOf("name").forGetter(EchoRecord::name),
            Codec.INT.fieldOf("level").forGetter(EchoRecord::level),
            Codec.INT.fieldOf("xp").forGetter(EchoRecord::xp),
            Codec.BOOL.fieldOf("field").forGetter(EchoRecord::field),
            Codec.LONG.optionalFieldOf("down_until", 0L).forGetter(EchoRecord::downUntil),
            Codec.STRING.optionalFieldOf("origin", "ELITE").forGetter(EchoRecord::origin),
            Codec.INT.optionalFieldOf("content_level", 1).forGetter(EchoRecord::contentLevel),
            Codec.LONG.optionalFieldOf("bound_day", 0L).forGetter(EchoRecord::boundDay)
    ).apply(i, EchoRecord::new));

    public static final int MAX_LEVEL = 50;

    /** XP an Echo needs to go from {@code level} to the next. Slow on purpose: kills give 1-5, training 1 per minute. */
    public static int xpToNext(int level) {
        return 12 + 8 * level;
    }

    public EchoRecord withField(boolean f) {
        return new EchoRecord(id, type, data, name, level, xp, f, downUntil, origin, contentLevel, boundDay);
    }

    public EchoRecord withDownUntil(long t) {
        return new EchoRecord(id, type, data, name, level, xp, field, t, origin, contentLevel, boundDay);
    }

    /** Adds XP and returns the record with any level-ups applied. */
    public EchoRecord withXp(int gained) {
        int lv = level, x = xp + Math.max(0, gained);
        while (lv < MAX_LEVEL && x >= xpToNext(lv)) {
            x -= xpToNext(lv);
            lv++;
        }
        if (lv >= MAX_LEVEL) x = 0;
        return new EchoRecord(id, type, data, name, lv, x, field, downUntil, origin, contentLevel, boundDay);
    }

    public boolean isBoss() {
        return "BOSS".equals(origin);
    }
}
