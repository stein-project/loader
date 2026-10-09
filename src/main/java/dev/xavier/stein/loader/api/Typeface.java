/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

/**
 * The Stein design system fonts, the same ones used by the Stein Client and the Loader screens, so
 * a mod can draw in the same style ({@link Render#text(String, float, float, int, Typeface,
 * float)}).
 *
 * <ul>
 *   <li>{@link #TITLE}: titles (Cinzel);
 *   <li>{@link #TEXT}: names, descriptions and running text (EB Garamond); {@link #ITALIC}, the
 *       same in italics;
 *   <li>{@link #MONO}: numbers, versions, ids and upper-case labels (JetBrains Mono).
 * </ul>
 */
@Experimental(since = "0.1.18")
public enum Typeface {
    TITLE,
    TEXT,
    ITALIC,
    MONO
}
