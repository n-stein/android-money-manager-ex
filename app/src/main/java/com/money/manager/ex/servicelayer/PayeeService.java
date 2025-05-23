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
package com.money.manager.ex.servicelayer;

import android.content.ContentValues;
import android.content.Context;
import android.text.TextUtils;

import com.money.manager.ex.Constants;
import com.money.manager.ex.datalayer.AccountTransactionRepository;
import com.money.manager.ex.datalayer.PayeeRepository;
import com.money.manager.ex.domainmodel.Payee;

/**
 */
public class PayeeService
    extends ServiceBase {

    public PayeeService(Context context) {
        super(context);

        this.payeeRepository = new PayeeRepository(context);
    }

    private final PayeeRepository payeeRepository;

    public Payee loadByName(String name) {
        return payeeRepository.loadByName(name);
    }

    public long loadIdByName(String name) {
        return payeeRepository.loadIdByName(name);
    }

    public Payee createNew(String name) {
        if (TextUtils.isEmpty(name)) return null;

        name = name.trim();

        Payee payee = new Payee();
        payee.setName(name);
        payee.setCategoryId(Constants.NOT_SET);

        long id = this.payeeRepository.add(payee);

        payee.setId(id);

        return payee;
    }

    public boolean exists(String name) {
        name = name.trim();

        Payee payee = loadByName(name);
        return (payee != null);
    }

    public boolean isPayeeUsed(long payeeId) {
        AccountTransactionRepository repo = new AccountTransactionRepository(getContext());
        return repo.isPayeeUsed(payeeId);
    }

    public long update(long id, String name) {
        if(TextUtils.isEmpty(name)) return Constants.NOT_SET;

        name = name.trim();

        ContentValues values = new ContentValues();
        values.put(Payee.PAYEENAME, name);

        return getContext().getContentResolver().update(payeeRepository.getUri(),
                values,
                Payee.PAYEEID + "=?",
                new String[]{Long.toString(id)});
    }

    /**
     * Method, which returns the last payee used
     * @return last payee used
     */
    public Payee getLastPayeeUsed() {
        // TODO
        return null;
    }
}
