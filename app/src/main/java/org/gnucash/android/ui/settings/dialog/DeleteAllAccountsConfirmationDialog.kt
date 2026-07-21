/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.settings.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.BackupManager.backupActiveBookAsync

/**
 * Confirmation dialog for deleting all accounts from the system.
 * This class currently only works with HONEYCOMB and above.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class DeleteAllAccountsConfirmationDialog : DoubleConfirmationDialog() {
    private var dbHelper: DatabaseHelper? = null
    private lateinit var accountsDbAdapter: AccountsDbAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = requireContext()
        val bookUID = activeBookUID
        val dbHelper = DatabaseHelper(context, bookUID)
        this.dbHelper = dbHelper
        val holder = dbHelper.holder
        accountsDbAdapter = holder.accountsDbAdapter
    }

    override fun onDestroy() {
        dbHelper?.close()
        super.onDestroy()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity = requireActivity()

        return dialogBuilder
            .setIcon(R.drawable.ic_warning)
            .setTitle(R.string.title_confirm_delete)
            .setMessage(R.string.confirm_delete_all_accounts)
            .setPositiveButton(R.string.alert_dialog_ok_delete) { _, _ ->
                deleteAccounts(activity)
            }
            .create()
    }

    private fun deleteAccounts(activity: Activity) {
        backupActiveBookAsync(activity) {
            accountsDbAdapter.deleteAllRecords()
            snackLong(R.string.toast_all_accounts_deleted)
            WidgetConfigurationActivity.updateAllWidgets(activity)
        }
    }
}
