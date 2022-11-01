/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry;

import io.servicetalk.http.api.HttpHeaders;

import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Locale;
import javax.annotation.Nullable;

final class HeadersPropagatorSetter implements TextMapSetter<HttpHeaders> {

    public static final TextMapSetter<HttpHeaders> INSTANCE = new HeadersPropagatorSetter();

    private HeadersPropagatorSetter() {
    }

    @Override
    public void set(@Nullable final HttpHeaders headers, final String key, final String value) {
        if (headers != null) {
            headers.set(key.toLowerCase(Locale.ENGLISH), value);
        }
    }
}
