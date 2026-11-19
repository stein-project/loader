/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Opt;

/**
 * Every {@code -D} option the Loader reads, in one place. Each field is read once, when the class
 * initializes, through the Bridge's {@link Opt}. {@code tools/Options.java} lists them in {@code
 * docs/OPTIONS.md} from the Javadoc below ({@code ./gradlew options}; {@code ./gradlew build} fails
 * when the file is out of date).
 *
 * <p>The API's own {@code -Dstein.debug} option is read in {@code api.Log}: the API cannot depend
 * on the Loader.
 */
public final class LoaderOptions {

    private LoaderOptions() {}

    /**
     * {@code -Dsteinloader.disable=id1,id2}: disables these mods for this run only, without
     * changing config/steinloader.json (default: unset).
     */
    public static final String DISABLE = Opt.text("steinloader.disable", null);

    /**
     * {@code -Dsteinloader.noRemote=true}: turns the Stein registry off entirely, including the
     * stored copies: no Discord button, no update warning, no message, no server rules, no revoked
     * mods (default: off). {@code -Dstein.registry=<base>} reads the registry from another address
     * instead ({@link Registry}).
     */
    public static final boolean NO_REMOTE = Opt.flag("steinloader.noRemote");

    /**
     * {@code -Dsteinloader.noAnnounce=true}: does not tell the server which mods are loaded when
     * joining (default: off, the list is sent on the {@code SteinLoader} channel).
     */
    public static final boolean NO_ANNOUNCE = Opt.flag("steinloader.noAnnounce");

    /**
     * {@code -Dsteinloader.noSteinSync=true}: turns Stein Sync off on this client: no mark in the
     * server list and no answer to a Stein Sync proxy's Hello, so every connection stays in the
     * vanilla mode (default: off, Stein Sync is on).
     */
    public static final boolean NO_STEIN_SYNC = Opt.flag("steinloader.noSteinSync");

    /**
     * {@code -Dsteinloader.noQuic=true}: never joins over QUIC (stein-sync's docs/protocol.md
     * section 7); every connection is TCP, as before the transport existed (default: off, QUIC is
     * tried when a server offers it). The player's setting {@code steinSyncQuic} in the loader
     * config does the same for good.
     */
    public static final boolean NO_QUIC = Opt.flag("steinloader.noQuic");

    /**
     * {@code -Dsteinloader.steinSyncSnap=true}: under a Stein Sync proxy's movement authority, a
     * correction moves the picture at once with the player (default: off, a correction of a few
     * centimetres or more is drawn as a short glide over the rest of the tick; the player's state
     * is corrected at once either way).
     */
    public static final boolean STEIN_SYNC_SNAP = Opt.flag("steinloader.steinSyncSnap");

    /**
     * {@code -Dsteinloader.diag.syncNudge=D}: test, under a Stein Sync proxy's movement authority,
     * pushes the player D blocks along X once, after the movement of Tick {@code syncNudgeAtTick}:
     * a divergence the proxy must correct (default: 0, never).
     */
    public static final double DIAG_SYNC_NUDGE = Opt.decimal("steinloader.diag.syncNudge", 0);

    /**
     * {@code -Dsteinloader.diag.syncNudgeAtTick=N}: the Tick of {@code syncNudge} (default 200: ten
     * seconds after the Accept).
     */
    public static final int DIAG_SYNC_NUDGE_AT =
            Opt.integer("steinloader.diag.syncNudgeAtTick", 200);

    /**
     * {@code -Dsteinloader.quicTransport=kwik|native}: which QUIC implementation a join over QUIC
     * uses (default: kwik, in Java; native is the Bridge's library, kept to compare the two).
     */
    public static final String QUIC_TRANSPORT = Opt.text("steinloader.quicTransport", "kwik");

    /**
     * {@code -Dsteinloader.diag.quicRebindAtSec=N}: test, a QUIC connection moves to a new local
     * port after N seconds of Ticks, as an address change would (default: 0, never).
     */
    public static final int DIAG_QUIC_REBIND_AT_SEC =
            Opt.integer("steinloader.diag.quicRebindAtSec", 0);

    /**
     * {@code -Dsteinloader.diag.editorOptions=id}: test, the HUD editor opens the options of this
     * element directly (default: unset).
     */
    public static final String EDITOR_OPTIONS = Opt.text("steinloader.diag.editorOptions", null);

    /**
     * {@code -Dsteinloader.debugChest=true}: logs each chest the server opens and whether a virtual
     * chest was requested (default: off).
     */
    public static final boolean DEBUG_CHEST = Opt.flag("steinloader.debugChest");

    /**
     * {@code -Dsteinloader.debugSteinSync=true}: logs the Stein Sync client messages as they go:
     * each attack put in a Tick, each BlockAction, and each block change held and released
     * (default: off).
     */
    public static final boolean DEBUG_STEIN_SYNC = Opt.flag("steinloader.debugSteinSync");
}
