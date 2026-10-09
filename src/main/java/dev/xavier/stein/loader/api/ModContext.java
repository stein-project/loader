/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * What the Loader gives each mod — what {@code JavaPlugin} gives a Spigot plugin: id, version, log,
 * folder and configuration, without the mod building a file path or a log prefix.
 *
 * <pre>
 * public final class MyMod implements SteinMod {
 *     private final ModContext ctx = ModContext.of(this);
 *     private final ModConfig&lt;Config&gt; config = ctx.config(Config.class);
 *     ... ctx.log().info("ready"); ...
 * }
 * </pre>
 */
@Experimental(since = "0.1.16")
public final class ModContext {

    private final String id;
    private final Log log;
    private final Map<Class<?>, ModConfig<?>> configs = new HashMap<>();

    private ModContext(String id) {
        this.id = id;
        this.log = Log.of(id);
    }

    /**
     * The context of the mod that owns this instance (the entry class, the one in steinmod.json's
     * {@code entry}). Can be called in the constructor, in a field.
     */
    public static ModContext of(SteinMod mod) {
        String id = Host.INSTANCE.modIdOf(mod);
        if (id == null) {
            throw new IllegalArgumentException(
                    mod.getClass().getName() + " is not the entry of a loaded mod");
        }
        return new ModContext(id);
    }

    /**
     * The mod's id, from steinmod.json.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * The mod's version, from steinmod.json.
     *
     * @return the version
     */
    public String version() {
        return Host.INSTANCE.modVersion(id);
    }

    /**
     * The mod's log ({@link Log#of} with its id).
     *
     * @return the log
     */
    public Log log() {
        return log;
    }

    /**
     * {@code config/<id>.json}, the file of {@link #config}: for a mod that already has its own
     * configuration reader and only needs the right place.
     */
    public File configFile() {
        return new File(Host.INSTANCE.gameDir(), "config/" + id + ".json");
    }

    /**
     * {@code config/<id>/}, created on the first call: for what does not fit in the configuration
     * JSON.
     */
    public File folder() {
        File f = new File(Host.INSTANCE.gameDir(), "config/" + id);
        f.mkdirs();
        return f;
    }

    /**
     * The mod's configuration in {@code config/<id>.json} (see {@link ModConfig}); one per type.
     */
    @SuppressWarnings("unchecked")
    public <T> ModConfig<T> config(Class<T> type) {
        return (ModConfig<T>) configs.computeIfAbsent(type, t -> ModConfig.of(id, type));
    }

    /** The text for the key in the player's language (see {@link Lang}). */
    public String tr(String key, Object... args) {
        return Lang.tr(key, args);
    }

    /** Stored in the system vault, per mod (see {@link Secrets}). */
    public Secrets secrets() {
        return Secrets.of(id);
    }
}
