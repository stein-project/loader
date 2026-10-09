/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.SteinMod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The work the mods hand to the game thread (api.Tasks, Inventory.clickLater and the relations
 * notice): queued from any thread, run at the end of each tick.
 */
final class GameTasks {

    /** A delayed task (Tasks.later): runs once {@code ticksLeft} goes below zero. */
    private static final class Delayed {
        int ticksLeft;
        final Runnable task;

        Delayed(int ticksLeft, Runnable task) {
            this.ticksLeft = ticksLeft;
            this.task = task;
        }
    }

    /** A click asked through Inventory.clickLater, with the window and screen it was asked in. */
    private record PendingClick(int windowId, int slot, int button, int mode, boolean screenOpen) {}

    // Mods' tasks for the game thread (api.Tasks): from any thread into the queue; the end of the
    // tick runs it.
    private static final ConcurrentLinkedQueue<Runnable> GAME_TASKS = new ConcurrentLinkedQueue<>();

    // Tasks.later: only the game thread touches it (it enters through the queue above).
    private static final List<Delayed> LATER = new ArrayList<>();

    private static volatile boolean relationsDirty;

    // Inventory.clickLater: one queue per owner (the mod that asked); only the game thread touches
    // it. One click per tick in total, alternating between the owners.
    private static final LinkedHashMap<String, ArrayDeque<PendingClick>> CLICKS =
            new LinkedHashMap<>();

    private GameTasks() {}

    static void onGameThread(Runnable task) {
        GAME_TASKS.add(task);
    }

    static void later(int ticks, Runnable task) {
        GAME_TASKS.add(() -> LATER.add(new Delayed(ticks, task)));
    }

    static void relationsChanged() {
        relationsDirty = true;
    }

    static void clickLater(String owner, int windowId, int slot, int button, int mode) {
        CLICKS.computeIfAbsent(owner, k -> new ArrayDeque<>())
                .add(new PendingClick(windowId, slot, button, mode, InventoryOps.screenOpen()));
    }

    static int pendingClicks(String owner) {
        ArrayDeque<PendingClick> q = CLICKS.get(owner);
        return q == null ? 0 : q.size();
    }

    static void clearClicks(String owner) {
        CLICKS.remove(owner);
    }

    /**
     * One click per tick. Each owner's queue belongs to the window in which the click was asked:
     * another window, or the screen that was open closed (the player's inventory is window 0, open
     * or closed), and what is left of the owner is discarded — a chest slot never lands in another
     * window.
     */
    private static void runClick() {
        String turn = null;
        for (var it = CLICKS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            PendingClick c = e.getValue().peek();
            if (c == null
                    || c.windowId() != InventoryOps.windowId()
                    || c.screenOpen() && !InventoryOps.screenOpen()) {
                it.remove();
                continue;
            }
            if (turn == null) {
                turn = e.getKey();
            }
        }
        if (turn == null) {
            return;
        }
        ArrayDeque<PendingClick> q = CLICKS.remove(turn);
        PendingClick c = q.poll();
        if (!q.isEmpty()) {
            CLICKS.put(turn, q); // goes to the back: the next tick is for another owner, if any
        }
        InventoryOps.click(c.slot(), c.button(), c.mode());
    }

    /** Runs the click, the queued tasks, the delayed ones and the relations notice for a tick. */
    static void run(Loader loader, SteinMod[] mods) {
        try {
            runClick();
        } catch (Throwable e) {
            loader.failLoader("click queue", e);
        }
        // Only what was already in the queue: a task that schedules another does not hold the tick
        // in a loop.
        for (int n = GAME_TASKS.size(); n > 0; n--) {
            Runnable r = GAME_TASKS.poll();
            if (r == null) {
                break;
            }
            try {
                r.run();
            } catch (Throwable e) {
                loader.failLoader("mod task", e);
            }
        }
        for (int i = LATER.size() - 1; i >= 0; i--) {
            Delayed l = LATER.get(i);
            if (--l.ticksLeft < 0) {
                LATER.remove(i);
                try {
                    l.task.run();
                } catch (Throwable e) {
                    loader.failLoader("mod task", e);
                }
            }
        }
        if (relationsDirty) {
            relationsDirty = false;
            for (SteinMod m : mods) {
                try {
                    m.onRelationsChanged();
                } catch (Throwable e) {
                    loader.fail(m, "onRelationsChanged", e);
                }
            }
        }
    }
}
