/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api.spi;

/**
 * Whoever draws behind {@code Render}, {@code WorldRender} and {@code Mesh}: the Stein Loader.
 *
 * <p><b>Not for mods.</b> The API classes build the geometry (thick lines, boxes, circles,
 * billboards) and hand it here as plain vertices; the Loader sends them through the game's own
 * drawing path, which works the same on Stein GFX and on the fixed-function pipeline.
 *
 * <p>Vertices are packed in a {@code float[]}, one after the other: the position (x, y, z), then
 * the texture coordinate (u, v) when the format has one, then the color when the format has one (an
 * ARGB int stored with {@link Float#intBitsToFloat}).
 */
public interface RenderBackend {

    // Primitives (the OpenGL values).
    int LINES = 1;
    int LINE_STRIP = 3;
    int TRIANGLES = 4;
    int TRIANGLE_STRIP = 5;
    int TRIANGLE_FAN = 6;
    int QUADS = 7;

    // Vertex formats.
    int POSITION_COLOR = 0;
    int POSITION_TEX = 1;
    int POSITION_TEX_COLOR = 2;

    /**
     * Draws {@code count} vertices starting at vertex {@code first} of {@code data}, now, in the
     * current space (the GUI's or the world's, with the current transform).
     *
     * @param texture a mod texture handle ({@code Render.texture}), or 0
     * @param resource a resource texture ("domain:path"), used when {@code texture} is 0; null for
     *     none
     * @param tint multiplies the vertex colors (ARGB); {@code 0xFFFFFFFF} for none
     */
    void draw(
            int primitive,
            int format,
            float[] data,
            int first,
            int count,
            int texture,
            String resource,
            int tint);

    /**
     * Sets the drawing state until {@link #endState}: the blend (the ordinal of {@code Blend}),
     * whether the depth buffer is tested and written, and whether back faces are culled. Calls do
     * not nest: one {@code beginState}, one {@code endState}.
     */
    void beginState(int blend, boolean depthTest, boolean depthWrite, boolean cull);

    /** Puts back the state the game had before {@link #beginState}. */
    void endState();

    /** Saves the current transform. */
    void push();

    /** Restores the transform saved by the matching {@link #push}. */
    void pop();

    void translate(double x, double y, double z);

    /** Rotates by {@code degrees} around the axis (x, y, z). */
    void rotate(float degrees, float x, float y, float z);

    void scale(float x, float y, float z);

    /** Whether the world is being drawn right now (inside {@code SteinMod.onRenderWorld}). */
    boolean inWorld();

    /**
     * A number that changes before each mod draws (each mod's onRenderWorld, HUD element or
     * screen): what a mod left behind (an unmatched push, a blend) does not reach the next one.
     */
    int session();

    /**
     * The camera, in world coordinates: the origin of the world drawing space (out[0..2], the
     * camera entity's interpolated position), the eye (out[3..5]), the yaw and pitch the world is
     * seen with (out[6], out[7], in degrees) and the frame's partial tick (out[8]).
     */
    void camera(double[] out);

    /**
     * An entity's bounding box at the frame's interpolated position: out[0..5] = minX, minY, minZ,
     * maxX, maxY, maxZ. False if it is not an entity.
     */
    boolean entityBounds(Object entity, double[] out);

    /** The game font's width of {@code s}, in pixels. */
    int textWidth(String s);

    /** Draws {@code s} with the game font at (x, y) of the current space; returns the width. */
    int text(String s, float x, float y, int argb, boolean shadow);

    /** Linear filtering (smooth) or nearest (pixelated) for a mod texture. */
    void textureSmooth(int texture, boolean smooth);
}
