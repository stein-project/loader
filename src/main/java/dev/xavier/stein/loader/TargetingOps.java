/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import static dev.xavier.stein.loader.GameOps.mc;
import static dev.xavier.stein.loader.PlayerOps.player;

import dev.xavier.stein.bridge.hooks.GameHooks;
import dev.xavier.stein.loader.api.Targeting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The API's aiming (Targeting) and attacks against the game: the pointed entity, the ray with all
 * the entities in order, attack, swing and interact.
 */
final class TargetingOps {

    private TargetingOps() {}

    static double attackReach() {
        PlayerControllerMP pc = mc().playerController;
        return pc != null && pc.extendedReach() ? 6.0 : 3.0;
    }

    static Object pointedEntity() {
        MovingObjectPosition m = mc().objectMouseOver;
        return m != null && m.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                ? m.entityHit
                : null;
    }

    /**
     * The same test as the game's getMouseOver, but without stopping at the first: all of them, in
     * order.
     */
    static Targeting.Ray raycast(double reach) {
        return raycast(Targeting.query().reach(reach));
    }

    static Targeting.Ray raycast(Targeting.Query q) {
        Minecraft mc = mc();
        Entity viewer = mc.getRenderViewEntity();
        double reach = Double.isNaN(q.reach) ? attackReach() : q.reach;
        List<Targeting.Hit> hits = new ArrayList<>();
        if (viewer == null || mc.theWorld == null) {
            return new Targeting.Ray(hits, Double.POSITIVE_INFINITY, reach);
        }
        float pt = GameHooks.partialTicks();
        Vec3 eye = viewer.getPositionEyes(pt);
        Vec3 look = viewer.getLook(pt);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

        BlockRay.Hit block = BlockRay.first(mc.theWorld, eye, end, q.passThrough);
        double blockDistance = block == null ? Double.POSITIVE_INFINITY : block.distance;

        AxisAlignedBB area =
                viewer.getEntityBoundingBox()
                        .addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach)
                        .expand(1.0, 1.0, 1.0);
        if (!Double.isNaN(q.margin) && q.margin > 1.0) {
            area = area.expand(q.margin, q.margin, q.margin);
        }
        List<Entity> near =
                mc.theWorld.getEntitiesInAABBexcluding(
                        viewer,
                        area,
                        e ->
                                e != null
                                        && e.canBeCollidedWith()
                                        && !(e instanceof EntityPlayer
                                                && ((EntityPlayer) e).isSpectator()));
        for (Entity e : near) {
            if (q.living && !(e instanceof EntityLivingBase l && l.isEntityAlive())
                    || q.visibleOnly && e.isInvisible()
                    || !q.excluded.isEmpty() && q.excluded.contains(e)) {
                continue;
            }
            double border = Double.isNaN(q.margin) ? e.getCollisionBorderSize() : q.margin;
            AxisAlignedBB box = e.getEntityBoundingBox().expand(border, border, border);
            if (box.isVecInside(eye)) {
                hits.add(new Targeting.Hit(e, 0.0, eye.xCoord, eye.yCoord, eye.zCoord));
                continue;
            }
            MovingObjectPosition m = box.calculateIntercept(eye, end);
            if (m != null) {
                double d = eye.distanceTo(m.hitVec);
                if (!q.cutAtBlock || d < blockDistance) {
                    hits.add(
                            new Targeting.Hit(
                                    e, d, m.hitVec.xCoord, m.hitVec.yCoord, m.hitVec.zCoord));
                }
            }
        }
        hits.sort((x, y) -> Double.compare(x.distance, y.distance));
        return block == null
                ? new Targeting.Ray(hits, blockDistance, reach)
                : new Targeting.Ray(
                        hits, blockDistance, reach, block.id, block.x, block.y, block.z);
    }

    static boolean attack(Object entity) {
        EntityPlayerSP p = player();
        PlayerControllerMP pc = mc().playerController;
        if (p == null || pc == null || !(entity instanceof Entity)) {
            return false;
        }
        pc.attackEntity(p, (Entity) entity);
        return true;
    }

    static void swing() {
        EntityPlayerSP p = player();
        if (p != null) {
            p.swingItem();
        }
    }

    static boolean interact(Object entity) {
        EntityPlayerSP p = player();
        PlayerControllerMP pc = mc().playerController;
        if (p == null || pc == null || !(entity instanceof Entity)) {
            return false;
        }
        return pc.interactWithEntitySendPacket(p, (Entity) entity);
    }
}
