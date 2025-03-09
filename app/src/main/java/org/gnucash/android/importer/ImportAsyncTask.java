/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.importer;

import static org.gnucash.android.util.ContentExtKt.getDocumentName;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.service.ScheduledActionService;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.BookUtils;

import java.io.InputStream;

import timber.log.Timber;

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 */
public class ImportAsyncTask extends AsyncTask<Uri, Object, String> {
    @NonNull
    private final Activity mContext;
    @Nullable
    private final TaskDelegate mDelegate;
    private final boolean mBackup;
    @NonNull
    private final ProgressDialog mProgressDialog;
    @NonNull
    private final GncXmlListener listener;

    public ImportAsyncTask(@NonNull Activity context) {
        this(context, null);
    }

    public ImportAsyncTask(@NonNull Activity context, @Nullable TaskDelegate delegate) {
        this(context, delegate, false);
    }

    public ImportAsyncTask(@NonNull Activity context, @Nullable TaskDelegate delegate, boolean backup) {
        this.mContext = context;
        this.mDelegate = delegate;
        this.mBackup = backup;
        ProgressDialog progressDialog = new GnucashProgressDialog(context);
        progressDialog.setTitle(R.string.title_progress_importing_book);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(dialogInterface -> cancel(true));
        mProgressDialog = progressDialog;
        this.listener = new ProgressListener(context);
    }

    private class ProgressListener implements GncXmlListener {
        private static final long PUBLISH_TIMEOUT = 100;

        private static class PublishItem {
            final Object[] values;
            final long timestamp;

            private PublishItem(Object[] values, long timestamp) {
                this.values = values;
                this.timestamp = timestamp;
            }
        }

        private final String labelAccounts;
        private final String labelBook;
        private final String labelBudgets;
        private final String labelCommodities;
        private final String labelPrices;
        private final String labelSchedules;
        private final String labelTransactions;
        private long countDataCommodityTotal = 0;
        private long countDataCommodity = 0;
        private long countDataAccountTotal = 0;
        private long countDataAccount = 0;
        private long countDataTransactionTotal = 0;
        private long countDataTransaction = 0;
        private long countDataPriceTotal = 0;
        private long countDataPrice = 0;
        @Nullable
        private PublishItem itemPublished = null;

        ProgressListener(Context context) {
            labelAccounts = context.getString(R.string.title_progress_importing_accounts);
            labelBook = context.getString(R.string.title_progress_importing_book);
            labelBudgets = context.getString(R.string.title_progress_importing_budgets);
            labelCommodities = context.getString(R.string.title_progress_importing_commodities);
            labelPrices = context.getString(R.string.title_progress_importing_prices);
            labelSchedules = context.getString(R.string.title_progress_importing_schedules);
            labelTransactions = context.getString(R.string.title_progress_importing_transactions);
        }

        @Override
        public void onImportAccountCount(long count) {
            countDataAccountTotal = count;
        }

        @Override
        public void onImportAccount(@NonNull Account account) {
            Timber.v("%s: %s", labelAccounts, account);
            publishProgressDebounce(labelAccounts, ++countDataAccount, countDataAccountTotal);
        }

        @Override
        public void onImportBookCount(long count) {
        }

        @Override
        public void onImportBook(@NonNull String name) {
            Timber.v("%s: %s", labelBook, name);
            publishProgressDebounce(labelBook);
        }

        @Override
        public void onImportBook(@NonNull Book book) {
            onImportBook(book.getDisplayName());
        }

        @Override
        public void onImportBudgetCount(long count) {
        }

        @Override
        public void onImportBudget(@NonNull Budget budget) {
            Timber.v("%s: %s", labelBudgets, budget);
            publishProgressDebounce(labelBudgets);
        }

        @Override
        public void onImportCommodityCount(long count) {
            countDataCommodityTotal = count;
        }

        @Override
        public void onImportCommodity(@NonNull Commodity commodity) {
            if (commodity.isTemplate()) return;
            Timber.v("%s: %s", labelCommodities, commodity);
            publishProgressDebounce(labelCommodities, ++countDataCommodity, countDataCommodityTotal);
        }

        @Override
        public void onImportPriceCount(long count) {
            countDataPriceTotal = count;
        }

        @Override
        public void onImportPrice(@NonNull Price price) {
            Timber.v("%s: %s", labelPrices, price);
            publishProgressDebounce(labelPrices, ++countDataPrice, countDataPriceTotal);
        }

        @Override
        public void onImportScheduleCount(long count) {
        }

        @Override
        public void onImportSchedule(@NonNull ScheduledAction scheduledAction) {
            Timber.v("%s: %s", labelSchedules, scheduledAction);
            publishProgressDebounce(labelSchedules);
        }

        @Override
        public void onImportTransactionCount(long count) {
            countDataTransactionTotal = count;
        }

        @Override
        public void onImportTransaction(@NonNull Transaction transaction) {
            if (transaction.isTemplate()) return;
            Timber.v("%s: %s", labelTransactions, transaction);
            publishProgressDebounce(labelTransactions, ++countDataTransaction, countDataTransactionTotal);
        }

        private void publishProgressDebounce(final Object... values) {
            final PublishItem item = itemPublished;
            long timestampDelta = (item == null) ? PUBLISH_TIMEOUT : SystemClock.elapsedRealtime() - item.timestamp;
            if (timestampDelta >= PUBLISH_TIMEOUT) {
                // Publish straight away, or if we waited enough time.
                itemPublished = new PublishItem(values, SystemClock.elapsedRealtime());
                publishProgress(values);
            }
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog.show();
    }

    @Override
    protected String doInBackground(Uri... uris) {
        if (mBackup) {
            BackupManager.backupActiveBook();
        }
        if (isCancelled()) {
            return null;
        }

        Uri uri = uris[0];
        Book book;
        String bookUID;
        try {
            String name = getDocumentName(uri, mContext);
            if (TextUtils.isEmpty(name)) {
                name = uri.getLastPathSegment();
            }
            listener.onImportBook(name);
            ContentResolver contentResolver = mContext.getContentResolver();
            InputStream accountInputStream = contentResolver.openInputStream(uri);
            book = GncXmlImporter.parseBook(accountInputStream, listener);
            book.setSourceUri(uri);
            bookUID = book.getUID();
        } catch (final Throwable e) {
            Timber.e(e, "Error importing: %s", uri);
            //TODO delete the partial book at `uri`
            return null;
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uri.toString());

        String displayName = book.getDisplayName();
        String name = getDocumentName(uri, mContext);
        if (!TextUtils.isEmpty(name)) {
            // Remove short file type extension, e.g. ".xml" or ".gnucash" or ".gnca.gz"
            int indexFileType = name.indexOf('.');
            if (indexFileType > 0) {
                name = name.substring(0, indexFileType);
            }
            displayName = name;
            book.setDisplayName(displayName);
        }
        contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName);
        BooksDbAdapter.getInstance().updateRecord(bookUID, contentValues);

        //set the preferences to their default values
        mContext.getSharedPreferences(bookUID, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(mContext.getString(R.string.key_use_double_entry), true)
            .apply();

        return bookUID;
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        final ProgressDialog progressDialog = mProgressDialog;
        if (progressDialog == null) return;

        int length = values.length;
        if (length > 0) {
            String value = (String) values[0];
            mProgressDialog.setTitle(value);
            if (length >= 3) {
                float count = ((Number) values[1]).floatValue();
                float total = ((Number) values[2]).floatValue();
                float progress = (count * 100) / total;
                progressDialog.setIndeterminate(false);
                progressDialog.setProgress((int) progress);
            } else {
                progressDialog.setIndeterminate(true);
            }
        }
    }

    @Override
    protected void onPostExecute(String bookUID) {
        dismissProgressDialog();

        if (!TextUtils.isEmpty(bookUID)) {
            int message = R.string.toast_success_importing_accounts;
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            BookUtils.loadBook(mContext, bookUID);
        } else {
            int message = R.string.toast_error_importing_accounts;
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }

        ScheduledActionService.schedulePeriodic(mContext);

        if (mDelegate != null)
            mDelegate.onTaskComplete();
    }

    private void dismissProgressDialog() {
        final ProgressDialog progressDialog = mProgressDialog;
        try {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (IllegalArgumentException ex) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        }
    }
}
