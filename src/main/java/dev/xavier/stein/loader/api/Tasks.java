/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Work off the game thread, and the return to it. The game can only be touched from its own thread
 * (the drawing and tick one); a download, a disk read or a heavy computation go to {@link #async},
 * and the result comes back on the game thread, ready to use — without each mod opening its own
 * threads and passing the result through volatile fields.
 */
@Experimental(since = "0.1.16")
public final class Tasks {

    private static final AtomicInteger COUNT = new AtomicInteger();

    /** Few threads for all the mods: background work must not compete with the game. */
    private static final ExecutorService POOL =
            new ThreadPoolExecutor(
                    0,
                    4,
                    30,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "stein-mods-" + COUNT.incrementAndGet());
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    });

    private Tasks() {}

    /**
     * Runs on the game thread, at the end of the current tick (or the next one, if called from
     * there).
     */
    public static void onGameThread(Runnable task) {
        Host.INSTANCE.onGameThread(task);
    }

    /** Runs on the game thread {@code ticks} ticks from now (20 per second). */
    public static void later(int ticks, Runnable task) {
        Host.INSTANCE.later(Math.max(0, ticks), task);
    }

    /**
     * Runs {@code work} on a background thread; the result (or the error) comes back on the game
     * thread.
     *
     * @param onError may be null: the error goes to the game log
     */
    public static <T> void async(
            Callable<T> work, Consumer<T> onResult, Consumer<Throwable> onError) {
        POOL.execute(
                () -> {
                    T value;
                    try {
                        value = work.call();
                    } catch (Throwable e) {
                        // Throwable on purpose: nothing a mod task throws may kill the pool thread.
                        onGameThread(
                                () -> {
                                    if (onError != null) {
                                        onError.accept(e);
                                    } else {
                                        Host.INSTANCE.log(
                                                "tasks", Log.ERROR, "background task failed", e);
                                    }
                                });
                        return;
                    }
                    onGameThread(() -> onResult.accept(value));
                });
    }

    /** Runs {@code work} on a background thread, with no return. */
    public static void background(Runnable work) {
        POOL.execute(work);
    }
}
