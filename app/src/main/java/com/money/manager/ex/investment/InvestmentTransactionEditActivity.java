/*
 * Copyright (C) 2012-2018 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.money.manager.ex.investment;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.mikepenz.fontawesome_typeface_library.FontAwesome;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.money.manager.ex.Constants;
import com.money.manager.ex.MmexApplication;
import com.money.manager.ex.R;
import com.money.manager.ex.common.Calculator;
import com.money.manager.ex.common.CategoryListActivity;
import com.money.manager.ex.common.MmxBaseFragmentActivity;
import com.money.manager.ex.core.TransactionTypes;
import com.money.manager.ex.core.TransactionStatuses;
import com.money.manager.ex.core.MenuHelper;
import com.money.manager.ex.core.RequestCodes;
import com.money.manager.ex.core.UIHelper;
import com.money.manager.ex.datalayer.AccountRepository;
import com.money.manager.ex.datalayer.AccountTransactionRepository;
import com.money.manager.ex.datalayer.PayeeRepository;
import com.money.manager.ex.datalayer.Select;
import com.money.manager.ex.datalayer.StockRepository;
import com.money.manager.ex.datalayer.TransactionLinkRepository;
import com.money.manager.ex.domainmodel.Account;
import com.money.manager.ex.domainmodel.AccountTransaction;
import com.money.manager.ex.domainmodel.Payee;
import com.money.manager.ex.domainmodel.Stock;
import com.money.manager.ex.domainmodel.TransactionLink;
import com.money.manager.ex.database.ITransactionEntity;
import com.money.manager.ex.servicelayer.AccountService;
import com.money.manager.ex.utils.MmxDate;
import com.money.manager.ex.utils.MmxDateTimeUtils;
import com.money.manager.ex.view.RobotoTextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import dagger.Lazy;
import info.javaperformance.money.Money;
import info.javaperformance.money.MoneyFactory;

/**
 * Edit investment transaction (stock purchase).
 */
public class InvestmentTransactionEditActivity
    extends MmxBaseFragmentActivity {

    public static final String ARG_ACCOUNT_ID = "InvestmentTransactionEditActivity:AccountId";
    public static final String ARG_STOCK_ID = "InvestmentTransactionEditActivity:StockId";
    public static final String ARG_TRANS_ID = "InvestmentTransactionEditActivity:TransId";

    public static final int REQUEST_NUM_SHARES = 1;
    public static final int REQUEST_PURCHASE_PRICE = 2;
    public static final int REQUEST_COMMISSION = 3;
    public static final int REQUEST_CURRENT_PRICE = 4;

    @Inject Lazy<MmxDateTimeUtils> dateTimeUtilsLazy;

    private Account mAccount;
    private Stock mStock;
    private AccountTransaction mLinkedTransaction;
    private InvestmentTransactionViewHolder mViewHolder;
    private boolean mIsShareTransactionMode;
    private long mCategoryId = Constants.NOT_SET;
    private String mCategoryName = "";

    private final ArrayList<TransactionTypes> mTransactionTypes = new ArrayList<>();
    private final ArrayList<String> mStatusCodes = new ArrayList<>();
    private final ArrayList<Long> mPayeeIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_investment_transaction_edit);

        MmexApplication.getApp().iocComponent.inject(this);

        setDisplayHomeAsUpEnabled(true);

        // load account & currency
        Intent intent = getIntent();
        if (intent != null) {
            long accountId = intent.getLongExtra(ARG_ACCOUNT_ID, Constants.NOT_SET);
            if (accountId != Constants.NOT_SET) {
                AccountRepository repository = new AccountRepository(this);
                mAccount = repository.load(accountId);
            }

            long stockId = intent.getLongExtra(ARG_STOCK_ID, Constants.NOT_SET);
            if (stockId != Constants.NOT_SET) {
                StockRepository repo = new StockRepository(this);
                mStock = repo.load(stockId);
            } else {
                mStock = Stock.create();
                if (mAccount != null) {
                    mStock.setHeldAt(mAccount.getId());
                }
            }

            long transId = intent.getLongExtra(ARG_TRANS_ID, Constants.NOT_SET);
            if (transId != Constants.NOT_SET) {
                mLinkedTransaction = new AccountTransactionRepository(this).load(transId);
            }

            if (mLinkedTransaction == null && mStock != null && mStock.getId() != null) {
                mLinkedTransaction = loadLinkedTransaction(mStock.getId());
            }

            mIsShareTransactionMode = mLinkedTransaction != null;
        }

        initializeForm();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_CANCELED || data == null) return;

        Money amount = Calculator.getAmountFromResult(data);

        switch (requestCode) {
            case REQUEST_NUM_SHARES:
                mStock.setNumberOfShares(amount.toDouble());
                showNumberOfShares();
                showValue();
                break;

            case REQUEST_PURCHASE_PRICE:
                mStock.setPurchasePrice(amount);
                showPurchasePrice();

                if (mStock.getCurrentPrice().isZero()) {
                    mStock.setCurrentPrice(amount);
                    showCurrentPrice();
                    // recalculate value
                    showValue();
                }
                break;

            case REQUEST_COMMISSION:
                mStock.setCommission(amount);
                showCommission();
                break;

            case REQUEST_CURRENT_PRICE:
                mStock.setCurrentPrice(amount);
                showCurrentPrice();
                showValue();
                break;

            case RequestCodes.CATEGORY:
                long categoryId = data.getLongExtra(CategoryListActivity.INTENT_RESULT_CATEGID, Constants.NOT_SET);
                if (categoryId != Constants.NOT_SET) {
                    mCategoryId = categoryId;
                    mCategoryName = data.getStringExtra(CategoryListActivity.INTENT_RESULT_CATEGNAME);
                    displayCategoryName();
                }
                break;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        new MenuHelper(this, menu).addSaveToolbarIcon();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically e clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        long id = item.getItemId();

        if (id == MenuHelper.save) {
            return onActionDoneClick();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onActionDoneClick() {
        if (save()) {
            // set result ok and finish activity
            setResult(RESULT_OK);
            finish();
            return true;
        } else {
            return false;
        }
    }

    public void setDirty(boolean dirty) {
    }

    private void onNumSharesClick() {
        Money amount = MoneyFactory.fromDouble(mStock.getNumberOfShares());

        Calculator.forActivity(this)
                .amount(amount)
                .roundToCurrency(false)
                .show(REQUEST_NUM_SHARES);
    }

    private void onPurchasePriceClick() {
        if (mAccount == null) return;

        Calculator.forActivity(this)
                .roundToCurrency(false)
                .amount(mStock.getPurchasePrice())
                .currency(mAccount.getCurrencyId())
                .show(REQUEST_PURCHASE_PRICE);
    }

    private void onCommissionClick() {
        if (mAccount == null) return;

        Calculator.forActivity(this)
                .amount(mStock.getCommission())
                .currency(mAccount.getCurrencyId())
                .show(REQUEST_COMMISSION);
    }

    private void onCurrentPriceClick() {
        if (mAccount == null) return;
        Calculator.forActivity(this)
                .currency(mAccount.getCurrencyId())
                .amount(mStock.getCurrentPrice())
                .show(REQUEST_CURRENT_PRICE);
    }

    /*
        Private
     */

    private void collectData() {
        String stockName = mViewHolder.stockNameEdit.getText().toString().trim();
        mStock.setName(stockName);

        // Symbols are always uppercase.
        String symbol = mViewHolder.symbolEdit.getText().toString()
            .trim().replace(" ", "").toUpperCase();
        mStock.setSymbol(symbol);

        if (mLinkedTransaction != null) {
            mLinkedTransaction.setNotes(mViewHolder.notesEdit.getText().toString());
            mLinkedTransaction.setTransactionType(mTransactionTypes.get(mViewHolder.transactionTypeSpinner.getSelectedItemPosition()));
            mLinkedTransaction.setStatus(mStatusCodes.get(mViewHolder.statusSpinner.getSelectedItemPosition()));
            mLinkedTransaction.setPayeeId(mPayeeIds.get(mViewHolder.payeeSpinner.getSelectedItemPosition()));
            mLinkedTransaction.setCategoryId(mCategoryId);
        } else {
            mStock.setNotes(mViewHolder.notesEdit.getText().toString());
        }
    }

    private void displayStock(Stock stock, InvestmentTransactionViewHolder viewHolder) {
        if (mAccount == null) return;

        // Date
        String dateDisplay = new MmxDate(stock.getPurchaseDate()).toString(Constants.LONG_DATE_PATTERN);
        viewHolder.dateView.setText(dateDisplay);

        // Account.
        SpinnerAdapter adapter = viewHolder.accountSpinner.getAdapter();
        if (adapter != null) {
            ArrayAdapter<Account> accountAdapter = (ArrayAdapter<Account>) adapter;
            for (int i = 0; i < accountAdapter.getCount(); i++) {
                Account acc = accountAdapter.getItem(i);
                if (acc != null && acc.getId().equals(mAccount.getId())) {
                    viewHolder.accountSpinner.setSelection(i, true);
                    break;
                }
            }
        }

        viewHolder.stockNameEdit.setText(stock.getName());
        viewHolder.symbolEdit.setText(stock.getSymbol());

        showNumberOfShares();
        showPurchasePrice();
        if (mLinkedTransaction != null) {
            viewHolder.notesEdit.setText(mLinkedTransaction.getNotes());
            selectTransactionType(mLinkedTransaction.getTransactionType());
            selectStatus(mLinkedTransaction.getStatus());
            selectId(viewHolder.payeeSpinner, mPayeeIds, mLinkedTransaction.getPayeeId());
            mCategoryId = mLinkedTransaction.getCategoryId() == null ? Constants.NOT_SET : mLinkedTransaction.getCategoryId();
            loadCategoryName(mCategoryId);
        } else {
            viewHolder.notesEdit.setText(stock.getNotes());
        }
        displayCategoryName();
        showCommission();
        showCurrentPrice();
        showValue();
    }

    private void initializeForm() {
        View rootView = this.findViewById(R.id.content);
        mViewHolder = new InvestmentTransactionViewHolder(rootView);

        initDateControl(mViewHolder);
        initAccountSelectors(mViewHolder);

        initTransactionDetailsControls(mViewHolder);
        updateShareTransactionSectionVisibility();

        displayStock(mStock, mViewHolder);

        // Icons
        UIHelper ui = new UIHelper(this);
        mViewHolder.symbolEdit.setCompoundDrawablesWithIntrinsicBounds(ui.getIcon(GoogleMaterial.Icon.gmd_account_balance), null, null, null);
        mViewHolder.notesEdit.setCompoundDrawablesWithIntrinsicBounds(ui.getIcon(GoogleMaterial.Icon.gmd_content_paste), null, null, null);
        mViewHolder.numSharesView.setCompoundDrawablesWithIntrinsicBounds(ui.getIcon(FontAwesome.Icon.faw_hashtag), null, null, null);

        mViewHolder.numSharesView.setOnClickListener(view -> {
            onNumSharesClick();
        });

        mViewHolder.purchasePriceView.setOnClickListener(view -> {
            onPurchasePriceClick();
        });

        mViewHolder.commissionView.setOnClickListener(view -> {
            onCommissionClick();
        });

        mViewHolder.currentPriceView.setOnClickListener(view -> {
            onCurrentPriceClick();
        });
    }

    /**
     * Initialize account selectors.
     */
    private void initAccountSelectors(final InvestmentTransactionViewHolder viewHolder) {
        Context context = this;
        // Account list as the data source to populate the drop-downs.

        AccountService accountService = new AccountService(context);
        accountService.loadInvestmentAccountsToSpinner(viewHolder.accountSpinner, false);

        final Long accountId = mStock.getHeldAt();

        viewHolder.accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Account selected = (Account) parent.getItemAtPosition(position);

                if (!selected.getId().equals(accountId)) {
                    setDirty(true);
                    mStock.setHeldAt(selected.getId());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void initDateControl(final InvestmentTransactionViewHolder viewHolder) {
        // Purchase Date

        viewHolder.dateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(mStock.getPurchaseDate());

                DatePickerDialog.OnDateSetListener listener = (view, year, month, dayOfMonth) -> {
                    setDirty(true);

                    MmxDate dateTime = new MmxDate(year, month, dayOfMonth);
                    viewHolder.dateView.setText(dateTime.toString(Constants.LONG_DATE_PATTERN));
                };

                DatePickerDialog datePicker = new DatePickerDialog(
                        InvestmentTransactionEditActivity.this,
                        listener,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );

                // Customize the DatePickerDialog if needed
                datePicker.show();
            }
        });

        // Icon
        UIHelper ui = new UIHelper(this);
        viewHolder.dateView.setCompoundDrawablesWithIntrinsicBounds(ui.getIcon(FontAwesome.Icon.faw_calendar), null, null, null);

        // prev/next day
        viewHolder.previousDayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MmxDate dateTime = new MmxDate(mStock.getPurchaseDate()).minusDays(1);
                setDate(dateTime.toDate());
            }
        });
        viewHolder.nextDayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MmxDate dateTime = new MmxDate(mStock.getPurchaseDate()).plusDays(1);
                setDate(dateTime.toDate());
            }
        });
    }

    private void showCommission() {
        RobotoTextView view = this.findViewById(R.id.commissionView);
        if (view == null) return;

        // todo: format the number of shares based on selected locale.
        view.setText(mStock.getCommission().toString());
    }

    private void showCurrentPrice() {
        RobotoTextView view = this.findViewById(R.id.currentPriceView);
        // todo: format the number of shares based on selected locale.
        view.setText(mStock.getCurrentPrice().toString());
    }

    private void showNumberOfShares() {
        RobotoTextView view = this.findViewById(R.id.numSharesView);
        if (view == null) return;

        // todo: format the number of shares based on selected locale?

        view.setText(mStock.getNumberOfShares().toString());
    }

    private void showPurchasePrice() {
        RobotoTextView view = this.findViewById(R.id.purchasePriceView);
        // todo: format the number of shares based on selected locale.
        view.setText(mStock.getPurchasePrice().toString());
    }

    private void showValue() {
        RobotoTextView view = this.findViewById(R.id.valueView);
        //mViewHolder.
        view.setText(mStock.getValue().toString());
    }

    private boolean save() {
        collectData();

        if (!validate()) return false;

        // update
        StockRepository repository = new StockRepository(getApplicationContext());
        if (mStock.getId() != null) {
            repository.save(mStock);
        } else {
            repository.add(mStock);
        }

        if (mLinkedTransaction != null && mLinkedTransaction.hasId()) {
            new AccountTransactionRepository(getApplicationContext()).update(mLinkedTransaction);
        }

        return true;
    }

    private AccountTransaction loadLinkedTransaction(long stockId) {
        TransactionLinkRepository linkRepository = new TransactionLinkRepository(this);
        TransactionLink link = linkRepository.first(
            linkRepository.getAllColumns(),
            "LOWER(" + TransactionLink.LINKTYPE + ")=? AND " + TransactionLink.LINKRECORDID + "=?",
            new String[]{"stock", Long.toString(stockId)},
            TransactionLink.TRANSLINKID + " DESC"
        );

        if (link == null || link.getCheckingAccountId() == null) {
            return null;
        }

        return new AccountTransactionRepository(this).load(link.getCheckingAccountId());
    }

    private void initTransactionDetailsControls(InvestmentTransactionViewHolder viewHolder) {
        if (!mIsShareTransactionMode) {
            return;
        }

        initTransactionTypeSelector(viewHolder.transactionTypeSpinner);
        initStatusSelector(viewHolder.statusSpinner);
        initPayeeSelector(viewHolder.payeeSpinner);
        initCategorySelector(viewHolder.categoryTextView);

        boolean hasLinkedTransaction = mLinkedTransaction != null;
        viewHolder.transactionTypeSpinner.setEnabled(hasLinkedTransaction);
        viewHolder.statusSpinner.setEnabled(hasLinkedTransaction);
        viewHolder.payeeSpinner.setEnabled(hasLinkedTransaction);
        viewHolder.categoryTextView.setEnabled(hasLinkedTransaction);
    }

    private void updateShareTransactionSectionVisibility() {
        int visibility = mIsShareTransactionMode ? View.VISIBLE : View.GONE;
        View shareSection = findViewById(R.id.shareTransactionSection);
        View statusSection = findViewById(R.id.shareStatusSection);
        View payeeSection = findViewById(R.id.sharePayeeSection);
        View categorySection = findViewById(R.id.shareCategorySection);

        if (shareSection != null) shareSection.setVisibility(visibility);
        if (statusSection != null) statusSection.setVisibility(visibility);
        if (payeeSection != null) payeeSection.setVisibility(visibility);
        if (categorySection != null) categorySection.setVisibility(visibility);
    }

    private void initTransactionTypeSelector(Spinner spinner) {
        mTransactionTypes.clear();
        mTransactionTypes.add(TransactionTypes.Withdrawal);
        mTransactionTypes.add(TransactionTypes.Deposit);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            new String[]{getString(R.string.buy), getString(R.string.sell)}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void initStatusSelector(Spinner spinner) {
        mStatusCodes.clear();
        mStatusCodes.add(TransactionStatuses.NONE.getCode());
        mStatusCodes.add(TransactionStatuses.RECONCILED.getCode());
        mStatusCodes.add(TransactionStatuses.VOID.getCode());
        mStatusCodes.add(TransactionStatuses.FOLLOWUP.getCode());
        mStatusCodes.add(TransactionStatuses.DUPLICATE.getCode());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            new String[]{
                getString(R.string.status_none),
                getString(R.string.status_reconciled),
                getString(R.string.status_void),
                getString(R.string.status_follow_up),
                getString(R.string.status_duplicate)
            }
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void initPayeeSelector(Spinner spinner) {
        mPayeeIds.clear();
        ArrayList<String> payeeNames = new ArrayList<>();
        mPayeeIds.add(Constants.NOT_SET);
        payeeNames.add(getString(R.string.status_none));

        PayeeRepository repository = new PayeeRepository(this);
        List<Payee> payees = repository.query(new Select(repository.getAllColumns()).orderBy("UPPER(" + Payee.PAYEENAME + ")"));
        for (Payee payee : payees) {
            if (!payee.getActive()) {
                continue;
            }
            mPayeeIds.add(payee.getId());
            payeeNames.add(payee.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, payeeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void initCategorySelector(TextView categoryTextView) {
        categoryTextView.setOnClickListener(v -> {
            if (!mIsShareTransactionMode || mLinkedTransaction == null) {
                return;
            }

            Intent intent = new Intent(this, CategoryListActivity.class);
            intent.setAction(Intent.ACTION_PICK);
            startActivityForResult(intent, RequestCodes.CATEGORY);
        });
    }

    private void loadCategoryName(long categoryId) {
        if (categoryId == Constants.NOT_SET) {
            mCategoryName = "";
            return;
        }

        com.money.manager.ex.datalayer.CategoryRepository repository = new com.money.manager.ex.datalayer.CategoryRepository(this);
        com.money.manager.ex.domainmodel.Category category = repository.load(categoryId);
        mCategoryName = category != null ? category.getName() : "";
    }

    private void displayCategoryName() {
        if (mViewHolder == null || mViewHolder.categoryTextView == null) {
            return;
        }

        if (TextUtils.isEmpty(mCategoryName)) {
            mViewHolder.categoryTextView.setText(getString(R.string.status_none));
        } else {
            mViewHolder.categoryTextView.setText(mCategoryName);
        }
    }

    private void selectTransactionType(TransactionTypes transactionType) {
        if (transactionType == null) {
            mViewHolder.transactionTypeSpinner.setSelection(0);
            return;
        }

        for (int i = 0; i < mTransactionTypes.size(); i++) {
            if (mTransactionTypes.get(i) == transactionType) {
                mViewHolder.transactionTypeSpinner.setSelection(i);
                return;
            }
        }
    }

    private void selectStatus(String statusCode) {
        if (statusCode == null) {
            mViewHolder.statusSpinner.setSelection(0);
            return;
        }

        for (int i = 0; i < mStatusCodes.size(); i++) {
            if (mStatusCodes.get(i).equals(statusCode)) {
                mViewHolder.statusSpinner.setSelection(i);
                return;
            }
        }
    }

    private void setDate(Date dateTime) {
        setDirty(true);

        mStock.setPurchaseDate(dateTime);

        showDate(dateTime);
    }

    private void showDate(Date date) {
        String display = new MmxDate(date).toString(Constants.LONG_DATE_PATTERN);
        mViewHolder.dateView.setText(display);
    }

    private void selectId(Spinner spinner, ArrayList<Long> ids, Long id) {
        if (id == null) return;
        for (int i = 0; i < ids.size(); i++) {
            if (id.equals(ids.get(i))) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private boolean validate() {
        // symbol must not be empty.
        if (TextUtils.isEmpty(mStock.getSymbol())) {
            new UIHelper(this).showToast(getString(R.string.symbol_required));
            return false;
        }

        // number of shares, price?

        return true;
    }
}
