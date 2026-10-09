/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;
import dev.xavier.stein.loader.api.spi.RenderBackend;

import java.util.List;

/**
 * 2D drawing for the HUD and screens, without the mod talking to OpenGL: rectangles, shapes, text,
 * images, items, player heads and textures made by the mod (a minimap). Stein's renderer may change
 * underneath (the release ships with OpenGL today): whoever draws through here keeps working,
 * whoever calls GL/GlStateManager/Tessellator directly breaks. For drawing in the world see {@link
 * WorldRender}; for geometry built once and drawn many times, {@link Mesh}.
 *
 * <p>GUI coordinates (those of {@link HudElement#draw} and drawScreen), in floating point: nothing
 * jumps pixel by pixel. Colors in ARGB ({@code 0xAARRGGBB}; alpha 0 draws nothing — use {@code
 * 0xFF000000 | rgb} for opaque). Call from the game thread, during drawing.
 */
@Experimental(since = "0.1.16")
public final class Render {

    private static final int WHITE = 0xFFFFFFFF;

    private Render() {}

    // --- shapes ---

    /**
     * Filled rectangle.
     *
     * @param x left edge
     * @param y top edge
     * @param w width
     * @param h height
     * @param argb color, {@code 0xAARRGGBB}
     */
    public static void rect(float x, float y, float w, float h, int argb) {
        Host.INSTANCE.drawRect(x, y, w, h, argb);
    }

    /** Rounded-corner rectangle (radius r). */
    public static void roundRect(float x, float y, float w, float h, float r, int argb) {
        Host.INSTANCE.drawRoundRect(x, y, w, h, r, argb);
    }

    /**
     * Stein's default background (the same as the HUD grid's elements) from (x, y) to (x + w, y +
     * h), with the requested opacity (0 to 1): so the mod's screen or block gets the same look.
     */
    @Experimental(since = "0.1.18")
    public static void surface(float x, float y, float w, float h, float opacity) {
        Host.INSTANCE.drawSurface(x, y, w, h, opacity);
    }

    /**
     * Runs {@code draw} with the text in Minecraft's own font, even where Stein's font applies (the
     * HUD grid): for the "Font: game's" option of a mod block.
     */
    @Experimental(since = "0.1.18")
    public static void gameFont(Runnable draw) {
        Host.INSTANCE.withGameFont(draw);
    }

    /**
     * Filled circle.
     *
     * @param cx center x
     * @param cy center y
     * @param r radius
     * @param argb color, {@code 0xAARRGGBB}
     */
    public static void circle(float cx, float cy, float r, int argb) {
        roundRect(cx - r, cy - r, r * 2, r * 2, r, argb);
    }

    /** Ring of thickness t. */
    public static void ring(float cx, float cy, float r, float t, int argb) {
        Host.INSTANCE.drawRing(cx, cy, r, t, argb);
    }

    /** Filled triangle (a minimap's arrow, a marker). */
    public static void triangle(
            float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        Host.INSTANCE.drawTriangle(x1, y1, x2, y2, x3, y3, argb);
    }

    /**
     * Filled fan polygon: the first point connects to all the others, in order (xs[i], ys[i],
     * {@code count} points). A circle with as many sides as you want, a four-pointed arrow.
     */
    @Experimental(since = "0.1.16")
    public static void fan(float[] xs, float[] ys, int count, int argb) {
        Host.INSTANCE.drawFan(xs, ys, null, null, count, argb, 0);
    }

    // --- text ---

    /** Text with the game's color codes; returns the width. */
    public static int text(String s, float x, float y, int argb, boolean shadow) {
        return Host.INSTANCE.drawText(s, x, y, argb, shadow);
    }

    /**
     * The width of {@code s} as {@link #text} draws it (color codes do not count).
     *
     * @param s the text; null is 0 wide
     * @return the width in GUI units
     */
    public static int textWidth(String s) {
        return Host.INSTANCE.textWidth(s);
    }

    /**
     * Text in one of the fonts of Stein's design system ({@link Typeface}), with {@code size} as
     * body size in screen units (10 is the text of the Loader screens; 6 to 7 the labels; 12 to 16
     * the titles). It is smoothed and sharp at any interface scale. The game's color codes apply,
     * with the theme's colors. The top-left corner is at (x, y); returns the x where the text
     * ended.
     */
    @Experimental(since = "0.1.18")
    public static float text(String s, float x, float y, int argb, Typeface face, float size) {
        return Host.INSTANCE.drawStyledText(s, x, y, argb, face.ordinal(), size);
    }

    /** The width of a text of {@link #text(String, float, float, int, Typeface, float)}. */
    @Experimental(since = "0.1.18")
    public static float textWidth(String s, Typeface face, float size) {
        return Host.INSTANCE.styledTextWidth(s, face.ordinal(), size);
    }

    /** The height of a line of that font at that size. */
    @Experimental(since = "0.1.18")
    public static float textHeight(Typeface face, float size) {
        return Host.INSTANCE.styledTextHeight(face.ordinal(), size);
    }

    /** The height of a text line (9 in the game). */
    public static int fontHeight() {
        return Host.INSTANCE.fontHeight();
    }

    /**
     * The text broken into lines of up to {@code width} pixels, carrying the colors from one line
     * to the next.
     */
    @Experimental(since = "0.1.16")
    public static List<String> wrap(String text, int width) {
        return Host.INSTANCE.wrapText(text, width);
    }

    /**
     * The beginning of the text that fits in {@code width} pixels (colors count the way the game
     * counts them), without an ellipsis.
     */
    @Experimental(since = "0.1.16")
    public static String trim(String text, int width) {
        return Host.INSTANCE.trimText(text, width);
    }

    /** The screen width at the interface scale (the same as the HUD's and the buttons'). */
    @Experimental(since = "0.1.16")
    public static int guiWidth() {
        return Host.INSTANCE.guiSize(true);
    }

    /** The screen height at the interface scale. */
    @Experimental(since = "0.1.16")
    public static int guiHeight() {
        return Host.INSTANCE.guiSize(false);
    }

    // --- images ---

    /**
     * A piece of a resource texture ("domain:path", e.g. {@code "mymod:textures/gui/icon.png"}) at
     * x, y, w, h, from coordinates u0,v0 to u1,v1 (0 to 1), painted with argb ({@code 0xFFFFFFFF} =
     * its own colors).
     */
    public static void image(
            String texture,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb) {
        Host.INSTANCE.drawImage(texture, x, y, w, h, u0, v0, u1, v1, argb);
    }

    /** The whole texture. */
    public static void image(String texture, float x, float y, float w, float h) {
        image(texture, x, y, w, h, 0, 0, 1, 1, WHITE);
    }

    /**
     * The item (ItemStack) as in the inventory, 16x16 at x, y; with {@code overlay}, the count and
     * the durability.
     */
    public static void item(Object stack, int x, int y, boolean overlay) {
        Host.INSTANCE.drawItem(stack, x, y, overlay);
    }

    /**
     * The item with the count you choose: {@code count} null is the stack's, "" none, and any other
     * text goes in the bottom corner the way the game writes the count (a bow's arrows, the total
     * in the inventory).
     */
    @Experimental(since = "0.1.16")
    public static void item(Object stack, int x, int y, boolean overlay, String count) {
        Host.INSTANCE.drawItemCount(stack, x, y, overlay, count);
    }

    /**
     * The game's own Tab list, drawn now (GUI screen width): for whoever stores the {@link
     * SteinMod#onPlayerList} request and draws later, on top of everything.
     */
    @Experimental(since = "0.1.16")
    public static void gameTabList(int width) {
        Host.INSTANCE.drawGameTabList(width);
    }

    /** The effect icon (game id) as in the inventory, 18x18 at x, y; nothing if it has no icon. */
    @Experimental(since = "0.1.16")
    public static void effectIcon(int id, float x, float y) {
        Host.INSTANCE.drawEffectIcon(id, x, y);
    }

    /** The game's Tab ping bars for {@code ms} (10x8 at x, y; negative = no connection). */
    @Experimental(since = "0.1.16")
    public static void pingIcon(int ms, float x, float y) {
        Host.INSTANCE.drawPingIcon(ms, x, y);
    }

    /**
     * The player's face (with the hat layer), size x size; the game's default if they are not in
     * the Tab.
     */
    public static void playerHead(String nick, float x, float y, float size) {
        Host.INSTANCE.drawPlayerHead(nick, x, y, size);
    }

    /**
     * The same, and on a server without authentication (where everyone is Steve) the skin of the
     * original account with that nick, asked from Mojang in batches and cached: useful for
     * recognizing who you know — on an offline server, the "Notch" there is not Notch. Until the
     * answer arrives, the game's.
     */
    @Experimental(since = "0.1.16")
    public static void playerHead(String nick, float x, float y, float size, boolean premiumSkin) {
        Host.INSTANCE.drawPlayerHeadPremium(nick, x, y, size, premiumSkin);
    }

    /**
     * A player's skin by nick (a {@code ResourceLocation}), to draw their body: the one the server
     * sent; with none (server without authentication), the original account's with that nick, as in
     * {@link #playerHead(String, float, float, float, boolean)}; until none arrives, the game's
     * default.
     */
    @Experimental(since = "0.1.21")
    public static Object playerSkin(String nick) {
        return Host.INSTANCE.playerSkin(nick);
    }

    // --- lines, outlines, gradients, arcs and meshes (0.1.24) ---

    /** A line from (x1, y1) to (x2, y2), {@code width} pixels wide (GUI pixels). */
    @Experimental(since = "0.1.24")
    public static void line(float x1, float y1, float x2, float y2, float width, int argb) {
        begin2d();
        segment(x1, y1, x2, y2, width, argb);
        end2d(RenderBackend.QUADS);
    }

    /**
     * Lines through the {@code count} points (xs, ys), {@code width} pixels wide; with {@code
     * closed}, the last point joins the first. A chart, a path on a map.
     */
    @Experimental(since = "0.1.24")
    public static void polyline(
            float[] xs, float[] ys, int count, float width, int argb, boolean closed) {
        if (count < 2) {
            return;
        }
        begin2d();
        for (int i = 0; i + 1 < count; i++) {
            segment(xs[i], ys[i], xs[i + 1], ys[i + 1], width, argb);
        }
        if (closed && count > 2) {
            segment(xs[count - 1], ys[count - 1], xs[0], ys[0], width, argb);
        }
        end2d(RenderBackend.QUADS);
    }

    /** The edge of a rectangle, {@code width} pixels wide, inside (x, y, w, h). */
    @Experimental(since = "0.1.24")
    public static void rectOutline(float x, float y, float w, float h, float width, int argb) {
        begin2d();
        float t = Math.min(width, Math.min(w, h) / 2);
        Geometry.quad(x, y, 0, x, y + t, 0, x + w, y + t, 0, x + w, y, 0, argb);
        Geometry.quad(x, y + h - t, 0, x, y + h, 0, x + w, y + h, 0, x + w, y + h - t, 0, argb);
        Geometry.quad(x, y + t, 0, x, y + h - t, 0, x + t, y + h - t, 0, x + t, y + t, 0, argb);
        Geometry.quad(
                x + w - t, y + t, 0, x + w - t, y + h - t, 0, x + w, y + h - t, 0, x + w, y + t, 0,
                argb);
        end2d(RenderBackend.QUADS);
    }

    /**
     * A rectangle that fades from {@code from} to {@code to}: top to bottom, or left to right with
     * {@code horizontal}.
     */
    @Experimental(since = "0.1.24")
    public static void gradient(
            float x, float y, float w, float h, int from, int to, boolean horizontal) {
        begin2d();
        int tl = from;
        int bl = horizontal ? from : to;
        int br = to;
        int tr = horizontal ? to : from;
        Geometry.vertex(x, y, 0, tl);
        Geometry.vertex(x, y + h, 0, bl);
        Geometry.vertex(x + w, y + h, 0, br);
        Geometry.vertex(x + w, y, 0, tr);
        end2d(RenderBackend.QUADS);
    }

    /**
     * A piece of a ring centered at (cx, cy): radius {@code r}, {@code thickness} pixels thick,
     * from {@code startDegrees} (0 = right, growing clockwise on the screen) over {@code
     * sweepDegrees}. A cooldown or progress indicator.
     */
    @Experimental(since = "0.1.24")
    public static void arc(
            float cx,
            float cy,
            float r,
            float thickness,
            float startDegrees,
            float sweepDegrees,
            int argb) {
        if (sweepDegrees == 0 || r <= 0) {
            return;
        }
        begin2d();
        float in = Math.max(0, r - thickness);
        int n = Math.max(2, (int) Math.ceil(Geometry.segments(r) * Math.abs(sweepDegrees) / 360));
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(startDegrees + sweepDegrees * i / n);
            double c = Math.cos(a);
            double s = Math.sin(a);
            Geometry.vertex(cx + c * r, cy + s * r, 0, argb);
            Geometry.vertex(cx + c * in, cy + s * in, 0, argb);
        }
        end2d(RenderBackend.TRIANGLE_STRIP);
    }

    /** A mesh, with x and y in GUI coordinates (z is ignored), at the current transform. */
    @Experimental(since = "0.1.24")
    public static void mesh(Mesh mesh) {
        drawMesh(mesh, 0, null);
    }

    /** A textured mesh, with a mod texture. */
    @Experimental(since = "0.1.24")
    public static void mesh(Mesh mesh, Texture texture) {
        drawMesh(mesh, texture.handle, null);
    }

    /** A textured mesh, with a resource texture ("domain:path"). */
    @Experimental(since = "0.1.24")
    public static void mesh(Mesh mesh, String texture) {
        drawMesh(mesh, 0, texture);
    }

    /** Runs {@code draw} with another {@link Blend} for the shapes, lines and meshes. */
    @Experimental(since = "0.1.24")
    public static void blend(Blend mode, Runnable draw) {
        session();
        Blend before = blend;
        blend = mode;
        try {
            draw.run();
        } finally {
            blend = before;
        }
    }

    private static Blend blend = Blend.NORMAL;
    private static int session = -1;

    // A new mod drawing: the blend the previous one left does not carry over.
    private static void session() {
        int s = Geometry.R.session();
        if (s != session) {
            session = s;
            blend = Blend.NORMAL;
        }
    }

    private static void begin2d() {
        session();
        Geometry.R.beginState(blend.ordinal(), false, false, false);
        Geometry.begin(RenderBackend.POSITION_COLOR);
    }

    private static void end2d(int primitive) {
        try {
            Geometry.draw(primitive, 0, null);
        } finally {
            Geometry.R.endState();
        }
    }

    private static void drawMesh(Mesh mesh, int texture, String resource) {
        session();
        Geometry.R.beginState(blend.ordinal(), false, false, false);
        try {
            mesh.draw(Geometry.R, texture, resource, WHITE);
        } finally {
            Geometry.R.endState();
        }
    }

    /** A segment as a quad, {@code width} across the line. */
    private static void segment(float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6F) {
            return;
        }
        float nx = -dy / len * width / 2;
        float ny = dx / len * width / 2;
        Geometry.quad(
                x1 + nx, y1 + ny, 0, x1 - nx, y1 - ny, 0, x2 - nx, y2 - ny, 0, x2 + nx, y2 + ny, 0,
                argb);
    }

    // --- transform and clip ---

    /**
     * Saves the current transform ({@link #translate}, {@link #scale}, {@link #rotate}); {@link
     * #pop} brings it back. Every push needs its pop in the same drawing event.
     */
    public static void push() {
        Host.INSTANCE.drawPush();
    }

    /** Restores the transform saved by the last {@link #push}. */
    public static void pop() {
        Host.INSTANCE.drawPop();
    }

    /**
     * Moves the origin of what is drawn next.
     *
     * @param x offset in GUI units
     * @param y offset in GUI units
     */
    public static void translate(float x, float y) {
        Host.INSTANCE.drawTranslate(x, y);
    }

    /**
     * Scales what is drawn next, around the current origin.
     *
     * @param s factor (1 = unchanged)
     */
    public static void scale(float s) {
        Host.INSTANCE.drawScale(s);
    }

    /** Rotates by degrees, around the current origin. */
    public static void rotate(float degrees) {
        Host.INSTANCE.drawRotate(degrees);
    }

    /** Clips the drawing to the rectangle (GUI coordinates) until {@link #unclip}. */
    public static void clip(float x, float y, float w, float h) {
        Host.INSTANCE.drawClip(x, y, w, h);
    }

    /** Ends the clip started by {@link #clip}. */
    public static void unclip() {
        Host.INSTANCE.drawUnclip();
    }

    private static float clamp01(float x) {
        return Math.max(0, Math.min(1, x));
    }

    // --- color ---

    /** The color with the alpha multiplied by a (0 to 1). */
    public static int alpha(int argb, float a) {
        int al = Math.round((argb >>> 24) * clamp01(a));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    /** The blend of two colors (t = 0: from; 1: to), alpha included. */
    public static int mix(int from, int to, float t) {
        float u = clamp01(t);
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * u);
        int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * u);
        int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * u);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * u);
        return a << 24 | r << 16 | g << 8 | b;
    }

    // --- mod textures ---

    /**
     * A w x h pixel texture made by the mod (a minimap, a chart), transparent until the first
     * {@link Texture#set}.
     */
    public static Texture texture(int w, int h) {
        return new Texture(Host.INSTANCE.textureCreate(w, h), w, h);
    }

    /**
     * With {@code repeat}, the texture repeats beyond the edges (coordinates outside 0 to 1 wrap
     * around): a map in which block (x, z) always lands on the same pixel, and walking requires
     * moving nothing.
     */
    @Experimental(since = "0.1.16")
    public static Texture texture(int w, int h, boolean repeat) {
        return new Texture(Host.INSTANCE.textureCreate(w, h, repeat), w, h);
    }

    /**
     * A mod texture. Release it with {@link #release} when no longer needed: it takes up video
     * memory.
     */
    public static final class Texture {
        /** Width in pixels. */
        public final int width;

        /** Height in pixels. */
        public final int height;

        private int handle;

        Texture(int handle, int width, int height) {
            this.handle = handle;
            this.width = width;
            this.height = height;
        }

        /**
         * Replaces a rectangle of pixels (ARGB, row by row, {@code w * h} starting at {@code
         * offset}).
         */
        public void set(int x, int y, int w, int h, int[] argb, int offset) {
            if (handle != 0) {
                Host.INSTANCE.textureSet(handle, x, y, w, h, argb, offset);
            }
        }

        /** The whole texture at x, y, w, h. */
        public void draw(float x, float y, float w, float h) {
            draw(x, y, w, h, 0, 0, 1, 1, WHITE);
        }

        /** A piece (u0,v0 to u1,v1, 0 to 1), painted with argb. */
        public void draw(
                float x,
                float y,
                float w,
                float h,
                float u0,
                float v0,
                float u1,
                float v1,
                int argb) {
            if (handle != 0) {
                Host.INSTANCE.textureDraw(handle, x, y, w, h, u0, v0, u1, v1, argb);
            }
        }

        /**
         * Fan polygon with the texture (see {@link Render#fan}): each point (xs, ys) with its own
         * texture coordinate (us, vs). A round or rotated map.
         */
        @Experimental(since = "0.1.16")
        public void drawFan(float[] xs, float[] ys, float[] us, float[] vs, int count, int argb) {
            if (handle != 0) {
                Host.INSTANCE.drawFan(xs, ys, us, vs, count, argb, handle);
            }
        }

        /**
         * Smooth (linear) or pixelated (nearest, the default) when the texture is drawn bigger or
         * smaller than its pixels.
         */
        @Experimental(since = "0.1.24")
        public void smooth(boolean smooth) {
            if (handle != 0) {
                Geometry.R.textureSmooth(handle, smooth);
            }
        }

        /** Frees the texture's video memory; drawing it afterwards draws nothing. */
        public void release() {
            if (handle != 0) {
                Host.INSTANCE.textureRelease(handle);
                handle = 0;
            }
        }

        int handle() {
            return handle;
        }
    }
}
