/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.loader.api.SteinMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * The Stein Sync messages that become mod events: HitConfirm ({@link SteinMod#onHitConfirmed}, and
 * the hit marker), Damaged ({@link SteinMod#onDamaged}) and CombatSeen ({@link
 * SteinMod#onCombatSeen}), handed to every mod in order on the game thread.
 */
final class SteinSyncEvents {

    private SteinSyncEvents() {}

    /** A message {@link SteinSync#received} read, by its type. */
    static void dispatch(Loader loader, SteinMod[] mods, int type) {
        switch (type) {
            case SteinSyncCodec.HIT_CONFIRM:
                hit(loader, mods, SteinSync.HIT);
                break;
            case SteinSyncCodec.DAMAGED:
                damaged(loader, mods, SteinSync.DAMAGED);
                break;
            case SteinSyncCodec.COMBAT_SEEN:
                seen(loader, mods, SteinSync.SEEN);
                break;
            default:
                break;
        }
    }

    private static void hit(Loader loader, SteinMod[] mods, SteinSyncCodec.HitConfirm hit) {
        HitMarker.hit(hit.killed);
        Object entity = entity(hit.target);
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].onHitConfirmed(
                        hit.target, entity, hit.damage, hit.critical, hit.killed, hit.tick);
            } catch (Throwable e) {
                loader.fail(mods[i], "onHitConfirmed", e);
            }
        }
        ModTimes.current = -1;
    }

    private static void damaged(Loader loader, SteinMod[] mods, SteinSyncCodec.Damaged d) {
        Object attacker = d.attacker == 0 ? null : entity(d.attacker);
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].onDamaged(d.attacker, attacker, d.damage, d.cause, d.critical, d.killed);
            } catch (Throwable e) {
                loader.fail(mods[i], "onDamaged", e);
            }
        }
        ModTimes.current = -1;
    }

    private static void seen(Loader loader, SteinMod[] mods, SteinSyncCodec.CombatSeen s) {
        Object attacker = entity(s.attacker);
        Object target = entity(s.target);
        for (int i = 0; i < mods.length; i++) {
            ModTimes.current = i;
            try {
                mods[i].onCombatSeen(s.attacker, attacker, s.target, target, s.critical, s.killed);
            } catch (Throwable e) {
                loader.fail(mods[i], "onCombatSeen", e);
            }
        }
        ModTimes.current = -1;
    }

    /** The entity with this id in the client's world, or null. */
    private static Object entity(int id) {
        WorldClient world = Minecraft.getMinecraft().theWorld;
        return world == null ? null : world.getEntityByID(id);
    }
}
