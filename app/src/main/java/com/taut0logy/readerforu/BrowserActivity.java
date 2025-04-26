package com.taut0logy.readerforu;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.barteksc.pdfviewer.BuildConfig;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Objects;

public class BrowserActivity extends AppCompatActivity {
    private static ArrayList<PDFFile> pdfFiles;
    private static ArrayList<PDFFile> filteredPdfFiles;
    private static ArrayList<PDFFile> favPdfFiles;
    private PDFFileAdapter pdfFileAdapter;
    private TextView statusText;
    private SharedPreferences sharedPreferences;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private static final String PDF_CACHE_KEY = "pdf_cache";
    protected static boolean favouritesFirst = true;
    protected static int sortParameter = 1;
    protected static int sortDirection = 1;
    private boolean dataChanged = false;
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

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme(); // Apply theme before super.onCreate
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        toolbar = findViewById(R.id.browser_toolbar);
        recyclerView = findViewById(R.id.recyclerView_browser);
        statusText = findViewById(R.id.tvStatus);
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
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
                    builder.setMessage("This will rescan the storage for PDF files. It may take some time. Continue?");
                    builder.setPositiveButton("Yes", (dialog, which) -> {
                        dialog.dismiss();
                        AlertDialog.Builder builder2 = new AlertDialog.Builder(BrowserActivity.this);
                        builder2.setTitle("Refreshing PDF Files...");
                        builder2.setMessage(
                                "This may take a while...\n" +
                                        "Please do not close the app...");
                        ProgressBar progressBar = new ProgressBar(this);
                        progressBar.setIndeterminate(true);
                        progressBar.setPadding(0, 0, 0, 30);
                        builder2.setView(progressBar);
                        builder2.setCancelable(false);
                        AlertDialog alertDialog2 = builder2.create();
                        alertDialog2.show();
                        pdfFiles.clear();
                        new Thread(() -> {
                            loadPDFFiles();
                            savePDFFilesToCache();
                            runOnUiThread(() -> {
                                pdfFileAdapter.notifyDataSetChanged();
                                alertDialog2.dismiss();
                            });
                        }).start();
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
                if(id == R.id.action_exit) {
                    finish();
                    System.exit(0);
                    return true;
                }
                return false;
        });
        pdfFileAdapter = new PDFFileAdapter(pdfFiles, filteredPdfFiles, this);
        recyclerView.setAdapter(pdfFileAdapter);
        //load pdf files asynchronously
        statusText.setVisibility(TextView.VISIBLE);
        statusText.setText(R.string.loading_pdf_files);
        new Thread(() -> {
            if (isPdfCacheAvailable()) {
                loadCachedPDFFiles();
            } else {
                loadPDFFiles();
            }
            runOnUiThread(() -> {
                if(pdfFiles.isEmpty())
                    statusText.setText(R.string.no_pdf_files_found);
                else
                    statusText.setVisibility(TextView.GONE);
                pdfFileAdapter.notifyDataSetChanged();
            });
        }).start();
        //savePDFFilesToCache();
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
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pdfFileUpdatedReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePDFFilesToCache();
    }

    @Override
    protected void onStart() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.taut0logy.readerforu.PDF_FILE_UPDATED");
        //intentFilter.addAction("com.taut0logy.readerforu.PDF_FILE_DELETED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pdfFileUpdatedReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pdfFileUpdatedReceiver, intentFilter);
        }
        super.onStart();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onResume() {
        pdfFileAdapter.notifyDataSetChanged();
        super.onResume();
    }

    private BroadcastReceiver pdfFileUpdatedReceiver = new BroadcastReceiver() {
        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("com.taut0logy.readerforu.PDF_FILE_UPDATED")) {
                int position = intent.getIntExtra("position", -1);
                if(position != -1) {
                    pdfFiles.sort(BrowserActivity.this::comparePDFFiles);
                    filteredPdfFiles.sort(BrowserActivity.this::comparePDFFiles);
                    //pdfFileAdapter.notifyDataSetChanged();
                }
            }
        }
    };



    private boolean isPdfCacheAvailable() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        return sharedPreferences.contains(PDF_CACHE_KEY);
    }

    private void savePDFFilesToCache() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        JSONArray pdfArray = new JSONArray();
        for (PDFFile pdfFile : pdfFiles) {
            pdfArray.put(pdfFile.toJSON());
        }
        editor.remove(PDF_CACHE_KEY); // Clear the cache (if any)
        editor.putString(PDF_CACHE_KEY, pdfArray.toString());
        editor.apply();
        Log.d("PDFErr", "Saved PDF Files to cache: " + pdfArray.length() + " " + pdfFiles.size() + " " + pdfFileAdapter.getItemCount());
    }

    private void loadCachedPDFFiles() {
        String pdfCacheJson = sharedPreferences.getString(PDF_CACHE_KEY, "[]");
        Log.d("PDFErr", "Loading Cached PDF Files: " + pdfCacheJson);
        if (!pdfCacheJson.isEmpty()) {
            try {
                JSONArray pdfArray = new JSONArray(pdfCacheJson);
                Log.d("PDFErr", "Loading Cached PDF Files: " + pdfArray.length());
                if(pdfArray.length() == 0) {
                    loadPDFFiles();
                    return;
                }
                //pdfFiles = new ArrayList<>();
                for (int i = 0; i < pdfArray.length(); i++) {
                    JSONObject pdfObj = pdfArray.getJSONObject(i);
                    PDFFile pdfFile = new PDFFile(
                            pdfObj.getString("name"),
                            pdfObj.getString("author"),
                            pdfObj.getString("description"),
                            pdfObj.getString("path"),
                            pdfObj.getString("thumbnailPath"),
                            pdfObj.getInt("currPage"),
                            pdfObj.getInt("totalPages"),
                            pdfObj.getBoolean("isFav"),
                            pdfObj.getLong("modified"),
                            pdfObj.getLong("lastRead")
                    );
                    //Log.d("PDFErr", "Loading Cached PDF Files: " + pdfFile.getName() + " " + pdfFile.getAuthor() + " " + pdfFile.getDescription() + " " + pdfFile.getLocation() + " " + pdfFile.getImagePath() + " " + pdfFile.getCurrPage() + " " + pdfFile.getTotalPages() + " " + pdfFile.getFavourite());
                    pdfFiles.add(pdfFile);
                    filteredPdfFiles.add(pdfFile);
                    if(pdfFile.getFavourite())
                        favPdfFiles.add(pdfFile);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            pdfFiles.sort(this::comparePDFFiles);
            filteredPdfFiles.sort(this::comparePDFFiles);
            Log.d("PDFErr", "Finished loading Cached PDF Files: " + pdfFiles.size() + " " + pdfFileAdapter.getItemCount());
        } else {
            loadPDFFiles();
        }
    }

    private void loadPDFFiles() {
        Log.e("PDFErr", "Loading PDF Files from storage...");
        ArrayList<File> allFiles = new ArrayList<>();

        // Query PDF files using MediaStore
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Files.getContentUri("external"),
                new String[]{MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA},
                MediaStore.Files.FileColumns.MIME_TYPE + "=?",
                new String[]{"application/pdf"},
                null)) {

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                    allFiles.add(new File(filePath));
                }
            }
        } catch (SecurityException e) {
            Log.e("PDFErr", "Permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e("PDFErr", "Load Error: " + e.getMessage());
        }
        if (!allFiles.isEmpty()) {
            for (File file : allFiles) {
                addPdfFile(file);
                String message = "Scanning for PDF Files...\nFound " + pdfFiles.size() + " PDF Files";
                runOnUiThread(() -> statusText.setText(message));
            }
            pdfFiles.sort(this::comparePDFFiles);
            filteredPdfFiles.sort(this::comparePDFFiles);
            savePDFFilesToCache();
        } else {
            runOnUiThread(() -> statusText.setText(R.string.no_pdf_files_found));
        }
    }

    private void addPdfFile(File file) {
        String path = file.getAbsolutePath();
        String name = file.getName();
        long modified = file.lastModified();
        int currPage = sharedPreferences.getInt(path + "nowPage", 0);
        long lastRead = sharedPreferences.getLong(path + "_lastRead", 0);
        try {
            PdfReader reader = new PdfReader(path);
            PdfDocument pdfDocument = new PdfDocument(reader);
            int pages = pdfDocument.getNumberOfPages();
            String title = pdfDocument.getDocumentInfo().getTitle();
            String author = pdfDocument.getDocumentInfo().getAuthor();
            if(author == null || author.isEmpty() || author.equals("null"))
                author = "Unknown";
            String description = pdfDocument.getDocumentInfo().getSubject();
            if(description == null || description.isEmpty() || description.equals("null"))
                description = "No description";
            boolean isFav = pdfDocument.getDocumentInfo().getMoreInfo("favourite")!= null && pdfDocument.getDocumentInfo().getMoreInfo("favourite").equals("true");
            pdfDocument.close();
            File thumbnail = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/.thumbnails/" + name + ".png");
            if (!thumbnail.exists()) {
                // Create thumbnail
                ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
                PdfRenderer.Page page = pdfRenderer.openPage(0);
                Bitmap thumbnailImage = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(thumbnailImage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                FileOutputStream fileOutputStream = new FileOutputStream(thumbnail);
                thumbnailImage.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
                fileOutputStream.close();
                fileDescriptor.close();
            }
            String bookName=(title==null || title.isEmpty() || title.equals("null"))?name:title;
            PDFFile pdfFile = new PDFFile(bookName, author, description, path, thumbnail.getAbsolutePath(), currPage, pages, isFav, modified, lastRead);
            pdfFiles.add(pdfFile);
            filteredPdfFiles.add(pdfFile);
            if(isFav)
                favPdfFiles.add(pdfFile);
        } catch (Exception e) {
            if(Objects.requireNonNull(e.getMessage()).contains("password")) {
                Log.d("PDFErr", "Protected PDF: " + name);
                PDFFile pdfFile = new PDFFile(name, "Protected", "Protected", path, "__protected", 0, 0, false, modified, lastRead);
                pdfFiles.add(pdfFile);
            } else {
                Log.e("PDFErr", "Error: " + e.getMessage());
            }
        }
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

}

/*
 *  Caused by: java.lang.UnsupportedOperationException: Failed to resolve attribute at index 13: TypedValue{t=0x2/d=0x7f030124 a=-1}, theme={InheritanceMap=[id=0x7f130272com.taut0logy.readerforu:style/Theme.ReaderForU,
 *  id=0x7f130078com.taut0logy.readerforu:style/Base.Theme.ReaderForU,
 *  id=0x7f130255com.taut0logy.readerforu:style/Theme.MaterialComponents.DayNight.NoActionBar,
 *  id=0x7f13026ecom.taut0logy.readerforu:style/Theme.MaterialComponents.Light.NoActionBar,
 *  id=0x7f130260com.taut0logy.readerforu:style/Theme.MaterialComponents.Light,
 *  id=0x7f13006ecom.taut0logy.readerforu:style/Base.Theme.MaterialComponents.Light,
 *  id=0x7f1300a8com.taut0logy.readerforu:style/Base.V21.Theme.MaterialComponents.Light,
 *  id=0x7f130096com.taut0logy.readerforu:style/Base.V14.Theme.MaterialComponents.Light,
 *  id=0x7f130097com.taut0logy.readerforu:style/Base.V14.Theme.MaterialComponents.Light.Bridge,
 *  id=0x7f13013bcom.taut0logy.readerforu:style/Platform.MaterialComponents.Light,
 *  id=0x7f13021acom.taut0logy.readerforu:style/Theme.AppCompat.Light,
 *  id=0x7f130052com.taut0logy.readerforu:style/Base.Theme.AppCompat.Light,
 *  id=0x7f1300bacom.taut0logy.readerforu:style/Base.V28.Theme.AppCompat.Light,
 *  id=0x7f1300b7com.taut0logy.readerforu:style/Base.V26.Theme.AppCompat.Light,
 *  id=0x7f1300b1com.taut0logy.readerforu:style/Base.V23.Theme.AppCompat.Light, 
 * id=0x7f1300afcom.taut0logy.readerforu:style/Base.V22.Theme.AppCompat.Light, 
 * id=0x7f1300a4com.taut0logy.readerforu:style/Base.V21.Theme.AppCompat.Light, 
 * id=0x7f1300bdcom.taut0logy.readerforu:style/Base.V7.Theme.AppCompat.Light, 
 * id=0x7f130138com.taut0logy.readerforu:style/Platform.AppCompat.Light, 
 * id=0x7f130143com.taut0logy.readerforu:style/Platform.V25.AppCompat.Light, 
 * id=0x1030241android:style/Theme.Material.Light.NoActionBar, 
 * id=0x1030237android:style/Theme.Material.Light, 
 * id=0x103000candroid:style/Theme.Light, 
 * id=0x1030005android:style/Theme], Themes=[com.taut0logy.readerforu:style/Theme.ReaderForU, forced, com.taut0logy.readerforu:style/Theme.AppCompat.Empty, forced, android:style/Theme.DeviceDefault.Light.DarkActionBar, forced]}
                                                                                                    	at android.content.res.TypedArray.getDrawableForDensity(TypedArray.java:1007)
 */