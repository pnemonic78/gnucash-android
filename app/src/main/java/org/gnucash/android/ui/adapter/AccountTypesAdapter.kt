package org.gnucash.android.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import org.gnucash.android.R
import org.gnucash.android.model.AccountType

class AccountTypesAdapter @JvmOverloads constructor(
    context: Context,
    @LayoutRes resource: Int = android.R.layout.simple_spinner_item
) : ArrayAdapter<AccountTypesAdapter.Label>(context, resource) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val records = AccountType.entries.filterNot { it == AccountType.ROOT }.toTypedArray()
        val labels = context.resources.getStringArray(R.array.key_account_type_entries)
        clear()
        addAll(records.map { Label(it, labels[it.ordinal]) })
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    fun getType(position: Int): AccountType? {
        return getItem(position)?.type
    }

    fun getPosition(value: AccountType): Int {
        for (i in 0 until count) {
            val type = getType(i)!!
            if (type == value) {
                return i
            }
        }
        return -1
    }

    data class Label(val type: AccountType, val text: String) {
        override fun toString(): String = text
    }
}