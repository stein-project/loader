/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.internal;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Mod access to a non-public member of the game: the other side of {@code ModPatch}.
 *
 * <p>The mod compiles against the game with private fields and methods opened (the SDK and the mod
 * build assemble that jar), so it writes {@code overlay.header} as if it were public. In the real
 * game the field stays private, and the JVM would reject the instruction; ModPatch swaps it for an
 * invokedynamic that lands here <b>once</b>, on first use. From here comes a constant MethodHandle
 * for the member, and from then on the JIT treats it like direct access — without the per-call cost
 * of reflection, which is what mods paid before.
 *
 * <p>Not for mods to call: ModPatch writes the call. That is why it sits in {@code internal},
 * outside stein-api.jar, but with its name kept (the adapted mod's bytecode cites it).
 */
public final class Access {

    // What the original instruction did (same numbering as ModPatch).
    /** Instance field read. */
    public static final int GET = 0;

    /** Instance field write. */
    public static final int PUT = 1;

    /** Static field read. */
    public static final int GET_STATIC = 2;

    /** Static field write. */
    public static final int PUT_STATIC = 3;

    /** Instance method call. */
    public static final int VIRTUAL = 4;

    /** Static method call. */
    public static final int STATIC = 5;

    private Access() {}

    /**
     * Links the call site to the member.
     *
     * @param name member name
     * @param type the original instruction's stack: receiver (if any), arguments and return
     * @param kind {@link #GET}, {@link #PUT}, ...
     * @param owner the class that declares the member, with slashes
     * @param desc member descriptor (field or method)
     */
    public static CallSite bootstrap(
            MethodHandles.Lookup caller,
            String name,
            MethodType type,
            int kind,
            String owner,
            String desc)
            throws ReflectiveOperationException {
        ClassLoader cl = caller.lookupClass().getClassLoader();
        Class<?> c = Class.forName(owner.replace('/', '.'), false, cl);
        MethodHandle h;
        if (kind <= PUT_STATIC) {
            // By name AND type: the obfuscated game has fields that share a name (Vec3i.a is both x
            // and
            // a static constant).
            Field f =
                    field(
                            c,
                            name,
                            MethodType.fromMethodDescriptorString("()" + desc, cl).returnType());
            f.setAccessible(true);
            // unreflectSetter accepts an instance final field after setAccessible
            // (Minecraft.session, for example).
            h =
                    kind == GET || kind == GET_STATIC
                            ? caller.unreflectGetter(f)
                            : caller.unreflectSetter(f);
        } else {
            h = caller.unreflect(method(c, name, MethodType.fromMethodDescriptorString(desc, cl)));
        }
        return new ConstantCallSite(h.asType(type));
    }

    private static Field field(Class<?> c, String name, Class<?> type) throws NoSuchFieldException {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name) && f.getType() == type) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + name + " " + type.getName());
    }

    private static Method method(Class<?> c, String name, MethodType want)
            throws NoSuchMethodException {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)
                    && m.getReturnType() == want.returnType()
                    && Arrays.equals(m.getParameterTypes(), want.parameterArray())) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(c.getName() + "." + name + want);
    }
}
