/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.StreamingHttpRequest;

import org.glassfish.jersey.internal.util.collection.Ref;

import java.io.IOException;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;

import static io.servicetalk.http.api.StreamingHttpRequests.applyAggregationSizeLimit;
import static javax.ws.rs.Priorities.ENTITY_CODER;

/**
 * Bounds the entity read by the aggregating {@link javax.ws.rs.ext.MessageBodyReader}s that consume the request as a
 * blocking {@link java.io.InputStream} (Jersey's built-in {@code String}, {@code byte[]}, and form
 * ({@link Form}/{@link MultivaluedMap}) readers). Such an entity is fully read into memory before the resource method
 * runs, so the application cannot bound it itself. ServiceTalk's own reactive readers bound their payload on the
 * {@link io.servicetalk.concurrent.api.Publisher} path instead, and genuinely streaming reads
 * ({@code InputStream}/{@code Reader}) are intentionally left unbounded.
 * <p>
 * The allowlist deliberately covers only these common built-in aggregating readers. An arbitrary or third-party
 * {@link javax.ws.rs.ext.MessageBodyReader} that aggregates from the blocking {@code InputStream} is not bounded here;
 * bound those at the wire level with a payload-size-limiting HTTP service filter instead.
 */
// ENTITY_CODER priority: ServiceTalk decodes content at the transport layer (not via a JAX-RS interceptor), so the
// bytes counted here are already decoded, matching the reactive path. Ordering vs. an app-registered decoding
// interceptor at the same priority would be undefined.
@Priority(ENTITY_CODER)
final class PayloadSizeLimitingReaderInterceptor implements ReaderInterceptor {

    private final Provider<Ref<StreamingHttpRequest>> requestRefProvider;

    PayloadSizeLimitingReaderInterceptor(
            @Context final Provider<Ref<StreamingHttpRequest>> requestRefProvider) {
        this.requestRefProvider = requestRefProvider;
    }

    @Override
    public Object aroundReadFrom(final ReaderInterceptorContext context) throws IOException, WebApplicationException {
        if (limitApplies(context.getType())) {
            context.setInputStream(
                    applyAggregationSizeLimit(requestRefProvider.get().get(), context.getInputStream()));
        }
        return context.proceed();
    }

    private static boolean limitApplies(final Class<?> type) {
        // Only the built-in readers that aggregate the whole entity into memory: the ServiceTalk reactive readers are
        // bounded on the Publisher path and bypass this InputStream, and raw InputStream/Reader reads are exempt.
        return type == String.class || type == byte[].class || type == Form.class || type == MultivaluedMap.class;
    }
}
