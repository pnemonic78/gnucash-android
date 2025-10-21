package org.gnucash.android.ui.search

import android.database.DatabaseUtils.sqlEscapeString
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.toDateTimeAtEndOfDay
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

data class SearchForm(
    var comparisonType: ComparisonType = ComparisonType.All
) {
    private val _criteria: MutableList<SearchCriteria> = mutableListOf()
    val criteria: List<SearchCriteria> get() = _criteria

    private val amountFormatter by lazy {
        val commodity = Commodity.DEFAULT_COMMODITY
        (NumberFormat.getInstance(Locale.getDefault()) as DecimalFormat).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = commodity.smallestFractionDigits
            isGroupingUsed = false
        }
    }

    fun addDescription(): SearchCriteria.Description {
        val criterion = SearchCriteria.Description()
        _criteria.add(criterion)
        return criterion
    }

    fun addNote(): SearchCriteria.Note {
        val criterion = SearchCriteria.Note()
        _criteria.add(criterion)
        return criterion
    }

    fun addMemo(): SearchCriteria.Memo {
        val criterion = SearchCriteria.Memo()
        _criteria.add(criterion)
        return criterion
    }

    fun addDate(): SearchCriteria.Date {
        val criterion = SearchCriteria.Date()
        _criteria.add(criterion)
        return criterion
    }

    fun addNumeric(debcred: Boolean): SearchCriteria.Numeric {
        val match = if (debcred) NumericMatch.HasDebitsOrCredits else null
        val criterion = SearchCriteria.Numeric(match = match)
        _criteria.add(criterion)
        return criterion
    }

    fun addAccount(): SearchCriteria.Account {
        val criterion = SearchCriteria.Account()
        _criteria.add(criterion)
        return criterion
    }

    fun remove(criterion: SearchCriteria) {
        _criteria.remove(criterion)
    }

    fun buildSQL(): String {
        val where = StringBuilder()
        for (criterion in criteria) {
            if (where.isNotEmpty()) {
                if (comparisonType == ComparisonType.All) {
                    where.append(" AND ")
                } else {
                    where.append(" OR ")
                }
            }
            where.append('(')
            appendQuery(criterion, where)
            where.append(')')
        }

        return where.toString()
    }

    private fun appendQuery(criterion: SearchCriteria, where: StringBuilder) {
        when (criterion) {
            is SearchCriteria.Account -> appendQuery(criterion, where)
            is SearchCriteria.Date -> appendQuery(criterion, where)
            is SearchCriteria.Description -> appendQuery(criterion, where)
            is SearchCriteria.Memo -> appendQuery(criterion, where)
            is SearchCriteria.Note -> appendQuery(criterion, where)
            is SearchCriteria.Numeric -> appendQuery(criterion, where)
        }
    }

    private fun appendQuery(criterion: SearchCriteria.Numeric, where: StringBuilder) {
        val amount = criterion.value ?: BigDecimal.ZERO
        val whereLength = where.length
        where.append('(')
            .append(SplitEntry.TABLE_NAME)
            .append('.')
            .append(SplitEntry.COLUMN_VALUE_NUM)
            .append(" * 1.0 / ")
            .append(SplitEntry.TABLE_NAME)
            .append('.')
            .append(SplitEntry.COLUMN_VALUE_DENOM)
            .append(')')
        when (criterion.compare) {
            Compare.LessThan -> where.append(" < ")
            Compare.LessThanOrEqualTo -> where.append(" <= ")
            Compare.EqualTo -> where.append(" = ")
            Compare.NotEqualTo -> where.append(" <> ")
            Compare.GreaterThan -> where.append(" > ")
            Compare.GreaterThanOrEqualTo -> where.append(" >= ")
        }
        where.append(amountFormatter.format(amount))

        if (criterion.match != null && criterion.match != NumericMatch.HasDebitsOrCredits) {
            val typeName = if (criterion.match == NumericMatch.HasDebits) {
                TransactionType.DEBIT.value
            } else {
                TransactionType.CREDIT.value
            }
            where.insert(whereLength, '(')
            where.append(") AND (")
                .append(SplitEntry.TABLE_NAME)
                .append('.')
                .append(SplitEntry.COLUMN_TYPE)
                .append(" = ")
                .append(sqlEscapeString(typeName))
                .append(')')
        }
    }

    private fun appendQuery(criterion: SearchCriteria.Date, where: StringBuilder) {
        val date = criterion.value ?: LocalDate.now()
        val dayStart = date.toDateTimeAtStartOfDay()
        val dayEnd = date.toDateTimeAtEndOfDay()

        where.append(TransactionEntry.COLUMN_TIMESTAMP)

        when (criterion.compare) {
            Compare.LessThan -> {
                where.append(" < ").append(dayEnd.toMillis())
            }

            Compare.LessThanOrEqualTo -> {
                where.append(" <= ").append(dayEnd.toMillis())
            }

            Compare.EqualTo -> {
                where.append(" BETWEEN ")
                    .append(dayStart.toMillis())
                    .append(" AND ")
                    .append(dayEnd.toMillis())
            }

            Compare.NotEqualTo -> {
                where.append(" NOT BETWEEN ")
                    .append(dayStart.toMillis())
                    .append(" AND ")
                    .append(dayEnd.toMillis())
            }

            Compare.GreaterThan -> {
                where.append(" > ").append(dayStart.toMillis())
            }

            Compare.GreaterThanOrEqualTo -> {
                where.append(" >= ").append(dayStart.toMillis())
            }
        }
    }

    private fun appendQuery(
        criterion: SearchCriteria.Description,
        where: StringBuilder
    ) {
        val s = criterion.value.orEmpty()
        where.append(TransactionEntry.COLUMN_DESCRIPTION)
        when (criterion.compare) {
            StringCompare.Contains -> where.append(" LIKE ")
                .append(sqlEscapeLike(s))

            StringCompare.Equals -> where.append(" = ")
                .append(sqlEscapeString(s))
        }
    }

    private fun appendQuery(criterion: SearchCriteria.Memo, where: StringBuilder) {
        val s = criterion.value.orEmpty()
        where.append(SplitEntry.TABLE_NAME)
            .append('.')
            .append(SplitEntry.COLUMN_MEMO)
        when (criterion.compare) {
            StringCompare.Contains -> where.append(" LIKE ")
                .append(sqlEscapeLike(s))

            StringCompare.Equals -> where.append(" = ")
                .append(sqlEscapeString(s))
        }
    }

    private fun appendQuery(criterion: SearchCriteria.Note, where: StringBuilder) {
        val s = criterion.value.orEmpty()
        where.append(TransactionEntry.COLUMN_NOTES)
        when (criterion.compare) {
            StringCompare.Contains -> where.append(" LIKE ")
                .append(sqlEscapeLike(s))

            StringCompare.Equals -> where.append(" = ")
                .append(sqlEscapeString(s))
        }
    }

    private fun appendQuery(criterion: SearchCriteria.Account, where: StringBuilder) {
        val s = criterion.value?.uid ?: return
        where.append(SplitEntry.TABLE_NAME)
            .append('.')
            .append(SplitEntry.COLUMN_ACCOUNT_UID)
        when (criterion.compare) {
            ComparisonType.Any -> where.append(" = ")
                .append(sqlEscapeString(s))

            ComparisonType.None -> where.append(" <> ")
                .append(sqlEscapeString(s))

            ComparisonType.All -> where.append(" <> ''")
        }
    }
}
