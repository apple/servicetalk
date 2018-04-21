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

import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.ws.rs.NameBinding;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.buffer.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.http.api.HttpHeaderNames.ALLOW;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpRequestMethods.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpRequestMethods.PUT;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.CONFLICT;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.http.router.jersey.TestUtil.assertResponse;
import static io.servicetalk.http.router.jersey.TestUtil.newH11Request;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.status;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonStringEquals;
import static org.glassfish.jersey.message.internal.CommittingOutputStream.DEFAULT_BUFFER_SIZE;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public abstract class AbstractResourceTest extends AbstractRequestHandlerTest {
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

    public static class TestApplication extends ResourceConfig {
        TestApplication() {
            super(
                    TestFilter.class,
                    SynchronousResources.class,
                    AsynchronousResources.class
            );
        }
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }

    abstract String getResourcePath();

    @Test
    public void notFound() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(HEAD, getResourcePath() + "/not_a_resource");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, NOT_FOUND, null, "");
    }

    @Test(expected = DeliberateException.class)
    public void notTranslatedException() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/text?qp=throw-not-translated");

        handler.apply(ctx, req);
    }

    @Test
    public void translatedException() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text?qp=throw-translated");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, CONFLICT, null, "");
    }

    @Test
    public void implicitHead() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(HEAD, getResourcePath() + "/text");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, isEmptyString(), 16);
    }

    @Test
    public void implicitOptions() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(OPTIONS, getResourcePath() + "/text");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, "application/vnd.sun.wadl+xml", not(isEmptyString()), String::length);
        assertThat(res.getHeaders().get(ALLOW, "").toString().split(","),
                is(arrayContainingInAnyOrder("HEAD", "POST", "GET", "OPTIONS")));
    }

    @Test
    public void getText() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: null & null");
    }

    @Test
    public void getTextQueryParam() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text?qp=foo%20|bar");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: foo |bar & null");
    }

    @Test
    public void getTextHeaderParam() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text");
        req.getHeaders().add("hp", "bar");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: null & bar");
    }

    @Test
    public void postText() {
        // Small payload
        HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, getResourcePath() + "/text", ctx.getAllocator().fromUtf8("foo"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: foo");

        // Large payload that goes above default buffer size
        final String payload = new String(new char[2 * DEFAULT_BUFFER_SIZE]).replace('\0', 'A');
        req = newH11Request(POST, PATH + "/text", ctx.getAllocator().fromUtf8(payload));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: " + payload), $ -> null);
    }

    @Test
    public void getTextResponse() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/text-response");
        req.getHeaders().add("hdr", "bar");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, NO_CONTENT, null, "");
        assertThat(res.getHeaders().get("X-Test"), is("bar"));
    }

    @Test
    public void postTextResponse() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, getResourcePath() + "/text-response", ctx.getAllocator().fromUtf8("foo"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, ACCEPTED, TEXT_PLAIN, "GOT: foo");
    }

    @Test
    public void filtered() {
        HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, getResourcePath() + "/filtered", ctx.getAllocator().fromUtf8("foo1"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: foo1");
        assertThat(res.getHeaders().get("X-Foo-Prop"), is("barProp"));

        req = newH11Request(POST, getResourcePath() + "/filtered", ctx.getAllocator().fromUtf8("foo2"));
        req.getHeaders().set("X-Abort-With-Status", "451");
        res = handler.apply(ctx, req);
        assertResponse(res, getResponseStatus(451, EMPTY_BUFFER), null, "");
    }

    @Test
    public void getJson() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/json");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, OK, APPLICATION_JSON, jsonStringEquals("{\"foo\":\"bar1\"}"), String::length);
    }

    @Test
    public void putJsonResponse() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(PUT, getResourcePath() + "/json-response",
                ctx.getAllocator().fromUtf8("{\"key\":\"val1\"}"));
        req.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(ctx, req);
        assertResponse(res, ACCEPTED, APPLICATION_JSON, jsonStringEquals("{\"key\":\"val1\",\"foo\":\"bar2\"}"),
                String::length);
        assertThat(res.getHeaders().get("X-Test"), is("test-header"));
    }
}
