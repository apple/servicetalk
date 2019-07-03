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
import static io.servicetalk.http.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;

public class AsynchronousResourceTest extends AbstractAsynchronousResourceTest {
    @Ignore("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    public void getJson() {
        // NOOP
    }

    @Ignore("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    public void putJsonResponse() {
        // NOOP
    }

    @Ignore("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    public void postJson() {
        // NOOP
    }

    @Ignore("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    public void postJsonPojoInPojoOut() {
        // NOOP
    }

    @Test
    public void getVoidCompletion() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/void-completion"), NO_CONTENT, null, isEmptyString(), __ -> null);
            // Jersey is inconsistent: should be NO_CONTENT
            sendAndAssertNoResponse(get("/void-completion?defer=true"), OK);

            sendAndAssertNoResponse(get("/void-completion?fail=true"), INTERNAL_SERVER_ERROR);
            sendAndAssertNoResponse(get("/void-completion?fail=true&defer=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @Test
    public void failedText() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/failed-text"), INTERNAL_SERVER_ERROR);
        });
    }

    @Test
    public void cancelledDelayedText() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/failed-text?cancel=true"), SERVICE_UNAVAILABLE);
        });
    }

    @Test
    public void getDelayedText() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/delayed-text?delay=10&unit=MILLISECONDS"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void rsCancelDelayedText() {
        runTwiceToEnsureEndpointCache(() -> {
            expected.expectCause(instanceOf(TimeoutException.class));
            sendAndAssertResponse(get("/delayed-text?delay=1&unit=DAYS"), OK, TEXT_PLAIN, "DONE", 1, SECONDS);
        });
    }

    @Test
    public void completedStageResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/response-comsta"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void delayedStageResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/delayed-response-comsta?delay=10&unit=MILLISECONDS"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void rsCancelDelayedDelayedStageResponse() {
        runTwiceToEnsureEndpointCache(() -> {
            expected.expectCause(instanceOf(TimeoutException.class));
            sendAndAssertResponse(get("/delayed-response-comsta?delay=10&unit=DAYS"),
                    OK, TEXT_PLAIN, "DONE", 1, SECONDS);
        });
    }

    @Test
    public void resumeSuspended() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/suspended/resume"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void cancelSuspended() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/suspended/cancel"), SERVICE_UNAVAILABLE);
        });
    }

    @Test
    public void setTimeOutResumeSuspended() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/suspended/timeout-resume"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void setTimeOutExpire() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/suspended/timeout-expire"), SERVICE_UNAVAILABLE);
        });
    }

    @Test
    public void setTimeOutExpireHandled() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertNoResponse(get("/suspended/timeout-expire-handled"), GATEWAY_TIMEOUT);
        });
    }

    @Test
    public void setTimeOutResumed() {
        runTwiceToEnsureEndpointCache(() -> {
            // Jersey catches and logs the exception that is raised internally when attempting
            // to set a timeout on the resumed request ; and just proceeds with normal response handling
            sendAndAssertResponse(get("/suspended/resume-timeout"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @Test
    public void rsCancelSuspended() {
        runTwiceToEnsureEndpointCache(() -> {
            expected.expectCause(instanceOf(TimeoutException.class));
            sendAndAssertResponse(get("/suspended/busy"), OK, TEXT_PLAIN, "DONE", 1, SECONDS);
        });
    }

    @Test
    public void resumeSuspendedWithJson() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/suspended/json"), OK, APPLICATION_JSON,
                    jsonEquals("{\"foo\":\"bar3\"}"), getJsonResponseContentLengthExtractor());
        });
    }

    @Test
    public void sseStream() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/sse/stream"), OK, newAsciiString(SERVER_SENT_EVENTS),
                    is(range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())),
                    __ -> null);
        });
    }

    @Test
    public void sseBroadcast() {
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/sse/broadcast"), OK, newAsciiString(SERVER_SENT_EVENTS),
                    is("data: bar\n\n" + range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())),
                    __ -> null);
        });
    }

    @Test
    public void sseUnsupported() {
        runTwiceToEnsureEndpointCache(() -> {
            // Jersey's OutboundEventWriter ignores the lack of a MessageBodyWriter for the event (an error is logged
            // but no feedback is provided to the client side)
            sendAndAssertResponse(get("/sse/unsupported"), OK, newAsciiString(SERVER_SENT_EVENTS),
                    isEmptyString(), __ -> null);
        });
    }
}
