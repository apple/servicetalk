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
package io.servicetalk.opentracing.http;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.opentracing.inmemory.CharSequenceKeyValueAccessor;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class HttpHeadersKeyValueAccessor implements CharSequenceKeyValueAccessor {

    private final HttpHeaders headers;

    private HttpHeadersKeyValueAccessor(final HttpHeaders headers) {
        this.headers = requireNonNull(headers);
    }

    @Nullable
    @Override
    public CharSequence get(final CharSequence key) {
        return headers.get(key);
    }

    @Override
    public void set(final CharSequence key, final CharSequence value) {
        headers.set(key, value);
    }

    static CharSequenceKeyValueAccessor accessorOf(final HttpHeaders headers) {
        return new HttpHeadersKeyValueAccessor(headers);
    }
}
