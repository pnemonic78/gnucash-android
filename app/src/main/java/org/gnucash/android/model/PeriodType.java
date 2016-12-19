/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
* Represents a type of period which can be associated with a recurring event
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see org.gnucash.android.model.ScheduledAction
*/
public enum PeriodType {
    // TODO: 22.10.2015 add support for hourly
    ONCE("once"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    END_OF_MONTH("end of month"),
    NTH_WEEKDAY("nth weekday"),
    LAST_WEEKDAY("last weekday"),
    YEAR("year");

    private final String value;

    PeriodType(String value) {
        this.value = value;
    }

    /**
     * Get the value.
     * @return the value.
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue();
    }

    /**
     * Find the relevant period type.
     * @param value the period value.
     * @return the period type.
     * @throws IllegalArgumentException if the period type is not found.
     */
    public static PeriodType find(String value) throws IllegalArgumentException {
        for (PeriodType v : PeriodType.values()) {
            if (v.getValue().equals(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException();
    }

    /**
     * Returns the frequency description of this period type.
     * This is used mostly for generating the recurrence rule.
     * @return Frequency description
     */
    public String getFrequencyDescription() {
        switch (this) {
            case DAY:
                return "DAILY";
            case WEEK:
            case NTH_WEEKDAY:
            case LAST_WEEKDAY:
                return "WEEKLY";
            case MONTH:
            case END_OF_MONTH:
                return "MONTHLY";
            case YEAR:
                return "YEARLY";
            default:
                return "";
        }
    }

    /**
     * Returns the parts of the recurrence rule which describe the day or month on which to run the
     * scheduled transaction. These parts are the BYxxx
     * @param startTime Start time of transaction used to determine the start day of execution
     * @return String describing the BYxxx parts of the recurrence rule
     */
    public String getByParts(long startTime){
        String partString = "";
        switch (this){
            case DAY:
                break;
            case WEEK:
                String dayOfWeek = new SimpleDateFormat("E", Locale.US).format(new Date(startTime));
                //our parser only supports two-letter day names
                partString = "BYDAY=" + dayOfWeek.substring(0, dayOfWeek.length()-1).toUpperCase();
            case MONTH:
                break;
            case YEAR:
                break;
        }
        return partString;
    }
}
