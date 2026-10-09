/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.engine.TextHook;

/**
 * The bridge's text-font counters while the grid draws or measures an element: everything the grid
 * draws uses the HUD font (and the game's own, for an element that asked for it).
 */
final class HudText {

    private HudText() {}

    /** Text drawn from here on uses the HUD font (and the game font when {@code gameFont} is 1). */
    static void enter(int gameFont) {
        TextHook.hudDepth++;
        TextHook.gameFontDepth += gameFont;
    }

    static void exit(int gameFont) {
        TextHook.hudDepth--;
        TextHook.gameFontDepth -= gameFont;
    }

    /**
     * A new frame starts from zero: if an exception interrupted a measurement halfway, the counter
     * does not stay stuck (with it stuck, the game's own text would come out in the HUD font).
     */
    static void reset() {
        TextHook.hudDepth = 0;
        TextHook.gameFontDepth = 0;
    }
}
