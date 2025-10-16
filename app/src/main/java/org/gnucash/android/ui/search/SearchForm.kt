package org.gnucash.android.ui.search

import android.os.Bundle
import java.math.BigDecimal

data class SearchForm(
    var description: String? = null,
    var notes: String? = null,
    var memo: String? = null,
    var dateMin: Long? = null,
    var dateMax: Long? = null,
    var amountMin: BigDecimal? = null,
    var amountMax: BigDecimal? = null
)

private const val EXTRA_DESCRIPTION = "description"
private const val EXTRA_NOTES = "notes"
private const val EXTRA_MEMO = "memo"
private const val EXTRA_DATE_START = "date_start"
private const val EXTRA_DATE_END = "date_end"

fun Bundle.putForm(form: SearchForm) {
    putString(EXTRA_DESCRIPTION, form.description)
    putString(EXTRA_NOTES, form.notes)
    putString(EXTRA_MEMO, form.memo)
    form.dateMin?.let { putLong(EXTRA_DATE_START, it) }
    form.dateMax?.let { putLong(EXTRA_DATE_END, it) }
}

fun Bundle.getForm(): SearchForm {
    val form = SearchForm()
    form.description = getString(EXTRA_DESCRIPTION)
    form.notes = getString(EXTRA_NOTES)
    form.memo = getString(EXTRA_MEMO)
    if (containsKey(EXTRA_DATE_START)) {
        form.dateMin = getLong(EXTRA_DATE_START)
    }
    if (containsKey(EXTRA_DATE_END)) {
        form.dateMax = getLong(EXTRA_DATE_END)
    }
    return form
}