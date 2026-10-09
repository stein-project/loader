/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Color of each block for maps (Terrain.color), as minimaps do it: the average of the top face's
 * texture, read once from the block atlas (at a lower mipmap level), tinted by the biome where the
 * game tints (grass, leaves, water). Without a texture, the game's own map color. Restarts on every
 * resource reload (new texture pack). It came from the HUD's minimap.
 */
final class BlockColors {

    /** Per state: base color (RGB) and the biome tint index (-1 for none). */
    private final Map<IBlockState, int[]> cache = new IdentityHashMap<>();

    private int[] atlas;
    private int atlasW;
    private int atlasH;
    private boolean atlasFailed;

    void reset() {
        cache.clear();
        atlas = null;
        atlasFailed = false;
    }

    /** Color (opaque ARGB) of the block at pos. */
    int color(World w, IBlockState s, BlockPos pos) {
        int[] c = cache.get(s);
        if (c == null) {
            c = compute(s);
            cache.put(s, c);
        }
        int rgb = c[0];
        if (c[1] >= 0) {
            rgb = multiply(rgb, s.getBlock().colorMultiplier(w, pos, c[1]));
        }
        return 0xFF000000 | rgb;
    }

    /** The game's own map color (the map item's): only the material color, no texture or biome. */
    int mapColor(IBlockState s) {
        return 0xFF000000 | s.getBlock().getMapColor(s).colorValue;
    }

    private int[] compute(IBlockState s) {
        Block b = s.getBlock();
        try {
            BlockModelShapes shapes =
                    Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes();
            float[] uv = null;
            int tint = -1;
            if (b.getRenderType() == 3) { // block with a model
                BakedQuad q = top(shapes.getModelForState(s));
                if (q != null) {
                    uv = uv(q.getVertexData());
                    tint = q.hasTintIndex() ? q.getTintIndex() : -1;
                }
            }
            if (uv == null) {
                TextureAtlasSprite sp = shapes.getTexture(s);
                if (sp != null) {
                    uv = new float[] {sp.getMinU(), sp.getMinV(), sp.getMaxU(), sp.getMaxV()};
                }
                if (b.getMaterial().isLiquid()) {
                    tint = 0; // water: the biome color
                }
            }
            int avg = uv != null ? average(uv) : -1;
            if (avg >= 0) {
                return new int[] {avg, tint};
            }
        } catch (RuntimeException e) {
            // falls back to the game's map color
        }
        return new int[] {b.getMapColor(s).colorValue, -1};
    }

    /**
     * The top face's quad of the model (or the first, on models without a top face, such as
     * plants).
     */
    private static BakedQuad top(IBakedModel m) {
        if (m == null) {
            return null;
        }
        List<?> up = m.getFaceQuads(EnumFacing.UP);
        if (up != null && !up.isEmpty()) {
            return (BakedQuad) up.get(0);
        }
        List<?> general = m.getGeneralQuads();
        if (general == null || general.isEmpty()) {
            return null;
        }
        for (Object o : general) {
            if (((BakedQuad) o).getFace() == EnumFacing.UP) {
                return (BakedQuad) o;
            }
        }
        return (BakedQuad) general.get(0);
    }

    /** Rectangle (u0, v0, u1, v1) of the atlas covered by the quad. */
    private static float[] uv(int[] d) {
        int stride = d.length / 4;
        if (stride < 6) {
            return null;
        }
        float u0 = 1;
        float v0 = 1;
        float u1 = 0;
        float v1 = 0;
        for (int i = 0; i < 4; i++) {
            float u = Float.intBitsToFloat(d[i * stride + 4]);
            float v = Float.intBitsToFloat(d[i * stride + 5]);
            u0 = Math.min(u0, u);
            v0 = Math.min(v0, v);
            u1 = Math.max(u1, u);
            v1 = Math.max(v1, v);
        }
        return new float[] {u0, v0, u1, v1};
    }

    /**
     * Average (RGB) of the rectangle's visible pixels; -1 without an atlas or without a visible
     * pixel.
     */
    private int average(float[] uv) {
        if (!loadAtlas()) {
            return -1;
        }
        int x0 = clamp((int) (uv[0] * atlasW), 0, atlasW - 1);
        int y0 = clamp((int) (uv[1] * atlasH), 0, atlasH - 1);
        int x1 = clamp((int) Math.ceil(uv[2] * atlasW), x0 + 1, atlasW);
        int y1 = clamp((int) Math.ceil(uv[3] * atlasH), y0 + 1, atlasH);
        long r = 0;
        long g = 0;
        long bl = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = atlas[y * atlasW + x];
                if ((p >>> 24) < 32) {
                    continue;
                }
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                bl += p & 0xFF;
                n++;
            }
        }
        return n == 0 ? -1 : (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
    }

    /**
     * The block atlas, once, from GL (at mipmap level 2 when there is one: 1/16 of the pixels, the
     * same average).
     */
    private boolean loadAtlas() {
        if (atlas != null) {
            return true;
        }
        if (atlasFailed) {
            return false;
        }
        atlasFailed = true;
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.bindTexture(mc.getTextureMapBlocks().getGlTextureId());
        int level = Math.max(0, Math.min(2, mc.gameSettings.mipmapLevels));
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0) {
            level = 0;
            w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        }
        if (w <= 0 || h <= 0 || (long) w * h > 16L << 20) {
            return false;
        }
        IntBuffer buf = BufferUtils.createIntBuffer(w * h);
        GL11.glGetTexImage(
                GL11.GL_TEXTURE_2D, level, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        int[] a = new int[w * h];
        buf.get(a);
        atlas = a;
        atlasW = w;
        atlasH = h;
        atlasFailed = false;
        return true;
    }

    static int multiply(int rgb, int tint) {
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
