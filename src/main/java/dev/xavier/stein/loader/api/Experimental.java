/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * May still change in a minor Loader version: its shape, its name or the moment it is called. The
 * rest of the API only changes with notice, and without breaking mods compiled against it (the API
 * compatibility check verifies this on every build).
 *
 * <p>Feel free to use it, but expect to adjust the mod when the Loader is upgraded.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Experimental {

    /** The Loader version in which it was introduced. */
    String since();
}
