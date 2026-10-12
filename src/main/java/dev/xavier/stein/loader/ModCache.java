/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Each mod's classes come from a .jar copy of the .steinmod, in steinloader/cache (outside the mods
 * folder, which Forge scans). Mods that look for their own classes in the file they came from only
 * recognize a jar by its extension (.jar or .zip); with .steinmod they would not find their own
 * classes and stop halfway through initialization. The resources (assets) are still read from the
 * .steinmod itself.
 *
 * <p>A mod that asks for the Bridge's rules ({@link ModInfo#patch}) is adapted when it becomes that
 * copy ({@link ModPatch}): the adaptation runs once, and is only redone when the mod or the rules
 * change — which the stamp next to the copy (base.jar.stamp: sha-256 of the mod and the rules)
 * tells.
 */
final class ModCache {

    private ModCache() {}

    /**
     * URL for the class loader: the .jar copy (redone when the .steinmod changes) or, without it,
     * the file itself.
     *
     * @throws IOException when the mod needs to be adapted and could not be: loading without the
     *     rules would draw wrong, so it does not get in
     */
    static URL url(File mod, boolean patch) throws IOException {
        String name = mod.getName();
        String base =
                name.toLowerCase(Locale.ROOT).endsWith(ModScanner.EXT)
                        ? name.substring(0, name.length() - ModScanner.EXT.length())
                        : name;
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "steinloader/cache");
        File copy = new File(dir, base + ".jar");
        if (patch) {
            return patched(mod, dir, copy);
        }
        try {
            if (!(copy.isFile()
                    && copy.length() == mod.length()
                    && copy.lastModified() == mod.lastModified())) {
                Files.createDirectories(dir.toPath());
                Files.copy(
                        mod.toPath(),
                        copy.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            return copy.toURI().toURL();
        } catch (IOException | RuntimeException e) {
            // Another game open with the copy in use, or a folder without permission: the file
            // itself still loads.
            Log.info("loader", name + ": no .jar copy (" + e + "), loading the file itself");
            return mod.toURI().toURL();
        }
    }

    private static URL patched(File mod, File dir, File copy) throws IOException {
        ModPatch rules = ModPatch.get();
        // By content, not by date: the installer re-downloading the same file does not redo the
        // adaptation.
        String want = ModPatch.sha256(Files.readAllBytes(mod.toPath())) + " " + rules.stamp();
        Path stamp = new File(dir, copy.getName() + ".stamp").toPath();
        if (copy.isFile()
                && Files.isRegularFile(stamp)
                && want.equals(
                        new String(Files.readAllBytes(stamp), StandardCharsets.UTF_8).trim())) {
            return copy.toURI().toURL();
        }
        Files.createDirectories(dir.toPath());
        // Written alongside and swapped in at once: a game closed halfway leaves no half copy
        // marked as ready.
        Path tmp = Files.createTempFile(dir.toPath(), copy.getName(), ".tmp");
        try {
            long t0 = System.nanoTime();
            rules.transformJar(mod.toPath(), tmp);
            Files.move(tmp, copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.write(stamp, want.getBytes(StandardCharsets.UTF_8));
            Log.info(
                    "loader",
                    mod.getName()
                            + " adapted to the Bridge in "
                            + (System.nanoTime() - t0) / 1_000_000
                            + " ms");
        } finally {
            Files.deleteIfExists(tmp);
        }
        return copy.toURI().toURL();
    }
}
