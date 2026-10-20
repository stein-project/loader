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
     * {@code -Dsteinloader.vanillaFont=true}: the Loader's menus use Minecraft's font instead of
     * Stein's (default: off).
     */
    public static final boolean VANILLA_FONT = Opt.flag("steinloader.vanillaFont");

    /**
     * {@code -Dsteinloader.hudWeight=SemiBold|Bold|ExtraBold}: the weight of the HUD font, for
     * comparison (default {@code Bold}).
     */
    public static final String HUD_WEIGHT = Opt.text("steinloader.hudWeight", "Bold");

    /**
     * {@code -Dsteinloader.hudFontSize=N}: the body size of the HUD font, for comparison (default
     * 8.6).
     */
    public static final float HUD_FONT_SIZE = (float) Opt.decimal("steinloader.hudFontSize", 8.6);

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
}
