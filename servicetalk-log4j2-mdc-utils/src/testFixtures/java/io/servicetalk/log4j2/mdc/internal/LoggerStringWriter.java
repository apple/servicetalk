/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.log4j2.mdc.internal;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public final class LoggerStringWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerStringWriter.class);
    @Nullable
    private static StringWriter logStringWriter;

    private LoggerStringWriter() {
        // no instances.
    }

    /**
     * Clear the content of the {@link #accumulated()}.
     */
    public static void reset() {
        getStringWriter().getBuffer().setLength(0);
    }

    /**
     * Get the accumulated content that has been logged.
     *
     * @return the accumulated content that has been logged.
     */
    public static String accumulated() {
        return getStringWriter().toString();
    }

    /**
     * Wait for the {@link #accumulated()} content to remain unchanged for {@code delayMillis} milliseconds.
     *
     * @param totalWaitTimeMillis The amount of milliseconds to wait for the {@link #accumulated()} content to
     * stabilize.
     * @return The accumulated content that has been logged.
     * @throws InterruptedException If interrupted while waiting for log content to stabilize.
     * @throws TimeoutException If the {@code totalWaitTimeMillis} duration has been exceeded and the
     * {@link #accumulated()} has not yet stabilize.
     */
    public static String stableAccumulated(int totalWaitTimeMillis) throws InterruptedException, TimeoutException {
        return stableAccumulated(totalWaitTimeMillis, 10);
    }

    /**
     * Wait for the {@link #accumulated()} content to remain unchanged for {@code sleepDurationMs} milliseconds.
     *
     * @param totalWaitTimeMillis The total amount of milliseconds to wait.
     * @param sleepDurationMs The amount of milliseconds to wait between checking if {@link #accumulated()} has
     * stabilize.
     * @return The accumulated content that has been logged.
     * @throws InterruptedException If interrupted while waiting for log content to stabilize.
     * @throws TimeoutException If the {@code totalWaitTimeMillis} duration has been exceeded and the
     * {@link #accumulated()} has not yet stabilize.
     */
    public static String stableAccumulated(int totalWaitTimeMillis, final long sleepDurationMs)
            throws InterruptedException, TimeoutException {
        // We force a unique log entry, and wait for it to ensure the content from the local thread has been flushed.
        String forcedLogEntry = "forced log entry to help for flush on current thread " +
                ThreadLocalRandom.current().nextLong();
        LOGGER.error(forcedLogEntry);

        String logContent = accumulated();
        String newLogContent = logContent;

        long nanoTimeA = nanoTime();
        long nanoTimeB;

        // Wait for this thread's content to be flushed to the log.
        while (!logContent.contains(forcedLogEntry)) {
            if (totalWaitTimeMillis <= 0) {
                throw new TimeoutException("timed out waiting for thread: " + currentThread());
            }
            sleep(sleepDurationMs);
            nanoTimeB = nanoTime();
            totalWaitTimeMillis -= NANOSECONDS.toMillis(nanoTimeB - nanoTimeA);
            nanoTimeA = nanoTimeB;

            newLogContent = accumulated();
            logContent = newLogContent;
        }

        // Give some time for other threads to be flushed to logs.
        do {
            logContent = newLogContent;

            if (totalWaitTimeMillis <= 0) {
                throw new TimeoutException("timed out waiting for thread: " + currentThread());
            }
            sleep(sleepDurationMs);
            nanoTimeB = nanoTime();
            totalWaitTimeMillis -= NANOSECONDS.toMillis(nanoTimeB - nanoTimeA);
            nanoTimeA = nanoTimeB;

            newLogContent = accumulated();
        } while (!newLogContent.equals(logContent));

        return logContent;
    }

    /**
     * Verify that an MDC {@code expectedLabel=expectedValue} pair is present in {@code value}.
     *
     * @param value The log line.
     * @param expectedLabel The MDC key.
     * @param expectedValue The MDC value.
     */
    public static void assertContainsMdcPair(String value, String expectedLabel, String expectedValue) {
        int x = value.indexOf(expectedLabel);
        assertThat("couldn't find expectedLabel: " + expectedLabel, x, is(greaterThanOrEqualTo(0)));
        int beginIndex = x + expectedLabel.length();
        assertThat(value.substring(beginIndex, beginIndex + expectedValue.length()), is(expectedValue));
    }

    private static synchronized StringWriter getStringWriter() {
        if (logStringWriter == null) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            logStringWriter = addWriterAppender(context, DEBUG);
        }
        return logStringWriter;
    }

    private static StringWriter addWriterAppender(final LoggerContext context, Level level) {
        final Configuration config = context.getConfiguration();
        final StringWriter writer = new StringWriter();

        final Map.Entry<String, Appender> existing = config.getAppenders().entrySet().iterator().next();
        final WriterAppender writerAppender = WriterAppender.newBuilder()
                .setName("writer")
                .setLayout((StringLayout) existing.getValue().getLayout())
                .setTarget(writer)
                .build();
        writerAppender.start();

        config.addAppender(writerAppender);
        config.getRootLogger().addAppender(writerAppender, level, null);

        return writer;
    }
}
