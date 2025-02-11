/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.transaction;

import static org.gnucash.android.ui.util.widget.ViewExtKt.setTextToEnd;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.FilterQueryProvider;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.FragmentActivity;

import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter;
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentTransactionFormBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment;
import org.gnucash.android.ui.util.RecurrenceParser;
import org.gnucash.android.ui.util.RecurrenceViewClickListener;
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment;
import org.gnucash.android.ui.util.dialog.TimePickerDialogFragment;
import org.gnucash.android.ui.util.widget.CalculatorKeyboard;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import timber.log.Timber;

/**
 * Fragment for creating or editing transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionFormFragment extends MenuFragment implements
    DatePickerDialog.OnDateSetListener,
    TimePickerDialog.OnTimeSetListener,
    RecurrencePickerDialogFragment.OnRecurrenceSetListener, OnTransferFundsListener {

    private static final int REQUEST_SPLIT_EDITOR = 0x11;

    /**
     * Transactions database adapter
     */
    private TransactionsDbAdapter mTransactionsDbAdapter;

    /**
     * Accounts database adapter
     */
    private AccountsDbAdapter mAccountsDbAdapter;

    /**
     * Adapter for transfer account spinner
     */
    private QualifiedAccountNameCursorAdapter mAccountCursorAdapter;

    /**
     * Cursor for transfer account spinner
     */
    private Cursor mCursor;

    /**
     * Transaction to be created/updated
     */
    private Transaction mTransaction;

    /**
     * Formats milliseconds into a date string of the format "dd MMM yyyy" e.g. 18 July 2012
     */
    public final static DateTimeFormatter DATE_FORMATTER = DateTimeFormat.mediumDate();

    /**
     * Formats milliseconds to time string of format "HH:mm" e.g. 15:25
     */
    public final static DateTimeFormatter TIME_FORMATTER = DateTimeFormat.mediumTime();

    /**
     * Flag to note if double entry accounting is in use or not
     */
    private boolean mUseDoubleEntry;

    /**
     * {@link Calendar} for holding the set date
     */
    private Calendar mDate = Calendar.getInstance();

    /**
     * {@link Calendar} object holding the set time
     */
    private Calendar mTime;

    /**
     * The AccountType of the account to which this transaction belongs.
     * Used for determining the accounting rules for credits and debits
     */
    AccountType mAccountType;

    private RecurrenceViewClickListener mRecurrenceViewClickListener;
    private String mRecurrenceRule;
    private EventRecurrence mEventRecurrence = new EventRecurrence();

    private String mAccountUID;

    private List<Split> mSplitsList = new ArrayList<>();

    private boolean mEditMode = false;

    /**
     * Flag which is set if another action is triggered during a transaction save (which interrrupts the save process).
     * Allows the fragment to check and resume the save operation.
     * Primarily used for multi-currency transactions when the currency transfer dialog is opened during save
     */
    private boolean onSaveAttempt = false;

    /**
     * Split value for the current account.
     */
    private Money mSplitValue;
    /**
     * Split quantity for the transfer account.
     */
    private Money mSplitQuantity;

    @Nullable
    private FragmentTransactionFormBinding mBinding;

    private final CommoditiesDbAdapter commoditiesDbAdapter = CommoditiesDbAdapter.getInstance();

    /**
     * Create the view and retrieve references to the UI elements
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentTransactionFormBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        if (mEditMode) {
            actionBar.setTitle(R.string.title_edit_transaction);
        } else {
            actionBar.setTitle(R.string.title_add_transaction);
        }

        FragmentTransactionFormBinding binding = mBinding;
        setListeners(binding);
        //updateTransferAccountsList must only be called after initializing mAccountsDbAdapter
        updateTransferAccountsList(binding);
        initializeViews(binding);

        if (mTransaction == null) {
            initTransactionNameAutocomplete(binding);
        } else {
            initializeViewsWithTransaction(binding, mTransaction);
        }
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private void startTransferFunds(FragmentTransactionFormBinding binding) {
        String fromCurrencyCode = mTransactionsDbAdapter.getAccountCurrencyCode(mAccountUID);

        BigDecimal enteredAmount = binding.inputTransactionAmount.getValue();
        if ((enteredAmount == null) || enteredAmount.equals(BigDecimal.ZERO)) {
            return;
        }
        Commodity fromCommodity = Commodity.getInstance(fromCurrencyCode);
        Money amount = new Money(enteredAmount, fromCommodity).abs();

        //if both accounts have same currency
        Cursor cursor = (Cursor) binding.inputTransferAccountSpinner.getSelectedItem();
        String targetCurrencyCode = cursor.getString(cursor.getColumnIndex(DatabaseSchema.AccountEntry.COLUMN_CURRENCY));
        if (fromCurrencyCode.equals(targetCurrencyCode)) {
            transferComplete(amount, amount);
            return;
        }

        if (amount.equals(mSplitValue)
            && (mSplitQuantity != null)
            && !amount.equals(mSplitQuantity)
        ) {
            transferComplete(amount, mSplitQuantity);
            return;
        }
        mSplitValue = null;
        mSplitQuantity = null;

        TransferFundsDialogFragment fragment
            = TransferFundsDialogFragment.getInstance(amount, targetCurrencyCode, this);
        fragment.show(getParentFragmentManager(), "transfer_funds_editor;" + fromCurrencyCode + ";" + targetCurrencyCode + ";" + amount.toPlainString());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final FragmentTransactionFormBinding binding = mBinding;
        if (binding == null) return;
        ViewGroup parent = binding.getRoot();
        CalculatorKeyboardView keyboardView = binding.calculatorKeyboard.calculatorKeyboard;
        CalculatorKeyboard.rebind(parent, keyboardView, binding.inputTransactionAmount);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        mUseDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();

        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        mAccountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID, mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID());
        assert (mAccountUID != null);
        mAccountType = mAccountsDbAdapter.getAccountType(mAccountUID);

        mEditMode = false;

        String transactionUID = args.getString(UxArgument.SELECTED_TRANSACTION_UID);
        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
        Transaction transaction = null;
        if (!TextUtils.isEmpty(transactionUID)) {
            transaction = mTransactionsDbAdapter.getRecord(transactionUID);
            if (transaction != null) {
                mEditMode = true;
                String scheduledActionUID = args.getString(UxArgument.SCHEDULED_ACTION_UID);
                if (!TextUtils.isEmpty(scheduledActionUID)) {
                    transaction.setScheduledActionUID(scheduledActionUID);
                }
            }
        }
        mTransaction = transaction;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    /**
     * Extension of SimpleCursorAdapter which is used to populate the fields for the list items
     * in the transactions suggestions (auto-complete transaction description).
     */
    private class DropDownCursorAdapter extends SimpleCursorAdapter {

        public DropDownCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to, 0);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);
            String transactionUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_UID));
            Money balance = TransactionsDbAdapter.getInstance().getBalance(transactionUID, mAccountUID);

            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP));
            String dateString = DateUtils.formatDateTime(view.getContext(), timestamp,
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);

            TextView secondaryTextView = (TextView) view.findViewById(R.id.secondary_text);
            secondaryTextView.setText(balance.formattedString() + " on " + dateString); //TODO: Extract string
        }
    }

    /**
     * Initializes the transaction name field for autocompletion with existing transaction names in the database
     */
    private void initTransactionNameAutocomplete(final FragmentTransactionFormBinding binding) {
        final int[] to = new int[]{R.id.primary_text};
        final String[] from = new String[]{DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION};

        Context context = binding.inputTransactionName.getContext();
        SimpleCursorAdapter adapter = new DropDownCursorAdapter(
            context, R.layout.dropdown_item_2lines, null, from, to);

        adapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {
            @Override
            public CharSequence convertToString(Cursor cursor) {
                final int colIndex = cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION);
                return cursor.getString(colIndex);
            }
        });

        adapter.setFilterQueryProvider(new FilterQueryProvider() {
            @Override
            public Cursor runQuery(CharSequence name) {
                return mTransactionsDbAdapter.fetchTransactionSuggestions(name == null ? "" : name.toString(), mAccountUID);
            }
        });

        binding.inputTransactionName.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Transaction transaction = new Transaction(mTransactionsDbAdapter.getRecord(id), true);
                transaction.setTime(System.currentTimeMillis());
                //we check here because next method will modify it and we want to catch user-modification
                boolean amountEntered = binding.inputTransactionAmount.isInputModified();
                initializeViewsWithTransaction(binding, transaction);
                List<Split> splitList = transaction.getSplits();
                boolean isSplitPair = splitList.size() == 2 && splitList.get(0).isPairOf(splitList.get(1));
                if (isSplitPair) {
                    mSplitsList.clear();
                    if (!amountEntered) //if user already entered an amount
                        binding.inputTransactionAmount.setValue(splitList.get(0).getValue().asBigDecimal());
                } else {
                    if (amountEntered) { //if user entered own amount, clear loaded splits and use the user value
                        mSplitsList.clear();
                        setDoubleEntryViewsVisibility(binding, View.VISIBLE);
                    } else {
                        if (mUseDoubleEntry) { //don't hide the view in single entry mode
                            setDoubleEntryViewsVisibility(binding, View.GONE);
                        }
                    }
                }
                mTransaction = null; //we are creating a new transaction after all
            }
        });

        binding.inputTransactionName.setAdapter(adapter);
    }

    /**
     * Initialize views in the fragment with information from a transaction.
     * This method is called if the fragment is used for editing a transaction
     */
    private void initializeViewsWithTransaction(@NonNull final FragmentTransactionFormBinding binding, @NonNull Transaction transaction) {
        setTextToEnd(binding.inputTransactionName, transaction.getDescription());

        binding.inputTransactionType.setAccountType(mAccountType);
        binding.inputTransactionType.setChecked(transaction.getBalance(mAccountUID).isNegative());

        //when autocompleting, only change the amount if the user has not manually changed it already
        binding.inputTransactionAmount.setValue(transaction.getBalance(mAccountUID).asBigDecimal(), !binding.inputTransactionAmount.isInputModified());
        binding.currencySymbol.setText(transaction.getCommodity().getSymbol());
        binding.inputDescription.setText(transaction.getNote());
        binding.inputDate.setText(DATE_FORMATTER.print(transaction.getTimeMillis()));
        binding.inputTime.setText(TIME_FORMATTER.print(transaction.getTimeMillis()));
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(transaction.getTimeMillis());
        mDate = mTime = cal;

        //TODO: deep copy the split list. We need a copy so we can modify with impunity
        mSplitsList = new ArrayList<>(transaction.getSplits());
        toggleAmountInputEntryMode(binding, mSplitsList.size() <= 2);

        mSplitValue = null;
        mSplitQuantity = null;
        if (mSplitsList.size() == 2) {
            for (Split split : mSplitsList) {
                if (split.getAccountUID().equals(mAccountUID)) {
                    mSplitValue = split.getValue();
                } else if (!split.getQuantity().getCommodity().equals(transaction.getCommodity())) {
                    mSplitQuantity = split.getQuantity();
                }
            }
        }
        //if there are more than two splits (which is the default for one entry), then
        //disable editing of the transfer account. User should open editor
        if (mSplitsList.size() == 2 && mSplitsList.get(0).isPairOf(mSplitsList.get(1))) {
            for (Split split : transaction.getSplits()) {
                //two splits, one belongs to this account and the other to another account
                if (mUseDoubleEntry && !split.getAccountUID().equals(mAccountUID)) {
                    setSelectedTransferAccount(binding, mAccountsDbAdapter.getID(split.getAccountUID()));
                }
            }
        } else {
            setDoubleEntryViewsVisibility(binding, View.GONE);
        }

        String currencyCode = mTransactionsDbAdapter.getAccountCurrencyCode(mAccountUID);
        Commodity accountCommodity = Commodity.getInstance(currencyCode);
        binding.currencySymbol.setText(accountCommodity.getSymbol());

        Commodity commodity = Commodity.getInstance(currencyCode);
        binding.inputTransactionAmount.setCommodity(commodity);

        binding.checkboxSaveTemplate.setChecked(transaction.isTemplate());
        String scheduledActionUID = transaction.getScheduledActionUID();
        if (!TextUtils.isEmpty(scheduledActionUID)) {
            Context context = binding.inputRecurrence.getContext();
            ScheduledAction scheduledAction = ScheduledActionDbAdapter.getInstance().getRecord(scheduledActionUID);
            onRecurrenceSet(scheduledAction.getRuleString());
        }
    }

    private void setDoubleEntryViewsVisibility(FragmentTransactionFormBinding binding, int visibility) {
        binding.layoutDoubleEntry.setVisibility(visibility);
        binding.inputTransactionType.setVisibility(visibility);
    }

    private void toggleAmountInputEntryMode(FragmentTransactionFormBinding binding, boolean enabled) {
        if (enabled) {
            binding.inputTransactionAmount.setFocusable(true);
        } else {
            binding.inputTransactionAmount.setFocusable(false);
            binding.inputTransactionAmount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openSplitEditor(binding);
                }
            });
        }
    }

    /**
     * Initialize views with default data for new transactions
     */
    private void initializeViews(final FragmentTransactionFormBinding binding) {
        Context context = binding.inputTransactionType.getContext();

        long now = System.currentTimeMillis();
        binding.inputDate.setText(DATE_FORMATTER.print(now));
        binding.inputTime.setText(TIME_FORMATTER.print(now));
        mTime = mDate = Calendar.getInstance();

        binding.inputTransactionType.setAccountType(mAccountType);
        TransactionType txType = GnuCashApplication.getDefaultTransactionType(context);
        binding.inputTransactionType.setChecked(txType);

        final String code;
        if (mAccountUID != null) {
            code = mTransactionsDbAdapter.getAccountCurrencyCode(mAccountUID);
        } else {
            code = GnuCashApplication.getDefaultCurrencyCode();
        }

        Commodity commodity = Commodity.getInstance(code);
        binding.currencySymbol.setText(commodity.getSymbol());
        binding.inputTransactionAmount.setCommodity(commodity);
        binding.inputTransactionAmount.bindKeyboard(binding.calculatorKeyboard);

        binding.btnSplitEditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSplitEditor(binding);
            }
        });

        if (mUseDoubleEntry) {
            String currentAccountUID = mAccountUID;
            long defaultTransferAccountID;
            String rootAccountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
            do {
                defaultTransferAccountID = mAccountsDbAdapter.getDefaultTransferAccountID(mAccountsDbAdapter.getID(currentAccountUID));
                if (defaultTransferAccountID > 0) {
                    setSelectedTransferAccount(binding, defaultTransferAccountID);
                    break; //we found a parent with default transfer setting
                }
                currentAccountUID = mAccountsDbAdapter.getParentAccountUID(currentAccountUID);
            } while (currentAccountUID != null && !currentAccountUID.equals(rootAccountUID));
        } else {
            binding.layoutDoubleEntry.setVisibility(View.GONE);
            binding.btnSplitEditor.setVisibility(View.GONE);
        }
    }

    /**
     * Updates the list of possible transfer accounts.
     * Only accounts with the same currency can be transferred to
     */
    private void updateTransferAccountsList(FragmentTransactionFormBinding binding) {
        String conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_UID + " != ?"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + ")";

        if (mCursor != null) {
            mCursor.close();
        }
        mCursor = mAccountsDbAdapter.fetchAccountsOrderedByFavoriteAndFullName(conditions, new String[]{mAccountUID, AccountType.ROOT.name()});

        Context context = binding.getRoot().getContext();
        mAccountCursorAdapter = new QualifiedAccountNameCursorAdapter(context, mCursor);
        binding.inputTransferAccountSpinner.setAdapter(mAccountCursorAdapter);
    }

    /**
     * Opens the split editor dialog
     */
    private void openSplitEditor(FragmentTransactionFormBinding binding) {
        BigDecimal enteredAmount = binding.inputTransactionAmount.getValue();
        if (enteredAmount == null) {
            Snackbar.make(binding.getRoot(), R.string.toast_enter_amount_to_split, Snackbar.LENGTH_SHORT).show();
            binding.inputTransactionAmount.requestFocus();
            binding.inputTransactionAmount.setError(getString(R.string.toast_enter_amount_to_split));
            return;
        } else {
            binding.inputTransactionAmount.setError(null);
        }

        String baseAmountString;

        if (mTransaction == null) { //if we are creating a new transaction (not editing an existing one)
            baseAmountString = enteredAmount.toPlainString();
        } else {
            Money biggestAmount = Money.createZeroInstance(mTransaction.getCurrencyCode());
            for (Split split : mTransaction.getSplits()) {
                if (split.getValue().asBigDecimal().compareTo(biggestAmount.asBigDecimal()) > 0)
                    biggestAmount = split.getValue();
            }
            baseAmountString = biggestAmount.toPlainString();
        }

        Context context = binding.getRoot().getContext();
        Intent intent = new Intent(context, FormActivity.class)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.SPLIT_EDITOR.name())
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID)
            .putExtra(UxArgument.AMOUNT_STRING, baseAmountString)
            .putParcelableArrayListExtra(UxArgument.SPLIT_LIST, (ArrayList<Split>) extractSplitsFromView(binding));

        startActivityForResult(intent, REQUEST_SPLIT_EDITOR);
    }

    /**
     * Sets click listeners for the dialog buttons
     */
    private void setListeners(final FragmentTransactionFormBinding binding) {
        binding.btnSplitEditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSplitEditor(binding);
            }
        });

        binding.inputTransactionType.setAmountFormattingListener(binding.inputTransactionAmount, binding.currencySymbol);

        binding.inputDate.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                long dateMillis = mDate.getTimeInMillis();
                DatePickerDialogFragment.newInstance(TransactionFormFragment.this, dateMillis)
                    .show(getParentFragmentManager(), "date_picker_fragment");
            }
        });

        binding.inputTime.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                long timeMillis = mDate.getTimeInMillis();
                TimePickerDialogFragment.newInstance(TransactionFormFragment.this, timeMillis)
                    .show(getParentFragmentManager(), "time_picker_dialog_fragment");
            }
        });

        mRecurrenceViewClickListener = new RecurrenceViewClickListener((AppCompatActivity) requireActivity(), mRecurrenceRule, this);
        binding.inputRecurrence.setOnClickListener(mRecurrenceViewClickListener);

        binding.inputTransferAccountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            /**
             * Flag for ignoring first call to this listener.
             * The first call is during layout, but we want it called only in response to user interaction
             */
            boolean userInteraction = false;

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                removeFavoriteIconFromSelectedView((TextView) view);

                if (mSplitsList.size() == 2) { //when handling simple transfer to one account
                    for (Split split : mSplitsList) {
                        if (!split.getAccountUID().equals(mAccountUID)) {
                            split.setAccountUID(mAccountsDbAdapter.getUID(id));
                        }
                        // else case is handled when saving the transactions
                    }
                }
                if (!userInteraction) {
                    userInteraction = true;
                    return;
                }
                startTransferFunds(binding);
            }

            // Removes the icon from view to avoid visual clutter
            private void removeFavoriteIconFromSelectedView(TextView view) {
                if (view != null) {
                    view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //nothing to see here, move along
            }
        });
    }

    /**
     * Updates the spinner to the selected transfer account
     *
     * @param accountId Database ID of the transfer account
     */
    private void setSelectedTransferAccount(FragmentTransactionFormBinding binding, long accountId) {
        int position = mAccountCursorAdapter.getItemPosition(mAccountsDbAdapter.getUID(accountId));
        if (position >= 0)
            binding.inputTransferAccountSpinner.setSelection(position);
    }

    /**
     * Returns a list of splits based on the input in the transaction form.
     * This only gets the splits from the simple view, and not those from the Split Editor.
     * If the Split Editor has been used and there is more than one split, then it returns {@link #mSplitsList}
     *
     * @return List of splits in the view or {@link #mSplitsList} is there are more than 2 splits in the transaction
     */
    private List<Split> extractSplitsFromView(FragmentTransactionFormBinding binding) {
        if (binding.inputTransactionType.getVisibility() != View.VISIBLE) {
            return mSplitsList;
        }

        BigDecimal enteredAmount = binding.inputTransactionAmount.getValue();
        if (enteredAmount == null) enteredAmount = BigDecimal.ZERO;
        String baseCurrencyCode = mTransactionsDbAdapter.getAccountCurrencyCode(mAccountUID);
        Money value = new Money(enteredAmount, commoditiesDbAdapter.getCommodity(baseCurrencyCode));
        Money quantity = new Money(value);

        String transferAcctUID = getTransferAccountUID(binding);

        if (isMultiCurrencyTransaction(binding)) { //if multi-currency transaction
            String transferCurrencyCode = mAccountsDbAdapter.getCurrencyCode(transferAcctUID);
            String commodityUID = commoditiesDbAdapter.getCommodityUID(baseCurrencyCode);
            String targetCmdtyUID = commoditiesDbAdapter.getCommodityUID(transferCurrencyCode);

            if ((value.equals(mSplitValue)) && mSplitQuantity != null) {
                quantity = mSplitQuantity;
            } else {
                Pair<Long, Long> pricePair = PricesDbAdapter.getInstance()
                    .getPrice(commodityUID, targetCmdtyUID);

                if (pricePair.first > 0 && pricePair.second > 0) {
                    quantity = quantity.times(pricePair.first.intValue())
                        .div(pricePair.second.intValue())
                        .withCurrency(commoditiesDbAdapter.getRecord(targetCmdtyUID));
                }
            }
        }

        Split split1;
        Split split2;
        // Try to preserve the other split attributes.
        if (mSplitsList.size() >= 2) {
            split1 = mSplitsList.get(0);
            split1.setValue(value);
            split1.setQuantity(value);
            split1.setAccountUID(mAccountUID);

            split2 = mSplitsList.get(1);
            split2.setValue(value);
            split2.setQuantity(quantity);
            split2.setAccountUID(transferAcctUID);
        } else {
            split1 = new Split(value, mAccountUID);
            split2 = new Split(value, quantity, transferAcctUID);
        }
        split1.setType(binding.inputTransactionType.getTransactionType());
        split2.setType(binding.inputTransactionType.getTransactionType().invert());

        List<Split> splitList = new ArrayList<>();
        splitList.add(split1);
        splitList.add(split2);

        return splitList;
    }

    /**
     * Returns the GUID of the currently selected transfer account.
     * If double-entry is disabled, this method returns the GUID of the imbalance account for the currently active account
     *
     * @return GUID of transfer account
     */
    @NonNull
    private String getTransferAccountUID(FragmentTransactionFormBinding binding) {
        String transferAcctUID;
        if (mUseDoubleEntry) {
            long transferAcctId = binding.inputTransferAccountSpinner.getSelectedItemId();
            transferAcctUID = mAccountsDbAdapter.getUID(transferAcctId);
        } else {
            Commodity baseCommodity = mAccountsDbAdapter.getSimpleRecord(mAccountUID).getCommodity();
            transferAcctUID = mAccountsDbAdapter.getOrCreateImbalanceAccountUID(baseCommodity);
        }
        return transferAcctUID;
    }

    /**
     * Extracts a transaction from the input in the form fragment
     *
     * @return New transaction object containing all info in the form
     */
    @NonNull
    private Transaction extractTransactionFromView(FragmentTransactionFormBinding binding) {
        Calendar cal = new GregorianCalendar(
            mDate.get(Calendar.YEAR),
            mDate.get(Calendar.MONTH),
            mDate.get(Calendar.DAY_OF_MONTH),
            mTime.get(Calendar.HOUR_OF_DAY),
            mTime.get(Calendar.MINUTE),
            mTime.get(Calendar.SECOND));
        String description = binding.inputTransactionName.getText().toString();
        String notes = binding.inputDescription.getText().toString();
        String currencyCode = mAccountsDbAdapter.getAccountCurrencyCode(mAccountUID);
        Commodity commodity = commoditiesDbAdapter.getCommodity(currencyCode);

        List<Split> splits = extractSplitsFromView(binding);

        Transaction transaction = new Transaction(description);
        transaction.setTime(cal.getTimeInMillis());
        transaction.setCommodity(commodity);
        transaction.setNote(notes);
        transaction.setSplits(splits);
        transaction.setExported(false); //not necessary as exports use timestamps now. Because, legacy

        return transaction;
    }

    /**
     * Checks whether the split editor has been used for editing this transaction.
     * <p>The Split Editor is considered to have been used if the transaction type switch is not visible</p>
     *
     * @return {@code true} if split editor was used, {@code false} otherwise
     */
    private boolean splitEditorUsed(FragmentTransactionFormBinding binding) {
        return binding.inputTransactionType.getVisibility() != View.VISIBLE;
    }

    /**
     * Checks if this is a multi-currency transaction being created/edited
     * <p>A multi-currency transaction is one in which the main account and transfer account have different currencies. <br>
     * Single-entry transactions cannot be multi-currency</p>
     *
     * @return {@code true} if multi-currency transaction, {@code false} otherwise
     */
    private boolean isMultiCurrencyTransaction(FragmentTransactionFormBinding binding) {
        if (!mUseDoubleEntry)
            return false;

        String currencyCode = mAccountsDbAdapter.getAccountCurrencyCode(mAccountUID);
        Commodity accountCommodity = Commodity.getInstance(currencyCode);

        List<Split> splits = mSplitsList;
        for (Split split : splits) {
            Commodity splitCommodity = split.getQuantity().getCommodity();
            if (!accountCommodity.equals(splitCommodity)) {
                return true;
            }
        }

        String transferAcctUID = mAccountsDbAdapter.getUID(binding.inputTransferAccountSpinner.getSelectedItemId());
        String transferCurrencyCode = mAccountsDbAdapter.getCurrencyCode(transferAcctUID);

        return !currencyCode.equals(transferCurrencyCode);
    }

    /**
     * Collects information from the fragment views and uses it to create
     * and save a transaction
     */
    private void saveNewTransaction(FragmentTransactionFormBinding binding) {
        binding.inputTransactionAmount.setError(null);

        //determine whether we need to do currency conversion
        if (isMultiCurrencyTransaction(binding) && !splitEditorUsed(binding) && !onSaveAttempt) {
            onSaveAttempt = true;
            startTransferFunds(binding);
            return;
        }

        boolean isTemplate = binding.checkboxSaveTemplate.isChecked();
        Transaction transactionOld = mTransaction;
        Transaction transaction = extractTransactionFromView(binding);
        String scheduledActionUID = null;

        if (transactionOld != null) { //if editing an existing transaction
            transaction.setUID(transactionOld.getUID());
            transaction.setTemplate(transactionOld.isTemplate());
            scheduledActionUID = transactionOld.getScheduledActionUID();
        }
        boolean wasScheduled = !TextUtils.isEmpty(scheduledActionUID);

        mTransaction = transaction;

        try {
            mAccountsDbAdapter.beginTransaction();

            if (isTemplate) { //template is automatically checked when a transaction is scheduled
                if (mEditMode && wasScheduled) {
                    transaction.setScheduledActionUID(scheduledActionUID);
                    scheduleRecurringTransaction(transaction);
                } else { //means it was new transaction, so a new template
                    Transaction templateTransaction = new Transaction(transaction, true);
                    templateTransaction.setTemplate(true);
                    mTransactionsDbAdapter.addRecord(templateTransaction, DatabaseAdapter.UpdateMethod.insert);
                    scheduleRecurringTransaction(templateTransaction);
                }
            }

            // 1) Transactions may be existing or non-existing
            // 2) when transaction exists in the db, the splits may exist or not exist in the db
            // So replace is chosen.
            mTransactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.replace);

            if (!isTemplate && wasScheduled) { //we were editing a schedule and it was turned off
                ScheduledActionDbAdapter.getInstance().deleteRecord(scheduledActionUID);
            }

            mAccountsDbAdapter.setTransactionSuccessful();

            finish(Activity.RESULT_OK);
        } catch (ArithmeticException ae) {
            Timber.e(ae);
            binding.inputTransactionAmount.setError(getString(R.string.error_invalid_amount));
        } catch (Throwable e) {
            Timber.e(e);
        } finally {
            mAccountsDbAdapter.endTransaction();
        }
    }

    /**
     * Schedules a recurring transaction (if necessary) after the transaction has been saved
     *
     * @see #saveNewTransaction(FragmentTransactionFormBinding)
     */
    private void scheduleRecurringTransaction(@NonNull Transaction transaction) {
        String transactionUID = transaction.getUID();
        ScheduledActionDbAdapter scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();

        Recurrence recurrence = RecurrenceParser.parse(mEventRecurrence);

        ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
        scheduledAction.setRecurrence(recurrence);

        String scheduledActionUID = transaction.getScheduledActionUID();

        if (!TextUtils.isEmpty(scheduledActionUID)) { //if we are editing an existing schedule
            if (recurrence == null) {
                scheduledActionDbAdapter.deleteRecord(scheduledActionUID);
                transaction.setScheduledActionUID(null);
            } else {
                scheduledAction.setUID(scheduledActionUID);
                scheduledActionDbAdapter.updateRecurrenceAttributes(scheduledAction);
                Snackbar.make(getView(), R.string.toast_updated_transaction_recurring_schedule, Snackbar.LENGTH_SHORT).show();
            }
        } else {
            if (recurrence != null) {
                scheduledAction.setActionUID(transactionUID);
                scheduledActionDbAdapter.addRecord(scheduledAction, DatabaseAdapter.UpdateMethod.replace);
                scheduledActionUID = scheduledAction.getUID();
                transaction.setScheduledActionUID(scheduledActionUID);
                Snackbar.make(getView(), R.string.toast_scheduled_recurring_transaction, Snackbar.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mCursor != null) {
            mCursor.close();
        }
        mBinding = null;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.default_save_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        //hide the keyboard if it is visible
        final FragmentTransactionFormBinding binding = mBinding;
        if (binding == null) return super.onOptionsItemSelected(item);
        View view = binding.getRoot();
        Context context = view.getContext();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        switch (item.getItemId()) {
            case android.R.id.home:
                finish(Activity.RESULT_CANCELED);
                return true;

            case R.id.menu_save:
                if (canSave(binding)) {
                    saveNewTransaction(binding);
                } else {
                    if (binding.inputTransactionAmount.getValue() == null) {
                        Snackbar.make(view, R.string.toast_transaction_amount_required, Snackbar.LENGTH_LONG).show();
                        binding.inputTransactionAmount.requestFocus();
                        binding.inputTransactionAmount.setError(getString(R.string.toast_transaction_amount_required));
                    } else {
                        binding.inputTransactionAmount.setError(null);
                    }
                    if (mUseDoubleEntry && binding.inputTransferAccountSpinner.getCount() == 0) {
                        Snackbar.make(view, R.string.toast_disable_double_entry_to_save_transaction, Snackbar.LENGTH_LONG).show();
                    }
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Checks if the pre-requisites for saving the transaction are fulfilled
     * <p>The conditions checked are that a valid amount is entered and that a transfer account is set (where applicable)</p>
     *
     * @return {@code true} if the transaction can be saved, {@code false} otherwise
     */
    private boolean canSave(FragmentTransactionFormBinding binding) {
        return (mUseDoubleEntry && binding.inputTransactionAmount.isInputValid()
            && binding.inputTransferAccountSpinner.getCount() > 0)
            || (!mUseDoubleEntry && binding.inputTransactionAmount.isInputValid());
    }

    /**
     * Called by the split editor fragment to notify of finished editing
     *
     * @param splitList List of splits produced in the fragment
     */
    private void setSplits(FragmentTransactionFormBinding binding, List<Split> splitList) {
        mSplitsList = splitList;
        Money balance = Transaction.computeBalance(mAccountUID, splitList);

        binding.inputTransactionAmount.setValue(balance.asBigDecimal());
        binding.inputTransactionType.setChecked(balance.isNegative());
    }


    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private void finish(int resultCode) {
        final FragmentActivity activity = requireActivity();

        if (resultCode == Activity.RESULT_OK) {
            //update widgets, if any
            WidgetConfigurationActivity.updateAllWidgets(activity);
        }

        if (activity.getSupportFragmentManager().getBackStackEntryCount() == 0) {
            activity.setResult(resultCode);
            //means we got here directly from the accounts list activity, need to finish
            activity.finish();
        } else {
            //go back to transactions list
            activity.getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        mDate.set(Calendar.YEAR, year);
        mDate.set(Calendar.MONTH, month);
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        final FragmentTransactionFormBinding binding = mBinding;
        if (binding == null) return;
        binding.inputDate.setText(DATE_FORMATTER.print(mDate.getTimeInMillis()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        mTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        mTime.set(Calendar.MINUTE, minute);
        final FragmentTransactionFormBinding binding = mBinding;
        if (binding == null) return;
        binding.inputTime.setText(TIME_FORMATTER.print(mTime.getTimeInMillis()));
    }

    /**
     * Strips formatting from a currency string.
     * All non-digit information is removed, but the sign is preserved.
     *
     * @param s String to be stripped
     * @return Stripped string with all non-digits removed
     */
    public static String stripCurrencyFormatting(String s) {
        if (TextUtils.isEmpty(s))
            return s;
        //remove all currency formatting and anything else which is not a number
        String sign = s.trim().substring(0, 1);
        String stripped = s.trim().replaceAll("\\D*", "");
        if (TextUtils.isEmpty(stripped))
            return "";
        if (sign.equals("+") || sign.equals("-")) {
            stripped = sign + stripped;
        }
        return stripped;
    }

    @Override
    public void transferComplete(Money value, Money amount) {
        mSplitValue = value;
        mSplitQuantity = amount;

        //The transfer dialog was called while attempting to save. So try saving again
        if (onSaveAttempt) {
            final FragmentTransactionFormBinding binding = mBinding;
            if (binding == null) return;
            saveNewTransaction(binding);
        }
        onSaveAttempt = false;
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        Timber.i("TX reoccurs: %s", rrule);
        final FragmentTransactionFormBinding binding = mBinding;
        if (binding == null) return;
        Context context = binding.inputRecurrence.getContext();
        String repeatString = null;
        if (!TextUtils.isEmpty(rrule)) {
            try {
                mEventRecurrence.parse(rrule);
                repeatString = EventRecurrenceFormatter.getRepeatString(context, context.getResources(), mEventRecurrence, true);
            } catch (Exception e) {
                Timber.e(e, "Bad recurrence for [%s]", rrule);
                return;
            }

            //when recurrence is set, we will definitely be saving a template
            binding.checkboxSaveTemplate.setChecked(true);
            binding.checkboxSaveTemplate.setEnabled(false);
        } else {
            binding.checkboxSaveTemplate.setEnabled(true);
            binding.checkboxSaveTemplate.setChecked(false);
        }
        if (TextUtils.isEmpty(repeatString)) {
            repeatString = context.getString(R.string.label_tap_to_create_schedule);
        }

        binding.inputRecurrence.setText(repeatString);
        mRecurrenceRule = rrule;
        if (mRecurrenceViewClickListener != null) {
            mRecurrenceViewClickListener.setRecurrence(rrule);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            FragmentTransactionFormBinding binding = mBinding;
            if (binding == null) return;
            List<Split> splits = data.getParcelableArrayListExtra(UxArgument.SPLIT_LIST);
            setSplits(binding, splits);

            //once split editor has been used and saved, only allow editing through it
            toggleAmountInputEntryMode(binding, false);
            setDoubleEntryViewsVisibility(binding, View.GONE);
            binding.btnSplitEditor.setVisibility(View.VISIBLE);
        }
    }
}
