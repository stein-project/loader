/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.SteinMod;

import net.minecraft.client.resources.I18n;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mod in the Loader's list: a jar in the mods folder, or a part of the game (Minecraft, bridge,
 * Loader).
 */
public final class ModInfo {

    /**
     * State in this run; turning it on or off takes effect on the next start.
     *
     * <p>{@code REJECTED} is a mod the Loader did not even try to load — it asks for a newer
     * version, arrived without a manifest, or came with the old extension. It used to simply not
     * show up, and whoever installed it kept looking for where the mod had gone; now it stays in
     * the list, grayed out, with the reason written next to it.
     */
    public enum State {
        BUILTIN,
        LOADED,
        DISABLED,
        FAILED,
        REJECTED
    }

    public final String id;
    public String name;
    public String version;
    public String author;

    /** Text from steinmod.json; for the game's parts, a translation key. */
    public String description;

    /** The mod's jar; null for the game's parts. */
    public final File file;

    final List<String> entries;
    final boolean assets;

    /**
     * The Loader applies the bridge's rules to the mod when loading ({@link ModPatch}): applies to
     * a mod that asks for Loader {@link ModScanner#RUNTIME_PATCH} or newer. Older ones arrive
     * already adapted by the build and go in as they are.
     */
    boolean patch;

    /**
     * Mods this one needs, id -> minimum version ({@code depends} in steinmod.json; {@link
     * Dependencies}).
     */
    final Map<String, String> depends = new LinkedHashMap<>();

    /** Mods it uses if they are enabled, id -> minimum version ({@code optional}). */
    final Map<String, String> optional = new LinkedHashMap<>();

    /**
     * Goes in the list the Loader sends to the server ({@code "announce"} in steinmod.json, default
     * true). If false, the mod does not show up for the server; in the player's menu it stays like
     * any other.
     */
    boolean announce = true;

    final List<SteinMod> instances = new ArrayList<>();
    State state;
    String error;

    /** Failure to load with a translatable reason: key and arguments, translated when displayed. */
    private String errorKey;

    private Object[] errorArgs;

    /** Reason for the refusal: translation key and arguments, translated only when displayed. */
    private String reasonKey;

    private Object[] reasonArgs;
    private Boolean config;

    ModInfo(
            String id,
            String name,
            String version,
            String author,
            String description,
            File file,
            List<String> entries,
            boolean assets) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.file = file;
        this.entries = entries;
        this.assets = assets;
    }

    static ModInfo builtIn(
            String id, String name, String version, String author, String descriptionKey) {
        ModInfo m =
                new ModInfo(
                        id,
                        name,
                        version,
                        author,
                        descriptionKey,
                        null,
                        Collections.<String>emptyList(),
                        false);
        m.state = State.BUILTIN;
        return m;
    }

    /** A mod that did not get in, with the reason. */
    static ModInfo rejected(File file, String id, String reasonKey, Object... reasonArgs) {
        return new ModInfo(id, id, "?", "", "", file, Collections.<String>emptyList(), false)
                .reject(reasonKey, reasonArgs);
    }

    /** Refuses an already-read mod: duplicate id, a part that became another mod. */
    ModInfo reject(String key, Object... args) {
        state = State.REJECTED;
        reasonKey = key;
        reasonArgs = args;
        return this;
    }

    /**
     * What the refused mod's manifest said, when it could be read: the list shows name and version
     * anyway.
     */
    ModInfo describe(String name, String version, String author, String description) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        return this;
    }

    public State state() {
        return state;
    }

    public boolean builtIn() {
        return state == State.BUILTIN;
    }

    /** Refused: cannot be enabled or configured, only read the reason. */
    public boolean rejected() {
        return state == State.REJECTED;
    }

    /**
     * Reason for the refusal, in the game's language, or null.
     *
     * <p>Comes out raw (the key and the values) when the translation does not exist yet: the scan
     * happens before the game loads the languages, and an odd line in the log is better than
     * breaking the read of the mods folder.
     */
    public String reason() {
        if (reasonKey == null) {
            return null;
        }
        try {
            String s = I18n.format(reasonKey, reasonArgs);
            if (!s.equals(reasonKey)) {
                return s;
            }
        } catch (Throwable ignored) {
            // language not loaded yet
        }
        return reasonKey + (reasonArgs.length == 0 ? "" : " " + Arrays.toString(reasonArgs));
    }

    /**
     * The translation key of the refusal reason, raw (for whoever checks the rule without the game,
     * such as tests).
     */
    String reasonKey() {
        return reasonKey;
    }

    /** Reason for the failure to load, or null. */
    public String error() {
        if (errorKey != null) {
            try {
                String s = I18n.format(errorKey, errorArgs);
                if (!s.equals(errorKey)) {
                    return s;
                }
            } catch (Throwable ignored) {
                // language not loaded yet
            }
        }
        return error;
    }

    /** Marks the load as failed with a translatable reason. */
    void fail(String key, Object... args) {
        state = State.FAILED;
        errorKey = key;
        errorArgs = args;
        error = key + (args.length == 0 ? "" : " " + Arrays.toString(args));
    }

    /** Whether some class of the mod overrides {@link SteinMod#configScreen}. */
    public boolean hasConfig() {
        if (config == null) {
            boolean has = false;
            for (SteinMod m : instances) {
                try {
                    has |=
                            m.getClass().getMethod("configScreen", Object.class).getDeclaringClass()
                                    != SteinMod.class;
                } catch (NoSuchMethodException e) {
                    // cannot happen: the method belongs to the interface
                }
            }
            config = has;
        }
        return config;
    }

    Object configScreen(Object parent) {
        for (SteinMod m : instances) {
            Object s = m.configScreen(parent);
            if (s != null) {
                return s;
            }
        }
        return null;
    }
}
