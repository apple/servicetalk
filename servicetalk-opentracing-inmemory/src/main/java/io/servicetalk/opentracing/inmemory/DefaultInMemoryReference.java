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
package io.servicetalk.opentracing.inmemory;

import io.servicetalk.opentracing.inmemory.api.InMemoryReference;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContext;

import static java.util.Objects.requireNonNull;

final class DefaultInMemoryReference implements InMemoryReference {
    private final String type;
    private final InMemorySpanContext referredTo;

    /**
     * Constructs a reference.
     *
     * @param type       reference type
     * @param referredTo span being referred to
     */
    DefaultInMemoryReference(String type, InMemorySpanContext referredTo) {
        this.type = requireNonNull(type, "type");
        this.referredTo = requireNonNull(referredTo, "referredTo");
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public InMemorySpanContext referredTo() {
        return referredTo;
    }
}
