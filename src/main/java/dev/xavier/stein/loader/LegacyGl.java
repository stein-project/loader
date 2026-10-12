/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mods that call old OpenGL directly. Stein GFX runs in a core context (4.1+), where a
 * fixed-function call that reaches the driver (glNewList, glBegin...) does not exist: LWJGL aborts
 * the whole JVM at once, with no report. Such a mod is left out, listed as incompatible with Stein
 * GFX, instead of closing the game.
 *
 * <p>It checks the classes as they will load (after ModPatch, which already turns part of the old
 * calls into safe ones). The rule is LWJGL's own: each GL11, GL12... has its GL11C, GL12C... with
 * only the core-profile functions; a call to GLxx.f that GLxxC does not have is fixed-function.
 */
final class LegacyGl {

    private static final Pattern GL = Pattern.compile("org/lwjgl/opengl/GL[1-4][0-9]");

    /**
     * The functions of each GLxxC (name + descriptor), read once; {@link #MISSING} when the class
     * does not exist, so that it is not looked up again on every call.
     */
    private static final Map<String, Set<String>> CORE = new HashMap<>();

    /** Marks a GLxx without a GLxxC: compared by identity, never by content. */
    private static final Set<String> MISSING = Collections.unmodifiableSet(new HashSet<>());

    private LegacyGl() {}

    /** The mod's first call to a function the core profile lacks ("GL11.glNewList"), or null. */
    static String firstMissing(File jar) throws IOException {
        String[] found = {null};
        try (ZipFile z = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = z.entries();
            while (entries.hasMoreElements() && found[0] == null) {
                ZipEntry e = entries.nextElement();
                if (!e.getName().endsWith(".class")) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = z.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
                new ClassReader(bytes)
                        .accept(
                                new ClassVisitor(Opcodes.ASM9) {
                                    @Override
                                    public MethodVisitor visitMethod(
                                            int access,
                                            String name,
                                            String desc,
                                            String signature,
                                            String[] exceptions) {
                                        return new MethodVisitor(Opcodes.ASM9) {
                                            @Override
                                            public void visitMethodInsn(
                                                    int opcode,
                                                    String owner,
                                                    String method,
                                                    String mdesc,
                                                    boolean itf) {
                                                if (found[0] == null
                                                        && GL.matcher(owner).matches()
                                                        && isFixedFunction(owner, method + mdesc)) {
                                                    found[0] =
                                                            owner.substring(
                                                                            owner.lastIndexOf('/')
                                                                                    + 1)
                                                                    + "."
                                                                    + method;
                                                }
                                            }
                                        };
                                    }
                                },
                                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return found[0];
    }

    /** Whether GLxx.member is missing from GLxxC. Without that class, nothing counts as old. */
    private static boolean isFixedFunction(String owner, String member) {
        Set<String> core = CORE.computeIfAbsent(owner, LegacyGl::coreFunctions);
        return core != MISSING && !core.contains(member);
    }

    private static Set<String> coreFunctions(String owner) {
        try (InputStream in =
                LegacyGl.class.getClassLoader().getResourceAsStream(owner + "C.class")) {
            if (in == null) {
                return MISSING;
            }
            Set<String> out = new HashSet<>();
            new ClassReader(in.readAllBytes())
                    .accept(
                            new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(
                                        int access,
                                        String name,
                                        String desc,
                                        String signature,
                                        String[] exceptions) {
                                    out.add(name + desc);
                                    return null;
                                }
                            },
                            ClassReader.SKIP_CODE);
            return out;
        } catch (IOException e) {
            return MISSING;
        }
    }
}
