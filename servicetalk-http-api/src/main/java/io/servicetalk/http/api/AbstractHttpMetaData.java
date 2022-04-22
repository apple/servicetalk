/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.internal.DefaultContextMap;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.encoding.api.ContentCodec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for {@link HttpMetaData}.
 */
public abstract class AbstractHttpMetaData implements HttpMetaData {
    @Deprecated
    @Nullable
    private ContentCodec encoding;
    private HttpProtocolVersion version;
    private final HttpHeaders headers;
    @Nullable
    private ContextMap context;

    public final List<Throwable> contextAssigned = new CopyOnWriteArrayList<>();

    AbstractHttpMetaData(final HttpProtocolVersion version, final HttpHeaders headers,
                         @Nullable final ContextMap context) {
        this.version = requireNonNull(version);
        this.headers = requireNonNull(headers);
        this.context = context;
        if (context != null) {
            contextAssigned.add(new Throwable("context=" + context +
                    " on " + Thread.currentThread().getName() +
                    " at " + System.nanoTime() +
                    " for " + Integer.toHexString(System.identityHashCode(this))));
        }
    }

    @Override
    public final HttpProtocolVersion version() {
        return version;
    }

    @Override
    public HttpMetaData version(final HttpProtocolVersion version) {
        this.version = requireNonNull(version);
        return this;
    }

    @Deprecated
    @Override
    public HttpMetaData encoding(final ContentCodec encoding) {
        this.encoding = requireNonNull(encoding);
        return this;
    }

    @Deprecated
    @Override
    public ContentCodec encoding() {
        return encoding;
    }

    @Override
    public final HttpHeaders headers() {
        return headers;
    }

    /**
     * An internal overload that returns {@link ContextMap} as-is, without allocating a new one.
     */
    @Nullable
    final ContextMap context0() {
        return context;
    }

    @Nonnull
    @Override
    public final ContextMap context() {
        if (context == null) {
            context = new DefaultContextMap();
            contextAssigned.add(new Throwable("context=" + context +
                    " on " + Thread.currentThread().getName() +
                    " at " + System.nanoTime() +
                    " for " + Integer.toHexString(System.identityHashCode(this))));
        }
        return context;
    }

    @Override
    public HttpMetaData context(final ContextMap context) {
        this.context = requireNonNull(context);
        contextAssigned.add(new Throwable("context=" + context +
                " on " + Thread.currentThread().getName() +
                " at " + System.nanoTime() +
                " for " + Integer.toHexString(System.identityHashCode(this))));
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AbstractHttpMetaData that = (AbstractHttpMetaData) o;

        return version.equals(that.version) && headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        return 31 * version.hashCode() + headers.hashCode();
    }
}
