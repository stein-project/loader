/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

/**
 * Version comparison in the project's format ("0.1.2"): each part is a number, and a missing part
 * counts as zero ("0.2" is the same as "0.2.0").
 *
 * <p>It exists because the Loader no longer has an "API level" of its own. An API number separate
 * from the version was one more thing to remember to bump — and when it was not bumped, a new mod
 * would load into an old Loader and break at run time. Now the mod asks for the <b>minimum Loader
 * version</b> and the Loader asks for the <b>minimum Bridge version</b>: a single number, the same
 * one that appears in the mod list and in the installer.
 */
final class Versions {

    private Versions() {}

    /**
     * Negative if a is less than b, zero if equal, positive if greater. Text that is not a number
     * counts as zero.
     */
    static int compare(String a, String b) {
        String[] x = split(a);
        String[] y = split(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int va = i < x.length ? number(x[i]) : 0;
            int vb = i < y.length ? number(y[i]) : 0;
            if (va != vb) {
                return va < vb ? -1 : 1;
            }
        }
        return 0;
    }

    /** Is the first version at least the second? */
    static boolean atLeast(String version, String minimum) {
        return compare(version, minimum) >= 0;
    }

    private static String[] split(String v) {
        return (v == null ? "" : v.trim()).split("[.\\-+]");
    }

    private static int number(String s) {
        int n = 0;
        for (int i = 0; i < s.length() && Character.isDigit(s.charAt(i)); i++) {
            n = n * 10 + (s.charAt(i) - '0');
        }
        return n;
    }
}
