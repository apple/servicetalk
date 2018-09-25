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
package io.servicetalk.http.router.jersey.internal;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.router.jersey.BufferPublisherInputStream;

import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.message.internal.InboundMessageContext;
import org.reactivestreams.Subscriber;

import java.io.Closeable;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.xml.transform.Source;

import static java.util.Objects.requireNonNull;

/**
 * When request's contents are not buffered, Jersey's determines if the entity body input stream that backs a request
 * can be closed by introspecting the return type of the {@link MessageBodyReader}. If the return type is
 * a {@link Closeable} or a {@link Source} then it doesn't close it, otherwise it closes it.
 * <p>
 * We have {@link MessageBodyReader}s that deliver content as RS sources: in that case we do not want Jersey to close
 * the backing input stream because the content has not been consumed yet when exiting
 * the {@link MessageBodyReader#readFrom(Class, Type, Annotation[], MediaType, MultivaluedMap, InputStream)} method.
 * <p>
 * Since RS sources don't implement {@link Closeable} nor {@link Source}, this class provides wrappers that implement
 * the latter, allowing us to prevent an untimely closure of the entity input stream.
 * <p>
 * Note that this is only necessary when a user-provided entity input stream is used, which can only happen when
 * a filter or interceptor has replaced the one we've put in place at request creation time.
 * Indeed, the {@code close()} method of the entity stream we initially provide ({@link BufferPublisherInputStream})
 * is a no-op so it doesn't matter if it gets called.
 *
 * @see InboundMessageContext#readEntity(Class, Type, Annotation[], PropertiesDelegate)
 */
public final class SourceWrappers {
    /**
     * A {@link Publisher} that is also a {@link Source}.
     *
     * @param <T> Type of items emitted.
     */
    public static final class PublisherSource<T> extends Publisher<T> implements Source {
        private final Publisher<T> original;

        @Nullable
        private String systemId;

        /**
         * Creates a new {@link PublisherSource} instance.
         *
         * @param original the original {@link Publisher} to wrap.
         */
        public PublisherSource(final Publisher<T> original) {
            this.original = requireNonNull(original);
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super T> subscriber) {
            original.subscribe(subscriber);
        }

        @Override
        public void setSystemId(final String systemId) {
            this.systemId = systemId;
        }

        @Nullable
        @Override
        public String getSystemId() {
            return systemId;
        }
    }

    /**
     * A {@link Single} that is also a {@link Source}.
     *
     * @param <T> Type of items emitted.
     */
    public static final class SingleSource<T> extends Single<T> implements Source {
        private final Single<T> original;

        @Nullable
        private String systemId;

        /**
         * Creates a new {@link SingleSource} instance.
         *
         * @param original the original {@link Single} to wrap.
         */
        public SingleSource(final Single<T> original) {
            this.original = requireNonNull(original);
        }

        @Override
        protected void handleSubscribe(final Subscriber<? super T> subscriber) {
            original.subscribe(subscriber);
        }

        @Override
        public void setSystemId(final String systemId) {
            this.systemId = systemId;
        }

        @Nullable
        @Override
        public String getSystemId() {
            return systemId;
        }
    }

    private SourceWrappers() {
        // no instances
    }
}
