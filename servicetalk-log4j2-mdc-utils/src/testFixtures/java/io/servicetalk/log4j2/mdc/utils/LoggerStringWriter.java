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
package io.servicetalk.log4j2.mdc.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

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
    private static final ThreadLocal<ConcurrentStringWriter> THREAD_LOCAL_APPENDER = new ThreadLocal<>();

    private LoggerStringWriter() {
        // no instances.
    }

    /**
     * Clear the content of the {@link #accumulated()}.
     */
    public static void reset() {
        getStringWriter().reset();
    }

    /**
     * Remove the underlying in-memory log appender.
     */
    public static void remove() {
        removeStringWriter();
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

    private static ConcurrentStringWriter getStringWriter() {
        ConcurrentStringWriter writer = THREAD_LOCAL_APPENDER.get();
        if (writer == null) {
            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
            writer = addWriterAppender(context, DEBUG);
            THREAD_LOCAL_APPENDER.set(writer);
        }
        return writer;
    }

    private static void removeStringWriter() {
        ConcurrentStringWriter writer = THREAD_LOCAL_APPENDER.get();
        if (writer == null) {
            return;
        }
        removeWriterAppender(writer, (LoggerContext) LogManager.getContext(false));
        THREAD_LOCAL_APPENDER.remove();
    }

    private static ConcurrentStringWriter addWriterAppender(final LoggerContext context, Level level) {
        final Configuration config = context.getConfiguration();
        final ConcurrentStringWriter writer = new ConcurrentStringWriter();
        final Map.Entry<String, Appender> existing = config.getAppenders().entrySet().iterator().next();
        final WriterAppender writerAppender = WriterAppender.newBuilder()
                .setName(writer.name)
                .setLayout(existing.getValue().getLayout())
                .setTarget(writer)
                .build();

        writerAppender.start();
        config.getRootLogger().addAppender(writerAppender, level, null);

        return writer;
    }

    private static void removeWriterAppender(ConcurrentStringWriter writer, final LoggerContext context) {
        final Configuration config = context.getConfiguration();
        LoggerConfig rootConfig = config.getRootLogger();
        // Stopping the logger is subject to race conditions where logging during cleanup on global executor
        // may still try to log and raise an error.
        WriterAppender writerAppender = (WriterAppender) rootConfig.getAppenders().get(writer.name);
        if (writerAppender != null) {
            writerAppender.stop(0, NANOSECONDS);
        }
        // Don't remove directly from map, because the root logger also cleans up filters.
        rootConfig.removeAppender(writer.name);
    }

    // This is essentially just a thread safe `StringAppender` with a unique `String name` field to use
    // as a map key.
    private static final class ConcurrentStringWriter extends Writer {

        private static final String APPENDER_NAME_PREFIX = "writer";

        private final StringWriter stringWriter = new StringWriter();

        // We use uuid as a way to give the appender a unique name. We could try and do it with the current
        // thread name but it's hard to say if that will be unique but it is certain to be ugly.
        final String name = APPENDER_NAME_PREFIX + '_' + UUID.randomUUID();
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            synchronized (stringWriter) {
                stringWriter.write(cbuf, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (stringWriter) {
                stringWriter.flush();
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (stringWriter) {
                stringWriter.close();
            }
        }

        @Override
        public String toString() {
            synchronized (stringWriter) {
                return stringWriter.toString();
            }
        }

        void reset() {
            synchronized (stringWriter) {
                stringWriter.getBuffer().setLength(0);
            }
        }
    }
}
