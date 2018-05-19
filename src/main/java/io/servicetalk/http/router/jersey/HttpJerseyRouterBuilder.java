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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpService;

import org.glassfish.jersey.server.ApplicationHandler;

import java.io.InputStream;
import javax.ws.rs.core.Application;

/**
 * Builds an {@link HttpService}{@code <}{@link HttpPayloadChunk}{@code >} which routes requests
 * to JAX-RS annotated classes, using Jersey as the routing engine.
 * eg.
 * <pre>{@code
 * final HttpService router = new HttpJerseyRouterBuilder()
 *     .build(application);
 * }</pre>
 */
public final class HttpJerseyRouterBuilder {
    private int publisherInputStreamQueueCapacity = 16;

    /**
     * Set the hint for the capacity of the intermediary queue that stores items when adapting {@link Publisher}s
     * into {@link InputStream}s.
     *
     * @param publisherInputStreamQueueCapacity the capacity hint.
     * @return this
     */
    public HttpJerseyRouterBuilder setPublisherInputStreamQueueCapacity(final int publisherInputStreamQueueCapacity) {
        if (publisherInputStreamQueueCapacity <= 0) {
            throw new IllegalArgumentException("Invalid queue capacity: " + publisherInputStreamQueueCapacity
                    + " (expected > 0).");
        }
        this.publisherInputStreamQueueCapacity = publisherInputStreamQueueCapacity;
        return this;
    }

    /**
     * Build the {@link HttpService} for the specified JAX-RS {@link Application}.
     *
     * @param application the {@link Application} to route requests to.
     * @return the {@link HttpService}.
     */
    public HttpService build(final Application application) {
        return build(new ApplicationHandler(application));
    }

    /**
     * Build the {@link HttpService} for the specified JAX-RS {@link Application} class.
     *
     * @param applicationClass the {@link Application} class to instantiate and route requests to.
     * @return the {@link HttpService}.
     */
    public HttpService build(final Class<? extends Application> applicationClass) {
        return build(new ApplicationHandler(applicationClass));
    }

    /**
     * Build the {@link HttpService} for the specified Jersey {@link ApplicationHandler}.
     *
     * @param applicationHandler the {@link ApplicationHandler} to route requests to.
     * @return the {@link HttpService}.
     */
    public HttpService build(final ApplicationHandler applicationHandler) {
        return new DefaultJerseyHttpRouter(applicationHandler, publisherInputStreamQueueCapacity);
    }
}
