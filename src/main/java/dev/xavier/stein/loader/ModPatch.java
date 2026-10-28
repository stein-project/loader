/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.internal.Access;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

/**
 * The Bridge rules applied to a mod jar, on the player's machine: renaming from LWJGL 2 to 3, the
 * calls the engine replaces with its own versions, the matrix stack (GLMatrix) and the model batch
 * (ModelBatch) before each GL call.
 *
 * <p>Up to 0.1.13 this was done at mod build time, by the Patcher and the Bridge rules — which only
 * the author's machine has. Here the mod arrives as it came out of javac (with the game's names)
 * and the Loader adapts it to the Bridge that is installed: whoever writes a mod needs nothing of
 * ours beyond the API, and a new Bridge (another engine, another batch) re-adapts the mods without
 * them changing.
 *
 * <p>It does the same as the Patcher (patcher/Patcher.java) with the call-site rules — redirect,
 * "static", "before", "after", with scope —, in the same order, and the result is the same byte for
 * byte (the mod build checks this on every build, in the Loader itself). The "body", "field" and
 * "new" modes are not included: they touch game classes, and a mod has none.
 *
 * <p>And it does one thing the Patcher does not: <b>access to non-public game members</b>. The mod
 * compiles against the game with private fields and methods opened; each instruction the JVM would
 * refuse (private field, protected method called from outside the hierarchy) becomes an
 * invokedynamic to {@code internal.Access}, which binds it to the member once. There is no list to
 * declare: the decision is the same access rule as the JVM's, read from the game classes.
 *
 * <p>The rules come from inside the Bridge jar (steinbridge/rules/), so they always match the
 * Bridge that is running: the plain ones in the Bridge's development build, the ones with the
 * obfuscated names in the jar players get.
 */
public final class ModPatch {

    /** Rule files, in the order in which the Patcher received them in the mod build. */
    static final String[] RULES = {
        "name-mapping.txt", "redirects-mc.tsv", "redirects-opt.tsv", "redirects-modgl.tsv"
    };

    private static final String RESOURCE = "/steinbridge/rules/";

    /**
     * Version of what this class does with the rules: part of the identity of the adapted copies,
     * together with the rules. It goes up when the transformation changes, otherwise the copies
     * made by the previous version would remain valid.
     */
    private static final int VERSION = 2;

    /**
     * GL calls that go through the batch first: the LWJGL OpenGL ones, except the matrix ones
     * (those go to GLMatrix).
     */
    private static final Pattern GL =
            Pattern.compile("org/lwjgl/opengl/(GL[0-9]+|ARB|EXT)[A-Za-z0-9]*");

    private static final Pattern MATRIX =
            Pattern.compile(
                    "org/lwjgl/opengl/GL11\\.(gl(MatrixMode|LoadIdentity"
                            + "|PushMatrix|PopMatrix|Ortho|Rotatef|Scalef|Scaled|Translatef|Translated"
                            + "|MultMatrix):.*|glGetFloat:\\(ILjava/nio/FloatBuffer;\\)V)");

    /**
     * Calls that touch vertex and buffer state: the batch restores what was its, instead of just
     * drawing.
     */
    private static final Pattern RESTORE =
            Pattern.compile(
                    ".*(Pointer|ClientState|ClientActiveTexture|BindBuffer"
                            + "|BufferData|BufferSubData|DeleteBuffers|GenBuffers|DrawArrays"
                            + "|DrawElements|ArrayElement"
                            + "|InterleavedArrays).*");

    private static final String BATCH = "dev/xavier/stein/bridge/engine/ModelBatch";

    private static final Handle ACCESS =
            new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "dev/xavier/stein/loader/internal/Access",
                    "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                            + "Ljava/lang/invoke/MethodType;"
                            + "ILjava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/CallSite;",
                    false);

    private record Target(String owner, String name, boolean toStatic, String scope) {}

    private final Map<String, List<Target>> map = new HashMap<>();
    private final Map<String, List<Target>> before = new HashMap<>();
    private final Map<String, List<Target>> after = new HashMap<>();

    /**
     * Identity of the rules and of the transformation (sha-256): when it changes, the copies
     * adapted before are redone.
     */
    private final String stamp;

    /** Where the game (and JDK) classes come from, to know each member's access. */
    private final ClassLoader game;

    /**
     * Only the access invokedynamic, without rules or batch hooks: the build's last step on the
     * Loader itself, after the Patcher (see {@link #main}).
     */
    private final boolean accessOnly;

    private ModPatch(List<byte[]> files, ClassLoader game) {
        this(files, game, false);
    }

    private ModPatch(List<byte[]> files, ClassLoader game, boolean accessOnly) {
        this.game = game;
        this.accessOnly = accessOnly;
        MessageDigest sha = digest();
        sha.update((byte) VERSION);
        for (byte[] f : files) {
            sha.update(f);
            load(new String(f, StandardCharsets.UTF_8));
        }
        stamp = HexFormat.of().formatHex(sha.digest());
    }

    static String sha256(byte[] data) {
        return HexFormat.of().formatHex(digest().digest(data));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ModPatch instance;

    /** The rules that came inside the Bridge. */
    static synchronized ModPatch get() throws IOException {
        if (instance == null) {
            List<byte[]> files = new ArrayList<>();
            for (String r : RULES) {
                try (InputStream in = ModPatch.class.getResourceAsStream(RESOURCE + r)) {
                    if (in == null) {
                        throw new IOException("rules missing from the Bridge: " + RESOURCE + r);
                    }
                    files.add(in.readAllBytes());
                }
            }
            instance = new ModPatch(files, ModPatch.class.getClassLoader());
        }
        return instance;
    }

    String stamp() {
        return stamp;
    }

    /**
     * Same format as the Patcher: class TAB name TAB descriptor TAB new name [TAB new owner [TAB
     * mode [TAB scope]]].
     */
    private void load(String text) {
        for (String line : text.split("\r?\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t");
            if (f.length < 4) {
                continue;
            }
            String owner = f[0].trim();
            String newOwner = f.length > 4 && !f[4].isEmpty() ? f[4] : owner;
            String mode = f.length > 5 ? f[5] : "";
            String scope = f.length > 6 && !f[6].trim().isEmpty() ? f[6].trim() : null;
            String key = owner + "." + f[1] + ":" + f[2];
            switch (mode) {
                case "body", "field", "new" -> {
                    // Only on a game class.
                }
                case "before" ->
                        before.computeIfAbsent(key, k -> new ArrayList<>())
                                .add(new Target(newOwner, f[3], false, scope));
                case "after" ->
                        after.computeIfAbsent(key, k -> new ArrayList<>())
                                .add(new Target(newOwner, f[3], false, scope));
                default -> {
                    List<Target> l = map.computeIfAbsent(key, k -> new ArrayList<>());
                    l.removeIf(x -> Objects.equals(x.scope, scope));
                    l.add(new Target(newOwner, f[3], mode.equals("static"), scope));
                }
            }
        }
    }

    /**
     * The adapted mod jar, written to out (the classes go through the rules; the rest goes as is).
     */
    void transformJar(Path in, Path out) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Map<String, Long> times = new HashMap<>();
        Classes classes = new Classes();
        try (JarFile jar = new JarFile(in.toFile())) {
            for (JarEntry e : jar.stream().toList()) {
                String n = e.getName();
                if (e.isDirectory()
                        || n.startsWith("META-INF/")
                                && (n.endsWith(".SF")
                                        || n.endsWith(".DSA")
                                        || n.endsWith(".RSA"))) {
                    continue; // a signature does not survive a changed class
                }
                try (InputStream is = jar.getInputStream(e)) {
                    entries.put(n, is.readAllBytes());
                }
                times.put(n, e.getTime());
                if (n.endsWith(".class")) {
                    classes.mod.put(n.substring(0, n.length() - 6), entries.get(n));
                }
            }
        }
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                byte[] data = e.getValue();
                if (e.getKey().endsWith(".class")) {
                    data = transform(data, classes);
                }
                JarEntry ne = new JarEntry(e.getKey());
                ne.setTime(times.get(e.getKey()));
                jos.putNextEntry(ne);
                jos.write(data);
                jos.closeEntry();
            }
        }
    }

    private byte[] transform(byte[] classBytes, Classes classes) {
        ClassReader cr = new ClassReader(classBytes);
        // As in the Patcher, without recomputing frames or maxs: the hooks do not touch the stack,
        // and both the "static" redirect and the access invokedynamic consume and leave on the
        // stack the same as the original instruction.
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRewriter(cw, classes), 0);
        return cw.toByteArray();
    }

    /** Rewrites every method of a class through a {@link MethodRewriter}. */
    private final class ClassRewriter extends ClassVisitor {
        private final Classes classes;
        private String className;

        ClassRewriter(ClassVisitor next, Classes classes) {
            super(Opcodes.ASM9, next);
            this.classes = classes;
        }

        @Override
        public void visit(
                int version, int access, String name, String sig, String sup, String[] itf) {
            className = name;
            super.visit(version, access, name, sig, sup, itf);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (mv == null) {
                return null;
            }
            String here = className + "." + name;
            String hereDesc = here + desc;
            return new MethodRewriter(mv, classes, className, here, hereDesc);
        }
    }

    /**
     * Applies the rules to the instructions of one method: the before/after hooks, the batch flush
     * before GL calls, the redirects and the access invokedynamic.
     */
    private final class MethodRewriter extends MethodVisitor {
        private final Classes classes;
        private final String className;
        private final String here;
        private final String hereDesc;

        MethodRewriter(
                MethodVisitor next,
                Classes classes,
                String className,
                String here,
                String hereDesc) {
            super(Opcodes.ASM9, next);
            this.classes = classes;
            this.className = className;
            this.here = here;
            this.hereDesc = hereDesc;
        }

        @Override
        public void visitMethodInsn(int op, String owner, String mName, String mDesc, boolean itf) {
            if (accessOnly) {
                if (!bridge(op, owner, mName, mDesc)) {
                    super.visitMethodInsn(op, owner, mName, mDesc, itf);
                }
                return;
            }
            String key = owner + "." + mName + ":" + mDesc;
            hooks(before.get(key));
            if (GL.matcher(owner).matches() && !MATRIX.matcher(key).matches()) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        BATCH,
                        RESTORE.matcher(mName).matches() ? "restore" : "flush",
                        "()V",
                        false);
            }
            rewrite(op, owner, mName, mDesc, itf, key);
            hooks(after.get(key));
        }

        @Override
        public void visitFieldInsn(int op, String owner, String fName, String fDesc) {
            int kind =
                    switch (op) {
                        case Opcodes.GETFIELD -> Access.GET;
                        case Opcodes.PUTFIELD -> Access.PUT;
                        case Opcodes.GETSTATIC -> Access.GET_STATIC;
                        default -> Access.PUT_STATIC;
                    };
            Classes.Member m = classes.field(owner, fName, fDesc);
            boolean statik = kind == Access.GET_STATIC || kind == Access.PUT_STATIC;
            if (m != null && classes.denied(className, owner, m, statik)) {
                String type =
                        switch (kind) {
                            case Access.GET -> "(L" + owner + ";)" + fDesc;
                            case Access.PUT -> "(L" + owner + ";" + fDesc + ")V";
                            case Access.GET_STATIC -> "()" + fDesc;
                            default -> "(" + fDesc + ")V";
                        };
                super.visitInvokeDynamicInsn(fName, type, ACCESS, kind, m.owner(), fDesc);
                return;
            }
            super.visitFieldInsn(op, owner, fName, fDesc);
        }

        private void hooks(List<Target> l) {
            if (l == null) {
                return;
            }
            for (Target h : l) {
                if (h.scope == null || h.scope.equals(here) || h.scope.equals(hereDesc)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, h.owner, h.name, "()V", false);
                }
            }
        }

        private void rewrite(
                int op, String owner, String mName, String mDesc, boolean itf, String key) {
            Target t = pick(map.get(key), here, hereDesc);
            if (t == null) {
                if (!bridge(op, owner, mName, mDesc)) {
                    super.visitMethodInsn(op, owner, mName, mDesc, itf);
                }
            } else if (t.toStatic) {
                // The receiver and references become Object, and the return value goes back to
                // its type with a cast.
                Type ret = Type.getReturnType(mDesc);
                StringBuilder sd =
                        new StringBuilder(op == Opcodes.INVOKESTATIC ? "(" : "(Ljava/lang/Object;");
                for (Type a : Type.getArgumentTypes(mDesc)) {
                    sd.append(erase(a));
                }
                sd.append(')').append(erase(ret));
                super.visitMethodInsn(Opcodes.INVOKESTATIC, t.owner, t.name, sd.toString(), false);
                if (ret.getSort() == Type.OBJECT || ret.getSort() == Type.ARRAY) {
                    super.visitTypeInsn(Opcodes.CHECKCAST, ret.getInternalName());
                }
            } else {
                super.visitMethodInsn(op, t.owner, t.name, mDesc, itf);
            }
        }

        /**
         * Call to a method the JVM would refuse: becomes an invokedynamic. invokespecial (super and
         * constructor, which must stay as they are) and interface calls (an interface method is
         * public) are excluded.
         */
        private boolean bridge(int op, String owner, String mName, String mDesc) {
            if (op != Opcodes.INVOKEVIRTUAL && op != Opcodes.INVOKESTATIC) {
                return false;
            }
            Classes.Member m = classes.method(owner, mName, mDesc);
            boolean statik = op == Opcodes.INVOKESTATIC;
            if (m == null || !classes.denied(className, owner, m, statik)) {
                return false;
            }
            String type = statik ? mDesc : "(L" + owner + ";" + mDesc.substring(1);
            super.visitInvokeDynamicInsn(
                    mName, type, ACCESS, statik ? Access.STATIC : Access.VIRTUAL, m.owner(), mDesc);
            return true;
        }
    }

    /** The target with this method's scope, otherwise the general one. */
    private static Target pick(List<Target> l, String here, String hereDesc) {
        if (l == null) {
            return null;
        }
        Target general = null;
        for (Target t : l) {
            if (t.scope == null) {
                general = t;
            } else if (t.scope.equals(here) || t.scope.equals(hereDesc)) {
                return t;
            }
        }
        return general;
    }

    private static String erase(Type t) {
        return t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY
                ? "Ljava/lang/Object;"
                : t.getDescriptor();
    }

    /**
     * The classes a mod sees: its own (from the jar) and the game's and JDK's (from the game's
     * class loader), read only for what matters to the access rule — superclass, interfaces and
     * each member's access.
     */
    private final class Classes {

        record Member(String owner, int access) {}

        private record Info(
                String name,
                String sup,
                String[] itfs,
                Map<String, Integer> fields,
                Map<String, Integer> methods) {}

        final Map<String, byte[]> mod = new HashMap<>();
        private final Map<String, Info> cache = new HashMap<>();

        private Info get(String name) {
            if (name == null) {
                return null;
            }
            if (cache.containsKey(name)) {
                return cache.get(name);
            }
            Info info = null;
            try {
                byte[] b = mod.get(name);
                if (b == null) {
                    try (InputStream in = game.getResourceAsStream(name + ".class")) {
                        b = in == null ? null : in.readAllBytes();
                    }
                }
                if (b != null) {
                    info = read(b);
                }
            } catch (IOException | RuntimeException e) {
                info = null; // unreadable class: nothing is replaced in it
            }
            cache.put(name, info);
            return info;
        }

        private Info read(byte[] b) {
            Map<String, Integer> fields = new HashMap<>();
            Map<String, Integer> methods = new HashMap<>();
            String[] head = new String[1];
            String[] sup = new String[1];
            String[][] itfs = new String[1][];
            new ClassReader(b)
                    .accept(
                            new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(
                                        int v,
                                        int access,
                                        String name,
                                        String sig,
                                        String s,
                                        String[] i) {
                                    head[0] = name;
                                    sup[0] = s;
                                    itfs[0] = i == null ? new String[0] : i;
                                }

                                @Override
                                public FieldVisitor visitField(
                                        int access,
                                        String name,
                                        String desc,
                                        String sig,
                                        Object value) {
                                    fields.put(name + ":" + desc, access);
                                    return null;
                                }

                                @Override
                                public MethodVisitor visitMethod(
                                        int access,
                                        String name,
                                        String desc,
                                        String sig,
                                        String[] ex) {
                                    methods.put(name + desc, access);
                                    return null;
                                }
                            },
                            ClassReader.SKIP_CODE
                                    | ClassReader.SKIP_DEBUG
                                    | ClassReader.SKIP_FRAMES);
            return new Info(head[0], sup[0], itfs[0], fields, methods);
        }

        /** The field as the JVM resolves it: the class, its interfaces, the superclass. */
        Member field(String owner, String name, String desc) {
            Info c = get(owner);
            if (c == null) {
                return null;
            }
            Integer a = c.fields.get(name + ":" + desc);
            if (a != null) {
                return new Member(c.name, a);
            }
            for (String i : c.itfs) {
                Member m = field(i, name, desc);
                if (m != null) {
                    return m;
                }
            }
            return field(c.sup, name, desc);
        }

        /**
         * The method through the superclass chain (interface ones are public and do not matter
         * here).
         */
        Member method(String owner, String name, String desc) {
            for (Info c = get(owner); c != null; c = get(c.sup)) {
                Integer a = c.methods.get(name + desc);
                if (a != null) {
                    return new Member(c.name, a);
                }
            }
            return null;
        }

        /**
         * Would the JVM refuse this access? Only for game members: the mod's own and the JDK's stay
         * as they are (javac would already have refused the JDK's, and forcing entry into it is not
         * what a mod should do).
         *
         * @param accessor the mod class where the instruction is
         * @param owner the class named in the instruction (the receiver's type)
         */
        boolean denied(String accessor, String owner, Member m, boolean statik) {
            if (mod.containsKey(m.owner) || jdk(m.owner)) {
                return false;
            }
            int a = m.access;
            if ((a & Opcodes.ACC_PUBLIC) != 0) {
                return false;
            }
            if ((a & Opcodes.ACC_PRIVATE) != 0) {
                return true;
            }
            if (pkg(accessor).equals(pkg(m.owner))) {
                return false;
            }
            if ((a & Opcodes.ACC_PROTECTED) == 0) {
                return true; // package, from another package
            }
            // Protected: from a subclass, and (if instance) through a receiver of its type.
            return !(isSub(accessor, m.owner) && (statik || isSub(owner, accessor)));
        }

        private boolean isSub(String c, String parent) {
            for (Info i = get(c); i != null; i = get(i.sup)) {
                if (i.name.equals(parent)) {
                    return true;
                }
            }
            return false;
        }

        private String pkg(String name) {
            int s = name.lastIndexOf('/');
            return s < 0 ? "" : name.substring(0, s);
        }

        private boolean jdk(String name) {
            return name.startsWith("java/")
                    || name.startsWith("javax/")
                    || name.startsWith("jdk/")
                    || name.startsWith("sun/")
                    || name.startsWith("com/sun/");
        }
    }

    /**
     * Outside the game, for the build: applies the rules of a folder to a jar. The game classes
     * come from this run's classpath (the game jar must be on it).
     *
     * <p>With {@code --access} in place of the rules folder it applies only the access to
     * non-public game members. That is for the Loader itself: the build compiles it against the
     * opened game jar like a mod, passes it through the Patcher and then through this, so the
     * Loader that ships is exactly what ModPatch makes of it (modPatchCheck compares the two).
     *
     * <p>Usage: ModPatch &lt;rules folder&gt; &lt;input.jar&gt; &lt;output.jar&gt;, or ModPatch
     * --access &lt;input.jar&gt; &lt;output.jar&gt;
     */
    public static void main(String[] args) throws IOException {
        if (args[0].equals("--access")) {
            new ModPatch(List.of(), ModPatch.class.getClassLoader(), true)
                    .transformJar(Paths.get(args[1]), Paths.get(args[2]));
            return;
        }
        List<byte[]> files = new ArrayList<>();
        for (String r : RULES) {
            files.add(Files.readAllBytes(Paths.get(args[0], r)));
        }
        new ModPatch(files, ModPatch.class.getClassLoader())
                .transformJar(Paths.get(args[1]), Paths.get(args[2]));
    }
}
