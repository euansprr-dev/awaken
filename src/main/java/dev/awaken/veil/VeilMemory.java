package dev.awaken.veil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.GlobalPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the Veil remembers about one player (attachment {@code awaken:veil_memory}, kept through death). Mutable on the
 * server thread; saved with the player.
 */
public final class VeilMemory {
    public static final int VERSION = 1;
    static final int MAX_SECRETS = 4096;
    static final int MAX_GATES = 1024;

    /** Where to go when leaving the Stillpoint. */
    public record ReturnPoint(GlobalPos pos, float yaw, float pitch) {
        public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("pos").forGetter(ReturnPoint::pos),
                Codec.FLOAT.fieldOf("yaw").forGetter(ReturnPoint::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(ReturnPoint::pitch)
        ).apply(i, ReturnPoint::new));
    }

    public boolean firstMeditation;
    public boolean glimpsed;
    public int projections;
    public int tipsShown;
    public final Map<String, Integer> spiritLines = new HashMap<>();
    public final Set<String> spiritsComplete = new LinkedHashSet<>();
    public final Map<String, Long> spiritLastCounted = new HashMap<>();
    public final Set<String> gatesScouted = new LinkedHashSet<>();
    public final Set<String> riftsCleared = new LinkedHashSet<>();
    public final LongLinkedOpenHashSet secretsFound = new LongLinkedOpenHashSet();
    public Optional<ReturnPoint> stillpointReturn = Optional.empty();

    public VeilMemory() {}

    public boolean markSecret(long key) {
        if (!secretsFound.add(key)) return false;
        while (secretsFound.size() > MAX_SECRETS) secretsFound.removeFirstLong();
        return true;
    }

    public boolean markGate(String id) {
        if (!gatesScouted.add(id)) return false;
        while (gatesScouted.size() > MAX_GATES) gatesScouted.remove(gatesScouted.iterator().next());
        return true;
    }

    private record Snapshot(int version, boolean firstMeditation, boolean glimpsed, int projections, int tipsShown,
                            Map<String, Integer> spiritLines, List<String> spiritsComplete, Map<String, Long> spiritLastCounted,
                            List<String> gatesScouted, List<String> riftsCleared, List<Long> secrets, Optional<ReturnPoint> ret) {}

    public static final MapCodec<VeilMemory> MAP_CODEC = RecordCodecBuilder.<Snapshot>mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema_version", VERSION).forGetter(Snapshot::version),
            Codec.BOOL.optionalFieldOf("first_meditation", false).forGetter(Snapshot::firstMeditation),
            Codec.BOOL.optionalFieldOf("glimpsed", false).forGetter(Snapshot::glimpsed),
            Codec.INT.optionalFieldOf("projections", 0).forGetter(Snapshot::projections),
            Codec.INT.optionalFieldOf("tips", 0).forGetter(Snapshot::tipsShown),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("spirit_lines", Map.of()).forGetter(Snapshot::spiritLines),
            Codec.STRING.listOf().optionalFieldOf("spirits_complete", List.of()).forGetter(Snapshot::spiritsComplete),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("spirit_last_counted", Map.of()).forGetter(Snapshot::spiritLastCounted),
            Codec.STRING.listOf().optionalFieldOf("gates_scouted", List.of()).forGetter(Snapshot::gatesScouted),
            Codec.STRING.listOf().optionalFieldOf("rifts_cleared", List.of()).forGetter(Snapshot::riftsCleared),
            Codec.LONG.listOf().optionalFieldOf("secrets", List.of()).forGetter(Snapshot::secrets),
            ReturnPoint.CODEC.optionalFieldOf("stillpoint_return").forGetter(Snapshot::ret)
    ).apply(i, Snapshot::new)).xmap(VeilMemory::fromSnapshot, VeilMemory::toSnapshot);

    private static VeilMemory fromSnapshot(Snapshot s) {
        VeilMemory m = new VeilMemory();
        m.firstMeditation = s.firstMeditation();
        m.glimpsed = s.glimpsed();
        m.projections = s.projections();
        m.tipsShown = s.tipsShown();
        m.spiritLines.putAll(s.spiritLines());
        m.spiritsComplete.addAll(s.spiritsComplete());
        m.spiritLastCounted.putAll(s.spiritLastCounted());
        m.gatesScouted.addAll(s.gatesScouted());
        m.riftsCleared.addAll(s.riftsCleared());
        for (long l : s.secrets()) m.secretsFound.add(l);
        m.stillpointReturn = s.ret();
        return m;
    }

    private Snapshot toSnapshot() {
        List<Long> secrets = new ArrayList<>(secretsFound.size());
        for (long l : secretsFound) secrets.add(l);
        return new Snapshot(VERSION, firstMeditation, glimpsed, projections, tipsShown, Map.copyOf(spiritLines), List.copyOf(spiritsComplete),
                Map.copyOf(spiritLastCounted), List.copyOf(gatesScouted), List.copyOf(riftsCleared), secrets, stillpointReturn);
    }
}
