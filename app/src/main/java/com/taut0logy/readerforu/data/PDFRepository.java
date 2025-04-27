package com.taut0logy.readerforu.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class PDFRepository {

    private static final String TAG = "PDFRepository";
    private SQLiteDatabase database;
    private DatabaseHelper dbHelper;
    private String[] allColumns = {
            DatabaseHelper.COLUMN_ID,
            DatabaseHelper.COLUMN_PATH,
            DatabaseHelper.COLUMN_NAME,
            DatabaseHelper.COLUMN_AUTHOR,
            DatabaseHelper.COLUMN_DESCRIPTION,
            DatabaseHelper.COLUMN_TOTAL_PAGES,
            DatabaseHelper.COLUMN_CURRENT_PAGE,
            DatabaseHelper.COLUMN_IS_FAVOURITE,
            DatabaseHelper.COLUMN_LAST_READ_TIMESTAMP,
            DatabaseHelper.COLUMN_DATE_ADDED_TIMESTAMP,
            DatabaseHelper.COLUMN_THUMBNAIL_PATH,
            DatabaseHelper.COLUMN_IS_PROTECTED
    };

    public PDFRepository(Context context) {
        dbHelper = new DatabaseHelper(context);
    }

    public void open() throws SQLException {
        database = dbHelper.getWritableDatabase();
        Log.i(TAG, "Database opened successfully.");
    }

    public void close() {
        dbHelper.close();
        Log.i(TAG, "Database closed.");
    }

    // Insert or Update a PDFFile
    public PDFFile insertOrUpdatePDFFile(PDFFile pdfFile) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_PATH, pdfFile.getLocation());
        values.put(DatabaseHelper.COLUMN_NAME, pdfFile.getName());
        values.put(DatabaseHelper.COLUMN_AUTHOR, pdfFile.getAuthor());
        values.put(DatabaseHelper.COLUMN_DESCRIPTION, pdfFile.getDescription());
        values.put(DatabaseHelper.COLUMN_TOTAL_PAGES, pdfFile.getTotalPages());
        values.put(DatabaseHelper.COLUMN_CURRENT_PAGE, pdfFile.getCurrPage());
        values.put(DatabaseHelper.COLUMN_IS_FAVOURITE, pdfFile.getFavourite() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_LAST_READ_TIMESTAMP, pdfFile.getLastRead());
        values.put(DatabaseHelper.COLUMN_DATE_ADDED_TIMESTAMP, pdfFile.getDateAdded());
        values.put(DatabaseHelper.COLUMN_THUMBNAIL_PATH, pdfFile.getImagePath()); // Assuming getImagePath holds thumbnail path
        values.put(DatabaseHelper.COLUMN_IS_PROTECTED, pdfFile.isProtected() ? 1 : 0);

        // Try to update first based on path
        int rowsAffected = database.update(DatabaseHelper.TABLE_PDF_FILES,
                values,
                DatabaseHelper.COLUMN_PATH + " = ?",
                new String[]{pdfFile.getLocation()});

        if (rowsAffected > 0) {
            Log.d(TAG, "Updated existing PDF: " + pdfFile.getLocation());
            // Query the updated row to get the ID
            Cursor cursor = database.query(DatabaseHelper.TABLE_PDF_FILES,
                    allColumns, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{pdfFile.getLocation()}, null, null, null);
            if (cursor.moveToFirst()) {
                PDFFile updatedPdf = cursorToPDFFile(cursor);
                cursor.close();
                return updatedPdf;
            } else {
                cursor.close();
                return pdfFile; // Should not happen, but return original as fallback
            }
        } else {
            // If update failed (no rows affected), insert new row
            long insertId = database.insert(DatabaseHelper.TABLE_PDF_FILES, null, values);
            if (insertId == -1) {
                Log.e(TAG, "Error inserting PDF: " + pdfFile.getLocation());
                return null; // Indicate error
            } else {
                Log.d(TAG, "Inserted new PDF with ID: " + insertId + ", Path: " + pdfFile.getLocation());
                // Query the new row to get the complete PDFFile object with ID
                Cursor cursor = database.query(DatabaseHelper.TABLE_PDF_FILES,
                        allColumns, DatabaseHelper.COLUMN_ID + " = " + insertId, null, null, null, null);
                if (cursor.moveToFirst()) {
                    PDFFile newPdf = cursorToPDFFile(cursor);
                    cursor.close();
                    return newPdf;
                } else {
                    cursor.close();
                    pdfFile.setId(insertId); // Manually set ID if query fails
                    return pdfFile;
                }
            }
        }
    }

    public void deletePDFFile(String path) {
        Log.d(TAG, "Deleting PDF with path: " + path);
        int rowsDeleted = database.delete(DatabaseHelper.TABLE_PDF_FILES,
                DatabaseHelper.COLUMN_PATH + " = ?",
                new String[]{path});
        Log.d(TAG, "Rows deleted: " + rowsDeleted);
    }

    public void deletePDFFile(long id) {
        Log.d(TAG, "Deleting PDF with ID: " + id);
        int rowsDeleted = database.delete(DatabaseHelper.TABLE_PDF_FILES,
                DatabaseHelper.COLUMN_ID + " = " + id,
                null);
        Log.d(TAG, "Rows deleted: " + rowsDeleted);
    }

    public List<PDFFile> getAllPDFFiles() {
        List<PDFFile> pdfFiles = new ArrayList<>();
        Cursor cursor = database.query(DatabaseHelper.TABLE_PDF_FILES, allColumns, null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            PDFFile pdfFile = cursorToPDFFile(cursor);
            pdfFiles.add(pdfFile);
            cursor.moveToNext();
        }
        cursor.close();
        Log.d(TAG, "Fetched " + pdfFiles.size() + " PDF files from DB.");
        return pdfFiles;
    }

    public PDFFile getPDFFileByPath(String path) {
        Cursor cursor = database.query(DatabaseHelper.TABLE_PDF_FILES,
                allColumns, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{path}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            PDFFile pdfFile = cursorToPDFFile(cursor);
            cursor.close();
            return pdfFile;
        } else {
            if (cursor != null) {
                cursor.close();
            }
            return null;
        }
    }
    
    public PDFFile getPDFFileById(long id) {
        Cursor cursor = database.query(DatabaseHelper.TABLE_PDF_FILES,
                allColumns, DatabaseHelper.COLUMN_ID + " = " + id, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            PDFFile pdfFile = cursorToPDFFile(cursor);
            cursor.close();
            return pdfFile;
        } else {
            if (cursor != null) {
                cursor.close();
            }
            return null;
        }
    }

    // Update specific fields
    public int updateFavouriteStatus(String path, boolean isFavourite) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_IS_FAVOURITE, isFavourite ? 1 : 0);
        return database.update(DatabaseHelper.TABLE_PDF_FILES, values, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{path});
    }

    public int updateCurrentPage(String path, int currentPage, long lastReadTimestamp) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_CURRENT_PAGE, currentPage);
        values.put(DatabaseHelper.COLUMN_LAST_READ_TIMESTAMP, lastReadTimestamp);
        return database.update(DatabaseHelper.TABLE_PDF_FILES, values, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{path});
    }

    public int updateMetadata(String path, String name, String author, String description) {
         ContentValues values = new ContentValues();
         values.put(DatabaseHelper.COLUMN_NAME, name);
         values.put(DatabaseHelper.COLUMN_AUTHOR, author);
         values.put(DatabaseHelper.COLUMN_DESCRIPTION, description);
         return database.update(DatabaseHelper.TABLE_PDF_FILES, values, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{path});
    }

    /**
     * Update the thumbnail path for a PDF file
     * @param path Path to the PDF file
     * @param thumbnailPath Path to the thumbnail image
     * @return Number of rows affected
     */
    public int updateThumbnailPath(String path, String thumbnailPath) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_THUMBNAIL_PATH, thumbnailPath);
        return database.update(DatabaseHelper.TABLE_PDF_FILES, values, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{path});
    }

    /**
     * Update the protected status for a PDF file
     * @param path Path to the PDF file
     * @param isProtected True if the file is protected, false otherwise
     * @return Number of rows affected
     */
    public int updateProtectedStatus(String path, boolean isProtected) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_IS_PROTECTED, isProtected ? 1 : 0);
        return database.update(DatabaseHelper.TABLE_PDF_FILES, values, DatabaseHelper.COLUMN_PATH + " = ?", new String[]{path});

    }

    /**
     * Update the last read time for a PDF file
     * @param filePath The location/path of the PDF file
     * @param lastReadTime The timestamp when the file was last read
     * @return true if update was successful, false otherwise
     */
    public boolean updateLastReadTime(String filePath, long lastReadTime) {
        if (database == null || !database.isOpen()) {
            try {
                open();
            } catch (SQLException e) {
                Log.e(TAG, "Error opening database in updateLastReadTime", e);
                return false;
            }
        }
        
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_LAST_READ_TIMESTAMP, lastReadTime);
        
        int rowsAffected = database.update(
            DatabaseHelper.TABLE_PDF_FILES,
            values,
            DatabaseHelper.COLUMN_PATH + " = ?",
            new String[] { filePath }
        );
        
        return rowsAffected > 0;
    }
    
    /**
     * Get the thumbnail path for a PDF file
     * @param path Path to the PDF file
     * @return Thumbnail path or null if not found
     */
    public String getThumbnailPath(String path) {
        Cursor cursor = database.query(DatabaseHelper.TABLE_PDF_FILES,
                new String[]{DatabaseHelper.COLUMN_THUMBNAIL_PATH},
                DatabaseHelper.COLUMN_PATH + " = ?", 
                new String[]{path}, 
                null, null, null);
        
        String thumbnailPath = null;
        if (cursor != null && cursor.moveToFirst()) {
            thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_THUMBNAIL_PATH));
            cursor.close();
        }
        return thumbnailPath;
    }

    /**
     * Update the total pages for a PDF file
     * @param filePath The location/path of the PDF file
     * @param totalPages The total number of pages in the PDF
     * @return true if update was successful, false otherwise
     */
    public boolean updateTotalPages(String filePath, int totalPages) {
        if (database == null || !database.isOpen()) {
            try {
                open();
            } catch (SQLException e) {
                Log.e(TAG, "Error opening database in updateTotalPages", e);
                return false;
            }
        }
        
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_TOTAL_PAGES, totalPages);
        
        int rowsAffected = database.update(
            DatabaseHelper.TABLE_PDF_FILES,
            values,
            DatabaseHelper.COLUMN_PATH + " = ?",
            new String[] { filePath }
        );
        
        return rowsAffected > 0;
    }

    /**
     * Convert a database cursor to a PDFFile object
     * @param cursor Database cursor positioned at the row to convert
     * @return PDFFile object with data from the cursor
     */
    private PDFFile cursorToPDFFile(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
        String path = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PATH));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_NAME));
        String author = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AUTHOR));
        String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DESCRIPTION));
        int totalPages = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TOTAL_PAGES));
        int currentPage = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CURRENT_PAGE));
        boolean isFavourite = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_FAVOURITE)) == 1;
        long lastRead = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LAST_READ_TIMESTAMP));
        long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DATE_ADDED_TIMESTAMP));
        String thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_THUMBNAIL_PATH));
        boolean isProtected = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_PROTECTED)) == 1;

        // Create PDFFile object - Assuming constructor exists or needs adjustment
        PDFFile pdfFile = new PDFFile(name, author, description, path, thumbnailPath, currentPage, totalPages, isFavourite, lastRead, dateAdded);
        pdfFile.setId(id); // Set the database ID
        pdfFile.setProtected(isProtected);
        // Note: The Bitmap thumbnail itself is not stored in DB, only its path.
        // Loading the bitmap would happen elsewhere (e.g., in the adapter).

        return pdfFile;
    }
}