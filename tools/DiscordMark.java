/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Writes the Discord mark of the Loader's button: 16 by 16, pixel by pixel, white on a transparent
 * background (whoever draws it picks the color). The texture has no filtering ({@code .mcmeta} with
 * blur false) and is drawn over 16 GUI units, so each pixel here is a whole pixel on screen, in the
 * style of the rest of the game.
 *
 * <p>It is a tool rather than a drawing in code because the PNG is what the game loads; this exists
 * for the next time the mark has to change. From the repository root:
 *
 * <pre>
 * java tools/DiscordMark.java src/main/resources/assets/steinloader/textures/gui/discord.png
 * </pre>
 *
 * A second path also writes a preview, 20 times larger over Discord's gray, to check by eye.
 */
public final class DiscordMark {

    /** One character per pixel: '#' is the mark, '.' is transparent. */
    private static final String[] ART = {
        "................",
        "................",
        "................",
        ".....######.....",
        "...##########...",
        "..############..",
        ".###..####..###.",
        ".###..####..###.",
        ".###..####..###.",
        ".##############.",
        ".##############.",
        "################",
        "###...####...###",
        "................",
        "................",
        "................",
    };

    private static final int ZOOM = 20;

    private DiscordMark() {}

    public static void main(String[] args) throws IOException {
        int n = ART.length;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (ART[y].charAt(x) == '#') {
                    img.setRGB(x, y, 0xFFFFFFFF);
                }
            }
        }
        File out = new File(args.length > 0 ? args[0] : "discord.png");
        ImageIO.write(img, "png", out);
        System.out.println("written: " + out.getAbsolutePath());

        if (args.length > 1) {
            BufferedImage big = new BufferedImage(n * ZOOM, n * ZOOM, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = big.createGraphics();
            g.setColor(new Color(0x2B2D31));
            g.fillRect(0, 0, n * ZOOM, n * ZOOM);
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, 0, 0, n * ZOOM, n * ZOOM, null);
            g.dispose();
            File preview = new File(args[1]);
            ImageIO.write(big, "png", preview);
            System.out.println("preview: " + preview.getAbsolutePath());
        }
    }
}
