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

import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

final class HeadersPropagatorGetter implements TextMapGetter<HttpHeaders> {

    @Override
    public Iterable<String> keys(final HttpHeaders carrier) {
        Set<String> keys = new HashSet<>();
        for (CharSequence entry : carrier.names()) {
            keys.add(entry.toString());
        }
        return keys;
    }

    @Override
    public String get(@Nullable HttpHeaders carrier, final String key) {
        if (carrier == null) {
            return null;
        }
        if (carrier.contains(key)) {
            return Optional.ofNullable(carrier.get(key)).orElse("").toString();
        }
        return null;
    }
}
