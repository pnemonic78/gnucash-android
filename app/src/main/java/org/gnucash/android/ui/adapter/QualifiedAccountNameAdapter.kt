package org.gnucash.android.ui.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.Account

class QualifiedAccountNameAdapter @JvmOverloads constructor(
    context: Context,
    private val where: String? = null,
    private val whereArgs: Array<String>? = null,
    adapter: AccountsDbAdapter = AccountsDbAdapter.getInstance(),
    @LayoutRes resource: Int = android.R.layout.simple_spinner_item
) : ArrayAdapter<QualifiedAccountNameAdapter.Label>(context, resource) {

    private val recordsByUID = mutableMapOf<String, Account>()

    constructor(context: Context, adapter: AccountsDbAdapter) :
            this(context = context, adapter = adapter, where = null)

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        swapAdapter(adapter)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getItemId(position: Int): Long {
        return getAccount(position)?.id ?: -position.toLong()
    }

    fun getAccount(position: Int): Account? {
        return getItem(position)?.account
    }

    fun getUID(position: Int): String? {
        return getAccount(position)?.uID
    }

    fun getAccount(uid: String?): Account? {
        if (uid.isNullOrEmpty()) return null
        return recordsByUID[uid]
    }

    fun getPosition(uid: String?): Int {
        if (uid.isNullOrEmpty()) return -1

        for (i in 0 until count) {
            if (getUID(i) == uid) return i
        }
        return -1
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val account = getAccount(position)!!

        val view = super.getDropDownView(position, convertView, parent)
        val textView = if (view is TextView) view else view.findViewById(android.R.id.text1)

        @DrawableRes val icon = if (account.isFavorite) R.drawable.ic_favorite else 0
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)

        return view
    }

    fun swapAdapter(adapter: AccountsDbAdapter) {
        //FIXME fetch list in IO thread
        val records = adapter.getSimpleAccountList(
            where ?: (AccountEntry.COLUMN_HIDDEN + " = 0"),
            whereArgs,
            AccountEntry.COLUMN_FAVORITE + " DESC, " + AccountEntry.COLUMN_FULL_NAME + " ASC"
        )
        recordsByUID.clear()
        recordsByUID.putAll(records.associateBy { it.uID!! })
        clear()
        addAll(records.map { Label(it) })
    }

    fun getDescendants(uid: String?): List<Account> {
        val result = mutableListOf<Account>()
        if (uid.isNullOrEmpty()) return result
        populateDescendants(uid, result)
        return result
    }

    private fun populateDescendants(uid: String, result: MutableList<Account>) {
        for (i in 0 until count) {
            val account = getAccount(i)!!
            if (account.parentUID == uid) {
                result.add(account)
                populateDescendants(account.uID!!, result)
            }
        }
    }

    data class Label(val account: Account) {
        override fun toString(): String {
            return account.fullName ?: account.name
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun where(
            context: Context,
            where: String,
            whereArgs: Array<String>? = null
        ): QualifiedAccountNameAdapter =
            QualifiedAccountNameAdapter(context = context, where = where, whereArgs = whereArgs)
    }
}