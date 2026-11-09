package dev.awaken.gates.realms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** What a gate record keeps of its Realm: enough to draw the same land again on any thread after a restart. */
public record RealmRef(String blueprint, long seed, int version, int cell) {
    public static final Codec<RealmRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("blueprint").forGetter(RealmRef::blueprint),
            Codec.LONG.fieldOf("seed").forGetter(RealmRef::seed),
            Codec.INT.fieldOf("version").forGetter(RealmRef::version),
            Codec.INT.fieldOf("cell").forGetter(RealmRef::cell)
    ).apply(i, RealmRef::new));
}
