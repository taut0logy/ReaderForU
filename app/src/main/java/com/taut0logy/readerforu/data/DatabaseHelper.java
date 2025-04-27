package com.taut0logy.readerforu.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "readerforu.db";
    private static final int DATABASE_VERSION = 1;

    // Table Name
    public static final String TABLE_PDF_FILES = "pdf_files";

    // PDF Files Table Columns
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_PATH = "path"; // Unique identifier
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_AUTHOR = "author";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_TOTAL_PAGES = "total_pages";
    public static final String COLUMN_CURRENT_PAGE = "current_page";
    public static final String COLUMN_IS_FAVOURITE = "is_favourite"; // INTEGER 1 for true, 0 for false
    public static final String COLUMN_LAST_READ_TIMESTAMP = "last_read_timestamp"; // INTEGER as millis
    public static final String COLUMN_DATE_ADDED_TIMESTAMP = "date_added_timestamp"; // INTEGER as millis
    public static final String COLUMN_THUMBNAIL_PATH = "thumbnail_path"; // Path to cached thumbnail
    public static final String COLUMN_IS_PROTECTED = "is_protected"; // INTEGER 1 for true, 0 for false

    // Database creation sql statement
    private static final String TABLE_CREATE = 
        "CREATE TABLE " + TABLE_PDF_FILES + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_PATH + " TEXT NOT NULL UNIQUE, " +
        COLUMN_NAME + " TEXT, " +
        COLUMN_AUTHOR + " TEXT, " +
        COLUMN_DESCRIPTION + " TEXT, " +
        COLUMN_TOTAL_PAGES + " INTEGER DEFAULT 0, " +
        COLUMN_CURRENT_PAGE + " INTEGER DEFAULT 0, " +
        COLUMN_IS_FAVOURITE + " INTEGER DEFAULT 0, " +
        COLUMN_LAST_READ_TIMESTAMP + " INTEGER DEFAULT 0, " +
        COLUMN_DATE_ADDED_TIMESTAMP + " INTEGER DEFAULT 0, " +
        COLUMN_THUMBNAIL_PATH + " TEXT, " +
        COLUMN_IS_PROTECTED + " INTEGER DEFAULT 0" +
        ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i("DatabaseHelper", "Creating database table: " + TABLE_CREATE);
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(DatabaseHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PDF_FILES);
        // Create tables again
        onCreate(db);
        // TODO: Implement proper migration strategy for future versions if needed
    }
}