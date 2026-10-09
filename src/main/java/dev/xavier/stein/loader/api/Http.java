/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Breno Xavier
 *
 * Part of the Stein Loader API, licensed under the MIT License (see LICENSE-API).
 */

package dev.xavier.stein.loader.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Text download (lists, JSON), off the game thread and with the callback back on it. Follows
 * redirects, gives up after 15 s and identifies itself as the Stein Loader. An HTTP code outside
 * 2xx arrives as an error.
 */
@Experimental(since = "0.1.16")
public final class Http {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();

    private Http() {}

    /**
     * Downloads the text of {@code url}. {@code onText} and {@code onError} run on the game thread.
     *
     * @param onError may be null: the error goes to the game log
     */
    public static void getText(String url, Consumer<String> onText, Consumer<Throwable> onError) {
        Tasks.async(() -> getTextNow(url), onText, onError);
    }

    /**
     * The same, blocking: only from inside a background task ({@link Tasks#async}), never from the
     * game thread.
     */
    public static String getTextNow(String url) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("User-Agent", "SteinLoader")
                        .GET()
                        .build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }
}
