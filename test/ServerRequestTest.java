/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

/**
 * What a server asks to turn off (ServerRequest) and the update notice (ModUpdates.find), without
 * the game. Exits 0 when all is well, 1 on failure. Runs on every loader build.
 */
public final class ServerRequestTest {

    private static int failures;

    public static void main(String[] args) {
        ServerRequest r =
                ServerRequest.parse(
                        "{\"disable\":[{\"mod\":\"steinhud\",\"feature\":\"minimap\"},"
                                + "{\"mod\":\"steincombat\"},"
                                + "{\"feature\":\"noMod\"},7,"
                                + "{\"mod\":\"steinhud\",\"feature\":\"target\"}]}");
        check("whole mods", "[steincombat]", r.mods.toString());
        check("features", "{steinhud=[minimap, target]}", r.features.toString());
        check("an empty list gives back", "[]", ServerRequest.parse("{\"disable\":[]}").mods + "");
        check("no list gives back", "[]", ServerRequest.parse("{}").mods + "");
        check("not an object", "null", String.valueOf(ServerRequest.parse("[1]")));
        check("does not parse", "null", String.valueOf(ServerRequest.parse("{\"disable\":[")));

        String index =
                "{\"homepage\":\"https://example.com/hud\",\"mods\":["
                        + "{\"modId\":\"other\",\"version\":\"9.0\"},"
                        + "{\"modId\":\"steinhud\",\"version\":\"0.2.0\",\"loader\":\"0.1.31\"}]}";
        ModUpdates.Update u = ModUpdates.find(index, "steinhud", "0.1.16", "0.1.31");
        check("newer version", "0.2.0", u.version);
        check("page", "https://example.com/hud", u.url);
        check("fits this Loader", "", u.needsLoader);
        u = ModUpdates.find(index, "steinhud", "0.1.16", "0.1.30");
        check("asks for a newer Loader", "0.1.31", u.needsLoader);
        check("already there", "null", "" + ModUpdates.find(index, "steinhud", "0.2.0", "0.1.31"));
        check("ahead of it", "null", "" + ModUpdates.find(index, "steinhud", "0.3.0", "0.1.31"));
        check("another mod's index", "null", "" + ModUpdates.find(index, "x", "0.1", "0.1.31"));
        check("not an index", "null", "" + ModUpdates.find("[", "steinhud", "0.1", "0.1.31"));
        String http =
                "{\"homepage\":\"http://example.com\",\"mods\":[{\"modId\":\"a\",\"version\":\"2\"}]}";
        check("no plain http link", "", ModUpdates.find(http, "a", "1", "0.1.31").url);

        if (failures > 0) {
            System.exit(1);
        }
        System.out.println("ServerRequestTest: ok");
    }

    private static void check(String name, String expected, String got) {
        if (!expected.equals(got)) {
            failures++;
            System.out.println("FAILED " + name + ": expected " + expected + ", got " + got);
        }
    }
}
