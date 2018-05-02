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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;

import org.junit.Test;

import static io.servicetalk.buffer.ReadOnlyBufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.PARTIAL_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.http.router.jersey.TestUtil.TEST_HOST;
import static io.servicetalk.http.router.jersey.TestUtil.assertResponse;
import static io.servicetalk.http.router.jersey.TestUtil.newH10Request;
import static io.servicetalk.http.router.jersey.TestUtil.newH11Request;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.glassfish.jersey.message.internal.CommittingOutputStream.DEFAULT_BUFFER_SIZE;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class SynchronousResourceTest extends AbstractResourceTest {
    @Override
    String getResourcePath() {
        return PATH;
    }

    @Test
    public void uriBuilding() {
        HttpRequest<HttpPayloadChunk> req = newH11Request(GET, PATH + "/uris/relative");
        HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "/async/text");

        req = newH11Request(GET, getResourcePath() + "/uris/absolute");
        res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "http://" + TEST_HOST + "/sync/uris/absolute");
    }

    @Test
    public void customResponseStatus() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, PATH + "/statuses/444");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, getResponseStatus(444, DEFAULT_ALLOCATOR.fromAscii("Three fours!")), null, "");
        assertThat(res.getVersion(), is(HTTP_1_1));
    }

    @Test
    public void pathParams() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, PATH + "/matrix/ps;foo=bar1;foo=bar2/params;mp=bar3;mp=bar4");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: foo=bar1,bar2 & ps & bar3,bar4");
    }

    @Test
    public void bogusChunked() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, PATH + "/bogus-chunked");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "foo");
    }

    @Test
    public void servicetalkRequestContext() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, PATH + "/servicetalk-request");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: " + PATH + "/servicetalk-request");
    }

    @Test
    public void http10Support() {
        final HttpRequest<HttpPayloadChunk> req = newH10Request(GET, PATH + "/text");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, HTTP_1_0, OK, TEXT_PLAIN, is("GOT: null & null"), $ -> 16);
    }

    @Test
    public void postTextStrInPubOut() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(POST, PATH + "/text-strin-pubout",
                ctx.getBufferAllocator().fromUtf8("bar2"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: bar2"), $ -> null);
    }

    @Test
    public void postTextPubInStrOut() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(POST, PATH + "/text-pubin-strout",
                ctx.getBufferAllocator().fromUtf8("bar3"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: bar3");
    }

    @Test
    public void postTextPubInPubOut() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(POST, PATH + "/text-pubin-pubout",
                ctx.getBufferAllocator().fromUtf8("bar23"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: bar23"), $ -> null);
    }

    @Test
    public void getTextPubResponse() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET,
                PATH + "/text-pub-response?i=" + PARTIAL_CONTENT.getCode());

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, PARTIAL_CONTENT, TEXT_PLAIN, "GOT: 206");
    }

    @Test
    public void postTextOioStreams() {
        // Small payload
        HttpRequest<HttpPayloadChunk> req = newH11Request(POST, PATH + "/text-oio-streams",
                ctx.getBufferAllocator().fromUtf8("bar4"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: bar4");

        // Large payload that goes above default buffer size
        final String payload = new String(new char[2 * DEFAULT_BUFFER_SIZE]).replace('\0', 'A');
        req = newH11Request(POST, PATH + "/text-oio-streams", ctx.getBufferAllocator().fromUtf8(payload));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: " + payload), $ -> null);
    }

    @Test
    public void postJsonMapInPubOut() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(POST, PATH + "/json-mapin-pubout",
                ctx.getBufferAllocator().fromUtf8("{\"key\":\"val2\"}"));
        req.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val2\",\"foo\":\"bar3\"}"), $ -> null);
    }

    @Test
    public void postJsonPubInMapOut() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(POST, PATH + "/json-pubin-mapout",
                ctx.getBufferAllocator().fromUtf8("{\"key\":\"val3\"}"));
        req.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val3\",\"foo\":\"bar4\"}"),
                String::length);
    }

    @Test
    public void defaultSecurityContext() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, PATH + "/security-context");

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, APPLICATION_JSON,
                jsonEquals("{\"authenticationScheme\":null,\"secure\":false,\"userPrincipal\":null}"),
                String::length);
    }
}
