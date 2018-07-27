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

import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.router.jersey.resources.AsynchronousResources.PATH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class AsynchronousResourceTest extends AbstractResourceTest {
    @Override
    String getResourcePath() {
        return PATH;
    }

    @Test
    public void getVoidCompletion() {
        sendAndAssertResponse(get("/void-completion"), NO_CONTENT, null, is(""), $ -> null);
        sendAndAssertNoResponse(get("/void-completion?defer=true"), OK); // Jersey is inconsistent: should be NO_CONTENT

        sendAndAssertNoResponse(get("/void-completion?fail=true"), INTERNAL_SERVER_ERROR);
        sendAndAssertNoResponse(get("/void-completion?fail=true&defer=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void failedText() {
        sendAndAssertNoResponse(get("/failed-text"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void cancelledDelayedText() {
        sendAndAssertNoResponse(get("/failed-text?cancel=true"), SERVICE_UNAVAILABLE);
    }

    @Test
    public void getDelayedText() {
        sendAndAssertResponse(get("/delayed-text?delay=10&unit=MILLISECONDS"), OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void rsCancelDelayedText() {
        expected.expectCause(instanceOf(TimeoutException.class));
        sendAndAssertResponse(get("/delayed-text?delay=1&unit=DAYS"), OK, TEXT_PLAIN, "DONE", 1, SECONDS);
    }

    @Test
    public void completedStageResponse() {
        sendAndAssertResponse(get("/response-comsta"), OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void delayedStageResponse() {
        sendAndAssertResponse(get("/delayed-response-comsta?delay=10&unit=MILLISECONDS"), OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void rsCancelDelayedDelayedStageResponse() {
        expected.expectCause(instanceOf(TimeoutException.class));
        sendAndAssertResponse(get("/delayed-response-comsta?delay=10&unit=DAYS"), OK, TEXT_PLAIN, "DONE", 1, SECONDS);
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
        sendAndAssertResponse(get("/suspended/resume"), OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void cancelSuspended() {
        sendAndAssertNoResponse(get("/suspended/cancel"), SERVICE_UNAVAILABLE);
    }

    @Test
    public void setTimeOutResumeSuspended() {
        sendAndAssertResponse(get("/suspended/timeout-resume"), OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void setTimeOutExpire() {
        sendAndAssertNoResponse(get("/suspended/timeout-expire"), SERVICE_UNAVAILABLE);
    }

    @Test
    public void setTimeOutExpireHandled() {
        sendAndAssertNoResponse(get("/suspended/timeout-expire-handled"), GATEWAY_TIMEOUT);
    }

    @Test
    public void setTimeOutResumed() {
        // Jersey catches and logs the exception that is raised internally when attempting
        // to set a timeout on the resumed request ; and just proceeds with normal response handling
        sendAndAssertResponse(get("/suspended/resume-timeout"), OK, TEXT_PLAIN, "DONE");
    }

    @Test
    public void rsCancelSuspended() {
        expected.expectCause(instanceOf(TimeoutException.class));
        sendAndAssertResponse(get("/suspended/busy"), OK, TEXT_PLAIN, "DONE", 1, SECONDS);
    }

    @Test
    public void resumeSuspendedWithJson() {
        sendAndAssertResponse(get("/suspended/json"), OK, APPLICATION_JSON,
                jsonEquals("{\"foo\":\"bar3\"}"), String::length);
    }

    @Test
    public void sseStream() {
        sendAndAssertResponse(get("/sse/stream"), OK, newAsciiString(SERVER_SENT_EVENTS),
                is(range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())),
                $ -> null);
    }

    @Test
    public void sseBroadcast() {
        sendAndAssertResponse(get("/sse/broadcast"), OK, newAsciiString(SERVER_SENT_EVENTS),
                is("data: bar\n\n" + range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())),
                $ -> null);
    }

    @Test
    public void getCompletable() {
        HttpResponse<HttpPayloadChunk> res =
                sendAndAssertResponse(get("/completable"), NO_CONTENT, null, is(""), $ -> null);
        assertThat(res.getHeaders().get("X-Foo-Prop"), is(newAsciiString("barProp")));

        res = sendAndAssertResponse(get("/completable?fail=true"), INTERNAL_SERVER_ERROR, null, "");
        // There is no mapper for DeliberateException hence it is propagated to the container and response filters
        // are thus bypassed.
        assertThat(res.getHeaders().contains("X-Foo-Prop"), is(false));
    }

    @Test
    public void postJsonBufSingleInSingleOut() {
        sendAndAssertResponse(post("/json-buf-sglin-sglout", "{\"key\":\"val5\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val5\",\"foo\":\"bar6\"}"), $ -> null);

        sendAndAssertResponse(post("/json-buf-sglin-sglout?fail=true", "{\"key\":\"val5\"}", APPLICATION_JSON),
                INTERNAL_SERVER_ERROR, null, "");
    }

    @Test
    public void getResponseSingle() {
        sendAndAssertResponse(get("/single-response"), ACCEPTED, TEXT_PLAIN, "DONE");

        sendAndAssertNoResponse(get("/single-response?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void getMapSingle() {
        sendAndAssertResponse(get("/single-map"), OK, APPLICATION_JSON,
                jsonEquals("{\"foo\":\"bar4\"}"), String::length);

        sendAndAssertNoResponse(get("/single-map?fail=true"), INTERNAL_SERVER_ERROR);
    }
}
