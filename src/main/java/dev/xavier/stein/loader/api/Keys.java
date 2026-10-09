/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

/**
 * Keys: the mod's on the Controls screen, the game's, and the keyboard and mouse as they are now.
 *
 * <pre>
 * private Keys.Binding mark;
 * public void afterStartGame() {
 *     mark = Keys.bind("key.mymod.mark", Keys.KEY_M, "key.categories.mymod");
 * }
 * public void onKeyInput() {
 *     while (mark.consumeClick()) { ... }
 * }
 * </pre>
 *
 * <p>Why go through here instead of the game's list: the game reads options.txt inside startGame,
 * before mods register their keys (afterStartGame), so the key has to be added to the options list
 * and receive the already saved code — otherwise the next saveOptions would write the default over
 * the player's choice.
 *
 * <p>The key codes are LWJGL 2's, which the game writes to options.txt ({@link #KEY_NONE} = no
 * key). A mouse button on a key is {@code button - 100} (the middle button is -98).
 */
public final class Keys {

    /** No key. */
    public static final int KEY_NONE = 0;

    /** Escape. */
    public static final int KEY_ESCAPE = 1;

    /** Tab. */
    public static final int KEY_TAB = 15;

    /** The Q key. */
    public static final int KEY_Q = 16;

    /** The E key. */
    public static final int KEY_E = 18;

    /** The R key. */
    public static final int KEY_R = 19;

    /** The T key. */
    public static final int KEY_T = 20;

    /** The Y key. */
    public static final int KEY_Y = 21;

    /** The U key. */
    public static final int KEY_U = 22;

    /** The I key. */
    public static final int KEY_I = 23;

    /** The O key. */
    public static final int KEY_O = 24;

    /** The P key. */
    public static final int KEY_P = 25;

    /** Enter (Return). */
    public static final int KEY_RETURN = 28;

    /** Left Control. */
    public static final int KEY_LCONTROL = 29;

    /** The F key. */
    public static final int KEY_F = 33;

    /** The G key. */
    public static final int KEY_G = 34;

    /** The H key. */
    public static final int KEY_H = 35;

    /** The J key. */
    public static final int KEY_J = 36;

    /** The K key. */
    public static final int KEY_K = 37;

    /** The L key. */
    public static final int KEY_L = 38;

    /** Left Shift. */
    public static final int KEY_LSHIFT = 42;

    /** The Z key. */
    public static final int KEY_Z = 44;

    /** The X key. */
    public static final int KEY_X = 45;

    /** The C key. */
    public static final int KEY_C = 46;

    /** The V key. */
    public static final int KEY_V = 47;

    /** The B key. */
    public static final int KEY_B = 48;

    /** The N key. */
    public static final int KEY_N = 49;

    /** The M key. */
    public static final int KEY_M = 50;

    /** Right Shift. */
    public static final int KEY_RSHIFT = 54;

    /** Left Alt. */
    public static final int KEY_LMENU = 56;

    /** Space. */
    public static final int KEY_SPACE = 57;

    /** The F1 key. */
    public static final int KEY_F1 = 59;

    /** Right Control. */
    public static final int KEY_RCONTROL = 157;

    /** Right Alt (AltGr). */
    public static final int KEY_RMENU = 184;

    /** Insert. */
    public static final int KEY_INSERT = 210;

    /** Game keys (those on Minecraft's own Controls screen). */
    @Experimental(since = "0.1.16")
    public enum GameKey {
        /** Walk forward. */
        FORWARD,
        /** Walk backwards. */
        BACK,
        /** Strafe left. */
        LEFT,
        /** Strafe right. */
        RIGHT,
        /** Jump. */
        JUMP,
        /** Sneak. */
        SNEAK,
        /** Sprint. */
        SPRINT,
        /** Attack/destroy. */
        ATTACK,
        /** Use item/place block. */
        USE,
        /** Pick block. */
        PICK_BLOCK,
        /** Drop item. */
        DROP,
        /** Open/close the inventory. */
        INVENTORY,
        /** Open chat. */
        CHAT,
        /** Open command (chat with /). */
        COMMAND,
        /** List players (Tab). */
        PLAYER_LIST,
        /** Toggle perspective. */
        PERSPECTIVE,
        /** Take screenshot. */
        SCREENSHOT
    }

    /** A key on the Controls screen: the mod's ({@link #bind}) or the game's ({@link #game}). */
    @Experimental(since = "0.1.16")
    public static final class Binding {

        private final Object handle;

        private Binding(Object handle) {
            this.handle = handle;
        }

        /** The key the player chose (see the codes in {@link Keys}). */
        public int key() {
            return Host.INSTANCE.keyCode(handle);
        }

        /**
         * Changes the key (0 = none), as the Controls screen does: it takes effect immediately and
         * is saved in options.txt. It is what an {@link Option#key} in the Panel calls.
         */
        @Experimental(since = "0.1.19")
        public void setKey(int key) {
            Host.INSTANCE.keySet(handle, key);
        }

        /** Pressed right now? */
        public boolean isDown() {
            return Host.INSTANCE.keyIsDown(handle);
        }

        /**
         * A press that has not been seen yet: true once per press (the game counts the presses
         * between one tick and the next). Read it with a while loop in onKeyInput or onTickEnd.
         */
        public boolean consumeClick() {
            return Host.INSTANCE.keyConsume(handle);
        }

        /**
         * Holds the key as if a finger were on it (true), or returns to what the keyboard says
         * (false). It is like an "always sprint": {@code Keys.game(GameKey.SPRINT).hold(true)}.
         */
        public void hold(boolean held) {
            Host.INSTANCE.keyHold(handle, held);
        }

        /** The game's KeyBinding, for whoever needs it. */
        public Object handle() {
            return handle;
        }
    }

    private static final Binding[] GAME = new Binding[GameKey.values().length];

    private Keys() {}

    /** Registers a KeyBinding (game type, as Object) after startGame. */
    public static void register(Object binding) {
        Host.INSTANCE.registerKey(binding);
    }

    /**
     * A new key on the Controls screen. {@code name} and {@code category} are keys of the mod's
     * .lang ("key.mymod.mark", "key.categories.mymod"). Call it in afterStartGame, once per key.
     */
    @Experimental(since = "0.1.16")
    public static Binding bind(String name, int defaultKey, String category) {
        return new Binding(Host.INSTANCE.keyBind(name, defaultKey, category));
    }

    /** A game key. */
    @Experimental(since = "0.1.16")
    public static Binding game(GameKey key) {
        Binding b = GAME[key.ordinal()];
        if (b == null) {
            b = new Binding(Host.INSTANCE.keyGame(key.ordinal()));
            GAME[key.ordinal()] = b;
        }
        return b;
    }

    /**
     * Is the key (keyboard code; negative = mouse button, as in {@link Binding#key}) pressed right
     * now?
     */
    @Experimental(since = "0.1.16")
    public static boolean isDown(int key) {
        return Host.INSTANCE.keyPhysical(key);
    }

    /** Either Shift pressed (the same test as the game's shift+click). */
    @Experimental(since = "0.1.16")
    public static boolean isShiftDown() {
        return isDown(KEY_LSHIFT) || isDown(KEY_RSHIFT);
    }

    /** Either Ctrl pressed (on macOS, Command too, as the game does). */
    @Experimental(since = "0.1.16")
    public static boolean isCtrlDown() {
        return Host.INSTANCE.ctrlDown();
    }

    /** Either Alt pressed. */
    @Experimental(since = "0.1.16")
    public static boolean isAltDown() {
        return isDown(KEY_LMENU) || isDown(KEY_RMENU);
    }

    /** Is the mouse button (0 left, 1 right, 2 middle) pressed right now? */
    @Experimental(since = "0.1.16")
    public static boolean isMouseDown(int button) {
        return Host.INSTANCE.mouseDown(button);
    }

    /**
     * The button of the mouse event in progress, or -1 (movement or wheel). Only valid inside
     * {@link SteinMod#onMouseInput} and {@link SteinMod#onGuiMouseInput}.
     */
    @Experimental(since = "0.1.16")
    public static int mouseButton() {
        return Host.INSTANCE.mouseEventButton();
    }

    /**
     * A click of the mouse button (0 left, 1 right, 2 middle): a press and a release at the end of
     * the mouse event queue, as the physical mouse (or an external autoclick) would do. The game
     * reads them on the next tick, together with and in the order of the real ones: the hit or use,
     * the arm swing, the packets, {@link SteinMod#onMouseInput} (the HUD's click counter) and the
     * game's hit delay — nothing is called from outside. The physical button ({@link #isMouseDown})
     * does not change. Call it on the game thread.
     */
    @Experimental(since = "0.1.22")
    public static void click(int button) {
        Host.INSTANCE.mouseClick(button);
    }

    /**
     * Was the button of the event in progress pressed (true) or released (false)? See {@link
     * #mouseButton}.
     */
    @Experimental(since = "0.1.16")
    public static boolean mousePressed() {
        return Host.INSTANCE.mouseEventPressed();
    }

    /** The wheel in the event in progress: positive up, negative down, 0 for no wheel. */
    @Experimental(since = "0.1.16")
    public static int mouseWheel() {
        return Host.INSTANCE.mouseEventWheel();
    }
}
