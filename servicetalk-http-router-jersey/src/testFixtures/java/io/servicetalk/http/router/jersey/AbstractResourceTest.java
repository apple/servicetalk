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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
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

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public abstract class AbstractResourceTest extends AbstractJerseyStreamingHttpServiceTest {
    private boolean serverNoOffloads;

    void setUp(final boolean serverNoOffloads, final RouterApi api) {
        assertDoesNotThrow(() -> super.setUp(api));
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

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestFilter.class,
                    SynchronousResources.class,
                    AsynchronousResources.class
            ));
        }
    }

    static List<Arguments> data() {
        List<Arguments> params = new ArrayList<>();
        for (RouterApi value : RouterApi.values()) {
            params.add(Arguments.of(false, value));
            params.add(Arguments.of(true, value));
        }

        return params;
    }

    boolean serverNoOffloads() {
        return serverNoOffloads;
    }

    @Override
    void configureBuilders(final HttpServerBuilder serverBuilder,
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

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void notFound(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertNoResponse(head("/not_a_resource"), NOT_FOUND));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void notTranslatedException(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertNoResponse(get("/text?qp=throw-not-translated"), INTERNAL_SERVER_ERROR));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void translatedException(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertNoResponse(get("/text?qp=throw-translated"), CONFLICT));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void implicitHead(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertResponse(head("/text"), OK, TEXT_PLAIN, isEmptyString(), 16));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void explicitHead(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(head("/head"), ACCEPTED, TEXT_PLAIN, isEmptyString(), 123));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void implicitOptions(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res = sendAndAssertResponse(options("/text"),
                    OK, newAsciiString("application/vnd.sun.wadl+xml"), not(isEmptyString()), String::length);

            assertThat(res.headers().get(ALLOW).toString().split(","),
                    is(arrayContainingInAnyOrder("HEAD", "POST", "GET", "OPTIONS")));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getText(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/text"), OK, TEXT_PLAIN, "GOT: null & null");
            sendAndAssertResponse(get("/text?null=true"), NO_CONTENT, null, isEmptyString(), __ -> null);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getTextQueryParam(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/text?qp=foo%20%7Cbar"), OK, TEXT_PLAIN, "GOT: foo |bar & null"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getTextHeaderParam(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(withHeader(get("/text"), "hp", "bar"), OK, TEXT_PLAIN, "GOT: null & bar"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postText(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            // Small payload
            sendAndAssertResponse(post("/text", "foo", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: foo");

            // Large payload that goes above Jersey's default buffer size
            final CharSequence payload = newLargePayload();
            sendAndAssertResponse(post("/text", payload, TEXT_PLAIN), OK, TEXT_PLAIN,
                    is("GOT: " + payload), __ -> null);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postTextNoEntity(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(noPayloadRequest(POST, "/text"), OK, TEXT_PLAIN, "GOT: "));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getTextResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res = sendAndAssertResponse(withHeader(get("/text-response"), "hdr",
                    "bar"), NO_CONTENT, null, isEmptyString(), __ -> null);
            assertThat(res.headers().get("X-Test"), is(newAsciiString("bar")));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postTextResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(withHeader(post("/text-response", "foo", TEXT_PLAIN), "hdr", "bar"),
                                                              ACCEPTED, TEXT_PLAIN, "GOT: foo"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void filtered(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            StreamingHttpResponse res = sendAndAssertResponse(post("/filtered", "foo1", TEXT_PLAIN),
                    OK, TEXT_PLAIN, "GOT: foo1");
            assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));

            res = sendAndAssertNoResponse(withHeader(post("/filtered", "foo2", TEXT_PLAIN),
                    "X-Abort-With-Status", "451"), HttpResponseStatus.of(451, ""));
            assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getJson(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/json"), OK, APPLICATION_JSON, jsonStringEquals("{\"foo\":\"bar0\"}"),
                                                              getJsonResponseContentLengthExtractor()));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJson(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(post("/json", "{\"key\":\"val0\"}", APPLICATION_JSON), OK, APPLICATION_JSON,
                        jsonStringEquals("{\"key\":\"val0\",\"foo\":\"bar1\"}"),
                        getJsonResponseContentLengthExtractor()));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void putJsonResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res =
                    sendAndAssertResponse(put("/json-response", "{\"key\":\"val1\"}", APPLICATION_JSON), ACCEPTED,
                            APPLICATION_JSON, jsonStringEquals("{\"key\":\"val1\",\"foo\":\"bar2\"}"),
                            getJsonResponseContentLengthExtractor());
            assertThat(res.headers().get("X-Test"), is(newAsciiString("test-header")));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getTextBuffer(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertResponse(get("/text-buffer"), OK, TEXT_PLAIN, "DONE"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getTextBufferResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            final StreamingHttpResponse res = sendAndAssertResponse(withHeader(get("/text-buffer-response"), "hdr",
                    "bar"), NON_AUTHORITATIVE_INFORMATION, TEXT_PLAIN, "DONE");
            assertThat(res.headers().get("X-Test"), is(newAsciiString("bar")));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postTextBuffer(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            // Small payload
            sendAndAssertResponse(post("/text-buffer", "foo", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: foo");

            // Large payload that goes above Jersey's default buffer size
            final CharSequence payload = newLargePayload();
            sendAndAssertResponse(post("/text-buffer", payload, TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: " + payload);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJsonBuffer(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        post("/json-buffer", "{\"key\":456}", APPLICATION_JSON), OK, APPLICATION_JSON,
                        jsonStringEquals("{\"got\":{\"key\":456}}"), String::length));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postTextBufferResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(withHeader(post("/text-buffer-response", "foo", TEXT_PLAIN), "hdr", "bar"),
                        ACCEPTED, TEXT_PLAIN, "GOT: foo"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJsonPojoInPojoOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        post("/json-pojoin-pojoout", "{\"aString\":\"val8\",\"anInt\":123}",
                                APPLICATION_JSON), OK, APPLICATION_JSON,
                        jsonEquals("{\"aString\":\"val8x\",\"anInt\":124}"),
                        getJsonResponseContentLengthExtractor()));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postTextBytes(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(post("/text-bytes", "bar", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: bar"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJsonBytes(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        post("/json-bytes", "{\"key\":789}", APPLICATION_JSON), OK, APPLICATION_JSON,
                        jsonStringEquals("{\"got\":{\"key\":789}}"), String::length));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postBadJson(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            // Broken JSON
            sendAndAssertStatusOnly(post("/json-pojoin-pojoout", "{key:789}", APPLICATION_JSON), BAD_REQUEST);

            // Missing mandatory field
            sendAndAssertStatusOnly(post("/json-pojoin-pojoout", "{\"foo\":\"bar\"}", APPLICATION_JSON), BAD_REQUEST);
        });
    }

    static void assumeSafeToDisableOffloading(final boolean serverNoOffloads, final RouterApi api) {
        assumeFalse(
            serverNoOffloads && (api == RouterApi.BLOCKING_STREAMING), "BlockingStreaming + noOffloads = deadlock");
    }
}
