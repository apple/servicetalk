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

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatuses.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
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

public class AsynchronousResourceTest extends AbstractResourceTest {
    @Override
    String getResourcePath() {
        return PATH;
    }

    @Ignore("Server doesn't handle errors yet")
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
        sendAndAssertResponse(get("/suspended/json"), OK, APPLICATION_JSON, jsonEquals("{\"foo\":\"bar3\"}"), String::length);
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
}
