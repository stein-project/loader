/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The vault behind {@code api.Secrets}. On Windows, the system's own data protection (DPAPI:
 * CryptProtectData/CryptUnprotectData from Crypt32), called through Java 25's FFM — no extra
 * library: the output only opens under the same account, on the same machine. On other systems,
 * AES-GCM with a random key in ~/.stein/secret.key (readable only by the owner): it scrambles, but
 * does not protect against whoever uses the same account.
 *
 * <p>The first byte says who encrypted (1 DPAPI, 2 AES), so a file is never read with the wrong
 * method.
 */
final class Vault {

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte DPAPI = 1;
    private static final byte AES = 2;
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static MethodHandle protect;
    private static MethodHandle unprotect;
    private static MethodHandle localFree;
    private static boolean nativeReady;

    static {
        if (WINDOWS) {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup crypt = SymbolLookup.libraryLookup("Crypt32", Arena.global());
                SymbolLookup kernel = SymbolLookup.libraryLookup("Kernel32", Arena.global());
                // BOOL CryptProtectData(DATA_BLOB*, LPCWSTR, DATA_BLOB*, PVOID, PROMPTSTRUCT*,
                // DWORD, DATA_BLOB*)
                FunctionDescriptor fd =
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS);
                protect = linker.downcallHandle(crypt.find("CryptProtectData").orElseThrow(), fd);
                unprotect =
                        linker.downcallHandle(crypt.find("CryptUnprotectData").orElseThrow(), fd);
                localFree =
                        linker.downcallHandle(
                                kernel.find("LocalFree").orElseThrow(),
                                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                nativeReady = true;
            } catch (Throwable e) {
                Log.info("loader", "Windows vault unavailable, secrets only scrambled: " + e);
            }
        }
    }

    private Vault() {}

    static boolean systemProtected() {
        return nativeReady;
    }

    static byte[] protect(byte[] plain) {
        if (nativeReady) {
            byte[] c = dpapi(protect, plain);
            if (c != null) {
                return tag(DPAPI, c);
            }
        }
        return tag(AES, aes(Cipher.ENCRYPT_MODE, plain));
    }

    /** The value, or null if it does not open (another account, another machine, tampered file). */
    static byte[] unprotect(byte[] stored) {
        if (stored == null || stored.length < 2) {
            return null;
        }
        byte[] body = Arrays.copyOfRange(stored, 1, stored.length);
        try {
            return switch (stored[0]) {
                case DPAPI -> nativeReady ? dpapi(unprotect, body) : null;
                case AES -> aes(Cipher.DECRYPT_MODE, body);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] tag(byte kind, byte[] body) {
        byte[] out = new byte[body.length + 1];
        out[0] = kind;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

    // DATA_BLOB { DWORD cbData; BYTE* pbData; }: 4 bytes, 4 of alignment, the pointer.
    private static byte[] dpapi(MethodHandle call, byte[] in) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment data = a.allocate(Math.max(1, in.length));
            MemorySegment.copy(in, 0, data, ValueLayout.JAVA_BYTE, 0, in.length);
            MemorySegment inBlob = a.allocate(16, 8);
            inBlob.set(ValueLayout.JAVA_INT, 0, in.length);
            inBlob.set(ValueLayout.ADDRESS, 8, data);
            MemorySegment outBlob = a.allocate(16, 8);
            int ok =
                    (int)
                            call.invokeExact(
                                    inBlob,
                                    MemorySegment.NULL,
                                    MemorySegment.NULL,
                                    MemorySegment.NULL,
                                    MemorySegment.NULL,
                                    1 /* CRYPTPROTECT_UI_FORBIDDEN */,
                                    outBlob);
            if (ok == 0) {
                return null;
            }
            int n = outBlob.get(ValueLayout.JAVA_INT, 0);
            MemorySegment p = outBlob.get(ValueLayout.ADDRESS, 8);
            byte[] res = p.reinterpret(n).toArray(ValueLayout.JAVA_BYTE);
            MemorySegment ignored = (MemorySegment) localFree.invokeExact(p);
            return res;
        } catch (Throwable e) {
            return null;
        }
    }

    private static byte[] aes(int mode, byte[] in) {
        try {
            SecretKeySpec key = new SecretKeySpec(key(), "AES");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            if (mode == Cipher.ENCRYPT_MODE) {
                byte[] iv = new byte[GCM_IV_BYTES];
                new SecureRandom().nextBytes(iv);
                c.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
                byte[] body = c.doFinal(in);
                return ByteBuffer.allocate(GCM_IV_BYTES + body.length).put(iv).put(body).array();
            }
            c.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, in, 0, GCM_IV_BYTES));
            return c.doFinal(in, GCM_IV_BYTES, in.length - GCM_IV_BYTES);
        } catch (Exception e) {
            if (mode == Cipher.DECRYPT_MODE) {
                return null;
            }
            throw new IllegalStateException(e);
        }
    }

    private static byte[] key() throws Exception {
        File f = new File(System.getProperty("user.home"), ".stein/secret.key");
        if (f.isFile()) {
            return Files.readAllBytes(f.toPath());
        }
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        f.getParentFile().mkdirs();
        // Create the file owner-only BEFORE writing the key, so it is never readable by others.
        try {
            try {
                Files.createFile(
                        f.toPath(),
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException notPosix) {
                Files.createFile(f.toPath());
                f.setReadable(false, false);
                f.setReadable(true, true);
            }
        } catch (FileAlreadyExistsException raced) {
            return Files.readAllBytes(f.toPath());
        }
        Files.write(f.toPath(), k);
        return k;
    }
}
