/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.AsyncContextAssertionFilter;
import io.servicetalk.http.router.jersey.resources.AsyncContextResources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import javax.ws.rs.core.Application;

import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest.RouterApi.ASYNC_STREAMING;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AsyncContextTest extends AbstractJerseyStreamingHttpServiceTest {

    private final BlockingQueue<Throwable> errors = new LinkedBlockingDeque<>();
    private boolean lazyPayload;
    private boolean hasK2;
    private boolean hasK3;

    void setUp(boolean lazyPayload, boolean hasK2, boolean hasK3) {
        this.lazyPayload = lazyPayload;
        this.hasK2 = hasK2;
        this.hasK3 = hasK3;
        assertDoesNotThrow(() -> super.setUp(ASYNC_STREAMING));
    }

    @AfterEach
    void assertNoErrors() {
        assertNoAsyncErrors(errors);
    }

    @Override
    protected Application application() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return Collections.singleton(AsyncContextResources.class);
            }
        };
    }

    @Override
    void configureBuilders(HttpServerBuilder serverBuilder, HttpJerseyRouterBuilder jerseyRouterBuilder) {
        super.configureBuilders(serverBuilder, jerseyRouterBuilder);
        serverBuilder.appendServiceFilter(new AsyncContextAssertionFilter(errors, lazyPayload, hasK2, hasK3));
    }

    @Override
    protected String testUri(final String path) {
        return AsyncContextResources.PATH + path;
    }

    @Test
    void noArgsNoReturn() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, false, false);
        sendAndAssertResponse(get("/noArgsNoReturn"), NO_CONTENT, null, is(emptyString()), __ -> null);
    }

    @Test
    void getBuffer() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, false, false);
        sendAndAssertResponse(get("/getBuffer"), OK, TEXT_PLAIN, is(equalTo("foo")), 3);
    }

    @Test
    void postBuffer() {
        setUp(false, false, false);
        sendAndAssertResponse(post("/postBuffer", "foo", TEXT_PLAIN), NO_CONTENT, null, is(emptyString()), __ -> null);
    }

    @Test
    void syncEcho() {
        setUp(false, false, false);
        String payload = "foo";
        sendAndAssertResponse(post("/syncEcho", payload, TEXT_PLAIN),
                OK, TEXT_PLAIN, is(equalTo(payload)), payload.length());
    }

    @Test
    void syncEchoResponse() {
        setUp(false, false, false);
        String payload = "foo";
        sendAndAssertResponse(post("/syncEchoResponse", payload, TEXT_PLAIN),
                OK, TEXT_PLAIN, is(equalTo(payload)), payload.length());
    }

    @Test
    void postTextOioStreams() {
        setUp(true, true, false);
        String payload = "foo";
        sendAndAssertResponse(post("/postTextOioStreams", payload, TEXT_PLAIN),
                OK, TEXT_PLAIN, is(equalTo(payload)), payload.length());
    }

    @Test
    void syncEchoJsonMap() {
        setUp(false, false, false);
        sendAndAssertResponse(post("/syncEchoJsonMap", "{\"key\":\"val1\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val1\",\"foo\":\"bar1\"}"),
                getJsonResponseContentLengthExtractor());
    }

    @Test
    void completable() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, true, false);
        sendAndAssertResponse(get("/completable"), NO_CONTENT, null, is(emptyString()), __ -> null);
    }

    @Test
    void getSingleBuffer() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, true, false);
        sendAndAssertResponse(get("/getSingleBuffer"), OK, TEXT_PLAIN, is(equalTo("foo")), 3);
    }

    @Test
    void postSingleBuffer() {
        setUp(true, true, false);
        sendAndAssertResponse(post("/postSingleBuffer", "foo", TEXT_PLAIN),
                NO_CONTENT, null, is(emptyString()), __ -> null);
    }

    @Test
    void postSingleBufferSync() {
        setUp(true, true, false);
        sendAndAssertResponse(post("/postSingleBufferSync", "foo", TEXT_PLAIN),
                NO_CONTENT, null, is(emptyString()), __ -> null);
    }

    @Test
    void singleEcho() {
        setUp(true, true, false);
        String payload = "foo";
        sendAndAssertResponse(post("/singleEcho", payload, TEXT_PLAIN),
                OK, TEXT_PLAIN, is(equalTo(payload)), payload.length());
    }

    @Test
    void getPublisherBuffer() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, false, true);
        sendAndAssertResponse(get("/getPublisherBuffer"),
                OK, TEXT_PLAIN, is(equalTo("foo")), __ -> null);
    }

    @Test
    void postPublisherBuffer() {
        setUp(true, true, false);
        sendAndAssertResponse(post("/postPublisherBuffer", "foo", TEXT_PLAIN),
                NO_CONTENT, null, is(emptyString()), __ -> null);
    }

    @Test
    void publisherEcho() {
        setUp(true, false, true);
        String payload = "foo";
        sendAndAssertResponse(post("/publisherEcho", payload, TEXT_PLAIN),
                OK, TEXT_PLAIN, is(equalTo(payload)), __ -> null);
    }

    @Test
    void publisherEchoSync() {
        setUp(true, false, true);
        String payload = "foo";
        sendAndAssertResponse(post("/publisherEchoSync", payload, TEXT_PLAIN),
                OK, TEXT_PLAIN, is(equalTo(payload)), __ -> null);
    }

    @Test
    void getCompletionStage() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, true, false);
        sendAndAssertResponse(get("/getCompletionStage"), OK, TEXT_PLAIN, is(equalTo("foo")), 3);
    }

    @Test
    void getCompletionStageCompleteWithStExecutor() {
        // FIXME: lazyPayload=false, see https://github.com/apple/servicetalk/issues/3384
        setUp(true, true, false);
        sendAndAssertResponse(get("/getCompletionStageCompleteWithStExecutor"), OK, TEXT_PLAIN, is(equalTo("foo")), 3);
    }
}
