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
 * The Loader's dispatch loops that return a value, timed per mod: the ones {@link Loader} switches
 * to while the profiler runs ({@link ModProfile#active}). Each one does exactly what the Loader's
 * own loop does (same order, same early return, same error handling), plus two {@code
 * System.nanoTime} per mod; off, the Loader never comes here, so its loops stay as they were.
 */
final class ProfiledHooks {

    private static final int EVENTS = ModProfile.EVENTS;
    private static final int HOOKS = ModProfile.HOOKS;

    private ProfiledHooks() {}

    /** The time since {@code t} goes to this mod instance. */
    private static void spent(SteinMod m, int phase, long t) {
        ModProfile.add(ModProfile.instanceOf(m), phase, System.nanoTime() - t);
    }

    static boolean claim(
            Loader l, SteinMod[] mods, String event, int phase, Loader.Claim c, Object arg) {
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            boolean claimed = false;
            try {
                claimed = c.on(mods[i], arg);
            } catch (Throwable e) {
                l.fail(mods[i], event, e);
            }
            ModProfile.add(i, phase, System.nanoTime() - t);
            if (claimed) {
                return true;
            }
        }
        return false;
    }

    static Object chat(Loader l, SteinMod[] mods, Object message) {
        Object msg = message;
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                Object r = mods[i].onChat(msg);
                if (r == null) {
                    ModProfile.add(i, EVENTS, System.nanoTime() - t);
                    return null;
                }
                msg = r;
            } catch (Throwable e) {
                l.fail(mods[i], "onChat", e);
            }
            ModProfile.add(i, EVENTS, System.nanoTime() - t);
        }
        return msg;
    }

    static String sendChat(Loader l, SteinMod[] mods, String message) {
        String msg = message;
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                msg = mods[i].onSendChat(msg);
                if (msg == null) {
                    ModProfile.add(i, EVENTS, System.nanoTime() - t);
                    return null;
                }
            } catch (Throwable e) {
                l.fail(mods[i], "onSendChat", e);
            }
            ModProfile.add(i, EVENTS, System.nanoTime() - t);
        }
        return msg;
    }

    static boolean guiAction(Loader l, SteinMod[] mods, Object screen, Object button) {
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            boolean claimed = false;
            try {
                claimed = mods[i].onGuiAction(screen, button);
            } catch (Throwable e) {
                l.fail(mods[i], "onGuiAction", e);
            }
            ModProfile.add(i, EVENTS, System.nanoTime() - t);
            if (claimed) {
                return true;
            }
        }
        return false;
    }

    static boolean windowClick(
            Loader l, SteinMod[] mods, int windowId, int slot, int button, int mode) {
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            boolean claimed = false;
            try {
                claimed = mods[i].onWindowClick(windowId, slot, button, mode);
            } catch (Throwable e) {
                l.fail(mods[i], "onWindowClick", e);
            }
            ModProfile.add(i, EVENTS, System.nanoTime() - t);
            if (claimed) {
                return true;
            }
        }
        return false;
    }

    static boolean entityHurt(Loader l, SteinMod[] mods, Object entity) {
        boolean cancel = false;
        for (int i = 0; i < mods.length; i++) {
            long t = System.nanoTime();
            try {
                cancel |= mods[i].onEntityHurt(entity);
            } catch (Throwable e) {
                l.fail(mods[i], "onEntityHurt", e);
            }
            ModProfile.add(i, EVENTS, System.nanoTime() - t);
        }
        return cancel;
    }

    // ---- the hot hooks: over the subset of mods that override each one (Overrides) ----

    static float fov(Loader l, SteinMod[] mods, float fov, float partialTicks, boolean setting) {
        float f = fov;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                f = m.onFov(f, partialTicks, setting);
            } catch (Throwable e) {
                l.fail(m, "onFov", e);
            }
            spent(m, HOOKS, t);
        }
        return f;
    }

    static float fovModifier(Loader l, SteinMod[] mods, float modifier) {
        float f = modifier;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                f = m.onFovModifier(f);
            } catch (Throwable e) {
                l.fail(m, "onFovModifier", e);
            }
            spent(m, HOOKS, t);
        }
        return f;
    }

    static Object itemModel(Loader l, SteinMod[] mods, Object stack, Object model) {
        Object r = model;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                Object x = m.onItemModel(stack, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                l.fail(m, "onItemModel", e);
            }
            spent(m, HOOKS, t);
        }
        return r;
    }

    static Object armorTexture(
            Loader l, SteinMod[] mods, Object stack, int layer, String overlay, Object location) {
        Object r = location;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                Object x = m.onArmorTexture(stack, layer, overlay, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                l.fail(m, "onArmorTexture", e);
            }
            spent(m, HOOKS, t);
        }
        return r;
    }

    static boolean hideEntity(Loader l, SteinMod[] mods, Object entity) {
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            boolean hide = false;
            try {
                hide = m.onHideEntity(entity);
            } catch (Throwable e) {
                l.fail(m, "onHideEntity", e);
            }
            spent(m, HOOKS, t);
            if (hide) {
                return true;
            }
        }
        return false;
    }

    static boolean ghostEntity(Loader l, SteinMod[] mods, Object entity) {
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            boolean ghost = false;
            try {
                ghost = m.onGhostEntity(entity);
            } catch (Throwable e) {
                l.fail(m, "onGhostEntity", e);
            }
            spent(m, HOOKS, t);
            if (ghost) {
                return true;
            }
        }
        return false;
    }

    static int entityColor(Loader l, SteinMod[] mods, Object entity, int color) {
        int c = color;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                c = m.onEntityColor(entity, c);
            } catch (Throwable e) {
                l.fail(m, "onEntityColor", e);
            }
            spent(m, HOOKS, t);
        }
        return c;
    }

    static boolean playerList(
            Loader l,
            SteinMod[] mods,
            Object overlay,
            int width,
            Object scoreboard,
            Object objective) {
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            boolean drawn = false;
            try {
                drawn = m.onPlayerList(overlay, width, scoreboard, objective);
            } catch (Throwable e) {
                l.fail(m, "onPlayerList", e);
            }
            spent(m, HOOKS, t);
            if (drawn) {
                return true;
            }
        }
        return false;
    }

    static String playerListName(Loader l, SteinMod[] mods, Object playerInfo, String name) {
        String r = name;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                String x = m.onPlayerListName(playerInfo, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                l.fail(m, "onPlayerListName", e);
            }
            spent(m, HOOKS, t);
        }
        return r;
    }

    static Object nametag(Loader l, SteinMod[] mods, Object entity, Object name) {
        Object r = name;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                Object x = m.onNametag(entity, r);
                if (x != null) {
                    r = x;
                }
            } catch (Throwable e) {
                l.fail(m, "onNametag", e);
            }
            spent(m, HOOKS, t);
        }
        return r;
    }

    static Object playSound(Loader l, SteinMod[] mods, Object sound) {
        Object s = sound;
        for (SteinMod m : mods) {
            long t = System.nanoTime();
            try {
                s = m.onPlaySound(s);
                if (s == null) {
                    spent(m, HOOKS, t);
                    return null;
                }
            } catch (Throwable e) {
                l.fail(m, "onPlaySound", e);
            }
            spent(m, HOOKS, t);
        }
        return s;
    }
}
