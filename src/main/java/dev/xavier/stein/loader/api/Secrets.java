/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A mod's passwords and tokens kept outside the configuration JSON, in {@code config/<id>.secrets}.
 *
 * <p>On Windows each value is encrypted by the system itself (DPAPI, the user data protection): the
 * file only opens on the same Windows account, on the same machine — copied somewhere else, it is
 * useless. On other systems, for now, the value is only scrambled and the file is readable only by
 * its owner: better than plain text in a JSON, but not a vault. Never write the value to the log.
 */
@Experimental(since = "0.1.16")
public final class Secrets {

    private final String id;

    private Secrets(String id) {
        this.id = id;
    }

    public static Secrets of(String modId) {
        return new Secrets(modId);
    }

    /** The stored value, or null (there is none, or it does not open on this account/machine). */
    public String get(String key) {
        String stored = read().get(key);
        if (stored == null) {
            return null;
        }
        try {
            byte[] plain = Host.INSTANCE.unprotect(Base64.getDecoder().decode(stored));
            return plain == null ? null : new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Stores a value (null or empty deletes it). */
    public void put(String key, String value) {
        Map<String, String> all = read();
        if (value == null || value.isEmpty()) {
            all.remove(key);
        } else {
            all.put(
                    key,
                    Base64.getEncoder()
                            .encodeToString(
                                    Host.INSTANCE.protect(value.getBytes(StandardCharsets.UTF_8))));
        }
        write(all);
    }

    public void remove(String key) {
        put(key, null);
    }

    /** The stored keys (to clean up what is left over from something the mod already deleted). */
    public Set<String> keys() {
        return new TreeSet<>(read().keySet());
    }

    /** Is the system vault really protecting (Windows)? False: only scrambled. */
    public static boolean isSystemProtected() {
        return Host.INSTANCE.secretsProtected();
    }

    private File file() {
        return new File(Host.INSTANCE.gameDir(), "config/" + id + ".secrets");
    }

    private Map<String, String> read() {
        return Host.INSTANCE.readStrings(file());
    }

    private void write(Map<String, String> all) {
        try {
            Host.INSTANCE.writeStrings(file(), all);
        } catch (Exception e) {
            Log.of(id).error("could not write " + file().getName(), e);
        }
    }
}
