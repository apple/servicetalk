/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.NameBinding;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.buffer.internal.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.CONFLICT;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.TestUtils.newLargePayload;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.Response.status;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonStringEquals;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public abstract class AbstractResourceTest extends AbstractNonParameterizedJerseyStreamingHttpServiceTest {
    private final boolean serverNoOffloads;

    public AbstractResourceTest(final boolean serverNoOffloads, final RouterApi api) {
        super(api);
        this.serverNoOffloads = serverNoOffloads;
        assumeSafeToDisableOffloading(serverNoOffloads, api);
    }

    @NameBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TestFiltered {
    }

    @TestFiltered
    @Provider
    public static class TestFilter implements ContainerRequestFilter, ContainerResponseFilter {
        private static final String TEST_PROPERTY_NAME = "fooProp";

        @Override
        public void filter(final ContainerRequestContext requestContext) {
            final String abortStatus = requestContext.getHeaderString("X-Abort-With-Status");
            if (abortStatus != null) {
                requestContext.abortWith(status(Integer.parseInt(abortStatus)).build());
            }
            requestContext.setProperty("fooProp", "barProp");
        }

        @Override
        public void filter(final ContainerRequestContext requestContext,
                           final ContainerResponseContext responseContext) {
            responseContext.getHeaders().add("X-Foo-Prop", requestContext.getProperty(TEST_PROPERTY_NAME));
        }
    }

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestFilter.class,
                    SynchronousResources.class,
                    AsynchronousResources.class
            ));
        }
    }

    @Parameterized.Parameters(name = "{1} server-no-offloads = {0}")
    public static Collection<Object[]> data() {
        List<Object[]> params = new ArrayList<>();
        AbstractJerseyStreamingHttpServiceTest.data().forEach(oa -> {
            params.add(new Object[]{false, oa[0]});
            params.add(new Object[]{true, oa[0]});
        });
        return params;
    }

    protected boolean serverNoOffloads() {
        return serverNoOffloads;
    }

    @Override
    protected void configureBuilders(final HttpServerBuilder serverBuilder,
                                     final HttpJerseyRouterBuilder jerseyRouterBuilder) {
        super.configureBuilders(serverBuilder, jerseyRouterBuilder);
        if (serverNoOffloads) {
            serverBuilder.executionStrategy(noOffloadsStrategy());
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    abstract String resourcePath();

    @Override
    protected String testUri(final String path) {
        return resourcePath() + path;
    }

    @Test
    public void notFound() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(head("/not_a_resource"), NOT_FOUND);
        });
    }

    @Test
    public void notTranslatedException() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/text?qp=throw-not-translated"), INTERNAL_SERVER_ERROR);
        });
    }

    @Test
    public void translatedException() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/text?qp=throw-translated"), CONFLICT);
        });
    }

    @Test
    public void implicitHead() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(head("/text"), OK, TEXT_PLAIN, isEmptyString(), 16);
        });
    }

    @Test
    public void explicitHead() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(head("/head"), ACCEPTED, TEXT_PLAIN, isEmptyString(), 123);
        });
    }

    @Test
    public void implicitOptions() {
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res = sendAndAssertResponse(options("/text"),
                    OK, newAsciiString("application/vnd.sun.wadl+xml"), not(isEmptyString()), String::length);

            assertThat(res.headers().get(ALLOW).toString().split(","),
                    is(arrayContainingInAnyOrder("HEAD", "POST", "GET", "OPTIONS")));
        });
    }

    @Test
    public void getText() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/text"), OK, TEXT_PLAIN, "GOT: null & null");
            sendAndAssertResponse(get("/text?null=true"), NO_CONTENT, null, isEmptyString(), __ -> null);
        });
    }

    @Test
    public void getTextQueryParam() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/text?qp=foo%20%7Cbar"), OK, TEXT_PLAIN, "GOT: foo |bar & null");
        });
    }

    @Test
    public void getTextHeaderParam() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(withHeader(get("/text"), "hp", "bar"), OK, TEXT_PLAIN, "GOT: null & bar");
        });
    }

    @Test
    public void postText() {
        runTwiceToEnsureEndpointCache(() -> {
            // Small payload
            sendAndAssertResponse(post("/text", "foo", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: foo");

            // Large payload that goes above Jersey's default buffer size
            final CharSequence payload = newLargePayload();
            sendAndAssertResponse(post("/text", payload, TEXT_PLAIN), OK, TEXT_PLAIN,
                    is("GOT: " + payload), __ -> null);
        });
    }

    @Test
    public void postTextNoEntity() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(noPayloadRequest(POST, "/text"), OK, TEXT_PLAIN, "GOT: ");
        });
    }

    @Test
    public void getTextResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res = sendAndAssertResponse(withHeader(get("/text-response"), "hdr",
                    "bar"), NO_CONTENT, null, isEmptyString(), __ -> null);
            assertThat(res.headers().get("X-Test"), is(newAsciiString("bar")));
        });
    }

    @Test
    public void postTextResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(withHeader(post("/text-response", "foo", TEXT_PLAIN), "hdr", "bar"),
                    ACCEPTED, TEXT_PLAIN, "GOT: foo");
        });
    }

    @Test
    public void filtered() {
        runTwiceToEnsureEndpointCache(() -> {
            StreamingHttpResponse res = sendAndAssertResponse(post("/filtered", "foo1", TEXT_PLAIN),
                    OK, TEXT_PLAIN, "GOT: foo1");
            assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));

            res = sendAndAssertNoResponse(withHeader(post("/filtered", "foo2", TEXT_PLAIN),
                    "X-Abort-With-Status", "451"), HttpResponseStatus.of(451, ""));
            assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));
        });
    }

    @Test
    public void getJson() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/json"), OK, APPLICATION_JSON, jsonStringEquals("{\"foo\":\"bar0\"}"),
                    getJsonResponseContentLengthExtractor());
        });
    }

    @Test
    public void postJson() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json", "{\"key\":\"val0\"}", APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonStringEquals("{\"key\":\"val0\",\"foo\":\"bar1\"}"), getJsonResponseContentLengthExtractor());
        });
    }

    @Test
    public void putJsonResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res =
                    sendAndAssertResponse(put("/json-response", "{\"key\":\"val1\"}", APPLICATION_JSON), ACCEPTED,
                            APPLICATION_JSON, jsonStringEquals("{\"key\":\"val1\",\"foo\":\"bar2\"}"),
                            getJsonResponseContentLengthExtractor());
            assertThat(res.headers().get("X-Test"), is(newAsciiString("test-header")));
        });
    }

    @Test
    public void getTextBuffer() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/text-buffer"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void getTextBufferResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res = sendAndAssertResponse(withHeader(get("/text-buffer-response"), "hdr",
                    "bar"), NON_AUTHORITATIVE_INFORMATION, TEXT_PLAIN, "DONE");
            assertThat(res.headers().get("X-Test"), is(newAsciiString("bar")));
        });
    }

    @Test
    public void postTextBuffer() {
        runTwiceToEnsureEndpointCache(() -> {
            // Small payload
            sendAndAssertResponse(post("/text-buffer", "foo", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: foo");

            // Large payload that goes above Jersey's default buffer size
            final CharSequence payload = newLargePayload();
            sendAndAssertResponse(post("/text-buffer", payload, TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: " + payload);
        });
    }

    @Test
    public void postJsonBuffer() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json-buffer", "{\"key\":456}", APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonStringEquals("{\"got\":{\"key\":456}}"), String::length);
        });
    }

    @Test
    public void postTextBufferResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(withHeader(post("/text-buffer-response", "foo", TEXT_PLAIN), "hdr", "bar"),
                    ACCEPTED, TEXT_PLAIN, "GOT: foo");
        });
    }

    @Test
    public void postJsonPojoInPojoOut() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json-pojoin-pojoout", "{\"aString\":\"val8\",\"anInt\":123}",
                    APPLICATION_JSON), OK, APPLICATION_JSON, jsonEquals("{\"aString\":\"val8x\",\"anInt\":124}"),
                    getJsonResponseContentLengthExtractor());
        });
    }

    @Test
    public void postTextBytes() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/text-bytes", "bar", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: bar");
        });
    }

    @Test
    public void postJsonBytes() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json-bytes", "{\"key\":789}", APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonStringEquals("{\"got\":{\"key\":789}}"), String::length);
        });
    }

    @Test
    public void postBadJson() {
        runTwiceToEnsureEndpointCache(() -> {
            // Broken JSON
            sendAndAssertStatusOnly(post("/json-pojoin-pojoout", "{key:789}", APPLICATION_JSON), BAD_REQUEST);

            // Missing mandatory field
            sendAndAssertStatusOnly(post("/json-pojoin-pojoout", "{\"foo\":\"bar\"}", APPLICATION_JSON), BAD_REQUEST);
        });
    }

    static void assumeSafeToDisableOffloading(final boolean serverNoOffloads, final RouterApi api) {
        assumeThat("BlockingStreaming + noOffloads = deadlock",
                serverNoOffloads && (api == RouterApi.BLOCKING_STREAMING), is(false));
    }
}
