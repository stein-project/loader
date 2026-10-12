/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.engine.TextHook;
import dev.xavier.stein.bridge.hooks.GameHooks;
import dev.xavier.stein.loader.api.spi.RenderBackend;
import dev.xavier.stein.loader.font.SteinFont;
import dev.xavier.stein.loader.gui.Draw2D;
import dev.xavier.stein.loader.gui.Surface;
import dev.xavier.stein.loader.gui.Theme;
import dev.xavier.stein.loader.hud.HudGrid;
import dev.xavier.stein.loader.render.LoaderRender;

import java.util.List;

/**
 * The drawing part of {@link LoaderHost}: shapes, text, fonts, images, items, textures and the
 * render backend, delegated to Draw2D, SteinFont and LoaderRender.
 */
abstract class HostRender extends HostWorld {

    @Override
    public void drawRect(float x, float y, float w, float h, int argb) {
        Draw2D.rect(x, y, w, h, argb);
    }

    @Override
    public void drawRoundRect(float x, float y, float w, float h, float r, int argb) {
        Draw2D.roundRect(x, y, w, h, r, argb);
    }

    @Override
    public void drawSurface(float x, float y, float w, float h, float opacity) {
        Surface.draw(x, y, w, h, opacity);
    }

    @Override
    public void withGameFont(Runnable draw) {
        TextHook.gameFontDepth++;
        try {
            draw.run();
        } finally {
            TextHook.gameFontDepth--;
        }
    }

    @Override
    public void hudCover(float x, float y, float w, float h) {
        HudGrid.cover(x, y, w, h);
    }

    @Override
    public void hudHidden(boolean hidden) {
        GameHooks.hideHud = hidden;
    }

    @Override
    public void drawRing(float cx, float cy, float r, float t, int argb) {
        Draw2D.ring(cx, cy, r, t, argb);
    }

    @Override
    public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        Draw2D.triangle(x1, y1, x2, y2, x3, y3, argb);
    }

    @Override
    public int drawText(String s, float x, float y, int argb, boolean shadow) {
        return Draw2D.text(s, x, y, argb, shadow);
    }

    @Override
    public int textWidth(String s) {
        return s == null ? 0 : Draw2D.textWidth(s);
    }

    @Override
    public int fontHeight() {
        return Draw2D.fontHeight();
    }

    /** The API's faces (Typeface), in its order: title, text, italic, mono. */
    private static final SteinFont.Face[] FACES = {
        SteinFont.Face.DISPLAY, SteinFont.Face.BODY,
        SteinFont.Face.ITALIC, SteinFont.Face.MONO,
    };

    @Override
    public float drawStyledText(String s, float x, float y, int argb, int face, float size) {
        if (s == null || s.isEmpty()) {
            return x;
        }
        SteinFont.Face f = FACES[face];
        if (!SteinFont.ready(f)) {
            // Without the font file: the game's, so the text does not vanish.
            return x + Draw2D.text(s, x, y, argb, false);
        }
        return SteinFont.draw(f, s, x, y, size, argb, 0.0F, Theme.CODES);
    }

    @Override
    public float styledTextWidth(String s, int face, float size) {
        if (s == null) {
            return 0;
        }
        SteinFont.Face f = FACES[face];
        return SteinFont.ready(f) ? SteinFont.width(f, s, size) : Draw2D.textWidth(s);
    }

    @Override
    public float styledTextHeight(int face, float size) {
        return SteinFont.height(FACES[face], size);
    }

    @Override
    public void drawImage(
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
        Draw2D.image(texture, x, y, w, h, u0, v0, u1, v1, argb);
    }

    @Override
    public void drawItem(Object stack, int x, int y, boolean overlay) {
        Draw2D.item(stack, x, y, overlay);
    }

    @Override
    public void drawPlayerHead(String nick, float x, float y, float size) {
        Draw2D.playerHead(nick, x, y, size);
    }

    @Override
    public void drawPush() {
        Draw2D.push();
    }

    @Override
    public void drawPop() {
        Draw2D.pop();
    }

    @Override
    public void drawTranslate(float x, float y) {
        Draw2D.translate(x, y);
    }

    @Override
    public void drawScale(float s) {
        Draw2D.scale(s);
    }

    @Override
    public void drawRotate(float degrees) {
        Draw2D.rotate(degrees);
    }

    @Override
    public void drawClip(float x, float y, float w, float h) {
        Draw2D.clip(x, y, w, h);
    }

    @Override
    public void drawUnclip() {
        Draw2D.unclip();
    }

    @Override
    public RenderBackend render() {
        return LoaderRender.INSTANCE;
    }

    @Override
    public int textureCreate(int w, int h) {
        return Draw2D.textureCreate(w, h);
    }

    @Override
    public void textureSet(int handle, int x, int y, int w, int h, int[] argb, int offset) {
        Draw2D.textureSet(handle, x, y, w, h, argb, offset);
    }

    @Override
    public void textureDraw(
            int handle,
            float x,
            float y,
            float w,
            float h,
            float u0,
            float v0,
            float u1,
            float v1,
            int argb) {
        Draw2D.textureDraw(handle, x, y, w, h, u0, v0, u1, v1, argb);
    }

    @Override
    public void textureRelease(int handle) {
        Draw2D.textureRelease(handle);
    }

    @Override
    public void drawFan(
            float[] xs, float[] ys, float[] us, float[] vs, int count, int argb, int handle) {
        Draw2D.fan(xs, ys, us, vs, count, argb, handle);
    }

    @Override
    public List<String> wrapText(String text, int width) {
        return Draw2D.wrap(text, width);
    }

    @Override
    public int guiSize(boolean width) {
        return Draw2D.guiSize(width);
    }

    @Override
    public void drawItemCount(Object stack, int x, int y, boolean overlay, String count) {
        Draw2D.itemCount(stack, x, y, overlay, count);
    }

    @Override
    public void drawEffectIcon(int id, float x, float y) {
        Draw2D.effectIcon(id, x, y);
    }

    @Override
    public void drawPingIcon(int ms, float x, float y) {
        Draw2D.pingIcon(ms, x, y);
    }

    @Override
    public void drawPlayerHeadPremium(
            String nick, float x, float y, float size, boolean premiumSkin) {
        Draw2D.playerHead(nick, x, y, size, premiumSkin);
    }

    @Override
    public void drawGameTabList(int width) {
        HudOps.drawGameTabList(width);
    }

    @Override
    public int textureCreate(int w, int h, boolean repeat) {
        return Draw2D.textureCreate(w, h, repeat);
    }

    @Override
    public String trimText(String text, int width) {
        return text == null ? "" : Draw2D.trim(text, width);
    }
}
