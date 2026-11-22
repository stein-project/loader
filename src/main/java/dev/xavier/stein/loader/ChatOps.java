/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader;

import dev.xavier.stein.bridge.core.Log;
import dev.xavier.stein.bridge.hooks.VanillaHud;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import org.lwjgl.input.Mouse;

import java.util.Locale;

/**
 * The game-side work of the chat API that needs no Bridge hook: text components (copy and join,
 * JSON) and the click event of a text. The message store itself is the Bridge's ({@code
 * engine.ChatLines}, {@code engine.ChatView}, {@code engine.ChatInput}).
 */
final class ChatOps {

    private ChatOps() {}

    private static IChatComponent component(Object o) {
        if (o instanceof IChatComponent c) {
            return c;
        }
        return o == null ? null : new ChatComponentText(o.toString());
    }

    static Object append(Object component, Object tail) {
        IChatComponent head = component(component);
        IChatComponent rest = component(tail);
        if (head == null) {
            return rest == null ? new ChatComponentText("") : rest.createCopy();
        }
        // A parent with no text and no style keeps the two from sharing styles: the tail must not
        // inherit the head's color, nor the head's click.
        IChatComponent out = new ChatComponentText("");
        out.appendSibling(head.createCopy());
        if (rest != null) {
            out.appendSibling(rest.createCopy());
        }
        return out;
    }

    static String toJson(Object component) {
        return component instanceof IChatComponent c
                ? IChatComponent.Serializer.componentToJson(c)
                : "";
    }

    static Object fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return IChatComponent.Serializer.jsonToComponent(json);
        } catch (RuntimeException e) {
            Log.info("loader", "not a chat component: " + e);
            return null;
        }
    }

    private static ClickEvent click(Object component) {
        if (!(component instanceof IChatComponent c)) {
            return null;
        }
        ChatStyle style = c.getChatStyle();
        return style == null ? null : style.getChatClickEvent();
    }

    static String clickAction(Object component) {
        ClickEvent e = click(component);
        return e == null || e.getAction() == null
                ? ""
                : e.getAction().getCanonicalName().toLowerCase(Locale.ROOT);
    }

    static String clickValue(Object component) {
        ClickEvent e = click(component);
        return e == null || e.getValue() == null ? "" : e.getValue();
    }

    /** The text under the mouse in the open chat, the way GuiChat finds it for a click. */
    static Object hoveredText() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null) {
            return null;
        }
        try {
            return VanillaHud.chatComponent(mc.ingameGUI.getChatGUI(), Mouse.getX(), Mouse.getY());
        } catch (Throwable e) {
            Log.info("loader", "chat text under the mouse: " + e);
            return null;
        }
    }
}
