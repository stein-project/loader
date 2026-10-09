/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.SteinBridge;
import dev.xavier.stein.bridge.core.CoreOptions;
import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.loader.api.SteinMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Finds the mods in the mods folder, decides which ones may run and starts them in a class loader
 * of their own, joining the assets of those that bring some to the game's resource packs.
 */
final class ModLoading {

    /** What starting the enabled mods produced. */
    record Loaded(List<SteinMod> instances, List<ModInfo> owners, List<File> packs) {}

    private ModLoading() {}

    /**
     * Scans the mods folder, adds every jar found to {@code all} and returns those that are neither
     * rejected nor disabled.
     */
    static List<ModInfo> scanAndFilter(
            File dir, List<ModInfo> all, Set<String> disabled, String loaderVersion) {
        Set<String> ids = new HashSet<>();
        for (ModInfo m : all) {
            ids.add(m.id);
        }

        boolean oldBridge = !Versions.atLeast(SteinBridge.VERSION, Loader.MIN_BRIDGE);
        if (oldBridge) {
            Log.info(
                    "loader",
                    SteinBridge.NAME
                            + " "
                            + SteinBridge.VERSION
                            + " is older than the "
                            + Loader.MIN_BRIDGE
                            + " that Stein Loader "
                            + loaderVersion
                            + " requires: no mod will be loaded");
        }
        List<ModInfo> enabled = new ArrayList<>();
        for (ModInfo m : ModScanner.scan(dir, loaderVersion)) {
            if ("steinaltmanager".equals(m.id) || "steinreconnect".equals(m.id)) {
                // Both became Connect in 0.1.0, which reads each one's configuration once.
                m.reject("steinloader.reject.merged", "Connect");
            } else if ("steinlens".equals(m.id)) {
                // Up to 0.0.12 Lens was a mod; now it belongs to the Bridge (Video settings).
                m.reject("steinloader.reject.merged", SteinBridge.NAME);
            } else if (!m.rejected() && !ids.add(m.id)) {
                m.reject("steinloader.reject.duplicate", m.id);
            } else if (oldBridge && !m.rejected()) {
                m.reject("steinloader.reject.bridge", Loader.MIN_BRIDGE, SteinBridge.VERSION);
            }
            all.add(m);
            if (m.rejected()) {
                continue;
            }
            if (disabled.contains(m.id)) {
                m.state = ModInfo.State.DISABLED;
                Log.info("loader", "disabled: " + m.id);
            } else {
                enabled.add(m);
            }
        }
        return enabled;
    }

    /**
     * Creates the class loader and one instance of every entry of each enabled mod. A mod that
     * cannot start is marked FAILED and left out; {@code enabled} must already be in dependency
     * order.
     */
    static Loaded instantiate(List<ModInfo> enabled, List<ModInfo> all) {
        ClassLoader cl = classLoader(enabled);
        List<SteinMod> instances = new ArrayList<>();
        List<ModInfo> owners =
                new ArrayList<>(); // the owner of each instance, for per-mod timing (ModTimes)
        List<File> packs = new ArrayList<>();
        File self = ownLocation();
        if (self != null) {
            packs.add(self);
        }
        for (ModInfo m : enabled) {
            try {
                if (m.entries.isEmpty()) {
                    m.fail("steinloader.fail.noentry");
                    Log.info("loader", m.id + " failed to load: no entry in steinmod.json");
                    continue;
                }
                String missing = unloadedDependency(m, all);
                if (missing != null) {
                    // The dependency started earlier (the order follows the dependencies) and
                    // failed: this one cannot run without it.
                    m.fail("steinloader.fail.dependency", missing);
                    Log.info(
                            "loader",
                            m.id
                                    + " failed to load: depends on "
                                    + missing
                                    + ", which did not load");
                    continue;
                }
                for (String e : m.entries) {
                    m.instances.add(
                            (SteinMod)
                                    Class.forName(e, true, cl)
                                            .getDeclaredConstructor()
                                            .newInstance());
                }
                m.state = ModInfo.State.LOADED;
                instances.addAll(m.instances);
                for (int k = 0; k < m.instances.size(); k++) {
                    owners.add(m);
                }
                if (m.assets) {
                    packs.add(m.file);
                }
                Log.info(
                        "loader",
                        "loaded: " + m.id + " " + m.version + " (" + m.file.getName() + ")");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                m.instances.clear();
                m.state = ModInfo.State.FAILED;
                m.error = e.toString();
                Log.error("loader", m.id + " failed to load", e);
            }
        }
        return new Loaded(instances, owners, packs);
    }

    /** The first dependency of the mod that is not in the LOADED state, or null if all are. */
    private static String unloadedDependency(ModInfo m, List<ModInfo> all) {
        for (String dep : m.depends.keySet()) {
            ModInfo d = byId(all, dep);
            if (d == null || d.state != ModInfo.State.LOADED) {
                return dep;
            }
        }
        return null;
    }

    /** The mod in the list with this id (the first, if repeated), or null. */
    static ModInfo byId(List<ModInfo> all, String id) {
        for (ModInfo m : all) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /**
     * The mods' class loader. Whoever cannot get in (adapting to the bridge failed) leaves the
     * list, as FAILED.
     */
    private static ClassLoader classLoader(List<ModInfo> mods) {
        List<URL> urls = new ArrayList<>();
        for (Iterator<ModInfo> it = mods.iterator(); it.hasNext(); ) {
            ModInfo m = it.next();
            try {
                // A .jar copy: mods that scan themselves only recognize a jar by its extension.
                URL url = ModCache.url(m.file, m.patch);
                // With Stein GFX asked for, a mod that calls old OpenGL directly would bring the
                // game down: it stays out, in the list. The window does not exist yet, so this is
                // what was asked for (CoreOptions.GFX), not whether the core context came.
                String old = CoreOptions.GFX ? LegacyGl.firstMissing(new File(url.toURI())) : null;
                if (old != null) {
                    m.reject("steinloader.reject.legacyGl", old);
                    Log.info(
                            "loader",
                            m.id
                                    + " left out: it calls old OpenGL ("
                                    + old
                                    + "), which Stein GFX does not have");
                    it.remove();
                    continue;
                }
                urls.add(url);
            } catch (IOException | URISyntaxException | RuntimeException e) {
                Log.info("loader", m.id + " failed to load: " + e);
                if (m.patch) {
                    m.state = ModInfo.State.FAILED;
                    m.error = e.toString();
                    it.remove();
                }
            }
        }
        return new URLClassLoader(
                "steinloader-mods", urls.toArray(new URL[0]), Loader.class.getClassLoader());
    }

    /**
     * The jar (or directory) of the Loader itself: it carries the textures and the menu
     * translations.
     */
    private static File ownLocation() {
        try {
            return new File(
                    Loader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | RuntimeException e) {
            Log.info("loader", "Loader resources unavailable: " + e);
            return null;
        }
    }

    /**
     * They join the default packs (Minecraft.defaultResourcePacks) before the first resource load.
     */
    @SuppressWarnings("unchecked")
    static void addResourcePacks(List<File> files) {
        try {
            List<IResourcePack> packs = Minecraft.getMinecraft().defaultResourcePacks;
            for (File file : files) {
                packs.add(
                        file.isDirectory()
                                ? new FolderResourcePack(file)
                                : new FileResourcePack(file));
            }
        } catch (RuntimeException | LinkageError e) {
            Log.info("loader", "mod resource packs: " + e);
        }
    }
}
