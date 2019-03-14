/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * An {@link HttpPayloadWriter} that delegates all method calls to another {@link HttpPayloadWriter}.
 *
 * @param <T> the type of element to write
 */
public abstract class DelegatingHttpPayloadWriter<T> implements HttpPayloadWriter<T> {

    private final HttpPayloadWriter<T> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link HttpPayloadWriter} to which all method calls will be delegated
     */
    protected DelegatingHttpPayloadWriter(final HttpPayloadWriter<T> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Get the {@link HttpPayloadWriter} that this class delegates to.
     *
     * @return the {@link HttpPayloadWriter} that this class delegates to
     */
    public final HttpPayloadWriter<T> delegate() {
        return delegate;
    }

    @Override
    public void write(final T object) throws IOException {
        delegate.write(object);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public HttpHeaders trailers() {
        return delegate.trailers();
    }
}
