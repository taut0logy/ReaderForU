package com.taut0logy.readerforu;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class BrowserActivity extends AppCompatActivity {
    private static ArrayList<PDFFile> pdfFiles;
    private static ArrayList<PDFFile> favPdfFiles;
    @SuppressLint("StaticFieldLeak")
    private static PDFFileAdapter pdfFileAdapter;
    private TextView statusText;
    private SharedPreferences sharedPreferences;
    Toolbar toolbar;
    private static final String PDF_CACHE_KEY = "pdf_cache";
    protected static boolean favouritesFirst = true;
    protected static int sortParameter = 1;
    protected static int sortDirection = 1;
    protected static ArrayList<PDFFile> getPdfFiles() {
        return pdfFiles;
    }
    protected static ArrayList<PDFFile> getFavPdfFiles() {
        return favPdfFiles;
    }
    protected static PDFFileAdapter getPdfFileAdapter() {
        return pdfFileAdapter;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        toolbar = findViewById(R.id.browser_toolbar);
        RecyclerView recyclerView = findViewById(R.id.recyclerView_browser);
        statusText = findViewById(R.id.tvStatus);
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        pdfFiles = new ArrayList<>();
        favPdfFiles = new ArrayList<>();
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
                if(id == R.id.action_refresh) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivity.this);
                    builder.setTitle("Refreshing PDF Files...");
                    builder.setMessage(
                            "This may take a while...\n" +
                            "Please do not close the app...");
                    builder.setCancelable(false);
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                    new Thread(() -> {
                        loadPDFFiles();
                        savePDFFilesToCache();
                        runOnUiThread(() -> {
                            pdfFileAdapter.notifyDataSetChanged();
                            alertDialog.dismiss();
                        });
                    }).start();
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
                    return true;
                }
                return false;
        });
        pdfFileAdapter = new PDFFileAdapter(pdfFiles, this);
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
                statusText.setVisibility(TextView.GONE);
                pdfFileAdapter.notifyDataSetChanged();
            });
        }).start();
        savePDFFilesToCache();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showFilterDialog(Context context) {
        // Inside your activity or fragment
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            // Inflate the layout for the dialog
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
            builder.setView(dialogView);

            CheckBox checkBoxFavorites = dialogView.findViewById(R.id.checkBoxFavourites);
            RadioGroup radioGroupSortingParameters = dialogView.findViewById(R.id.radioGroupSortingParameters);
            RadioGroup radioGroupSortingOrder = dialogView.findViewById(R.id.radioGroupSortingOrder);

            // Add buttons to apply or cancel the filter and sort options
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
            switch (sortDirection) {
                case 1:
                    radioGroupSortingOrder.check(R.id.radioButtonAscending);
                    break;
                case 2:
                    radioGroupSortingOrder.check(R.id.radioButtonDescending);
                    break;
                default:
                    radioGroupSortingOrder.check(R.id.radioButtonAscending);
            }

            buttonApply.setOnClickListener(v -> {
                // Apply the selected filter and sort options
                favouritesFirst = checkBoxFavorites.isChecked();

                int selectedSortingParameterId = radioGroupSortingParameters.getCheckedRadioButtonId();
                // Handle selected sorting parameter option
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
                // Handle selected sorting order option
                if(selectedSortingOrderId == R.id.radioButtonAscending)
                    sortDirection = 1;
                else if(selectedSortingOrderId == R.id.radioButtonDescending)
                    sortDirection = 2;
                else sortDirection = 1;
                // Sort the PDF files according to the selected options and notify the adapter
                pdfFiles.sort(this::comparePDFFiles);
                pdfFileAdapter.notifyDataSetChanged();
                // Dismiss the dialog
                dialog.dismiss();
            });

            buttonCancel.setOnClickListener(v -> {
                // Dismiss the dialog without applying any changes
                dialog.dismiss();
            });

            dialog.show();
        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }


    @Override
    protected void onPause() {
        super.onPause();
        // Save PDF files to cache when activity pauses
        savePDFFilesToCache();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Save PDF files to cache when activity stops
        savePDFFilesToCache();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onStart() {
        super.onStart();
        pdfFiles.sort(this::comparePDFFiles);
        pdfFileAdapter.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onResume() {
        super.onResume();
        pdfFiles.sort(this::comparePDFFiles);
        pdfFileAdapter.notifyDataSetChanged();
    }

    private boolean isPdfCacheAvailable() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        return sharedPreferences.contains(PDF_CACHE_KEY);
    }

    private void savePDFFilesToCache() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        JSONArray pdfArray;
        try {
            pdfArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
            SharedPreferences.Editor editor = sharedPreferences.edit();
            for (PDFFile pdfFile : pdfFiles) {
                JSONObject pdfObj = pdfFile.toJSON();
                if(!pdfArray.toString().contains(pdfObj.getString("path")))
                    pdfArray.put(pdfObj);
            }
            editor.putString(PDF_CACHE_KEY, pdfArray.toString());
            editor.apply();
            Log.d("PDFErr", "Saved PDF Files to cache: " + pdfArray.length() + " " + pdfFiles.size() + " " + pdfFileAdapter.getItemCount());
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
                    if(pdfFile.getFavourite())
                        favPdfFiles.add(pdfFile);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            pdfFiles.sort(this::comparePDFFiles);
            Log.d("PDFErr", "Finished loading Cached PDF Files: " + pdfFiles.size() + " " + pdfFileAdapter.getItemCount());
        } else {
            loadPDFFiles();
        }
    }

    private void loadPDFFiles() {
        Log.e("PDFErr", "Loading PDF Files from storage...");
        // Load PDF files from the storage
        File folder = new File(Environment.getExternalStorageDirectory() + "/Documents");
        File[] files = folder.listFiles();
        File folder1 = new File(Environment.getExternalStorageDirectory() + "/Download");
        File[] files1 = folder1.listFiles();
        ArrayList<File> allFiles = new ArrayList<>();
        //Log.d("PDFErr", "Files: " + allFiles + " " + Arrays.toString(files) + " " + Arrays.toString(files1));
        if(files != null) {
            allFiles.addAll(Arrays.asList(files));
        }
        if(files1 != null) {
            allFiles.addAll(Arrays.asList(files1));
        }
        if(allFiles.size() != 0) {
            for (File file : allFiles) {
                if (file.getName().endsWith(".pdf")) {
                    addPdfFile(file);
                    String message="Scanning for PDF Files...\nFound "+pdfFiles.size()+" PDF Files";
                    runOnUiThread(() -> statusText.setText(message));
                    //Log.d("PDFErr", (String) statusText.getText());
                }
            }
            pdfFiles.sort(this::comparePDFFiles);
            savePDFFilesToCache();
        }
    }

    private void addPdfFile(File file) {
        String path = file.getAbsolutePath();
        String name = file.getName();
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
            File thumbnail = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/thumbnails/" + name + ".png");
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
            //boolean isFav = favList.has(path);
            int currPage = sharedPreferences.getInt(path + "nowPage", 0);
            long lastRead = sharedPreferences.getLong(path + "_lastRead", 0);
            long modified = file.lastModified();
            //Log.e("PDFErr", "PDF File: " + name + " " + author + " " + description + " " + path + " " + thumbnail.getAbsolutePath() + " " + currPage + " " + pages);
            String bookName=(title==null || title.isEmpty() || title.equals("null"))?name:title;
            PDFFile pdfFile = new PDFFile(bookName, author, description, path, thumbnail.getAbsolutePath(), currPage, pages, isFav, modified, lastRead);
            pdfFiles.add(pdfFile);
            if(isFav)
                favPdfFiles.add(pdfFile);
        } catch (Exception e) {
            Log.e("PDFErr", "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void showAboutDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("About ReaderForU");
        builder.setMessage(
                "ReaderForU is a simple PDF reader app.\n" +
                "Developed by Raufun Ahsan\n" +
                "Version 1.0.0\n" +
                "© 2021 Taut0logy\n" +
                "All rights reserved."
        );
        builder.setCancelable(true);
        builder.setPositiveButton("GitHub", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.github.com/Taut0logy"));
            context.startActivity(intent);
        });
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
                return pdfFile1.getName().compareTo(pdfFile2.getName())*sortDirection;
            case 2:
                return pdfFile1.getAuthor().compareTo(pdfFile2.getAuthor())*sortDirection;
            case 3:
                return (int) (pdfFile1.getModified()-pdfFile2.getModified())*sortDirection;
            case 4:
                return (int) (pdfFile2.getLastRead()-pdfFile1.getLastRead())*sortDirection;
            default:
                return 0;
        }
    }
}