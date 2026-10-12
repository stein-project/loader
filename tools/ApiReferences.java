/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Checks that the API mentions nothing besides itself, the JDK and the game: neither the Bridge nor
 * the rest of the Loader. This is what lets stein-api.jar be shipped on its own — the one doing the
 * work is {@code dev.xavier.stein.loader.api.spi.Host}, which the Loader implements. A new
 * reference pointing inward fails the build here, and not after the jar is already in someone's
 * hands.
 *
 * <p>Usage: {@code ApiReferences <classes dir> <api package, e.g. dev/xavier/stein/loader/api>}.
 * Every type the classes mention counts: owners, descriptors, generic signatures, annotations.
 */
public final class ApiReferences {

    private ApiReferences() {}

    public static void main(String[] args) throws IOException {
        Path classes = Path.of(args[0]);
        String api = args[1];
        Set<String> refs = new TreeSet<>();
        Remapper collect =
                new Remapper(Opcodes.ASM9) {
                    @Override
                    public String map(String internalName) {
                        refs.add(internalName);
                        return internalName;
                    }
                };
        int count = 0;
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                new ClassReader(Files.readAllBytes(f))
                        .accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9) {}, collect), 0);
                count++;
            }
        }
        Set<String> bad = new TreeSet<>();
        for (String r : refs) {
            String name = r.replaceFirst("^\\[+L?", "").replaceFirst(";$", "");
            if (name.contains("/")
                    && !name.startsWith("java/")
                    && !name.startsWith("javax/")
                    && !name.startsWith("jdk/")
                    && !name.startsWith("net/minecraft/")
                    && !name.startsWith(api + "/")) {
                bad.add(name);
            }
        }
        if (!bad.isEmpty()) {
            System.err.println(
                    "the API references classes that are not its own, the JDK's or the game's:");
            bad.forEach(b -> System.err.println("  " + b));
            System.exit(1);
        }
        System.out.println("stein-api: " + count + " classes, API, JDK and game only");
    }
}
