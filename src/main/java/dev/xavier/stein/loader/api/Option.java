/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import dev.xavier.stein.loader.api.spi.Host;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * An option the player tweaks: a button that toggles or cycles through values (left button
 * advances, right goes back), an action, or free text (a field). Also the game's formatting colors.
 *
 * <p>It serves the two places where the player tweaks options: those of a HUD element (in the grid
 * editor) and those of a mod on its {@link Page page}, in the Panel (it was born as HudOption, only
 * for the HUD; the name stayed generic when it started serving the pages too).
 */
public abstract class Option {

    /** Game color codes (0-9, a-f). */
    public static final String CODES = "0123456789abcdef";

    /** How long a click that needs confirmation stays armed. */
    private static final long CONFIRM_NANOS = 3_000_000_000L;

    /** Mouse buttons are stored as negative key codes: button 0 (left) is -100. */
    private static final int MOUSE_KEY_OFFSET = 100;

    /** Color of each code, without alpha. */
    public static final int[] RGB = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };

    public final String label;

    /**
     * An option for those who already know what they want: the Panel keeps it behind "More
     * options", in its own section. The default is false — only what was marked goes there.
     */
    private boolean advanced;

    protected Option(String label) {
        this.label = label;
    }

    /**
     * The key that does the same as this option, on its own row ({@link #withKey}); null if none.
     */
    private Key key;

    /**
     * Puts a key picker on the option's own row, next to the control: a click on the key label
     * waits for the next key (Esc gives up), right click clears it. It is the key that turns a
     * feature on and off sitting next to it, without a separate key list. Returns this option, for
     * chaining.
     */
    @Experimental(since = "0.1.19")
    public Option withKey(IntSupplier get, IntConsumer set) {
        key = new Key(label, get, set);
        return this;
    }

    /** The row's check mark ({@link #withCheck}); null if none. */
    private Check check;

    /**
     * A small checkbox on the option's own row, next to the key: a click toggles it, and {@code
     * hint} shows beside it with the mouse over it. For a small choice that belongs to the option
     * (e.g. show the feature in a HUD list), without an extra line in the Panel. Returns this
     * option, for chaining.
     */
    @Experimental(since = "0.1.22")
    public Option withCheck(String hint, BooleanSupplier get, Consumer<Boolean> set) {
        check = new Check(hint, get, set);
        return this;
    }

    /** The row's check mark ({@link #withCheck}), or null. */
    @Experimental(since = "0.1.22")
    public Check check() {
        return check;
    }

    /** The checkbox of {@link #withCheck}. */
    @Experimental(since = "0.1.22")
    public static final class Check {
        private final String hint;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        Check(String hint, BooleanSupplier get, Consumer<Boolean> set) {
            this.hint = hint;
            this.get = get;
            this.set = set;
        }

        public String hint() {
            return hint;
        }

        public boolean isOn() {
            return get.getAsBoolean();
        }

        public void toggle() {
            set.accept(!get.getAsBoolean());
        }
    }

    /** When the option appears ({@link #visibleWhen}); null = always. */
    private BooleanSupplier visible;

    /**
     * The option only appears while {@code when} is true (the Panel checks it every frame): for a
     * group of options that only makes sense in one mode (those of each piece, in per-piece mode).
     * Returns this option, for chaining.
     */
    @Experimental(since = "0.1.19")
    public Option visibleWhen(BooleanSupplier when) {
        visible = when;
        return this;
    }

    /** Does the option appear now? */
    @Experimental(since = "0.1.19")
    public boolean isVisible() {
        return visible == null || visible.getAsBoolean();
    }

    /** The row's key ({@link #withKey}), or null. */
    @Experimental(since = "0.1.19")
    public Key key() {
        return key;
    }

    /**
     * Option to choose between values (cycle, color): the Panel shows the back and forward arrows.
     * Actions and texts do not.
     */
    @Experimental(since = "0.1.20")
    public boolean isStepped() {
        return false;
    }

    /** Marks this option as advanced and returns it, for chaining at creation. */
    public Option advanced() {
        advanced = true;
        return this;
    }

    public boolean isAdvanced() {
        return advanced;
    }

    /**
     * Is it a section's "Restore defaults"? The screen draws it with an icon and in another tone.
     */
    public boolean isRestore() {
        return false;
    }

    public abstract String value();

    /** Left button: next value; right: previous. */
    public abstract void click(boolean forward);

    /**
     * On or off, for those that only have two states; null for the others. The Panel uses it to
     * summarize a closed section ("9 of 14") without needing to know what each option is.
     */
    public Boolean state() {
        return null;
    }

    public String text() {
        return label + ": " + value();
    }

    /** Index (0-15) of a color code ("f", "c"...); unknown = white. */
    public static int colorIndex(String code) {
        int i =
                code == null || code.isEmpty()
                        ? -1
                        : CODES.indexOf(Character.toLowerCase(code.charAt(0)));
        return i < 0 ? CODES.length() - 1 : i;
    }

    public static int rgb(String code) {
        return RGB[colorIndex(code)];
    }

    /** Formatting prefix (section sign + code) for a colored string. */
    public static String fmt(String code) {
        return "§" + CODES.charAt(colorIndex(code));
    }

    /** Name of the color, in the game's language. */
    public static String colorName(String code) {
        return Lang.tr("steinloader.color." + CODES.charAt(colorIndex(code)));
    }

    public static Option toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return new Option(label) {
            @Override
            public String value() {
                return get.getAsBoolean() ? "§a" + Lang.tr("gui.yes") : "§c" + Lang.tr("gui.no");
            }

            @Override
            public Boolean state() {
                return get.getAsBoolean();
            }

            @Override
            public void click(boolean forward) {
                set.accept(!get.getAsBoolean());
            }
        };
    }

    public static Option cycle(String label, String[] names, IntSupplier get, IntConsumer set) {
        return new Option(label) {
            @Override
            public boolean isStepped() {
                return true;
            }

            @Override
            public String value() {
                int i = get.getAsInt();
                return i >= 0 && i < names.length ? names[i] : "?";
            }

            @Override
            public void click(boolean forward) {
                int n = names.length;
                set.accept(((get.getAsInt() + (forward ? 1 : -1)) % n + n) % n);
            }
        };
    }

    public static Option color(String label, Supplier<String> get, Consumer<String> set) {
        return new Option(label) {
            @Override
            public boolean isStepped() {
                return true;
            }

            @Override
            public String value() {
                return fmt(get.get()) + colorName(get.get());
            }

            @Override
            public void click(boolean forward) {
                int i =
                        (colorIndex(get.get()) + (forward ? 1 : CODES.length() - 1))
                                % CODES.length();
                set.accept(String.valueOf(CODES.charAt(i)));
            }
        };
    }

    public static Option action(String label, String value, Runnable run) {
        return new Option(label) {
            @Override
            public String value() {
                return value;
            }

            @Override
            public void click(boolean forward) {
                run.run();
            }
        };
    }

    /**
     * "Restore defaults", the same name and the same way everywhere: the first click asks for
     * confirmation ("Confirm?" for 3 seconds) and only the second one runs — returning everything
     * to default by an accidental click would erase what the player took time to choose. Each Panel
     * section has its own, right at its end.
     */
    public static Option restore(Runnable run) {
        return new Option(Lang.tr("steinloader.restore")) {
            private long until;

            @Override
            public boolean isRestore() {
                return true;
            }

            @Override
            public String value() {
                return System.nanoTime() < until ? "§e" + Lang.tr("steinloader.confirm") : "";
            }

            @Override
            public void click(boolean forward) {
                if (System.nanoTime() < until) {
                    until = 0;
                    run.run();
                } else {
                    until = System.nanoTime() + CONFIRM_NANOS;
                }
            }
        };
    }

    /**
     * Number between {@code min} and {@code max}, in steps of {@code step}: a track that is dragged
     * in the Panel. On any screen, left click goes up one step and right click goes down (in the
     * HUD editor, which does not drag).
     *
     * @param format the value's text (e.g. {@code v -> (int) (v * 100) + "%"}); null shows the
     *     number
     */
    @Experimental(since = "0.1.15")
    public static Slider slider(
            String label,
            double min,
            double max,
            double step,
            DoubleSupplier get,
            DoubleConsumer set,
            DoubleFunction<String> format) {
        return new Slider(label, min, max, step, get, set, format);
    }

    /**
     * Choose a key: the click waits for the next key, Esc gives up, right click clears it (0 =
     * none).
     */
    @Experimental(since = "0.1.15")
    public static Key key(String label, IntSupplier get, IntConsumer set) {
        return new Key(label, get, set);
    }

    @Experimental(since = "0.1.15")
    public static final class Slider extends Option {
        public final double min;
        public final double max;
        public final double step;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final DoubleFunction<String> format;

        Slider(
                String label,
                double min,
                double max,
                double step,
                DoubleSupplier get,
                DoubleConsumer set,
                DoubleFunction<String> format) {
            super(label);
            this.min = min;
            this.max = Math.max(min, max);
            this.step = step > 0 ? step : 0;
            this.get = get;
            this.set = set;
            this.format = format;
        }

        public double get() {
            return get.getAsDouble();
        }

        /** Stores the value clamped to the range and the step. */
        public void set(double v) {
            double c = Math.max(min, Math.min(max, v));
            if (step > 0) {
                c = Math.max(min, Math.min(max, min + Math.round((c - min) / step) * step));
            }
            set.accept(c);
        }

        /** Where the value is on the track, from 0 to 1. */
        public float fraction() {
            return max == min ? 0 : (float) ((get() - min) / (max - min));
        }

        /** Stores by position on the track (0 to 1): the drag. */
        public void setFraction(float f) {
            set(min + Math.max(0, Math.min(1, f)) * (max - min));
        }

        @Override
        public String value() {
            double v = get();
            if (format != null) {
                return format.apply(v);
            }
            return v == Math.rint(v) && Math.abs(v) < 1e9
                    ? String.valueOf((long) v)
                    : String.format("%.2f", v);
        }

        @Override
        public void click(boolean forward) {
            double by = step > 0 ? step : (max - min) / 20;
            set(get() + (forward ? by : -by));
        }
    }

    @Experimental(since = "0.1.15")
    public static final class Key extends Option {
        private final IntSupplier get;
        private final IntConsumer set;
        private boolean listening;

        Key(String label, IntSupplier get, IntConsumer set) {
            super(label);
            this.get = get;
            this.set = set;
        }

        /** Waiting for the key? The screen sends the next one to {@link #keyPressed}. */
        public boolean isListening() {
            return listening;
        }

        /**
         * The key that arrived while waiting. Esc gives up without changing anything.
         *
         * @return true if the key was used here (the screen does nothing more with it)
         */
        public boolean keyPressed(int keyCode) {
            if (!listening) {
                return false;
            }
            listening = false;
            if (keyCode != Keys.KEY_ESCAPE) {
                set.accept(keyCode);
            }
            return true;
        }

        /**
         * A mouse button while waiting (0 left, 1 right, 2 middle, 3 and 4 the side ones): becomes
         * the key, with the game's mouse code (-100 + button). The left one only gives up (it is
         * the one that clicks in the Panel).
         *
         * @return true if the click was used here
         */
        @Experimental(since = "0.1.22")
        public boolean mousePressed(int button) {
            if (!listening) {
                return false;
            }
            listening = false;
            if (button > 0) {
                set.accept(button - MOUSE_KEY_OFFSET);
            }
            return true;
        }

        /** The key code (0 none; negative, mouse button: -100 + button). */
        @Experimental(since = "0.1.22")
        public int code() {
            return get.getAsInt();
        }

        @Override
        public String value() {
            if (listening) {
                return "§e> " + Lang.tr("steinloader.key.press") + " <";
            }
            int code = get.getAsInt();
            if (code < 0) {
                return Lang.tr("steinloader.key.mouse", code + MOUSE_KEY_OFFSET + 1);
            }
            return code == 0 ? "§7" + Lang.tr("steinloader.key.none") : Host.INSTANCE.keyName(code);
        }

        @Override
        public void click(boolean forward) {
            if (forward) {
                listening = !listening;
            } else {
                listening = false;
                set.accept(0);
            }
        }
    }

    /** Free text (e.g. a list of colors): a text field, not a button. One per element. */
    public static final class Text extends Option {
        public final Supplier<String> get;
        public final Consumer<String> set;
        public final String hint;

        /** Password: the screen shows dots in place of the letters. */
        public final boolean secret;

        public Text(String label, String hint, Supplier<String> get, Consumer<String> set) {
            this(label, hint, get, set, false);
        }

        public Text(
                String label,
                String hint,
                Supplier<String> get,
                Consumer<String> set,
                boolean secret) {
            super(label);
            this.hint = hint;
            this.get = get;
            this.set = set;
            this.secret = secret;
        }

        @Override
        public String value() {
            return get.get();
        }

        @Override
        public void click(boolean forward) {}
    }
}
