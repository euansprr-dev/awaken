package dev.awaken.signal.client;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.CoreModule;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.signal.SignalWindow;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Draws Signal windows. One window at a time, top centre, typing in. In combat they collapse to toasts in the top-right
 * corner; windows with an action (or high priority) wait for the fight to end.
 */
public final class SignalHud {
    private SignalHud() {}

    private static final int OPEN_TICKS = 5;
    private static final int CLOSE_TICKS = 6;

    private static final class Live {
        final SignalWindow w;
        int age;
        int closingAt = -1;
        Live(SignalWindow w) { this.w = w; }
    }

    private record Toast(Component text, int colour, long until) {}

    private static final PriorityQueue<SignalWindow> QUEUE = new PriorityQueue<>(
            Comparator.comparingInt((SignalWindow w) -> -w.priority()).thenComparingInt(SignalWindow::id));
    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static Live current;
    private static long clientTicks;
    private static long lastHurtTick = -1000;
    private static boolean hostileNear;

    public static void show(SignalWindow w) {
        Minecraft mc = Minecraft.getInstance();
        boolean urgent = w.kind() == SignalWindow.Kind.WARNING || w.priority() >= 9;
        if (inCombat() && !urgent) {
            toast(w.title(), edgeFor(w.kind()));
            if (w.action().isEmpty() && w.priority() < 5) return; // toast was enough
        }
        QUEUE.add(w);
    }

    public static void toast(Component text, int colour) {
        TOASTS.addLast(new Toast(text, colour, clientTicks + 70));
        while (TOASTS.size() > 4) TOASTS.removeFirst();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CoreModule.SIGNAL_CHIME.get(), 1.0f, 0.35f));
    }

    public static boolean inCombat() {
        return clientTicks - lastHurtTick < 100 || hostileNear;
    }

    static void clear() {
        QUEUE.clear();
        TOASTS.clear();
        current = null;
    }

    static void tick() {
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.hurtTime > 0) lastHurtTick = clientTicks;
        if (clientTicks % 10 == 0) {
            hostileNear = !mc.player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    mc.player.getBoundingBox().inflate(8), m -> m instanceof Enemy && m.isAlive()).isEmpty();
        }
        while (!TOASTS.isEmpty() && TOASTS.peekFirst().until() < clientTicks) TOASTS.removeFirst();

        if (current != null) {
            current.age++;
            boolean hasAction = current.w.action().isPresent();
            if (current.closingAt < 0) {
                if (hasAction && AwakenKeys.ACCEPT.consumeClick()) answer(true);
                else if (AwakenKeys.DISMISS.consumeClick()) {
                    if (hasAction) answer(false); else current.closingAt = current.age;
                } else if (current.age >= current.w.ttlTicks()) {
                    current.closingAt = current.age;
                }
            }
            if (current.closingAt >= 0 && current.age - current.closingAt >= CLOSE_TICKS) current = null;
        } else {
            // swallow stray presses so they don't fire later
            while (AwakenKeys.ACCEPT.consumeClick()) {}
            while (AwakenKeys.DISMISS.consumeClick()) {}
        }

        if (current == null && !QUEUE.isEmpty()) {
            SignalWindow next = QUEUE.peek();
            boolean urgent = next.kind() == SignalWindow.Kind.WARNING || next.priority() >= 9;
            if (!inCombat() || urgent) {
                current = new Live(QUEUE.poll());
                var sound = next.kind() == SignalWindow.Kind.WARNING ? CoreModule.SIGNAL_WARNING.get() : CoreModule.SIGNAL_OPEN.get();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 0.6f));
            }
        }
    }

    private static void answer(boolean accepted) {
        current.w.action().ifPresent(a -> ClientPacketDistributor.sendToServer(new SignalPayloads.Answer(a, accepted)));
        current.closingAt = current.age;
    }

    static int edgeFor(SignalWindow.Kind k) {
        return switch (k) {
            case WARNING -> Glass.WARN;
            case REASSESSMENT, DAYS_END -> Glass.LANTERN;
            case TRUTH -> Glass.AMBER;
            case REVELATION -> Glass.VIOLET;
            default -> Glass.EDGE;
        };
    }

    static String headerFor(SignalWindow.Kind k) {
        return switch (k) {
            case NOTICE -> "◇ NOTICE";
            case GATE -> "◇ GATE DETECTED";
            case DIRECTIVE -> "◇ DIRECTIVE";
            case TRUTH -> "◇ TRUTH";
            case WARNING -> "◇ WARNING";
            case REASSESSMENT -> "◇ REASSESSMENT";
            case REVELATION -> "◇ REVELATION";
            case DAYS_END -> "◇ DAY'S END";
        };
    }

    // ---- rendering --------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        renderToasts(g, mc.font);
        if (current != null) renderWindow(g, mc.font, current, pt);
    }

    private static void renderToasts(GuiGraphicsExtractor g, Font font) {
        int y = 8;
        int right = g.guiWidth() - 8;
        for (Toast t : TOASTS) {
            float life = (t.until() - clientTicks) / 70f;
            float alpha = Math.min(1f, life * 6f);
            int w = Math.min(200, font.width(t.text()) + 16);
            Glass.panel(g, right - w, y, w, 16, t.colour(), alpha);
            g.text(font, t.text(), right - w + 8, y + 4, Glass.a(Glass.TITLE, alpha), false);
            y += 21;
        }
    }

    private static void renderWindow(GuiGraphicsExtractor g, Font font, Live live, float pt) {
        SignalWindow w = live.w;
        float age = live.age + pt;
        float open = Math.min(1f, age / OPEN_TICKS);
        float alpha = open;
        if (live.closingAt >= 0) alpha = Math.max(0f, 1f - (age - live.closingAt) / CLOSE_TICKS);
        if (alpha <= 0.01f) return;
        int edge = edgeFor(w.kind());
        int width = Math.min(260, g.guiWidth() - 40);
        int inner = width - 20;

        double speed = AwakenConfig.Client.SIGNAL_TYPE_SPEED.get();
        String title = w.title().getString();
        int typed = (int) Math.min(title.length(), Math.floor(age * speed));
        double afterTitle = Math.max(0, age - title.length() / speed);

        List<FormattedCharSequence> titleLines = font.split(Component.literal(title), inner);
        List<FormattedCharSequence> bodyLines = w.body().map(b -> font.split(b, inner)).orElse(List.of());
        int height = 10 + 10 + 4 + titleLines.size() * 11 + (bodyLines.isEmpty() ? 0 : 4 + bodyLines.size() * 10)
                + (w.rows().isEmpty() ? 0 : 6 + w.rows().size() * 12) + 6 + (w.footer().isPresent() || w.action().isPresent() ? 14 : 0);

        int cx = g.guiWidth() / 2;
        int y = Math.max(10, (int) (g.guiHeight() * 0.12f));
        int drawW = Math.max(12, Math.round(width * (0.25f + 0.75f * easeOut(open))));
        int x = cx - drawW / 2;
        Glass.panel(g, x, y, drawW, height, edge, alpha);
        if (open < 1f) return; // unfold first, then write
        x = cx - width / 2;
        g.enableScissor(x, y, x + width, y + height);

        int ty = y + 7;
        g.text(font, headerFor(w.kind()), x + 10, ty, Glass.a(edge, alpha), false);
        ty += 11;
        g.horizontalLine(x + 10, x + width - 10, ty, Glass.a(edge, 0.25f * alpha));
        ty += 5;

        // title types in
        String shown = title.substring(0, typed);
        for (FormattedCharSequence line : font.split(Component.literal(shown), inner)) {
            g.text(font, line, x + 10, ty, Glass.a(Glass.TITLE, alpha), true);
            ty += 11;
        }
        ty += Math.max(0, titleLines.size() - font.split(Component.literal(shown.isEmpty() ? " " : shown), inner).size()) * 11;

        float reveal = (float) Math.min(1, afterTitle / 4.0);
        if (!bodyLines.isEmpty()) {
            ty += 4;
            for (FormattedCharSequence line : bodyLines) {
                g.text(font, line, x + 10, ty, Glass.a(Glass.TEXT, alpha * reveal), false);
                ty += 10;
            }
        }
        if (!w.rows().isEmpty()) {
            ty += 6;
            int i = 0;
            for (SignalWindow.Row row : w.rows()) {
                float r = (float) Math.max(0, Math.min(1, (afterTitle - 2 - i * 1.5) / 3.0));
                g.text(font, row.label(), x + 10, ty, Glass.a(Glass.LABEL, alpha * r), false);
                int vw = font.width(row.value());
                int vc = row.value().getString().equalsIgnoreCase("not true yet") ? Glass.AMBER : Glass.TITLE;
                g.text(font, row.value(), x + width - 10 - vw, ty, Glass.a(vc, alpha * r), false);
                if (i < w.rows().size() - 1) g.horizontalLine(x + 10, x + width - 10, ty + 10, Glass.a(edge, 0.10f * alpha * r));
                ty += 12;
                i++;
            }
        }
        ty += 6;
        if (w.footer().isPresent() || w.action().isPresent()) {
            String foot = w.footer().map(Component::getString).orElse("");
            if (w.action().isPresent()) {
                String keys = "[" + AwakenKeys.ACCEPT.getTranslatedKeyMessage().getString() + "] Accept   ["
                        + AwakenKeys.DISMISS.getTranslatedKeyMessage().getString() + "] Dismiss";
                foot = foot.isEmpty() ? keys : foot + "   " + keys;
            }
            g.text(font, foot, x + 10, ty, Glass.a(edge, alpha * reveal), false);
        }
        g.disableScissor();

        // ttl line along the bottom edge
        if (live.closingAt < 0 && w.ttlTicks() > 0) {
            float left = 1f - Math.min(1f, age / w.ttlTicks());
            g.horizontalLine(x + 1, x + 1 + Math.round((width - 2) * left), y + height - 1, Glass.a(edge, 0.6f * alpha));
        }
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
