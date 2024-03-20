package com.taut0logy.readerforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.SharedPreferences;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;

//import com.tom_roush.pdfbox.pdmodel.PDDocument;
//import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;
//import com.tom_roush.pdfbox.rendering.PDFRenderer;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BrowserActivity extends AppCompatActivity {

    private static ArrayList<PDFFile> pdfFiles;
    private static PDFFileAdapter pdfFileAdapter;
    private RecyclerView recyclerView;
    private SharedPreferences sharedPreferences;
    Toolbar toolbar;
    private static final String PDF_CACHE_KEY = "pdf_cache";
    private boolean isLoading = false;
    private boolean isLastPage = false;
    private int currentPage = 1;
    private int totalItems = 0;
    private int visibleItemCount;
    private int pastVisibleItems;
    private int recyclerViewThreshold = 5;

    protected static ArrayList<PDFFile> getPdfFiles() {
        return pdfFiles;
    }

    protected static PDFFileAdapter getPdfFileAdapter() {
        return pdfFileAdapter;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        toolbar = findViewById(R.id.browser_toolbar);
        recyclerView = findViewById(R.id.recyclerView_browser);
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        pdfFiles = new ArrayList<PDFFile>();
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
                if(id == R.id.action_refresh) {
                    loadPDFFiles();
                    pdfFileAdapter.notifyDataSetChanged();
                    return true;
                }
                if(id == R.id.action_search) {
                    startActivity(new Intent(BrowserActivity.this, SearchActivity.class));
                    return true;
                }
                if(id == R.id.action_about) {
                    startActivity(new Intent(BrowserActivity.this, AboutActivity.class));
                    return true;
                }
                if(id == R.id.action_exit) {
                    finish();
                    return true;
                }
                return false;
        });
        //loadPDFFiles();
        if (isPdfCacheAvailable()) {
            loadCachedPDFFiles();
        } else {
            loadPDFFiles();
        }
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                visibleItemCount = linearLayoutManager.getChildCount();
                totalItems = linearLayoutManager.getItemCount();
                pastVisibleItems = linearLayoutManager.findFirstVisibleItemPosition();
                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + pastVisibleItems) >= totalItems - recyclerViewThreshold) {
                        isLoading = true;
                        currentPage++;
                        loadmorePDFFiles(currentPage);
                        isLoading = false;
                    }
                }
            }
        });
        pdfFileAdapter = new PDFFileAdapter(pdfFiles, this);
        recyclerView.setAdapter(pdfFileAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Save PDF files to cache when activity stops
        savePDFFilesToCache();
    }

    private void loadmorePDFFiles(int page) {
        File folder = new File(Environment.getExternalStorageDirectory() + "/Documents");
        File[] files = folder.listFiles();
        File folder1 = new File(Environment.getExternalStorageDirectory() + "/Download");
        File[] files1 = folder1.listFiles();
        ArrayList<File> allFiles = new ArrayList<File>();
        if(files != null) {
            for (File file : files) {
                allFiles.add(file);
            }
        }
        if(files1 != null) {
            for (File file : files1) {
                allFiles.add(file);
            }
        }
        allFiles.sort(Comparator.comparing(File::getName));
        if(allFiles.size() != 0) {
            for (File file : allFiles) {
                if (file.getName().endsWith(".pdf")) {
                    String path = file.getAbsolutePath();
                    String name = file.getName();
                    try {
                        PdfReader reader = new PdfReader(path);
                        PdfDocument pdfDocument = new PdfDocument(reader);
                        int pages = pdfDocument.getNumberOfPages();
                        String title = pdfDocument.getDocumentInfo().getTitle();
                        String author = pdfDocument.getDocumentInfo().getAuthor();
                        if(author == null)
                            author = "Unknown";
                        String description = pdfDocument.getDocumentInfo().getSubject();
                        if(description == null)
                            description = "No description";
                        pdfDocument.close();
                        File thumbnail = new File(Environment.getExternalStorageDirectory() + "/ReaderForU/thumbnails/" + name + ".png");
                        if (!thumbnail.exists()) {
                            // Create thumbnail
                            ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                            PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
                            PdfRenderer.Page page1 = pdfRenderer.openPage(0);
                            Bitmap thumbnailImage = Bitmap.createBitmap(page1.getWidth(), page1.getHeight(), Bitmap.Config.ARGB_8888);
                            page1.render(thumbnailImage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            FileOutputStream fileOutputStream = new FileOutputStream(thumbnail);
                            thumbnailImage.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
                            fileOutputStream.close();
                            fileDescriptor.close();
                        }
                        PDFFile pdfFile = new PDFFile(title, author, description, path, thumbnail.getAbsolutePath(), 0, pages, false);
                        pdfFiles.add(pdfFile);
                    } catch (Exception e) {
                        Log.e("PDFErr", "Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private boolean isPdfCacheAvailable() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        return sharedPreferences.contains(PDF_CACHE_KEY);
    }

    private void savePDFFilesToCache() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        JSONArray pdfArray = null;
        try {
            pdfArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        //JSONArray pdfArray = new JSONArray();
        for (PDFFile pdfFile : pdfFiles) {
            JSONObject pdfObj = new JSONObject();
            try {
                pdfObj.put("name", pdfFile.getName());
                pdfObj.put("author", pdfFile.getAuthor());
                pdfObj.put("description", pdfFile.getDescription());
                pdfObj.put("path", pdfFile.getLocation());
                pdfObj.put("thumbnailPath", pdfFile.getImagePath());
                pdfObj.put("currPage", pdfFile.getCurrPage());
                pdfObj.put("totalPages", pdfFile.getTotalPages());
                pdfObj.put("isFav", pdfFile.getFavourite());
                pdfArray.put(pdfObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        editor.putString(PDF_CACHE_KEY, pdfArray.toString());
        editor.apply();
    }

    private void loadCachedPDFFiles() {
        String pdfCacheJson = sharedPreferences.getString(PDF_CACHE_KEY, "[]");
        if (!pdfCacheJson.isEmpty()) {
            try {
                JSONArray pdfArray = new JSONArray(pdfCacheJson);
                Log.d("PDFErr", "Loading Cached PDF Files: " + pdfArray.length());
                pdfFiles = new ArrayList<>();
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
                            pdfObj.getBoolean("isFav")
                    );
                    pdfFiles.add(pdfFile);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadPDFFiles() {
        String favListPath = Environment.getExternalStorageDirectory() + "/ReaderForU/BookData/favlist.json";
        JSONObject favList = new JSONObject(); // Initialize an empty JSONObject
        try {
            // Read the contents of the file into a string
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = new BufferedReader(new FileReader(favListPath));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();
            // Parse the string as JSON and assign it to favList
            favList = new JSONObject(stringBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Log.d("PDFErr", "FavList: " + favList.toString());
        //Log.e("PDFErr", "Loading PDF Files");
        // Load PDF files from the storage
        File folder = new File(Environment.getExternalStorageDirectory() + "/Documents");
        File[] files = folder.listFiles();
        File folder1 = new File(Environment.getExternalStorageDirectory() + "/Download");
        File[] files1 = folder1.listFiles();
        ArrayList<File> allFiles = new ArrayList<File>();
        Log.d("PDFErr", "Files: " + allFiles.toString() + " " + files + " " + files1);
        if(files != null) {
            for (File file : files) {
                allFiles.add(file);
            }
        }
        if(files1 != null) {
            for (File file : files1) {
                allFiles.add(file);
            }
        }
        allFiles.sort(Comparator.comparing(File::getName));
        if(allFiles.size() != 0) {
            for (File file : allFiles) {
                if (file.getName().endsWith(".pdf")) {
                    String path = file.getAbsolutePath();
                    String name = file.getName();
                    try {
                        PdfReader reader = new PdfReader(path);
                        PdfDocument pdfDocument = new PdfDocument(reader);
                        int pages = pdfDocument.getNumberOfPages();
                        String title = pdfDocument.getDocumentInfo().getTitle();
                        String author = pdfDocument.getDocumentInfo().getAuthor();
                        if(author == null)
                            author = "Unknown";
                        String description = pdfDocument.getDocumentInfo().getSubject();
                        if(description == null)
                            description = "No description";
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
                        boolean isFav = favList.has(name);
                        int currPage = sharedPreferences.getInt(path + "nowPage", 0);
                        //Log.e("PDFErr", "PDF File: " + name + " " + author + " " + description + " " + path + " " + thumbnail.getAbsolutePath() + " " + currPage + " " + pages);
                        String bookName=(title==null)?name:title;
                        PDFFile pdfFile = new PDFFile(bookName, author, description, path, thumbnail.getAbsolutePath(), currPage, pages, isFav);
                        pdfFiles.add(pdfFile);
                    } catch (Exception e) {
                        Log.e("PDFErr", "Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}