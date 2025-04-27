package com.taut0logy.readerforu;

import android.annotation.SuppressLint;
// import android.content.BroadcastReceiver; // Removed unused import
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.taut0logy.readerforu.data.PDFFile;
import com.taut0logy.readerforu.data.PDFRepository;
import com.taut0logy.readerforu.util.CacheManager;
import com.taut0logy.readerforu.util.ThumbnailUtils;

// import org.json.JSONObject; // Removed unused import (due to cache removal)

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap; // Added import
import java.util.HashSet;
import java.util.List; // Added import
import java.util.Map; // Added import
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.activity.result.ActivityResultLauncher;
import androidx.documentfile.provider.DocumentFile;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class BrowserActivity extends AppCompatActivity {
    private static final String TAG = "BrowserActivity"; // Added TAG
    private static ArrayList<PDFFile> pdfFiles;
    private static ArrayList<PDFFile> filteredPdfFiles;
    private static ArrayList<PDFFile> favPdfFiles;
    private PDFFileAdapter pdfFileAdapter;
    private TextView statusText;
    private SharedPreferences sharedPreferences;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    // private static final String PDF_CACHE_KEY = "pdf_cache"; // Removed cache key
    private PDFRepository pdfRepository; // Added repository instance
    protected static boolean favouritesFirst = true;
    protected static int sortParameter = 1;
    protected static int sortDirection = 1;
    private boolean dataChanged = false; // Keep this flag for now, might be useful
    private boolean usingCache = false; // Flag to indicate if we're using cached data

    // Action strings for BroadcastReceivers
    public static final String ACTION_PDF_UPDATED = "com.taut0logy.readerforu.PDF_FILE_UPDATED";
    public static final String ACTION_PDF_DELETED = "com.taut0logy.readerforu.PDF_FILE_DELETED";

    // private BroadcastReceiver pdfUpdateReceiver; // Removed unused receiver
    private BroadcastReceiver pdfDeleteReceiver, pdfUpdateReceiver;

    private SwipeRefreshLayout swipeRefreshLayout;

    protected static ArrayList<PDFFile> getPdfFiles() {
        return pdfFiles;
    }
    protected static ArrayList<PDFFile> getFavPdfFiles() {
        return favPdfFiles;
    }
    protected static ArrayList<PDFFile> getFilteredPdfFiles() {
        return filteredPdfFiles;
    }

    private void applyTheme() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        String theme = sharedPreferences.getString("theme", "system"); // Default to system
        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default: // "system"
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private void saveAndApplyTheme(String theme) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("theme", theme);
        editor.apply();
        applyTheme(); // Apply immediately
        recreate(); // Recreate activity to apply theme fully
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        toolbar = findViewById(R.id.toolbar_browser);
        
        recyclerView = findViewById(R.id.recyclerView_browser);
        statusText = findViewById(R.id.tvStatus);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);

        pdfRepository = new PDFRepository(this); // Initialize repository
        try {
            pdfRepository.open(); // Open database
        } catch (SQLException e) {
            Log.e(TAG, "Error opening database", e);
            // Handle error appropriately, maybe show a message to the user
            statusText.setText("Error initializing database.");
            statusText.setVisibility(View.VISIBLE);
            return; // Cannot proceed without DB
        }

        pdfFiles = new ArrayList<>();
        filteredPdfFiles = new ArrayList<>();
        favPdfFiles = new ArrayList<>();
        setSupportActionBar(toolbar);
        favouritesFirst = sharedPreferences.getBoolean("favouritesFirst", true);
        sortParameter = sharedPreferences.getInt("sortParameter", 1);
        sortDirection = sharedPreferences.getInt("sortDirection", 1);

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
                if(id == R.id.theme_light) {
                    saveAndApplyTheme("light");
                    return true;
                }
                if(id == R.id.theme_dark) {
                    saveAndApplyTheme("dark");
                    return true;
                }
                if(id == R.id.theme_system) {
                    saveAndApplyTheme("system");
                    return true;
                }
                if(id == R.id.action_search) {
                    // Handled by SearchView listener in onCreateOptionsMenu
                    return true;
                }
                if(id == R.id.action_refresh) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Rescan PDF Files");
                    builder.setMessage("This will rescan storage and update the database. Continue?");
                    builder.setPositiveButton("Yes", (dialog, which) -> {
                        dialog.dismiss();
                        // Show refresh indicator
                        swipeRefreshLayout.setRefreshing(true);
                        refreshPdfFiles();
                    });
                    builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                    builder.show();
                    return true;
                }
                if(id == R.id.action_filter) {
                    showFilterDialog(BrowserActivity.this);
                    return true;
                }
                if(id == R.id.action_about) {
                    showAboutDialog(BrowserActivity.this);
                    return true;
                }
                if(id == R.id.action_clear_thumbnails) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Clear Thumbnails");
                    builder.setMessage("This will delete all cached thumbnails. PDF files will not be affected. Continue?");
                    builder.setPositiveButton("Yes", (dialog, which) -> {
                        dialog.dismiss();
                        new Thread(() -> {
                            int count = ThumbnailUtils.clearThumbnailCache(BrowserActivity.this);
                            runOnUiThread(() -> {
                                Toast.makeText(BrowserActivity.this,
                                    "Cleared " + count + " thumbnails", Toast.LENGTH_SHORT).show();
                                // Refresh adapter to show default icons
                                pdfFileAdapter.notifyDataSetChanged();
                            });
                        }).start();
                    });
                    builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                    builder.show();
                    return true;
                }
                if(id == R.id.action_exit) {
                    finish();
                    System.exit(0);
                    return true;
                }
                if (id == R.id.action_select_folders) {
                    launchDirectoryPicker();
                    return true;
                }
                if (id == R.id.action_manage_folders) {
                    showManageFoldersDialog();
                    return true;
                }
                return false;
        });

        pdfFileAdapter = new PDFFileAdapter(pdfFiles, filteredPdfFiles, this);
        recyclerView.setAdapter(pdfFileAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // Ensure layout manager is set

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this::refreshPdfFiles);
        swipeRefreshLayout.setColorSchemeResources(
            R.color.blue_primary_light,
            R.color.blue_secondary_light
        );

        // Load pdf files using cache if available, otherwise scan folders
        loadPDFFilesWithCache();

        setupReceivers();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showFilterDialog(Context context) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
            builder.setView(dialogView);

            CheckBox checkBoxFavorites = dialogView.findViewById(R.id.checkBoxFavourites);
            RadioGroup radioGroupSortingParameters = dialogView.findViewById(R.id.radioGroupSortingParameters);
            RadioGroup radioGroupSortingOrder = dialogView.findViewById(R.id.radioGroupSortingOrder);
            Button buttonApply = dialogView.findViewById(R.id.buttonApply);
            Button buttonCancel = dialogView.findViewById(R.id.buttonCancel);

            AlertDialog dialog = builder.create();
            if(favouritesFirst)
                checkBoxFavorites.setChecked(true);
            switch (sortParameter) {
                case 1:
                    radioGroupSortingParameters.check(R.id.radioButtonTitle);
                    break;
                case 2:
                    radioGroupSortingParameters.check(R.id.radioButtonAuthor);
                    break;
                case 3:
                    radioGroupSortingParameters.check(R.id.radioButtonModified);
                    break;
                case 4:
                    radioGroupSortingParameters.check(R.id.radioButtonLastRead);
                    break;
                default:
                    radioGroupSortingParameters.check(R.id.radioButtonTitle);
            }
            if(sortDirection == 1)
                radioGroupSortingOrder.check(R.id.radioButtonAscending);
            else
                radioGroupSortingOrder.check(R.id.radioButtonDescending);

            buttonApply.setOnClickListener(v -> {
                favouritesFirst = checkBoxFavorites.isChecked();
                int selectedSortingParameterId = radioGroupSortingParameters.getCheckedRadioButtonId();
                if(selectedSortingParameterId == R.id.radioButtonTitle)
                    sortParameter = 1;
                else if(selectedSortingParameterId == R.id.radioButtonAuthor)
                    sortParameter = 2;
                else if(selectedSortingParameterId == R.id.radioButtonModified)
                    sortParameter = 3;
                else if(selectedSortingParameterId == R.id.radioButtonLastRead)
                    sortParameter = 4;
                else sortParameter = 1;
                int selectedSortingOrderId = radioGroupSortingOrder.getCheckedRadioButtonId();
                if(selectedSortingOrderId == R.id.radioButtonAscending)
                    sortDirection = 1;
                else if(selectedSortingOrderId == R.id.radioButtonDescending)
                    sortDirection = -1;
                else sortDirection = 1;
                pdfFiles.sort(this::comparePDFFiles);
                filteredPdfFiles.sort(this::comparePDFFiles);
                pdfFileAdapter.notifyDataSetChanged();
                dialog.dismiss();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("sortParameter", sortParameter);
                editor.putInt("sortDirection", sortDirection);
                editor.putBoolean("favouritesFirst", favouritesFirst);
                editor.apply();
            });
            buttonCancel.setOnClickListener(v -> dialog.dismiss());

            dialog.show();
        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) item.getActionView();
        assert searchView != null;
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint("Search Titles/Authors");
        searchView.clearFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                pdfFileAdapter.getFilter().filter(newText);
                return false;
            }
        });
        
        // Find action_refresh menu item and update its behavior
        MenuItem refreshItem = menu.findItem(R.id.action_refresh);
        if (refreshItem != null) {
            refreshItem.setOnMenuItemClickListener(item2 -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Rescan PDF Files");
                builder.setMessage("This will rescan storage and update the database. Continue?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    dialog.dismiss();
                    // Clear the cache first
                    CacheManager.clearCache(this);
                    // Show refresh indicator
                    swipeRefreshLayout.setRefreshing(true);
                    refreshPdfFiles();
                });
                builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                builder.show();
                return true;
            });
        }
        
        // Handle rebuild thumbnails menu item
        MenuItem rebuildThumbnailsItem = menu.findItem(R.id.action_rebuild_thumbnails);
        if (rebuildThumbnailsItem != null) {
            rebuildThumbnailsItem.setOnMenuItemClickListener(item3 -> {
                showRebuildThumbnailsDialog();
                return true;
            });
        }
        
        // Handle clear thumbnails (also clear cache)
        MenuItem clearThumbnailsItem = menu.findItem(R.id.action_clear_thumbnails);
        if (clearThumbnailsItem != null) {
            clearThumbnailsItem.setOnMenuItemClickListener(item4 -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Clear Thumbnails");
                builder.setMessage("This will delete all cached thumbnails and PDF list cache. PDF files will not be affected. Continue?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    dialog.dismiss();
                    new Thread(() -> {
                        // Clear PDF list cache
                        CacheManager.clearCache(this);
                        // Clear thumbnails
                        int count = ThumbnailUtils.clearThumbnailCache(BrowserActivity.this);
                        runOnUiThread(() -> {
                            Toast.makeText(BrowserActivity.this,
                                "Cleared " + count + " thumbnails and PDF cache", Toast.LENGTH_SHORT).show();
                            // Refresh adapter to show default icons
                            pdfFileAdapter.notifyDataSetChanged();
                        });
                    }).start();
                });
                builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                builder.show();
                return true;
            });
        }
        
        return true;
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if (id == R.id.action_select_folders) {
//            launchDirectoryPicker();
//            return true;
//        } else if (id == R.id.action_manage_folders) {
//            showManageFoldersDialog();
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
    
    private final ActivityResultLauncher<Intent> directoryPickerLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri dirUri = result.getData().getData();
                    if (dirUri != null) {
                        // Take persistable URI permission
                        try {
                            getContentResolver().takePersistableUriPermission(dirUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            Log.d(TAG, "Got permission for directory: " + dirUri);
                            
                            // Save this folder URI
                            savePdfFolder(dirUri.toString());
                            
                            // Scan the selected directory for PDFs
                            scanSafDirectory(dirUri);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to take persistable permission", e);
                            Toast.makeText(this, "Failed to get permission for this folder", 
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
    
    private void launchDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                       Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        directoryPickerLauncher.launch(intent);
    }
    
    private void savePdfFolder(String folderUri) {
        SharedPreferences prefs = getSharedPreferences("ReaderForUPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Get current folders
        String foldersString = prefs.getString("pdf_folders", "");
        List<String> folders = new ArrayList<>();
        
        if (!foldersString.isEmpty()) {
            folders = new ArrayList<>(Arrays.asList(foldersString.split(",")));
        }
        
        // Add new folder if not already in list
        if (!folders.contains(folderUri)) {
            folders.add(folderUri);
            editor.putString("pdf_folders", String.join(",", folders));
            editor.apply();
            Log.d(TAG, "Saved new PDF folder: " + folderUri);
        }
    }
    
    private void removePdfFolder(String folderUri) {
        SharedPreferences prefs = getSharedPreferences("ReaderForUPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Get current folders
        String foldersString = prefs.getString("pdf_folders", "");
        List<String> folders = new ArrayList<>();
        
        if (!foldersString.isEmpty()) {
            folders = new ArrayList<>(Arrays.asList(foldersString.split(",")));
        }
        
        // Remove the folder
        if (folders.remove(folderUri)) {
            editor.putString("pdf_folders", String.join(",", folders));
            editor.apply();
            
            // Release the permission
            try {
                getContentResolver().releasePersistableUriPermission(
                        Uri.parse(folderUri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                Log.e(TAG, "Error releasing permission: " + e.getMessage());
            }
            
            Log.d(TAG, "Removed PDF folder: " + folderUri);
            Toast.makeText(this, "Folder removed", Toast.LENGTH_SHORT).show();
        }
    }
    
    private List<String> getSavedPdfFolders() {
        SharedPreferences prefs = getSharedPreferences("ReaderForUPrefs", MODE_PRIVATE);
        String foldersString = prefs.getString("pdf_folders", "");
        
        if (foldersString.isEmpty()) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(Arrays.asList(foldersString.split(",")));
    }
    
    private void showManageFoldersDialog() {
        List<String> folders = getSavedPdfFolders();
        
        if (folders.isEmpty()) {
            Toast.makeText(this, "No PDF folders added yet", Toast.LENGTH_SHORT).show();
            launchDirectoryPicker();
            return;
        }
        
        // Convert folder URIs to readable names for the dialog
        Map<String, String> folderMap = new HashMap<>(); // Uri string to display name
        List<String> folderDisplayNames = new ArrayList<>();
        
        for (String folderUri : folders) {
            try {
                Uri uri = Uri.parse(folderUri);
                DocumentFile docFile = DocumentFile.fromTreeUri(this, uri);
                String displayName = docFile != null && docFile.getName() != null ? 
                        docFile.getName() : "Unknown folder";
                        
                folderMap.put(folderUri, displayName);
                folderDisplayNames.add(displayName);
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting folder name: " + e.getMessage());
                folderMap.put(folderUri, "Invalid folder");
                folderDisplayNames.add("Invalid folder");
            }
        }
        
        // Create a dialog to show and manage folders
        String[] folderArray = folderDisplayNames.toArray(new String[0]);
        
        new AlertDialog.Builder(this)
            .setTitle("Manage PDF Folders")
            .setItems(folderArray, (dialog, which) -> {
                String selectedFolderUri = folders.get(which);
                String folderName = folderMap.get(selectedFolderUri);
                
                new AlertDialog.Builder(this)
                    .setTitle(folderName)
                    .setItems(new String[]{"Scan for PDFs", "Remove folder"}, (innerDialog, innerWhich) -> {
                        if (innerWhich == 0) {
                            // Scan folder
                            scanSafDirectory(Uri.parse(selectedFolderUri));
                        } else if (innerWhich == 1) {
                            // Remove folder
                            new AlertDialog.Builder(this)
                                .setTitle("Remove Folder")
                                .setMessage("Are you sure you want to remove this folder? " +
                                           "PDFs from this location will no longer be accessible.")
                                .setPositiveButton("Remove", (confirmDialog, confirmWhich) -> {
                                    removePdfFolder(selectedFolderUri);
                                    loadPDFFiles(); // Refresh the list
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                    })
                    .show();
            })
            .setPositiveButton("Add Folder", (dialog, which) -> {
                launchDirectoryPicker();
            })
            .setNegativeButton("Close", null)
            .show();
    }

    /**
     * Loads PDF files with cache optimization
     */
    private void loadPDFFilesWithCache() {
        // Show loading message
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(R.string.loading_pdf_files);
        
        // Get saved folders list to check cache validity
        List<String> foldersList = getSavedPdfFolders();
        
        // Try to load from cache first
        if (CacheManager.isCacheValid(this, foldersList)) {
            new Thread(() -> {
                List<PDFFile> cachedFiles = CacheManager.loadPdfListFromCache(this);
                if (cachedFiles != null && !cachedFiles.isEmpty()) {
                    usingCache = true;
                    
                    // Update our lists
                    pdfFiles.clear();
                    pdfFiles.addAll(cachedFiles);
                    
                    // Update favorites list
                    favPdfFiles.clear();
                    for (PDFFile file : pdfFiles) {
                        if (file.getFavourite()) {
                            favPdfFiles.add(file);
                        }
                    }
                    
                    // Sort according to preferences
                    pdfFiles.sort(this::comparePDFFiles);
                    
                    // Update filtered list and UI
                    runOnUiThread(() -> {
                        filteredPdfFiles.clear();
                        filteredPdfFiles.addAll(pdfFiles);
                        pdfFileAdapter.notifyDataSetChanged();
                        updateStatusTextVisibility();
                        Log.d(TAG, "Loaded " + pdfFiles.size() + " PDF files from cache");
                    });
                } else {
                    // Cache failed to load, do a regular scan
                    usingCache = false;
                    runOnUiThread(() -> {
                        Log.d(TAG, "Cache failed to load, scanning folders");
                        loadPDFFiles();
                    });
                }
            }).start();
        } else {
            // Cache is invalid, do a regular scan
            Log.d(TAG, "Cache is invalid or missing, scanning folders");
            new Thread(this::loadPDFFiles).start();
        }
    }

    private void loadPDFFiles() {
        Log.i(TAG, "Starting loadPDFFiles using Storage Access Framework");
        Set<String> currentFilePaths = new HashSet<>(); // Keep track of paths found in this scan
        dataChanged = false; // Reset data changed flag

        // Ensure database is open in this thread
        try {
            if (pdfRepository != null) {
                pdfRepository.open();
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error opening database in loadPDFFiles thread", e);
            runOnUiThread(() -> {
                statusText.setText("Database error. Please restart the app.");
                statusText.setVisibility(View.VISIBLE);
            });
            return;
        }

        // Clear existing lists
        synchronized (pdfFiles) {
            pdfFiles.clear();
            filteredPdfFiles.clear();
            favPdfFiles.clear();
        }
        
        // Update UI to show loading
        runOnUiThread(() -> {
            statusText.setText(R.string.loading_pdf_files);
            statusText.setVisibility(View.VISIBLE);
        });
        
        // Load existing files from database first
        ArrayList<PDFFile> dbFiles = new ArrayList<>(pdfRepository.getAllPDFFiles());
        Log.d(TAG, "Loaded " + dbFiles.size() + " files initially from database.");

        // Load saved PDF folders
        List<String> savedFolders = getSavedPdfFolders();
        if (savedFolders.isEmpty()) {
            Log.d(TAG, "No PDF folders saved yet. Please add folders first.");
            runOnUiThread(() -> {
                statusText.setText("No PDF folders added. Use 'Add PDF Folder' from the menu.");
                statusText.setVisibility(View.VISIBLE);
                
                // If this is the first time and no folders, offer to add one
                SharedPreferences prefs = getSharedPreferences("ReaderForUPrefs", MODE_PRIVATE);
                boolean firstTimeNoFolders = !prefs.contains("first_time_no_folders");
                if (firstTimeNoFolders) {
                    new Handler().postDelayed(() -> {
                        // Show dialog after a short delay
                        new AlertDialog.Builder(this)
                            .setTitle("Add PDF Folder")
                            .setMessage("You need to add folders containing your PDF files to get started.")
                            .setPositiveButton("Add Folder", (dialog, which) -> {
                                launchDirectoryPicker();
                            })
                            .setNegativeButton("Later", null)
                            .show();
                            
                            // Mark as shown
                            prefs.edit().putBoolean("first_time_no_folders", true).apply();
                        }, 500);
                }
            });
            return;
        }
        
        // Use a temp list to collect all files before updating the main list
        ArrayList<PDFFile> foundFiles = new ArrayList<>();
        AtomicInteger foldersToProcess = new AtomicInteger(savedFolders.size());
        AtomicInteger totalPdfsFound = new AtomicInteger(0);
        
        // Process each folder
        for (String folderUri : savedFolders) {
            Uri uri = Uri.parse(folderUri);
            DocumentFile directory = DocumentFile.fromTreeUri(this, uri);
            
            if (directory == null || !directory.exists() || !directory.canRead()) {
                Log.e(TAG, "Cannot access folder: " + folderUri);
                foldersToProcess.decrementAndGet();
                
                // If all folders processed, update UI
                if (foldersToProcess.get() == 0) {
                    finalizePdfLoading(foundFiles, currentFilePaths, dbFiles);
                }
                continue;
            }
            
            // Start scanning this folder
            String folderName = directory.getName() != null ? directory.getName() : "Unknown";
            Log.d(TAG, "Scanning folder: " + folderName);
            
            // Process the folder in a separate thread
            new Thread(() -> {
                // Ensure database is open in this thread too
                try {
                    if (pdfRepository != null) {
                        pdfRepository.open();
                    }
                } catch (SQLException e) {
                    Log.e(TAG, "Error opening database in folder scanning thread", e);
                    return;
                }
                
                // Find PDFs in this folder
                processDocumentFolder(directory, foundFiles, currentFilePaths, totalPdfsFound);
                
                // Check if all folders have been processed
                int remaining = foldersToProcess.decrementAndGet();
                if (remaining == 0) {
                    Log.d(TAG, "All folders processed, found " + totalPdfsFound.get() + " PDFs");
                    finalizePdfLoading(foundFiles, currentFilePaths, dbFiles);
                }
            }).start();
        }
    }
    
    /**
     * This method finalizes the PDF loading process, sorting and updating UI
     */
    private void finalizePdfLoading(ArrayList<PDFFile> foundFiles, Set<String> currentFilePaths, 
                                   ArrayList<PDFFile> dbFiles) {
        // Update the main lists with found files
        synchronized (pdfFiles) {  // Add synchronization here too
            pdfFiles.clear();
            pdfFiles.addAll(foundFiles);
            
            // Update favorites list
            favPdfFiles.clear();
            for (PDFFile file : pdfFiles) {
                if (file.getFavourite()) {
                    favPdfFiles.add(file);
                }
            }

            // Remove files from database that no longer exist in any folders
            for (PDFFile dbFile : dbFiles) {
                if (!currentFilePaths.contains(dbFile.getLocation())) {
                    Log.d(TAG, "Removing stale file from database: " + dbFile.getLocation());
                    pdfRepository.deletePDFFile(dbFile.getLocation());
                    dataChanged = true;
                }
            }

            // Sort files according to user preferences
            pdfFiles.sort(this::comparePDFFiles);
            
            // Create filtered list
            filteredPdfFiles.clear();
            filteredPdfFiles.addAll(pdfFiles);
        }
        
        Log.i(TAG, "Finished loading PDFs. Final count: " + pdfFiles.size());
        
        // Cache the PDF list for faster loading next time
        List<String> foldersList = getSavedPdfFolders();
        if (!pdfFiles.isEmpty()) {
            CacheManager.savePdfListToCache(this, pdfFiles, foldersList);
        }

        // Update UI on the main thread
        runOnUiThread(() -> {
            updateStatusTextVisibility();
            if (pdfFileAdapter != null) {
                pdfFileAdapter.notifyDataSetChanged();
                Log.d(TAG, "UI updated. Adapter count: " + pdfFileAdapter.getItemCount());
            } else {
                Log.e(TAG, "pdfFileAdapter is null, cannot update UI");
            }
        });
    }
    
    private void processDocumentFolder(DocumentFile folder, ArrayList<PDFFile> foundFiles, 
                                      Set<String> currentFilePaths, AtomicInteger totalFound) {
        if (folder == null || !folder.exists() || !folder.isDirectory() || !folder.canRead()) {
            return;
        }
        
        // List all files and folders
        DocumentFile[] contents = folder.listFiles();
        
        // Process each item
        for (DocumentFile item : contents) {
            if (item.isDirectory()) {
                // Recursive scan of subfolders
                processDocumentFolder(item, foundFiles, currentFilePaths, totalFound);
            } else if (item.isFile() && item.getName() != null && 
                      item.getName().toLowerCase().endsWith(".pdf")) {
                // Found a PDF file
                processPdfFile(item, foundFiles, currentFilePaths, totalFound);
            }
        }
    }
    
    private void processPdfFile(DocumentFile pdfFile, ArrayList<PDFFile> foundFiles, 
                               Set<String> currentFilePaths, AtomicInteger totalFound) {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.canRead()) {
            return;
        }
        
        String name = pdfFile.getName() != null ? pdfFile.getName() : "Unknown PDF";
        Uri uri = pdfFile.getUri();
        String path = uri.toString(); // Use URI as the unique identifier
        long size = pdfFile.length();
        long lastModified = pdfFile.lastModified();
        
        // Update running count
        int count = totalFound.incrementAndGet();
        runOnUiThread(() -> statusText.setText("Found " + count + " PDF files"));
        
        // Add to current paths set
        synchronized (currentFilePaths) {
            currentFilePaths.add(path);
        }
        
        // Check if already in database
        PDFFile existingFile = pdfRepository.getPDFFileByPath(path);
        if (existingFile != null) {
            // Update if needed
            boolean updated = false;
            
            if (!Objects.equals(existingFile.getName(), name)) {
                existingFile.setName(name);
                updated = true;
            }
            
            if (existingFile.getSize() != size) {
                existingFile.setSize(size);
                updated = true;
            }
            
            if (existingFile.getLastModified() != lastModified) {
                existingFile.setLastModified(lastModified);
                updated = true;
            }
            
            if (updated) {
                pdfRepository.insertOrUpdatePDFFile(existingFile);
            }
            
            // Always update content URI in case it changed
            existingFile.setContentUri(uri.toString());
            
            // Add to found files list
            synchronized (foundFiles) {
                foundFiles.add(existingFile);
            }
        } else {
            // Create new PDFFile
            String author = "Unknown Author";
            String description = "No Description";
            int pageCount = 0;
            
            // Try to get metadata
            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) {
                    try {
                        com.itextpdf.kernel.pdf.PdfReader reader = 
                            new com.itextpdf.kernel.pdf.PdfReader(new FileInputStream(pfd.getFileDescriptor()));
                        com.itextpdf.kernel.pdf.PdfDocument document = 
                            new com.itextpdf.kernel.pdf.PdfDocument(reader);
                            
                        pageCount = document.getNumberOfPages();
                        
                        com.itextpdf.kernel.pdf.PdfDocumentInfo info = document.getDocumentInfo();
                        if (info != null) {
                            if (info.getAuthor() != null && !info.getAuthor().isEmpty() && 
                                !info.getAuthor().equals("null")) {
                                author = info.getAuthor();
                            }
                            if (info.getSubject() != null && !info.getSubject().isEmpty() && 
                                !info.getSubject().equals("null")) {
                                description = info.getSubject();
                            }
                        }
                        
                        document.close();
                        reader.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading PDF metadata for " + name + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening PDF file: " + e.getMessage());
            }
            
            PDFFile newFile = new PDFFile(name, author, description, path, null, 
                            0, pageCount, false, System.currentTimeMillis(), lastModified);
            newFile.setSize(size);
            newFile.setContentUri(uri.toString());
            
            // Generate thumbnail during first scan
            String thumbnailPath = ThumbnailUtils.generateThumbnailForFirstScan(this, path);
            if (thumbnailPath != null) {
                newFile.setImagePath(thumbnailPath);
            }
            
            // Save to database
            PDFFile savedFile = pdfRepository.insertOrUpdatePDFFile(newFile);
            
            // Add to found files list
            synchronized (foundFiles) {
                if (savedFile != null) {
                    foundFiles.add(savedFile);
                } else {
                    foundFiles.add(newFile);
                }
            }
        }
    }
    
    // Scan a selected directory for PDFs
    private void scanSafDirectory(Uri directoryUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Scanning Folder...");
        builder.setMessage("Looking for PDF files in the selected folder...");
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, 0, 30);
        builder.setView(progressBar);
        builder.setCancelable(false);
        AlertDialog progressDialog = builder.create();
        progressDialog.show();
        
        new Thread(() -> {
            // Ensure database is open in this thread
            try {
                if (pdfRepository != null) {
                    pdfRepository.open();
                }
            } catch (SQLException e) {
                Log.e(TAG, "Error opening database in scan thread", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Database error. Please restart the app.", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            
            ArrayList<PDFFile> foundFiles = new ArrayList<>();
            Set<String> foundPaths = new HashSet<>();
            AtomicInteger count = new AtomicInteger(0);
            
            DocumentFile directory = DocumentFile.fromTreeUri(this, directoryUri);
            if (directory != null && directory.exists() && directory.canRead()) {
                processDocumentFolder(directory, foundFiles, foundPaths, count);
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    int filesFound = count.get();
                    if (filesFound > 0) {
                        // Add found files to the main lists
                        updateFilesFromScan(foundFiles);
                        Toast.makeText(this, "Found " + filesFound + " PDF files", 
                                       Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No PDF files found in the selected folder", 
                                       Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Cannot access the selected folder", 
                                   Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void updateFilesFromScan(ArrayList<PDFFile> newFiles) {
        // Add any new files to the main list
        boolean filesAdded = false;
        
        synchronized (pdfFiles) {  // Add synchronization here too
            for (PDFFile file : newFiles) {
                if (!pdfFiles.contains(file)) {
                    pdfFiles.add(file);
                    if (file.getFavourite()) {
                        favPdfFiles.add(file);
                    }
                    filesAdded = true;
                }
            }
            
            if (filesAdded) {
                // Sort and update UI
                pdfFiles.sort(this::comparePDFFiles);
                filteredPdfFiles.clear();
                filteredPdfFiles.addAll(pdfFiles);
                
                updateStatusTextVisibility();
                pdfFileAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        // If data changed while app was running, update the cache
        if (dataChanged && !pdfFiles.isEmpty()) {
            List<String> foldersList = getSavedPdfFolders();
            CacheManager.savePdfListToCache(this, pdfFiles, foldersList);
            dataChanged = false;
        }
        
        // Don't close database here as background threads might still be using it
        // We'll manage database connections directly in the threads that need them
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Ensure database is open in the main thread when activity starts
        try {
            if (pdfRepository != null) {
                pdfRepository.open();
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error opening database in onStart", e);
            Toast.makeText(this, "Database error. Please restart the app.", Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private static AlertDialog.Builder getBuilder(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("About ReaderForU");
        String version = "1.2";
        builder.setMessage(
                "ReaderForU is a simple PDF reader app.\n" +
                "Developed by Raufun Ahsan\n" +
                "Version" + version + "\n" +
                "© 2024 Taut0logy\n" +
                "All rights reserved."
        );
        builder.setCancelable(true);
        builder.setPositiveButton("GitHub", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.github.com/Taut0logy"));
            context.startActivity(intent);
        });
        return builder;
    }

    public static void showAboutDialog(Context context) {
        AlertDialog.Builder builder = getBuilder(context);
        builder.setNegativeButton("Facebook", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.facebook.com/raufun.ahsan"));
            context.startActivity(intent);
        });
        builder.setNeutralButton("Email", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:raufun.ahsan@gmail.com"));
            context.startActivity(intent);
        });
        builder.create().show();
    }

    

    // Removed unused pdfFileUpdatedReceiver
    // private BroadcastReceiver pdfFileUpdatedReceiver = new BroadcastReceiver() { ... };

    // Removed unused SharedPreferences cache methods
    // private boolean isPdfCacheAvailable() { ... }
    // private void savePDFFilesToCache() { ... }
    // private void loadCachedPDFFiles() { ... }

    private void updateStatusTextVisibility() {
        if (pdfFiles.isEmpty()) {
            statusText.setText(R.string.no_pdf_files_found);
            statusText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            statusText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Load existing thumbnail or generate a new one for a PDF file
     * @param pdfFile The PDF file to process
     */
    private void loadOrGenerateThumbnail(PDFFile pdfFile) {
        if (pdfFile == null || pdfFile.getLocation() == null) {
            return;
        }
        
        // Don't generate thumbnails for protected files
        if (pdfFile.isProtected() || "__protected".equals(pdfFile.getImagePath())) {
            return;
        }

        // Check if thumbnail already exists in memory
        if (pdfFile.getThumbnail() != null) {
            return; // Already loaded
        }
        
        // Use the generateThumbnail method which now handles caching internally
        Bitmap thumbnail = ThumbnailUtils.generateThumbnail(this, pdfFile.getLocation());
        if (thumbnail != null) {
            // Get the path using our utility method
            String thumbnailPath = ThumbnailUtils.getThumbnailPath(this, pdfFile.getLocation());
            pdfFile.setImagePath(thumbnailPath);
            pdfFile.setThumbnail(thumbnail);
            
            // Update the database with the thumbnail path
            pdfRepository.updateThumbnailPath(pdfFile.getLocation(), thumbnailPath);
            Log.d(TAG, "Thumbnail set for: " + pdfFile.getName());
        }
    }

    // Helper method to re-apply sorting and notify adapter
    @SuppressLint("NotifyDataSetChanged")
    private void sortAndNotifyAdapter() {
        synchronized (pdfFiles) {  // Add synchronization for sorting
            // Apply sorting using the comparator
            pdfFiles.sort(this::comparePDFFiles);
            // The filter method already updates filteredPdfFiles, but we sort it too for consistency if needed elsewhere
            filteredPdfFiles.sort(this::comparePDFFiles); 
        }

        // The filter call below will internally call notifyDataSetChanged
        runOnUiThread(() -> {
            pdfFileAdapter.getFilter().filter(getCurrentSearchQuery()); // Re-apply filter which also notifies
            updateStatusTextVisibility();
        });
    }

    public int comparePDFFiles(PDFFile pdfFile1, PDFFile pdfFile2) {
        if(favouritesFirst) {
            if(pdfFile1.getFavourite() && !pdfFile2.getFavourite()) {
                return -1;
            } else if(!pdfFile1.getFavourite() && pdfFile2.getFavourite()) {
                return 1;
            }
        }
        switch (sortParameter) {
            case 1:
                return pdfFile1.getName().toLowerCase().compareTo(pdfFile2.getName().toLowerCase())*sortDirection;
            case 2:
                return pdfFile1.getAuthor().toLowerCase().compareTo(pdfFile2.getAuthor().toLowerCase())*sortDirection;
            case 3: {
                long diff = pdfFile1.getModified() - pdfFile2.getModified();
                if(diff == 0)
                    return 0;
                else if(diff > 0)
                    return -1*sortDirection;
                else
                    return sortDirection;
                //return (int) (pdfFile1.getModified() - pdfFile2.getModified()) * sortDirection;
            }
            case 4: {
                if(pdfFile1.getLastRead() == pdfFile2.getLastRead())
                    return 0;
                else if(pdfFile1.getLastRead() > pdfFile2.getLastRead())
                    return -1*sortDirection;
                else
                    return sortDirection;
                //return (int) (pdfFile2.getLastRead() - pdfFile1.getLastRead()) * sortDirection;
            }
            default:
                return 0;
        }
    }

    private void setupReceivers() {
        pdfUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int position = intent.getIntExtra("position", -1);
                String filePath = intent.getStringExtra("path");
                Log.d("Receiver", "Received PDF_FILE_UPDATED broadcast for position: " + position);
                
                synchronized (pdfFiles) {
                    // Try to find file by position first
                    if (position != -1 && position < pdfFiles.size()) {
                        PDFFile file = pdfFiles.get(position);
                        
                        // If we have the file path, ensure we're updating the correct file
                        if (filePath != null && !file.getLocation().equals(filePath)) {
                            // Position doesn't match the path - find the file by path instead
                            position = -1;
                            for (int i = 0; i < pdfFiles.size(); i++) {
                                if (pdfFiles.get(i).getLocation().equals(filePath)) {
                                    position = i;
                                    file = pdfFiles.get(i);
                                    break;
                                }
                            }
                        }
                        
                        if (position != -1) {
                            // If we need to fetch fresh data from database
                            boolean needsDatabaseRefresh = false;
                            
                            // Check if we have a path but no position match
                            if (filePath != null && position == -1) {
                                needsDatabaseRefresh = true;
                            }
                            
                            if (needsDatabaseRefresh) {
                                // Get the updated file from database
                                PDFFile updatedFile = pdfRepository.getPDFFileByPath(filePath);
                                if (updatedFile != null) {
                                    // Find where to update in our lists
                                    for (int i = 0; i < pdfFiles.size(); i++) {
                                        if (pdfFiles.get(i).getLocation().equals(filePath)) {
                                            // Update the file in the main list
                                            pdfFiles.set(i, updatedFile);
                                            
                                            // Update the filtered list if present
                                            for (int j = 0; j < filteredPdfFiles.size(); j++) {
                                                if (filteredPdfFiles.get(j).getLocation().equals(filePath)) {
                                                    filteredPdfFiles.set(j, updatedFile);
                                                    position = j; // Update position for adapter
                                                    break;
                                                }
                                            }
                                            
                                            // Update favorites list if needed
                                            if (updatedFile.getFavourite()) {
                                                boolean inFavorites = false;
                                                for (int j = 0; j < favPdfFiles.size(); j++) {
                                                    if (favPdfFiles.get(j).getLocation().equals(filePath)) {
                                                        favPdfFiles.set(j, updatedFile);
                                                        inFavorites = true;
                                                        break;
                                                    }
                                                }
                                                if (!inFavorites) {
                                                    favPdfFiles.add(updatedFile);
                                                }
                                            } else {
                                                // Remove from favorites if it was unfavorited
                                                favPdfFiles.removeIf(pdf -> pdf.getLocation().equals(filePath));
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            // Notify the adapter
                            final int finalPosition = position;
                            runOnUiThread(() -> {
                                if (finalPosition != -1) {
                                    pdfFileAdapter.notifyItemChanged(finalPosition);
                                } else {
                                    // If we couldn't determine the position, update everything
                                    pdfFileAdapter.notifyDataSetChanged();
                                }
                                Log.d("Receiver", "Adapter notified for update");
                            });
                        }
                    } else if (filePath != null) {
                        // Try to find the file by path if position is invalid
                        PDFFile updatedFile = pdfRepository.getPDFFileByPath(filePath);
                        if (updatedFile != null) {
                            boolean fileFound = false;
                            
                            // Find and update in main list
                            for (int i = 0; i < pdfFiles.size(); i++) {
                                if (pdfFiles.get(i).getLocation().equals(filePath)) {
                                    pdfFiles.set(i, updatedFile);
                                    fileFound = true;
                                    position = i;
                                    break;
                                }
                            }
                            
                            if (fileFound) {
                                // Update in filtered list
                                for (int i = 0; i < filteredPdfFiles.size(); i++) {
                                    if (filteredPdfFiles.get(i).getLocation().equals(filePath)) {
                                        filteredPdfFiles.set(i, updatedFile);
                                        position = i; // Update position for adapter
                                        break;
                                    }
                                }
                                
                                // Handle favorites list
                                if (updatedFile.getFavourite()) {
                                    boolean inFavorites = false;
                                    for (int i = 0; i < favPdfFiles.size(); i++) {
                                        if (favPdfFiles.get(i).getLocation().equals(filePath)) {
                                            favPdfFiles.set(i, updatedFile);
                                            inFavorites = true;
                                            break;
                                        }
                                    }
                                    if (!inFavorites) {
                                        favPdfFiles.add(updatedFile);
                                    }
                                } else {
                                    // Remove from favorites if it was unfavorited
                                    favPdfFiles.removeIf(pdf -> pdf.getLocation().equals(filePath));
                                }
                                
                                final int finalPosition = position;
                                runOnUiThread(() -> {
                                    if (finalPosition != -1) {
                                        pdfFileAdapter.notifyItemChanged(finalPosition);
                                    } else {
                                        // If we couldn't determine the position, update everything
                                        pdfFileAdapter.notifyDataSetChanged();
                                    }
                                    Log.d("Receiver", "Adapter notified for update via path");
                                });
                            } else {
                                Log.w("Receiver", "File found in database but not in memory lists: " + filePath);
                            }
                        } else {
                            Log.w("Receiver", "File not found in database: " + filePath);
                        }
                    } else {
                        Log.w("Receiver", "Invalid position received for update: " + position + ", path: " + filePath);
                    }
                }
            }
        };

        pdfDeleteReceiver = new BroadcastReceiver() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onReceive(Context context, Intent intent) {
                int position = intent.getIntExtra("position", -1);
                String deletedPath = intent.getStringExtra("path"); // Get path for robust removal
                Log.d("Receiver", "Received PDF_FILE_DELETED broadcast for position: " + position + ", path: " + deletedPath);

                synchronized (pdfFiles) {  // Add synchronization to avoid concurrent modifications
                    if (deletedPath != null) {
                        boolean removed = false;
                        
                        // Get the file before deleting to access its thumbnail path
                        PDFFile fileToDelete = pdfRepository.getPDFFileByPath(deletedPath);
                        if (fileToDelete != null && fileToDelete.getImagePath() != null && 
                            !fileToDelete.getImagePath().isEmpty() && 
                            !fileToDelete.getImagePath().equals("__protected")) {
                            // Delete the thumbnail file
                            ThumbnailUtils.deleteThumbnail(fileToDelete.getImagePath());
                            Log.d(TAG, "Deleted thumbnail for: " + deletedPath);
                        }
                        
                        // Delete from database
                        pdfRepository.deletePDFFile(deletedPath);
                        Log.d("Receiver", "Deleted file from database: " + deletedPath);

                        // Remove from memory lists
                        removed = pdfFiles.removeIf(pdf -> pdf.getLocation().equals(deletedPath));
                        filteredPdfFiles.removeIf(pdf -> pdf.getLocation().equals(deletedPath));
                        favPdfFiles.removeIf(pdf -> pdf.getLocation().equals(deletedPath));

                        if (removed) {
                            Log.d("Receiver", "PDF removed based on path: " + deletedPath);
                            // Database already updated by pdfRepository.deletePDFFile call above
                            runOnUiThread(() -> {
                                pdfFileAdapter.notifyDataSetChanged(); // Run on UI thread
                                updateStatusTextVisibility();
                            });
                        } else {
                            Log.w("Receiver", "PDF path not found for deletion: " + deletedPath);
                        }
                    } else if (position != -1 && position < pdfFiles.size()) {
                        // Fallback to position if path is not provided (less reliable)
                        PDFFile removedFile = pdfFiles.remove(position);
                        filteredPdfFiles.remove(removedFile);
                        favPdfFiles.remove(removedFile);
                        Log.d("Receiver", "PDF removed based on position: " + position);
                        // Database already updated by pdfRepository.deletePDFFile call above
                        runOnUiThread(() -> {
                            pdfFileAdapter.notifyDataSetChanged(); // Run on UI thread
                            updateStatusTextVisibility();
                        });
                    } else {
                        Log.w("Receiver", "Invalid position or path received for deletion: " + position);
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register receivers
        IntentFilter updateFilter = new IntentFilter(ACTION_PDF_UPDATED);
        IntentFilter deleteFilter = new IntentFilter(ACTION_PDF_DELETED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pdfUpdateReceiver, updateFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(pdfDeleteReceiver, deleteFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pdfUpdateReceiver, updateFilter);
            registerReceiver(pdfDeleteReceiver, deleteFilter);
        }

        // Refresh list if data might have changed while paused (e.g., from InfoActivity/EditActivity)
        // Consider a more efficient way if performance becomes an issue
        if (dataChanged) {
             sortAndNotifyAdapter(); // Re-apply sort/filter
             dataChanged = false; // Reset flag
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister receivers
        unregisterReceiver(pdfUpdateReceiver);
        unregisterReceiver(pdfDeleteReceiver);
        dataChanged = true; // Flag that data might change while paused
    }

    // Helper method to get current search query from SearchView
    private String getCurrentSearchQuery() {
        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                return searchView.getQuery().toString();
            }
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close database when activity is destroyed
        if (pdfRepository != null) {
            pdfRepository.close();
        }
    }

    /**
     * Handle refresh action triggered by SwipeRefreshLayout
     */
    private void refreshPdfFiles() {
        // Launch on a background thread with proper database handling
        new Thread(() -> {
            // Ensure database is open in this thread
            try {
                if (pdfRepository != null) {
                    pdfRepository.open();
                }
            } catch (SQLException e) {
                Log.e(TAG, "Error opening database in refresh thread", e);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false); // Stop refresh animation
                    Toast.makeText(this, "Database error. Please restart the app.", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            
            // Clear the cache so we do a full refresh
            CacheManager.clearCache(this);
            usingCache = false;

            // Call the method to load PDF files
            loadPDFFiles();

            // Update UI when complete
            runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false); // Stop refresh animation
                Toast.makeText(this, "Refresh complete", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /**
     * Show dialog to confirm thumbnail rebuilding
     */
    private void showRebuildThumbnailsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rebuild Thumbnails");
        builder.setMessage("This will regenerate all PDF thumbnails using the first page of each PDF. This may take some time. Continue?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            dialog.dismiss();
            rebuildAllThumbnails();
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    /**
     * Rebuild all thumbnails in the background with a progress dialog
     */
    private void rebuildAllThumbnails() {
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            Toast.makeText(this, "No PDF files found to rebuild thumbnails", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create progress dialog
        View progressView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
        TextView progressText = progressView.findViewById(R.id.progress_text);
        ProgressBar progressBar = progressView.findViewById(R.id.progress_bar);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rebuilding Thumbnails");
        builder.setView(progressView);
        builder.setCancelable(false);
        
        AlertDialog progressDialog = builder.create();
        progressDialog.show();
        
        // Collect all PDF paths
        List<String> pdfPaths = new ArrayList<>();
        for (PDFFile pdfFile : pdfFiles) {
            if (pdfFile != null && pdfFile.getLocation() != null) {
                pdfPaths.add(pdfFile.getLocation());
            }
        }
        
        // Create thumbnail listener
        ThumbnailUtils.ThumbnailRebuildListener listener = new ThumbnailUtils.ThumbnailRebuildListener() {
            @Override
            public void onProgress(int percentage, int processed, int total) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percentage);
                    progressText.setText("Processing: " + processed + " of " + total);
                });
            }
            
            @Override
            public void onComplete(int successCount) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(BrowserActivity.this, 
                            "Successfully rebuilt " + successCount + " thumbnails", 
                            Toast.LENGTH_LONG).show();
                    
                    // Refresh the adapter to show new thumbnails
                    pdfFileAdapter.notifyDataSetChanged();
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(BrowserActivity.this, 
                            "Error rebuilding thumbnails: " + errorMessage, 
                            Toast.LENGTH_LONG).show();
                });
            }
        };
        
        // Start the rebuild process
        ThumbnailUtils.rebuildAllThumbnails(this, pdfPaths, listener);
    }
}

