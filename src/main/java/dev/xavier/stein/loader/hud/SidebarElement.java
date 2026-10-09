/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.hud;

import dev.xavier.stein.bridge.hooks.VanillaHud;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Option;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The sidebar scoreboard in the grid: move it, turn it off, and its format: red numbers, background
 * (the game's, light, or none), title and shadow. The lines are the game's own: up to 15, the
 * highest scores on top, skipping those that start with "#", with each one's team prefix and
 * suffix.
 */
final class SidebarElement extends BuiltinElement {

    private String title = "";
    private final List<String> lefts = new ArrayList<>();
    private final List<String> rights = new ArrayList<>();
    private int w;
    private int h;

    @Override
    public String id() {
        return "minecraft.sidebar";
    }

    @Override
    public String name() {
        return I18n.format("steinloader.hud.sidebar");
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        lefts.clear();
        rights.clear();
        if (preview) {
            title = I18n.format("steinloader.hud.sample.sidebar");
            for (int i = 0; i < 4; i++) {
                lefts.add(I18n.format("steinloader.hud.sample.player") + " " + (i + 1));
                rights.add(EnumChatFormatting.RED + "" + (10 - i * 3));
            }
        } else {
            Object o = VanillaHud.sidebarObjective();
            if (!(o instanceof ScoreObjective)) {
                return false;
            }
            ScoreObjective objective = (ScoreObjective) o;
            Scoreboard board = objective.getScoreboard();
            Collection<Score> all = board.getSortedScores(objective);
            List<Score> list = new ArrayList<>();
            for (Score s : all) {
                if (s.getPlayerName() != null && !s.getPlayerName().startsWith("#")) {
                    list.add(s);
                }
            }
            if (list.size() > 15) {
                list = list.subList(list.size() - 15, list.size());
            }
            title = objective.getDisplayName();
            for (int i = list.size() - 1; i >= 0; i--) {
                Score s = list.get(i);
                ScorePlayerTeam team = board.getPlayersTeam(s.getPlayerName());
                lefts.add(ScorePlayerTeam.formatPlayerName(team, s.getPlayerName()));
                rights.add(EnumChatFormatting.RED + "" + s.getScorePoints());
            }
        }
        if (lefts.isEmpty()) {
            return false;
        }
        FontRenderer fr = font();
        int max = VanillaOptions.sideTitle ? fr.getStringWidth(title) : 0;
        for (int i = 0; i < lefts.size(); i++) {
            int lw =
                    fr.getStringWidth(lefts.get(i))
                            + (VanillaOptions.sideNumbers
                                    ? fr.getStringWidth(": " + rights.get(i))
                                    : 0);
            max = Math.max(max, lw);
        }
        int fh = fr.FONT_HEIGHT;
        w = max + 4;
        h = lefts.size() * fh + (VanillaOptions.sideTitle ? fh + 1 : 0);
        return true;
    }

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        FontRenderer fr = font();
        int fh = fr.FONT_HEIGHT;
        boolean shadow = VanillaOptions.sideShadow;
        int y = 0;
        if (VanillaOptions.sideTitle) {
            fr.drawString(title, w / 2 - fr.getStringWidth(title) / 2, 1, 0xFFFFFFFF, shadow);
            y = fh + 1;
        }
        for (int i = 0; i < lefts.size(); i++) {
            fr.drawString(lefts.get(i), 2, y, 0xFFFFFFFF, shadow);
            if (VanillaOptions.sideNumbers) {
                String r = rights.get(i);
                fr.drawString(r, w - fr.getStringWidth(r), y, 0xFFFFFFFF, shadow);
            }
            y += fh;
        }
    }

    @Override
    public HudPlacement defaultPlacement() {
        // Where the game draws it: flush right (1 px from the edge), vertically centred.
        return new HudPlacement(2, 1, -1, 0, 1.0F, true, true);
    }

    @Override
    public void options(List<Option> out) {
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.numbers"),
                        () -> VanillaOptions.sideNumbers,
                        v -> VanillaOptions.sideNumbers = v));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.title"),
                        () -> VanillaOptions.sideTitle,
                        v -> VanillaOptions.sideTitle = v));
        out.add(
                Option.toggle(
                        I18n.format("steinloader.hud.shadow"),
                        () -> VanillaOptions.sideShadow,
                        v -> VanillaOptions.sideShadow = v));
    }
}
