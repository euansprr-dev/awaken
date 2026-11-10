package dev.awaken.holo.client;

import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.progression.client.SectionHost;
import dev.awaken.progression.client.StatusSections.Tab;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

/**
 * A band page whose module hasn't moved to the stage yet (SPEC §57): its old sections across the whole stage
 * ({@link SectionHost}), so a page that scrolled for five screens mostly fits on one. A page loses this the day its module
 * gives the band a place of its own.
 */
final class LegacyPlace implements BandPlaces.Place {
    private final Tab tab;
    private final SectionHost host = new SectionHost();

    LegacyPlace(Tab tab) {
        this.tab = tab;
    }

    @Override
    public void opened(String... focus) {
        host.focus(focus);
    }

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        host.draw(s, hot, tab, sec -> true, BandPlaces.X0, BandPlaces.Y0, BandPlaces.X1, BandPlaces.Y1, mx, my, HoloStatusScreen.empty(tab),
                HoloConfig.reducedMotion);
    }

    @Override
    public boolean key(KeyEvent event) {
        return host.key(event, tab, sec -> true);
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        return host.scroll(amount);
    }

    @Override
    public void hints(List<Kit.Hint> out) {
        if (host.more()) out.add(Kit.Hint.of("More", "Scroll"));
    }
}
