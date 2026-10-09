/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

/**
 * The colors of the Stein design system (ARGB), the same ones used by Stein Client and the Loader
 * screens: a mod that draws its own screen or its own HUD element uses these and matches the rest.
 * The mod's page in the Panel already comes out with them on its own.
 */
@Experimental(since = "0.1.18")
public final class Theme {

    /** The field: near-black violet (the background of cards and panels). */
    public static final int BACKGROUND = 0xFF0A0614;

    /** The ink: the main text. */
    public static final int INK = 0xFFE8E1F5;

    /** Secondary and dimmed text. */
    public static final int MUTED = 0x8CE8E1F5;

    /** Faint text: hints and placeholders. */
    public static final int FAINT = 0x59E8E1F5;

    /** Gold-violet: numbers, versions, marks. */
    public static final int GOLD = 0xFFB89CD9;

    /** Electric violet: on, selected, the main action. */
    public static final int ACCENT = 0xFF7C4DFF;

    /** The very faint accent: the background of what is selected or under the cursor. */
    public static final int ACCENT_SOFT = 0x247C4DFF;

    /** The rules (one-pixel borders and dividers). */
    public static final int RULE = 0x1AE8E1F5;

    /** The stronger rule: dividers that must stand out. */
    public static final int RULE_STRONG = 0x38E8E1F5;

    /** Error. */
    public static final int DANGER = 0xFFE0708A;

    /** Warning. */
    public static final int WARN = 0xFFE0B870;

    /** Success. */
    public static final int OK = 0xFF9BD4A8;

    private Theme() {}
}
