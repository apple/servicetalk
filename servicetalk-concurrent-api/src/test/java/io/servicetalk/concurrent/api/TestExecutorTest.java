/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.concurrent.api.VerificationTestUtils.expectThrowable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestExecutorTest {

    @Test
    void testAdvanceTimeByNoExecuteTasks() {
        TestExecutor fixture = new TestExecutor();

        long initialTime = fixture.currentNanos();
        assertThat(fixture.currentNanos(), equalTo(initialTime));
        fixture.advanceTimeByNoExecuteTasks(1000, MILLISECONDS);
        assertThat(fixture.currentNanos(), equalTo(initialTime + 1000000000L));
        fixture.advanceTimeByNoExecuteTasks(1000, MILLISECONDS);
        assertThat(fixture.currentNanos(), equalTo(initialTime + 2000000000L));
    }

    @Test
    void testExecute() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.execute(i::incrementAndGet);

        assertThat(i.get(), equalTo(0));
        fixture.executeTasks();
        assertThat(i.get(), equalTo(1));
        fixture.executeTasks();
        assertThat(i.get(), equalTo(1));
    }

    @Test
    void testExecuteSameTaskMultipleTimes() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.execute(i::incrementAndGet);
        fixture.execute(i::incrementAndGet);

        assertThat(i.get(), equalTo(0));
        fixture.executeTasks();
        assertThat(i.get(), equalTo(2));
        fixture.executeTasks();
        assertThat(i.get(), equalTo(2));
    }

    @Test
    void testSchedule() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);

        assertThat(i.get(), equalTo(0));
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(999, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(1));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(1));
    }

    @Test
    void testScheduleAtSameTime() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);
        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);

        assertThat(i.get(), equalTo(0));
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(999, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(2));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(2));
    }

    @Test
    void testScheduleAtSameTimeFromDifferentNow() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);
        fixture.advanceTimeByNoExecuteTasks(300, NANOSECONDS);
        fixture.schedule(i::incrementAndGet, 700, NANOSECONDS);

        assertThat(i.get(), equalTo(0));
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(699, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(2));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(2));
    }

    @Test
    void testScheduleAtDifferentTimes() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);
        fixture.schedule(i::incrementAndGet, 1500, NANOSECONDS);

        assertThat(i.get(), equalTo(0));
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(999, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(0));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(1));

        fixture.advanceTimeByNoExecuteTasks(499, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(1));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(2));

        fixture.advanceTimeByNoExecuteTasks(1, NANOSECONDS);
        fixture.executeScheduledTasks();
        assertThat(i.get(), equalTo(2));
    }

    @Test
    void testExecuteNextTask() {
        TestExecutor fixture = new TestExecutor();

        AtomicInteger i = new AtomicInteger();

        fixture.execute(i::incrementAndGet);
        fixture.execute(i::incrementAndGet);

        assertThat(i.get(), equalTo(0));
        fixture.executeNextTask();
        assertThat(i.get(), equalTo(1));
        fixture.executeNextTask();
        assertThat(i.get(), equalTo(2));
        expectThrowable(fixture::executeNextTask, IllegalStateException.class);
        assertThat(i.get(), equalTo(2));
    }

    @Test
    void testExecuteNextScheduledTask() {
        testExecuteNextScheduledTask(new TestExecutor());
    }

    @Test
    void testScheduleDoesNotOverflow() {
        testExecuteNextScheduledTask(new TestExecutor(Long.MAX_VALUE - 1));
    }

    private void testExecuteNextScheduledTask(TestExecutor fixture) {
        AtomicInteger i = new AtomicInteger();

        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);
        fixture.schedule(i::incrementAndGet, 1000, NANOSECONDS);

        fixture.advanceTimeByNoExecuteTasks(2000, NANOSECONDS);

        assertThat(i.get(), equalTo(0));
        fixture.executeNextScheduledTask();
        assertThat(i.get(), equalTo(1));
        fixture.executeNextScheduledTask();
        assertThat(i.get(), equalTo(2));
        expectThrowable(fixture::executeNextScheduledTask, IllegalStateException.class);
        assertThat(i.get(), equalTo(2));
    }

    @Test
    void testCloseAsync() throws Exception {
        TestExecutor fixture = new TestExecutor();
        Future<Void> closeFuture = fixture.closeAsync().toFuture();
        Future<Void> onCloseFuture = fixture.onClose().toFuture();

        assertNull(closeFuture.get());
        assertTrue(closeFuture.isDone());
        assertTrue(onCloseFuture.isDone());
    }
}
