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
package io.servicetalk.http.router.jersey.resources;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.router.jersey.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.router.jersey.resources.MixedModeResources.PATH;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(PATH)
@Produces(TEXT_PLAIN)
public final class MixedModeResources {
    public static final String PATH = "/mixed-mode";

    @NoOffloadsRouteExecutionStrategy
    @Path("/string")
    @GET
    public String getString() {
        return currentThread().getName();
    }

    @Path("/cs-string")
    @GET
    public CompletionStage<String> getCompletionStageString(@Context ConnectionContext ctx) {
        final String threadName = currentThread().getName();
        final CompletableFuture<String> cfs = new CompletableFuture<>();
        ctx.executionContext().executor().schedule(() -> cfs.complete(threadName), 10, MILLISECONDS);
        return cfs;
    }

    @NoOffloadsRouteExecutionStrategy
    @Path("/single-string")
    @GET
    public Single<String> getSingleString() {
        return succeeded(currentThread().getName());
    }
}
