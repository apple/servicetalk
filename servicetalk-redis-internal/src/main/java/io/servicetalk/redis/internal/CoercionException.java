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
package io.servicetalk.redis.internal;

/**
 * Exception throws when a data type can not be converted to an intended type.
 */
public final class CoercionException extends RuntimeException {
    private static final long serialVersionUID = 3800561374199643550L;

    /**
     * New instance.
     * @param source Data.
     * @param type Intended type.
     */
    public CoercionException(final Object source, final Class<?> type) {
        super("Failed to coerce: " + source + " to: " + type);
    }
}
