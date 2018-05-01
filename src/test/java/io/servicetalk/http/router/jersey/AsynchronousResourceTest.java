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
import io.servicetalk.http.api.HttpResponseStatuses;

import org.junit.Test;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.router.jersey.TestUtil.assertResponse;
import static io.servicetalk.http.router.jersey.TestUtil.getContentAsString;
import static io.servicetalk.http.router.jersey.TestUtil.newH11Request;
import static io.servicetalk.http.router.jersey.resources.AsynchronousResources.PATH;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class AsynchronousResourceTest extends AbstractResourceTest {
    @Override
    String getResourcePath() {
        return PATH;
    }

    @Test(expected = DeliberateException.class)
    public void failedText() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/failed-text");
        handler.apply(req);
    }

    @Test
    public void cancelledDelayedText() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/failed-text?cancel=true");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, SERVICE_UNAVAILABLE, null, "");
    }

    @Test
    public void getDelayedText() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/delayed-text?delay=10&unit=MILLISECONDS");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void rsCancelDelayedText() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/delayed-text?delay=1&unit=DAYS");
        service.handle(ctx, req)
                .subscribe(res -> fail("No response expected, got: " + res))
                .cancel();
    }

    @Override
    public void getJson() {
        // TODO remove after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved
    }

    @Override
    public void putJsonResponse() {
        // TODO remove after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved
    }

    @Test
    public void resumeSuspended() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/suspended/resume");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void cancelSuspended() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/suspended/cancel");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, SERVICE_UNAVAILABLE, null, "");
    }

    @Test
    public void setTimeOutResumeSuspended() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/suspended/timeout-resume");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void setTimeOutExpire() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/suspended/timeout-expire");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, SERVICE_UNAVAILABLE, null, "");
    }

    @Test
    public void setTimeOutExpireHandled() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/suspended/timeout-expire-handled");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, GATEWAY_TIMEOUT, null, "");
    }

    @Test
    public void setTimeOutResumed() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/suspended/resume-timeout");
        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        // Jersey catches and logs the exception that is raised internally when attempting
        // to set a timeout on the resumed request ; and just proceeds with normal response handling
        assertResponse(res, OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void rsCancelSuspended() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(GET, getResourcePath() + "/suspended/busy");
        service.handle(ctx, req)
                .subscribe(res -> fail("No response expected, got: " + res))
                .cancel();
    }

    @Test
    public void sseStream() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/sse/stream");

        final String messages = getContentAsString(service.handle(ctx, req)
                .flatmapPublisher(res -> {
                    assertThat(res.getStatus(), is(HttpResponseStatuses.OK));
                    assertThat(res.getHeaders().get(CONTENT_TYPE), is(SERVER_SENT_EVENTS));
                    return res.getPayloadBody();
                }));

        assertThat(messages, is(range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())));
    }

    @Test
    public void sseBroadcast() {
        final HttpRequest<HttpPayloadChunk> req = newH11Request(GET, getResourcePath() + "/sse/broadcast");

        final String messages = getContentAsString(service.handle(ctx, req)
                .flatmapPublisher(res -> {
                    assertThat(res.getStatus(), is(HttpResponseStatuses.OK));
                    assertThat(res.getHeaders().get(CONTENT_TYPE), is(SERVER_SENT_EVENTS));
                    return res.getPayloadBody();
                }));

        assertThat(messages,
                is("data: bar\n\n" + range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())));
    }

    // TODO test support for Single responses
}
