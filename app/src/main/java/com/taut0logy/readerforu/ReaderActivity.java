package com.taut0logy.readerforu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;

import com.github.barteksc.pdfviewer.PDFView;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.taut0logy.readerforu.data.PDFFile;
import com.taut0logy.readerforu.data.PDFRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity implements JumpToPageFragment.JumpToPageListener {
    private static final String TAG = "ReaderActivity";
    private PDFFile pdfFile;
    private TextToSpeech textToSpeech;
    private TextView etCurrPage;
    TextView tvBookName, tvAuthorName, tvTotalPages;
    private boolean barsVisible = true;
    private int recyclerPosition = -1;
    private ConstraintLayout topBar, bottomBar;
    private PDFView pdfView;
    private boolean isNight = false;
    private int nowPage = 0;
    private SharedPreferences sharedPreferences;
    private PDFRepository pdfRepository; // Database repository

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        ImageButton toggleDark, infoBtn, sharebtn, speechbtn;
        Button showDialog;
        pdfView = findViewById(R.id.pdfView);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        tvBookName = findViewById(R.id.tvBookName);
        tvAuthorName = findViewById(R.id.tvAuthorName);
        tvTotalPages = findViewById(R.id.tvTotalPage);
        etCurrPage = findViewById(R.id.etCurrentPage);
        toggleDark = findViewById(R.id.toggleDark);
        infoBtn = findViewById(R.id.infobtn);
        sharebtn = findViewById(R.id.sharebtn);
        speechbtn = findViewById(R.id.speechbtn);
        showDialog = findViewById(R.id.showDialog);
        
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

        // Handle intent data
        handleIntent(getIntent());

        if(pdfFile == null) {
            Toast.makeText(this, "Invalid file", Toast.LENGTH_SHORT).show();
            Intent intent2 = new Intent(this, BrowserActivity.class);
            startActivity(intent2);
            finish();
            return;
        }
        
        // Update last read time
        pdfFile.setLastRead(System.currentTimeMillis());
        
        // Save the updated last read time to database
        if (pdfRepository != null) {
            try {
                pdfRepository.updateLastReadTime(pdfFile.getLocation(), System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Error updating last read time", e);
            }
        }
        
        loadPreferences();

        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                //textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.8f);
            }
        }, "com.google.android.tts");

        tvBookName.setText(pdfFile.getName());
        tvAuthorName.setText(pdfFile.getAuthor());
        tvTotalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        
        // Load the PDF
        loadPdf(pdfFile.getLocation());

        toggleDark.setOnClickListener(v -> {
            if (isNight) {
                pdfView.setNightMode(false);
                isNight = false;
                pdfView.loadPages();
            } else {
                pdfView.setNightMode(true);
                isNight = true;
                pdfView.loadPages();
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isNight", isNight);
            editor.apply();
        });

        showDialog.setOnClickListener(v -> showJumpToPageDialog(pdfFile.getTotalPages(), nowPage));

        infoBtn.setOnClickListener(v -> {
            Intent intent3 = new Intent(ReaderActivity.this, InfoActivity.class);
            intent3.putExtra("position", recyclerPosition);
            intent3.putExtra("file_location", pdfFile.getLocation());
            startActivity(intent3);
        });

        sharebtn.setOnClickListener(v -> sharePdf());

        speechbtn.setOnClickListener(v -> {
            if(textToSpeech.isSpeaking()) {
                textToSpeech.stop();
                Toast.makeText(this, "Stopped speaking", Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Please wait");
            builder.setMessage("Extracting text from PDF...");
            ProgressBar progressBar = new ProgressBar(this);
            progressBar.setIndeterminate(true);
            progressBar.setPadding(0, 0, 0, 30);
            builder.setView(progressBar);
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                ArrayList<String> text = extractTextFromPDF();
                handler.post(() -> {
                    dialog.dismiss();
                    if (text.isEmpty()) {
                        Toast.makeText(getApplicationContext(), "No text found", Toast.LENGTH_SHORT).show();
                    } else {
                        for (String s : text) {
                            textToSpeech.speak(s, TextToSpeech.QUEUE_ADD, null, null);
                        }
                        Toast.makeText(getApplicationContext(), "Started speaking", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    @Override
    protected void onPause() {
        if(recyclerPosition == -1) {
            super.onPause();
            return;
        }
        pdfFile.setCurrPage(nowPage);
        BrowserActivity.getPdfFiles().set(recyclerPosition, pdfFile);
        Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
        intent.putExtra("position", recyclerPosition);
        intent.putExtra("pdfFile", pdfFile);
        sendBroadcast(intent);
        
        // Update database with current page, last read time, and ensure thumbnail path is saved
        if (pdfRepository != null) {
            pdfRepository.updateCurrentPage(pdfFile.getLocation(), nowPage, System.currentTimeMillis());
            
            // Make sure thumbnail path is saved if it exists
            if (pdfFile.getImagePath() != null && !pdfFile.getImagePath().isEmpty()) {
                pdfRepository.updateThumbnailPath(pdfFile.getLocation(), pdfFile.getImagePath());
                Log.d(TAG, "Updated thumbnail path in database: " + pdfFile.getImagePath());
            }
            
            Log.d(TAG, "Updated current page in database: " + nowPage);
        }
        
        super.onPause();
    }

    @Override
    protected void onStop() {
        if(recyclerPosition == -1) {
            super.onStop();
            return;
        }
        pdfFile.setCurrPage(nowPage);
        BrowserActivity.getPdfFiles().set(recyclerPosition, pdfFile);
        Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
        intent.putExtra("position", recyclerPosition);
        
        // Save night mode preference
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isNight", isNight);
        editor.apply();
        
        // Update database with current page and thumbnail path
        if (pdfRepository != null) {
            pdfRepository.updateCurrentPage(pdfFile.getLocation(), nowPage, System.currentTimeMillis());
            
            // Make sure thumbnail path is saved if it exists
            if (pdfFile.getImagePath() != null && !pdfFile.getImagePath().isEmpty()) {
                pdfRepository.updateThumbnailPath(pdfFile.getLocation(), pdfFile.getImagePath());
                Log.d(TAG, "Updated thumbnail path in database before stopping: " + pdfFile.getImagePath());
            }
            
            pdfRepository.close();
        }
        
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        // Close database connection
        if (pdfRepository != null) {
            pdfRepository.close();
        }
        super.onDestroy();
    }

    private void loadPreferences() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        isNight = sharedPreferences.getBoolean("isNight", false);
        
        // Get current page and thumbnail from database if available
        if (pdfRepository != null && pdfFile != null) {
            PDFFile dbFile = pdfRepository.getPDFFileByPath(pdfFile.getLocation());
            if (dbFile != null) {
                nowPage = dbFile.getCurrPage();
                
                // Get thumbnail path from database
                String thumbnailPath = pdfRepository.getThumbnailPath(pdfFile.getLocation());
                if (thumbnailPath != null && !thumbnailPath.isEmpty()) {
                    pdfFile.setImagePath(thumbnailPath);
                    Log.d(TAG, "Loaded thumbnail path from database: " + thumbnailPath);
                }
            } else {
                // Fallback to SharedPreferences if not in database yet
                nowPage = sharedPreferences.getInt(pdfFile.getLocation() + "nowPage", 0);
            }
        } else {
            nowPage = 0;
        }
        
        Log.d(TAG, "loadPreferences: " + nowPage + " " + isNight);
        
        // Update last read time in database
        if (recyclerPosition != -1 && pdfRepository != null) {
            new Thread(() -> pdfRepository.updateLastReadTime(pdfFile.getLocation(), System.currentTimeMillis())).start();
        }
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Reopen database connection if needed
        if (pdfRepository != null) {
            try {
                pdfRepository.open();
            } catch (SQLException e) {
                Log.e(TAG, "Error reopening database", e);
            }
        }
    }


    private void loadPdf(String location) {
        try {
            Log.d(TAG, "Attempting to load PDF from location: " + location);
            
            // Check if this is a content:// URI (SAF)
            if (location.startsWith("content://")) {
                Uri uri = Uri.parse(location);
                Log.d(TAG, "Loading from content URI: " + uri);
                
                try {
                    // Check permissions
                    int modeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri, modeFlags);
                } catch (SecurityException e) {
                    Log.w(TAG, "Could not take persistable permission, continuing anyway", e);
                    // We'll try to open it even without persistable permissions
                }
                
                // First try direct loading
                try {
                    configurePdfView(pdfView.fromUri(uri)
                        .defaultPage(Math.max(0, nowPage - 1))
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .nightMode(isNight)
                        .spacing(10));
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error using URI directly, trying file descriptor method", e);
                }
                
                // If direct loading fails, try through ParcelFileDescriptor
                try {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        // Convert ParcelFileDescriptor to InputStream
                        FileInputStream fileInputStream = new FileInputStream(pfd.getFileDescriptor());
                        configurePdfView(pdfView.fromStream(fileInputStream)
                            .defaultPage(Math.max(0, nowPage - 1))
                            .enableSwipe(true)
                            .swipeHorizontal(false)
                            .enableDoubletap(true)
                            .nightMode(isNight)
                            .spacing(10));
                    } else {
                        throw new IOException("Failed to open file descriptor");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load PDF using file descriptor", e);
                    Toast.makeText(this, "Error accessing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                // Traditional file path
                File file = new File(location);
                Log.d(TAG, "Loading from file path: " + file.getAbsolutePath() + ", exists: " + file.exists());
                
                if (file.exists()) {
                    configurePdfView(pdfView.fromFile(file)
                        .defaultPage(Math.max(0, nowPage - 1))
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .nightMode(isNight)
                        .spacing(10));
                } else {
                    Toast.makeText(this, "File not found: " + location, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "File does not exist: " + location);
                    finish();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error loading PDF: " + e.getMessage(), e);
            finish();
        }
    }
    
    private void loadPdfFromUri(Uri uri) {
        try {
            Log.d(TAG, "Loading PDF directly from URI: " + uri);
            configurePdfView(pdfView.fromUri(uri)
                .defaultPage(Math.max(0, nowPage - 1))
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .nightMode(isNight)
                .spacing(10));
        } catch (Exception e) {
            try {
                Log.e(TAG, "Error with direct URI load, trying file descriptor: " + e.getMessage(), e);
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    // Convert ParcelFileDescriptor to InputStream
                    FileInputStream fileInputStream = new FileInputStream(pfd.getFileDescriptor());
                    configurePdfView(pdfView.fromStream(fileInputStream)
                        .defaultPage(Math.max(0, nowPage - 1))
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .nightMode(isNight)
                        .spacing(10));
                } else {
                    Toast.makeText(this, "Cannot access file", Toast.LENGTH_LONG).show();
                    finish();
                }
            } catch (Exception ex) {
                Toast.makeText(this, "Error loading PDF: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error loading PDF from file descriptor: " + ex.getMessage(), ex);
                finish();
            }
        }
    }
    
    private void loadPdfWithPassword(String location, String password) throws Exception {
        if (location.startsWith("content://")) {
            Uri uri = Uri.parse(location);
            try {
                configurePdfView(pdfView.fromUri(uri)
                    .password(password)
                    .defaultPage(Math.max(0, nowPage - 1))
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .nightMode(isNight)
                    .spacing(10));
            } catch (Exception e) {
                // Try with file descriptor if URI method fails
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    // Convert ParcelFileDescriptor to InputStream
                    FileInputStream fileInputStream = new FileInputStream(pfd.getFileDescriptor());
                    configurePdfView(pdfView.fromStream(fileInputStream)
                        .password(password)
                        .defaultPage(Math.max(0, nowPage - 1))
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .nightMode(isNight)
                        .spacing(10));
                } else {
                    throw new IOException("Failed to open file descriptor");
                }
            }
        } else {
            File file = new File(location);
            configurePdfView(pdfView.fromFile(file)
                .password(password)
                .defaultPage(Math.max(0, nowPage - 1))
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .nightMode(isNight)
                .spacing(10));
        }
    }
    
    private void configurePdfView(PDFView.Configurator configurator) {
        Log.d(TAG, "Configuring PDF view with page: " + (nowPage > 0 ? nowPage - 1 : 0));
        
        configurator
            .onLoad(nbPages -> {
                Log.d(TAG, "PDF loaded with " + nbPages + " pages");
                if (pdfFile != null && pdfFile.getTotalPages() != nbPages) {
                    pdfFile.setTotalPages(nbPages);
                    // Update in database
                    if (pdfRepository != null) {
                        try {
                            pdfRepository.updateTotalPages(pdfFile.getLocation(), nbPages);
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating total pages in database", e);
                        }
                    }
                }
            })
            .onError(t -> {
                Log.e(TAG, "Error loading PDF in configurator", t);
                // Check if this is a password error
                if (
                    (t.getMessage() != null && t.getMessage().toLowerCase().contains("password"))) {
                    runOnUiThread(() -> promptUserForPassword(pdfFile.getLocation()));
                } else {
                    Toast.makeText(ReaderActivity.this, "Error loading PDF: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .onPageError((page, t) -> {
                Log.e(TAG, "Error loading page " + page, t);
                Toast.makeText(ReaderActivity.this, "Error loading page " + page, Toast.LENGTH_SHORT).show();
            })
            .scrollHandle(new com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle(this))
            .onPageChange((page, pageCount) -> {
                etCurrPage.setText(String.valueOf(page + 1));
                nowPage = page + 1;
                Log.d(TAG, "Page changed to: " + nowPage);
                
                // Update current page in database and send broadcast
                if (recyclerPosition != -1 && pdfRepository != null) {
                    // Update in-memory object
                    pdfFile.setCurrPage(nowPage);
                    
                    // Update database in background thread
                    new Thread(() -> {
                        try {
                            pdfRepository.updateCurrentPage(pdfFile.getLocation(), nowPage, System.currentTimeMillis());
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating current page", e);
                        }
                    }).start();
                    
                    // Send broadcast to update browser activity
                    Intent intent = new Intent(BrowserActivity.ACTION_PDF_UPDATED);
                    intent.putExtra("position", recyclerPosition);
                    intent.putExtra("path", pdfFile.getLocation());
                    sendBroadcast(intent);
                }
            })
            .onPageScroll((page, positionOffset) -> {
                // Get direction of scroll
                if (positionOffset > 0) {
                    // Scrolling down
                    if (barsVisible) {
                        hideBarsWithAnimation();
                        barsVisible = false;
                    }
                } else {
                    // Scrolling up
                    if (!barsVisible) {
                        showBarsWithAnimation();
                        barsVisible = true;
                    }
                }
            })
            .onTap(e -> {
                toggleBarsVisibility();
                return true;
            })
            .load();
    }

    private void promptUserForPassword(String location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.pdf_password_dialog, null);
        builder.setView(view);
        EditText passwordEditText = view.findViewById(R.id.etPassword);
        Button okButton = view.findViewById(R.id.submitPassword);
        Button cancelButton = view.findViewById(R.id.cancelPassword);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        
        // Show dialog and handle password input
        okButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString();
            try {
                loadPdfWithPassword(location, password);
                
                // Mark file as protected in memory and database
                if (pdfFile != null) {
                    pdfFile.setProtected(true);
                    if (pdfRepository != null) {
                        new Thread(() -> {
                            try {
                                pdfRepository.updateProtectedStatus(pdfFile.getLocation(), true);
                                Log.d(TAG, "Updated protected status in database for: " + pdfFile.getLocation());
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating protected status", e);
                            }
                        }).start();
                    }
                }
                
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid password", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error with password: " + e.getMessage(), e);
                // Keep dialog open for another attempt
            }
        });
        
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            finish(); // Close activity if user cancels password entry
        });
        
        dialog.show();
    }

    private void showJumpToPageDialog(int totalPage, int curPage) {
        JumpToPageFragment dialogFragment = new JumpToPageFragment(totalPage, curPage);
        dialogFragment.setJumpToPageListener(this);
        dialogFragment.show(getSupportFragmentManager(), "JumpToPageDialogFragment");
    }

    @Override
    public void onJumpToPage(int pageNumber) {
        pdfView.jumpTo(pageNumber - 1);
    }

    private void toggleBarsVisibility() {
        if (barsVisible) {
            hideBarsWithAnimation();
        } else {
            showBarsWithAnimation();
        }
        barsVisible = !barsVisible;
    }

    private void showBarsWithAnimation() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        topBar.animate().translationY(0).setDuration(300).start();
        bottomBar.animate().translationY(0).setDuration(300).start();
    }

    private void hideBarsWithAnimation() {
        topBar.animate().translationY(-topBar.getHeight() - 100).setDuration(300).start();
        bottomBar.animate().translationY(bottomBar.getHeight() + 100).setDuration(300).start();
    }

    ArrayList<String> extractTextFromPDF() {
        ArrayList<String> pages = new ArrayList<>();
        
        try {
            String location = pdfFile.getLocation();
            PdfReader reader;
            
            if (location.startsWith("content://")) {
                // Handle content URI
                Uri uri = Uri.parse(location);
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
                    reader = new PdfReader(inputStream);
                } else {
                    return pages;
                }
            } else {
                // Handle file path
                reader = new PdfReader(location);
            }
            
            PdfDocument pdfDocument = new PdfDocument(reader);
            int pageCount = pdfDocument.getNumberOfPages();
            
            for (int i = 1; i <= pageCount; i++) {
                PdfPage page = pdfDocument.getPage(i);
                String text = PdfTextExtractor.getTextFromPage(page);
                pages.add(text);
            }
            pdfDocument.close();
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Error extracting text from PDF: " + e.getMessage(), e);
        }
        
        return pages;
    }
    
    /**
     * Handle menu item selections
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_clear_thumbnails) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Clear Thumbnails");
            builder.setMessage("This will delete all cached thumbnails. PDF files will not be affected. Continue?");
            builder.setPositiveButton("Yes", (dialog, which) -> {
                dialog.dismiss();
                new Thread(() -> {
                    int count = com.taut0logy.readerforu.util.ThumbnailUtils.clearThumbnailCache(ReaderActivity.this);
                    runOnUiThread(() -> {
                        Toast.makeText(ReaderActivity.this, 
                            "Cleared " + count + " thumbnails", Toast.LENGTH_SHORT).show();
                        // Update the current PDF file's thumbnail path
                        if (pdfFile != null) {
                            pdfFile.setImagePath(null);
                            if (pdfRepository != null) {
                                pdfRepository.updateThumbnailPath(pdfFile.getLocation(), null);
                            }
                        }
                    });
                }).start();
            });
            builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
            builder.show();
            return true;
        } else if (id == R.id.action_about) {
            // Show about dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("About ReaderForU");
            builder.setMessage("ReaderForU is a PDF reader application developed for Android.");
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            builder.show();
            return true;
        } else if (id == R.id.action_exit) {
            finish();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void sharePdf() {
        String location = pdfFile.getLocation();
        Uri pdfUri;
        
        if (location.startsWith("content://")) {
            // Content URI - use as is
            pdfUri = Uri.parse(location);
        } else {
            // File path - use FileProvider
            File pdfFile = new File(location);
            pdfUri = FileProvider.getUriForFile(this, 
                    getApplicationContext().getPackageName() + ".provider", pdfFile);
        }
        
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.setType("application/pdf");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share PDF Via"));
    }

    /**
     * Handle various intent types, including "Open with" functionality
     */
    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        
        String action = intent.getAction();
        Uri uri = intent.getData();
        
        if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            // External app is opening the PDF via ACTION_VIEW
            try {
                // Take persistable URI permission if possible
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Log.d(TAG, "Got persistable permission for: " + uri);
                } catch (SecurityException e) {
                    Log.w(TAG, "Could not take persistable permission for: " + uri, e);
                    // We can still try to open the file even without persistable permission
                }
                
                // Check if this file exists in our database
                PDFFile existingFile = pdfRepository.getPDFFileByPath(uri.toString());

                // Not from recycler view
                if (existingFile != null) {
                    // We already have this file in our database
                    pdfFile = existingFile;
                } else {
                    // This is a new file, create a temporary PDFFile object for it
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd == null) {
                        Toast.makeText(this, "Cannot access file", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    
                    try {
                        // Extract PDF metadata
                        FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
                        PdfReader reader = new PdfReader(inputStream);
                        PdfDocument pdfDocument = new PdfDocument(reader);
                        
                        int pages = pdfDocument.getNumberOfPages();
                        PdfDocumentInfo info = pdfDocument.getDocumentInfo();
                        
                        String title = uri.getLastPathSegment();
                        if (info != null && info.getTitle() != null && !info.getTitle().isEmpty() && !info.getTitle().equals("null")) {
                            title = info.getTitle();
                        }
                        
                        String author = "Unknown";
                        if (info != null && info.getAuthor() != null && !info.getAuthor().isEmpty() && !info.getAuthor().equals("null")) {
                            author = info.getAuthor();
                        }
                        
                        String description = "No description";
                        if (info != null && info.getSubject() != null && !info.getSubject().isEmpty() && !info.getSubject().equals("null")) {
                            description = info.getSubject();
                        }
                        
                        pdfDocument.close();
                        reader.close();
                        
                        // Create a temporary PDFFile object
                        pdfFile = new PDFFile(title, author, description, uri.toString(), null, 
                                0, pages, false, System.currentTimeMillis(), System.currentTimeMillis());
                        
                        // Store this file in the database for future reference
                        pdfRepository.insertOrUpdatePDFFile(pdfFile);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading PDF metadata", e);
                        // Create a basic PDFFile with minimal info
                        String fileName = uri.getLastPathSegment();
                        if (fileName == null) fileName = "Unknown PDF";
                        pdfFile = new PDFFile(fileName, "Unknown", "No description", uri.toString(), 
                                null, 0, 0, false, System.currentTimeMillis(), System.currentTimeMillis());
                    } finally {
                        try {
                            pfd.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing file descriptor", e);
                        }
                    }

                }
                recyclerPosition = -1; // Not from recycler view
            } catch (Exception e) {
                Log.e(TAG, "Error handling external file", e);
                Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            // Normal internal app usage
            recyclerPosition = intent.getIntExtra("position", -1);
            String fileLocation = intent.getStringExtra("file_location");
            
            if (recyclerPosition >= 0 && recyclerPosition < BrowserActivity.getPdfFiles().size()) {
                // Get from BrowserActivity's static list
                pdfFile = BrowserActivity.getPdfFiles().get(recyclerPosition);
            } else if (fileLocation != null) {
                // Get by location
                pdfFile = pdfRepository.getPDFFileByPath(fileLocation);
            }
        }
    }
}
