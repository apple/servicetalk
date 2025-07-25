/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.opentelemetry.context.propagation.TextMapGetter;

import javax.annotation.Nullable;

final class RequestHeadersPropagatorGetter implements TextMapGetter<RequestInfo> {

    static final TextMapGetter<RequestInfo> INSTANCE = new RequestHeadersPropagatorGetter();

    private RequestHeadersPropagatorGetter() {
    }

    @Override
    public Iterable<String> keys(final RequestInfo carrier) {
        return HeadersPropagatorGetter.INSTANCE.keys(carrier.getMetadata().headers());
    }

    @Override
    @Nullable
    public String get(@Nullable RequestInfo carrier, final String key) {
        if (carrier == null) {
            return null;
        }
        CharSequence value = carrier.getMetadata().headers().get(key);
        return value == null ? null : value.toString();
    }
}
