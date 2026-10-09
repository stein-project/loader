/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "depends" and "optional" rules of steinmod.json (Dependencies), without the game: who gets
 * in, who is refused and why, and the order they start in. Exits 0 when all is well, 1 on failure.
 * Runs on every loader build.
 */
public final class DependenciesTest {

    private static int failures;

    public static void main(String[] args) {
        // Base (b) is requested by everyone; the folder comes in alphabetical order, so "a" (which
        // requires b) comes before b.
        ModInfo a = mod("a", "1.0");
        a.depends.put("b", "0.2");
        ModInfo b = mod("b", "0.3");
        ModInfo c = mod("c", "1.0");
        c.depends.put("a", "1.0"); // cascading: depends on something that depends on b
        ModInfo d = mod("d", "1.0");
        d.optional.put("b", "0.1"); // optional, present and in the right version
        List<ModInfo> ok = Dependencies.resolve(list(a, b, c, d), list(a, b, c, d));
        check("order: requested before the requester", "b a c d", ids(ok));

        ModInfo old = mod("old", "1.0");
        old.depends.put("b", "0.5"); // b is 0.3
        ok = Dependencies.resolve(list(old, b), list(old, b));
        check("old version refuses", "b", ids(ok));
        check("reason for version", "steinloader.reject.depends.old", old.reasonKey());

        ModInfo missing = mod("m", "1.0");
        missing.depends.put("doesnotexist", "1.0");
        ModInfo chain = mod("n", "1.0");
        chain.depends.put("m", "1.0");
        ok = Dependencies.resolve(list(missing, chain), list(missing, chain));
        check("missing refuses, cascading", "", ids(ok));
        check("reason for missing", "steinloader.reject.depends.missing", missing.reasonKey());
        check("cascading reason", "steinloader.reject.depends.missing", chain.reasonKey());

        ModInfo off = mod("off", "1.0"); // in the list, but disabled: not among the enabled ones
        ModInfo needsOff = mod("x", "1.0");
        needsOff.depends.put("off", "1.0");
        ok = Dependencies.resolve(list(needsOff), list(off, needsOff));
        check("disabled dependency refuses", "", ids(ok));
        check("reason for disabled", "steinloader.reject.depends.disabled", needsOff.reasonKey());

        ModInfo opt = mod("opt", "1.0");
        opt.optional.put("absent", "1.0"); // optional and absent: gets in
        ModInfo optOld = mod("opt2", "1.0");
        optOld.optional.put("b", "9.0"); // optional and present, but too old: refused
        ok = Dependencies.resolve(list(opt, optOld, b), list(opt, optOld, b));
        check("absent optional gets in, old optional refuses", "opt b", ids(ok));

        System.out.println(
                failures == 0
                        ? "Dependencies: rules ok"
                        : "Dependencies: " + failures + " failure(s)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static ModInfo mod(String id, String version) {
        return new ModInfo(
                id,
                id,
                version,
                "",
                "",
                new File(id + ".steinmod"),
                Collections.<String>emptyList(),
                false);
    }

    private static List<ModInfo> list(ModInfo... mods) {
        List<ModInfo> l = new ArrayList<>();
        Collections.addAll(l, mods);
        return l;
    }

    private static String ids(List<ModInfo> mods) {
        StringBuilder sb = new StringBuilder();
        for (ModInfo m : mods) {
            sb.append(sb.length() == 0 ? "" : " ").append(m.id);
        }
        return sb.toString();
    }

    private static void check(String what, Object want, Object got) {
        if (!want.equals(got)) {
            failures++;
            System.out.println("FAIL " + what + ": expected '" + want + "', got '" + got + "'");
        }
    }
}
