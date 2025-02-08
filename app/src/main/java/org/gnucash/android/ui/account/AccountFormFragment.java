/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.account;

import static org.gnucash.android.model.Account.DEFAULT_COLOR;
import static org.gnucash.android.ui.colorpicker.ColorPickerDialog.COLOR_PICKER_DIALOG_TAG;
import static org.gnucash.android.ui.util.widget.ViewExtKt.setTextToEnd;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentAccountFormBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.adapter.AccountTypesAdapter;
import org.gnucash.android.ui.adapter.CommoditiesAdapter;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.colorpicker.ColorPickerDialog;
import org.gnucash.android.ui.common.UxArgument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import timber.log.Timber;

/**
 * Fragment used for creating and editing accounts
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public class AccountFormFragment extends MenuFragment implements FragmentResultListener {

    /**
     * Accounts database adapter
     */
    private final AccountsDbAdapter mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    private CommoditiesAdapter commoditiesAdapter;
    private AccountTypesAdapter accountTypesAdapter;

    /**
     * GUID of the parent account
     * This value is set to the parent account of the transaction being edited or
     * the account in which a new sub-account is being created
     */
    private String mParentAccountUID = null;

    /**
     * Account UID of the root account
     */
    private String mRootAccountUID = null;

    /**
     * Reference to account object which will be created at end of dialog
     */
    private Account mAccount = null;

    /**
     * List of all descendant accounts.
     */
    private final List<Account> descendantAccounts = new ArrayList<>();

    private QualifiedAccountNameAdapter accountNameAdapter;

    /**
     * Adapter for the parent account spinner
     */
    private QualifiedAccountNameAdapter parentAccountNameAdapter;

    /**
     * Adapter which binds to the spinner for default transfer account
     */
    private QualifiedAccountNameAdapter defaultTransferNameAdapter;

    /**
     * Flag indicating if double entry transactions are enabled
     */
    private boolean mUseDoubleEntry;

    private int mSelectedColor = DEFAULT_COLOR;

    private FragmentAccountFormBinding mBinding;

    /**
     * Default constructor
     * Required, else the app crashes on screen rotation
     */
    public AccountFormFragment() {
        //nothing to see here, move along
    }

    /**
     * Construct a new instance of the dialog
     *
     * @return New instance of the dialog fragment
     */
    static public AccountFormFragment newInstance() {
        return new AccountFormFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();
        commoditiesAdapter = new CommoditiesAdapter(context);
        accountTypesAdapter = new AccountTypesAdapter(context);
        mUseDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();
        mParentAccountUID = getArguments().getString(UxArgument.PARENT_ACCOUNT_UID);
        mRootAccountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
        String accountUID = getArguments().getString(UxArgument.SELECTED_ACCOUNT_UID);
        loadAccounts(context, accountUID);
    }

    private void loadAccounts(Context context, @Nullable String accountUID) {
        accountNameAdapter = new QualifiedAccountNameAdapter(context);

        mAccount = !TextUtils.isEmpty(accountUID) ? mAccountsDbAdapter.getSimpleRecord(accountUID) : null;

        String whereDefault = DatabaseSchema.AccountEntry.COLUMN_UID + " != ?"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?";
        defaultTransferNameAdapter = QualifiedAccountNameAdapter.where(
            context,
            whereDefault,
            new String[]{accountUID != null ? accountUID : "", AccountType.ROOT.name()}
        );

        descendantAccounts.clear();
        descendantAccounts.addAll(accountNameAdapter.getDescendants(accountUID));
    }

    /**
     * Inflates the dialog view and retrieves references to the dialog elements
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentAccountFormBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentAccountFormBinding binding = mBinding;
        assert binding != null;

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        if (mAccount == null) {
            actionBar.setTitle(R.string.title_edit_account);
        } else {
            actionBar.setTitle(R.string.title_create_account);
        }

        binding.inputAccountName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //nothing to see here, move along
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //nothing to see here, move along
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s)) {
                    binding.nameTextInputLayout.setErrorEnabled(false);
                }
            }
        });

        binding.inputAccountTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                AccountType accountType = accountTypesAdapter.getType(position);
                if (mAccount != null) {
                    mAccount.setAccountType(accountType);
                }
                loadParentAccountList(accountType, binding);
                setParentAccountSelection(mParentAccountUID, binding);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //nothing to see here, move along
            }
        });

        binding.inputParentAccount.setEnabled(false);

        binding.checkboxParentAccount.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                binding.inputParentAccount.setEnabled(isChecked);
            }
        });

        binding.inputDefaultTransferAccount.setEnabled(false);
        binding.checkboxDefaultTransferAccount.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                binding.inputDefaultTransferAccount.setEnabled(isChecked);
            }
        });

        binding.inputColorPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showColorPickerDialog();
            }
        });

        binding.inputCurrencySpinner.setAdapter(commoditiesAdapter);
        binding.inputAccountTypeSpinner.setAdapter(accountTypesAdapter);

        //need to load the cursor adapters for the spinners before initializing the views
        loadDefaultTransferAccountList(binding);
        setDefaultTransferAccountInputsVisible(mUseDoubleEntry, binding);

        if (mAccount != null) {
            initializeViewsWithAccount(mAccount, binding);
            //do not immediately open the keyboard when editing an account
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        } else {
            initializeViews(binding);
        }
    }

    /**
     * Initialize view with the properties of <code>account</code>.
     * This is applicable when editing an account
     *
     * @param account Account whose fields are used to populate the form
     */
    private void initializeViewsWithAccount(Account account, @NonNull FragmentAccountFormBinding binding) {
        if (account == null)
            throw new IllegalArgumentException("Account required");

        loadParentAccountList(account.getAccountType(), binding);
        mParentAccountUID = account.getParentUID();
        if (mParentAccountUID == null) {
            // null parent, set Parent as root
            mParentAccountUID = mRootAccountUID;
        }

        setParentAccountSelection(mParentAccountUID, binding);

        String currencyCode = account.getCommodity().getCurrencyCode();
        setSelectedCurrency(currencyCode, binding);

        if (mAccountsDbAdapter.getTransactionMaxSplitNum(account.getUID()) > 1) {
            //TODO: Allow changing the currency and effecting the change for all transactions without any currency exchange (purely cosmetic change)
            binding.inputCurrencySpinner.setEnabled(false);
        }

        setTextToEnd(binding.inputAccountName, account.getName());
        binding.inputAccountDescription.setText(account.getDescription());

        if (mUseDoubleEntry) {
            String defaultTransferAccountUID = account.getDefaultTransferAccountUID();
            if (!TextUtils.isEmpty(defaultTransferAccountUID)) {
                setDefaultTransferAccountSelection(defaultTransferAccountUID, true, binding);
            } else {
                String parentUID = account.getParentUID();
                while (!TextUtils.isEmpty(parentUID)) {
                    Account parentAccount = defaultTransferNameAdapter.getAccount(parentUID);
                    if (parentAccount == null) break;
                    defaultTransferAccountUID = parentAccount.getDefaultTransferAccountUID();
                    if (!TextUtils.isEmpty(defaultTransferAccountUID)) {
                        setDefaultTransferAccountSelection(parentUID, false, binding);
                        break; //we found a parent with default transfer setting
                    }
                    parentUID = parentAccount.getParentUID();
                }
            }
        }

        binding.checkboxPlaceholderAccount.setChecked(account.isPlaceholderAccount());
        mSelectedColor = account.getColor();
        binding.inputColorPicker.setBackgroundColor(account.getColor());

        setAccountTypeSelection(account.getAccountType(), binding);
    }

    /**
     * Initialize views with defaults for new account
     */
    private void initializeViews(@NonNull FragmentAccountFormBinding binding) {
        setSelectedCurrency(Commodity.DEFAULT_COMMODITY.getCurrencyCode(), binding);
        binding.inputColorPicker.setBackgroundColor(Color.LTGRAY);

        if (!TextUtils.isEmpty(mParentAccountUID)) {
            Account parentAccount = accountNameAdapter.getAccount(mParentAccountUID);
            if (parentAccount != null) {
                AccountType parentAccountType = parentAccount.getAccountType();
                setAccountTypeSelection(parentAccountType, binding);
                loadParentAccountList(parentAccountType, binding);
                setParentAccountSelection(mParentAccountUID, binding);
            }
        }
    }

    /**
     * Selects the corresponding account type in the spinner
     *
     * @param accountType AccountType to be set
     */
    private void setAccountTypeSelection(AccountType accountType, @NonNull FragmentAccountFormBinding binding) {
        int accountTypeIndex = accountTypesAdapter.getPosition(accountType);
        binding.inputAccountTypeSpinner.setSelection(accountTypeIndex);
    }

    /**
     * Toggles the visibility of the default transfer account input fields.
     * This field is irrelevant for users who do not use double accounting
     */
    private void setDefaultTransferAccountInputsVisible(boolean visible, @NonNull FragmentAccountFormBinding binding) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        binding.layoutDefaultTransferAccount.setVisibility(visibility);
        binding.labelDefaultTransferAccount.setVisibility(visibility);
    }

    /**
     * Selects the currency with code <code>currencyCode</code> in the spinner
     *
     * @param currencyCode ISO 4217 currency code to be selected
     */
    private void setSelectedCurrency(String currencyCode, @NonNull FragmentAccountFormBinding binding) {
        int position = commoditiesAdapter.getPosition(currencyCode);
        binding.inputCurrencySpinner.setSelection(position);
    }

    /**
     * Selects the account with UID in the parent accounts spinner
     *
     * @param parentAccountUID UID of parent account to be selected
     */
    private void setParentAccountSelection(@Nullable String parentAccountUID, @NonNull FragmentAccountFormBinding binding) {
        if (TextUtils.isEmpty(parentAccountUID) || parentAccountUID.equals(mRootAccountUID)) {
            return;
        }

        int position = parentAccountNameAdapter.getPosition(parentAccountUID);
        if (position >= 0) {
            binding.checkboxParentAccount.setChecked(true);
            binding.inputParentAccount.setEnabled(true);
            binding.inputParentAccount.setSelection(position, true);
        } else {
            binding.checkboxParentAccount.setChecked(false);
            binding.inputParentAccount.setEnabled(false);
        }
    }

    /**
     * Selects the account with UID <code>parentAccountId</code> in the default transfer account spinner
     *
     * @param defaultTransferAccountUID UID of parent account to be selected
     */
    private void setDefaultTransferAccountSelection(
        @Nullable String defaultTransferAccountUID,
        boolean enableTransferAccount,
        @NonNull FragmentAccountFormBinding binding
    ) {
        if (TextUtils.isEmpty(defaultTransferAccountUID)) {
            return;
        }
        binding.checkboxDefaultTransferAccount.setChecked(enableTransferAccount);
        binding.inputDefaultTransferAccount.setEnabled(enableTransferAccount);

        int position = defaultTransferNameAdapter.getPosition(defaultTransferAccountUID);
        binding.inputDefaultTransferAccount.setSelection(position);
    }

    /**
     * Returns an array of colors used for accounts.
     * The array returned has the actual color values and not the resource ID.
     *
     * @return Integer array of colors used for accounts
     */
    private int[] getAccountColorOptions(Context context) {
        Resources res = context.getResources();
        int colorDefault = ResourcesCompat.getColor(res, R.color.title_green, context.getTheme());
        TypedArray colorTypedArray = res.obtainTypedArray(R.array.account_colors);
        int colorLength = colorTypedArray.length();
        int[] colorOptions = new int[colorLength];
        for (int i = 0; i < colorLength; i++) {
            colorOptions[i] = colorTypedArray.getColor(i, colorDefault);
        }
        colorTypedArray.recycle();
        return colorOptions;
    }

    /**
     * Shows the color picker dialog
     */
    private void showColorPickerDialog() {
        FragmentActivity activity = requireActivity();
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        int currentColor = DEFAULT_COLOR;
        if (mAccount != null) {
            currentColor = mAccount.getColor();
        }

        ColorPickerDialog colorPickerDialogFragment = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            getAccountColorOptions(activity),
            currentColor, 4, 12);
        fragmentManager.setFragmentResultListener(COLOR_PICKER_DIALOG_TAG, this, this);
        colorPickerDialogFragment.show(fragmentManager, COLOR_PICKER_DIALOG_TAG);
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (COLOR_PICKER_DIALOG_TAG.equals(requestKey)) {
            int color = result.getInt(ColorPickerDialog.EXTRA_COLOR);
            mBinding.inputColorPicker.setBackgroundColor(color);
            mSelectedColor = color;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.default_save_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                saveAccount(mBinding);
                return true;

            case android.R.id.home:
                finishFragment(mBinding);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Initializes the default transfer account spinner with eligible accounts
     */
    private void loadDefaultTransferAccountList(@NonNull FragmentAccountFormBinding binding) {
        binding.inputDefaultTransferAccount.setAdapter(defaultTransferNameAdapter);
        if (defaultTransferNameAdapter.getCount() <= 0) {
            setDefaultTransferAccountInputsVisible(false, binding);
        }
    }

    /**
     * Loads the list of possible accounts which can be set as a parent account and initializes the spinner.
     * The allowed parent accounts depends on the account type
     *
     * @param accountType AccountType of account whose allowed parent list is to be loaded
     * @param binding     The view binding.
     */
    private void loadParentAccountList(AccountType accountType, @NonNull FragmentAccountFormBinding binding) {
        Context context = binding.getRoot().getContext();

        String where = DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0"
            + " AND " + DatabaseSchema.SplitEntry.COLUMN_TYPE + " IN (" + getAllowedParentAccountTypes(accountType) + ")";

        if (mAccount != null) {  //if editing an account
            String accountUID = mAccount.getUID();
            assert accountUID != null;
            List<String> descendantAccountUIDs = new ArrayList<>();
            descendantAccountUIDs.add(accountUID);
            String rootAccountUID = mRootAccountUID;
            if (!TextUtils.isEmpty(rootAccountUID)) {
                descendantAccountUIDs.add(rootAccountUID);
            }
            for (Account descendant : descendantAccounts) {
                descendantAccountUIDs.add(descendant.getUID());
            }
            // limit cyclic account hierarchies.
            where += " AND (" + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ( '"
                + TextUtils.join("','", descendantAccountUIDs) + "') )";
        }

        parentAccountNameAdapter = QualifiedAccountNameAdapter.where(context, where);
        binding.inputParentAccount.setAdapter(parentAccountNameAdapter);

        if (parentAccountNameAdapter.getCount() <= 0) {
            binding.checkboxParentAccount.setChecked(false); //disable before hiding, else we can still read it when saving
            binding.layoutParentAccount.setVisibility(View.GONE);
            binding.labelParentAccount.setVisibility(View.GONE);
        } else {
            binding.layoutParentAccount.setVisibility(View.VISIBLE);
            binding.labelParentAccount.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Returns a comma separated list of account types which can be parent accounts for the specified <code>type</code>.
     * The strings in the list are the {@link org.gnucash.android.model.AccountType#name()}s of the different types.
     *
     * @param type {@link org.gnucash.android.model.AccountType}
     * @return String comma separated list of account types
     */
    private String getAllowedParentAccountTypes(AccountType type) {

        switch (type) {
            case EQUITY:
                return "'" + AccountType.EQUITY.name() + "'";

            case INCOME:
            case EXPENSE:
                return "'" + AccountType.EXPENSE.name() + "', '" + AccountType.INCOME.name() + "'";

            case CASH:
            case BANK:
            case CREDIT:
            case ASSET:
            case LIABILITY:
            case PAYABLE:
            case RECEIVABLE:
            case CURRENCY:
            case STOCK:
            case MUTUAL: {
                List<String> accountTypeStrings = getAccountTypeStringList();
                accountTypeStrings.remove(AccountType.EQUITY.name());
                accountTypeStrings.remove(AccountType.EXPENSE.name());
                accountTypeStrings.remove(AccountType.INCOME.name());
                accountTypeStrings.remove(AccountType.ROOT.name());
                return "'" + TextUtils.join("','", accountTypeStrings) + "'";
            }

            case TRADING:
                return "'" + AccountType.TRADING.name() + "'";

            case ROOT:
            default:
                return Arrays.toString(AccountType.values()).replaceAll("\\[|]", "");
        }
    }

    /**
     * Returns a list of all the available {@link org.gnucash.android.model.AccountType}s as strings
     *
     * @return String list of all account types
     */
    private List<String> getAccountTypeStringList() {
        String[] accountTypes = Arrays.toString(AccountType.values()).replaceAll("\\[|]", "").split(",");
        List<String> accountTypesList = new ArrayList<>();
        for (String accountType : accountTypes) {
            accountTypesList.add(accountType.trim());
        }

        return accountTypesList;
    }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private void finishFragment(@NonNull FragmentAccountFormBinding binding) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            Timber.w("Activity required");
            return;
        }
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);

        final String action = activity.getIntent().getAction();
        if (action != null && action.equals(Intent.ACTION_INSERT_OR_EDIT)) {
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        } else {
            activity.getSupportFragmentManager().popBackStack();
        }
    }

    /**
     * Reads the fields from the account form and saves as a new account
     */
    private void saveAccount(@NonNull FragmentAccountFormBinding binding) {
        Timber.i("Saving account");

        // accounts to update, in case we're updating full names of a sub account tree
        boolean isNameChanged = false;
        String newName = getEnteredName(binding);
        if (TextUtils.isEmpty(newName)) {
            binding.nameTextInputLayout.setErrorEnabled(true);
            binding.nameTextInputLayout.setError(getString(R.string.toast_no_account_name_entered));
            return;
        } else {
            binding.nameTextInputLayout.setError(null);
        }
        if (mAccount == null) {
            mAccount = new Account(newName);
            int selectedAccountTypeIndex = binding.inputAccountTypeSpinner.getSelectedItemPosition();
            AccountType accountType = accountTypesAdapter.getType(selectedAccountTypeIndex);
            mAccount.setAccountType(accountType);
            mAccountsDbAdapter.addRecord(mAccount, DatabaseAdapter.UpdateMethod.insert); //new account, insert it
        } else {
            isNameChanged = !newName.equals(mAccount.getName());
            mAccount.setName(newName);
        }

        int commodityPosition = binding.inputCurrencySpinner.getSelectedItemPosition();
        Commodity commodity = commoditiesAdapter.getCommodity(commodityPosition);
        mAccount.setCommodity(commodity);

        mAccount.setDescription(binding.inputAccountDescription.getText().toString());
        mAccount.setPlaceHolderFlag(binding.checkboxPlaceholderAccount.isChecked());
        mAccount.setColor(mSelectedColor);

        final String newParentAccountUID;
        if (binding.checkboxParentAccount.isChecked()) {
            int parentAccountIndex = binding.inputParentAccount.getSelectedItemPosition();
            newParentAccountUID = parentAccountNameAdapter.getUID(parentAccountIndex);
        } else {
            //need to do this explicitly in case user removes parent account
            newParentAccountUID = mRootAccountUID;
        }
        mAccount.setParentUID(newParentAccountUID);

        if (binding.checkboxDefaultTransferAccount.isChecked()
            && binding.inputDefaultTransferAccount.getSelectedItemId() != Spinner.INVALID_ROW_ID) {
            int transferIndex = binding.inputDefaultTransferAccount.getSelectedItemPosition();
            String transferUID = defaultTransferNameAdapter.getUID(transferIndex);
            mAccount.setDefaultTransferAccountUID(transferUID);
        } else {
            //explicitly set in case of removal of default account
            mAccount.setDefaultTransferAccountUID(null);
        }

        // update full names
        List<Account> accountsToUpdate = new ArrayList<>();
        accountsToUpdate.add(mAccount);
        // modifying existing account, e.g. name changed and/or parent changed
        boolean isParentChanged = !Objects.equals(mParentAccountUID, newParentAccountUID);
        if (isNameChanged || isParentChanged) {
            accountsToUpdate.addAll(descendantAccounts);
        }

        // bulk update, will not update transactions
        mAccountsDbAdapter.bulkAddRecords(accountsToUpdate, DatabaseAdapter.UpdateMethod.update);

        finishFragment(binding);
    }

    /**
     * Retrieves the name of the account which has been entered in the EditText
     *
     * @return Name of the account which has been entered in the EditText
     */
    private String getEnteredName(@NonNull FragmentAccountFormBinding binding) {
        return binding.inputAccountName.getText().toString().trim();
    }

}
