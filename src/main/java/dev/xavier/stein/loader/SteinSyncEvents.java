/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.SteinMod;

/**
 * The Stein Sync events for the mods: the Bridge's Stein Sync client reads HitConfirm, Damaged and
 * CombatSeen (and flashes the hit marker), and hands them to the Loader as game events ({@code
 * HookSink.steinSyncHit} and the others); here they become {@link SteinMod#onHitConfirmed}, {@link
 * SteinMod#onDamaged} and {@link SteinMod#onCombatSeen}, to every mod in order on the game thread.
 * No allocation: one plain loop per event.
 */
final class SteinSyncEvents {

    private SteinSyncEvents() {}

    static void hit(
            Loader loader,
            SteinMod[] mods,
            int targetId,
            Object target,
            float damage,
            boolean critical,
            boolean killed,
            int tick) {
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].onHitConfirmed(targetId, target, damage, critical, killed, tick);
            } catch (Throwable e) {
                loader.fail(mods[i], "onHitConfirmed", e);
            }
        }
        ModTimes.current = -1;
    }

    static void damaged(
            Loader loader,
            SteinMod[] mods,
            int attackerId,
            Object attacker,
            float damage,
            int cause,
            boolean critical,
            boolean killed) {
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].onDamaged(attackerId, attacker, damage, cause, critical, killed);
            } catch (Throwable e) {
                loader.fail(mods[i], "onDamaged", e);
            }
        }
        ModTimes.current = -1;
    }

    static void seen(
            Loader loader,
            SteinMod[] mods,
            int attackerId,
            Object attacker,
            int targetId,
            Object target,
            boolean critical,
            boolean killed) {
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].onCombatSeen(attackerId, attacker, targetId, target, critical, killed);
            } catch (Throwable e) {
                loader.fail(mods[i], "onCombatSeen", e);
            }
        }
        ModTimes.current = -1;
    }
}
