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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpHeaders;

import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;

final class HeadersPropagatorGetter implements TextMapGetter<HttpHeaders> {

    static final TextMapGetter<HttpHeaders> INSTANCE = new HeadersPropagatorGetter();

    private HeadersPropagatorGetter() {
    }

    @Override
    public Iterable<String> keys(final HttpHeaders carrier) {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {
                    private final Iterator<Map.Entry<CharSequence, CharSequence>> itr = carrier.iterator();

                    @Override
                    public boolean hasNext() {
                        return itr.hasNext();
                    }

                    @Override
                    public String next() {
                        return itr.next().getKey().toString();
                    }

                    @Override
                    public void remove() {
                        itr.remove();
                    }
                };
            }
        };
    }

    @Override
    @Nullable
    public String get(@Nullable HttpHeaders carrier, final String key) {
        if (carrier == null) {
            return null;
        }
        final CharSequence value = carrier.get(key);
        return value == null ? null : value.toString();
    }
}
