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
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.router.jaxrs.HttpJaxRsRouterBuilder;
import io.servicetalk.transport.api.ServerContext;

import org.glassfish.jersey.uri.PathTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

import static io.servicetalk.concurrent.api.Single.succeeded;

@Path("/all")
public class JaxRsExampleApplication {
    private static final Logger logger = LoggerFactory.getLogger(JaxRsExampleApplication.class);
    private HttpSerializationProvider serializer = HttpSerializationProviders
            .jsonSerializer(new JacksonSerializationProvider());

    @Path("/a")
    public Single<StreamingHttpResponse> a(HttpServiceContext ctx, StreamingHttpRequest request,
                                    StreamingHttpResponseFactory responseFactory) {
        return succeeded(responseFactory.ok().payloadBody(succeeded("a").toPublisher(),
                serializer.serializerFor(String.class)));
    }

    @Path("/b/{a}/{b}")
    public Single<StreamingHttpResponse> b(HttpServiceContext ctx, StreamingHttpRequest request,
                                    StreamingHttpResponseFactory responseFactory) {
        final Map<String, String> pathParameters = pathParameters("/all/b/{a}/{b}", request);
        final String a = pathParameters.get("a");
        if (a == null) {
            return succeeded(responseFactory.badRequest());
        }

        final String b = pathParameters.get("b");
        if (b == null) {
            return succeeded(responseFactory.badRequest());
        }

        return succeeded(responseFactory.ok().payloadBody(succeeded("b" + a + b).toPublisher(),
                serializer.serializerFor(String.class)));
    }

    private Map<String, String> pathParameters(final String templatePath, final StreamingHttpRequest request) {

        final PathTemplate pathTemplate = new PathTemplate(templatePath);
        final Map<String, String> parameters = new HashMap<>(pathTemplate.getNumberOfTemplateVariables());
        if (!pathTemplate.match(request.path(), parameters)) {
            return Collections.emptyMap();
        }
        return parameters;
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