/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The mod API does not break a mod compiled against it unless someone decides that it should.
 *
 * <p>Reads the public and protected signatures of the classes in dev.xavier.stein.loader.api
 * (except spi, which only the Loader implements) and compares them with the list kept in the
 * repository (api/stein-api.txt):
 *
 * <ul>
 *   <li>what was removed or changed (type, static, final, abstract, visibility) breaks whoever used
 *       it: the build stops;
 *   <li>a new abstract method on a class or interface that already existed breaks whoever
 *       implements it (HudElement, Option): the build stops — add a default;
 *   <li>what was added is simply appended to the list;
 *   <li>what carries {@code @Experimental} may change or go away without stopping the build.
 * </ul>
 *
 * With {@code --update}, it writes the current list: that is how a break (or an addition) becomes a
 * recorded decision.
 *
 * <p>Usage: java -cp asm.jar tools/ApiCheck.java &lt;classes&gt; &lt;list.txt&gt; [--update]
 */
public final class ApiCheck {

    private static final String EXPERIMENTAL = "Ldev/xavier/stein/loader/api/Experimental;";

    public static void main(String[] args) throws IOException {
        Path classes = Paths.get(args[0]);
        Path baseline = Paths.get(args[1]);
        boolean update = args.length > 2 && args[2].equals("--update");

        Map<String, String> now = readCurrent(classes);
        List<String> lines = new ArrayList<>();
        now.forEach((k, v) -> lines.add(k + " " + v));

        if (update || !Files.exists(baseline)) {
            Files.createDirectories(baseline.getParent());
            Files.write(baseline, lines, StandardCharsets.UTF_8);
            System.out.println(
                    "api: list written (" + lines.size() + " signatures) at " + baseline);
            return;
        }
        Map<String, String> before = readBaseline(baseline);

        List<String> broken = new ArrayList<>();
        List<String> added = new ArrayList<>();
        compare(before, now, broken, added);
        if (!broken.isEmpty()) {
            System.out.println(
                    "api: "
                            + broken.size()
                            + " change(s) that break a mod compiled against the API:");
            broken.forEach(b -> System.out.println("  " + b));
            System.out.println(
                    "if intentional, write the new list: ./gradlew build -Pstein.apiUpdate");
            System.exit(1);
        }
        if (!added.isEmpty()) {
            System.out.println("api: " + added.size() + " new signature(s), not in the list:");
            added.forEach(a -> System.out.println("  " + a));
            System.out.println("review and write the list: ./gradlew build -Pstein.apiUpdate");
            System.exit(1);
        }
        System.out.println("api: compatible with the list (" + now.size() + " signatures)");
    }

    /** The signatures of the compiled API classes, sorted. */
    private static Map<String, String> readCurrent(Path classes) throws IOException {
        Map<String, String> now = new TreeMap<>();
        try (Stream<Path> s = Files.walk(classes)) {
            for (Path p : s.filter(p -> p.toString().endsWith(".class")).toList()) {
                read(Files.readAllBytes(p), now);
            }
        }
        return now;
    }

    /** The recorded list: "C|M|F name" to the rest of the line. */
    private static Map<String, String> readBaseline(Path baseline) throws IOException {
        Map<String, String> before = new TreeMap<>();
        for (String l : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            String[] p = l.split(" ", 3); // "C|M|F", the name, the rest
            if (p.length == 3) {
                before.put(p[0] + " " + p[1], p[2]);
            }
        }
        return before;
    }

    /**
     * What breaks a mod (removed, changed, new abstract member) goes to broken; the rest new, to
     * added.
     */
    private static void compare(
            Map<String, String> before,
            Map<String, String> now,
            List<String> broken,
            List<String> added) {
        for (Map.Entry<String, String> b : before.entrySet()) {
            String cur = now.get(b.getKey());
            boolean experimental = b.getValue().contains("@Experimental");
            if (cur == null && !experimental) {
                broken.add("removed: " + b.getKey());
            } else if (cur != null && !strip(cur).equals(strip(b.getValue())) && !experimental) {
                broken.add(
                        "changed: "
                                + b.getKey()
                                + " ["
                                + strip(b.getValue())
                                + "] -> ["
                                + strip(cur)
                                + "]");
            }
        }
        for (Map.Entry<String, String> n : now.entrySet()) {
            if (before.containsKey(n.getKey())) {
                continue;
            }
            String owner =
                    n.getKey().startsWith("M ")
                            ? "C " + n.getKey().substring(2, n.getKey().indexOf('.'))
                            : null;
            if (owner != null && before.containsKey(owner) && n.getValue().contains("abstract")) {
                broken.add(
                        "new abstract member in a class that mods implement: "
                                + n.getKey()
                                + " (give it a default)");
            } else {
                added.add(n.getKey());
            }
        }
    }

    /**
     * Without the experimental marker: it appearing or disappearing does not change the signature.
     */
    private static String strip(String v) {
        return v.replace(" @Experimental", "");
    }

    private static void read(byte[] bytes, Map<String, String> out) {
        new ClassReader(bytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            private String name;
                            private boolean visible;
                            private boolean classExperimental;

                            @Override
                            public void visit(
                                    int v,
                                    int access,
                                    String n,
                                    String sig,
                                    String sup,
                                    String[] itf) {
                                name = n;
                                visible =
                                        (access & Opcodes.ACC_PUBLIC) != 0 && !n.contains("/spi/");
                                if (visible) {
                                    out.put(
                                            "C " + n,
                                            flags(access, true)
                                                    + " super="
                                                    + sup
                                                    + " itf="
                                                    + String.join(",", itf));
                                }
                            }

                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean vis) {
                                if (desc.equals(EXPERIMENTAL) && visible) {
                                    classExperimental = true;
                                    out.computeIfPresent(
                                            "C " + name, (k, v) -> v + " @Experimental");
                                }
                                return null;
                            }

                            @Override
                            public FieldVisitor visitField(
                                    int access, String n, String desc, String sig, Object value) {
                                if (!visible || !exposed(access)) {
                                    return null;
                                }
                                String key = "F " + name + "." + n + ":" + desc;
                                out.put(
                                        key,
                                        flags(access, false)
                                                + (classExperimental ? " @Experimental" : ""));
                                return new FieldVisitor(Opcodes.ASM9) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(
                                            String d, boolean vis) {
                                        mark(key, d);
                                        return null;
                                    }
                                };
                            }

                            @Override
                            public MethodVisitor visitMethod(
                                    int access, String n, String desc, String sig, String[] ex) {
                                if (!visible || !exposed(access) || n.equals("<clinit>")) {
                                    return null;
                                }
                                String key = "M " + name + "." + n + desc;
                                out.put(
                                        key,
                                        flags(access, false)
                                                + (classExperimental ? " @Experimental" : ""));
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(
                                            String d, boolean vis) {
                                        mark(key, d);
                                        return null;
                                    }
                                };
                            }

                            private void mark(String key, String desc) {
                                if (desc.equals(EXPERIMENTAL)) {
                                    out.computeIfPresent(
                                            key,
                                            (k, v) ->
                                                    v.contains("@Experimental")
                                                            ? v
                                                            : v + " @Experimental");
                                }
                            }
                        },
                        ClassReader.SKIP_CODE);
    }

    /** Public or protected, and not compiler-generated. */
    private static boolean exposed(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0
                && (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) == 0;
    }

    private static String flags(int a, boolean type) {
        StringBuilder sb =
                new StringBuilder((a & Opcodes.ACC_PUBLIC) != 0 ? "public" : "protected");
        if ((a & Opcodes.ACC_STATIC) != 0 && !type) {
            sb.append(" static");
        }
        if ((a & Opcodes.ACC_FINAL) != 0) {
            sb.append(" final");
        }
        if ((a & Opcodes.ACC_INTERFACE) != 0) {
            sb.append(" interface");
        } else if ((a & Opcodes.ACC_ABSTRACT) != 0) {
            sb.append(" abstract");
        }
        return sb.toString();
    }
}
