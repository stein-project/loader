/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import static dev.xavier.stein.loader.api.Geometry.R;

import dev.xavier.stein.loader.api.spi.RenderBackend;

/**
 * Drawing in the world: lines, boxes, block and entity outlines, circles on the ground, beams,
 * labels and images that face the camera, and {@link Mesh meshes}. Call it from {@link
 * SteinMod#onRenderWorld}; anywhere else it throws {@link IllegalStateException}.
 *
 * <pre>{@code
 * public void onRenderWorld(float partialTicks) {
 *     WorldRender.blockOutline(x, y, z, 0.02, 0xFF55FF55);
 *     WorldRender.throughWalls(() -> WorldRender.label("Home", x + 0.5, y + 1.5, z + 0.5, 1, 0xFFFFFFFF));
 * }
 * }</pre>
 *
 * <p><b>Coordinates</b> are world coordinates (blocks), the same as the entities' and the blocks':
 * the camera is taken into account here, frame by frame. Between {@link #push(double, double,
 * double)} and {@link #pop} they become local, relative to the point given to push, and {@link
 * #rotate} and {@link #scale} turn and size what is drawn inside — the way to place a {@link Mesh}.
 *
 * <p><b>Sizes</b> (line widths, radii) are in blocks too: a line {@code 0.02} wide is thin, and it
 * gets thinner with distance like everything else in the world. Colors are ARGB ({@code
 * 0xAARRGGBB}). Every shape is visible from both sides.
 *
 * <p>The same drawing works on Stein GFX and on the game's fixed-function pipeline: the Loader
 * draws it through the game's own path. Drawing with OpenGL, {@code GlStateManager} or the {@code
 * Tessellator} directly is not supported.
 */
@Experimental(since = "0.1.24")
public final class WorldRender {

    private static final double[] CAMERA = new double[9];
    private static final double[] BOX = new double[6];

    private static Blend blend = Blend.NORMAL;
    private static boolean throughWalls;
    private static int local;
    private static int session = -1;

    private WorldRender() {}

    // --- state ---

    /**
     * Runs {@code draw} with everything drawn visible through blocks and entities (no depth test):
     * markers, waypoints, labels that must never hide.
     */
    public static void throughWalls(Runnable draw) {
        inWorld();
        boolean before = throughWalls;
        throughWalls = true;
        try {
            draw.run();
        } finally {
            throughWalls = before;
        }
    }

    /** Runs {@code draw} with another {@link Blend} (glows: {@link Blend#ADDITIVE}). */
    public static void blend(Blend mode, Runnable draw) {
        inWorld();
        Blend before = blend;
        blend = mode;
        try {
            draw.run();
        } finally {
            blend = before;
        }
    }

    // --- the camera ---

    /** The camera's interpolated position this frame (x). */
    public static double cameraX() {
        return camera()[3];
    }

    /**
     * The camera's interpolated position this frame (y).
     *
     * @return the camera's y, in world coordinates
     */
    public static double cameraY() {
        return camera()[4];
    }

    /**
     * The camera's interpolated position this frame (z).
     *
     * @return the camera's z, in world coordinates
     */
    public static double cameraZ() {
        return camera()[5];
    }

    /** How far (x, y, z) is from the camera, in blocks. */
    public static double distance(double x, double y, double z) {
        double[] c = camera();
        double dx = x - c[3];
        double dy = y - c[4];
        double dz = z - c[5];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The frame's partial tick: for interpolating your own moving things. */
    public static float partialTicks() {
        return (float) camera()[8];
    }

    // --- transform ---

    /**
     * Moves the origin to the world point (x, y, z): until the matching {@link #pop}, coordinates
     * are relative to it, and {@link #rotate} and {@link #scale} act around it.
     */
    public static void push(double x, double y, double z) {
        inWorld();
        camera();
        R.push();
        R.translate(wx(x), wy(y), wz(z));
        local++;
    }

    /** Saves the transform without moving the origin (only inside another push). */
    public static void push() {
        inWorld();
        if (local == 0) {
            throw new IllegalStateException("push() needs a point first: push(x, y, z)");
        }
        R.push();
        local++;
    }

    /**
     * Restores the transform saved by the matching push.
     *
     * @throws IllegalStateException without a push
     */
    public static void pop() {
        if (local == 0) {
            throw new IllegalStateException("pop() without push");
        }
        R.pop();
        local--;
    }

    /**
     * Moves the origin, relative to the current one (only inside a push).
     *
     * @param x offset in blocks
     * @param y offset in blocks
     * @param z offset in blocks
     */
    public static void translate(double x, double y, double z) {
        local();
        R.translate(x, y, z);
    }

    /** Rotates by degrees around the axis (x, y, z), e.g. (0, 1, 0) to turn sideways. */
    public static void rotate(float degrees, float x, float y, float z) {
        local();
        R.rotate(degrees, x, y, z);
    }

    /**
     * Scales evenly around the current origin (only inside a push).
     *
     * @param s factor (1 = unchanged)
     */
    public static void scale(float s) {
        scale(s, s, s);
    }

    /**
     * Scales around the current origin, per axis (only inside a push).
     *
     * @param x factor along x
     * @param y factor along y
     * @param z factor along z
     */
    public static void scale(float x, float y, float z) {
        local();
        R.scale(x, y, z);
    }

    // --- lines ---

    /**
     * A line from (x1, y1, z1) to (x2, y2, z2), {@code width} blocks wide, turned to the camera.
     */
    public static void line(
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double width,
            int argb) {
        begin();
        segment(x1, y1, z1, x2, y2, z2, width, argb);
        end(RenderBackend.QUADS);
    }

    /** The edges of the box from (minX, minY, minZ) to (maxX, maxY, maxZ). */
    public static void boxOutline(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            double width,
            int argb) {
        begin();
        edges(minX, minY, minZ, maxX, maxY, maxZ, width, argb);
        end(RenderBackend.QUADS);
    }

    /** The edges of the block at (x, y, z), slightly outside it so they do not flicker. */
    public static void blockOutline(int x, int y, int z, double width, int argb) {
        double e = 0.002;
        boxOutline(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e, width, argb);
    }

    /** The edges of an entity's hit box, where it is drawn this frame. */
    public static void entityOutline(Object entity, double width, int argb) {
        if (bounds(entity)) {
            boxOutline(BOX[0], BOX[1], BOX[2], BOX[3], BOX[4], BOX[5], width, argb);
        }
    }

    // --- surfaces ---

    /** A filled box (all six faces). */
    public static void box(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            int argb) {
        begin();
        double ax = wx(minX), ay = wy(minY), az = wz(minZ);
        double bx = wx(maxX), by = wy(maxY), bz = wz(maxZ);
        Geometry.quad(ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, argb);
        Geometry.quad(ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, argb);
        Geometry.quad(ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, argb);
        Geometry.quad(ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, argb);
        Geometry.quad(ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, argb);
        Geometry.quad(bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, argb);
        end(RenderBackend.QUADS);
    }

    /** The block at (x, y, z) filled with a color (use some transparency). */
    public static void block(int x, int y, int z, int argb) {
        double e = 0.002;
        box(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e, argb);
    }

    /** An entity's hit box, filled. */
    public static void entityBox(Object entity, int argb) {
        if (bounds(entity)) {
            box(BOX[0], BOX[1], BOX[2], BOX[3], BOX[4], BOX[5], argb);
        }
    }

    /** A quadrilateral through four points, in order around its edge. */
    public static void quad(
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
        begin();
        Geometry.quad(
                wx(x1), wy(y1), wz(z1), wx(x2), wy(y2), wz(z2), wx(x3), wy(y3), wz(z3), wx(x4),
                wy(y4), wz(z4), argb);
        end(RenderBackend.QUADS);
    }

    /**
     * A filled triangle.
     *
     * @param x1 first corner x
     * @param y1 first corner y
     * @param z1 first corner z
     * @param x2 second corner x
     * @param y2 second corner y
     * @param z2 second corner z
     * @param x3 third corner x
     * @param y3 third corner y
     * @param z3 third corner z
     * @param argb color, {@code 0xAARRGGBB}
     */
    public static void triangle(
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double x3,
            double y3,
            double z3,
            int argb) {
        begin();
        Geometry.vertex(wx(x1), wy(y1), wz(z1), argb);
        Geometry.vertex(wx(x2), wy(y2), wz(z2), argb);
        Geometry.vertex(wx(x3), wy(y3), wz(z3), argb);
        end(RenderBackend.TRIANGLES);
    }

    /** A filled horizontal disc centered at (x, y, z): an area on the ground. */
    public static void circle(double x, double y, double z, double radius, int argb) {
        begin();
        double cx = wx(x), cy = wy(y), cz = wz(z);
        int n = segments(radius);
        Geometry.vertex(cx, cy, cz, argb);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            Geometry.vertex(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius, argb);
        }
        end(RenderBackend.TRIANGLE_FAN);
    }

    /** A horizontal ring (the edge of a circle), {@code width} blocks wide. */
    public static void ring(double x, double y, double z, double radius, double width, int argb) {
        begin();
        double cx = wx(x), cy = wy(y), cz = wz(z);
        double in = Math.max(0, radius - width / 2);
        double out = radius + width / 2;
        int n = segments(radius);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            Geometry.vertex(cx + c * out, cy, cz + s * out, argb);
            Geometry.vertex(cx + c * in, cy, cz + s * in, argb);
        }
        end(RenderBackend.TRIANGLE_STRIP);
    }

    /**
     * A vertical beam (a cylinder without caps) at (x, z), from height {@code fromY} to {@code
     * toY}: a waypoint seen from far away. Looks best with {@link Blend#ADDITIVE}.
     */
    public static void beam(double x, double z, double fromY, double toY, double radius, int argb) {
        begin();
        double cx = wx(x), cz = wz(z);
        double y0 = wy(fromY), y1 = wy(toY);
        int n = 16;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double px = cx + Math.cos(a) * radius;
            double pz = cz + Math.sin(a) * radius;
            Geometry.vertex(px, y1, pz, argb);
            Geometry.vertex(px, y0, pz, argb);
        }
        end(RenderBackend.TRIANGLE_STRIP);
    }

    // --- facing the camera ---

    /**
     * Text centered at (x, y, z), always facing the camera, like the names above the players.
     * {@code scale} 1 is the size of those names.
     *
     * @param background a dark rectangle behind the text, as the game's names have
     */
    public static void label(
            String text, double x, double y, double z, float scale, int argb, boolean background) {
        billboard(x, y, z, scale * 0.025F);
        state();
        try {
            int w = R.textWidth(text);
            float left = -w / 2F;
            if (background) {
                Geometry.begin(RenderBackend.POSITION_COLOR);
                Geometry.quad(
                        left - 1,
                        -1,
                        0,
                        left - 1,
                        8,
                        0,
                        left + w + 1,
                        8,
                        0,
                        left + w + 1,
                        -1,
                        0,
                        0x40000000);
                Geometry.draw(RenderBackend.QUADS, 0, null);
            }
            R.text(text, left, 0, argb, false);
        } finally {
            R.endState();
            R.pop();
        }
    }

    /** {@link #label(String, double, double, double, float, int, boolean)} without a background. */
    public static void label(String text, double x, double y, double z, float scale, int argb) {
        label(text, x, y, z, scale, argb, false);
    }

    /**
     * A resource texture ("domain:path") as a square {@code size} blocks wide, centered at (x, y,
     * z) and facing the camera: an icon over a place or an entity.
     */
    public static void image(String texture, double x, double y, double z, double size, int argb) {
        facing(x, y, z, size, 0, texture, argb);
    }

    /** The same with a mod texture ({@link Render#texture}). */
    public static void image(
            Render.Texture texture, double x, double y, double z, double size, int argb) {
        if (texture.handle() != 0) {
            facing(x, y, z, size, texture.handle(), null, argb);
        }
    }

    // --- meshes ---

    /** A mesh with its origin at the world point (x, y, z) (or the local one, inside a push). */
    public static void mesh(Mesh mesh, double x, double y, double z) {
        mesh(mesh, x, y, z, 0, null, 0xFFFFFFFF);
    }

    /** A textured mesh, with a mod texture. */
    public static void mesh(Mesh mesh, Render.Texture texture, double x, double y, double z) {
        mesh(mesh, x, y, z, texture.handle(), null, 0xFFFFFFFF);
    }

    /** A textured mesh, with a resource texture ("domain:path"). */
    public static void mesh(Mesh mesh, String texture, double x, double y, double z) {
        mesh(mesh, x, y, z, 0, texture, 0xFFFFFFFF);
    }

    private static void mesh(
            Mesh mesh, double x, double y, double z, int texture, String resource, int tint) {
        inWorld();
        camera();
        R.push();
        R.translate(wx(x), wy(y), wz(z));
        state();
        try {
            mesh.draw(R, texture, resource, tint);
        } finally {
            R.endState();
            R.pop();
        }
    }

    // --- inside ---

    private static void inWorld() {
        if (!R.inWorld()) {
            throw new IllegalStateException("WorldRender only draws inside SteinMod.onRenderWorld");
        }
        // A new mod drawing: nothing the previous one left behind carries over.
        int s = R.session();
        if (s != session) {
            session = s;
            local = 0;
            blend = Blend.NORMAL;
            throughWalls = false;
        }
    }

    private static void local() {
        if (local == 0) {
            throw new IllegalStateException("translate, rotate and scale need push(x, y, z) first");
        }
    }

    private static double[] camera() {
        R.camera(CAMERA);
        return CAMERA;
    }

    // World coordinates to the drawing space: minus the camera's origin, except inside a push.
    private static double wx(double x) {
        return local > 0 ? x : x - CAMERA[0];
    }

    private static double wy(double y) {
        return local > 0 ? y : y - CAMERA[1];
    }

    private static double wz(double z) {
        return local > 0 ? z : z - CAMERA[2];
    }

    private static boolean bounds(Object entity) {
        inWorld();
        return R.entityBounds(entity, BOX);
    }

    private static int segments(double radius) {
        return (int) Math.max(16, Math.min(128, Math.round(radius * 16)));
    }

    private static void state() {
        R.beginState(blend.ordinal(), !throughWalls, false, false);
    }

    private static void begin() {
        inWorld();
        camera();
        state();
        Geometry.begin(RenderBackend.POSITION_COLOR);
    }

    private static void end(int primitive) {
        try {
            Geometry.draw(primitive, 0, null);
        } finally {
            R.endState();
        }
    }

    /**
     * A segment as a quad turned to the eye: across the line and across the view, so it keeps its
     * width from any angle.
     */
    private static void segment(
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double width,
            int argb) {
        double ax = wx(x1), ay = wy(y1), az = wz(z1);
        double bx = wx(x2), by = wy(y2), bz = wz(z2);
        // The eye in the drawing space (inside a push the line is local: use the view direction).
        double ex = local > 0 ? 0 : CAMERA[3] - CAMERA[0];
        double ey = local > 0 ? 0 : CAMERA[4] - CAMERA[1];
        double ez = local > 0 ? 0 : CAMERA[5] - CAMERA[2];
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double vx = (ax + bx) / 2 - ex, vy = (ay + by) / 2 - ey, vz = (az + bz) / 2 - ez;
        // side = dir x view
        double sx = dy * vz - dz * vy;
        double sy = dz * vx - dx * vz;
        double sz = dx * vy - dy * vx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-9) {
            return;
        }
        double k = width / 2 / len;
        sx *= k;
        sy *= k;
        sz *= k;
        Geometry.quad(
                ax - sx, ay - sy, az - sz, ax + sx, ay + sy, az + sz, bx + sx, by + sy, bz + sz,
                bx - sx, by - sy, bz - sz, argb);
    }

    private static void edges(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz,
            double width,
            int argb) {
        // bottom
        segment(ax, ay, az, bx, ay, az, width, argb);
        segment(bx, ay, az, bx, ay, bz, width, argb);
        segment(bx, ay, bz, ax, ay, bz, width, argb);
        segment(ax, ay, bz, ax, ay, az, width, argb);
        // top
        segment(ax, by, az, bx, by, az, width, argb);
        segment(bx, by, az, bx, by, bz, width, argb);
        segment(bx, by, bz, ax, by, bz, width, argb);
        segment(ax, by, bz, ax, by, az, width, argb);
        // sides
        segment(ax, ay, az, ax, by, az, width, argb);
        segment(bx, ay, az, bx, by, az, width, argb);
        segment(bx, ay, bz, bx, by, bz, width, argb);
        segment(ax, ay, bz, ax, by, bz, width, argb);
    }

    /** Pushes a transform at (x, y, z) that faces the camera, scaled by s, with y going down. */
    private static void billboard(double x, double y, double z, float s) {
        inWorld();
        double[] c = camera();
        R.push();
        R.translate(wx(x), wy(y), wz(z));
        R.rotate((float) -c[6], 0, 1, 0);
        R.rotate((float) c[7], 1, 0, 0);
        R.scale(-s, -s, s);
    }

    private static void facing(
            double x, double y, double z, double size, int texture, String resource, int argb) {
        billboard(x, y, z, (float) size);
        state();
        try {
            Geometry.begin(RenderBackend.POSITION_TEX_COLOR);
            Geometry.vertex(-0.5, -0.5, 0, 0, 0, argb);
            Geometry.vertex(-0.5, 0.5, 0, 0, 1, argb);
            Geometry.vertex(0.5, 0.5, 0, 1, 1, argb);
            Geometry.vertex(0.5, -0.5, 0, 1, 0, argb);
            Geometry.draw(RenderBackend.QUADS, texture, resource);
        } finally {
            R.endState();
            R.pop();
        }
    }
}
