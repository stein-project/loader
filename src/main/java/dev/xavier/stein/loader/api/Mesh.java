/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.RenderBackend;

import java.util.Arrays;

/**
 * Geometry built once and drawn as many times as needed: a model, a marker, a shape that does not
 * change from frame to frame. Draw it on the screen with {@link Render#mesh} or in the world with
 * {@link WorldRender#mesh}, with any transform around it.
 *
 * <pre>{@code
 * Mesh arrow = Mesh.builder(Mesh.Primitive.TRIANGLES, Mesh.Format.COLOR)
 *         .vertex(0, 1, 0).color(0xFFFF5555)
 *         .vertex(-0.5, 0, 0).color(0xFFFF5555)
 *         .vertex(0.5, 0, 0).color(0xFFFF5555)
 *         .build();
 * }</pre>
 *
 * <p>A mesh is immutable and can be drawn from any frame; build it outside the drawing events (when
 * the mod starts, or when what it shows changes), not every frame. Call {@link #release} when it is
 * no longer needed.
 */
@Experimental(since = "0.1.24")
public final class Mesh {

    /** How the vertices are joined. */
    @Experimental(since = "0.1.24")
    public enum Primitive {
        /**
         * Each pair of vertices is a line, one pixel wide. For thick lines see {@link Render#line}.
         */
        LINES(RenderBackend.LINES, 2, 2),
        /** Vertices joined one after the other by one-pixel lines. */
        LINE_STRIP(RenderBackend.LINE_STRIP, 2, 1),
        /** Each three vertices are a triangle. */
        TRIANGLES(RenderBackend.TRIANGLES, 3, 3),
        /** Each vertex after the second makes a triangle with the two before it. */
        TRIANGLE_STRIP(RenderBackend.TRIANGLE_STRIP, 3, 1),
        /** Each vertex after the second makes a triangle with the one before it and the first. */
        TRIANGLE_FAN(RenderBackend.TRIANGLE_FAN, 3, 1),
        /** Each four vertices are a quadrilateral (in order around its edge). */
        QUADS(RenderBackend.QUADS, 4, 4);

        final int code;
        final int minimum;
        final int multiple;

        Primitive(int code, int minimum, int multiple) {
            this.code = code;
            this.minimum = minimum;
            this.multiple = multiple;
        }
    }

    /** What each vertex carries besides its position. */
    @Experimental(since = "0.1.24")
    public enum Format {
        /** A color per vertex. */
        COLOR(RenderBackend.POSITION_COLOR, 4),
        /** A texture coordinate per vertex: draw with a texture. */
        TEXTURE(RenderBackend.POSITION_TEX, 5),
        /** A texture coordinate and a color (which tints the texture) per vertex. */
        TEXTURE_COLOR(RenderBackend.POSITION_TEX_COLOR, 6);

        final int code;
        final int stride;

        Format(int code, int stride) {
            this.code = code;
            this.stride = stride;
        }

        boolean textured() {
            return this != COLOR;
        }

        boolean colored() {
            return this != TEXTURE;
        }
    }

    final Primitive primitive;
    final Format format;
    final float[] data;
    final int count;
    private boolean released;

    private Mesh(Primitive primitive, Format format, float[] data, int count) {
        this.primitive = primitive;
        this.format = format;
        this.data = data;
        this.count = count;
    }

    /**
     * Starts a mesh of {@code primitive}, with vertices in {@code format}.
     *
     * @param primitive how the vertices are joined
     * @param format what each vertex carries besides its position
     * @return a builder with no vertices yet
     */
    public static Builder builder(Primitive primitive, Format format) {
        return new Builder(primitive, format);
    }

    /**
     * How the vertices are joined.
     *
     * @return the primitive the mesh was built with
     */
    public Primitive primitive() {
        return primitive;
    }

    /**
     * What each vertex carries besides its position.
     *
     * @return the format the mesh was built with
     */
    public Format format() {
        return format;
    }

    /**
     * How many vertices the mesh has.
     *
     * @return the vertex count
     */
    public int vertexCount() {
        return count;
    }

    /** Frees what the Loader keeps for this mesh; it draws nothing afterwards. */
    public void release() {
        released = true;
    }

    /**
     * Whether {@link #release} was called (the mesh then draws nothing).
     *
     * @return true once released
     */
    public boolean isReleased() {
        return released;
    }

    /** Draws the mesh now, in the current space, with a texture (handle or resource) or none. */
    void draw(RenderBackend r, int texture, String resource, int tint) {
        if (!released && count > 0) {
            r.draw(primitive.code, format.code, data, 0, count, texture, resource, tint);
        }
    }

    /**
     * Builds a {@link Mesh} vertex by vertex: {@link #vertex} starts a vertex, and {@link #color}
     * and {@link #uv} set it (a vertex without a color is white, without a coordinate is at 0, 0).
     */
    @Experimental(since = "0.1.24")
    public static final class Builder {
        private final Primitive primitive;
        private final Format format;
        private float[] data = new float[64];
        private int count;

        private Builder(Primitive primitive, Format format) {
            this.primitive = primitive;
            this.format = format;
        }

        /** Starts a new vertex at (x, y, z). On the screen, z is ignored. */
        public Builder vertex(double x, double y, double z) {
            int at = count * format.stride;
            if (at + format.stride > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[at] = (float) x;
            data[at + 1] = (float) y;
            data[at + 2] = (float) z;
            if (format.textured()) {
                data[at + 3] = 0;
                data[at + 4] = 0;
            }
            if (format.colored()) {
                data[at + format.stride - 1] = Float.intBitsToFloat(0xFFFFFFFF);
            }
            count++;
            return this;
        }

        /** The current vertex's color (ARGB). Only for the formats with a color. */
        public Builder color(int argb) {
            if (!format.colored()) {
                throw new IllegalStateException("format " + format + " has no color");
            }
            data[current() + format.stride - 1] = Float.intBitsToFloat(argb);
            return this;
        }

        /** The current vertex's texture coordinate (0 to 1). Only for the textured formats. */
        public Builder uv(float u, float v) {
            if (!format.textured()) {
                throw new IllegalStateException("format " + format + " has no texture coordinate");
            }
            int at = current();
            data[at + 3] = u;
            data[at + 4] = v;
            return this;
        }

        /**
         * The finished mesh.
         *
         * @throws IllegalStateException if the number of vertices does not fit the primitive (a
         *     multiple of 3 for triangles, 4 for quads, 2 for lines; at least 3 for strips and
         *     fans)
         */
        public Mesh build() {
            if (count < primitive.minimum || count % primitive.multiple != 0) {
                throw new IllegalStateException(
                        count
                                + " vertices do not make "
                                + primitive
                                + " ("
                                + primitive.multiple
                                + " per shape, at least "
                                + primitive.minimum
                                + ")");
            }
            return new Mesh(primitive, format, Arrays.copyOf(data, count * format.stride), count);
        }

        private int current() {
            if (count == 0) {
                throw new IllegalStateException("call vertex(...) first");
            }
            return (count - 1) * format.stride;
        }
    }
}
