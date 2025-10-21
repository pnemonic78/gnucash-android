package org.gnucash.android.ui.search

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.toDateTimeAtEndOfDay
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import org.junit.Test
import java.math.BigDecimal

class SearchFormTest : GnuCashTest() {
    @Test
    fun `Query for single description`() {
        val form = SearchForm()
        val criterion = form.addDescription()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        var where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_DESCRIPTION} LIKE '%zebra%')")

        criterion.compare = StringCompare.Equals
        where = form.buildSQL()
        assertThat(where).isEqualTo("(${TransactionEntry.COLUMN_DESCRIPTION} = 'zebra')")
    }

    @Test
    fun `Query for single note`() {
        val column = TransactionEntry.COLUMN_NOTES
        val form = SearchForm()
        val criterion = form.addNote()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        var where = form.buildSQL()
        assertThat(where).isEqualTo("($column LIKE '%zebra%')")

        criterion.compare = StringCompare.Equals
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column = 'zebra')")
    }

    @Test
    fun `Query for single memo`() {
        val column = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_MEMO
        val form = SearchForm()
        val criterion = form.addMemo()
        criterion.value = "zebra"
        criterion.compare = StringCompare.Contains
        var where = form.buildSQL()
        assertThat(where).isEqualTo("($column LIKE '%zebra%')")

        criterion.compare = StringCompare.Equals
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column = 'zebra')")
    }

    @Test
    fun `Query for single date`() {
        val column = TransactionEntry.COLUMN_TIMESTAMP
        val form = SearchForm()
        val criterion = form.addDate()
        val now = LocalDate.now()
        val dayStart = now.toDateTimeAtStartOfDay()
        val dayEnd = now.toDateTimeAtEndOfDay()

        criterion.value = now
        criterion.compare = Compare.GreaterThan
        var where = form.buildSQL()
        assertThat(where).isEqualTo("($column > ${dayStart.toMillis()})")

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column >= ${dayStart.toMillis()})")

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column < ${dayEnd.toMillis()})")

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column <= ${dayEnd.toMillis()})")

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column BETWEEN ${dayStart.toMillis()} AND ${dayEnd.toMillis()})")

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("($column NOT BETWEEN ${dayStart.toMillis()} AND ${dayEnd.toMillis()})")
    }

    @Test
    fun `Query for single amount`() {
        val column1 = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM
        val column2 = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM
        val form = SearchForm()
        val criterion = form.addNumeric(false)
        criterion.value = BigDecimal.valueOf(123.45)
        criterion.compare = Compare.GreaterThan
        var where = form.buildSQL()
        assertThat(where).isEqualTo("(($column1 * 1.0 / $column2) > 123.45)")

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(($column1 * 1.0 / $column2) >= 123.45)")

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        assertThat(where).isEqualTo("(($column1 * 1.0 / $column2) < 123.45)")

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(($column1 * 1.0 / $column2) <= 123.45)")

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(($column1 * 1.0 / $column2) = 123.45)")

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("(($column1 * 1.0 / $column2) <> 123.45)")
    }

    @Test
    fun `Query for single value`() {
        `Query for single value`(NumericMatch.HasDebits)
        `Query for single value`(NumericMatch.HasCredits)
    }

    fun `Query for single value`(match: NumericMatch) {
        val column1 = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM
        val column2 = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM
        val column3 = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE
        val typeName = if (match == NumericMatch.HasDebits) {
            TransactionType.DEBIT.value
        } else {
            TransactionType.CREDIT.value
        }
        val form = SearchForm()
        val criterion = form.addNumeric(true)
        criterion.value = BigDecimal.valueOf(123.45)
        criterion.compare = Compare.GreaterThan
        criterion.match = match
        var where = form.buildSQL()
        assertThat(where).isEqualTo("((($column1 * 1.0 / $column2) > 123.45) AND ($column3 = '$typeName'))")

        criterion.compare = Compare.GreaterThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((($column1 * 1.0 / $column2) >= 123.45) AND ($column3 = '$typeName'))")

        criterion.compare = Compare.LessThan
        where = form.buildSQL()
        assertThat(where).isEqualTo("((($column1 * 1.0 / $column2) < 123.45) AND ($column3 = '$typeName'))")

        criterion.compare = Compare.LessThanOrEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((($column1 * 1.0 / $column2) <= 123.45) AND ($column3 = '$typeName'))")

        criterion.compare = Compare.EqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((($column1 * 1.0 / $column2) = 123.45) AND ($column3 = '$typeName'))")

        criterion.compare = Compare.NotEqualTo
        where = form.buildSQL()
        assertThat(where).isEqualTo("((($column1 * 1.0 / $column2) <> 123.45) AND ($column3 = '$typeName'))")
    }
}