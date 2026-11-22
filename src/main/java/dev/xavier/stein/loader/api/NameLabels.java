/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * The look of the name labels above heads (players and named mobs): the background, the text, the
 * size and the height, and whether the player's own label shows in third person.
 *
 * <pre>
 * NameLabels.setStyle(NameLabels.Style.VANILLA
 *         .withBackground(true, 0x40000000)
 *         .withCornerRadius(3)
 *         .withShadow(true));
 * </pre>
 *
 * <p><b>Only the picture changes.</b> Who gets a label, and from how far, is still the game's rule:
 * sneaking players, invisible ones, the team's name tag setting, F1, riding, 64 blocks (32
 * sneaking). The own label follows the same rules a teammate's would, and never shows in first
 * person or on the inventory's player.
 *
 * <p>The labels are drawn by the Stein renderer, on Vulkan and OpenGL alike (and on the
 * fixed-function fallback), with the same caching as the game's look: setting a style costs nothing
 * per frame beyond the extra shapes. Labels of a renderer that draws the name its own way (some
 * mods' entities) keep their look.
 *
 * <p>Global: there is one style in the game, not one per mod, and the last call wins. Every option
 * starts at the game's look; a mod that offers them should start there too. Call from the game
 * thread (a tick, a screen, an option's callback), not while the world draws.
 */
@Experimental(since = "0.1.36")
public final class NameLabels {

    private NameLabels() {}

    /**
     * A label look. Immutable: each {@code with} method returns a copy with one thing changed,
     * starting from {@link #VANILLA}.
     */
    @Experimental(since = "0.1.36")
    public static final class Style {

        /** The game's background: black at 25% (alpha 63). */
        public static final int VANILLA_BACKGROUND = 0x3F000000;

        /** The game's look. */
        public static final Style VANILLA =
                new Style(true, VANILLA_BACKGROUND, 0, 0, 0, false, 0xFFFFFF, false, 1.0F, 0.0F);

        /** The largest corner radius, in label pixels (the game's label is 9 pixels high). */
        public static final int MAX_CORNER_RADIUS = 6;

        /** The largest padding, in label pixels, on each side. */
        public static final int MAX_PADDING = 8;

        /** A background behind the text (false: the text alone). */
        public final boolean background;

        /** The background's color, ARGB (alpha in the top byte). */
        public final int backgroundColor;

        /** Label pixels added left and right of the game's background. */
        public final int paddingX;

        /** Label pixels added above and below the game's background. */
        public final int paddingY;

        /** The background's corner radius in label pixels; 0 = square, as the game. */
        public final int cornerRadius;

        /** The game's drop shadow under the text (not on a sneaking player's faint label). */
        public final boolean textShadow;

        /** The text's color, RGB, where no formatting code gives one (the game's is white). */
        public final int textColor;

        /** Leaves the formatting codes out: the whole name in {@link #textColor}, ranks ignored. */
        public final boolean plainText;

        /** The label's size; 1 = the game's. Between 0.1 and 4. */
        public final float scale;

        /** How far above the game's height, in blocks (negative goes down). Between -4 and 4. */
        public final float heightOffset;

        private Style(
                boolean background,
                int backgroundColor,
                int paddingX,
                int paddingY,
                int cornerRadius,
                boolean textShadow,
                int textColor,
                boolean plainText,
                float scale,
                float heightOffset) {
            this.background = background;
            this.backgroundColor = backgroundColor;
            this.paddingX = Math.max(0, Math.min(MAX_PADDING, paddingX));
            this.paddingY = Math.max(0, Math.min(MAX_PADDING, paddingY));
            this.cornerRadius = Math.max(0, Math.min(MAX_CORNER_RADIUS, cornerRadius));
            this.textShadow = textShadow;
            this.textColor = textColor & 0xFFFFFF;
            this.plainText = plainText;
            this.scale = Float.isFinite(scale) ? Math.max(0.1F, Math.min(4.0F, scale)) : 1.0F;
            this.heightOffset =
                    Float.isFinite(heightOffset)
                            ? Math.max(-4.0F, Math.min(4.0F, heightOffset))
                            : 0.0F;
        }

        /**
         * The background.
         *
         * @param on false draws the text alone
         * @param argb its color, alpha in the top byte ({@link #VANILLA_BACKGROUND} is the game's)
         * @return a copy with the background changed
         */
        public Style withBackground(boolean on, int argb) {
            return new Style(
                    on,
                    argb,
                    paddingX,
                    paddingY,
                    cornerRadius,
                    textShadow,
                    textColor,
                    plainText,
                    scale,
                    heightOffset);
        }

        /**
         * Room around the text, on top of the game's (one pixel at the sides and the top).
         *
         * @param x label pixels left and right, 0 to {@link #MAX_PADDING}
         * @param y label pixels above and below, 0 to {@link #MAX_PADDING}
         * @return a copy with the padding changed
         */
        public Style withPadding(int x, int y) {
            return new Style(
                    background,
                    backgroundColor,
                    x,
                    y,
                    cornerRadius,
                    textShadow,
                    textColor,
                    plainText,
                    scale,
                    heightOffset);
        }

        /**
         * Rounded corners.
         *
         * @param radius label pixels, 0 (square) to {@link #MAX_CORNER_RADIUS}; never more than
         *     half the background's height
         * @return a copy with the corners changed
         */
        public Style withCornerRadius(int radius) {
            return new Style(
                    background,
                    backgroundColor,
                    paddingX,
                    paddingY,
                    radius,
                    textShadow,
                    textColor,
                    plainText,
                    scale,
                    heightOffset);
        }

        /**
         * The text's drop shadow, as the game draws it on the HUD.
         *
         * @param on true for the shadow
         * @return a copy with the shadow changed
         */
        public Style withShadow(boolean on) {
            return new Style(
                    background,
                    backgroundColor,
                    paddingX,
                    paddingY,
                    cornerRadius,
                    on,
                    textColor,
                    plainText,
                    scale,
                    heightOffset);
        }

        /**
         * The text's color.
         *
         * @param rgb the color where no formatting code gives one (0xFFFFFF is the game's)
         * @param plain true leaves the formatting codes out, so the whole name is {@code rgb}
         * @return a copy with the text color changed
         */
        public Style withTextColor(int rgb, boolean plain) {
            return new Style(
                    background,
                    backgroundColor,
                    paddingX,
                    paddingY,
                    cornerRadius,
                    textShadow,
                    rgb,
                    plain,
                    scale,
                    heightOffset);
        }

        /**
         * The label's size.
         *
         * @param scale 1 is the game's, 0.1 to 4
         * @return a copy with the size changed
         */
        public Style withScale(float scale) {
            return new Style(
                    background,
                    backgroundColor,
                    paddingX,
                    paddingY,
                    cornerRadius,
                    textShadow,
                    textColor,
                    plainText,
                    scale,
                    heightOffset);
        }

        /**
         * The label's height above the head.
         *
         * @param blocks added to the game's height, -4 to 4 (negative goes down)
         * @return a copy with the height changed
         */
        public Style withHeightOffset(float blocks) {
            return new Style(
                    background,
                    backgroundColor,
                    paddingX,
                    paddingY,
                    cornerRadius,
                    textShadow,
                    textColor,
                    plainText,
                    scale,
                    blocks);
        }
    }

    /**
     * Sets the look of every name label.
     *
     * @param style the look; null goes back to {@link Style#VANILLA}
     */
    public static void setStyle(Style style) {
        Style s = style != null ? style : Style.VANILLA;
        Host.INSTANCE.setNameLabelStyle(
                s.background,
                s.backgroundColor,
                s.paddingX,
                s.paddingY,
                s.cornerRadius,
                s.textShadow,
                s.textColor,
                s.plainText,
                s.scale,
                s.heightOffset);
    }

    /**
     * The player's own label in third person (F5), which the game never draws. Only in the world:
     * not in first person, not on the inventory's player.
     *
     * @param on true to show it
     */
    public static void setShowOwn(boolean on) {
        Host.INSTANCE.setShowOwnNameLabel(on);
    }
}
