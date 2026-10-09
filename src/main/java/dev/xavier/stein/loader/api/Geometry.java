/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;
import dev.xavier.stein.loader.api.spi.RenderBackend;

import java.util.Arrays;

/**
 * The vertices of the shape being drawn, reused from call to call: {@link Render} and {@link
 * WorldRender} add vertices here and hand them to the Loader in one go. Game thread only, like all
 * drawing.
 */
final class Geometry {

    static final RenderBackend R = Host.INSTANCE.render();

    private static float[] data = new float[1024];
    private static int count;
    private static int stride;
    private static int format;

    private Geometry() {}

    static void begin(int vertexFormat) {
        format = vertexFormat;
        stride =
                switch (vertexFormat) {
                    case RenderBackend.POSITION_TEX -> 5;
                    case RenderBackend.POSITION_TEX_COLOR -> 6;
                    default -> 4;
                };
        count = 0;
    }

    private static int room() {
        int at = count * stride;
        if (at + stride > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        count++;
        return at;
    }

    /** A vertex with a color (POSITION_COLOR). */
    static void vertex(double x, double y, double z, int argb) {
        int at = room();
        data[at] = (float) x;
        data[at + 1] = (float) y;
        data[at + 2] = (float) z;
        data[at + 3] = Float.intBitsToFloat(argb);
    }

    /** A vertex with a texture coordinate, and a color if the format has one. */
    static void vertex(double x, double y, double z, float u, float v, int argb) {
        int at = room();
        data[at] = (float) x;
        data[at + 1] = (float) y;
        data[at + 2] = (float) z;
        data[at + 3] = u;
        data[at + 4] = v;
        if (format == RenderBackend.POSITION_TEX_COLOR) {
            data[at + 5] = Float.intBitsToFloat(argb);
        }
    }

    /** Hands the vertices added since {@link #begin} to the Loader. */
    static void draw(int primitive, int texture, String resource) {
        if (count > 0) {
            R.draw(primitive, format, data, 0, count, texture, resource, 0xFFFFFFFF);
        }
        count = 0;
    }

    /** A quadrilateral of one color (four corners in order around its edge). */
    static void quad(
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double x3,
            double y3,
            double z3,
            double x4,
            double y4,
            double z4,
            int argb) {
        vertex(x1, y1, z1, argb);
        vertex(x2, y2, z2, argb);
        vertex(x3, y3, z3, argb);
        vertex(x4, y4, z4, argb);
    }

    /** Segments for a circle of radius r drawn at the given scale (smooth, but not wasteful). */
    static int segments(double r) {
        return (int) Math.max(12, Math.min(96, Math.round(r * 6)));
    }
}
