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
package io.servicetalk.http.router.jersey.resources;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.serialization.api.DefaultSerializer;
import io.servicetalk.serialization.api.Serializer;
import io.servicetalk.serialization.api.TypeHolder;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.router.jersey.TestUtils.asChunkPublisher;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.accepted;
import static javax.ws.rs.core.Response.status;

/**
 * Asynchronous (in JAX-RS lingo) resources.
 */
public abstract class AbstractAsynchronousResources {
    public static final String PATH = "/async";

    protected static final Serializer SERIALIZER = new DefaultSerializer(new JacksonSerializationProvider());
    protected static final TypeHolder<Map<String, Object>> STRING_OBJECT_MAP_TYPE =
            new TypeHolder<Map<String, Object>>() {
            };

    @Context
    protected ConnectionContext ctx;

    @TestFiltered
    @Path("/completable")
    @GET
    public Completable getCompletableOut(@QueryParam("fail") final boolean fail) {
        return Completable.defer(() -> fail ? Completable.error(DELIBERATE_EXCEPTION) : completed());
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-buf-sglin-sglout")
    @POST
    public Single<Buffer> postJsonBufSingleInSingleOut(@QueryParam("fail") final boolean fail,
                                                       final Single<Buffer> requestContent) {
        final BufferAllocator allocator = ctx.getExecutionContext().getBufferAllocator();

        return fail ? defer(() -> error(DELIBERATE_EXCEPTION)) :
                requestContent.map(buf -> {
                    final Map<String, Object> responseContent =
                            new HashMap<>(SERIALIZER.deserializeAggregatedSingle(buf, STRING_OBJECT_MAP_TYPE));
                    responseContent.put("foo", "bar6");
                    return SERIALIZER.serialize(responseContent, allocator);
                });
    }

    @Produces(TEXT_PLAIN)
    @Path("/single-response")
    @GET
    public Single<Response> getResponseSingle(final @QueryParam("fail") boolean fail) {
        return ctx.getExecutionContext().getExecutor().timer(10, MILLISECONDS)
                .andThen(fail ? error(DELIBERATE_EXCEPTION) : success(accepted("DONE").build()));
    }

    @Produces(TEXT_PLAIN)
    @Path("/single-response-pub-entity")
    @GET
    public Single<Response> getResponseSinglePublisherEntity(@QueryParam("i") final int i) {
        final BufferAllocator allocator = ctx.getExecutionContext().getBufferAllocator();
        return ctx.getExecutionContext().getExecutor().timer(10, MILLISECONDS)
                .andThen(defer(() -> {
                    final String contentString = "GOT: " + i;
                    final Publisher<HttpPayloadChunk> responseContent = asChunkPublisher(contentString, allocator);

                    // Wrap content Publisher to capture its generic type (i.e. HttpPayloadChunk)
                    final GenericEntity<Publisher<HttpPayloadChunk>> entity =
                            new GenericEntity<Publisher<HttpPayloadChunk>>(responseContent) {
                            };

                    return success(status(i)
                            // We know the content length so we set it, otherwise the response is chunked
                            .header(CONTENT_LENGTH, contentString.length())
                            .entity(entity)
                            .build());
                }));
    }

    @Produces(APPLICATION_JSON)
    @Path("/single-map")
    @GET
    public Single<Map<String, Object>> getMapSingle(final @QueryParam("fail") boolean fail) {
        return ctx.getExecutionContext().getExecutor().timer(10, MILLISECONDS)
                .andThen(fail ? error(DELIBERATE_EXCEPTION) : success(singletonMap("foo", "bar4")));
    }
}
