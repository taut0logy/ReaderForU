package com.taut0logy.readerforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.SQLException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.taut0logy.readerforu.data.PDFFile;
import com.taut0logy.readerforu.data.PDFRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EditActivity extends AppCompatActivity {
    private static final String TAG = "EditActivity";
    private EditText etName, etAuthor, etDescription, etCreator;
    private PDFFile pdfFile;
    private int position;
    private Uri fileUri; // URI obtained from InfoActivity via SAF
    private PDFRepository pdfRepository; // Database repository

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        Toolbar toolbar = findViewById(R.id.edit_toolbar);
        etName = findViewById(R.id.bookNameEdit);
        etAuthor = findViewById(R.id.authorEdit);
        etDescription = findViewById(R.id.descriptionEdit);
        etCreator = findViewById(R.id.creatorEdit);
        Button saveButton = findViewById(R.id.editSaveButton);
        setSupportActionBar(toolbar);
        
        // Initialize database repository
        pdfRepository = new PDFRepository(this);
        try {
            pdfRepository.open();
        } catch (SQLException e) {
            Log.e(TAG, "Error opening database", e);
            Toast.makeText(this, "Error accessing database", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        position = intent.getIntExtra("position", -1);
        fileUri = intent.getData(); // Get the URI passed from InfoActivity
        String fileLocation = intent.getStringExtra("file_location");

        // Determine how to get the PDF file information
        if (fileUri != null) {
            // Use the URI directly
            loadPdfInfoFromUri(fileUri);
            // Try to find the file in the database
            if (fileLocation != null) {
                pdfFile = pdfRepository.getPDFFileByPath(fileLocation);
            } else if (position >= 0 && position < BrowserActivity.getPdfFiles().size()) {
                pdfFile = BrowserActivity.getPdfFiles().get(position);
            } else {
                // We need to create a new PDFFile entry for this URI
                pdfFile = createPdfFileFromUri(fileUri);
            }
        } else if (position >= 0 && position < BrowserActivity.getPdfFiles().size()) {
            // Get from position
            pdfFile = BrowserActivity.getPdfFiles().get(position);
            // Convert file path to URI if needed
            if (!pdfFile.getLocation().startsWith("content://")) {
                fileUri = Uri.fromFile(new File(pdfFile.getLocation()));
            } else {
                fileUri = Uri.parse(pdfFile.getLocation());
            }
            loadPdfInfoFromUri(fileUri);
        } else if (fileLocation != null) {
            // Get by location from database
            pdfFile = pdfRepository.getPDFFileByPath(fileLocation);
            if (pdfFile != null) {
                if (pdfFile.getLocation().startsWith("content://")) {
                    fileUri = Uri.parse(pdfFile.getLocation());
                } else {
                    fileUri = Uri.fromFile(new File(pdfFile.getLocation()));
                }
                loadPdfInfoFromUri(fileUri);
            } else {
                Toast.makeText(this, "Error: File not found in database.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, "Error: Invalid file information.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        saveButton.setOnClickListener(v -> saveChanges(fileUri));
    }

    private PDFFile createPdfFileFromUri(Uri uri) {
        try {
            String displayName = getDisplayNameFromUri(uri);
            
            // Extract metadata
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return new PDFFile(displayName, "Unknown", "No description", 
                    uri.toString(), null, 0, 0, false, 
                    System.currentTimeMillis(), System.currentTimeMillis());
            }
            
            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDocument = new PdfDocument(reader);
            PdfDocumentInfo info = pdfDocument.getDocumentInfo();
            
            String title = info.getTitle();
            if (title == null || title.isEmpty() || title.equals("null")) {
                title = displayName;
            }
            
            String author = info.getAuthor();
            if (author == null || author.isEmpty() || author.equals("null")) {
                author = "Unknown";
            }
            
            String description = info.getSubject();
            if (description == null || description.isEmpty() || description.equals("null")) {
                description = "No description";
            }
            
            int pageCount = pdfDocument.getNumberOfPages();
            
            pdfDocument.close();
            reader.close();
            inputStream.close();
            
            // Create and save to database
            PDFFile newFile = new PDFFile(title, author, description, uri.toString(), 
                null, 0, pageCount, false, System.currentTimeMillis(), System.currentTimeMillis());
            
            return pdfRepository.insertOrUpdatePDFFile(newFile);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating PDFFile from URI", e);
            String displayName = getDisplayNameFromUri(uri);
            return new PDFFile(displayName, "Unknown", "No description", 
                uri.toString(), null, 0, 0, false, 
                System.currentTimeMillis(), System.currentTimeMillis());
        }
    }
    
    private String getDisplayNameFromUri(Uri uri) {
        String displayName = "Unknown PDF";
        try {
            if (uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            displayName = cursor.getString(nameIndex);
                        }
                    }
                }
            } else if (uri.getScheme().equals("file")) {
                displayName = uri.getLastPathSegment();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting display name from URI", e);
        }
        return displayName != null ? displayName : "Unknown PDF";
    }

    private void loadPdfInfoFromUri(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        try {
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Cannot access file", Toast.LENGTH_SHORT).show();
                return;
            }
            
            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDocument = new PdfDocument(reader);
            PdfDocumentInfo info = pdfDocument.getDocumentInfo();

            String title = info.getTitle();
            String author = info.getAuthor();
            String subject = info.getSubject(); // Description is often in Subject
            String creator = info.getCreator();

            etName.setText(title != null && !title.isEmpty() && !title.equals("null") ? title : "");
            etAuthor.setText(author != null && !author.isEmpty() && !author.equals("null") ? author : "");
            etDescription.setText(subject != null && !subject.isEmpty() && !subject.equals("null") ? subject : "");
            etCreator.setText(creator != null && !creator.isEmpty() && !creator.equals("null") ? creator : "");

            pdfDocument.close();
            reader.close();
            inputStream.close();

        } catch (IOException e) {
            Log.e(TAG, "Error reading PDF info from URI: " + uri, e);
            Toast.makeText(this, "Error reading PDF details: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) { // Catch potential iText errors
            Log.e(TAG, "iText error reading PDF info from URI: " + uri, e);
            Toast.makeText(this, "Error parsing PDF details: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveChanges(Uri uri) {
        String newName = etName.getText().toString().trim();
        String newAuthor = etAuthor.getText().toString().trim();
        String newDescription = etDescription.getText().toString().trim();
        String newCreator = etCreator.getText().toString().trim();

        ContentResolver contentResolver = getContentResolver();
        ParcelFileDescriptor pfd = null;
        PdfDocument pdfDocument = null;
        PdfReader reader = null;

        try {
            // Open the document with read/write mode
            pfd = contentResolver.openFileDescriptor(uri, "rw");
            if (pfd == null) {
                throw new IOException("Failed to open file descriptor for writing");
            }

            // Create reader for the original content
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            reader = new PdfReader(inputStream); // Use the input stream from PFD

            // Create writer targeting the same file descriptor
            OutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor());
            PdfWriter writer = new PdfWriter(outputStream, new WriterProperties().setFullCompressionMode(true));

            // Create the PdfDocument
            pdfDocument = new PdfDocument(reader, writer);
            PdfDocumentInfo info = pdfDocument.getDocumentInfo();

            // Update metadata
            info.setTitle(newName.isEmpty() ? null : newName);
            info.setAuthor(newAuthor.isEmpty() ? null : newAuthor);
            info.setSubject(newDescription.isEmpty() ? null : newDescription); // Update subject for description
            info.setCreator(newCreator.isEmpty() ? null : newCreator);

            // Update the PDFFile object in database
            if (pdfFile != null) {
                pdfFile.setName(newName.isEmpty() ? "Unknown Title" : newName);
                pdfFile.setAuthor(newAuthor.isEmpty() ? "Unknown Author" : newAuthor);
                pdfFile.setDescription(newDescription.isEmpty() ? "No Description" : newDescription);
                
                // Update the database
                updateDatabase();
            }

            // Close the document - this flushes changes to the output stream
            pdfDocument.close(); // This closes the writer and reader implicitly

            // Notify BrowserActivity if needed
            if (position >= 0) {
                Intent updateIntent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
                updateIntent.putExtra("position", position);
                sendBroadcast(updateIntent);
            }

            Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show();
            finish(); // Close EditActivity after saving

        } catch (IOException e) {
            Log.e(TAG, "Error writing PDF info to URI: " + uri, e);
            Toast.makeText(this, "Error saving changes: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) { // Catch potential iText errors
            Log.e(TAG, "iText error writing PDF info to URI: " + uri, e);
            Toast.makeText(this, "Error processing PDF for saving: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            // Ensure resources are closed even if errors occur
            try {
                if (pdfDocument != null && !pdfDocument.isClosed()) {
                    pdfDocument.close();
                }
                if (pfd != null) {
                    pfd.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing resources", e);
            }
        }
    }

    private void updateDatabase() {
        // Update the database with the modified PDF file
        if (pdfRepository != null && pdfFile != null) {
            pdfRepository.updateMetadata(
                pdfFile.getLocation(),
                pdfFile.getName(),
                pdfFile.getAuthor(),
                pdfFile.getDescription()
            );
            Log.d(TAG, "Updated PDF metadata in database: " + pdfFile.getLocation());
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Don't close database here as other threads might still be using it
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Ensure database is open
        if (pdfRepository != null) {
            try {
                pdfRepository.open();
            } catch (SQLException e) {
                Log.e(TAG, "Error reopening database", e);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close database when activity is destroyed
        if (pdfRepository != null) {
            pdfRepository.close();
        }
    }
}