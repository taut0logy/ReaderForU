package com.taut0logy.readerforu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final String PREFS_NAME = "ReaderForUPrefs";
    private static final String KEY_FOLDERS = "pdf_folders";
    
    // Legacy permissions for Android <= 12 (API 32)
    private static final String[] PERMISSIONS_STORAGE_LEGACY = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityResultLauncher<Intent> directoryPickerLauncher;
    
    private boolean isFirstLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Check if this is the first launch
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isFirstLaunch = !prefs.contains(KEY_FOLDERS);
        
        // Initialize permission launcher
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allGranted = !permissions.containsValue(false);
                    if (allGranted) {
                        Log.d(TAG, "All permissions granted");
                        proceedToFolderSelection();
                    } else {
                        Log.d(TAG, "Some permissions were denied");
                        showPermissionDeniedDialog();
                    }
                });
                
        // Initialize directory picker launcher
        directoryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            handleSelectedDirectory(treeUri);
                        } else {
                            showFolderSelectionError();
                        }
                    } else {
                        // User cancelled folder selection
                        if (isFirstLaunch) {
                            // If first launch and user cancelled, show message and retry
                            showFolderSelectionRequired();
                        } else {
                            // Otherwise, proceed with existing folders
                            proceedToBrowserActivity();
                        }
                    }
                });

        // Start the app initialization process
        checkPermissions();
    }

    private void checkPermissions() {
        // On API 26+ (Android 8.0+), we primarily use Storage Access Framework
        // but for legacy API < 33, still need to request storage permissions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            ArrayList<String> permissionsNeeded = new ArrayList<>();
            for (String permission : PERMISSIONS_STORAGE_LEGACY) {
                if (ContextCompat.checkSelfPermission(this, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(permission);
                }
            }
            
            if (!permissionsNeeded.isEmpty()) {
                showPermissionExplanationDialog(permissionsNeeded.toArray(new String[0]));
            } else {
                proceedToFolderSelection();
            }
        } else {
            // For API 33+ (Android 13+), we don't need explicit permissions, just use SAF
            proceedToFolderSelection();
        }
    }
    
    private void proceedToFolderSelection() {
        // If first launch, need to select folder
        if (isFirstLaunch) {
            showFolderSelectionDialog();
        } else {
            // Check if we have any saved folders with valid access
            List<String> savedFolders = getSavedFolders();
            boolean hasValidFolder = false;
            
            for (String uriString : savedFolders) {
                try {
                    Uri uri = Uri.parse(uriString);
                    DocumentFile documentFile = DocumentFile.fromTreeUri(this, uri);
                    if (documentFile != null && documentFile.canRead()) {
                        hasValidFolder = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking saved folder: " + e.getMessage());
                }
            }
            
            if (hasValidFolder) {
                proceedToBrowserActivity();
            } else {
                // If no valid folders, ask to select one
                showFolderSelectionDialog();
            }
        }
    }
    
    private void showPermissionExplanationDialog(String[] permissions) {
        new AlertDialog.Builder(this)
                .setTitle("Storage Permissions")
                .setMessage("This app needs storage permissions to access PDF files on your device.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    requestPermissionsLauncher.launch(permissions);
                })
                .setCancelable(false)
                .show();
    }
    
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Storage permissions are required to access PDF files. " +
                          "Without these permissions, the app cannot function properly.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Exit", (dialog, which) -> {
                    finishAffinity();
                })
                .setCancelable(false)
                .show();
    }
    
    private void showFolderSelectionDialog() {
        String message = isFirstLaunch ?
                "Please select a folder containing your PDF files to get started." :
                "Select a folder containing your PDF files.";
        
        new AlertDialog.Builder(this)
                .setTitle("Select PDF Folder")
                .setMessage(message)
                .setPositiveButton("Select Folder", (dialog, which) -> {
                    launchDirectoryPicker();
                })
                .setNegativeButton(isFirstLaunch ? "Exit" : "Skip", (dialog, which) -> {
                    if (isFirstLaunch) {
                        finishAffinity();
                    } else {
                        proceedToBrowserActivity();
                    }
                })
                .setCancelable(!isFirstLaunch)
                .show();
    }
    
    private void showFolderSelectionRequired() {
        new AlertDialog.Builder(this)
                .setTitle("Folder Selection Required")
                .setMessage("You need to select at least one folder containing PDFs to use this app.")
                .setPositiveButton("Select Folder", (dialog, which) -> {
                    launchDirectoryPicker();
                })
                .setNegativeButton("Exit", (dialog, which) -> {
                    finishAffinity();
                })
                .setCancelable(false)
                .show();
    }
    
    private void showFolderSelectionError() {
        new AlertDialog.Builder(this)
                .setTitle("Folder Selection Error")
                .setMessage("There was an error accessing the selected folder. Please try again.")
                .setPositiveButton("Retry", (dialog, which) -> {
                    launchDirectoryPicker();
                })
                .setNegativeButton(isFirstLaunch ? "Exit" : "Skip", (dialog, which) -> {
                    if (isFirstLaunch) {
                        finishAffinity();
                    } else {
                        proceedToBrowserActivity();
                    }
                })
                .setCancelable(!isFirstLaunch)
                .show();
    }
    
    private void launchDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                       Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        directoryPickerLauncher.launch(intent);
    }
    
    private void handleSelectedDirectory(Uri treeUri) {
        // Take persistent permission
        try {
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Save this folder URI
            saveFolder(treeUri.toString());
            
            // Now proceed to the main activity
            proceedToBrowserActivity();
            
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to get persistent access to directory", e);
            showFolderSelectionError();
        }
    }
    
    private void proceedToBrowserActivity() {
        // Create app directories if needed
        initializeAppDirectories();
        
        // Launch BrowserActivity
        Intent intent = new Intent(this, BrowserActivity.class);
        startActivity(intent);
        finish();
    }
    
    private void initializeAppDirectories() {
        // Create required app-specific directories
        File baseDir = getExternalFilesDir(null);
        if (baseDir == null) {
            Toast.makeText(this, "Error accessing app storage.", Toast.LENGTH_SHORT).show();
            return;
        }

        File thumbnailsDir = new File(baseDir, ".thumbnails");
        if (!thumbnailsDir.exists()) {
            boolean created = thumbnailsDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create thumbnails directory");
            }
        }
    }
    
    private void saveFolder(String folderUri) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Get current folders set
        List<String> currentFolders = getSavedFolders();
        
        // Add new folder if not already present
        if (!currentFolders.contains(folderUri)) {
            currentFolders.add(folderUri);
        }
        
        // Save updated list
        editor.putString(KEY_FOLDERS, String.join(",", currentFolders));
        editor.apply();
        
        // Mark no longer first launch
        isFirstLaunch = false;
    }
    
    private List<String> getSavedFolders() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String foldersString = prefs.getString(KEY_FOLDERS, "");
        
        List<String> foldersList = new ArrayList<>();
        if (!foldersString.isEmpty()) {
            String[] folders = foldersString.split(",");
            for (String folder : folders) {
                if (!folder.isEmpty()) {
                    foldersList.add(folder);
                }
            }
        }
        
        return foldersList;
    }
}
