/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.render;

import dev.xavier.stein.gfx.SteinGameState;
import dev.xavier.stein.loader.api.spi.RenderBackend;
import dev.xavier.stein.loader.gui.Draw2D;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * The drawing behind the API's Render, WorldRender and Mesh: vertices through the game's
 * Tessellator and state through GlStateManager — the path Stein GFX captures and the fixed-function
 * pipeline runs as it is, so the same mod draws the same on both.
 *
 * <p>The state a drawing needs (blend, depth, cull; lighting off, alpha test at "greater than 0")
 * is set by {@link #beginState} and the game's is put back by {@link #endState}, read from
 * GlStateManager's own cache. Game thread only.
 */
public final class LoaderRender implements RenderBackend {

    public static final LoaderRender INSTANCE = new LoaderRender();

    // Blend factors per Blend ordinal: color source, color destination (alpha: one, zero).
    private static final int[][] BLENDS = {
        {GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA},
        {GL11.GL_SRC_ALPHA, GL11.GL_ONE},
        {GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA},
        {GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR},
    };

    private final Map<String, ResourceLocation> resources = new HashMap<>();

    private boolean inWorld;
    private int session;
    private float partialTicks;
    private int depth;

    // The game's state, saved by beginState.
    private boolean saved;
    private boolean blend;
    private int blendSrc;
    private int blendDst;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private boolean depthTest;
    private boolean depthMask;
    private boolean cull;
    private boolean lighting;
    private boolean alphaTest;
    private int alphaFunc;
    private float alphaRef;
    private boolean texture2D;

    private LoaderRender() {}

    // --- the Loader's side ---

    /** A mod is about to draw: what the previous one left behind does not carry over. */
    public void nextSession() {
        session++;
        // A mod that pushed and did not pop: the transforms go back to what they were.
        while (depth > 0) {
            GlStateManager.popMatrix();
            depth--;
        }
        if (saved) {
            endState();
        }
    }

    /** The world drawing (SteinMod.onRenderWorld) starts or ends. */
    public void world(boolean drawing, float partialTicks) {
        inWorld = drawing;
        this.partialTicks = partialTicks;
    }

    // --- RenderBackend ---

    @Override
    public void draw(
            int primitive,
            int format,
            float[] data,
            int first,
            int count,
            int texture,
            String resource,
            int tint) {
        boolean textured = format != POSITION_COLOR;
        boolean colored = format != POSITION_TEX;
        if (textured) {
            GlStateManager.enableTexture2D();
            if (texture != 0) {
                GlStateManager.bindTexture(texture);
            } else if (resource != null) {
                Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(resources.computeIfAbsent(resource, ResourceLocation::new));
            }
        } else {
            // A drawing without texture coordinates must not have a texture on (Stein GFX
            // refuses it, and the fixed pipeline would sample garbage).
            GlStateManager.disableTexture2D();
        }
        float ta = (tint >>> 24) / 255F;
        float tr = (tint >> 16 & 255) / 255F;
        float tg = (tint >> 8 & 255) / 255F;
        float tb = (tint & 255) / 255F;
        // Without a color per vertex, the tint is the current color.
        GlStateManager.color(
                colored ? 1 : tr, colored ? 1 : tg, colored ? 1 : tb, colored ? 1 : ta);
        VertexFormat vf =
                switch (format) {
                    case POSITION_TEX -> DefaultVertexFormats.POSITION_TEX;
                    case POSITION_TEX_COLOR -> DefaultVertexFormats.POSITION_TEX_COLOR;
                    default -> DefaultVertexFormats.POSITION_COLOR;
                };
        int stride = format == POSITION_TEX ? 5 : format == POSITION_TEX_COLOR ? 6 : 4;
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        wr.begin(primitive, vf);
        for (int i = first, at = first * stride; i < first + count; i++, at += stride) {
            wr.pos(data[at], data[at + 1], data[at + 2]);
            if (textured) {
                wr.tex(data[at + 3], data[at + 4]);
            }
            if (colored) {
                int c = Float.floatToRawIntBits(data[at + stride - 1]);
                wr.color(
                        Math.round((c >> 16 & 255) * tr),
                        Math.round((c >> 8 & 255) * tg),
                        Math.round((c & 255) * tb),
                        Math.round((c >>> 24) * ta));
            }
            wr.endVertex();
        }
        t.draw();
        GlStateManager.color(1, 1, 1, 1);
        if (!textured) {
            GlStateManager.enableTexture2D();
        }
    }

    @Override
    public void beginState(int blendMode, boolean depthTest, boolean depthWrite, boolean cull) {
        if (saved) {
            endState();
        }
        saved = true;
        this.blend = SteinGameState.blend();
        blendSrc = SteinGameState.blendSrc();
        blendDst = SteinGameState.blendDst();
        blendSrcAlpha = SteinGameState.blendSrcAlpha();
        blendDstAlpha = SteinGameState.blendDstAlpha();
        this.depthTest = SteinGameState.depthTest();
        depthMask = SteinGameState.depthMask();
        this.cull = SteinGameState.cull();
        lighting = SteinGameState.lighting();
        alphaTest = SteinGameState.alphaTest();
        alphaFunc = SteinGameState.alphaFunc();
        alphaRef = SteinGameState.alphaRef();
        texture2D = SteinGameState.texture2D(0);

        int[] f = BLENDS[Math.max(0, Math.min(BLENDS.length - 1, blendMode))];
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(f[0], f[1], GL11.GL_ONE, GL11.GL_ZERO);
        if (depthTest) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
        GlStateManager.depthMask(depthWrite);
        if (cull) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        // A color per vertex blends across the shape (the game keeps flat shading: one color per
        // triangle, the last vertex's).
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    @Override
    public void endState() {
        if (!saved) {
            return;
        }
        saved = false;
        GlStateManager.shadeModel(GL11.GL_FLAT);
        if (blend) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }
        GlStateManager.tryBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha);
        if (depthTest) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
        GlStateManager.depthMask(depthMask);
        if (cull) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        if (lighting) {
            GlStateManager.enableLighting();
        }
        GlStateManager.alphaFunc(alphaFunc, alphaRef);
        if (!alphaTest) {
            GlStateManager.disableAlpha();
        }
        if (texture2D) {
            GlStateManager.enableTexture2D();
        } else {
            GlStateManager.disableTexture2D();
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public void push() {
        GlStateManager.pushMatrix();
        depth++;
    }

    @Override
    public void pop() {
        if (depth > 0) {
            GlStateManager.popMatrix();
            depth--;
        }
    }

    @Override
    public void translate(double x, double y, double z) {
        GlStateManager.translate(x, y, z);
    }

    @Override
    public void rotate(float degrees, float x, float y, float z) {
        GlStateManager.rotate(degrees, x, y, z);
    }

    @Override
    public void scale(float x, float y, float z) {
        GlStateManager.scale(x, y, z);
    }

    @Override
    public boolean inWorld() {
        return inWorld;
    }

    @Override
    public int session() {
        return session;
    }

    @Override
    public void camera(double[] out) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        Entity e = mc.getRenderViewEntity();
        out[0] = rm.viewerPosX;
        out[1] = rm.viewerPosY;
        out[2] = rm.viewerPosZ;
        if (e != null) {
            // The eye, third person included (the camera's offset from the entity's eyes).
            Vec3 eye = ActiveRenderInfo.projectViewFromEntity(e, partialTicks);
            out[3] = eye.xCoord;
            out[4] = eye.yCoord;
            out[5] = eye.zCoord;
        } else {
            out[3] = out[0];
            out[4] = out[1];
            out[5] = out[2];
        }
        out[6] = rm.playerViewY;
        out[7] = rm.playerViewX;
        out[8] = partialTicks;
    }

    @Override
    public boolean entityBounds(Object entity, double[] out) {
        if (!(entity instanceof Entity e)) {
            return false;
        }
        AxisAlignedBB box = e.getEntityBoundingBox();
        if (box == null) {
            return false;
        }
        // Where the entity is drawn this frame: the box moved from its position to the
        // interpolated one.
        double dx = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks - e.posX;
        double dy = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks - e.posY;
        double dz = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks - e.posZ;
        out[0] = box.minX + dx;
        out[1] = box.minY + dy;
        out[2] = box.minZ + dz;
        out[3] = box.maxX + dx;
        out[4] = box.maxY + dy;
        out[5] = box.maxZ + dz;
        return true;
    }

    @Override
    public int textWidth(String s) {
        return Draw2D.textWidth(s);
    }

    @Override
    public int text(String s, float x, float y, int argb, boolean shadow) {
        return Draw2D.text(s, x, y, argb, shadow);
    }

    @Override
    public void textureSmooth(int texture, boolean smooth) {
        int filter = smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
    }
}
