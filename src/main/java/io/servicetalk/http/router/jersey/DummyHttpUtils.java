/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.router.jersey;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.Iterator;

import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.router.jersey.CharSequenceUtils.ensureTrailingSlash;

/**
 * Temporary HTTP helpers.
 */
final class DummyHttpUtils {
    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";

    private DummyHttpUtils() {
        // no instances
    }

    static void removeHeader(final HttpHeaders headers, final CharSequence name, final CharSequence value) {
        final Iterator<? extends CharSequence> i = headers.getAll(name);
        while (i.hasNext()) {
            if (contentEqualsIgnoreCase(value, i.next())) {
                i.remove();
            }
        }
    }

    static CharSequence getBaseUri(final ConnectionContext ctx, final HttpRequest<HttpPayloadChunk> req) {
        if (req.getRequestTarget().charAt(0) == '/') {
            // origin-form
            // HOST must be present, if not the request is invalid and there's not much we can do to get the authority
            return inferScheme(ctx) + "://" + ensureTrailingSlash(req.getHeaders().get(HOST, ""));
        }
        if (req.getRequestTarget().startsWith(HTTP_PREFIX)) {
            // absolute-form http
            final int i = req.getRequestTarget().indexOf('/', HTTP_PREFIX.length());
            return i > 0 ? req.getRequestTarget().substring(0, i + 1) : ensureTrailingSlash(req.getRequestTarget());
        }
        if (req.getRequestTarget().startsWith(HTTPS_PREFIX)) {
            // absolute-form https
            final int i = req.getRequestTarget().indexOf('/', HTTPS_PREFIX.length());
            return i > 0 ? req.getRequestTarget().substring(0, i + 1) : ensureTrailingSlash(req.getRequestTarget());
        }
        if (req.getRequestTarget().equals("*")) {
            // asterisk-form
            return inferScheme(ctx) + "://" + ensureTrailingSlash(ctx.getLocalAddress().toString());
        }
        // authority-form
        return inferScheme(ctx) + "://" + ensureTrailingSlash(req.getRequestTarget());
    }

    private static CharSequence inferScheme(final ConnectionContext ctx) {
        return ctx.getSslSession() != null ? "https" : "http";
    }
}
