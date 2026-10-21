/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import com.google.gson.JsonObject;

import dev.xavier.stein.bridge.compat.GLCompat;
import dev.xavier.stein.bridge.core.Log;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The graphics card this game runs on, written to {@code config/steinloader-gpu.json} on every
 * start: the Stein Client reads it before the next start to apply the registry's {@code
 * compat.json} rules (Java arguments for a given card or driver), since the launcher itself has no
 * graphics context to ask. Only the three OpenGL strings and the system: nothing that identifies
 * the player.
 */
final class GpuInfo {

    static final String FILE = "config/steinloader-gpu.json";

    private GpuInfo() {}

    /** Writes the file; on the game thread, with the context already created. */
    static void write(File gameDir) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("vendor", GLCompat.glGetString(GL11.GL_VENDOR));
            o.addProperty("renderer", GLCompat.glGetString(GL11.GL_RENDERER));
            o.addProperty("version", GLCompat.glGetString(GL11.GL_VERSION));
            o.addProperty("os", System.getProperty("os.name", ""));
            o.addProperty("arch", System.getProperty("os.arch", ""));
            File f = new File(gameDir, FILE);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception | LinkageError e) {
            Log.info("loader", "could not record the graphics card: " + e);
        }
    }
}
