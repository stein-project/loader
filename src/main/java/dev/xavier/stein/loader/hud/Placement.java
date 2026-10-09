/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.loader.api.HudPlacement;

/** Place and state of an element (what the player changed in the editor). */
final class Placement {
    int anchorX;
    int anchorY;
    int x;
    int y;
    float scale = 1.0F;
    boolean enabled = true;
    boolean showInChat;

    /**
     * The background opacity chosen by the player (0 to 1); negative = the element's factory one.
     */
    float background = -1.0F;

    /** The game's own font instead of Stein's, for this element only. */
    boolean gameFont;

    static Placement of(HudPlacement p) {
        Placement q = new Placement();
        q.position(p);
        q.enabled = p.enabled;
        q.showInChat = p.showInChat;
        return q;
    }

    /** Only the place and the scale. */
    void position(HudPlacement p) {
        anchorX = HudLayout.clamp(p.anchorX, 0, 2);
        anchorY = HudLayout.clamp(p.anchorY, 0, 2);
        x = p.x;
        y = p.y;
        scale = p.scale >= 0.25F && p.scale <= 4.0F ? p.scale : 1.0F;
    }
}
