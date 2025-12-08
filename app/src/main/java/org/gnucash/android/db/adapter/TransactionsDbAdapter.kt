/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
import android.database.SQLException
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_COMMODITY_UID
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_CREATED_AT
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_CURRENCY
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_EXPORTED
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_ID
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_MODIFIED_AT
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_NOTES
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_NUMBER
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_SCHEDX_ACTION_UID
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_TEMPLATE
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.COLUMN_UID
import org.gnucash.android.db.DatabaseSchema.TransactionEntry.TABLE_NAME
import org.gnucash.android.db.alias
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getBoolean
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.Transaction.Companion.computeBalance
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.TimestampHelper.timestampFromEpochZero
import org.gnucash.android.util.TimestampHelper.timestampFromNow
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.IOException
import java.sql.Timestamp

/**
 * Manages persistence of [Transaction]s in the database
 * Handles adding, modifying and deleting of transaction records.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class TransactionsDbAdapter(
    val splitsDbAdapter: SplitsDbAdapter
) : DatabaseAdapter<Transaction>(
    splitsDbAdapter.holder,
    TABLE_NAME,
    arrayOf(
        COLUMN_DESCRIPTION,
        COLUMN_NOTES,
        COLUMN_TIMESTAMP,
        COLUMN_EXPORTED,
        COLUMN_CURRENCY,
        COLUMN_COMMODITY_UID,
        COLUMN_CREATED_AT,
        COLUMN_SCHEDX_ACTION_UID,
        COLUMN_TEMPLATE,
        COLUMN_NUMBER
    )
) {
    val commoditiesDbAdapter: CommoditiesDbAdapter = splitsDbAdapter.commoditiesDbAdapter

    /**
     * Overloaded constructor. Creates adapter for already open db
     *
     * @param holder Database holder
     */
    constructor(holder: DatabaseHolder, initCommodity: Boolean = false) :
            this(SplitsDbAdapter(holder, initCommodity))

    constructor(commoditiesDbAdapter: CommoditiesDbAdapter) :
            this(SplitsDbAdapter(commoditiesDbAdapter))

    init {
        createTempView()
    }

    /**
     * Adds an transaction to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead
     *
     * @param transaction [Transaction] to be inserted to database
     */
    @Throws(SQLException::class)
    override fun addRecord(transaction: Transaction, updateMethod: UpdateMethod): Transaction {
        // Did the transaction have any splits before?
        val didChange = transaction.id != 0L
        try {
            beginTransaction()
            val imbalanceSplit = transaction.createAutoBalanceSplit()
            if (imbalanceSplit != null) {
                val context = holder.context
                val imbalanceAccountUID = AccountsDbAdapter(this)
                    .getOrCreateImbalanceAccountUID(context, transaction.commodity)
                imbalanceSplit.accountUID = imbalanceAccountUID
            }
            super.addRecord(transaction, updateMethod)

            val splits = transaction.splits
            Timber.d("Adding %d splits for transaction", splits.size)
            val splitUIDs = mutableListOf<String>()
            for (split in splits) {
                if (imbalanceSplit === split) {
                    splitsDbAdapter.addRecord(split, UpdateMethod.Insert)
                } else {
                    splitsDbAdapter.addRecord(split, updateMethod)
                }
                splitUIDs.add(split.uid)
            }

            if (didChange) {
                val deleteWhere = (SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                        + SplitEntry.COLUMN_UID + " NOT IN " + splitUIDs.joinIn())
                val deleteArgs = arrayOf<String?>(transaction.uid)
                val deleted = db.delete(SplitEntry.TABLE_NAME, deleteWhere, deleteArgs)
                Timber.d("%d splits deleted", deleted)
            }

            setTransactionSuccessful()
        } finally {
            endTransaction()
        }

        return transaction
    }

    private val deleteEmptyTransaction: SQLiteStatement by lazy {
        db.compileStatement(
            "DELETE FROM $tableName WHERE NOT EXISTS ( SELECT * FROM " + SplitEntry.TABLE_NAME +
                    " WHERE " + tableName + "." + COLUMN_UID +
                    " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) "
        )
    }

    /**
     * Adds an several transactions to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead. Recurrence Transactions will not
     * be inserted, instead schedule Transaction would be called. If an exception
     * occurs, no transaction would be inserted.
     *
     * @param transactions [Transaction] transactions to be inserted to database
     * @return Number of transactions inserted
     */
    @Throws(SQLException::class)
    override fun bulkAddRecords(transactions: List<Transaction>, updateMethod: UpdateMethod): Long {
        var start = System.nanoTime()
        val rowInserted = super.bulkAddRecords(transactions, updateMethod)
        val end = System.nanoTime()
        Timber.d("bulk add transaction time %d", end - start)
        val splits = transactions.flatMap { it.splits }
        if (rowInserted != 0L && !splits.isEmpty()) {
            try {
                start = System.nanoTime()
                val nSplits = splitsDbAdapter.bulkAddRecords(splits, updateMethod)
                Timber.d("%d splits inserted in %d ns", nSplits, System.nanoTime() - start)
            } finally {
                deleteEmptyTransaction.execute()
            }
        }
        return rowInserted
    }

    override fun bind(stmt: SQLiteStatement, transaction: Transaction): SQLiteStatement {
        bindBaseModel(stmt, transaction)
        stmt.bindString(1, transaction.description)
        stmt.bindString(2, transaction.notes)
        stmt.bindLong(3, transaction.time)
        stmt.bindBoolean(4, transaction.isExported)
        stmt.bindString(5, transaction.currencyCode)
        stmt.bindString(6, transaction.commodity.uid)
        stmt.bindString(7, getUtcStringFromTimestamp(transaction.createdTimestamp))
        if (transaction.scheduledActionUID != null) {
            stmt.bindString(8, transaction.scheduledActionUID)
        }
        stmt.bindBoolean(9, transaction.isTemplate)
        stmt.bindString(10, transaction.number)

        return stmt
    }

    private val sqlAllTransactionsForAccount: String by lazy {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = tableName + " t" +
                " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON " +
                "t." + COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID +
                " INNER JOIN " + AccountEntry.TABLE_NAME + " a ON " +
                "a." + AccountEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_ACCOUNT_UID
        val projectionIn = arrayOf("t.*")
        val selection = "t." + COLUMN_TEMPLATE + " = 0" +
                " AND s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
        val sortOrder = "t." + COLUMN_TIMESTAMP + " DESC, " +
                "t." + COLUMN_NUMBER + " DESC, " +
                "t." + COLUMN_ID + " DESC"
        val groupBy = "t.$COLUMN_UID"

        queryBuilder.buildQuery(projectionIn, selection, groupBy, null, sortOrder, null)
    }

    /**
     * Returns a cursor to a set of all transactions which have a split belonging to the account with unique ID
     * `accountUID`.
     *
     * @param accountUID UID of the account whose transactions are to be retrieved
     * @return Cursor holding set of transactions for particular account
     * @throws java.lang.IllegalArgumentException if the accountUID is null
     */
    fun fetchAllTransactionsForAccount(accountUID: String): Cursor? {
        if (accountUID.isEmpty()) return null
        val selectionArgs = arrayOf<String?>(accountUID)
        return db.rawQuery(sqlAllTransactionsForAccount, selectionArgs)
    }

    /**
     * Returns a cursor to all scheduled transactions which have at least one split in the account
     *
     * This is basically a set of all template transactions for this account
     *
     * @param accountUID GUID of account
     * @return Cursor with set of transactions
     */
    fun fetchScheduledTransactionsForAccount(accountUID: String): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.isDistinct = true
        queryBuilder.tables = tableName + " t" +
                " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON " +
                "t." + COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID
        val projectionIn = arrayOf<String?>("t.*")
        val selection = "s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?" +
                " AND t." + COLUMN_TEMPLATE + " = 1"
        val selectionArgs = arrayOf<String?>(accountUID)
        val sortOrder = "t.$COLUMN_TIMESTAMP DESC"

        return queryBuilder.query(
            db,
            projectionIn,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
    }

    private val sqlDeleteTransactionsForAccount = "DELETE FROM " + tableName +
            " WHERE " + COLUMN_UID + " IN " +
            " (SELECT " + SplitEntry.COLUMN_TRANSACTION_UID +
            " FROM " + SplitEntry.TABLE_NAME + " WHERE " +
            SplitEntry.COLUMN_ACCOUNT_UID + " = ?)"

    /**
     * Deletes all transactions which contain a split in the account.
     *
     * **Note:**As long as the transaction has one split which belongs to the account `accountUID`,
     * it will be deleted. The other splits belonging to the transaction will also go away
     *
     * @param accountUID GUID of the account
     */
    fun deleteTransactionsForAccount(accountUID: String) {
        val selectionArgs = arrayOf<String?>(accountUID)
        db.execSQL(sqlDeleteTransactionsForAccount, selectionArgs)
    }

    /**
     * Returns list of all transactions for account with UID `accountUID`
     *
     * @param accountUID UID of account whose transactions are to be retrieved
     * @return List of [Transaction]s for account with UID `accountUID`
     */
    fun getAllTransactionsForAccount(accountUID: String): List<Transaction> {
        val cursor = fetchAllTransactionsForAccount(accountUID)
        return getRecords(cursor)
    }

    /**
     * Returns all transaction instances in the database.
     *
     * @return List of all transactions
     */
    @get:Deprecated("")
    val allTransactions: List<Transaction>
        get() = allRecords

    fun fetchTransactionsWithSplits(
        columns: Array<String?>?,
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        val table = tableName + " t, " + SplitEntry.TABLE_NAME + " s" +
                " ON t." + COLUMN_UID +
                " = s." + SplitEntry.COLUMN_TRANSACTION_UID +
                ", trans_extra_info ON trans_extra_info.trans_acct_t_uid = t." + COLUMN_UID
        return db.query(table, columns, where, whereArgs, null, null, orderBy)
    }

    /**
     * Fetch all transactions modified since a given timestamp
     *
     * @param timestamp Timestamp in milliseconds (since Epoch)
     * @return Cursor to the results
     */
    fun fetchTransactionsToExportSince(timestamp: Timestamp): Cursor {
        val where = COLUMN_TEMPLATE + " = 0 AND " +
                COLUMN_EXPORTED + " = 0 AND " +
                COLUMN_MODIFIED_AT + " >= ?"
        val whereArgs = arrayOf<String?>(getUtcStringFromTimestamp(timestamp))
        val orderBy = COLUMN_TIMESTAMP + " ASC, " +
                COLUMN_NUMBER + " ASC, " +
                COLUMN_ID + " ASC"
        return fetchAllRecords(where, whereArgs, orderBy)
    }

    fun markTransactionsExported(timestamp: Timestamp, exported: Boolean = true) {
        val values = ContentValues()
        values[COLUMN_EXPORTED] = exported
        val where = "$COLUMN_TEMPLATE = 0 AND $COLUMN_MODIFIED_AT >= ?"
        val whereArgs = arrayOf<String?>(getUtcStringFromTimestamp(timestamp))
        db.update(tableName, values, where, whereArgs)
    }

    fun fetchTransactionsWithSplitsWithTransactionAccount(
        columns: Array<String?>?,
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        // table is :
        // trans_split_acct, trans_extra_info ON trans_extra_info.trans_acct_t_uid = transactions_uid ,
        // accounts AS account1 ON account1.uid = trans_extra_info.trans_acct_a_uid
        //
        // views effectively simplified this query
        //
        // account1 provides information for the grouped account. Splits from the grouped account
        // can be eliminated with a WHERE clause. Transactions in QIF can be auto balanced.
        //
        // Account, transaction and split Information can be retrieve in a single query.
        val table =
            "trans_split_acct, trans_extra_info ON trans_extra_info.trans_acct_t_uid = trans_split_acct." +
                    tableName + "_" + COLUMN_UID + ", " +
                    AccountEntry.TABLE_NAME + " AS account1 ON account1." + AccountEntry.COLUMN_UID +
                    " = trans_extra_info.trans_acct_a_uid"
        return db.query(table, columns, where, whereArgs, null, null, orderBy)
    }

    override val recordsCount: Long
        get() = DatabaseUtils.queryNumEntries(
            db,
            tableName,
            "$COLUMN_TEMPLATE = 0"
        )

    override fun getRecordsCount(where: String?, whereArgs: Array<String?>?): Long {
        val table = (tableName + " t, trans_extra_info ON "
                + "t." + COLUMN_UID
                + " = trans_extra_info.trans_acct_t_uid")
        return DatabaseUtils.queryNumEntries(db, table, where, whereArgs)
    }

    /**
     * Builds a transaction instance with the provided cursor.
     * The cursor should already be pointing to the transaction record in the database
     *
     * @param cursor Cursor pointing to transaction record in database
     * @return [Transaction] object constructed from database record
     */
    override fun buildModelInstance(cursor: Cursor): Transaction {
        val name = cursor.getString(COLUMN_DESCRIPTION)!!
        val time = cursor.getLong(COLUMN_TIMESTAMP)
        val notes = cursor.getString(COLUMN_NOTES)
        val isExported = cursor.getBoolean(COLUMN_EXPORTED)
        val isTemplate = cursor.getBoolean(COLUMN_TEMPLATE)
        val commodityUID = cursor.getString(COLUMN_COMMODITY_UID)!!
        val scheduledActionUID = cursor.getString(COLUMN_SCHEDX_ACTION_UID)
        val number = cursor.getString(COLUMN_NUMBER)
        val commodity = commoditiesDbAdapter.getRecord(commodityUID)

        val transaction = Transaction(name)
        populateBaseModelAttributes(cursor, transaction)
        transaction.time = time
        transaction.notes = notes.orEmpty()
        transaction.isExported = isExported
        transaction.isTemplate = isTemplate
        transaction.commodity = commodity
        transaction.scheduledActionUID = scheduledActionUID
        transaction.number = number.orEmpty()
        try {
            transaction.splits = splitsDbAdapter.getSplitsForTransaction(transaction.uid)
        } catch (e: Exception) {
            Timber.e(e)
        }
        return transaction
    }

    /**
     * Returns the transaction balance for the transaction for the specified account.
     *
     * We consider only those splits which belong to this account
     *
     * @param transactionUID GUID of the transaction
     * @param accountUID     GUID of the account
     * @return [Money] balance of the transaction for that account
     */
    fun getBalance(transactionUID: String, accountUID: String, display: Boolean): Money {
        val splits = splitsDbAdapter.getSplitsForTransactionInAccount(transactionUID, accountUID)
        return computeBalance(accountUID, splits, display)
    }

    /**
     * Assigns transaction with id `rowId` to account with id `accountId`
     *
     * @param transactionUID GUID of the transaction
     * @param srcAccountUID  GUID of the account from which the transaction is to be moved
     * @param dstAccountUID  GUID of the account to which the transaction will be assigned
     * @return Number of transactions splits affected
     */
    fun moveTransaction(
        transactionUID: String,
        srcAccountUID: String,
        dstAccountUID: String?
    ): Int {
        Timber.i(
            ("Moving transaction ID " + transactionUID
                    + " splits from " + srcAccountUID + " to account " + dstAccountUID)
        )

        val splits =
            splitsDbAdapter.getSplitsForTransactionInAccount(transactionUID, srcAccountUID)
        for (split in splits) {
            split.accountUID = dstAccountUID
        }
        splitsDbAdapter.bulkAddRecords(splits, UpdateMethod.Update)
        return splits.size
    }

    /**
     * Returns the number of transactions belonging to an account
     *
     * @param accountUID GUID of the account
     * @return Number of transactions with splits in the account
     */
    fun getTransactionsCount(accountUID: String): Int {
        val cursor = fetchAllTransactionsForAccount(accountUID) ?: return 0
        return try {
            cursor.count
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the number of template transactions in the database
     *
     * @return Number of template transactions
     */
    val templateTransactionsCount: Long
        get() = DatabaseUtils.queryNumEntries(
            db,
            tableName,
            "$COLUMN_TEMPLATE = 1"
        )

    /**
     * Returns a list of all scheduled transactions in the database
     *
     * @return List of all scheduled transactions
     */
    fun getScheduledTransactionsForAccount(accountUID: String): List<Transaction> {
        val cursor = fetchScheduledTransactionsForAccount(accountUID)
        return getRecords(cursor)
    }

    /**
     * Returns a cursor to transactions whose name (UI: description) start with the `prefix`
     *
     * This method is used for autocomplete suggestions when creating new transactions. <br></br>
     * The suggestions are either transactions which have at least one split with `accountUID` or templates.
     *
     * @param prefix     Starting characters of the transaction name
     * @param accountUID GUID of account within which to search for transactions
     * @return Cursor to the data set containing all matching transactions
     */
    fun fetchTransactionSuggestions(prefix: String, accountUID: String): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = tableName + " t" +
                " INNER JOIN " + SplitEntry.TABLE_NAME + " s" +
                " ON t." + COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID
        val projectionIn =
            arrayOf<String?>("t.*", "MAX(t.$COLUMN_TIMESTAMP)")
        val selection = ("s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND t." + COLUMN_TEMPLATE + " = 0"
                + " AND t." + COLUMN_DESCRIPTION + " LIKE " + sqlEscapeLike(prefix))
        val selectionArgs = arrayOf<String?>(accountUID)
        val groupBy = COLUMN_DESCRIPTION
        val sortOrder = "t.$COLUMN_TIMESTAMP DESC"
        val limit = 10.toString()
        return queryBuilder.query(
            db,
            projectionIn,
            selection,
            selectionArgs,
            groupBy,
            null,
            sortOrder,
            limit
        )
    }

    /**
     * Updates a specific entry of an transaction
     *
     * @param contentValues Values with which to update the record
     * @param whereClause   Conditions for updating formatted as SQL where statement
     * @param whereArgs     Arguments for the SQL where statement
     * @return Number of records affected
     */
    fun updateTransaction(
        contentValues: ContentValues,
        whereClause: String?,
        whereArgs: Array<String?>?
    ): Int {
        return db.update(tableName, contentValues, whereClause, whereArgs)
    }

    /**
     * Deletes all transactions except those which are marked as templates.
     *
     * If you want to delete really all transaction records, use [.deleteAllRecords]
     *
     * @return Number of records deleted
     */
    fun deleteAllNonTemplateTransactions(): Int {
        val where = "$COLUMN_TEMPLATE = 0"
        return db.delete(tableName, where, null)
    }

    /**
     * Returns a timestamp of the earliest transaction for a specified account type and currency
     *
     * @param type         the account type
     * @param commodityUID the currency UID
     * @return the earliest transaction's timestamp. Returns `#INVALID_DATE` if no transaction found
     */
    fun getTimestampOfEarliestTransaction(type: AccountType, commodityUID: String): Long {
        return getTimestamp("MIN", type, commodityUID)
    }

    /**
     * Returns a timestamp of the latest transaction for a specified account type and currency
     *
     * @param type         the account type
     * @param commodityUID the currency UID
     * @return the latest transaction's timestamp. Returns `#INVALID_DATE` if no transaction found
     */
    fun getTimestampOfLatestTransaction(type: AccountType, commodityUID: String): Long {
        return getTimestamp("MAX", type, commodityUID)
    }

    val timestampOfFirstModification: Timestamp get() = getTimestampModification("MIN")

    /**
     * Returns the most recent `modified_at` timestamp of non-template transactions in the database
     *
     * @return Last modified time in milliseconds or current time if there is none in the database
     */
    val timestampOfLastModification: Timestamp get() = getTimestampModification("MAX")

    private fun getTimestampModification(mod: String): Timestamp {
        val cursor = db.query(
            tableName,
            arrayOf<String?>("$mod($COLUMN_MODIFIED_AT)"),
            null, null, null, null, null
        )

        var timestamp = timestampFromNow
        try {
            if (cursor.moveToFirst()) {
                val timeString = cursor.getString(0)
                if (!timeString.isNullOrEmpty()) { //in case there were no transactions in the XML file (account structure only)
                    timestamp = getTimestampFromUtcString(timeString)
                }
            }
        } finally {
            cursor.close()
        }
        return timestamp
    }

    /**
     * Returns the earliest or latest timestamp of transactions for a specific account type and currency
     *
     * @param mod          Mode (either MAX or MIN)
     * @param type         AccountType
     * @param commodityUID the currency UID
     * @return earliest or latest timestamp of transactions - `#INVALID_DATE` otherwise.
     * @see .getTimestampOfLatestTransaction
     * @see .getTimestampOfEarliestTransaction
     */
    private fun getTimestamp(mod: String, type: AccountType, commodityUID: String): Long {
        val sql = ("SELECT " + mod + "(t." + COLUMN_TIMESTAMP + ")"
                + " FROM " + tableName + " t"
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON"
                + " s." + SplitEntry.COLUMN_TRANSACTION_UID + " = t." + COLUMN_UID
                + " INNER JOIN " + AccountEntry.TABLE_NAME + " a ON"
                + " a." + AccountEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_ACCOUNT_UID
                + " WHERE a." + AccountEntry.COLUMN_TYPE + " = ?"
                + " AND a." + AccountEntry.COLUMN_COMMODITY_UID + " = ?"
                + " AND t." + COLUMN_TEMPLATE + " = 0")
        val args = arrayOf<String?>(type.name, commodityUID)
        val cursor = db.rawQuery(sql, args)
        var timestamp: Long = INVALID_DATE
        try {
            if (cursor.moveToFirst()) {
                timestamp = cursor.getLong(0)
            }
        } finally {
            cursor.close()
        }
        return timestamp
    }

    fun getTransactionsCountForAccount(accountUID: String): Long {
        val table = tableName + " t " +
                " INNER JOIN " + SplitEntry.TABLE_NAME + " s" +
                " ON t." + COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID
        val selection = "s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
        val selectionArgs = arrayOf<String?>(accountUID)
        return DatabaseUtils.queryNumEntries(db, table, selection, selectionArgs)
    }

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        splitsDbAdapter.close()
        super.close()
    }

    fun fetchSearch(where: String): Cursor? {
        if (where.isEmpty()) {
            val orderBy = COLUMN_TIMESTAMP + " DESC, " +
                    COLUMN_NUMBER + " DESC, " +
                    COLUMN_ID + " DESC"
            return fetchAllRecords(null, null, orderBy)
        }
        val table = tableName + " t" +
                " INNER JOIN " + SplitEntry.TABLE_NAME + " s1" + " ON t." + COLUMN_UID +
                " = s1." + SplitEntry.COLUMN_TRANSACTION_UID +
                " INNER JOIN " + SplitEntry.TABLE_NAME + " s2" + " ON t." + COLUMN_UID +
                " = s2." + SplitEntry.COLUMN_TRANSACTION_UID
        val columns = arrayOf<String?>("t.*")
        val selection = "(s1.${SplitEntry.COLUMN_ID} < s2.${SplitEntry.COLUMN_ID}) AND $where"
        val orderBy = "t." + COLUMN_TIMESTAMP + " DESC, " +
                "t." + COLUMN_NUMBER + " DESC, " +
                "t." + COLUMN_ID + " DESC"
        return db.query(true, table, columns, selection, null, null, null, orderBy, null)
    }

    private fun createTempView() {
        //the multiplication by 1.0 is to cause sqlite to handle the value as REAL and not to round off

        // Create some temporary views. Temporary views only exists in one DB session, and will not
        // be saved in the DB
        //
        // TODO: Useful views should be add to the DB
        //
        // create a temporary view, combining accounts, transactions and splits, as this is often used
        // in the queries

        //todo: would it be useful to add the split reconciled_state and reconciled_date to this view?
        val accountsDbAdapter = AccountsDbAdapter(this, PricesDbAdapter(commoditiesDbAdapter))
        val t = tableName
        val s = splitsDbAdapter.tableName
        val a = accountsDbAdapter.tableName
        val sql = "CREATE TEMP VIEW IF NOT EXISTS trans_split_acct AS SELECT " +
                allColumns.alias(t).joinToString(",") + "," +
                splitsDbAdapter.allColumns.alias(s).joinToString(",") + "," +
                accountsDbAdapter.allColumns.alias(a).joinToString(",") +
                " FROM " + tableName + ", " + s + " ON " +
                t + "." + COLUMN_UID + "=" + s + "." + SplitEntry.COLUMN_TRANSACTION_UID +
                ", " + a + " ON " +
                s + "." + SplitEntry.COLUMN_ACCOUNT_UID + "=" + a + "." + AccountEntry.COLUMN_UID
        db.execSQL(sql)

        val sqlExtra =
            "CREATE TEMP VIEW IF NOT EXISTS trans_extra_info AS SELECT " + t + "_" + COLUMN_UID +
                    " AS trans_acct_t_uid, SUBSTR ( MIN ( ( CASE WHEN IFNULL ( " + s + "_" +
                    SplitEntry.COLUMN_MEMO + ", '' ) == '' THEN 'a' ELSE 'b' END ) || " +
                    a + "_" + AccountEntry.COLUMN_UID +
                    " ), 2 ) AS trans_acct_a_uid, TOTAL ( CASE WHEN " + s + "_" +
                    SplitEntry.COLUMN_TYPE + " = 'DEBIT' THEN " + s + "_" +
                    SplitEntry.COLUMN_VALUE_NUM + " ELSE - " + s + "_" +
                    SplitEntry.COLUMN_VALUE_NUM + " END ) * 1.0 / " + s + "_" +
                    SplitEntry.COLUMN_VALUE_DENOM + " AS trans_acct_balance, COUNT ( DISTINCT " +
                    a + "_" + AccountEntry.COLUMN_COMMODITY_UID +
                    " ) AS trans_currency_count, COUNT (*) AS trans_split_count FROM trans_split_acct " +
                    " GROUP BY " + t + "_" + COLUMN_UID
        db.execSQL(sqlExtra)
    }

    fun getTransactionMaxSplitNum(accountUID: String): Int {
        val cursor = db.query(
            "trans_extra_info",
            arrayOf<String?>("MAX(trans_split_count)"),
            "trans_acct_t_uid IN ( SELECT DISTINCT " + tableName + "_" + COLUMN_UID +
                    " FROM trans_split_acct WHERE " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
                    " = ? )",
            arrayOf<String?>(accountUID),
            null,
            null,
            null
        )
        try {
            return if (cursor.moveToFirst()) {
                cursor.getLong(0).toInt()
            } else {
                0
            }
        } finally {
            cursor.close()
        }
    }

    fun getAllCommoditiesInUse(
        isTemplate: Boolean = false,
        modifiedSince: Timestamp = timestampFromEpochZero
    ): List<Commodity> {
        val result = mutableListOf<Commodity>()
        val table = TABLE_NAME + " t INNER JOIN " + SplitEntry.TABLE_NAME + " s" +
                " ON t." + COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID +
                " INNER JOIN " + AccountEntry.TABLE_NAME + " a" +
                " ON a." + AccountEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_ACCOUNT_UID
        val projection = arrayOf("a." + AccountEntry.COLUMN_COMMODITY_UID)
        val where = "t." + COLUMN_TEMPLATE + " = ?" +
                " AND t." + COLUMN_MODIFIED_AT + " >= ?" +
                " AND a." + AccountEntry.COLUMN_TEMPLATE + " = ?"
        val whereArgs = arrayOf<String?>(
            if (isTemplate) "1" else "0",
            getUtcStringFromTimestamp(modifiedSince),
            if (isTemplate) "1" else "0"
        )
        val cursor = db.query(true, table, projection, where, whereArgs, null, null, null, null)
        cursor.forEach { cursor ->
            val commodityUID = cursor.getString(0)
            val commodity = commoditiesDbAdapter.getRecord(commodityUID)
            if (isTemplate == commodity.isTemplate) {
                result.add(commodity)
            }
        }
        return result
    }

    companion object {
        const val INVALID_DATE: Long = Long.MIN_VALUE

        /**
         * Returns an application-wide instance of the database adapter
         *
         * @return Transaction database adapter
         */
        val instance: TransactionsDbAdapter get() = GnuCashApplication.transactionDbAdapter!!
    }
}
