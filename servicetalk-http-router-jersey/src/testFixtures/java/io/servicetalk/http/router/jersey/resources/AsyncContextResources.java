/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionContext;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.K1;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.K2;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.K3;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.V1;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.V2;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.V3;
import static io.servicetalk.http.router.jersey.resources.AsyncContextResources.PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

/**
 * Verifies {@link AsyncContext} visibility for different use-cases, because Jersey may have different logic to handling
 * different endpoints.
 */
@Path(PATH)
public class AsyncContextResources {
    public static final String PATH = "/asyncContext";

    @Context
    private ConnectionContext ctx;

    @GET
    @Path("/noArgsNoReturn")
    public void noArgsNoReturn() {
        AsyncContext.put(K1, V1);
    }

    @GET
    @Path("/getBuffer")
    @Produces(TEXT_PLAIN)
    public Buffer getBuffer() {
        AsyncContext.put(K1, V1);
        return ctx.executionContext().bufferAllocator().fromUtf8("foo");
    }

    @POST
    @Path("/postBuffer")
    @Consumes(TEXT_PLAIN)
    public void postBuffer(Buffer buffer) {
        AsyncContext.put(K1, V1);
    }

    @POST
    @Path("/syncEcho")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    public Buffer syncEcho(Buffer buffer) {
        AsyncContext.put(K1, V1);
        return buffer;
    }

    @POST
    @Path("/syncEchoResponse")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    public Response syncEchoResponse(Buffer buffer) {
        AsyncContext.put(K1, V1);
        return Response.ok(buffer).build();
    }

    @POST
    @Path("/postTextOioStreams")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    public StreamingOutput postTextOioStreams(final InputStream requestContent) {
        AsyncContext.put(K1, V1);
        Scanner s = new Scanner(requestContent, UTF_8.name()).useDelimiter("\\A");
        String result = s.hasNext() ? s.next() : "";
        AsyncContext.put(K2, V2);
        return output -> {
            output.write(result.getBytes(UTF_8));
            output.flush();
        };
    }

    @POST
    @Path("/syncEchoJsonMap")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Map<String, String> syncEchoJsonMap(Map<String, String> requestContent) {
        AsyncContext.put(K1, V1);
        Map<String, String> responseContent = new HashMap<>(requestContent);
        responseContent.put("foo", "bar1");
        return responseContent;
    }

    @GET
    @Path("/completable")
    public Completable completable() {
        AsyncContext.put(K1, V1);
        return completed().beforeOnComplete(() -> AsyncContext.put(K2, V2));
    }

    @GET
    @Path("/getSingleBuffer")
    @Produces(TEXT_PLAIN)
    public Single<Buffer> getSingleBuffer() {
        AsyncContext.put(K1, V1);
        return Single.fromSupplier(() -> ctx.executionContext().bufferAllocator().fromUtf8("foo"))
                .beforeOnSuccess(__ -> AsyncContext.put(K2, V2));
    }

    @POST
    @Path("/postSingleBuffer")
    @Consumes(TEXT_PLAIN)
    public Completable postSingleBuffer(Single<Buffer> in) {
        AsyncContext.put(K1, V1);
        return in.toCompletable().beforeOnComplete(() -> AsyncContext.put(K2, V2));
    }

    @POST
    @Path("/postSingleBufferSync")
    @Consumes(TEXT_PLAIN)
    public void postSingleBufferSync(Single<Buffer> in) {
        AsyncContext.put(K1, V1);
        in.shareContextOnSubscribe().toCompletionStage().toCompletableFuture().join();
        AsyncContext.put(K2, V2);
    }

    @POST
    @Path("/singleEcho")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    public Single<Buffer> singleEcho(Single<Buffer> in) {
        AsyncContext.put(K1, V1);
        return in.beforeOnSuccess(__ -> AsyncContext.put(K2, V2));
    }

    @GET
    @Path("/getPublisherBuffer")
    @Produces(TEXT_PLAIN)
    public Publisher<Buffer> getPublisherBuffer() {
        AsyncContext.put(K1, V1);
        return Publisher.from(ctx.executionContext().bufferAllocator().fromUtf8("foo"))
                .beforeOnComplete(() -> AsyncContext.put(K3, V3));
    }

    @POST
    @Path("/postPublisherBuffer")
    @Consumes(TEXT_PLAIN)
    public Completable postPublisherBuffer(Publisher<Buffer> in) {
        AsyncContext.put(K1, V1);
        return in.ignoreElements().beforeOnComplete(() -> AsyncContext.put(K2, V2));
    }

    @POST
    @Path("/publisherEcho")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    public Publisher<Buffer> publisherEcho(Publisher<Buffer> in) {
        AsyncContext.put(K1, V1);
        return in.beforeOnComplete(() -> AsyncContext.put(K3, V3));
    }

    @POST
    @Path("/publisherEchoSync")
    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    public Publisher<Buffer> publisherEchoSync(Publisher<Buffer> in) throws Exception {
        AsyncContext.put(K1, V1);
        in.ignoreElements().shareContextOnSubscribe().toFuture().get();
        return Publisher.from(ctx.executionContext().bufferAllocator().fromUtf8("foo"))
                .beforeOnComplete(() -> AsyncContext.put(K3, V3));
    }

    @GET
    @Path("/getCompletionStage")
    @Produces(TEXT_PLAIN)
    public CompletionStage<String> getCompletionStage() {
        AsyncContext.put(K1, V1);
        return ctx.executionContext().executor().submit(() -> "foo")
                .shareContextOnSubscribe()  // Users have to share the context manually
                .toCompletionStage()
                .thenApply(str -> {
                    AsyncContext.put(K2, V2);
                    return str;
                });
    }

    @GET
    @Path("/getCompletionStageCompleteWithStExecutor")
    @Produces(TEXT_PLAIN)
    public CompletionStage<String> getCompletionStageCompleteWithStExecutor() {
        AsyncContext.put(K1, V1);
        CompletableFuture<String> future = new CompletableFuture<>();
        ctx.executionContext().executor().schedule(() -> future.complete("foo"), Duration.ofMillis(100));
        return future.thenApply(str -> {
            AsyncContext.put(K2, V2);
            return str;
        });
    }
}
