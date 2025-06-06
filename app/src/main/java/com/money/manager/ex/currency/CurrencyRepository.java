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

package com.money.manager.ex.currency;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.money.manager.ex.database.DatasetType;
import com.money.manager.ex.datalayer.RepositoryBase;
import com.money.manager.ex.database.WhereStatementGenerator;
import com.money.manager.ex.domainmodel.Currency;

import info.javaperformance.money.Money;
import timber.log.Timber;

/**
 * Currency repository. Provides access to Currency entities.
 */
public class CurrencyRepository
    extends RepositoryBase<Currency> {

    private static final String TABLE_NAME = "currencyformats_v1";
    private static final String ID_COLUMN = Currency.CURRENCYID;
    private static final String NAME_COLUMN = Currency.CURRENCYNAME;

    public CurrencyRepository(Context context) {
        super(context, "currencyformats_v1", DatasetType.TABLE, "currencyformats", ID_COLUMN, NAME_COLUMN);

        //this.TABLENAME = "currencyformats_v1";
    }

    @Override
    public Currency createEntity() {
        return new Currency();
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public String[] getAllColumns() {
        return new String[] {
                ID_COLUMN + " AS _id", Currency.CURRENCYID, Currency.CURRENCYNAME,
            Currency.PFX_SYMBOL, Currency.SFX_SYMBOL, Currency.DECIMAL_POINT,
            Currency.GROUP_SEPARATOR, Currency.UNIT_NAME, Currency.CENT_NAME,
            Currency.SCALE, Currency.BASECONVRATE, Currency.CURRENCY_SYMBOL
                , Currency.CURRENCY_TYPE
        };
    }

    public boolean update(Currency value) {
        long id = value.getId();

        WhereStatementGenerator generator = new WhereStatementGenerator();
        String where = generator.getStatement(Currency.CURRENCYID, "=", id);

        return update(value, where);
    }

//    public boolean delete(Currency currency) {
//        delete(currency);
//    }

    public Currency loadCurrency(long currencyId) {
        return loadCurrency(
            Currency.CURRENCYID + "=?",
            new String[]{Long.toString(currencyId)});
    }

    public Currency loadCurrency(String code) {
        return loadCurrency(
            Currency.CURRENCY_SYMBOL + "=?",
            new String[] { code });
    }

    public long saveExchangeRate(long currencyId, Money exchangeRate) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Currency.BASECONVRATE, exchangeRate.toString());

        return getContext().getContentResolver().update(this.getUri(),
            contentValues,
            Currency.CURRENCYID + "=?",
            new String[] { Long.toString(currencyId) });
    }

    // private methods

    private Currency loadCurrency(String selection, String[] selectionArgs) {
        Currency result = null;
        try {
            result = loadCurrencyInternal(selection, selectionArgs);
        } catch (Exception e) {
            Timber.e(e, "loading currency");
        }
        return result;
    }

    private Currency loadCurrencyInternal(String selection, String[] selectionArgs) {
        Currency currency = new Currency();

        Cursor cursor = openCursor(null, selection, selectionArgs);
        if (cursor == null) return null;

        if (cursor.moveToNext()) {
            currency.loadFromCursor(cursor);
        } else {
            currency = null;
        }
        cursor.close();

        return currency;
    }

    public Currency first(String selection) {
        return query(null, selection, null);
    }

    public Currency query(String[] projection, String selection, String[] args) {
        Cursor c = openCursor(projection, selection, args);

        if (c == null) return null;

        Currency account = null;

        if (c.moveToNext()) {
            account = Currency.fromCursor(c);
        }

        c.close();

        return account;
    }
}
