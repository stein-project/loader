/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Predicate;

/**
 * The Stein registry: the files the project publishes for its programs to read at run time
 * (Stein-Project/registry, served at {@link #DEFAULT_BASE}), instead of baking them into a build.
 * This Loader reads only {@code stein.json} (Discord and the menu news): it says nothing about what
 * a mod can do. Older Loaders also read {@code servers.json} and {@code revoked.json}.
 *
 * <p>Every file comes with an ed25519 signature next to it ({@code <file>.sig}) and is only
 * believed when it matches {@link #PUBLIC_KEY}: what it says ends up as a clickable link in the
 * menu, and a hijacked account or a tampered copy on the way must not be able to put one there. The
 * signed copy is kept in {@code config/steinloader-registry/}, applies at once on the next start
 * (before the network answers, and offline) and is only replaced by another copy with a valid
 * signature.
 *
 * <p>{@code -Dstein.registry=<base>} reads another address (a local copy, for tests); an empty
 * value fetches nothing and keeps the stored copies. {@code -Dsteinloader.noRemote=true} turns the
 * registry off entirely.
 */
final class Registry {

    /** Where the registry is published; the only address of it in the Loader. */
    static final String DEFAULT_BASE = "https://stein-project.github.io/registry/";

    /**
     * The registry's public key (ed25519, the raw 32 bytes in base64). Changing it means a new
     * Loader for everyone: the private key that matches it signs every file.
     */
    static final String PUBLIC_KEY = "exxXasq5SsodR84fDi9gVEs3oRfXXaPVgTn/eK47TUc=";

    /** The DER header of an ed25519 SubjectPublicKeyInfo, before the raw key. */
    private static final byte[] SPKI_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    /** Cap on a file: they are a handful of lines, and there is no reason to download more. */
    private static final int LIMIT = 256 * 1024;

    private static final String BASE = baseAddress();

    private static File dir;

    private Registry() {}

    /** The folder of the stored copies (set once, at game start). */
    static void start(File gameDir) {
        dir = new File(gameDir, "config/steinloader-registry");
    }

    /** Whether the registry is in use at all. */
    static boolean enabled() {
        return !LoaderOptions.NO_REMOTE;
    }

    /** The stored copy of a file (already verified when it was stored), or null. */
    static String cached(String name) {
        if (!enabled() || dir == null) {
            return null;
        }
        File f = new File(dir, name);
        if (!f.isFile()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.info("loader", "registry: stored " + name + " unreadable: " + e);
            return null;
        }
    }

    /**
     * Fetches the files in the background; each one that arrives with a valid signature is handed
     * to its reader (on the fetch thread) and stored when the reader accepts it. A file that does
     * not arrive, arrives without a valid signature or is refused (unknown schema, bad JSON)
     * changes nothing: the stored copy stays in effect, also for the next start.
     */
    static void refresh(Entry... entries) {
        if (!enabled() || BASE.isEmpty()) {
            return;
        }
        Thread t =
                new Thread(
                        () -> {
                            HttpClient http =
                                    HttpClient.newBuilder()
                                            .connectTimeout(Duration.ofSeconds(5))
                                            .followRedirects(HttpClient.Redirect.NORMAL)
                                            .build();
                            for (Entry e : entries) {
                                fetch(http, e);
                            }
                        },
                        "stein-registry");
        t.setDaemon(true);
        t.start();
    }

    /**
     * A registry file and what to do with each verified copy: the reader returns whether it took
     * the copy, and only a copy it took replaces the stored one.
     */
    static final class Entry {
        final String name;
        final Predicate<String> apply;

        Entry(String name, Predicate<String> apply) {
            this.name = name;
            this.apply = apply;
        }
    }

    private static void fetch(HttpClient http, Entry e) {
        try {
            byte[] body = get(http, BASE + e.name);
            byte[] sig = get(http, BASE + e.name + ".sig");
            if (body == null || sig == null) {
                return;
            }
            if (!verify(body, new String(sig, StandardCharsets.US_ASCII))) {
                Log.info("loader", "registry: " + e.name + " has no valid signature, ignored");
                return;
            }
            if (!e.apply.test(new String(body, StandardCharsets.UTF_8))) {
                // Signed but not readable by this version (a newer schema): storing it would
                // leave the next start with nothing, so the last copy this version read stays.
                return;
            }
            if (dir != null) {
                dir.mkdirs();
                // Written beside and moved in one step: a game closed halfway through the write
                // must not leave a truncated copy that the next start would read.
                File tmp = new File(dir, e.name + ".tmp");
                Files.write(tmp.toPath(), body);
                Files.move(
                        tmp.toPath(),
                        new File(dir, e.name).toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (Exception | LinkageError ex) {
            // No network, DNS down, proxy, a file that does not parse: the stored copy stays.
            Log.info(
                    "loader",
                    "registry: " + e.name + " unavailable (keeping the stored copy): " + ex);
        }
    }

    private static byte[] get(HttpClient http, String url) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("User-Agent", "SteinLoader/" + Loader.VERSION)
                        .GET()
                        .build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) {
                Log.info("loader", "registry: " + url + " answered " + res.statusCode());
                return null;
            }
            // Read up to the cap and no further: a huge answer is refused without holding it all.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                if (out.size() + n > LIMIT) {
                    Log.info("loader", "registry: " + url + " too large, ignored");
                    return null;
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** Does the signature (base64 of the raw ed25519 signature) match the bytes and our key? */
    static boolean verify(byte[] data, String signature) {
        try {
            byte[] raw = Base64.getDecoder().decode(PUBLIC_KEY);
            byte[] spki = new byte[SPKI_PREFIX.length + raw.length];
            System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
            System.arraycopy(raw, 0, spki, SPKI_PREFIX.length, raw.length);
            PublicKey key =
                    KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(Base64.getDecoder().decode(signature.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    /** The registry's address, always ending in "/"; "" when the option turns fetching off. */
    private static String baseAddress() {
        String base = System.getProperty("stein.registry", DEFAULT_BASE).trim();
        if (base.isEmpty()) {
            return "";
        }
        return base.endsWith("/") ? base : base + "/";
    }
}
