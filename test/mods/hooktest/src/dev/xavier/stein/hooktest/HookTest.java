/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.hooktest;

import dev.xavier.stein.loader.api.Blend;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Mesh;
import dev.xavier.stein.loader.api.Net;
import dev.xavier.stein.loader.api.Render;
import dev.xavier.stein.loader.api.SteinMod;
import dev.xavier.stein.loader.api.WorldRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;

/**
 * Test mod for the hooks (does not go to the player; the mod build assembles it into
 * build/test-mods).
 *
 * <ul>
 *   <li>onRenderWorld and onOverlay: one of each shape of the Render API (WorldRender, Render,
 *       Mesh) around a point in front of the player — the screenshots of both pipelines compare
 *       them;
 *   <li>onCustomPayload: counts plugin packets and writes the first channels;
 *   <li>onPlaySound: counts sounds and writes the first ones;
 *   <li>onSendChat: "/hooktest" does not go to the server — answers in the chat with the counts and
 *       sends a plugin packet.
 * </ul>
 */
public final class HookTest implements SteinMod {

    private int frames;
    private int payloads;
    private int sounds;
    private String lastChannel = "-";

    // The Render API scene: one of each shape, around a point six blocks in front of the camera.
    private double sx;
    private double sy;
    private double sz;
    private Mesh pyramid;
    private Render.Texture checker;

    @Override
    public void onRenderWorld(float partialTicks) {
        frames++;
        Entity e = Minecraft.getMinecraft().getRenderViewEntity();
        if (e == null) {
            return;
        }
        // Six blocks in front of the camera, at foot height, every frame: always in view.
        double yaw = Math.toRadians(e.rotationYaw);
        sx = Math.floor(WorldRender.cameraX() - Math.sin(yaw) * 6);
        sy = Math.floor(e.posY);
        sz = Math.floor(WorldRender.cameraZ() + Math.cos(yaw) * 6);
        WorldRender.throughWalls(this::scene);
    }

    private void scene() {
        Entity e = Minecraft.getMinecraft().getRenderViewEntity();
        if (pyramid == null) {
            pyramid = pyramid();
        }
        WorldRender.blockOutline((int) sx, (int) sy, (int) sz, 0.03, 0xFF55FF55);
        WorldRender.block((int) sx + 2, (int) sy, (int) sz, 0x60FF3030);
        WorldRender.line(sx + 0.5, sy + 1, sz + 0.5, sx + 0.5, sy + 3, sz + 0.5, 0.05, 0xFFFFFF55);
        WorldRender.circle(sx - 1.5, sy + 0.02, sz + 0.5, 1.2, 0x503060FF);
        WorldRender.ring(sx - 1.5, sy + 0.03, sz + 0.5, 1.2, 0.08, 0xFF3060FF);
        WorldRender.blend(
                Blend.ADDITIVE,
                () -> WorldRender.beam(sx + 3.5, sz + 0.5, sy + 1, sy + 12, 0.15, 0xA040E0FF));
        WorldRender.image("textures/items/diamond.png", sx + 0.5, sy + 3.6, sz + 0.5, 0.6, -1);
        WorldRender.throughWalls(
                () ->
                        WorldRender.label(
                                "Stein WorldRender",
                                sx + 0.5,
                                sy + 4.4,
                                sz + 0.5,
                                1.5F,
                                0xFFFFFFFF,
                                true));
        // The mesh turns on its own axis: push moves the origin to the point, rotate turns it.
        WorldRender.push(sx + 2.5, sy + 1.6, sz + 0.5);
        WorldRender.rotate((frames % 360) * 2F, 0, 1, 0);
        WorldRender.mesh(pyramid, 0, 0, 0);
        WorldRender.pop();
        for (Object other : Entities.all()) {
            if (other != e && Entities.distance(other, e) < 16) {
                WorldRender.entityOutline(other, 0.02, 0xFFFF55FF);
            }
        }
    }

    @Override
    public void onOverlay(float partialTicks) {
        if (checker == null) {
            checker = Render.texture(8, 8);
            int[] px = new int[64];
            for (int i = 0; i < 64; i++) {
                px[i] = ((i / 8 + i % 8) % 2 == 0) ? 0xFFFFFFFF : 0xFF3050A0;
            }
            checker.set(0, 0, 8, 8, px, 0);
        }
        float x = 10;
        float y = 40;
        Render.gradient(x, y, 120, 20, 0xFF203060, 0xFF60A0FF, true);
        Render.rectOutline(x, y + 26, 120, 20, 2, 0xFFFFFFFF);
        Render.line(x, y + 54, x + 120, y + 70, 3, 0xFFFF5555);
        float[] xs = {x, x + 30, x + 60, x + 90, x + 120};
        float[] ys = {y + 100, y + 80, y + 95, y + 78, y + 90};
        Render.polyline(xs, ys, 5, 2, 0xFF55FF55, false);
        float progress = (frames % 120) / 120F;
        Render.arc(x + 160, y + 30, 22, 5, -90, 360 * progress, 0xFFFFAA00);
        Render.blend(Blend.ADDITIVE, () -> Render.circle(x + 160, y + 85, 14, 0x8040A0FF));
        checker.draw(x + 200, y, 40, 40);
        Render.push();
        Render.translate(x + 220, y + 80);
        Render.scale(20);
        if (pyramid != null) {
            Render.mesh(pyramid);
        }
        Render.pop();
    }

    /** A four-sided pyramid, 1 wide and 1 tall, with a color per face. */
    private static Mesh pyramid() {
        Mesh.Builder b = Mesh.builder(Mesh.Primitive.TRIANGLES, Mesh.Format.COLOR);
        double[][] base = {{-0.5, -0.5}, {0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}};
        int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55};
        for (int i = 0; i < 4; i++) {
            double[] a = base[i];
            double[] c = base[(i + 1) % 4];
            b.vertex(a[0], -0.5, a[1]).color(colors[i]);
            b.vertex(c[0], -0.5, c[1]).color(colors[i]);
            b.vertex(0, 0.5, 0).color(0xFFFFFFFF);
        }
        return b.build();
    }

    @Override
    public void onCustomPayload(String channel, byte[] data) {
        payloads++;
        lastChannel = channel;
        if (payloads <= 5) {
            System.out.println(
                    "[hooktest] plugin packet: " + channel + " (" + data.length + " bytes)");
        }
    }

    @Override
    public Object onPlaySound(Object sound) {
        sounds++;
        if (sounds <= 3) {
            System.out.println("[hooktest] sound: " + ((ISound) sound).getSoundLocation());
        }
        return sound;
    }

    @Override
    public String onSendChat(String message) {
        if (!message.equalsIgnoreCase("/hooktest")) {
            return message;
        }
        boolean sent = Net.sendPayload("stein:test", "hello".getBytes(StandardCharsets.UTF_8));
        String line =
                "hooktest: "
                        + frames
                        + " world frames, "
                        + payloads
                        + " payloads (last "
                        + lastChannel
                        + "), "
                        + sounds
                        + " sounds, payload sent: "
                        + sent;
        System.out.println("[hooktest] " + line);
        Minecraft.getMinecraft()
                .ingameGUI
                .getChatGUI()
                .printChatMessage(new ChatComponentText(line));
        return null; // client command: does not go to the server
    }
}
