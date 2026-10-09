/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.gui;

/**
 * A hover animation: a value that rises towards 1 while the mouse is over the control and falls
 * back to 0 when it leaves (in 140 ms), to be passed through {@link Ui#ease}.
 */
final class HoverFade {

    private float hover;
    private long last;

    /** Advances the animation to now; {@code over} = the mouse is over the control. */
    float update(boolean over) {
        long t = Ui.now();
        float step = last == 0L ? 0.0F : Math.min(100L, t - last) / 140.0F;
        last = t;
        hover = over ? Math.min(1.0F, hover + step) : Math.max(0.0F, hover - step);
        return hover;
    }

    /** The current value (0 to 1), not eased. */
    float value() {
        return hover;
    }
}
