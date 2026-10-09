/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

import dev.xavier.stein.loader.internal.Access;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * internal.Access, outside the game: each kind of access that ModPatch swaps for invokedynamic,
 * against a class whose members are closed off the way the game's are (private, instance final,
 * protected, static).
 *
 * <p>The target class is nested only for convenience: Access does not rely on that (it opens each
 * member through setAccessible, as it does with the game's, which live in another class loader).
 * Exits 0 when everything is right, 1 on failure.
 *
 * <p>Runs on every loader build.
 */
public final class AccessTest {

    /** Like a game class: nothing public. */
    static class Game {
        private int secret = 7;
        private final String session = "launcher";
        protected int guiLeft = 42;
        private static int count = 3;

        protected int ping(int a, int b) {
            return a * 10 + b;
        }

        private static String hidden(String s) {
            return "<" + s + ">";
        }
    }

    private static int failures;

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup me = MethodHandles.lookup();
        String owner = Game.class.getName().replace('.', '/');
        Game g = new Game();

        check(
                "private field",
                7,
                (int)
                        site(
                                        me,
                                        "secret",
                                        MethodType.methodType(int.class, Game.class),
                                        Access.GET,
                                        owner,
                                        "I")
                                .dynamicInvoker()
                                .invoke(g));

        site(
                        me,
                        "session",
                        MethodType.methodType(void.class, Game.class, String.class),
                        Access.PUT,
                        owner,
                        "Ljava/lang/String;")
                .dynamicInvoker()
                .invoke(g, "outra");
        check(
                "instance final written",
                "outra",
                site(
                                me,
                                "session",
                                MethodType.methodType(String.class, Game.class),
                                Access.GET,
                                owner,
                                "Ljava/lang/String;")
                        .dynamicInvoker()
                        .invoke(g));

        check(
                "protected from outside the hierarchy",
                42,
                (int)
                        site(
                                        me,
                                        "guiLeft",
                                        MethodType.methodType(int.class, Game.class),
                                        Access.GET,
                                        owner,
                                        "I")
                                .dynamicInvoker()
                                .invoke(g));

        site(
                        me,
                        "count",
                        MethodType.methodType(void.class, int.class),
                        Access.PUT_STATIC,
                        owner,
                        "I")
                .dynamicInvoker()
                .invoke(9);
        check(
                "private static",
                9,
                (int)
                        site(
                                        me,
                                        "count",
                                        MethodType.methodType(int.class),
                                        Access.GET_STATIC,
                                        owner,
                                        "I")
                                .dynamicInvoker()
                                .invoke());

        check(
                "protected method",
                34,
                (int)
                        site(
                                        me,
                                        "ping",
                                        MethodType.methodType(
                                                int.class, Game.class, int.class, int.class),
                                        Access.VIRTUAL,
                                        owner,
                                        "(II)I")
                                .dynamicInvoker()
                                .invoke(g, 3, 4));

        check(
                "private static method",
                "<x>",
                site(
                                me,
                                "hidden",
                                MethodType.methodType(String.class, String.class),
                                Access.STATIC,
                                owner,
                                "(Ljava/lang/String;)Ljava/lang/String;")
                        .dynamicInvoker()
                        .invoke("x"));

        // The call site's receiver may be a subclass (the type the mod used), and the member
        // belongs to the superclass.
        class Sub extends Game {}
        check(
                "subclass receiver",
                7,
                (int)
                        site(
                                        me,
                                        "secret",
                                        MethodType.methodType(int.class, Sub.class),
                                        Access.GET,
                                        owner,
                                        "I")
                                .dynamicInvoker()
                                .invoke(new Sub()));

        System.out.println(
                failures == 0 ? "Access: 7 cases ok" : "Access: " + failures + " failure(s)");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static CallSite site(
            MethodHandles.Lookup me,
            String name,
            MethodType type,
            int kind,
            String owner,
            String desc)
            throws ReflectiveOperationException {
        return Access.bootstrap(me, name, type, kind, owner, desc);
    }

    private static void check(String what, Object want, Object got) {
        if (!want.equals(got)) {
            failures++;
            System.out.println("FAIL " + what + ": expected " + want + ", got " + got);
        }
    }
}
