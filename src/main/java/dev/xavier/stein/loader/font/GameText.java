/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.font;

import dev.xavier.stein.bridge.engine.TextHook;

/**
 * Stein's font in the mods' HUD: what a mod draws through the Loader API (armor, effects,
 * statistics, the Tab list). It has its own option, which starts enabled; when disabled, the HUD
 * goes back to Minecraft's font.
 *
 * <p><b>The game's own text never changes</b>: chat, scoreboard, boss bar, names, menus and
 * everything else Minecraft draws stay in its font. Stein's font is only for what belongs to Stein.
 *
 * <p>What swaps the drawing is the bridge hook ({@link TextHook}), and only while an API drawing is
 * in progress ({@link TextHook#hudDepth}): this file says, for each character, whether Stein's font
 * covers it and which glyph it is. Symbols and ideograms that JetBrains Mono lacks keep coming out
 * in the game's font, within the same text.
 */
public final class GameText implements TextHook.Source {

    /**
     * Stein's font in the mods' HUD (armor, effects, statistics, whatever a mod draws through the
     * API).
     */
    public static volatile boolean hud = true;

    private GameText() {}

    /**
     * Registers the font with the bridge (once, when the Loader starts). Without the font file,
     * nothing changes.
     */
    public static void install() {
        if (SteinFont.ready(SteinFont.Face.HUD)) {
            TextHook.source = new GameText();
        } else {
            System.out.println("[loader] Stein font unavailable: the HUD uses the Minecraft font");
        }
    }

    @Override
    public int advance(char c) {
        if (TextHook.hudDepth <= 0 || TextHook.gameFontDepth > 0 || !hud) {
            return NONE; // game's own text, an element using the game's font, or the option
            // disabled
        }
        if (c == ' ') {
            return SteinFont.GAME_ADVANCE;
        }
        return SteinFont.gameCovers(c) ? SteinFont.GAME_ADVANCE : NONE;
    }

    @Override
    public boolean glyph(char c, float[] out) {
        return c != ' ' && SteinFont.gameGlyph(c, out);
    }

    /**
     * The font option and the generation of the game's glyphs (interface scale, rebuilt atlas):
     * TextHook.Source#generation (no @Override: the Loader compiles against the bridge from the
     * game jar, which may predate the method).
     */
    public int generation() {
        return SteinFont.gameGeneration() << 1 | (hud ? 1 : 0);
    }
}
