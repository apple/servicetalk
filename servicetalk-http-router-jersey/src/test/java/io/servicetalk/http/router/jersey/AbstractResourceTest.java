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
package io.servicetalk.http.router.jersey;

import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.NameBinding;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.CONFLICT;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.Response.status;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonStringEquals;
import static org.glassfish.jersey.message.internal.CommittingOutputStream.DEFAULT_BUFFER_SIZE;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public abstract class AbstractResourceTest extends AbstractJerseyHttpServiceTest {
    @NameBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(value = RetentionPolicy.RUNTIME)
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
                requestContext.abortWith(status(Integer.valueOf(abortStatus)).build());
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

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }

    abstract String getResourcePath();

    @Override
    protected String testUri(final String path) {
        return getResourcePath() + path;
    }

    @Test
    public void notFound() {
        sendAndAssertNoResponse(head("/not_a_resource"), NOT_FOUND);
    }

    @Test
    public void notTranslatedException() {
        sendAndAssertNoResponse(get("/text?qp=throw-not-translated"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void translatedException() {
        sendAndAssertNoResponse(get("/text?qp=throw-translated"), CONFLICT);
    }

    @Test
    public void implicitHead() {
        sendAndAssertResponse(head("/text"), OK, TEXT_PLAIN, isEmptyString(), 16);
    }

    @Test
    public void implicitOptions() {
        final HttpResponse<HttpPayloadChunk> res = sendAndAssertResponse(options("/text"),
                OK, newAsciiString("application/vnd.sun.wadl+xml"), not(isEmptyString()), String::length);

        assertThat(res.getHeaders().get(ALLOW).toString().split(","),
                is(arrayContainingInAnyOrder("HEAD", "POST", "GET", "OPTIONS")));
    }

    @Test
    public void getText() {
        sendAndAssertResponse(get("/text"), OK, TEXT_PLAIN, "GOT: null & null");
    }

    @Test
    public void getTextQueryParam() {
        sendAndAssertResponse(get("/text?qp=foo%20%7Cbar"), OK, TEXT_PLAIN, "GOT: foo |bar & null");
    }

    @Test
    public void getTextHeaderParam() {
        sendAndAssertResponse(withHeader(get("/text"), "hp", "bar"), OK, TEXT_PLAIN, "GOT: null & bar");
    }

    @Test
    public void postText() {
        // Small payload
        sendAndAssertResponse(post("/text", "foo", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: foo");

        // Large payload that goes above default buffer size
        final String payload = new String(new char[2 * DEFAULT_BUFFER_SIZE]).replace('\0', 'A');
        sendAndAssertResponse(post("/text", payload, TEXT_PLAIN), OK, TEXT_PLAIN, is("GOT: " + payload), $ -> null);
    }

    @Test
    public void postTextNoEntity() {
        sendAndAssertResponse(noPayloadRequest(POST, "/text"), OK, TEXT_PLAIN, "GOT: ");
    }

    @Test
    public void getTextResponse() {
        final HttpResponse<HttpPayloadChunk> res = sendAndAssertResponse(withHeader(get("/text-response"), "hdr", "bar"),
                NO_CONTENT, null, is(""), $ -> null);
        assertThat(res.getHeaders().get("X-Test"), is(newAsciiString("bar")));
    }

    @Test
    public void postTextResponse() {
        sendAndAssertResponse(withHeader(post("/text-response", "foo", TEXT_PLAIN), "hdr", "bar"),
                ACCEPTED, TEXT_PLAIN, "GOT: foo");
    }

    @Test
    public void filtered() {
        HttpResponse<HttpPayloadChunk> res = sendAndAssertResponse(post("/filtered", "foo1", TEXT_PLAIN),
                OK, TEXT_PLAIN, "GOT: foo1");
        assertThat(res.getHeaders().get("X-Foo-Prop"), is(newAsciiString("barProp")));

        res = sendAndAssertNoResponse(withHeader(post("/filtered", "foo2", TEXT_PLAIN), "X-Abort-With-Status", "451"),
                getResponseStatus(451, EMPTY_BUFFER));
        assertThat(res.getHeaders().get("X-Foo-Prop"), is(newAsciiString("barProp")));
    }

    @Test
    public void getJson() {
        sendAndAssertResponse(get("/json"), OK, APPLICATION_JSON,
                jsonStringEquals("{\"foo\":\"bar1\"}"), String::length);
    }

    @Test
    public void putJsonResponse() {
        final HttpResponse<HttpPayloadChunk> res =
                sendAndAssertResponse(put("/json-response", "{\"key\":\"val1\"}", APPLICATION_JSON),
                        ACCEPTED, APPLICATION_JSON, jsonStringEquals("{\"key\":\"val1\",\"foo\":\"bar2\"}"), String::length);
        assertThat(res.getHeaders().get("X-Test"), is(newAsciiString("test-header")));
    }
}
