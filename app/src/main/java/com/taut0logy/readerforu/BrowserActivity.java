package com.taut0logy.readerforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
    private PDFFileAdapter pdfFileAdapter;
    private RecyclerView recyclerView;
    private SharedPreferences sharedPreferences;
    Toolbar toolbar;

    protected static ArrayList<PDFFile> getPdfFiles() {
        return pdfFiles;
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
        loadPDFFiles();
        pdfFileAdapter = new PDFFileAdapter(pdfFiles, this);
        recyclerView.setAdapter(pdfFileAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    protected void onStart() {
        for(int i = 0; i < pdfFiles.size(); i++) {
            int currPage = sharedPreferences.getInt(pdfFiles.get(i).getName() + "nowPage", 0);
            pdfFiles.get(i).setCurrPage(currPage);
            pdfFileAdapter.notifyItemChanged(i);
        }
        super.onStart();
    }

    @Override
    protected void onResume() {
        for(int i = 0; i < pdfFiles.size(); i++) {
            int currPage = sharedPreferences.getInt(pdfFiles.get(i).getName() + "nowPage", 0);
            pdfFiles.get(i).setCurrPage(currPage);
            pdfFileAdapter.notifyItemChanged(i);
        }
        super.onResume();
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
        Log.d("PDFErr", "FavList: " + favList.toString());
        Log.e("PDFErr", "Loading PDF Files");
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
                        int currPage = sharedPreferences.getInt(name + "nowPage", 0);
                        Log.e("PDFErr", "PDF File: " + name + " " + author + " " + description + " " + path + " " + thumbnail.getAbsolutePath() + " " + currPage + " " + pages);
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