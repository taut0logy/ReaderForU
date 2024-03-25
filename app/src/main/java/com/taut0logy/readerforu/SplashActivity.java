package com.taut0logy.readerforu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1;
    private static final int READ_REQUEST_CODE = 42; // Arbitrary request code for SAF
    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        getPermissions();
    }

    private void initApp() {
        File folder = new File(Environment.getExternalStorageDirectory() + "/ReaderForU");
        boolean res = true;
        if(!folder.exists()) {
            res = folder.mkdir();
        }
        boolean res1 = true, res2 = true, res3 = true;
        if (!folder.exists()) {
            res1 = folder.mkdir();
        }
        File folder1 = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/thumbnails");
        if (!folder1.exists()) {
            res2 = folder1.mkdir();
        }
        File folder2 = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/BookData");
        if (!folder2.exists()) {
            res3 = folder2.mkdir();
        }
        if(!res || !res1 || !res2 || !res3) {
            Toast.makeText(this, "Error creating directories", Toast.LENGTH_SHORT).show();
        }
    }
    private void getPermissions() {
        //Android is 11 (R) or above
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(SplashActivity.this, BrowserActivity.class);
                initApp();
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                showPermissionDeniedPopup();
                //requestStoragePermission();
            }
        } else {
            //Below android 11
                ActivityCompat.requestPermissions(SplashActivity.this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
                if(ContextCompat.checkSelfPermission(SplashActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SplashActivity.this, BrowserActivity.class);
                    initApp();
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                    showPermissionDeniedPopup();
                    //requestStoragePermission();
                }
            }
        }


    private void showPermissionDeniedPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required");
        builder.setMessage("Storage permission is required to use this app.");
        builder.setPositiveButton("Grant Permission", (dialog, which) -> {
            // Request the storage permission again
            Log.d("Permission", "Requesting storage permission again");
            //requestStorageAccess();
            requestStoragePermission();
        });
        builder.setCancelable(false); // Prevent dismissing dialog by tapping outside
        builder.setOnCancelListener(dialog -> {
            // Close the app if back button is pressed while the permission popup is shown
            finishAffinity();
        });
        builder.show();
    }

    private void requestStorageAccess() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri treeUri = resultData.getData();
                // Use the selected treeUri to access the folder
                // Example: readFromFolder(treeUri);
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(SplashActivity.this, BrowserActivity.class);
                initApp();
                startActivity(intent);
                finish();
            }
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void requestStoragePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                Log.d("Permission", "Requesting storage permission try");
                storageActivityResultLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                Log.d("Permission", "Requesting storage permission catch");
                storageActivityResultLauncher.launch(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
            initApp();
        }
    }
    private final ActivityResultLauncher<Intent> storageActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if(Environment.isExternalStorageManager()) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SplashActivity.this, BrowserActivity.class);
                        initApp();
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    if(result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SplashActivity.this, BrowserActivity.class);
                        initApp();
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
    );
}
