/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.jaxrs;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpSerializationProviders;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.jaxrs.HttpJaxRsRouterBuilder;
import io.servicetalk.transport.api.ServerContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import static io.servicetalk.concurrent.api.Single.succeeded;

@Path("/all")
public class JaxRsExampleApplication {
    private static final Logger logger = LoggerFactory.getLogger(JaxRsExampleApplication.class);
    private HttpSerializationProvider serializer = HttpSerializationProviders
            .jsonSerializer(new JacksonSerializationProvider());

    @Path("/a")
    public Single<StreamingHttpResponse> a(@Context StreamingHttpResponseFactory responseFactory) {
        return succeeded(responseFactory.ok().payloadBody(succeeded("a").toPublisher(),
                serializer.serializerFor(String.class)));
    }

    @Path("/b/{a}")
    public Single<StreamingHttpResponse> b(@Nullable @PathParam("a") String a,
                                           @Nullable @QueryParam("b") String b,
                                           @Nullable @HeaderParam("Host") String host,
                                           @Context StreamingHttpResponseFactory responseFactory) {
        if (a == null) {
            return succeeded(responseFactory.badRequest());
        }

        if (b == null) {
            return succeeded(responseFactory.badRequest());
        }

        Example example = new Example();
        example.a = a;
        example.b = b;
        example.host = host;

        return succeeded(responseFactory.ok().payloadBody(succeeded(example).toPublisher(),
                serializer.serializerFor(Example.class)));
    }

    public static class Example {
        public String a;
        public String b;
        public String host;
    }

    public static void main(String[] args) throws Exception {
        final JaxRsExampleApplication resource = new JaxRsExampleApplication();
        final Application application = new Application() {

            @Override
            public Set<Object> getSingletons() {

                return Collections.singleton(resource);
            }
        };

        final StreamingHttpService service = new HttpJaxRsRouterBuilder()
                .from(application);

        ServerContext serverContext = HttpServers.forPort(8080)
                .listenStreamingAndAwait(service);

        logger.info("Listening on {}", serverContext.listenAddress());

        // Blocks and awaits shutdown of the server this ServerContext represents.
        serverContext.awaitShutdown();

    }
}