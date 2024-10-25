/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.time;

public class MockClock extends AbstractClock {
    public static final long DEFAULT_AUTOINCREMENT_MS = 10L;

    private long current;
    private final long autoIncrement;

    private MockClock(long startTimeMs, long autoIncrement) {
        this.current = startTimeMs;
        this.autoIncrement = autoIncrement;
    }

    public void increment(long diff) {
        current += diff;
    }

    /** Increments the time by 10ms and returns it. */
    @Override
    public long getCurrentTime() {
        current += autoIncrement;
        return current;
    }

    /**
     * Creates an instance of a mock clock that starts at 0 and is only incremented by {@link #increment(long)}.
     * @return the mock clock
     */
    public static MockClock create() {
        return new MockClock(0, 0);
    }

    /**
     * Creates an instance of a mock clock that starts at 0 and is only incremented by {@link #increment(long)}.
     * @param startTime start time in milliseconds since epoch
     * @return the mock clock
     */
    public static MockClock createAt(long startTime) {
        return new MockClock(startTime, 0);
    }

    /**
     * Creates an instance of a mock clock that starts at 0 and is incremented by {@link #DEFAULT_AUTOINCREMENT_MS} upon every {@link #getCurrentTime()} call.
     * @return the mock clock
     */
    public static MockClock createAutoIncrementing() {
        return new MockClock(0, DEFAULT_AUTOINCREMENT_MS);
    }

    /**
     * Creates an instance of a mock clock that starts at {@code startTime} and is incremented by {@link #DEFAULT_AUTOINCREMENT_MS} upon every {@link #getCurrentTime()} call.
     * @param startTime start time in milliseconds since epoch
     * @return the mock clock
     */
    public static MockClock createAutoIncrementingAt(long startTime) {
        return new MockClock(startTime, DEFAULT_AUTOINCREMENT_MS);
    }
}
