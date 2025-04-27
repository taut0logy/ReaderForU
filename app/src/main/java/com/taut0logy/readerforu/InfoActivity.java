package com.taut0logy.readerforu;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.imageview.ShapeableImageView;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;
import com.taut0logy.readerforu.data.PDFFile;
import com.taut0logy.readerforu.data.PDFRepository;

import java.io.File;
import java.io.IOException;

public class InfoActivity extends AppCompatActivity {
    private static final String TAG = "InfoActivity";
    private Toolbar toolbar;
    private PDFFile pdfFile;
    private int pdfPosition;
    private Uri currentFileUri; // To store the URI obtained via SAF
    private String pendingAction = null; // To track if we are editing or deleting
    private PDFRepository pdfRepository; // Database repository

    // Launcher for getting content URI
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    currentFileUri = result.getData().getData();
                    if (currentFileUri != null) {
                        // Persist permission for future access
                        final int takeFlags = result.getData().getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        try {
                            getContentResolver().takePersistableUriPermission(currentFileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            Log.e("SAF", "Failed to take persistable URI permission", e);
                            // Handle error - maybe inform user they need to pick again next time
                        }

                        if ("edit".equals(pendingAction)) {
                            Log.d("SAF", "Got URI for edit: " + currentFileUri);
                            Intent editIntent = new Intent(InfoActivity.this, EditActivity.class);
                            editIntent.setData(currentFileUri); // Pass URI to EditActivity
                            editIntent.putExtra("position", pdfPosition); // Still pass position for cache update
                            startActivity(editIntent);
                        } else if ("delete".equals(pendingAction)) {
                            Log.d("SAF", "Got URI for delete: " + currentFileUri);
                            showDeleteConfirmationDialog();
                        }
                    } else {
                        Toast.makeText(this, "Failed to get file URI", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "File selection cancelled or failed", Toast.LENGTH_SHORT).show();
                }
                pendingAction = null; // Reset pending action
            });

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        ShapeableImageView imageView;
        TextView bookName,authorName,description,totalPages,size,location, modified;
        toolbar = findViewById(R.id.info_toolbar);
        imageView = findViewById(R.id.bookImageView);
        bookName = findViewById(R.id.file_name);
        authorName = findViewById(R.id.author_name);
        description = findViewById(R.id.description);
        totalPages = findViewById(R.id.pagecnt);
        size = findViewById(R.id.size);
        modified = findViewById(R.id.modified);
        location = findViewById(R.id.path);
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

        // Get PDF file information from intent
        pdfPosition = getIntent().getIntExtra("position", -1);
        String fileLocation = getIntent().getStringExtra("file_location");
        
        // Try to get the PDF file from the position or location
        if (pdfPosition >= 0 && pdfPosition < BrowserActivity.getPdfFiles().size()) {
            // Get from BrowserActivity's static list
            pdfFile = BrowserActivity.getPdfFiles().get(pdfPosition);
        } else if (fileLocation != null) {
            // Get from database by location
            pdfFile = pdfRepository.getPDFFileByPath(fileLocation);
            // If found in database, update position for future use
            if (pdfFile != null) {
                pdfPosition = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            }
        }
        
        // Check if we have a valid PDF file
        if (pdfFile == null) {
            Toast.makeText(this, "Error: Invalid file information.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Handle PDF thumbnail
        if ("__protected".equals(pdfFile.getImagePath())) {
            imageView.setImageResource(R.drawable.lock);
        } else {
            if (pdfFile.getThumbnail() == null) {
                imageView.setImageResource(R.drawable.icon);
            } else {
                imageView.setImageBitmap(pdfFile.getThumbnail());
            }
        }
        
        // Set basic info from PDFFile object
        bookName.setText(pdfFile.getName());
        authorName.setText(pdfFile.getAuthor());
        description.setText(pdfFile.getDescription());
        totalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        
        // Try to get metadata from PDF file
        String pdfLocation = pdfFile.getLocation();
        try {
            if (pdfLocation.startsWith("content://")) {
                // Handle content URI
                Uri uri = Uri.parse(pdfLocation);
                try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    if (inputStream != null) {
                        PdfReader reader = new PdfReader(inputStream);
                        PdfDocument pdfDocument = new PdfDocument(reader);
                        PdfDocumentInfo info = pdfDocument.getDocumentInfo();
                        modified.setText(info.getCreator() != null ? info.getCreator() : "Unknown");
                        pdfDocument.close();
                        reader.close();
                        
                        // Get file size
                        try {
                            java.io.InputStream sizeStream = getContentResolver().openInputStream(uri);
                            if (sizeStream != null) {
                                long fileSize = 0;
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = sizeStream.read(buffer)) != -1) {
                                    fileSize += bytesRead;
                                }
                                sizeStream.close();
                                String sizeStr = (fileSize / 1024) + " KB";
                                size.setText(sizeStr);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting content URI file size", e);
                            size.setText("Unknown size");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading PDF from content URI", e);
                    modified.setText("Unknown");
                    size.setText("Unknown size");
                }
            } else {
                // Handle file path
                File file = new File(pdfLocation);
                PdfReader reader = new PdfReader(pdfLocation);
                PdfDocument pdfDocument = new PdfDocument(reader);
                PdfDocumentInfo info = pdfDocument.getDocumentInfo();
                modified.setText(info.getCreator() != null ? info.getCreator() : "Unknown");
                pdfDocument.close();
                reader.close();
                
                // Get file size
                String sizeStr = (file.length() / 1024) + " KB";
                size.setText(sizeStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading PDF metadata", e);
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                // Handle password-protected files
                String password = getIntent().getStringExtra("password");
                if (password == null) {
                    Toast.makeText(this, "File is encrypted", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                try {
                    byte[] passwordBytes = password.getBytes();
                    PdfReader reader = new PdfReader(pdfLocation, new ReaderProperties().setPassword(passwordBytes));
                    PdfDocument pdfDocument = new PdfDocument(reader);
                    PdfDocumentInfo info = pdfDocument.getDocumentInfo();
                    modified.setText(info.getCreator() != null ? info.getCreator() : "Unknown");
                    pdfDocument.close();
                    reader.close();
                } catch (Exception passwordException) {
                    Log.e(TAG, "Error reading password-protected PDF", passwordException);
                    Toast.makeText(this, "Cannot access encrypted file", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            } else {
                modified.setText("Unknown");
                size.setText("Unknown size");
            }
        }
        
        location.setText(pdfLocation);
        
        setupToolbarMenu();
    }
    
    private void setupToolbarMenu() {
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if(id == R.id.action_favourite) {
                if (pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
                    Toast.makeText(this, "Can't add locked file to favourites", Toast.LENGTH_SHORT).show();
                    return true;
                }
                MenuItem favButton = toolbar.getMenu().findItem(R.id.action_favourite);
                toggleFavoriteStatus(favButton);
                return true;
            }
            if(id == R.id.action_about) {
                BrowserActivity.showAboutDialog(InfoActivity.this);
                return true;
            }
            if(id == R.id.action_edit) {
                if (pdfFile.getLocation().startsWith("content://")) {
                    // For content URIs, we can launch EditActivity directly with the URI
                    Uri contentUri = Uri.parse(pdfFile.getLocation());
                    Intent editIntent = new Intent(InfoActivity.this, EditActivity.class);
                    editIntent.setData(contentUri);
                    editIntent.putExtra("position", pdfPosition);
                    editIntent.putExtra("file_location", pdfFile.getLocation());
                    startActivity(editIntent);
                } else {
                    // For file paths, use the SAF picker to get proper permissions
                    pendingAction = "edit";
                    launchFilePicker();
                }
                return true;
            }
            if(id == R.id.action_delete) {
                if (pdfFile.getLocation().startsWith("content://")) {
                    // For content URIs, we can attempt direct deletion
                    Uri contentUri = Uri.parse(pdfFile.getLocation());
                    showDeleteConfirmationDialog();
                    currentFileUri = contentUri;
                } else {
                    // For file paths, use the SAF picker to get proper permissions
                    pendingAction = "delete";
                    launchFilePicker();
                }
                return true;
            }
            return false;
        });
    }
    
    private void toggleFavoriteStatus(MenuItem favButton) {
        boolean wasAlreadyFavorite = pdfFile.getFavourite();
        String fileLocation = pdfFile.getLocation();
        
        // Update database first
        pdfRepository.updateFavouriteStatus(fileLocation, !wasAlreadyFavorite);
        
        // Update the PDFFile object
        pdfFile.setFavourite(!wasAlreadyFavorite);
        
        // Update UI
        if (pdfFile.getFavourite()) {
            favButton.setIcon(R.drawable.baseline_star_24);
            synchronized (BrowserActivity.getFavPdfFiles()) {
                if (!BrowserActivity.getFavPdfFiles().contains(pdfFile)) {
                    BrowserActivity.getFavPdfFiles().add(pdfFile);
                }
            }
        } else {
            favButton.setIcon(R.drawable.baseline_star_border_24);
            synchronized (BrowserActivity.getFavPdfFiles()) {
                BrowserActivity.getFavPdfFiles().remove(pdfFile);
            }
        }
        
        // Try to update the PDF file metadata if it's a regular file path
        if (!fileLocation.startsWith("content://")) {
            try {
                PdfReader pdfReader = new PdfReader(fileLocation);
                PdfWriter pdfWriter = new PdfWriter(fileLocation + "_temp");
                PdfDocument pdfDocument = new PdfDocument(pdfReader, pdfWriter);
                PdfDocumentInfo pdfDocumentInfo = pdfDocument.getDocumentInfo();
                pdfDocumentInfo.setMoreInfo("favourite", pdfFile.getFavourite() ? "true" : "false");
                Log.d(TAG, "Updated favourite status in PDF metadata: " + pdfDocumentInfo.getMoreInfo("favourite"));
                pdfDocument.close();
                pdfReader.close();
                pdfWriter.close();
                
                // Replace original file with the updated one
                new Thread(() -> {
                    try {
                        java.nio.file.Files.move(
                            java.nio.file.Paths.get(fileLocation + "_temp"),
                            java.nio.file.Paths.get(fileLocation),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                    } catch (IOException e) {
                        Log.e(TAG, "Error replacing file after updating favourite status", e);
                    }
                }).start();
            } catch (IOException e) {
                Log.e(TAG, "Error updating favourite status in PDF metadata", e);
            }
        }
        
        // Notify BrowserActivity about the change
        if (pdfPosition >= 0) {
            synchronized (BrowserActivity.getPdfFiles()) {
                // Verify position is still valid
                if (pdfPosition < BrowserActivity.getPdfFiles().size()) {
                    BrowserActivity.getPdfFiles().set(pdfPosition, pdfFile);
                } else {
                    // If position isn't valid, try to find the file by path
                    for (int i = 0; i < BrowserActivity.getPdfFiles().size(); i++) {
                        if (BrowserActivity.getPdfFiles().get(i).getLocation().equals(fileLocation)) {
                            BrowserActivity.getPdfFiles().set(i, pdfFile);
                            pdfPosition = i; // Update position
                            break;
                        }
                    }
                }
            }
            
            // Update filtered list if present
            if (BrowserActivity.getFilteredPdfFiles() != null) {
                synchronized (BrowserActivity.getFilteredPdfFiles()) {
                    for (int i = 0; i < BrowserActivity.getFilteredPdfFiles().size(); i++) {
                        if (BrowserActivity.getFilteredPdfFiles().get(i).getLocation().equals(fileLocation)) {
                            BrowserActivity.getFilteredPdfFiles().set(i, pdfFile);
                            break;
                        }
                    }
                }
            }
            
            Intent intent = new Intent(BrowserActivity.ACTION_PDF_UPDATED);
            intent.putExtra("position", pdfPosition);
            intent.putExtra("path", fileLocation);
            sendBroadcast(intent);
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
        try {
            if (pdfRepository != null) {
                pdfRepository.open();
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error opening database in onStart", e);
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

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        // Optionally, specify initial directory or file
        // intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, ...);
        filePickerLauncher.launch(intent);
    }

    private void showDeleteConfirmationDialog() {
        if (currentFileUri == null) {
            Toast.makeText(this, "Cannot delete: File URI not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(InfoActivity.this);
        builder.setTitle("Delete");
        builder.setMessage("Are you sure you want to delete this file?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            deleteFileUsingSaf(currentFileUri);
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void deleteFileUsingSaf(Uri fileUri) {
        ContentResolver contentResolver = getContentResolver();
        try {
            if (DocumentsContract.deleteDocument(contentResolver, fileUri)) {
                Toast.makeText(this, "File deleted successfully", Toast.LENGTH_SHORT).show();

                // Delete from database
                pdfRepository.deletePDFFile(pdfFile.getLocation());
                Log.d(TAG, "File deleted from database: " + pdfFile.getLocation());

                // Delete the thumbnail if it exists
                if (pdfFile.getImagePath() != null && !pdfFile.getImagePath().isEmpty() && 
                    !pdfFile.getImagePath().equals("__protected")) {
                    com.taut0logy.readerforu.util.ThumbnailUtils.deleteThumbnail(pdfFile.getImagePath());
                }

                // Get the position in the master list (redundant check for safety)
                int masterPosition = pdfPosition;
                String filePath = pdfFile.getLocation();

                // Synchronize access to all lists
                synchronized (BrowserActivity.getPdfFiles()) {
                    // Verify masterPosition points to the right file, otherwise find it
                    if (masterPosition >= 0 && masterPosition < BrowserActivity.getPdfFiles().size() &&
                        !BrowserActivity.getPdfFiles().get(masterPosition).getLocation().equals(filePath)) {
                        // Position doesn't match, search for the correct position
                        masterPosition = -1;
                        for (int i = 0; i < BrowserActivity.getPdfFiles().size(); i++) {
                            if (BrowserActivity.getPdfFiles().get(i).getLocation().equals(filePath)) {
                                masterPosition = i;
                                break;
                            }
                        }
                    }

                    // Remove from master list
                    if (masterPosition != -1) {
                        BrowserActivity.getPdfFiles().remove(masterPosition);
                    }
                    
                    // Remove from favorites list if present
                    if (pdfFile.getFavourite()) {
                        BrowserActivity.getFavPdfFiles().remove(pdfFile);
                    }
                    
                    // Remove from filtered list
                    if (BrowserActivity.getFilteredPdfFiles() != null) {
                        for (int i = 0; i < BrowserActivity.getFilteredPdfFiles().size(); i++) {
                            if (BrowserActivity.getFilteredPdfFiles().get(i).getLocation().equals(filePath)) {
                                BrowserActivity.getFilteredPdfFiles().remove(i);
                                break;
                            }
                        }
                    }
                }

                // Notify BrowserActivity about the deletion
                Intent intent = new Intent(BrowserActivity.ACTION_PDF_DELETED);
                intent.putExtra("position", masterPosition); // Send position
                intent.putExtra("path", filePath); // Send path for robust removal
                sendBroadcast(intent);

                finish(); // Close InfoActivity after deletion
            } else {
                Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file: " + fileUri, e);
            Toast.makeText(this, "Error deleting file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.info_menu, menu);
        // Set initial favourite icon state
        MenuItem favButton = menu.findItem(R.id.action_favourite);
        if (pdfFile != null && favButton != null) {
             if (pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
                 favButton.setEnabled(false); // Disable fav for protected files
                 favButton.setIcon(R.drawable.baseline_star_border_24);
             } else {
                 favButton.setEnabled(true); // Re-enable if needed
                 favButton.setIcon(pdfFile.getFavourite() ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
             }
        }
        return true;
    }
}