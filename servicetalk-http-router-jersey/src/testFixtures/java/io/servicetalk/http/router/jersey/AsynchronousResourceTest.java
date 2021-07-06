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

import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeoutException;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PARTIAL_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static javax.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AsynchronousResourceTest extends AbstractResourceTest {
    @Override
    String resourcePath() {
        return AsynchronousResources.PATH;
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getCompletable(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            StreamingHttpResponse res =
                    sendAndAssertResponse(get("/completable"), NO_CONTENT, null, isEmptyString(), __ -> null);
            assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));

            res = sendAndAssertResponse(get("/completable?fail=true"), INTERNAL_SERVER_ERROR, null, "");
            // There is no mapper for DeliberateException hence it is propagated to the container and response filters
            // are thus bypassed.
            assertThat(res.headers().contains("X-Foo-Prop"), is(false));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getStringSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/single-string"), OK, TEXT_PLAIN, "DONE");

            sendAndAssertNoResponse(get("/single-string?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void headStringSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(head("/single-string"), OK, TEXT_PLAIN, isEmptyString(), 4);

            sendAndAssertNoResponse(head("/single-string?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void headCompletable(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            StreamingHttpResponse res =
                    sendAndAssertResponse(head("/completable"), NO_CONTENT, null, isEmptyString(), __ -> null);
            assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));

            res = sendAndAssertResponse(head("/completable?fail=true"), INTERNAL_SERVER_ERROR, null, "");
            // There is no mapper for DeliberateException hence it is propagated to the container and response filters
            // are thus bypassed.
            assertThat(res.headers().contains("X-Foo-Prop"), is(false));
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJsonBufSingleInSingleOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json-buf-sglin-sglout", "{\"key\":\"val5\"}", APPLICATION_JSON),
                    OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val5\",\"foo\":\"bar6\"}"), String::length);

            sendAndAssertResponse(post("/json-buf-sglin-sglout?fail=true", "{\"key\":\"val5\"}", APPLICATION_JSON),
                    INTERNAL_SERVER_ERROR, null, "");
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getResponseSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/single-response"), ACCEPTED, TEXT_PLAIN, "DONE");

            sendAndAssertNoResponse(get("/single-response?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void headResponseSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(head("/single-response"), ACCEPTED, TEXT_PLAIN, isEmptyString(), 4);

            sendAndAssertNoResponse(head("/single-response?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getResponseSinglePublisherEntity(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        get("/single-response-pub-entity?i=206"), PARTIAL_CONTENT, TEXT_PLAIN, "GOT: 206"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void headResponseSinglePublisherEntity(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        head("/single-response-pub-entity?i=206"), PARTIAL_CONTENT, TEXT_PLAIN,
                        isEmptyString(), 8));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getMapSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/single-map"), OK, APPLICATION_JSON,
                    jsonEquals("{\"foo\":\"bar4\"}"), getJsonResponseContentLengthExtractor());

            sendAndAssertNoResponse(get("/single-map?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void headMapSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(head("/single-map"), OK, APPLICATION_JSON, isEmptyString(),
                    getJsonResponseContentLengthExtractor().andThen(i -> i != null ? 14 : null));

            sendAndAssertNoResponse(head("/single-map?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getPojoSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/single-pojo"), OK, APPLICATION_JSON,
                    jsonEquals("{\"aString\":\"boo\",\"anInt\":456}"), getJsonResponseContentLengthExtractor());

            sendAndAssertNoResponse(get("/single-pojo?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void headPojoSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(head("/single-pojo"), OK, APPLICATION_JSON, isEmptyString(),
                    getJsonResponseContentLengthExtractor().andThen(i -> i != null ? 29 : null));

            sendAndAssertNoResponse(head("/single-pojo?fail=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJsonPojoInPojoOutSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json-pojoin-pojoout-single", "{\"aString\":\"val6\",\"anInt\":123}",
                    APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonEquals("{\"aString\":\"val6x\",\"anInt\":124}"), getJsonResponseContentLengthExtractor());

            sendAndAssertNoResponse(post("/json-pojoin-pojoout-single?fail=true",
                    "{\"aString\":\"val8\",\"anInt\":123}", APPLICATION_JSON), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void postJsonPojoInPojoOutResponseSingle(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post("/json-pojoin-pojoout-response-single", "{\"aString\":\"val7\",\"anInt\":123}",
                    APPLICATION_JSON), ACCEPTED, APPLICATION_JSON, jsonEquals("{\"aString\":\"val7x\",\"anInt\":124}"),
                    getJsonResponseContentLengthExtractor());

            sendAndAssertNoResponse(post("/json-pojoin-pojoout-response-single?fail=true",
                    "{\"aString\":\"val7\",\"anInt\":123}", APPLICATION_JSON), INTERNAL_SERVER_ERROR);
        });
    }

    @Disabled("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    void getJson(final boolean serverNoOffloads, final RouterApi api) {
        // NOOP
    }

    @Disabled("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    void putJsonResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        // NOOP
    }

    @Disabled("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    void postJson(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        // NOOP
    }

    @Disabled("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    void postJsonPojoInPojoOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        // NOOP
    }

    @Override
    void explicitHead(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.explicitHead(serverNoOffloads, api);
    }

    @Override
    void getTextBuffer(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.getTextBuffer(serverNoOffloads, api);
    }

    @Override
    void postJsonBuffer(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.postJsonBuffer(serverNoOffloads, api);
    }

    @Override
    void postJsonBytes(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.postJsonBytes(serverNoOffloads, api);
    }

    @Override
    void postTextBuffer(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.postTextBuffer(serverNoOffloads, api);
    }

    @Override
    void postTextBufferResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.postTextBufferResponse(serverNoOffloads, api);
    }

    @Override
    void postTextBytes(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.postTextBytes(serverNoOffloads, api);
    }

    @Override
    void postTextResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        super.postTextResponse(serverNoOffloads, api);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getVoidCompletion(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(get("/void-completion"), NO_CONTENT, null, isEmptyString(), __ -> null);
            // Jersey is inconsistent: should be NO_CONTENT
            sendAndAssertNoResponse(get("/void-completion?defer=true"), OK);

            sendAndAssertNoResponse(get("/void-completion?fail=true"), INTERNAL_SERVER_ERROR);
            sendAndAssertNoResponse(get("/void-completion?fail=true&defer=true"), INTERNAL_SERVER_ERROR);
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void failedText(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertNoResponse(get("/failed-text"), INTERNAL_SERVER_ERROR));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void cancelledDelayedText(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertNoResponse(get("/failed-text?cancel=true"), SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void getDelayedText(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/delayed-text?delay=10&unit=MILLISECONDS"), OK, TEXT_PLAIN, "DONE"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void rsCancelDelayedText(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        runTwiceToEnsureEndpointCache(
                () -> {
                    RuntimeException ex = assertThrows(RuntimeException.class,
                            () -> sendAndAssertResponse(
                                    get("/delayed-text?delay=1&unit=DAYS"), OK, TEXT_PLAIN, "DONE", 1, SECONDS));
                    assertThat(ex.getCause(), instanceOf(TimeoutException.class));
                }
        );
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void completedStageResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertResponse(get("/response-comsta"), OK, TEXT_PLAIN, "DONE"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void delayedStageResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        get("/delayed-response-comsta?delay=10&unit=MILLISECONDS"), OK, TEXT_PLAIN, "DONE"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void rsCancelDelayedDelayedStageResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.COMPLETION_STAGE);
        runTwiceToEnsureEndpointCache(
                () -> {
                    RuntimeException ex = assertThrows(RuntimeException.class,
                            () -> sendAndAssertResponse(
                                    get("/delayed-response-comsta?delay=10&unit=DAYS"),
                                    OK, TEXT_PLAIN, "DONE", 1, SECONDS));
                    assertThat(ex.getCause(), instanceOf(TimeoutException.class));
                });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void resumeSuspended(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertResponse(get("/suspended/resume"), OK, TEXT_PLAIN, "DONE"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void cancelSuspended(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(() -> sendAndAssertNoResponse(get("/suspended/cancel"), SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void setTimeOutResumeSuspended(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/suspended/timeout-resume"), OK, TEXT_PLAIN, "DONE"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void setTimeOutExpire(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertNoResponse(get("/suspended/timeout-expire"), SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void setTimeOutExpireHandled(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertNoResponse(get("/suspended/timeout-expire-handled"), GATEWAY_TIMEOUT));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void setTimeOutResumed(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(() -> {
            // Jersey catches and logs the exception that is raised internally when attempting
            // to set a timeout on the resumed request ; and just proceeds with normal response handling
            sendAndAssertResponse(get("/suspended/resume-timeout"), OK, TEXT_PLAIN, "DONE");
        });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void rsCancelSuspended(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(
                () -> {
                    RuntimeException ex = assertThrows(RuntimeException.class,
                            () -> sendAndAssertResponse(
                                    get("/suspended/busy"), OK, TEXT_PLAIN, "DONE", 1, SECONDS));
                    assertThat(ex.getCause(), instanceOf(TimeoutException.class));
                });
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void resumeSuspendedWithJson(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.ASYNC_RESPONSE);
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(
                        get("/suspended/json"), OK, APPLICATION_JSON,
                        jsonEquals("{\"foo\":\"bar3\"}"), getJsonResponseContentLengthExtractor()));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void sseStream(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.SSE);
        assumeStreaming();
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/sse/stream"), OK, newAsciiString(SERVER_SENT_EVENTS),
                        is(range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())),
                        __ -> null));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void sseBroadcast(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.SSE);
        assumeStreaming();
        runTwiceToEnsureEndpointCache(
                () -> sendAndAssertResponse(get("/sse/broadcast"), OK, newAsciiString(SERVER_SENT_EVENTS),
                        is("data: bar\n\n" + range(0, 10).mapToObj(i -> "data: foo" + i + "\n\n").collect(joining())),
                        __ -> null));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("data")
    void sseUnsupported(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        assumeOffloads(AssumeOffloadsReason.SSE);
        assumeStreaming();
        runTwiceToEnsureEndpointCache(() -> {
            // Jersey's OutboundEventWriter ignores the lack of a MessageBodyWriter for the event (an error is logged
            // but no feedback is provided to the client side)
            sendAndAssertResponse(get("/sse/unsupported"), OK, newAsciiString(SERVER_SENT_EVENTS),
                    isEmptyString(), __ -> null);
        });
    }

    private enum AssumeOffloadsReason {
        COMPLETION_STAGE("CompletionStage responses rely on suspended async response which"),
        ASYNC_RESPONSE("Suspended async response"),
        SSE("SSE relies on suspended async response which");

        private final String message;

        AssumeOffloadsReason(final String message) {
            this.message = message;
        }
    }

    private void assumeOffloads(final AssumeOffloadsReason reason) {
        assumeFalse(serverNoOffloads(), () -> reason.message + " can't be used with noOffloads");
    }

    private void assumeStreaming() {
        Assumptions.assumeTrue(api == RouterApi.ASYNC_STREAMING
                               || api == RouterApi.BLOCKING_STREAMING, "not supported for aggregated APIs");
    }
}
