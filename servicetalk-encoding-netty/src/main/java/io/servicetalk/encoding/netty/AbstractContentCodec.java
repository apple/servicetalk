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
package io.servicetalk.encoding.netty;

import io.servicetalk.encoding.api.ContentCodec;

import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.buffer.api.CharSequences.contentEquals;
import static java.util.Objects.requireNonNull;

abstract class AbstractContentCodec implements ContentCodec {

    private final CharSequence name;

    AbstractContentCodec(final CharSequence name) {
        requireNonNull(name);
        if (name.length() == 0) {
            throw new IllegalArgumentException("Name length " + name.length() + " (expected > 0).");
        }

        this.name = name;
    }

    public final CharSequence name() {
        return name;
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof AbstractContentCodec)) {
            return false;
        }

        final AbstractContentCodec that = (AbstractContentCodec) o;
        return contentEquals(name, that.name);
    }

    @Override
    public final int hashCode() {
        return caseInsensitiveHashCode(name);
    }

    @Override
    public String toString() {
        return "ContentCodec{" +
                "name=" + name +
                '}';
    }
}
