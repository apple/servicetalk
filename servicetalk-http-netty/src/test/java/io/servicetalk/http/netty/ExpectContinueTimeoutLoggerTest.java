/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;

import io.netty.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpectContinueTimeoutLoggerTest {

    private static final long INTERVAL_NANOS = HOURS.toNanos(2);
    // Unique among test classes sharing the JVM-wide log appender, to filter out their lines.
    private static final String REMOTE_HOST = "expect-continue-timeout-logger-test-host";

    private final LoggerStringWriter loggerStringWriter = new LoggerStringWriter();
    private final AtomicLong clock = new AtomicLong();
    private final Channel channel = mock(Channel.class);
    private final ExpectContinueTimeoutLogger timeout = new ExpectContinueTimeoutLogger(SECONDS.toNanos(3),
            clock::get);

    ExpectContinueTimeoutLoggerTest() {
        when(channel.remoteAddress()).thenReturn(InetSocketAddress.createUnresolved(REMOTE_HOST, 80));
        loggerStringWriter.reset();
    }

    @AfterEach
    void removeLogAppender() {
        loggerStringWriter.remove();
    }

    @Test
    void timeoutNanosReturnsConfiguredValue() {
        assertThat(timeout.timeoutNanos(), is(SECONDS.toNanos(3)));
    }

    @Test
    void onTimeoutFirstCallLogsImmediatelyAtInfo() {
        timeout.onTimeout(channel);

        final List<String> lines = loggedLines();
        assertThat(lines, hasSize(1));
        assertThat(lines.get(0), containsString("[INFO ]"));
        assertThat(lines.get(0), containsString("No 100 (Continue) arrived from " + REMOTE_HOST));
        assertThat(lines.get(0), containsString("within the configured 3000ms"));
        assertThat(lines.get(0), containsString("the payload body was sent anyway"));
        assertThat(lines.get(0), containsString("1 requests since the last message"));
    }

    @Test
    void onTimeoutWithinIntervalDoesNotLog() {
        timeout.onTimeout(channel);
        clock.addAndGet(INTERVAL_NANOS - 1);
        timeout.onTimeout(channel);
        timeout.onTimeout(channel);

        assertThat(loggedLines(), hasSize(1));
    }

    @Test
    void onTimeoutAfterIntervalLogsSuppressedCountAndResets() {
        timeout.onTimeout(channel);
        clock.addAndGet(INTERVAL_NANOS - 1);
        timeout.onTimeout(channel);
        timeout.onTimeout(channel);
        clock.addAndGet(1);
        timeout.onTimeout(channel);

        List<String> lines = loggedLines();
        assertThat(lines, hasSize(2));
        assertThat(lines.get(1), containsString("[INFO ]"));
        assertThat(lines.get(1), containsString("3 requests since the last message"));

        clock.addAndGet(INTERVAL_NANOS);
        timeout.onTimeout(channel);

        lines = loggedLines();
        assertThat(lines, hasSize(3));
        assertThat(lines.get(2), containsString("1 requests since the last message"));
    }

    @Test
    void onTimeoutThrottleIsPerInstance() {
        final ExpectContinueTimeoutLogger other = new ExpectContinueTimeoutLogger(SECONDS.toNanos(3), clock::get);
        timeout.onTimeout(channel);
        other.onTimeout(channel);

        assertThat(loggedLines(), hasSize(2));
    }

    private List<String> loggedLines() {
        final List<String> lines = new ArrayList<>();
        for (final String line : loggerStringWriter.accumulated().split("\\R")) {
            if (line.contains(REMOTE_HOST)) {
                lines.add(line);
            }
        }
        return lines;
    }
}
