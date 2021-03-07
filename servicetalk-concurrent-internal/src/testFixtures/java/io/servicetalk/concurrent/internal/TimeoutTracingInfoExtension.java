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

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeoutTracingInfoExtension implements AfterEachCallback {

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    @Override
    public void afterEach(ExtensionContext context) {
        Optional<Throwable> executionException = context.getExecutionException();
        if (executionException.isPresent() && executionException.get() instanceof TimeoutException) {
            dumpAllStacks();
        }
    }

    private static void dumpAllStacks() {
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
