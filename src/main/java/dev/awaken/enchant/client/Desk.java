package dev.awaken.enchant.client;

import dev.awaken.enchant.EnchantRules;
import dev.awaken.enchant.Inscriptions;
import dev.awaken.enchant.Known;
import dev.awaken.enchant.Pages;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Choosing at the desk, in the world (SPEC §45.15; Euan, 2026-11-11: "let's not make it a menu for enchanting. It
 * should stay 3D inside of the actual world"). The pages of what you know that fits your piece rise out of the book and
 * hang in a row over the desk's near edge, each the real page with its own sign. Pointing at one lifts it and burns its
 * glyph faintly on the piece; a click takes it down onto the piece and the trace begins. What can't go on lies flat on
 * the slate, dim. What is already on the piece shows as glyphs let into the slate beside it, one socket for each
 * enchantment the piece has room for; a press held on one scrapes it off. Nothing here is a window: the screen only
 * frees the pointer and draws one small label by it.
 *
 * <p>Everything is in the table's own space (u to the writer's right, v away from them, from the block's middle), so the
 * table's renderer can draw it and this class can say where on screen it is.
 */
final class Desk {
    private Desk() {}

    /** One thing you know that fits the piece: how deep it can go here, what that costs, or why it can't go on. */
    record Offer(Inscriptions.Entry e, Holder<Enchantment> h, int now, int deepest, int anywhere, int to, int[] price,
                 String why, boolean memory, boolean afford) {
        boolean ok() {
            return why.isEmpty();
        }
    }

    /** One enchantment on the piece: its registry number (what a scrape names), its level and its entry (null: not one of ours). */
    record On(int id, int level, Inscriptions.@org.jspecify.annotations.Nullable Entry e, String name) {}

    static final int MAX = 16;
    static final List<Offer> OFFERS = new ArrayList<>();
    static final List<On> ON = new ArrayList<>();
    static int room;
    /** The level asked for each enchantment (its number), where the wheel has changed it. */
    static final Map<Integer, Integer> TARGETS = new HashMap<>();

    // where each page is this frame, in the table's space: u, height over the block's floor, v; its size, its lean and its light
    static final float[] PU = new float[MAX], PY = new float[MAX], PV = new float[MAX], PS = new float[MAX], LEAN = new float[MAX], LIT = new float[MAX];
    // and each socket (the piece has at most MAX): u, v
    static final float[] SU = new float[MAX], SV = new float[MAX];
    // the same on screen (GUI pixels): pages, then sockets; a radius to point within
    private static final float[] GX = new float[MAX * 2], GY = new float[MAX * 2], GR = new float[MAX * 2];

    /** The page and the socket under the pointer (-1 none), and since when. */
    static int hover = -1, socket = -1, hoverSince;
    /** The page on its way down to the piece (-1 none), and when it left the row. */
    static int picked = -1, pickedAt = -1000;
    /** A tour's pointer on a socket (-1 none): {@code /awaken enchant pilot socket <n>}. */
    static int pilotSocket = -1;
    /** The socket a press is held on (-1 none), and since when. */
    static int held = -1, heldAt;

    /** A page's size, the row's height over the slate, how far toward the writer it hangs, and a socket's half size. */
    static final float PAGE = 0.15F, ROW_Y = 0.13F, ROW_V = -0.43F, SOCKET = 0.055F;
    /**
     * Where the sockets run: down the slate's left side from the back, clear of the piece and of the writer's hand.
     * (They were a row behind the piece: a longsword's point lay over the one it had, and nobody could have found it.)
     */
    static final float SOCKET_U = -0.34F, SOCKET_V = 0.25F, SOCKET_STEP = 0.125F;
    /** Ticks a page takes from the book to its place, from its place to the piece, and a held press to scrape. */
    static final int RISE = 10, FALL = 8, HOLD = 16;

    private static final ItemStack[] STACKS = new ItemStack[64];

    static void reset() {
        OFFERS.clear();
        ON.clear();
        TARGETS.clear();
        hover = socket = picked = held = -1;
        room = 0;
    }

    /** The page item for an enchantment's number (made once). */
    static ItemStack stack(Offer o) {
        int no = o.e.no();
        if (no < 0 || no >= STACKS.length) return ItemStack.EMPTY;
        if (STACKS[no] == null) STACKS[no] = Pages.page(o.h, "");
        return STACKS[no];
    }

    /** What you know that fits the piece in hand, what is on it and its room (the server checks it all again). */
    static void build(LocalPlayer p, ItemStack piece) {
        OFFERS.clear();
        ON.clear();
        var reg = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Known k = Known.of(p);
        int lapis = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.LAPIS_LAZULI)) lapis += s.getCount();
        }
        List<Offer> blocked = new ArrayList<>();
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            if (!k.knows(e.id())) continue;
            var ref = reg.get(e.key());
            if (ref.isEmpty() || !EnchantRules.fits(piece, ref.get())) continue;
            Holder<Enchantment> h = ref.get();
            int now = EnchantRules.level(piece, h), top = Inscriptions.top(h);
            int deepest = EnchantRules.deepest(piece, h, e, InscribeScene.study), anywhere = EnchantRules.deepestAnywhere(piece, h, e);
            int lo = Math.min(top, now + 1), hi = Math.max(lo, Math.min(top, deepest));
            int to = Mth.clamp(TARGETS.getOrDefault(e.no(), hi), lo, hi);
            String why = EnchantRules.why(piece, h, to, InscribeScene.study);
            int[] price = EnchantRules.price(e, now, to);
            boolean afford = p.hasInfiniteMaterials() || (p.experienceLevel >= price[0] && lapis >= price[1]);
            Offer o = new Offer(e, h, now, deepest, anywhere, to, price, why == null ? "" : why, k.inMemory(e.id()), afford);
            (why == null ? OFFERS : blocked).add(o);
        }
        OFFERS.addAll(blocked);
        while (OFFERS.size() > MAX) OFFERS.removeLast();
        for (Object2IntMap.Entry<Holder<Enchantment>> x : piece.getEnchantments().entrySet()) {
            if (ON.size() >= MAX) break;
            ON.add(new On(reg.getId(x.getKey().value()), x.getIntValue(), Inscriptions.of(x.getKey()), x.getKey().value().description().getString()));
        }
        room = Math.min(MAX, Math.max(EnchantRules.room(piece), ON.size()));
        if (hover >= OFFERS.size()) hover = -1;
        if (socket >= room) socket = -1;
    }

    /** Whether the pages are out: while you choose, and for the moment after a choice while the chosen one comes down. */
    static boolean shown(float pt) {
        if (!InscribeScene.live) return false;
        if (InscribeScene.phase == InscribeScene.BOOK) return true;
        return (InscribeScene.phase == InscribeScene.TRACE || InscribeScene.phase == InscribeScene.MEMORY)
                && picked >= 0 && InscribeScene.now(pt) - pickedAt < FALL + 4;
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }

    /**
     * Lays the row out for this frame: each page rises out of the book to its place over the near edge, the one pointed
     * at lifts and grows a little, the chosen one comes down onto the piece while the rest sink, and what can't go on
     * lies flat on the slate. The sockets run down the slate's left side.
     */
    static void layout(float pt) {
        int n = OFFERS.size();
        float t = InscribeScene.now(pt);
        float since = t - Math.max(InscribeScene.enteredBookAt, InscribeScene.openedAt());
        float step = Math.min(0.17F, 0.84F / Math.max(1, n - 1));
        boolean leaving = InscribeScene.phase != InscribeScene.BOOK;
        float gone = leaving ? ease(Mth.clamp((t - pickedAt) / FALL, 0F, 1F)) : 0F;
        for (int i = 0; i < n; i++) {
            Offer o = OFFERS.get(i);
            float at = i - (n - 1) / 2F;
            float u = at * step, v = ROW_V - 0.006F * at * at, y = InscribingTableRenderer.SLATE + ROW_Y, size = PAGE, lean = 46F, lit = 1F;
            if (!o.ok()) {
                // it can't go on: lying on the slate, dim
                y = InscribingTableRenderer.SLATE + 0.012F + 0.0015F * i;
                v = ROW_V + 0.07F;
                lean = 90F;
                lit = 0.35F;
                size = PAGE * 0.9F;
            } else if (i == hover && !leaving) {
                float k = ease(Mth.clamp((t - hoverSince) / 4F, 0F, 1F));
                y += 0.035F * k;
                size *= 1F + 0.22F * k;
                lean -= 8F * k;
            }
            if (i == picked && leaving) {
                // down onto the piece: flat, shrinking into the mark
                u = Mth.lerp(gone, u, 0F);
                v = Mth.lerp(gone, v, -0.08F);
                y = Mth.lerp(gone, y, (float) (InscribingTableRenderer.SLATE + InscribeScene.lift + 0.01)) + 0.06F * Mth.sin(gone * Mth.PI);
                lean = Mth.lerp(gone, lean, 90F);
                size *= 1F - 0.75F * gone;
            } else if (leaving) {
                y -= 0.1F * gone;
                size *= 1F - gone;
            }
            // out of the book at the back left, one after another
            float rise = ease(Mth.clamp((since - i * 1.2F) / RISE, 0F, 1F));
            if (rise < 1F) {
                u = Mth.lerp(rise, -0.175F, u);
                v = Mth.lerp(rise, 0.33F, v);
                y = Mth.lerp(rise, 17.6F / 16F, y) + 0.09F * Mth.sin(rise * Mth.PI);
                size *= 0.3F + 0.7F * rise;
            }
            PU[i] = u;
            PY[i] = y;
            PV[i] = v;
            PS[i] = size;
            LEAN[i] = lean;
            LIT[i] = lit;
        }
        float gap = Math.min(SOCKET_STEP, 0.5F / Math.max(1, room - 1));
        for (int j = 0; j < room; j++) {
            SU[j] = SOCKET_U;
            SV[j] = SOCKET_V - j * gap;
        }
    }

    /** A point of the table's space in the world. */
    static double wx(float u, float v) {
        return InscribeScene.pos == null ? 0 : InscribeScene.pos.getX() + 0.5 + InscribeScene.rx * u - InscribeScene.fx * v;
    }

    static double wz(float u, float v) {
        return InscribeScene.pos == null ? 0 : InscribeScene.pos.getZ() + 0.5 + InscribeScene.rz * u - InscribeScene.fz * v;
    }

    /**
     * Where the pointer is among the pages and sockets: each one's place on screen from the camera, the nearest within
     * its own size taken. {@code forced} (the autopilot's hand) names a page by its enchantment's number instead.
     */
    static void point(double mx, double my, int guiW, int guiH, float pt, int forced, int age) {
        int was = hover, wasSocket = socket;
        hover = socket = -1;
        if (InscribeScene.phase != InscribeScene.BOOK || InscribeScene.pos == null || guiW <= 0 || guiH <= 0) return;
        layout(pt);
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        var fw = cam.forwardVector();
        var up = cam.upVector();
        var left = cam.leftVector();
        double tan = Math.tan(Math.toRadians(cam.getFov()) / 2), aspect = guiW / (double) guiH;
        int n = OFFERS.size();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n + room; i++) {
            boolean page = i < n;
            int j = i - n;
            double x = page ? wx(PU[i], PV[i]) : wx(SU[j], SV[j]), z = page ? wz(PU[i], PV[i]) : wz(SU[j], SV[j]);
            double y = InscribeScene.pos.getY() + (page ? PY[i] : InscribingTableRenderer.SLATE + 0.004F);
            double dx = x - o.x, dy = y - o.y, dz = z - o.z;
            double depth = dx * fw.x() + dy * fw.y() + dz * fw.z();
            int slot = page ? i : MAX + j;
            if (depth < 0.05) {
                GR[slot] = 0;
                continue;
            }
            double sx = -(dx * left.x() + dy * left.y() + dz * left.z()) / (depth * tan * aspect);
            double sy = (dx * up.x() + dy * up.y() + dz * up.z()) / (depth * tan);
            GX[slot] = (float) ((sx + 1) / 2 * guiW);
            GY[slot] = (float) ((1 - sy) / 2 * guiH);
            GR[slot] = (float) ((page ? PS[i] * 0.55 : SOCKET * 1.5) / (depth * tan) * guiH / 2);
            if (forced > 0) continue;
            double d = Math.hypot(mx - GX[slot], my - GY[slot]);
            if (d <= GR[slot] && d < best) {
                best = d;
                hover = page ? i : -1;
                socket = page ? -1 : j;
            }
        }
        if (forced > 0) for (int i = 0; i < n; i++) if (OFFERS.get(i).e.no() == forced) hover = i;
        if (pilotSocket >= 0 && pilotSocket < room) {
            hover = -1;
            socket = pilotSocket;
        }
        if (hover != was || socket != wasSocket) hoverSince = InscribeScene.ticks;
        if (socket != held) held = -1;
    }

    /** Where a page or a socket last was on screen, for the label beside it. */
    static float screenX(boolean page, int i) {
        return GX[page ? i : MAX + i];
    }

    static float screenY(boolean page, int i) {
        return GY[page ? i : MAX + i];
    }

    static float screenR(boolean page, int i) {
        return GR[page ? i : MAX + i];
    }

    /** The enchantment whose glyph shows faintly on the piece: the page pointed at, if it can go on. */
    static int hoverNo() {
        if (hover < 0 || hover >= OFFERS.size()) return 0;
        Offer o = OFFERS.get(hover);
        return o.ok() ? o.e.no() : 0;
    }

    /** How far a held press has got toward scraping (0..1). */
    static float holding(float pt, int age) {
        return held < 0 ? 0F : Mth.clamp((age + pt - heldAt) / HOLD, 0F, 1F);
    }
}
