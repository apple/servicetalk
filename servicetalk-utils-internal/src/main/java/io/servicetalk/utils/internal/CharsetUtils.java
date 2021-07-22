/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.utils.internal;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

/**
 * {@link Charset} utilities.
 */
public final class CharsetUtils {
    private static final Collection<Charset> STANDARD_CHARSETS =
            asList(US_ASCII, ISO_8859_1, UTF_8, UTF_16BE, UTF_16LE, UTF_16);

    private CharsetUtils() {
    }

    /**
     * Get a {@link Collection} of the {@link StandardCharsets}.
     * @return a {@link Collection} of the {@link StandardCharsets}.
     */
    public static Collection<Charset> standardCharsets() {
        return STANDARD_CHARSETS;
    }
}
