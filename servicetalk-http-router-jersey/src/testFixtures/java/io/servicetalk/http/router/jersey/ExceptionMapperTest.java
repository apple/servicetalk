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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.HttpHeaderValues;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.router.jersey.ExceptionMapperTest.ExceptionResponseType.BUF;
import static io.servicetalk.http.router.jersey.ExceptionMapperTest.ExceptionResponseType.MAP;
import static io.servicetalk.http.router.jersey.ExceptionMapperTest.ExceptionResponseType.SBUF;
import static io.servicetalk.http.router.jersey.ExceptionMapperTest.ExceptionResponseType.SMAP;
import static io.servicetalk.http.router.jersey.ExceptionMapperTest.ExceptionResponseType.STR;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.status;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonPartEquals;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExceptionMapperTest extends AbstractJerseyStreamingHttpServiceTest {
    private static final String EXCEPTION_RESPONSE_TYPE_HEADER = "X-EXCEPTION-RESPONSE-TYPE";
    private static final HttpResponseStatus STATUS_555 = HttpResponseStatus.of(555, "");

    enum ExceptionResponseType {
        STR {
            @Override
            Response toResponse(final Throwable exception) {
                return status(555)
                        .header(CONTENT_TYPE, TEXT_PLAIN)
                        .entity(exception.getClass().getName())
                        .build();
            }
        },
        BUF {
            @Override
            Response toResponse(final Throwable exception) {
                final Buffer buf = DEFAULT_ALLOCATOR.fromAscii(exception.getClass().getName());
                return status(555)
                        .header(CONTENT_TYPE, TEXT_PLAIN)
                        .header(CONTENT_LENGTH, buf.readableBytes())
                        .entity(buf)
                        .build();
            }
        },
        SBUF {
            @Override
            Response toResponse(final Throwable exception) {
                final Buffer buf = DEFAULT_ALLOCATOR.fromAscii(exception.getClass().getName());
                return status(555)
                        .header(CONTENT_TYPE, TEXT_PLAIN)
                        .header(CONTENT_LENGTH, buf.readableBytes())
                        .entity(new GenericEntity<Single<Buffer>>(succeeded(buf)) { })
                        .build();
            }
        },
        MAP {
            @Override
            Response toResponse(final Throwable exception) {
                return status(555)
                        .header(CONTENT_TYPE, APPLICATION_JSON)
                        .entity(singletonMap("exceptionClassName", exception.getClass().getName()))
                        .build();
            }
        },
        SMAP {
            @Override
            Response toResponse(final Throwable exception) {
                final Map<String, String> map = singletonMap("exceptionClassName", exception.getClass().getName());
                return status(555)
                        .header(CONTENT_TYPE, APPLICATION_JSON)
                        .entity(new GenericEntity<Single<Map<String, String>>>(succeeded(map)) { })
                        .build();
            }
        };

        abstract Response toResponse(Throwable exception);
    }

    public static class TestExceptionMapper implements ExceptionMapper<Throwable> {
        @Context
        private HttpHeaders headers;

        @Override
        public Response toResponse(final Throwable t) {
            return ExceptionResponseType.valueOf(headers.getHeaderString(EXCEPTION_RESPONSE_TYPE_HEADER)).toResponse(t);
        }
    }

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestExceptionMapper.class,
                    SynchronousResources.class,
                    AsynchronousResources.class
            ));
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void stringResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPlainResponse(STR));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void bufferResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPlainResponse(BUF));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void singleBufferResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testPlainResponse(SBUF));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void mapResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> testJsonResponse(MAP));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void singleMapResponse(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {
            assumeTrue(isStreamingJsonEnabled());
            testJsonResponse(SMAP);
        });
    }

    private void testPlainResponse(final ExceptionResponseType ert) {
        testAllExceptionTypes(ert, HttpHeaderValues.TEXT_PLAIN);
    }

    private void testJsonResponse(final ExceptionResponseType ert) {
        testAllExceptionTypes(ert, HttpHeaderValues.APPLICATION_JSON);
    }

    private void testAllExceptionTypes(final ExceptionResponseType ert,
                                       final CharSequence expectedContentType) {
        // Routing exception
        sendAndAssertResponse(get(SynchronousResources.PATH + "/not_a_resource"), ert,
                expectedContentType, NotFoundException.class);

        // Thrown exception
        sendAndAssertResponse(get(SynchronousResources.PATH + "/text?qp=throw-not-translated"), ert,
                expectedContentType, DeliberateException.class);
        sendAndAssertResponse(get(SynchronousResources.PATH + "/text?qp=throw-translated"), ert,
                expectedContentType, WebApplicationException.class);
        sendAndAssertResponse(get(AsynchronousResources.PATH + "/text?qp=throw-not-translated"), ert,
                expectedContentType, DeliberateException.class);
        sendAndAssertResponse(get(AsynchronousResources.PATH + "/text?qp=throw-translated"), ert,
                expectedContentType, WebApplicationException.class);

        // Failed CompletionStage
        sendAndAssertResponse(get(AsynchronousResources.PATH + "/failed-text"), ert,
                expectedContentType, DeliberateException.class);

        // Failed RS source
        sendAndAssertResponse(get(AsynchronousResources.PATH + "/completable?fail=true"), ert,
                expectedContentType, DeliberateException.class);
        sendAndAssertResponse(get(AsynchronousResources.PATH + "/single-response?fail=true"), ert,
                expectedContentType, DeliberateException.class);
        sendAndAssertResponse(get(AsynchronousResources.PATH + "/single-map?fail=true"), ert,
                expectedContentType, DeliberateException.class);
    }

    private void sendAndAssertResponse(final StreamingHttpRequest req,
                                       final ExceptionResponseType ert,
                                       final CharSequence expectedContentType,
                                       final Class<? extends Throwable> expectedExceptionClass) {
        req.headers().set(EXCEPTION_RESPONSE_TYPE_HEADER, ert.toString());

        if (HttpHeaderValues.APPLICATION_JSON.equals(expectedContentType)) {
            sendAndAssertResponse(req, STATUS_555, expectedContentType,
                    is(jsonPartEquals("exceptionClassName", expectedExceptionClass.getName())),
                    getJsonResponseContentLengthExtractor());
        } else {
            sendAndAssertResponse(req, STATUS_555, expectedContentType,
                    is(expectedExceptionClass.getName()), String::length);
        }
    }
}
