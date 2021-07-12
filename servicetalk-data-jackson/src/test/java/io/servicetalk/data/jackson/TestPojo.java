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
package io.servicetalk.data.jackson;

import io.servicetalk.concurrent.BlockingIterator;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

import static java.lang.Math.abs;
import static java.util.Objects.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@link Object} for testing JSON serialization.
 */
public final class TestPojo {
    private boolean myBoolean;
    private byte myByte;
    private short myShort;
    private char myChar;
    private int myInt;
    private long myLong;
    private float myFloat;
    private double myDouble;
    @Nullable
    private String myString;
    @Nullable
    private String[] myStringArray;
    @Nullable
    private TestPojo myPojo;

    /**
     * Create a new instance.
     */
    public TestPojo() {
        // required for Jackson
    }

    /**
     * Create a new instance.
     * @param myBoolean a boolean.
     * @param myByte a byte.
     * @param myShort a short.
     * @param myChar a char.
     * @param myInt an int.
     * @param myLong a long.
     * @param myFloat a float.
     * @param myDouble a double.
     * @param myString a {@link String}.
     * @param myStringArray an array of {@link String}s.
     * @param myPojo a {@link TestPojo}.
     */
    public TestPojo(boolean myBoolean, byte myByte, short myShort, char myChar, int myInt, long myLong, float myFloat,
             double myDouble, @Nullable String myString, @Nullable String[] myStringArray,
             @Nullable TestPojo myPojo) {
        this.myBoolean = myBoolean;
        this.myByte = myByte;
        this.myShort = myShort;
        this.myChar = myChar;
        this.myInt = myInt;
        this.myLong = myLong;
        this.myFloat = myFloat;
        this.myDouble = myDouble;
        this.myString = myString;
        this.myStringArray = myStringArray;
        this.myPojo = myPojo;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TestPojo)) {
            return false;
        }
        TestPojo that = (TestPojo) o;
        return myBoolean == that.myBoolean && myByte == that.myByte && myShort == that.myShort &&
                myChar == that.myChar && myInt == that.myInt && myLong == that.myLong &&
                abs(myFloat - that.myFloat) < 0.01 && abs(myDouble - that.myDouble) < 0.01 &&
                Objects.equals(myString, that.myString) && Arrays.equals(myStringArray, that.myStringArray) &&
                Objects.equals(myPojo, that.myPojo);
    }

    @Override
    public int hashCode() {
        return hash(myBoolean, myByte, myShort, myChar, myInt, myLong, myFloat, myDouble, myString,
                Arrays.hashCode(myStringArray),
                myPojo);
    }

    @Override
    public String toString() {
        return "myBoolean: " + myBoolean + " myByte: " + myByte + " myShort: " + myShort + " myChar: " + myChar +
                " myInt: " + myInt + " myLong: " + myLong + " myFloat: " + myFloat + " myDouble: " + myDouble +
                " myString: " + myString + " myStringArray: " + Arrays.toString(myStringArray) + " myPojo: " +
                myPojo;
    }

    public boolean isMyBoolean() {
        return myBoolean;
    }

    public void setMyBoolean(final boolean myBoolean) {
        this.myBoolean = myBoolean;
    }

    public byte getMyByte() {
        return myByte;
    }

    public void setMyByte(final byte myByte) {
        this.myByte = myByte;
    }

    public short getMyShort() {
        return myShort;
    }

    public void setMyShort(final short myShort) {
        this.myShort = myShort;
    }

    public char getMyChar() {
        return myChar;
    }

    public void setMyChar(final char myChar) {
        this.myChar = myChar;
    }

    public int getMyInt() {
        return myInt;
    }

    public void setMyInt(final int myInt) {
        this.myInt = myInt;
    }

    public long getMyLong() {
        return myLong;
    }

    public void setMyLong(final long myLong) {
        this.myLong = myLong;
    }

    public float getMyFloat() {
        return myFloat;
    }

    public void setMyFloat(final float myFloat) {
        this.myFloat = myFloat;
    }

    public double getMyDouble() {
        return myDouble;
    }

    public void setMyDouble(final double myDouble) {
        this.myDouble = myDouble;
    }

    @Nullable
    public String getMyString() {
        return myString;
    }

    public void setMyString(@Nullable final String myString) {
        this.myString = myString;
    }

    @Nullable
    public String[] getMyStringArray() {
        return myStringArray;
    }

    public void setMyStringArray(@Nullable final String[] myStringArray) {
        this.myStringArray = myStringArray;
    }

    @Nullable
    public TestPojo getMyPojo() {
        return myPojo;
    }

    public void setMyPojo(@Nullable final TestPojo myPojo) {
        this.myPojo = myPojo;
    }

    public static void verifyExpected1And2(TestPojo expected1, TestPojo expected2,
                                           BlockingIterator<TestPojo> pojoItr) {
        assertTrue(pojoItr.hasNext());
        assertEquals(expected1, pojoItr.next());
        assertTrue(pojoItr.hasNext());
        TestPojo next = pojoItr.next();
        assertEquals(expected2, next);
        assertEquals(expected1, next.getMyPojo());
        assertFalse(pojoItr.hasNext());
    }
}
