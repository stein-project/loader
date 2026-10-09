/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.io.File;

/**
 * A mod's settings in a JSON file, {@code config/<file>.json} in the game folder — the load and
 * save code that every mod used to write by hand.
 *
 * <p>The type is a plain class with fields and a no-argument constructor, which provides the
 * defaults:
 *
 * <pre>
 * static final class Config { boolean enabled = true; String color = "c"; int distance = 16; }
 * static final ModConfig&lt;Config&gt; CONFIG = ModConfig.of("mymod", Config.class);
 * ... CONFIG.get().enabled ...  CONFIG.saveSoon();
 * </pre>
 *
 * <ul>
 *   <li><b>Loads field by field</b>: what is missing from the file stays at the default, and a
 *       value that cannot be read (wrong type, garbage) goes back to the default for that field
 *       only — the rest of the file stands. A file that is not JSON is kept aside ({@code .bad})
 *       and the mod starts from the defaults.
 *   <li><b>Saves without corrupting</b>: writes to a temporary file and swaps it in at once; a
 *       crash midway does not leave half a JSON.
 *   <li>{@link #saveSoon} merges several changes (each keystroke in a text field) into a single
 *       save, one second later.
 * </ul>
 */
@Experimental(since = "0.1.16")
public final class ModConfig<T> {

    private final String name;
    private final Class<T> type;
    private final Log log;
    private T value;
    private boolean pending;

    private ModConfig(String name, Class<T> type) {
        this.name = name;
        this.type = type;
        this.log = Log.of(name);
        this.value = load();
    }

    /** {@code config/<name>.json}; already loaded. */
    public static <T> ModConfig<T> of(String name, Class<T> type) {
        return new ModConfig<>(name, type);
    }

    /** The file. */
    public File file() {
        return new File(Host.INSTANCE.gameDir(), "config/" + name + ".json");
    }

    /** The current values (the same object until a {@link #reset} or {@link #reload}). */
    public T get() {
        return value;
    }

    /** Resets everything to the defaults and saves. */
    public T reset() {
        value = defaults();
        save();
        return value;
    }

    /** Reads the file again. */
    public T reload() {
        value = load();
        return value;
    }

    /** Saves now. */
    public void save() {
        pending = false;
        try {
            Host.INSTANCE.writeJson(file(), value, type);
        } catch (Exception e) {
            log.error("could not save " + file(), e);
        }
    }

    /** Saves one second from now, merging the changes in the meantime into a single save. */
    public void saveSoon() {
        if (pending) {
            return;
        }
        pending = true;
        Tasks.later(
                20,
                () -> {
                    if (pending) {
                        save();
                    }
                });
    }

    private T defaults() {
        try {
            var c = type.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(type + " needs a no-argument constructor", e);
        }
    }

    private T load() {
        return Host.INSTANCE.readConfig(file(), type, defaults(), name);
    }
}
