package dev.awaken.hearth.client;

import dev.awaken.signal.client.Ui;

/**
 * Status screen section: the room you stand in at home, at the top of the K page while you're in one (holding the band
 * key at home shows how the Hearth reads the room round you). The panel itself is {@link RoomPanel}.
 */
final class RoomSection extends dev.awaken.signal.client.UiSection {
    @Override
    public String title() {
        return "THIS ROOM";
    }

    @Override
    public int order() {
        return -28; // right under NOW (-30), above the Atlas (-25): the room round you before the land round you
    }

    @Override
    public boolean visible() {
        return RoomPanel.visible();
    }

    @Override
    protected void build(Ui ui) {
        RoomPanel.build(ui);
    }
}
