/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.transaction.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.transaction.TransactionsActivity;

/**
 * Dialog fragment for moving transactions from one account to another
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class BulkMoveDialogFragment extends DialogFragment {

    public static final String TAG = "bulk_move_transactions";

    /**
     * Spinner for selecting the account to move the transactions to
     */
    Spinner mDestinationAccountSpinner;

    /**
     * Dialog positive button. Ok to moving the transactions
     */
    Button mOkButton;

    /**
     * Cancel button
     */
    Button mCancelButton;

    /**
     * Record UIDs of the transactions to be moved
     */
    String[] transactionUIDs = null;

    /**
     * GUID of account from which to move the transactions
     */
    String mOriginAccountUID = null;

    private QualifiedAccountNameAdapter accountNameAdapter;

    /**
     * Create new instance of the bulk move dialog
     *
     * @param transactionIds   Array of transaction database record UIDs
     * @param originAccountUID Account from which to move the transactions
     * @return BulkMoveDialogFragment instance with arguments set
     */
    public static BulkMoveDialogFragment newInstance(String[] transactionIds, String originAccountUID) {
        Bundle args = new Bundle();
        args.putStringArray(UxArgument.SELECTED_TRANSACTION_UIDS, transactionIds);
        args.putString(UxArgument.ORIGIN_ACCOUNT_UID, originAccountUID);
        BulkMoveDialogFragment bulkMoveDialogFragment = new BulkMoveDialogFragment();
        bulkMoveDialogFragment.setArguments(args);
        return bulkMoveDialogFragment;
    }

    /**
     * Creates the view and retrieves references to the dialog elements
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_bulk_move, container, false);

        mDestinationAccountSpinner = (Spinner) v.findViewById(R.id.accounts_list_spinner);
        mOkButton = (Button) v.findViewById(R.id.btn_save);
        mOkButton.setText(R.string.btn_move);

        mCancelButton = (Button) v.findViewById(R.id.btn_cancel);
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.CustomDialog);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getDialog().getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        Bundle args = getArguments();
        transactionUIDs = args.getStringArray(UxArgument.SELECTED_TRANSACTION_UIDS);
        mOriginAccountUID = args.getString(UxArgument.ORIGIN_ACCOUNT_UID);

        String title = getString(R.string.title_move_transactions, transactionUIDs.length);
        getDialog().setTitle(title);

        String where = DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0";
        String[] whereArgs = new String[]{mOriginAccountUID};

        accountNameAdapter = QualifiedAccountNameAdapter.where(requireContext(), where, whereArgs);
        mDestinationAccountSpinner.setAdapter(accountNameAdapter);
        setListeners();
    }

    /**
     * Binds click listeners for the dialog buttons
     */
    protected void setListeners() {
        mCancelButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mOkButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if ((transactionUIDs == null) || transactionUIDs.length == 0) {
                    dismiss();
                }

                int position = mDestinationAccountSpinner.getSelectedItemPosition();
                if (position < 0) {
                    dismiss();
                }
                Account account = accountNameAdapter.getAccount(position);
                if (account == null) {
                    dismiss();
                }
                String dstAccountUID = account.getUID();
                TransactionsDbAdapter trxnAdapter = TransactionsDbAdapter.getInstance();
                if (!trxnAdapter.getAccountCurrencyCode(dstAccountUID).equals(trxnAdapter.getAccountCurrencyCode(mOriginAccountUID))) {
                    Toast.makeText(getActivity(), R.string.toast_incompatible_currency, Toast.LENGTH_LONG).show();
                    return;
                }
                String srcAccountUID = ((TransactionsActivity) getActivity()).getCurrentAccountUID();

                for (String trxnUID : transactionUIDs) {
                    trxnAdapter.moveTransaction(trxnUID, srcAccountUID, dstAccountUID);
                }

                WidgetConfigurationActivity.updateAllWidgets(getActivity());
                Bundle result = new Bundle();
                result.putBoolean(Refreshable.EXTRA_REFRESH, true);
                getParentFragmentManager().setFragmentResult(TAG, result);

                dismiss();
            }
        });
    }
}
