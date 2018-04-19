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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.router.jersey.TestUtil.asChunkPublisher;
import static io.servicetalk.http.router.jersey.resources.AsynchronousResources.PATH;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.accepted;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;

/**
 * Asynchronous (in JAX-RS lingo) resources.
 */
@Path(PATH)
public class AsynchronousResources {
    public static final String PATH = "/async";

    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public CompletionStage<String> getText(@Nullable @QueryParam("qp") final String qp,
                                           @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return completedFuture("GOT: " + qp + " & " + hp);
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text")
    @POST
    public CompletionStage<String> postText(final String requestContent) {
        return completedFuture("GOT: " + requestContent);
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @GET
    public CompletionStage<Response> getTextResponse(@Context final HttpHeaders headers) {
        return completedFuture(noContent().header("X-Test", headers.getHeaderString("hdr")).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-pub-response")
    @GET
    public CompletionStage<Response> getTextPubResponse(@QueryParam("i") final int i,
                                                        final @Context ConnectionContext ctx) {
        final String contentString = "GOT: " + i;
        final Publisher<HttpPayloadChunk> responseContent = asChunkPublisher(contentString, ctx.getAllocator());
        // Wrap content Publisher to capture its generic type (i.e. HttpPayloadChunk)
        final GenericEntity<Publisher<HttpPayloadChunk>> entity = new GenericEntity<Publisher<HttpPayloadChunk>>(responseContent) {
        };
        return completedFuture(status(i)
                // We know the content length so we set it, otherwise the response is chunked
                .header(CONTENT_LENGTH, contentString.length())
                .entity(entity)
                .build());
    }

    @Produces(APPLICATION_JSON)
    @Path("/json")
    @GET
    public CompletionStage<Map<String, Object>> getJson() {
        return completedFuture(singletonMap("foo", "bar1"));
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/json-response")
    @PUT
    public CompletionStage<Response> putJsonResponse(final Map<String, Object> requestContent) {
        final Map<String, Object> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar2");
        return completedFuture(accepted(responseContent).header("X-Test", "test-header").build());
    }
}
