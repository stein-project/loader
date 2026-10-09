/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

/**
 * How a drawing mixes with what is already on the screen ({@link Render#blend}, {@link
 * WorldRender#blend}).
 */
@Experimental(since = "0.1.23")
public enum Blend {

    /** The usual: the color's alpha decides how much of it covers what is behind. */
    NORMAL,

    /** Adds light: glows, beams, highlights. Black adds nothing. */
    ADDITIVE,

    /** Darkens: what is behind is multiplied by the color. White changes nothing. */
    MULTIPLY,

    /** Lightens softly, the opposite of {@link #MULTIPLY}. Black changes nothing. */
    SCREEN
}
