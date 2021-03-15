/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.concurrent.internal;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.UNICODE_CASE;

/**
 * Junit extension which will print information about all threads if unit test method throws
 * {@link TimeoutException}.
 */
public final class TimeoutTracingInfoExtension implements AfterEachCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Pattern PATTERN = Pattern.compile("([1-9]\\d*) ?((?:[nμm]?s)|m|h|d)?",
            CASE_INSENSITIVE | UNICODE_CASE);
    private static final Map<String, TimeUnit> UNITS_MAP;

    static {
        Map<String, TimeUnit> unitsMap = new HashMap<>();
        unitsMap.put("ns", NANOSECONDS);
        unitsMap.put("μs", MICROSECONDS);
        unitsMap.put("ms", MILLISECONDS);
        unitsMap.put("s", SECONDS);
        unitsMap.put("m", MINUTES);
        unitsMap.put("h", HOURS);
        unitsMap.put("d", DAYS);
        UNITS_MAP = Collections.unmodifiableMap(unitsMap);
    }

    public static final long DEFAULT_TIMEOUT_SECONDS = parseSystemProperty();
    private static final int DEFAULT_TIMEOUT_SECONDS_DEFAULT = 30;

    private static long parseSystemProperty() {
        String systemPropertyValue = System.getProperty("junit.jupiter.execution.timeout.default");

        if (systemPropertyValue == null) {
            return DEFAULT_TIMEOUT_SECONDS_DEFAULT;
        }
        Matcher matcher = PATTERN.matcher(systemPropertyValue);
        if (matcher.matches()) {
            long value = Long.parseLong(matcher.group(1));
            String unitAbbreviation = matcher.group(2);
            TimeUnit unit = unitAbbreviation == null ? SECONDS
                    : UNITS_MAP.get(unitAbbreviation.toLowerCase(Locale.ENGLISH));
            return SECONDS.convert(value, unit);
        }

        LOGGER.error("Error parsing `{}`, using default value", systemPropertyValue);
        return DEFAULT_TIMEOUT_SECONDS_DEFAULT;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Optional<Throwable> executionException = context.getExecutionException();
        if (executionException.isPresent() && executionException.get() instanceof TimeoutException) {
            dumpAllStacks();
        }
    }

    static void dumpAllStacks() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        List<ThreadInfo> threadInfos = Stream.of(bean.getThreadInfo(bean.getAllThreadIds(),
                bean.isObjectMonitorUsageSupported(),
                bean.isSynchronizerUsageSupported()))
                .filter(Objects::nonNull) // filter out dead threads
                .sorted(Comparator.comparing(ThreadInfo::getThreadName))
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder(threadInfos.size() * 4096);
        for (ThreadInfo info : threadInfos) {
            sb.append('"').append(info.getThreadName()).append('"');
            sb.append(" #").append(info.getThreadId());
            sb.append(" ").append(info.getThreadState().toString().toLowerCase());
            if (info.getLockName() != null) {
                sb.append(" on ").append(info.getLockName());
            }
            if (info.getLockOwnerName() != null) {
                sb.append(" owned by \"").append(info.getLockOwnerName()).append("\" #")
                        .append(info.getLockOwnerId());
            }
            if (info.isSuspended()) {
                sb.append(" (suspended)");
            }
            if (info.isInNative()) {
                sb.append(" (in native)");
            }
            sb.append("\n");

            sb.append("  java.lang.Thread.State: ").append(info.getThreadState()).append("\n");
            StackTraceElement[] stackTrace = info.getStackTrace();
            for (int i = 0; i < stackTrace.length; ++i) {
                sb.append("\t  at ").append(stackTrace[i]).append("\n");
                for (MonitorInfo mi : info.getLockedMonitors()) {
                    if (mi.getLockedStackDepth() == i) {
                        sb.append("\t  - locked ").append(mi).append("\n");
                    }
                }
            }
            sb.append("\n");

            LockInfo[] locks = info.getLockedSynchronizers();
            if (locks.length > 0) {
                sb.append("\t  Number of locked synchronizers = ").append(locks.length).append("\n");
                for (LockInfo li : locks) {
                    sb.append("\t  - ").append(li).append("\n");
                }
                sb.append("\n");
            }
        }
        System.out.println(sb);
    }
}
