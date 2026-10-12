/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;

/**
 * The drag of an {@link Option.Slider} track on a screen of option rows (the Panel, the grid
 * editor): pressing on the track starts it, the mouse moves the value, releasing ends it. While it
 * lasts, the slider is highlighted through {@link Widgets#sliding}.
 */
public final class SliderDrag {

    /** The slider being dragged, or null. */
    private Option.Slider sliding;

    /** Where the dragged track starts on screen. */
    private int trackX;

    /**
     * A press on an option row (left edge {@code rowX}, width {@code rowW}): if it is the left
     * button on a slider's track, the value goes where the mouse is, the drag starts and true is
     * returned.
     */
    public boolean press(Option o, int button, int mx, int rowX, int rowW) {
        int track = rowX + rowW - Widgets.TRACK_W;
        if (o instanceof Option.Slider s && button == 0 && mx >= track - 4) {
            sliding = s;
            Widgets.sliding = s;
            trackX = track;
            s.setFraction((mx - track) / (float) Widgets.TRACK_W);
            return true;
        }
        return false;
    }

    /** The mouse moved: if a drag is on, the value follows it and true is returned. */
    public boolean drag(int mx) {
        if (sliding == null) {
            return false;
        }
        sliding.setFraction((mx - trackX) / (float) Widgets.TRACK_W);
        return true;
    }

    /** The button was released: ends the drag; true if there was one. */
    public boolean release() {
        boolean was = sliding != null;
        sliding = null;
        Widgets.sliding = null;
        return was;
    }
}
