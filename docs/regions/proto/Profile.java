package dev.awaken.living.region;
public class Profile {
    public static void main(String[] a) {
        long seed = 3500122753516418813L; int cx = -1238, cz = -736;
        RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> new RegionMap.Land(RegionMap.Group.FOREST, 0));
        NewMap nm = new NewMap(seed, cx, cz);
        StringBuilder o = new StringBuilder(), n = new StringBuilder();
        for (int d = 0; d <= 3500; d += 25) { o.append(old.levelAt(cx, cz - d)).append(','); n.append(nm.levelAt(cx, cz - d)).append(','); }
        o.setLength(o.length() - 1); n.setLength(n.length() - 1);
        System.out.println("{\"step\":25,\"now\":[" + o + "],\"proposed\":[" + n + "]}");
    }
}
