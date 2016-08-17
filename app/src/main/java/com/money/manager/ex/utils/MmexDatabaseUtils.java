/*
 * Copyright (C) 2012-2016 The Android Money Manager Ex Project Team
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
package com.money.manager.ex.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.money.manager.ex.MoneyManagerApplication;
import com.money.manager.ex.R;
import com.money.manager.ex.log.ExceptionHandler;
import com.money.manager.ex.core.InfoKeys;
import com.money.manager.ex.database.MmexOpenHelper;
import com.money.manager.ex.datalayer.InfoRepository;
import com.money.manager.ex.domainmodel.Info;
import com.money.manager.ex.settings.AppSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.Lazy;
import timber.log.Timber;

/**
 * Various database-related utility functions
 */
public class MmexDatabaseUtils {

    public static void closeCursor(Cursor c) {
        if (c == null || c.isClosed()) return;

        c.close();
    }

    public static String[] getArgsForId(int id) {
        String[] result = new String[] { Integer.toString(id) };
        return result;
    }

    public static boolean isEncryptedDatabase(String dbPath) {
        return dbPath.contains(".emb");
    }

    // Dynamic

    public MmexDatabaseUtils(Context context){
        mContext = context;

        // dependency injection
        MoneyManagerApplication.getApp().mainComponent.inject(this);
    }

    @Inject Lazy<MmexOpenHelper> openHelper;
    private Context mContext;

    public Context getContext() {
        return mContext;
    }

    /**
     * Runs SQLite pragma check on the database file.
     * @return A boolean indicating whether the check was successfully completed.
     */
    public boolean checkIntegrity() {
        boolean result = openHelper.get().getReadableDatabase()
                .isDatabaseIntegrityOk();
        return result;
    }

    public String makePlaceholders(int len) {
        if (len < 1) {
            // It would lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    /**
     * Checks if all the required tables are present.
     * Should be expanded and improved to check for the whole schema.
     * @return A boolean indicating whether the schema is correct.
     */
    public boolean checkSchema() {
        try {
            return checkSchemaInternal();
        } catch (Exception e) {
            Timber.e(e, "checking schema");
            return false;
        }
    }

    /**
     * Creates a new database file at the default location.
     * @param filename File name for the new database. Extension .mmb will be appended if not
     *                 included in the filename.
     */
    public boolean createDatabase(String filename) {
        boolean result = false;

        try {
            result = createDatabase_Internal(filename);
        } catch (Exception e) {
            Timber.e(e, "creating database");
        }
        return result;
    }

    public boolean fixDuplicates() {
        boolean result = false;

        // check if there are duplicate records in Info Table
        InfoRepository repo = new InfoRepository(getContext());
        List<Info> results = repo.loadAll(InfoKeys.DATEFORMAT);
        if (results == null) return false;

        if (results.size() > 1) {
            // delete them, leaving only the first one
            int keepId = results.get(0).getId();

            for(Info toBeDeleted : results) {
                int idToDelete = toBeDeleted.getId();
                if (idToDelete != keepId) {
                    repo.delete(idToDelete);
                }
            }
        } else {
            // no duplicates found
            result = true;
        }

        return result;
    }

    /**
     * Gets the directory on external storage where the database is (to be) stored. New databases
     * are created here by default.
     * The directory is created if it does not exist.
     * @return the default database directory
     */
    public String getDatabaseDirectory() {
        File defaultFolder;

        // try with the external storage first.
        File externalStorageDirectory = Environment.getExternalStorageDirectory();

        if (externalStorageDirectory == null || !externalStorageDirectory.exists() ||
                !externalStorageDirectory.isDirectory() || !externalStorageDirectory.canWrite()) {
            return getContext().getFilesDir().getAbsolutePath();
        }

        defaultFolder = new File(externalStorageDirectory + File.separator + getContext().getPackageName());
        if (!defaultFolder.exists()) {
            defaultFolder = new File(externalStorageDirectory + "/MoneyManagerEx");
            if (!defaultFolder.exists()) {
                //make a directory
                if (!defaultFolder.mkdirs()) {
                    return getContext().getFilesDir().getAbsolutePath();
                }
            }
        }
//        return defaultFolder;

        String databasePath;

        if (defaultFolder.getAbsoluteFile().exists()) {
            databasePath = defaultFolder.toString();
        } else {
            String internalFolder;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                internalFolder = getContext().getApplicationInfo().dataDir;
            } else {
                internalFolder = "/data/data/" + getContext().getApplicationContext().getPackageName();
            }
            // add databases
            internalFolder += "/databases"; // "/data.mmb";
            databasePath = internalFolder;
        }

        return databasePath;
    }

    // Private

    private boolean checkSchemaInternal() {
        boolean result = false;

        // Get the names of all the tables from the generation script.
        ArrayList<String> scriptTables;
        try {
            scriptTables = getAllTableNamesFromGenerationScript();
        } catch (IOException | SQLiteDiskIOException ex) {
            Timber.e(ex, "reading table names from generation script");
            return false;
        }

        // get the list of all the tables from the database.
        ArrayList<String> existingTables = getTableNamesFromDb();

        // compare. retainAll, removeAll, addAll
        scriptTables.removeAll(existingTables);
        // If there is anything left, the script schema has more tables than the db.
        if (!scriptTables.isEmpty()) {
            StringBuilder message = new StringBuilder("Tables missing: ");
            for(String table:scriptTables) {
                message.append(table);
                message.append(" ");
            }
            showToast(message.toString(), Toast.LENGTH_LONG);
        } else {
            // everything matches
            result = true;
        }

        return result;
    }

    private boolean createDatabase_Internal(String filename)
        throws IOException {
        boolean result = true;

        filename = cleanupFilename(filename);

        // it might be enough simply to generate the new filename and set it as the default database.
        MmexDatabaseUtils dbUtils = new MmexDatabaseUtils(getContext());
        String location = dbUtils.getDatabaseDirectory();
        String newFilePath = location + File.separator + filename;

        // Create db file.
        File dbFile = new File(newFilePath);
        if (dbFile.exists()) {
            showToast(R.string.create_db_exists, Toast.LENGTH_SHORT);
            return false;
        } else {
            result = dbFile.createNewFile();
        }

        // close connection
        openHelper.get().close();

        // change database
        // store as the default database in settings
        AppSettings settings = new AppSettings(getContext());
        settings.getDatabaseSettings().setDatabasePath(newFilePath);

        return result;
    }

    private String cleanupFilename(String filename) {
        // trim any trailing or leading spaces
        filename = filename.trim();

        // check if filename already contains the extension
        boolean containsExtension = Pattern.compile(Pattern.quote(".mmb"), Pattern.CASE_INSENSITIVE)
                .matcher(filename)
                .find();
        if (!containsExtension) {
            filename += ".mmb";
        }

        return filename;
    }

    private ArrayList<String> getAllTableNamesFromGenerationScript()
            throws IOException {
        InputStream inputStream = getContext().getResources().openRawResource(R.raw.tables_v1);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();
        String textToMatch = "create table";
        ArrayList<String> tableNames = new ArrayList<>();

        while (line != null) {
            boolean found = false;
            if (line.contains(textToMatch)) found = true;
            if (!found && line.contains(textToMatch.toUpperCase())) found = true;

            if (found) {
                // extract the table name from the instruction
                line = line.replace(textToMatch, "");
                line = line.replace(textToMatch.toUpperCase(), "");
                line = line.replace("(", "");
                // remove any empty spaces.
                line = line.trim();

                tableNames.add(line);
            }

            line = reader.readLine();
        }

        return tableNames;
    }

    /**
     * Get all table Details from teh sqlite_master table in Db.
     * @return An ArrayList of table details.
     */
    private ArrayList<String> getTableNamesFromDb() {
        SQLiteDatabase db = openHelper.get().getReadableDatabase();

        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        ArrayList<String> result = new ArrayList<>();
        int i = 0;
        while (c.moveToNext()) {
            String temp = c.getString(i);
            result.add(temp);
        }
        c.close();

        return result;
    }

    private void showToast(int resourceId, int duration) {
        Toast.makeText(getContext(), resourceId, duration).show();
    }

    private void showToast(String text, int duration) {
        Toast.makeText(getContext(), text, duration).show();
    }

}