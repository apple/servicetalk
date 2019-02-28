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
package io.servicetalk.data.jackson.jersey.resources;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.router.jersey.TestPojo;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.data.jackson.jersey.resources.PublisherJsonResources.PATH;
import static java.util.Collections.singletonMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.accepted;

@Path(PATH)
public class PublisherJsonResources {
    public static final String PATH = "/publisher-json";

    @Context
    protected ConnectionContext ctx;

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/map")
    @POST
    public Publisher<Map<String, Object>> postJson(@QueryParam("fail") final boolean fail,
                                                   final Publisher<Map<String, Object>> publisher) {
        return publisher.map(m -> {
            if (fail) {
                throw DELIBERATE_EXCEPTION;
            }
            return singletonMap("got", m);
        });
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/map-response")
    @POST
    public Single<Response> postJsonResponse(@QueryParam("fail") final boolean fail,
                                             final Publisher<Map<String, Object>> publisher) {
        return postJson(fail, publisher).toSingleOrError().map(m -> accepted(m).build());
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/pojo")
    @POST
    public Publisher<TestPojo> postPojo(@QueryParam("fail") final boolean fail,
                                        final Publisher<TestPojo> publisher) {
        return publisher.map(p -> {
            if (fail) {
                throw DELIBERATE_EXCEPTION;
            }
            p.setAnInt(p.getAnInt() + 1);
            p.setaString(p.getaString() + "x");
            return p;
        });
    }

    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/pojo-response")
    @POST
    public Single<Response> postPojoResponse(@QueryParam("fail") final boolean fail,
                                             final Publisher<TestPojo> publisher) {
        return postPojo(fail, publisher).first().map(m -> accepted(m).build());
    }
}
