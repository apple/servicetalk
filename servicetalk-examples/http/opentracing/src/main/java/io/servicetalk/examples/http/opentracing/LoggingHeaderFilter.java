/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.opentracing;

import io.servicetalk.http.api.HttpMetaData;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

/**
 * Header filter for {@link HttpMetaData#toString(BiFunction)} used when logging requests in these examples.
 */
final class LoggingHeaderFilter {
    // Allowlist of non-sensitive headers safe to log: content metadata plus the x-b3-* trace headers these tracing
    // examples demonstrate. Everything else (credentials, cookies, ...) is filtered.
    private static final Set<String> LOGGABLE_HEADERS = unmodifiableSet(new HashSet<>(asList(
            CONTENT_TYPE.toString(), CONTENT_LENGTH.toString(), TRANSFER_ENCODING.toString(),
            "x-b3-traceid", "x-b3-spanid", "x-b3-parentspanid", "x-b3-sampled")));

    static final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> INSTANCE = (name, value) ->
            LOGGABLE_HEADERS.contains(name.toString().toLowerCase(Locale.US)) ? value : "<filtered>";

    private LoggingHeaderFilter() {
        // No instances.
    }
}
