package com.taut0logy.readerforu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;

public class SplashActivity extends AppCompatActivity {

    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1;
    private static boolean isPermissionPopupShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        getPermissions();
        File folder = new File(Environment.getExternalStorageDirectory() + "/ReaderForU");
        if (!folder.exists()) {
            folder.mkdir();
        }
        File folder1 = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/thumbnails");
        if (!folder1.exists()) {
            folder1.mkdir();
        }
        File folder2 = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/BookData");
        if (!folder2.exists()) {
            folder2.mkdir();
        }
        String location= Environment.getExternalStorageDirectory() + "/ReaderForU/BookData/favlist.json";
        File file = new File(location);
        if (!file.exists()) {
            try {
                JSONObject jsonObject = new JSONObject();
                FileWriter fileWriter = new FileWriter(location);
                fileWriter.write(jsonObject.toString());
                fileWriter.flush();
                fileWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Start the main activity (BrowserActivity)
                Intent intent = new Intent(SplashActivity.this, BrowserActivity.class);
                startActivity(intent);
                finish();
            }
        }, 1000);
    }

    private void getPermissions() {
        //Android is 11 (R) or above
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if(Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
//                AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                builder.setTitle("Permission Required");
//                builder.setMessage("Storage permission is required to use this app.");
//                builder.setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        // Request the storage permission again
//                        requestStoragePermission();
//                    }
//                });
//                builder.setCancelable(false); // Prevent dismissing dialog by tapping outside
//                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
//                    @Override
//                    public void onCancel(DialogInterface dialog) {
//                        // Close the app if back button is pressed while the permission popup is shown
//                        finish();
//                    }
//                });
//                builder.create().show();
                requestStoragePermission();
            }
        } else {
            //Below android 11
//            if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this);
//                builder.setTitle("Permission Required");
//                builder.setMessage("Storage permission is required to use this app.");
//                builder.setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        // Request the storage permission again
//                        ActivityCompat.requestPermissions(SplashActivity.this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
//                    }
//                });
//                builder.setCancelable(false); // Prevent dismissing dialog by tapping outside
//                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
//                    @Override
//                    public void onCancel(DialogInterface dialog) {
//                        // Close the app if back button is pressed while the permission popup is shown
//                        finish();
//                    }
//                });
//                builder.create().show();
                ActivityCompat.requestPermissions(SplashActivity.this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_CODE);
            }
        }


    private void showPermissionDeniedPopup() {
        isPermissionPopupShown = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permission Required");
        builder.setMessage("Storage permission is required to use this app.");
        builder.setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Request the storage permission again
                Log.d("Permission", "Requesting storage permission again");
                requestStoragePermission();
            }
        });
        builder.setCancelable(false); // Prevent dismissing dialog by tapping outside
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // Close the app if back button is pressed while the permission popup is shown
                finish();
            }
        });
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if (isPermissionPopupShown) {
            // Close the app if back button is pressed while the permission popup is shown
            finish();
        } else {
            super.onBackPressed();
        }
    }

    private void requestStoragePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s", new Object[]{getApplicationContext().getPackageName()})));
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
        }
    }
    private final ActivityResultLauncher<Intent> storageActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if(Environment.isExternalStorageManager()) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if(result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );
}
