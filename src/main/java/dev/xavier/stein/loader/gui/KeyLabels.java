/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;

/**
 * The key labels of a Panel page ({@link Option.Key}): finding the one that is waiting for a key
 * (at most one at a time) and handing it the key or mouse button the player presses.
 */
final class KeyLabels {

    private KeyLabels() {}

    /** Did the click go to a key label that was waiting (Option.Key.mousePressed)? */
    static boolean mousePressed(Page current, int button) {
        if (current == null) {
            return false;
        }
        for (Page.Entry e : current.entries()) {
            Option.Key k =
                    e.option instanceof Option.Key ok
                            ? ok
                            : e.option != null ? e.option.key() : null;
            if (k != null && k.isListening()) {
                return k.mousePressed(button);
            }
        }
        return false;
    }

    /** One key label waiting at a time: clicking another gives up the previous one. */
    static void stopListening(Page current, Option.Key except) {
        if (current == null) {
            return;
        }
        for (Page.Entry e : current.entries()) {
            Option.Key k =
                    e.option instanceof Option.Key ok
                            ? ok
                            : e.option != null ? e.option.key() : null;
            if (k != null && k != except && k.isListening()) {
                k.keyPressed(1); // Esc: gives up without changing the key
            }
        }
    }

    /**
     * An Option.Key waiting for a key keeps it — even Esc, which there gives up without closing the
     * Panel. True if one took the key.
     */
    static boolean keyPressed(Page current, int key) {
        if (current == null) {
            return false;
        }
        for (Page.Entry e : current.entries()) {
            if (e.option instanceof Option.Key k && k.keyPressed(key)) {
                return true;
            }
            if (e.option != null && e.option.key() != null && e.option.key().keyPressed(key)) {
                return true;
            }
        }
        return false;
    }
}
