/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_ACCOUNT_UID
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_CREATED_AT
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_ID
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_MEMO
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_QUANTITY_DENOM
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_QUANTITY_NUM
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_RECONCILE_DATE
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_RECONCILE_STATE
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_TRANSACTION_UID
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_TYPE
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_VALUE_DENOM
import org.gnucash.android.db.DatabaseSchema.SplitEntry.COLUMN_VALUE_NUM
import org.gnucash.android.db.DatabaseSchema.SplitEntry.TABLE_NAME
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.db.joinIn
import org.gnucash.android.math.toBigDecimal
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Split.Companion.FLAG_NOT_RECONCILED
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.TimestampHelper.timestampFromNow
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.IOException

/**
 * Database adapter for managing transaction splits in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class SplitsDbAdapter(
    val commoditiesDbAdapter: CommoditiesDbAdapter
) : DatabaseAdapter<Split>(
    commoditiesDbAdapter.holder,
    TABLE_NAME,
    arrayOf(
        COLUMN_MEMO,
        COLUMN_TYPE,
        COLUMN_VALUE_NUM,
        COLUMN_VALUE_DENOM,
        COLUMN_QUANTITY_NUM,
        COLUMN_QUANTITY_DENOM,
        COLUMN_CREATED_AT,
        COLUMN_RECONCILE_STATE,
        COLUMN_RECONCILE_DATE,
        COLUMN_ACCOUNT_UID,
        COLUMN_TRANSACTION_UID,
        COLUMN_SCHEDX_ACTION_ACCOUNT_UID
    )
) {
    private val accountCommodities = mutableMapOf<String, Commodity>()

    constructor(holder: DatabaseHolder, initCommodity: Boolean = false) :
            this(CommoditiesDbAdapter(holder, initCommodity))

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        super.close()
    }

    /**
     * Adds a split to the database.
     * The transactions belonging to the split are marked as exported
     *
     * @param split [Split] to be recorded in DB
     */
    override fun addRecord(split: Split, updateMethod: UpdateMethod): Split {
        val result = super.addRecord(split, updateMethod)

        if (updateMethod != UpdateMethod.Insert) {
            //when a split is updated, we want mark the transaction as not exported
            //modifying a split means modifying the accompanying transaction as well
            val transactionUID = split.transactionUID!!
            val content = ContentValues()
            content[TransactionEntry.COLUMN_EXPORTED] = false
            content[TransactionEntry.COLUMN_MODIFIED_AT] =
                getUtcStringFromTimestamp(timestampFromNow)
            updateRecord(TransactionEntry.TABLE_NAME, transactionUID, content)
        }

        return result
    }

    override fun bind(stmt: SQLiteStatement, split: Split): SQLiteStatement {
        bindBaseModel(stmt, split)
        if (split.memo != null) {
            stmt.bindString(1, split.memo)
        }
        stmt.bindString(2, split.type.value)
        stmt.bindLong(3, split.value.numerator)
        stmt.bindLong(4, split.value.denominator)
        stmt.bindLong(5, split.quantity.numerator)
        stmt.bindLong(6, split.quantity.denominator)
        stmt.bindString(7, getUtcStringFromTimestamp(split.createdTimestamp))
        stmt.bindString(8, split.reconcileState.toString())
        stmt.bindString(9, getUtcStringFromTimestamp(split.reconcileDate))
        stmt.bindString(10, split.accountUID)
        stmt.bindString(11, split.transactionUID)
        if (split.scheduledActionAccountUID != null) {
            stmt.bindString(12, split.scheduledActionAccountUID)
        }

        return stmt
    }

    /**
     * Builds a split instance from the data pointed to by the cursor provided
     *
     * This method will not move the cursor in any way. So the cursor should already by pointing to the correct entry
     *
     * @param cursor Cursor pointing to transaction record in database
     * @return [Split] instance
     */
    override fun buildModelInstance(cursor: Cursor): Split {
        val valueNum = cursor.getLong(COLUMN_VALUE_NUM)
        val valueDenom = cursor.getLong(COLUMN_VALUE_DENOM)
        val quantityNum = cursor.getLong(COLUMN_QUANTITY_NUM)
        val quantityDenom = cursor.getLong(COLUMN_QUANTITY_DENOM)
        val typeName = cursor.getString(COLUMN_TYPE)!!
        val accountUID = cursor.getString(COLUMN_ACCOUNT_UID)!!
        val transxUID = cursor.getString(COLUMN_TRANSACTION_UID)!!
        val memo = cursor.getString(COLUMN_MEMO)
        val reconcileState = cursor.getString(COLUMN_RECONCILE_STATE)
        val reconcileDate = cursor.getString(COLUMN_RECONCILE_DATE)
        val schedxAccountUID = cursor.getString(COLUMN_SCHEDX_ACTION_ACCOUNT_UID)

        val transactionCurrencyUID = getAttribute(
            TransactionEntry.TABLE_NAME,
            transxUID,
            TransactionEntry.COLUMN_COMMODITY_UID
        )
        val transactionCurrency = commoditiesDbAdapter.getRecord(transactionCurrencyUID)
        val value = Money(valueNum, valueDenom, transactionCurrency)
        val commodity = if (schedxAccountUID.isNullOrEmpty()) {
            getAccountCommodity(accountUID)
        } else {
            getAccountCommodity(schedxAccountUID)
        }
        val quantity = Money(quantityNum, quantityDenom, commodity)

        val split = Split(value, accountUID)
        populateBaseModelAttributes(cursor, split)
        split.quantity = quantity
        split.transactionUID = transxUID
        split.type = TransactionType.of(typeName)
        split.memo = memo.orEmpty()
        split.reconcileState = reconcileState?.get(0) ?: FLAG_NOT_RECONCILED
        if (!reconcileDate.isNullOrEmpty()) {
            split.reconcileDate = getTimestampFromUtcString(reconcileDate).getTime()
        }
        split.scheduledActionAccountUID = schedxAccountUID

        return split
    }

    fun computeSplitBalance(account: Account, startTimestamp: Long, endTimestamp: Long): Money {
        val accounts = mutableListOf(account)
        val balances = computeSplitBalances(accounts, startTimestamp, endTimestamp)
        val balance = balances[account.uid]
        return balance ?: createZeroInstance(account.commodity)
    }

    fun computeSplitBalances(
        accounts: List<Account>,
        startTimestamp: Long,
        endTimestamp: Long
    ): Map<String, Money> {
        val accountUIDs = accounts.map { it.uid }
        val selection = "a." + AccountEntry.COLUMN_UID + " IN " + accountUIDs.joinIn()

        return computeSplitBalances(selection, null, startTimestamp, endTimestamp)
    }

    fun computeSplitBalances(
        accountsWhere: String?,
        accountsWhereArgs: Array<String?>?,
        startTimestamp: Long,
        endTimestamp: Long
    ): Map<String, Money> {
        var selection = ("t." + TransactionEntry.COLUMN_TEMPLATE + " = 0"
                + " AND s." + COLUMN_QUANTITY_DENOM + " > 0")

        if (!accountsWhere.isNullOrEmpty()) {
            selection += " AND ($accountsWhere)"
        }

        val validStart = startTimestamp != AccountsDbAdapter.ALWAYS
        val validEnd = endTimestamp != AccountsDbAdapter.ALWAYS
        if (validStart && validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " BETWEEN " + startTimestamp + " AND " + endTimestamp
        } else if (validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " <= " + endTimestamp
        } else if (validStart) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " >= " + startTimestamp
        }

        val sql = SQLiteQueryBuilder.buildQueryString(
            false,
            TransactionEntry.TABLE_NAME + " t" +
                    " INNER JOIN " + tableName + " s ON t." + TransactionEntry.COLUMN_UID + " = s." + COLUMN_TRANSACTION_UID +
                    " INNER JOIN " + AccountEntry.TABLE_NAME + " a ON s." + COLUMN_ACCOUNT_UID + " = a." + AccountEntry.COLUMN_UID,
            arrayOf<String?>(
                "SUM(s.$COLUMN_QUANTITY_NUM)",
                "s.$COLUMN_QUANTITY_DENOM",
                "s.$COLUMN_TYPE",
                "a." + AccountEntry.COLUMN_UID,
                "a." + AccountEntry.COLUMN_COMMODITY_UID
            ),
            selection,
            "a." + AccountEntry.COLUMN_UID
                    + ", s." + COLUMN_TYPE
                    + ", s." + COLUMN_QUANTITY_DENOM,
            null,
            null,
            null
        )
        val totals = mutableMapOf<String, Money>()
        db.rawQuery(sql, accountsWhereArgs).forEach { cursor ->
            //FIXME beware of 64-bit overflow - get as BigInteger
            var amountNumer = cursor.getLong(0)
            val amountDenom = cursor.getLong(1)
            val splitType = cursor.getString(2)
            val accountUID = cursor.getString(3)
            val commodityUID = cursor.getString(4)

            if (credit == splitType) {
                amountNumer = -amountNumer
            }
            val amount = toBigDecimal(amountNumer, amountDenom)
            val commodity = commoditiesDbAdapter.getRecord(commodityUID)
            val balance = Money(amount, commodity)
            var total = totals[accountUID]
            if (total == null) {
                total = balance
            } else {
                total += balance
            }
            totals[accountUID] = total
        }
        return totals
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionUID String unique ID of transaction
     * @return List of [Split]s
     */
    fun getSplitsForTransaction(transactionUID: String): List<Split> {
        val cursor = fetchSplitsForTransaction(transactionUID)
        return getRecords(cursor)
    }

    /**
     * Fetch splits for a given transaction within a specific account
     *
     * @param transactionUID String unique ID of transaction
     * @param accountUID     String unique ID of account
     * @return List of splits
     */
    fun getSplitsForTransactionInAccount(
        transactionUID: String,
        accountUID: String
    ): List<Split> {
        val cursor = fetchSplitsForTransactionAndAccount(transactionUID, accountUID)
        return getRecords(cursor)
    }

    private val sqlForTransaction: String by lazy {
        val selection = "$COLUMN_TRANSACTION_UID = ?"
        val orderBy = "$COLUMN_ID ASC"
        SQLiteQueryBuilder.buildQueryString(
            false,
            tableName,
            null,
            selection,
            null,
            null,
            orderBy,
            null
        )
    }

    /**
     * Returns a Cursor to a dataset of splits belonging to a specific transaction
     *
     * @param transactionUID Unique identifier of the transaction
     * @return Cursor to splits
     */
    fun fetchSplitsForTransaction(transactionUID: String): Cursor? {
        if (transactionUID.isEmpty()) return null

        Timber.v("Fetching splits for transaction %s", transactionUID)
        val selectionArgs = arrayOf<String?>(transactionUID)
        return db.rawQuery(sqlForTransaction, selectionArgs)
    }

    private val sqlForTransactionAndAccount: String by lazy {
        val selection = COLUMN_TRANSACTION_UID + " = ? AND " +
                COLUMN_ACCOUNT_UID + " = ?"
        val orderBy = "$COLUMN_VALUE_NUM ASC"
        SQLiteQueryBuilder.buildQueryString(
            false,
            tableName,
            null,
            selection,
            null,
            null,
            orderBy,
            null
        )
    }

    /**
     * Returns a cursor to splits for a given transaction and account
     *
     * @param transactionUID Unique identifier of the transaction
     * @param accountUID     String unique ID of account
     * @return Cursor to splits data set
     */
    fun fetchSplitsForTransactionAndAccount(transactionUID: String, accountUID: String): Cursor? {
        if (transactionUID.isEmpty() || accountUID.isEmpty()) return null

        Timber.v("Fetching splits for transaction %s and account %s", transactionUID, accountUID)
        val selectionArgs = arrayOf<String?>(transactionUID, accountUID)
        return db.rawQuery(sqlForTransactionAndAccount, selectionArgs)
    }

    override fun deleteRecord(rowId: Long): Boolean {
        val split = getRecord(rowId)
        val transactionUID = split.transactionUID
        var result = super.deleteRecord(rowId)

        if (!result)  //we didn't delete for whatever reason, invalid rowId etc
            return false

        //if we just deleted the last split, then remove the transaction from db
        if (!transactionUID.isNullOrEmpty()) {
            val cursor = fetchSplitsForTransaction(transactionUID) ?: return false
            try {
                if (cursor.count > 0) {
                    val transactionID = getTransactionID(transactionUID)
                    result = db.delete(
                        TransactionEntry.TABLE_NAME,
                        TransactionEntry.COLUMN_ID + "=" + transactionID, null
                    ) > 0
                }
            } finally {
                cursor.close()
            }
        }
        return result
    }

    private val sqlTransactionID: String by lazy {
        SQLiteQueryBuilder.buildQueryString(
            false,
            TransactionEntry.TABLE_NAME,
            arrayOf(TransactionEntry.COLUMN_ID),
            TransactionEntry.COLUMN_UID + "=?",
            null,
            null,
            null,
            null
        )
    }

    /**
     * Returns the database record ID for the specified transaction UID
     *
     * @param transactionUID Unique identifier of the transaction
     * @return Database record ID for the transaction
     */
    @Throws(IllegalArgumentException::class)
    fun getTransactionID(transactionUID: String): Long {
        return DatabaseUtils.longForQuery(db, sqlTransactionID, arrayOf<String?>(transactionUID))
    }

    fun reassignAccount(oldAccountUID: String, newAccountUID: String) {
        updateRecords(
            "$COLUMN_ACCOUNT_UID = ?",
            arrayOf<String?>(oldAccountUID),
            COLUMN_ACCOUNT_UID,
            newAccountUID
        )
    }

    private val sqlAccountCommodity: String by lazy {
        SQLiteQueryBuilder.buildQueryString(
            false,
            AccountEntry.TABLE_NAME,
            arrayOf<String?>(AccountEntry.COLUMN_COMMODITY_UID),
            AccountEntry.COLUMN_UID + "= ?",
            null,
            null,
            null,
            null
        )
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier `accountUID`
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    @Throws(IllegalArgumentException::class)
    fun getAccountCommodity(accountUID: String): Commodity {
        var commodity = accountCommodities[accountUID]
        if (commodity != null) {
            return commodity
        }
        val selectionArgs = arrayOf<String?>(accountUID)
        val commodityUID = DatabaseUtils.stringForQuery(db, sqlAccountCommodity, selectionArgs)
        commodity = commoditiesDbAdapter.getRecord(commodityUID)
        accountCommodities[accountUID] = commodity
        return commodity
    }

    companion object {
        private val credit = TransactionType.CREDIT.value

        /**
         * Returns application-wide instance of the database adapter
         *
         * @return SplitsDbAdapter instance
         */
        val instance: SplitsDbAdapter get() = GnuCashApplication.splitsDbAdapter!!
    }
}
