/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.data.protobuf.jersey.resources;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.tests.helloworld.HelloReply;
import io.servicetalk.tests.helloworld.HelloRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_X_PROTOBUF;
import static io.servicetalk.data.protobuf.jersey.ProtobufMediaTypes.APPLICATION_X_PROTOBUF_VAR_INT;
import static io.servicetalk.data.protobuf.jersey.resources.PublisherProtobufResources.PATH;
import static javax.ws.rs.core.Response.accepted;

@Path(PATH)
public class PublisherProtobufResources {
    public static final String PATH = "/publisher";

    @POST
    @Path("map")
    @Consumes(APPLICATION_X_PROTOBUF_VAR_INT)
    @Produces(APPLICATION_X_PROTOBUF_VAR_INT)
    public Publisher<HelloReply> postMap(@QueryParam("fail") final boolean fail,
                                         final Publisher<HelloRequest> publisher) {
        return publisher.map(request -> {
            if (fail) {
                throw DELIBERATE_EXCEPTION;
            }
            return HelloReply.newBuilder().setMessage("hello " + request.getName()).build();
        });
    }

    @POST
    @Path("map-single")
    @Consumes(APPLICATION_X_PROTOBUF_VAR_INT)
    @Produces(APPLICATION_X_PROTOBUF)
    public Single<HelloReply> postMapSingle(@QueryParam("fail") final boolean fail,
                                            final Publisher<HelloRequest> publisher) {
        return publisher.collect(StringBuilder::new, (sb, request) -> {
                    if (fail) {
                        throw DELIBERATE_EXCEPTION;
                    }
                    return sb.append(request.getName());
                })
                .map(sb -> HelloReply.newBuilder().setMessage("hello " + sb.toString()).build());
    }

    @POST
    @Path("map-single-response")
    @Consumes(APPLICATION_X_PROTOBUF_VAR_INT)
    @Produces(APPLICATION_X_PROTOBUF)
    public Single<Response> postPublisherSingleResponse(@QueryParam("fail") final boolean fail,
                                                        final Publisher<HelloRequest> publisher) {
        return postMapSingle(fail, publisher).map(m -> accepted(m).build());
    }
}
