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
import android.content.ContentValues;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.mikepenz.iconics.view.IconicsImageView;
import com.money.manager.ex.Constants;
import com.money.manager.ex.MmexApplication;
import com.money.manager.ex.R;
import com.money.manager.ex.common.Calculator;
import com.money.manager.ex.common.CalculatorActivity;
import com.money.manager.ex.common.MmxBaseFragmentActivity;
import com.money.manager.ex.core.MenuHelper;
import com.money.manager.ex.core.RequestCodes;
import com.money.manager.ex.datalayer.StockHistoryRepository;
import com.money.manager.ex.datalayer.StockRepository;
import com.money.manager.ex.domainmodel.StockHistory;
import com.money.manager.ex.investment.yahoofinance.StockPriceRepository;
import com.money.manager.ex.utils.MmxDate;
import com.money.manager.ex.utils.MmxDateTimeUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.Lazy;
import info.javaperformance.money.Money;
import info.javaperformance.money.MoneyFactory;
import timber.log.Timber;

public class PriceEditActivity
    extends MmxBaseFragmentActivity {

    public static final String ARG_CURRENCY_ID = "PriceEditActivity:CurrencyId";

    @Inject Lazy<MmxDateTimeUtils> dateTimeUtilsLazy;

    protected PriceEditModel model;
    private EditPriceViewHolder viewHolder;
    private StockHistoryRepository historyRepository;
    private StockHistoryAdapter historyAdapter;
    private RecyclerView priceHistoryRecyclerView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Money mStockCurrentPrice;
    private String mInitialDateIso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_edit);

        MmexApplication.getApp().iocComponent.inject(this);

        initializeToolbar();

        if (savedInstanceState != null) {
            // TODO
        } else {
            initializeModel();
        }

        viewHolder = new EditPriceViewHolder();
        viewHolder.bind(this);
        viewHolder.amountTextView.setOnClickListener(view -> onPriceClick());
        viewHolder.addButton.setOnClickListener(view -> onAddClick());
        viewHolder.dateTextView.setOnClickListener(view -> onDateClick());
        viewHolder.previousDayButton.setOnClickListener(view -> onPreviousDayClick());
        viewHolder.nextDayButton.setOnClickListener(view -> onNextDayClick());

        historyRepository = new StockHistoryRepository(this);

        setupHistoryRecyclerView();

        IconicsImageView downloadButton = findViewById(R.id.downloadPricesButton);
        downloadButton.setOnClickListener(v -> onDownloadPricesClick());

        model.display(this, viewHolder);

        loadHistoricalPriceForCurrentDate();
        loadHistory();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((resultCode == Activity.RESULT_CANCELED) || data == null) return;

        if (requestCode == RequestCodes.AMOUNT) {
            String stringExtra = data.getStringExtra(CalculatorActivity.RESULT_AMOUNT);
            model.price = MoneyFactory.fromString(stringExtra);
            model.display(this, viewHolder);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuHelper menuHelper = new MenuHelper(this, menu);
        menuHelper.addSaveToolbarIcon();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Timber.d("going back");
                break;
            case MenuHelper.save:
                if (save()) {
                    setResult(Activity.RESULT_OK);
                    finish();
                    return onActionDoneClick();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // TODO
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void onPriceClick() {
        Calculator.forActivity(this)
            .amount(model.price)
            .roundToCurrency(false)
            .show(RequestCodes.AMOUNT);
    }

    private void onDateClick() {
        MmxDate priceDate = model.date;

        DatePickerDialog.OnDateSetListener listener = (view, year, month, dayOfMonth) -> {
            model.date = new MmxDate(year, month, dayOfMonth);
            model.display(PriceEditActivity.this, viewHolder);
            loadHistoricalPriceForCurrentDate();
            scrollToCurrentDate();
        };

        DatePickerDialog datePicker = new DatePickerDialog(
                PriceEditActivity.this,
                listener,
                priceDate.getYear(),
                priceDate.getMonthOfYear(),
                priceDate.getDayOfMonth()
        );

        datePicker.show();
    }

    private void onPreviousDayClick() {
        model.date = model.date.minusDays(1);
        model.display(this, viewHolder);
        loadHistoricalPriceForCurrentDate();
        scrollToCurrentDate();
    }

    private void onNextDayClick() {
        model.date = model.date.plusDays(1);
        model.display(this, viewHolder);
        loadHistoricalPriceForCurrentDate();
        scrollToCurrentDate();
    }

    private void initializeModel() {
        model = new PriceEditModel();
        readParameters();
    }

    private void initializeToolbar() {
        CollapsingToolbarLayout collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar);
        collapsingToolbarLayout.setTitle(getString(R.string.edit_price));
        setDisplayHomeAsUpEnabled(true);
    }

    private void readParameters() {
        Intent intent = getIntent();
        if (intent == null) return;

        model.accountId = intent.getLongExtra(EditPriceDialog.ARG_ACCOUNT, Constants.NOT_SET);
        model.symbol = intent.getStringExtra(EditPriceDialog.ARG_SYMBOL);

        String priceString = intent.getStringExtra(EditPriceDialog.ARG_PRICE);
        model.price = MoneyFactory.fromString(priceString);
        mStockCurrentPrice = model.price;

        String dateString = intent.getStringExtra(EditPriceDialog.ARG_DATE);
        model.date = new MmxDate(dateString);
        mInitialDateIso = model.date.toIsoDateString();

        model.currencyId = intent.getLongExtra(ARG_CURRENCY_ID, Constants.NOT_SET);
    }

    private void setupHistoryRecyclerView() {
        priceHistoryRecyclerView = findViewById(R.id.priceHistoryRecyclerView);
        historyAdapter = new StockHistoryAdapter(this, (date, price) -> {
            model.date = new MmxDate(date);
            model.price = price;
            model.display(this, viewHolder);
        }, (item, position) -> {
            // confirm deletion then delete in background; if the row is synthetic (no DB entry) remove from UI
            new AlertDialog.Builder(PriceEditActivity.this)
                    .setMessage(R.string.confirmDelete)
                    .setPositiveButton(android.R.string.ok, (d, w) -> executor.execute(() -> {
                        try {
                            String isoDate = item.getString(com.money.manager.ex.domainmodel.StockHistory.DATE);
                            StockHistoryRepository repo = new StockHistoryRepository(PriceEditActivity.this);
                            // check if a DB record exists for that date
                            com.money.manager.ex.domainmodel.StockHistory dbEntry = repo.getPriceForDate(model.symbol, isoDate);
                            if (dbEntry != null) {
                                long deleted = repo.deletePrice(model.symbol, isoDate);
                                if (deleted > 0) {
                                    runOnUiThread(() -> {
                                        loadHistory();
                                        loadHistoricalPriceForCurrentDate();
                                    });
                                }
                            } else {
                                // synthetic row: remove from adapter and, if visible, clear the editor price
                                runOnUiThread(() -> {
                                    historyAdapter.removeAt(position);
                                    if (isoDate.equals(model.date.toIsoDateString())) {
                                        model.price = MoneyFactory.fromDouble(0);
                                        model.display(PriceEditActivity.this, viewHolder);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Timber.e(e, "Error deleting price");
                        }
                    }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        priceHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        priceHistoryRecyclerView.setAdapter(historyAdapter);
        priceHistoryRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    private void scrollToCurrentDate() {
        String isoDate = model.date.toIsoDateString();
        int position = historyAdapter.findPositionForDate(isoDate);
        if (position >= 0) {
            priceHistoryRecyclerView.post(() -> priceHistoryRecyclerView.smoothScrollToPosition(position));
        }
    }

    private void loadHistoricalPriceForCurrentDate() {
        String isoDate = model.date.toIsoDateString();
        executor.execute(() -> {
            try {
                StockHistory history = historyRepository.getPriceForDate(model.symbol, isoDate);
                if (history != null) {
                    String valueStr = history.getString(StockHistory.VALUE);
                    if (valueStr != null) {
                        model.price = MoneyFactory.fromString(valueStr);
                        runOnUiThread(() -> model.display(this, viewHolder));
                    }
                } else if (isoDate.equals(mInitialDateIso) && mStockCurrentPrice != null) {
                    // No history entry for today: restore the stock's current price.
                    model.price = mStockCurrentPrice;
                    runOnUiThread(() -> model.display(this, viewHolder));
                }
            } catch (Exception e) {
                Timber.e(e, "Error loading historical price for date %s", isoDate);
            }
        });
    }

    private void loadHistory() {
        executor.execute(() -> {
            try {
                List<StockHistory> history = historyRepository.getAllPricesForSymbol(model.symbol);
                if (mInitialDateIso != null && mStockCurrentPrice != null) {
                    boolean hasTodayEntry = false;
                    for (StockHistory entry : history) {
                        if (mInitialDateIso.equals(entry.getString(StockHistory.DATE))) {
                            hasTodayEntry = true;
                            break;
                        }
                    }
                    if (!hasTodayEntry) {
                        ContentValues cv = new ContentValues();
                        cv.put(StockHistory.SYMBOL, model.symbol);
                        cv.put(StockHistory.DATE, mInitialDateIso);
                        cv.put(StockHistory.VALUE, mStockCurrentPrice.toString());
                        history.add(0, new StockHistory(cv));
                    }
                }
                runOnUiThread(() -> {
                    historyAdapter.setData(history);
                    scrollToCurrentDate();
                });
            } catch (Exception e) {
                Timber.e(e, "Error loading price history");
            }
        });
    }

    private void onDownloadPricesClick() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.download_prices_explanation)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> showStartDatePicker())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showStartDatePicker() {
        MmxDate defaultFrom = new MmxDate().minusDays(30);
        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    MmxDate fromDate = new MmxDate(year, month, day);
                    downloadPrices(fromDate, new MmxDate());
                },
                defaultFrom.getYear(),
                defaultFrom.getMonthOfYear(),
                defaultFrom.getDayOfMonth()
        );
        picker.setTitle(getString(R.string.from_date));
        picker.show();
    }

    private void downloadPrices(MmxDate fromDate, MmxDate toDate) {
        Toast.makeText(this, R.string.starting_price_update, Toast.LENGTH_SHORT).show();
        StockPriceRepository priceRepository = new StockPriceRepository(getApplication());
        priceRepository.downloadPriceHistory(model.symbol, fromDate.toDate(), toDate.toDate())
                .observe(this, count -> {
                    if (count == null) return;
                    if (count < 0) {
                        Toast.makeText(this, R.string.error_downloading_symbol, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                getString(R.string.prices_downloaded, count),
                                Toast.LENGTH_SHORT).show();
                        loadHistory();
                        loadHistoricalPriceForCurrentDate();
                    }
                });
    }

    private void onAddClick() {
        if (save()) {
            loadHistory();
            loadHistoricalPriceForCurrentDate();
            scrollToCurrentDate();
        }
    }

    private boolean save() {
        StockRepository repo = new StockRepository(this);
        repo.updateCurrentPrice(model.symbol, model.price);

        StockHistoryRepository historyRepository = new StockHistoryRepository(this);
        boolean result = historyRepository.addStockHistoryRecord(model);
        if (!result) {
            Toast.makeText(this, getString(R.string.error_update_currency_exchange_rate),
                    Toast.LENGTH_SHORT).show();
        }
        return result;
    }
}
