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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatuses;

import org.junit.Test;

import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.CLIENT_ERROR_4XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.REDIRECTION_3XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESS_2XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.UNKNOWN;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.toStatusClass;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.MULTIPLE_CHOICES;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.fromNettyHttpResponseStatus;
import static io.servicetalk.http.netty.NettyHttpResponseStatusConverter.toNettyHttpResponseStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NettyHttpResponseStatusConverterTest {
    @Test
    public void testFromNettyStandardStatus() {
        final HttpResponseStatus responseStatus = fromNettyHttpResponseStatus(io.netty.handler.codec.http.HttpResponseStatus.OK);

        assertSame(OK, responseStatus);
    }

    @Test
    public void testFromNettyDifferentReason() {
        final io.netty.handler.codec.http.HttpResponseStatus nettyStatus = new io.netty.handler.codec.http.HttpResponseStatus(200, "OKAY");
        final HttpResponseStatus responseStatus = fromNettyHttpResponseStatus(nettyStatus);

        assertEquals("OKAY", responseStatus.getReasonPhrase());
        assertEquals(200, responseStatus.getCode());
        assertSame(SUCCESS_2XX, responseStatus.getStatusClass());
    }

    @Test
    public void testFromNettyUnknownCode() {
        final io.netty.handler.codec.http.HttpResponseStatus nettyStatus = new io.netty.handler.codec.http.HttpResponseStatus(418, "I'm a teapot");
        final HttpResponseStatus responseStatus = fromNettyHttpResponseStatus(nettyStatus);

        assertEquals("I'm a teapot", responseStatus.getReasonPhrase());
        assertEquals(418, responseStatus.getCode());
        assertSame(CLIENT_ERROR_4XX, responseStatus.getStatusClass());
    }

    @Test
    public void testToNettyStandardStatus() {
        final io.netty.handler.codec.http.HttpResponseStatus nettyStatus = toNettyHttpResponseStatus(HttpResponseStatuses.OK);

        assertSame(io.netty.handler.codec.http.HttpResponseStatus.OK, nettyStatus);
    }

    @Test
    public void testToNettyDifferentReason() {
        final HttpResponseStatus responseStatus = getResponseStatus(200, "OKAY");
        final io.netty.handler.codec.http.HttpResponseStatus nettyResponse = toNettyHttpResponseStatus(responseStatus);

        assertEquals("OKAY", nettyResponse.reasonPhrase());
        assertEquals(200, nettyResponse.code());
    }

    @Test
    public void testToNettyUnknownCode() {
        final HttpResponseStatus responseStatus = getResponseStatus(418, "I'm a teapot");
        final io.netty.handler.codec.http.HttpResponseStatus nettyResponse = toNettyHttpResponseStatus(responseStatus);

        assertEquals("I'm a teapot", nettyResponse.reasonPhrase());
        assertEquals(418, nettyResponse.code());
    }

    @Test
    public void testStatusClass1xx() {
        assertSame(INFORMATIONAL_1XX, toStatusClass(100));
        assertSame(INFORMATIONAL_1XX, toStatusClass(101));
        assertSame(INFORMATIONAL_1XX, toStatusClass(198));
        assertSame(INFORMATIONAL_1XX, toStatusClass(199));

        assertFalse(INFORMATIONAL_1XX.contains(99));
        assertTrue(INFORMATIONAL_1XX.contains(100));
        assertTrue(INFORMATIONAL_1XX.contains(CONTINUE));
        assertTrue(INFORMATIONAL_1XX.contains(101));
        assertTrue(INFORMATIONAL_1XX.contains(198));
        assertTrue(INFORMATIONAL_1XX.contains(199));
        assertFalse(INFORMATIONAL_1XX.contains(200));
    }

    @Test
    public void testStatusClass2xx() {
        assertSame(SUCCESS_2XX, toStatusClass(200));
        assertSame(SUCCESS_2XX, toStatusClass(201));
        assertSame(SUCCESS_2XX, toStatusClass(298));
        assertSame(SUCCESS_2XX, toStatusClass(299));

        assertFalse(SUCCESS_2XX.contains(199));
        assertTrue(SUCCESS_2XX.contains(200));
        assertTrue(SUCCESS_2XX.contains(OK));
        assertTrue(SUCCESS_2XX.contains(201));
        assertTrue(SUCCESS_2XX.contains(298));
        assertTrue(SUCCESS_2XX.contains(299));
        assertFalse(SUCCESS_2XX.contains(300));
    }

    @Test
    public void testStatusClass3xx() {
        assertSame(REDIRECTION_3XX, toStatusClass(300));
        assertSame(REDIRECTION_3XX, toStatusClass(301));
        assertSame(REDIRECTION_3XX, toStatusClass(398));
        assertSame(REDIRECTION_3XX, toStatusClass(399));

        assertFalse(REDIRECTION_3XX.contains(299));
        assertTrue(REDIRECTION_3XX.contains(300));
        assertTrue(REDIRECTION_3XX.contains(MULTIPLE_CHOICES));
        assertTrue(REDIRECTION_3XX.contains(301));
        assertTrue(REDIRECTION_3XX.contains(398));
        assertTrue(REDIRECTION_3XX.contains(399));
        assertFalse(REDIRECTION_3XX.contains(400));
    }

    @Test
    public void testStatusClass4xx() {
        assertSame(CLIENT_ERROR_4XX, toStatusClass(400));
        assertSame(CLIENT_ERROR_4XX, toStatusClass(401));
        assertSame(CLIENT_ERROR_4XX, toStatusClass(498));
        assertSame(CLIENT_ERROR_4XX, toStatusClass(499));

        assertFalse(CLIENT_ERROR_4XX.contains(399));
        assertTrue(CLIENT_ERROR_4XX.contains(400));
        assertTrue(CLIENT_ERROR_4XX.contains(BAD_REQUEST));
        assertTrue(CLIENT_ERROR_4XX.contains(401));
        assertTrue(CLIENT_ERROR_4XX.contains(498));
        assertTrue(CLIENT_ERROR_4XX.contains(499));
        assertFalse(CLIENT_ERROR_4XX.contains(500));
    }

    @Test
    public void testStatusClass5xx() {
        assertSame(SERVER_ERROR_5XX, toStatusClass(500));
        assertSame(SERVER_ERROR_5XX, toStatusClass(501));
        assertSame(SERVER_ERROR_5XX, toStatusClass(598));
        assertSame(SERVER_ERROR_5XX, toStatusClass(599));

        assertFalse(SERVER_ERROR_5XX.contains(499));
        assertTrue(SERVER_ERROR_5XX.contains(500));
        assertTrue(SERVER_ERROR_5XX.contains(INTERNAL_SERVER_ERROR));
        assertTrue(SERVER_ERROR_5XX.contains(501));
        assertTrue(SERVER_ERROR_5XX.contains(598));
        assertTrue(SERVER_ERROR_5XX.contains(599));
        assertFalse(SERVER_ERROR_5XX.contains(600));
    }

    @Test
    public void testStatusClassUnknown() {
        assertSame(UNKNOWN, toStatusClass(98));
        assertSame(UNKNOWN, toStatusClass(99));
        assertSame(UNKNOWN, toStatusClass(600));
        assertSame(UNKNOWN, toStatusClass(601));

        assertTrue(UNKNOWN.contains(98));
        assertTrue(UNKNOWN.contains(99));
        assertFalse(UNKNOWN.contains(100));
        assertFalse(UNKNOWN.contains(599));
        assertTrue(UNKNOWN.contains(600));
        assertTrue(UNKNOWN.contains(601));
    }
}
