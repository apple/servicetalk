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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.router.jersey.AbstractResourceTest.TestFiltered;
import io.servicetalk.http.router.jersey.ExecutionStrategy;

import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.router.jersey.ExecutionStrategy.ExecutorSelector.ROUTER_EXECUTOR;
import static io.servicetalk.http.router.jersey.resources.AbstractAsynchronousResources.PATH;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.accepted;
import static javax.ws.rs.core.Response.noContent;

@Path(PATH)
@ExecutionStrategy(ROUTER_EXECUTOR)
public class AsynchronousResourcesRouterExec extends AbstractAsynchronousResources {
    @Produces(TEXT_PLAIN)
    @Path("/text")
    @GET
    public Single<String> getText(@Nullable @QueryParam("qp") final String qp,
                                  @Nullable @HeaderParam("hp") final String hp) {
        if ("throw-not-translated".equals(qp)) {
            throw DELIBERATE_EXCEPTION;
        } else if ("throw-translated".equals(qp)) {
            throw new WebApplicationException("Deliberate Exception", CONFLICT);
        }

        return success("GOT: " + qp + " & " + hp);
    }

    @Consumes(TEXT_PLAIN)
    @Produces(TEXT_PLAIN)
    @Path("/text")
    @POST
    public Single<String> postText(final String requestContent) {
        return defer(() -> success("GOT: " + requestContent));
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @GET
    public Single<Response> getTextResponse(@Context final HttpHeaders headers) {
        return success(noContent().header("X-Test", headers.getHeaderString("hdr")).build());
    }

    @Produces(TEXT_PLAIN)
    @Path("/text-response")
    @POST
    public Single<Response> postTextResponse(final String requestContent) {
        return ctx.getExecutionContext().getExecutor().submit(() -> accepted("GOT: " + requestContent).build());
    }

    @TestFiltered
    @Produces(TEXT_PLAIN)
    @Path("/filtered")
    @POST
    public Single<String> postFiltered(final String requestContent) {
        return success("GOT: " + requestContent);
    }
}
