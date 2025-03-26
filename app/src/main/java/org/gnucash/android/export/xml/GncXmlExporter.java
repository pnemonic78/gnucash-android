/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.export.xml;

import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.db.adapter.AccountsDbAdapter.ROOT_ACCOUNT_NAME;
import static org.gnucash.android.export.xml.GncXmlHelper.*;
import static org.gnucash.android.model.Commodity.TEMPLATE;
import static org.gnucash.android.util.ColorExtKt.formatHexRGB;

import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.importer.GncXmlListener;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.model.WeekendAdjust;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import timber.log.Timber;

/**
 * Creates a GnuCash XML representation of the accounts and transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public class GncXmlExporter extends Exporter {

    /**
     * Root account for template accounts
     */
    private Account mRootTemplateAccount;
    private final Map<String, Account> mTransactionToTemplateAccountMap = new TreeMap<>();
    private final RecurrenceDbAdapter mRecurrenceDbAdapter;
    @Nullable
    private final GncXmlListener listener;

    /**
     * Creates an exporter with an already open database instance.
     *
     * @param context The context.
     * @param params  Parameters for the export
     * @param bookUID The book UID.
     */
    public GncXmlExporter(@NonNull Context context,
                          @NonNull ExportParams params,
                          @NonNull String bookUID) {
        this(context, params, bookUID, null);
    }

    /**
     * Creates an exporter with an already open database instance.
     *
     * @param context  The context.
     * @param params   Parameters for the export
     * @param bookUID  The book UID.
     * @param listener the listener to receive events.
     */
    public GncXmlExporter(@NonNull Context context,
                          @NonNull ExportParams params,
                          @NonNull String bookUID,
                          @Nullable GncXmlListener listener) {
        super(context, params, bookUID);
        mRecurrenceDbAdapter = new RecurrenceDbAdapter(mDb);
        this.listener = listener;
    }

    private void writeSlots(XmlSerializer xmlSerializer,
                            List<String> slotKey,
                            List<String> slotType,
                            List<String> slotValue) throws IOException {
        if (slotKey == null || slotType == null || slotValue == null ||
            slotKey.isEmpty() || slotType.size() != slotKey.size() || slotValue.size() != slotKey.size()) {
            return;
        }

        final int length = slotKey.size();
        for (int i = 0; i < length; i++) {
            xmlSerializer.startTag(null, TAG_SLOT);
            xmlSerializer.startTag(null, TAG_SLOT_KEY);
            xmlSerializer.text(slotKey.get(i));
            xmlSerializer.endTag(null, TAG_SLOT_KEY);
            xmlSerializer.startTag(null, TAG_SLOT_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, slotType.get(i));
            xmlSerializer.text(slotValue.get(i));
            xmlSerializer.endTag(null, TAG_SLOT_VALUE);
            xmlSerializer.endTag(null, TAG_SLOT);
        }
    }

    private void writeAccounts(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export accounts");
        // gnucash desktop requires that parent account appears before its descendants.
        // sort by full-name to fulfill the request
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList(null, null, BaseColumns._ID);

        //account count
        long count = accounts.size();
        if (listener != null) listener.onAccountCount(count);
        xmlSerializer.startTag(null, TAG_COUNT_DATA);
        xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, CD_TYPE_ACCOUNT);
        xmlSerializer.text(String.valueOf(count));
        xmlSerializer.endTag(null, TAG_COUNT_DATA);

        for (Account account : accounts) {
            writeAccount(xmlSerializer, account);
        }
    }

    private void writeAccount(XmlSerializer xmlSerializer, Account account) throws IOException {
        if (listener != null) listener.onAccount(account);
        // write account
        xmlSerializer.startTag(null, TAG_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // account name
        xmlSerializer.startTag(null, TAG_ACCT_NAME);
        xmlSerializer.text(account.getName());
        xmlSerializer.endTag(null, TAG_ACCT_NAME);
        // account guid
        xmlSerializer.startTag(null, TAG_ACCT_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(account.getUID());
        xmlSerializer.endTag(null, TAG_ACCT_ID);
        // account type
        xmlSerializer.startTag(null, TAG_ACCT_TYPE);
        AccountType accountType = account.getAccountType();
        xmlSerializer.text(accountType.name());
        xmlSerializer.endTag(null, TAG_ACCT_TYPE);
        // commodity
        Commodity commodity = account.getCommodity();
        xmlSerializer.startTag(null, TAG_ACCT_COMMODITY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_ACCT_COMMODITY);
        xmlSerializer.startTag(null, TAG_ACCT_COMMODITY_SCU);
        xmlSerializer.text(Integer.toString(commodity.getSmallestFraction()));
        xmlSerializer.endTag(null, TAG_ACCT_COMMODITY_SCU);
        // account description
        String description = account.getDescription();
        if (!TextUtils.isEmpty(description)) {
            xmlSerializer.startTag(null, TAG_ACCT_DESCRIPTION);
            xmlSerializer.text(description);
            xmlSerializer.endTag(null, TAG_ACCT_DESCRIPTION);
        }
        // account slots, color, placeholder, default transfer account, favorite
        List<String> slotKey = new ArrayList<>();
        List<String> slotType = new ArrayList<>();
        List<String> slotValue = new ArrayList<>();

        if (account.isPlaceholder()) {
            slotKey.add(KEY_PLACEHOLDER);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add("true");
        }

        int color = account.getColor();
        if (color != Account.DEFAULT_COLOR) {
            slotKey.add(KEY_COLOR);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add(formatHexRGB(color));
        }

        String defaultTransferAcctUID = account.getDefaultTransferAccountUID();
        if (!TextUtils.isEmpty(defaultTransferAcctUID)) {
            slotKey.add(KEY_DEFAULT_TRANSFER_ACCOUNT);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add(defaultTransferAcctUID);
        }

        if (account.isFavorite()) {
            slotKey.add(KEY_FAVORITE);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add("true");
        }

        String notes = account.getNote();
        if (!TextUtils.isEmpty(notes)) {
            slotKey.add(KEY_NOTES);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add(notes);
        }

        if (!slotKey.isEmpty()) {
            xmlSerializer.startTag(null, TAG_ACCT_SLOTS);
            writeSlots(xmlSerializer, slotKey, slotType, slotValue);
            xmlSerializer.endTag(null, TAG_ACCT_SLOTS);
        }

        // parent uid
        String parentUID = account.getParentUID();
        if ((accountType != AccountType.ROOT) && !TextUtils.isEmpty(parentUID)) {
            xmlSerializer.startTag(null, TAG_ACCT_PARENT);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
            xmlSerializer.text(parentUID);
            xmlSerializer.endTag(null, TAG_ACCT_PARENT);
        } else {
            Timber.d("root account : %s", account.getUID());
        }
        xmlSerializer.endTag(null, TAG_ACCOUNT);
    }

    /**
     * Exports template accounts
     * <p>Template accounts are just dummy accounts created for use with template transactions</p>
     *
     * @param xmlSerializer XML serializer
     * @param accounts      List of template accounts
     * @throws IOException if could not write XML to output stream
     */
    private void writeTemplateAccounts(XmlSerializer xmlSerializer, Collection<Account> accounts) throws IOException {
        Commodity commodity = new Commodity(TEMPLATE, TEMPLATE, 1);
        commodity.setNamespace(TEMPLATE);
        commodity.setLocalSymbol(TEMPLATE);
        commodity.setCusip(TEMPLATE);

        for (Account account : accounts) {
            account.setCommodity(commodity);
            writeAccount(xmlSerializer, account);
        }
    }

    /**
     * Serializes transactions from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @param isTemplates   Flag whether to export templates or normal transactions
     * @throws IOException if the XML serializer cannot be written to
     */
    private void writeTransactions(XmlSerializer xmlSerializer, boolean isTemplates) throws IOException {
        Timber.i("export transactions");
        final String where;
        if (isTemplates) {
            where = TransactionEntry.COLUMN_TEMPLATE + "=1";
        } else {
            where = TransactionEntry.COLUMN_TEMPLATE + "=0";
        }
        final Cursor cursor = mTransactionsDbAdapter.fetchAllRecords(where, null, null);
        if (!cursor.moveToFirst()) {
            return;
        }

        //transaction count
        long count = cursor.getCount();
        xmlSerializer.startTag(null, TAG_COUNT_DATA);
        xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, CD_TYPE_TRANSACTION);
        xmlSerializer.text(String.valueOf(count));
        xmlSerializer.endTag(null, TAG_COUNT_DATA);
        if (listener != null) listener.onTransactionCount(count);

        if (isTemplates) {
            mRootTemplateAccount = new Account(ROOT_ACCOUNT_NAME);
            mRootTemplateAccount.setAccountType(AccountType.ROOT);
            mTransactionToTemplateAccountMap.put(" ", mRootTemplateAccount);

            //FIXME: Retrieve the template account GUIDs from the scheduled action table and create accounts with that
            //this will allow use to maintain the template account GUID when we import from the desktop and also use the same for the splits
            do {
                Transaction transaction = mTransactionsDbAdapter.buildModelInstance(cursor);
                Account account = new Account(BaseModel.generateUID());
                account.setAccountType(AccountType.BANK);
                mTransactionToTemplateAccountMap.put(transaction.getUID(), account);
            } while (cursor.moveToNext());
            cursor.moveToFirst();

            writeTemplateAccounts(xmlSerializer, mTransactionToTemplateAccountMap.values());
        }

        do {
            Transaction transaction = mTransactionsDbAdapter.buildModelInstance(cursor);
            writeTransaction(xmlSerializer, transaction, isTemplates);
        } while (cursor.moveToNext());
        cursor.close();
    }

    private void writeTransaction(XmlSerializer xmlSerializer, Transaction transaction, boolean isTemplate) throws IOException {
        if (listener != null) listener.onTransaction(transaction);
        // new transaction
        xmlSerializer.startTag(null, TAG_TRANSACTION);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // transaction id
        xmlSerializer.startTag(null, TAG_TRX_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(transaction.getUID());
        xmlSerializer.endTag(null, TAG_TRX_ID);
        // currency
        Commodity commodity = transaction.getCommodity();
        xmlSerializer.startTag(null, TAG_TRX_CURRENCY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_TRX_CURRENCY);
        // date posted, time which user put on the transaction
        xmlSerializer.startTag(null, TAG_DATE_POSTED);
        xmlSerializer.startTag(null, TAG_TS_DATE);
        xmlSerializer.text(formatDateTime(transaction.getTimeMillis()));
        xmlSerializer.endTag(null, TAG_TS_DATE);
        xmlSerializer.endTag(null, TAG_DATE_POSTED);

        // date entered, time when the transaction was actually created
        xmlSerializer.startTag(null, TAG_DATE_ENTERED);
        xmlSerializer.startTag(null, TAG_TS_DATE);
        xmlSerializer.text(formatDateTime(transaction.getCreatedTimestamp()));
        xmlSerializer.endTag(null, TAG_TS_DATE);
        xmlSerializer.endTag(null, TAG_DATE_ENTERED);

        // description
        xmlSerializer.startTag(null, TAG_TRN_DESCRIPTION);
        xmlSerializer.text(transaction.getDescription());
        xmlSerializer.endTag(null, TAG_TRN_DESCRIPTION);

        // slots
        List<String> slotKey = new ArrayList<>();
        List<String> slotType = new ArrayList<>();
        List<String> slotValue = new ArrayList<>();

        String notes = transaction.getNote();
        if (!TextUtils.isEmpty(notes)) {
            slotKey.add(KEY_NOTES);
            slotType.add(ATTR_VALUE_STRING);
            slotValue.add(notes);
        }

        String scheduledActionUID = transaction.getScheduledActionUID();
        if (!TextUtils.isEmpty(scheduledActionUID)) {
            slotKey.add(KEY_FROM_SCHED_ACTION);
            slotType.add(ATTR_VALUE_GUID);
            slotValue.add(scheduledActionUID);
        }
        xmlSerializer.startTag(null, TAG_TRN_SLOTS);
        writeSlots(xmlSerializer, slotKey, slotType, slotValue);
        xmlSerializer.endTag(null, TAG_TRN_SLOTS);

        writeSplits(xmlSerializer, transaction.getSplits(), isTemplate);

        xmlSerializer.endTag(null, TAG_TRANSACTION);
    }

    private void writeTemplateTransactions(XmlSerializer xmlSerializer) throws IOException {
        if (mTransactionsDbAdapter.getTemplateTransactionsCount() > 0) {
            xmlSerializer.startTag(null, TAG_TEMPLATE_TRANSACTIONS);
            writeTransactions(xmlSerializer, true);
            xmlSerializer.endTag(null, TAG_TEMPLATE_TRANSACTIONS);
        }
    }

    private void writeSplits(XmlSerializer xmlSerializer, Collection<Split> splits, boolean isTemplate) throws IOException {
        // splits start
        xmlSerializer.startTag(null, TAG_TRN_SPLITS);
        for (Split split : splits) {
            writeSplit(xmlSerializer, split, isTemplate);
        }
        xmlSerializer.endTag(null, TAG_TRN_SPLITS);
    }

    private void writeSplit(XmlSerializer xmlSerializer, Split split, boolean isTemplate) throws IOException {
        xmlSerializer.startTag(null, TAG_TRN_SPLIT);
        // split id
        xmlSerializer.startTag(null, TAG_SPLIT_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(split.getUID());
        xmlSerializer.endTag(null, TAG_SPLIT_ID);
        // memo
        String memo = split.getMemo();
        if (!TextUtils.isEmpty(memo)) {
            xmlSerializer.startTag(null, TAG_SPLIT_MEMO);
            xmlSerializer.text(memo);
            xmlSerializer.endTag(null, TAG_SPLIT_MEMO);
        }
        // reconciled
        //todo: if split is reconciled, add reconciled date
        xmlSerializer.startTag(null, TAG_RECONCILED_STATE);
        xmlSerializer.text(split.isReconciled() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_RECONCILED_STATE);
        // value, in the transaction's currency
        Money value = split.getValue();
        long valueNumer = value.getNumerator();
        long valueDenom = value.getDenominator();
        TransactionType txType = split.getType();
        final String splitValueStr;
        if (isTemplate) {
            splitValueStr = "0/100";
        } else { //when doing normal transaction export
            splitValueStr = ((txType == TransactionType.CREDIT) ? "-" : "") + valueNumer + "/" + valueDenom;
        }
        xmlSerializer.startTag(null, TAG_SPLIT_VALUE);
        xmlSerializer.text(splitValueStr);
        xmlSerializer.endTag(null, TAG_SPLIT_VALUE);
        // quantity, in the split account's currency
        final String splitQuantityStr;
        if (isTemplate) {
            splitQuantityStr = "0/100";
        } else {
            Money quantity = split.getQuantity();
            long quantityNumer = quantity.getNumerator();
            long quantityDenom = quantity.getDenominator();
            splitQuantityStr = ((txType == TransactionType.CREDIT) ? "-" : "") + quantityNumer + "/" + quantityDenom;
        }
        xmlSerializer.startTag(null, TAG_SPLIT_QUANTITY);
        xmlSerializer.text(splitQuantityStr);
        xmlSerializer.endTag(null, TAG_SPLIT_QUANTITY);
        // account guid
        xmlSerializer.startTag(null, TAG_SPLIT_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(split.getAccountUID());
        xmlSerializer.endTag(null, TAG_SPLIT_ACCOUNT);

        //if we are exporting a template transaction, then we need to add some extra slots
        // TODO be able to import `KEY_SCHED_XACTION` slots.
        if (isTemplate) {
            xmlSerializer.startTag(null, TAG_SLOT);
            xmlSerializer.startTag(null, TAG_SLOT_KEY);
            xmlSerializer.text(KEY_SCHED_XACTION); //FIXME: not all templates may be scheduled actions
            xmlSerializer.endTag(null, TAG_SLOT_KEY);
            xmlSerializer.startTag(null, TAG_SLOT_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME);

            List<String> slotKeys = new ArrayList<>();
            List<String> slotTypes = new ArrayList<>();
            List<String> slotValues = new ArrayList<>();
            slotKeys.add(KEY_SPLIT_ACCOUNT_SLOT);
            slotTypes.add(ATTR_VALUE_GUID);
            slotValues.add(split.getAccountUID());
            if (txType == TransactionType.CREDIT) {
                slotKeys.add(KEY_CREDIT_FORMULA);
                slotTypes.add(ATTR_VALUE_STRING);
                slotValues.add(formatTemplateSplitAmount(value.toBigDecimal()));

                slotKeys.add(KEY_CREDIT_NUMERIC);
                slotTypes.add(ATTR_VALUE_NUMERIC);
                slotValues.add(valueNumer + "/" + valueDenom);
            } else {
                slotKeys.add(KEY_DEBIT_FORMULA);
                slotTypes.add(ATTR_VALUE_STRING);
                slotValues.add(formatTemplateSplitAmount(value.toBigDecimal()));

                slotKeys.add(KEY_DEBIT_NUMERIC);
                slotTypes.add(ATTR_VALUE_NUMERIC);
                slotValues.add(valueNumer + "/" + valueDenom);
            }

            xmlSerializer.startTag(null, TAG_SPLIT_SLOTS);
            writeSlots(xmlSerializer, slotKeys, slotTypes, slotValues);
            xmlSerializer.endTag(null, TAG_SPLIT_SLOTS);
        }

        xmlSerializer.endTag(null, TAG_TRN_SPLIT);
    }

    /**
     * Serializes {@link ScheduledAction}s from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @throws IOException
     */
    private void writeScheduledTransactions(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export scheduled transactions");
        final String where = ScheduledActionEntry.COLUMN_TYPE + "=?";
        final String[] whereArgs = new String[]{ScheduledAction.ActionType.TRANSACTION.name()};
        List<ScheduledAction> actions = mScheduledActionDbAdapter.getAllRecords(where, whereArgs);
        if (listener != null) listener.onScheduleCount(actions.size());

        for (ScheduledAction scheduledAction : actions) {
            writeScheduledTransaction(xmlSerializer, scheduledAction);
        }
    }

    private void writeScheduledTransaction(XmlSerializer xmlSerializer, ScheduledAction scheduledAction) throws IOException {
        if (listener != null) listener.onSchedule(scheduledAction);
        String actionUID = scheduledAction.getActionUID();
        Account account = mTransactionToTemplateAccountMap.get(actionUID);

        if (account == null) //if the action UID does not belong to a transaction we've seen before, skip it
            return;

        xmlSerializer.startTag(null, TAG_SCHEDULED_ACTION);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(null, TAG_SX_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        //xmlSerializer.text(scheduledAction.getUID());
        xmlSerializer.text(actionUID);
        xmlSerializer.endTag(null, TAG_SX_ID);
        xmlSerializer.startTag(null, TAG_SX_NAME);

        ScheduledAction.ActionType actionType = scheduledAction.getActionType();
        if (actionType == ScheduledAction.ActionType.TRANSACTION) {
            String description = mTransactionsDbAdapter.getAttribute(actionUID, TransactionEntry.COLUMN_DESCRIPTION);
            xmlSerializer.text(description);
        } else {
            xmlSerializer.text(actionType.name());
        }
        xmlSerializer.endTag(null, TAG_SX_NAME);
        xmlSerializer.startTag(null, TAG_SX_ENABLED);
        xmlSerializer.text(scheduledAction.isEnabled() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_SX_ENABLED);
        xmlSerializer.startTag(null, TAG_SX_AUTO_CREATE);
        xmlSerializer.text(scheduledAction.shouldAutoCreate() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_SX_AUTO_CREATE);
        xmlSerializer.startTag(null, TAG_SX_AUTO_CREATE_NOTIFY);
        xmlSerializer.text(scheduledAction.shouldAutoNotify() ? "y" : "n");
        xmlSerializer.endTag(null, TAG_SX_AUTO_CREATE_NOTIFY);
        xmlSerializer.startTag(null, TAG_SX_ADVANCE_CREATE_DAYS);
        xmlSerializer.text(Integer.toString(scheduledAction.getAdvanceCreateDays()));
        xmlSerializer.endTag(null, TAG_SX_ADVANCE_CREATE_DAYS);
        xmlSerializer.startTag(null, TAG_SX_ADVANCE_REMIND_DAYS);
        xmlSerializer.text(Integer.toString(scheduledAction.getAdvanceNotifyDays()));
        xmlSerializer.endTag(null, TAG_SX_ADVANCE_REMIND_DAYS);
        xmlSerializer.startTag(null, TAG_SX_INSTANCE_COUNT);
        String scheduledActionUID = scheduledAction.getUID();
        long instanceCount = mScheduledActionDbAdapter.getActionInstanceCount(scheduledActionUID);
        xmlSerializer.text(Long.toString(instanceCount));
        xmlSerializer.endTag(null, TAG_SX_INSTANCE_COUNT);

        //start date
        long scheduleStartTime = scheduledAction.getCreatedTimestamp().getTime();
        serializeDate(xmlSerializer, TAG_SX_START, scheduleStartTime);

        long lastRunTime = scheduledAction.getLastRunTime();
        if (lastRunTime > 0) {
            serializeDate(xmlSerializer, TAG_SX_LAST, lastRunTime);
        }

        long endTime = scheduledAction.getEndTime();
        if (endTime > 0) {
            //end date
            serializeDate(xmlSerializer, TAG_SX_END, endTime);
        } else { //add number of occurrences
            int totalFrequency = scheduledAction.getTotalPlannedExecutionCount();
            xmlSerializer.startTag(null, TAG_SX_NUM_OCCUR);
            xmlSerializer.text(Integer.toString(totalFrequency));
            xmlSerializer.endTag(null, TAG_SX_NUM_OCCUR);

            //remaining occurrences
            int executionCount = scheduledAction.getExecutionCount();
            xmlSerializer.startTag(null, TAG_SX_REM_OCCUR);
            xmlSerializer.text(Integer.toString(totalFrequency - executionCount));
            xmlSerializer.endTag(null, TAG_SX_REM_OCCUR);
        }

        String tag = scheduledAction.getTag();
        if (!TextUtils.isEmpty(tag)) {
            xmlSerializer.startTag(null, TAG_SX_TAG);
            xmlSerializer.text(tag);
            xmlSerializer.endTag(null, TAG_SX_TAG);
        }

        xmlSerializer.startTag(null, TAG_SX_TEMPL_ACCOUNT);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(account.getUID());
        xmlSerializer.endTag(null, TAG_SX_TEMPL_ACCOUNT);

        xmlSerializer.startTag(null, TAG_SX_SCHEDULE);
        xmlSerializer.startTag(null, TAG_GNC_RECURRENCE);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);
        writeRecurrence(xmlSerializer, scheduledAction.getRecurrence());
        xmlSerializer.endTag(null, TAG_GNC_RECURRENCE);
        xmlSerializer.endTag(null, TAG_SX_SCHEDULE);

        xmlSerializer.endTag(null, TAG_SCHEDULED_ACTION);
    }

    /**
     * Serializes a date as a {@code tag} which has a nested {@link GncXmlHelper#TAG_GDATE} which
     * has the date as a text element formatted {@link GncXmlHelper#formatDate(long)}
     *
     * @param xmlSerializer XML serializer
     * @param tag           Enclosing tag
     * @param timeMillis    Date to be formatted and output
     * @throws IOException
     */
    private void serializeDate(XmlSerializer xmlSerializer, String tag, long timeMillis) throws IOException {
        xmlSerializer.startTag(null, tag);
        xmlSerializer.startTag(null, TAG_GDATE);
        xmlSerializer.text(formatDateTime(timeMillis));
        xmlSerializer.endTag(null, TAG_GDATE);
        xmlSerializer.endTag(null, tag);
    }

    private void writeCommodities(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export commodities");
        List<Commodity> commodities = mAccountsDbAdapter.getCommoditiesInUse();

        //commodity count
        long count = commodities.size();
        if (listener != null) listener.onCommodityCount(count);
        xmlSerializer.startTag(null, TAG_COUNT_DATA);
        xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, CD_TYPE_COMMODITY);
        xmlSerializer.text(String.valueOf(count));
        xmlSerializer.endTag(null, TAG_COUNT_DATA);

        for (Commodity commodity : commodities) {
            writeCommodity(xmlSerializer, commodity);
        }
    }

    private void writeCommodity(XmlSerializer xmlSerializer, Commodity commodity) throws IOException {
        if (listener != null) listener.onCommodity(commodity);
        xmlSerializer.startTag(null, TAG_COMMODITY);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        if (commodity.getFullname() != null) {
            xmlSerializer.startTag(null, TAG_COMMODITY_NAME);
            xmlSerializer.text(commodity.getFullname());
            xmlSerializer.endTag(null, TAG_COMMODITY_NAME);
        }
        xmlSerializer.startTag(null, TAG_COMMODITY_FRACTION);
        xmlSerializer.text(String.valueOf(commodity.getSmallestFraction()));
        xmlSerializer.endTag(null, TAG_COMMODITY_FRACTION);
        if (commodity.getCusip() != null) {
            xmlSerializer.startTag(null, TAG_COMMODITY_XCODE);
            xmlSerializer.text(commodity.getCusip());
            xmlSerializer.endTag(null, TAG_COMMODITY_XCODE);
        }
        if (commodity.getQuoteFlag()) {
            xmlSerializer.startTag(null, TAG_COMMODITY_GET_QUOTES);
            xmlSerializer.endTag(null, TAG_COMMODITY_GET_QUOTES);
            xmlSerializer.startTag(null, TAG_COMMODITY_QUOTE_SOURCE);
            xmlSerializer.text(commodity.getQuoteSource());
            xmlSerializer.endTag(null, TAG_COMMODITY_QUOTE_SOURCE);
            TimeZone tz = commodity.getQuoteTimeZone();
            if (tz != null) {
                xmlSerializer.startTag(null, TAG_COMMODITY_QUOTE_TZ);
                xmlSerializer.text(tz.getID());
                xmlSerializer.endTag(null, TAG_COMMODITY_QUOTE_TZ);
            }
        }
        xmlSerializer.endTag(null, TAG_COMMODITY);
    }

    private void writePrices(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export prices");
        List<Price> prices = mPricesDbAdapter.getAllRecords();
        long count = prices.size();
        if (listener != null) listener.onPriceCount(count);
        if (count == 0) return;
        //price count
        xmlSerializer.startTag(null, TAG_COUNT_DATA);
        xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, CD_TYPE_PRICE);
        xmlSerializer.text(String.valueOf(count));
        xmlSerializer.endTag(null, TAG_COUNT_DATA);

        xmlSerializer.startTag(null, TAG_PRICEDB);
        for (Price price : prices) {
            writePrice(xmlSerializer, price);
        }
        xmlSerializer.endTag(null, TAG_PRICEDB);
    }

    private void writePrice(XmlSerializer xmlSerializer, Price price) throws IOException {
        if (listener != null) listener.onPrice(price);
        xmlSerializer.startTag(null, TAG_PRICE);
        // GUID
        xmlSerializer.startTag(null, TAG_PRICE_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(price.getUID());
        xmlSerializer.endTag(null, TAG_PRICE_ID);
        // commodity
        Commodity commodity = price.getCommodity();
        xmlSerializer.startTag(null, TAG_PRICE_COMMODITY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(commodity.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(commodity.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_PRICE_COMMODITY);
        // currency
        Commodity currency = price.getCurrency();
        xmlSerializer.startTag(null, TAG_PRICE_CURRENCY);
        xmlSerializer.startTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.text(currency.getNamespace());
        xmlSerializer.endTag(null, TAG_COMMODITY_SPACE);
        xmlSerializer.startTag(null, TAG_COMMODITY_ID);
        xmlSerializer.text(currency.getCurrencyCode());
        xmlSerializer.endTag(null, TAG_COMMODITY_ID);
        xmlSerializer.endTag(null, TAG_PRICE_CURRENCY);
        // time
        xmlSerializer.startTag(null, TAG_PRICE_TIME);
        xmlSerializer.startTag(null, TAG_TS_DATE);
        xmlSerializer.text(formatDateTime(price.getDate()));
        xmlSerializer.endTag(null, TAG_TS_DATE);
        xmlSerializer.endTag(null, TAG_PRICE_TIME);
        // source
        if (!TextUtils.isEmpty(price.getSource())) {
            xmlSerializer.startTag(null, TAG_PRICE_SOURCE);
            xmlSerializer.text(price.getSource());
            xmlSerializer.endTag(null, TAG_PRICE_SOURCE);
        }
        // type, optional
        String type = price.getType();
        if (!TextUtils.isEmpty(type)) {
            xmlSerializer.startTag(null, TAG_PRICE_TYPE);
            xmlSerializer.text(type);
            xmlSerializer.endTag(null, TAG_PRICE_TYPE);
        }
        // value
        xmlSerializer.startTag(null, TAG_PRICE_VALUE);
        xmlSerializer.text(price.getValueNum() + "/" + price.getValueDenom());
        xmlSerializer.endTag(null, TAG_PRICE_VALUE);
        xmlSerializer.endTag(null, TAG_PRICE);
    }

    /**
     * Exports the recurrence to GnuCash XML, except the recurrence tags itself i.e. the actual recurrence attributes only
     * <p>This is because there are different recurrence start tags for transactions and budgets.<br>
     * So make sure to write the recurrence start/closing tags before/after calling this method.</p>
     *
     * @param xmlSerializer XML serializer
     * @param recurrence    Recurrence object
     */
    private void writeRecurrence(XmlSerializer xmlSerializer, @Nullable Recurrence recurrence) throws IOException {
        if (recurrence == null) return;
        xmlSerializer.startTag(null, TAG_RX_MULT);
        xmlSerializer.text(String.valueOf(recurrence.getMultiplier()));
        xmlSerializer.endTag(null, TAG_RX_MULT);
        xmlSerializer.startTag(null, TAG_RX_PERIOD_TYPE);
        PeriodType periodType = recurrence.getPeriodType();
        xmlSerializer.text(periodType.value);
        xmlSerializer.endTag(null, TAG_RX_PERIOD_TYPE);

        long recurrenceStartTime = recurrence.getPeriodStart();
        serializeDate(xmlSerializer, TAG_RX_START, recurrenceStartTime);

        WeekendAdjust weekendAdjust = recurrence.getWeekendAdjust();
        if (weekendAdjust != WeekendAdjust.NONE) {
            /* In r17725 and r17751, I introduced this extra XML child
            element, but this means a gnucash-2.2.x cannot read the SX
            recurrence of a >=2.3.x file anymore, which is bad. In order
            to improve this broken backward compatibility for most of the
            cases, we don't write out this XML element as long as it is
            only "none". */
            xmlSerializer.startTag(null, GncXmlHelper.TAG_RX_WEEKEND_ADJ);
            xmlSerializer.text(weekendAdjust.value);
            xmlSerializer.endTag(null, GncXmlHelper.TAG_RX_WEEKEND_ADJ);
        }
    }

    private void writeBudgets(XmlSerializer xmlSerializer) throws IOException {
        Timber.i("export budgets");
        Cursor cursor = mBudgetsDbAdapter.fetchAllRecords();
        if (listener != null) listener.onBudgetCount(cursor.getCount());

        while (cursor.moveToNext()) {
            Budget budget = mBudgetsDbAdapter.buildModelInstance(cursor);
            writeBudget(xmlSerializer, budget);
        }
        cursor.close();
    }

    private void writeBudget(XmlSerializer xmlSerializer, Budget budget) throws IOException {
        if (listener != null) listener.onBudget(budget);
        xmlSerializer.startTag(null, TAG_BUDGET);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // budget id
        xmlSerializer.startTag(null, TAG_BUDGET_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(budget.getUID());
        xmlSerializer.endTag(null, TAG_BUDGET_ID);
        // budget name
        xmlSerializer.startTag(null, TAG_BUDGET_NAME);
        xmlSerializer.text(budget.getName());
        xmlSerializer.endTag(null, TAG_BUDGET_NAME);
        // budget description
        String description = budget.getDescription();
        if (!TextUtils.isEmpty(description)) {
            xmlSerializer.startTag(null, TAG_BUDGET_DESCRIPTION);
            xmlSerializer.text(description);
            xmlSerializer.endTag(null, TAG_BUDGET_DESCRIPTION);
        }
        // budget periods
        xmlSerializer.startTag(null, TAG_BUDGET_NUM_PERIODS);
        xmlSerializer.text(String.valueOf(budget.getNumberOfPeriods()));
        xmlSerializer.endTag(null, TAG_BUDGET_NUM_PERIODS);
        // budget recurrence
        xmlSerializer.startTag(null, TAG_BUDGET_RECURRENCE);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION);
        writeRecurrence(xmlSerializer, budget.getRecurrence());
        xmlSerializer.endTag(null, TAG_BUDGET_RECURRENCE);

        // budget as slots
        List<String> slotKey = new ArrayList<>();
        List<String> slotType = new ArrayList<>();
        List<String> slotValue = new ArrayList<>();
        xmlSerializer.startTag(null, TAG_BUDGET_SLOTS);
        for (BudgetAmount budgetAmount : budget.getExpandedBudgetAmounts()) {
            xmlSerializer.startTag(null, TAG_SLOT);

            // budget amount id
            xmlSerializer.startTag(null, TAG_SLOT_KEY);
            xmlSerializer.text(budgetAmount.getAccountUID());
            xmlSerializer.endTag(null, TAG_SLOT_KEY);

            // budget amounts as slots
            Money amount = budgetAmount.getAmount();
            slotKey.clear();
            slotType.clear();
            slotValue.clear();
            for (int period = 0; period < budget.getNumberOfPeriods(); period++) {
                slotKey.add(String.valueOf(period));
                slotType.add(ATTR_VALUE_NUMERIC);
                slotValue.add(amount.getNumerator() + "/" + amount.getDenominator());
            }
            xmlSerializer.startTag(null, TAG_SLOT_VALUE);
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME);
            writeSlots(xmlSerializer, slotKey, slotType, slotValue);
            xmlSerializer.endTag(null, TAG_SLOT_VALUE);

            xmlSerializer.endTag(null, TAG_SLOT);
        }
        xmlSerializer.endTag(null, TAG_BUDGET_SLOTS);

        xmlSerializer.endTag(null, TAG_BUDGET);
    }

    @Override
    public List<String> generateExport() throws ExporterException {
        OutputStreamWriter writer = null;
        String outputFile = getExportCacheFilePath();
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            writer = new OutputStreamWriter(bufferedOutputStream);

            export(writer);
            close();
        } catch (IOException ex) {
            Timber.e(ex, "Error exporting XML");
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new ExporterException(mExportParams, e);
                }
            }
        }

        List<String> exportedFiles = new ArrayList<>();
        exportedFiles.add(outputFile);

        return exportedFiles;
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param writer Output stream
     * @throws ExporterException
     */
    public void export(@NonNull Writer writer) throws ExporterException {
        Book book = mBooksDbADapter.getActiveBook();
        export(book, writer);
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param bookUID the book UID to export.
     * @param writer Output stream
     * @throws ExporterException
     */
    public void export(@NonNull String bookUID, @NonNull Writer writer) throws ExporterException {
        Book book = mBooksDbADapter.getRecord(bookUID);
        export(book, writer);
    }

    /**
     * Generates an XML export of the database and writes it to the {@code writer} output stream
     *
     * @param book the book to export.
     * @param writer Output stream
     * @throws ExporterException
     */
    public void export(@NonNull Book book, @NonNull Writer writer) throws ExporterException {
        Timber.i("generate export for book %s", book.getUID());
        final long timeStart = SystemClock.elapsedRealtime();
        try {
            String[] namespaces = new String[]{"gnc", "act", "book", "cd", "cmdty", "price", "slot",
                "split", "trn", "ts", "sx", "bgt", "recurrence"};
            XmlSerializer xmlSerializer = XmlPullParserFactory.newInstance().newSerializer();
            try {
                xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            } catch (IllegalStateException e) {
                // Feature not supported. No problem
            }
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument(StandardCharsets.UTF_8.name(), true);
            // root tag
            xmlSerializer.startTag(null, TAG_ROOT);
            for (String ns : namespaces) {
                xmlSerializer.attribute(null, "xmlns:" + ns, "http://www.gnucash.org/XML/" + ns);
            }
            // book count
            if (listener != null) listener.onBookCount(1);
            xmlSerializer.startTag(null, TAG_COUNT_DATA);
            xmlSerializer.attribute(null, ATTR_KEY_CD_TYPE, CD_TYPE_BOOK);
            xmlSerializer.text("1");
            xmlSerializer.endTag(null, TAG_COUNT_DATA);
            writeBook(xmlSerializer, book);
            xmlSerializer.endTag(null, TAG_ROOT);
            xmlSerializer.endDocument();
            xmlSerializer.flush();
        } catch (Exception e) {
            Timber.e(e);
            throw new ExporterException(mExportParams, e);
        }
        final long timeFinish = SystemClock.elapsedRealtime();
        Timber.v("exported in %d ms", timeFinish - timeStart);
    }

    private void writeBook(XmlSerializer xmlSerializer, Book book) throws IOException {
        if (listener != null) listener.onBook(book);

        // book
        xmlSerializer.startTag(null, TAG_BOOK);
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION);
        // book_id
        xmlSerializer.startTag(null, TAG_BOOK_ID);
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID);
        xmlSerializer.text(book.getUID());
        xmlSerializer.endTag(null, TAG_BOOK_ID);

        // export the commodities used in the DB
        writeCommodities(xmlSerializer);
        // prices
        writePrices(xmlSerializer);
        // accounts.
        writeAccounts(xmlSerializer);
        // transactions.
        writeTransactions(xmlSerializer, false);
        //transaction templates
        writeTemplateTransactions(xmlSerializer);
        //scheduled actions
        writeScheduledTransactions(xmlSerializer);
        //budgets
        writeBudgets(xmlSerializer);

        xmlSerializer.endTag(null, TAG_BOOK);
    }

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    @NonNull
    public String getExportMimeType() {
        return "text/xml";
    }

}
