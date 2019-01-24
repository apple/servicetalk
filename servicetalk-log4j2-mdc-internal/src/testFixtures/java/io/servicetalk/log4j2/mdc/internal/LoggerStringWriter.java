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

import java.io.StringWriter;
import java.util.Map;
import javax.annotation.Nullable;

import static org.apache.logging.log4j.Level.DEBUG;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public final class LoggerStringWriter {
    @Nullable
    private static StringWriter logStringWriter;

    private LoggerStringWriter() {
        // no instances.
    }

    public static void reset() {
        getStringWriter().getBuffer().setLength(0);
    }

    public static String getAccumulated() {
        return getStringWriter().toString();
    }

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
