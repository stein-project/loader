/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.gui;

import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;

import org.lwjgl.input.Keyboard;

/**
 * The state and editing of the single-line text field of a Panel page ({@link Option.Text}): the
 * caret, the selection, the mouse placement, the clipboard shortcuts and the typing, and its
 * drawing.
 */
final class TextEditor {

    /** Height of the input box. */
    static final int FIELD_H = 18;

    /** Cap on the letters in a field: what fit before, now also applied to what gets pasted. */
    private static final int MAX_TEXT = 48;

    /** Entry index (in the page) of the field being edited, or -1. */
    int focused = -1;

    String edit = "";
    int caret;
    int anchor;
    private int fieldFrom;
    private int fieldX;
    private int fieldY;
    private int fieldW;
    boolean selecting;

    /** The field loses the focus. */
    void blur() {
        focused = -1;
    }

    /** Draws the field at (x, y), w wide; {@code index} is its entry in the page. */
    void draw(Option.Text t, int index, int x, int y, int w, float p) {
        boolean on = focused == index;
        Ui.label(t.label, x, y + 2, Ui.alpha(Theme.MUTED, p));
        Draw2D.rect(x, y + 12, w, FIELD_H, Ui.alpha(Theme.BG_SUNKEN, p));
        Ui.frame(x, y + 12, w, FIELD_H, Ui.alpha(on ? Theme.ACCENT : Theme.RULE_STRONG, p));
        // When focused, what is being typed rules the text, not the get: some fields have a get
        // that normalizes the value (Connect's "host:port" returned "p:1080" after the first "p"),
        // and then each key fought the previous one.
        String value = on ? edit : t.get.get();
        if (value.isEmpty() && !on && t.hint != null) {
            Ui.text(Ui.trim(t.hint, w - 12), x + 6, y + 17, Ui.alpha(Theme.FAINT, p));
            return;
        }
        String shown = t.secret ? "*".repeat(value.length()) : value;
        int inner = w - 12;
        if (!on) {
            Ui.text(Ui.trim(shown, inner), x + 6, y + 17, Ui.alpha(Theme.INK, p));
            return;
        }
        fieldX = x + 6;
        fieldY = y + 12;
        fieldW = inner;
        fieldFrom = Math.min(fieldFrom, caret);
        while (Ui.width(shown.substring(fieldFrom, caret)) > inner) {
            fieldFrom++;
        }
        String visible = Ui.trim(shown.substring(fieldFrom), inner);
        int a = Math.min(caret, anchor);
        int b = Math.max(caret, anchor);
        if (a != b) {
            int fromI = Math.max(a - fieldFrom, 0);
            int toI = Math.min(b - fieldFrom, visible.length());
            if (toI > fromI) {
                int sx = fieldX + Ui.width(visible.substring(0, fromI));
                Ui.rect(
                        sx,
                        y + 14,
                        sx + Ui.width(visible.substring(fromI, toI)),
                        y + 12 + FIELD_H - 2,
                        0x667C4DFF);
            }
        }
        Ui.text(visible, fieldX, y + 17, Theme.INK);
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cx =
                    fieldX
                            + Ui.width(
                                    visible.substring(
                                            0,
                                            Math.max(
                                                    0,
                                                    Math.min(
                                                            visible.length(), caret - fieldFrom))));
            Ui.rect(cx, y + 15, cx + 1, y + 12 + FIELD_H - 3, Theme.INK);
        }
    }

    /**
     * A click on the field of entry {@code index} (row at {@code rowY}, input box from {@code
     * left}, {@code width} wide): focuses it and puts the cursor where the mouse is.
     */
    void press(Option.Text t, int index, int rowY, int left, int width, int mx, int my) {
        if (focused != index) {
            focused = index;
            edit = t.get.get();
            fieldFrom = 0;
            caret = edit.length();
            anchor = caret;
        }
        fieldX = left;
        fieldY = rowY + 12;
        fieldW = width;
        if (my >= fieldY && my < fieldY + FIELD_H) {
            caret = indexAt(t, mx);
            if (!GuiScreen.isShiftKeyDown()) {
                anchor = caret;
            }
            selecting = true;
        }
        Ui.click();
    }

    /**
     * The letter of the field being edited under the cursor: the first whose half has already
     * passed.
     */
    int indexAt(Option.Text t, int mx) {
        String shown = t.secret ? "*".repeat(edit.length()) : edit;
        int x = fieldX;
        for (int i = fieldFrom; i < shown.length(); i++) {
            int cw = Ui.width(shown.substring(i, i + 1));
            if (mx < x + cw / 2) {
                return i;
            }
            x += cw;
            if (x > fieldX + fieldW) {
                return i;
            }
        }
        return shown.length();
    }

    /**
     * Replaces the selected span (or nothing, if there is no selection) with the given text and
     * notifies the mod.
     */
    private void replace(Option.Text t, String with) {
        int a = Math.min(caret, anchor);
        int b = Math.max(caret, anchor);
        String next = edit.substring(0, a) + with + edit.substring(b);
        if (next.length() > MAX_TEXT) {
            next = next.substring(0, MAX_TEXT);
        }
        edit = next;
        caret = Math.min(a + with.length(), edit.length());
        anchor = caret;
        fieldFrom = Math.min(fieldFrom, caret);
        t.set.accept(edit);
    }

    /**
     * Edge of the word to the left or right of the cursor (Ctrl with the arrows and with delete).
     */
    private int word(int from, int dir) {
        int peek = dir < 0 ? -1 : 0;
        int i = from;
        while (i + peek >= 0 && i + peek < edit.length() && edit.charAt(i + peek) == ' ') {
            i += dir;
        }
        while (i + peek >= 0 && i + peek < edit.length() && edit.charAt(i + peek) != ' ') {
            i += dir;
        }
        return Math.max(0, Math.min(edit.length(), i));
    }

    /** Moves the cursor; without Shift, the selection dies with it. */
    private void moveCaret(int to) {
        caret = Math.max(0, Math.min(edit.length(), to));
        if (!GuiScreen.isShiftKeyDown()) {
            anchor = caret;
        }
    }

    /** A key in the text field being edited: shortcuts, cursor movement and typing. */
    void keyEdit(Option.Text t, char c, int key) {
        // The MCP names d/e/f/g of the game's shortcuts do not follow the order of the letters
        // (checked in the bytecode): Ctrl+A is key 30, Ctrl+C 46, Ctrl+X 45 and Ctrl+V 47. Swapping
        // two of them made Ctrl+A cut.
        if (GuiScreen.isKeyComboCtrlA(key)) {
            anchor = 0;
            caret = edit.length();
            return;
        }
        if (GuiScreen.isKeyComboCtrlC(key) || GuiScreen.isKeyComboCtrlX(key)) {
            GuiScreen.setClipboardString(selection());
            if (GuiScreen.isKeyComboCtrlX(key)) {
                replace(t, "");
            }
            return;
        }
        if (GuiScreen.isKeyComboCtrlV(key)) {
            StringBuilder ok = new StringBuilder();
            for (char ch : GuiScreen.getClipboardString().toCharArray()) {
                if (ChatAllowedCharacters.isAllowedCharacter(ch)) {
                    ok.append(ch);
                }
            }
            replace(t, ok.toString());
            return;
        }
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        switch (key) {
            case KeyCodes.BACK -> {
                if (caret != anchor) {
                    replace(t, "");
                } else if (caret > 0) {
                    anchor = ctrl ? word(caret, -1) : caret - 1;
                    replace(t, "");
                }
            }
            case KeyCodes.DELETE -> {
                if (caret != anchor) {
                    replace(t, "");
                } else if (caret < edit.length()) {
                    anchor = ctrl ? word(caret, 1) : caret + 1;
                    replace(t, "");
                }
            }
            case KeyCodes.LEFT -> moveCaret(ctrl ? word(caret, -1) : caret - 1);
            case KeyCodes.RIGHT -> moveCaret(ctrl ? word(caret, 1) : caret + 1);
            case KeyCodes.HOME -> moveCaret(0);
            case KeyCodes.END -> moveCaret(edit.length());
            case Keyboard.KEY_RETURN, KeyCodes.NUMPADENTER -> focused = -1;
            default -> {
                if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                    replace(t, String.valueOf(c));
                }
            }
        }
    }

    private String selection() {
        return caret == anchor
                ? ""
                : edit.substring(Math.min(caret, anchor), Math.max(caret, anchor));
    }
}
