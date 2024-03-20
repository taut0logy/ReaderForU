package com.taut0logy.readerforu;


import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class InfoActivity extends AppCompatActivity {
    private static final String PDF_CACHE_KEY = "pdf_cache";
    private Toolbar toolbar;
    private ImageView imageView;
    private TextView bookName,authorName,description,totalPages,size,location, modified;
    private PDFFile pdfFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
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
        //pdfFile = (PDFFile) getIntent().getSerializableExtra("pdfFile");
        pdfFile = BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0));
        imageView.setImageBitmap(pdfFile.getThumbnail());
        bookName.setText(pdfFile.getName());
        authorName.setText(pdfFile.getAuthor());
        description.setText(pdfFile.getDescription());
        totalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFile.getLocation());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PdfDocument pdfDocument = new PdfDocument(reader);
        PdfDocumentInfo info = pdfDocument.getDocumentInfo();
        modified.setText(pdfDocument.getDocumentInfo().getCreator());
        size.setText(String.valueOf(new File(pdfFile.getLocation()).length()/1024)+" KB");
        location.setText(pdfFile.getLocation());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if(id == R.id.action_favourite) {
                MenuItem favButton = toolbar.getMenu().findItem(R.id.action_favourite);
                String favListPath = Environment.getExternalStorageDirectory() + "/ReaderForU/BookData/favlist.json";
                // Initialize JSONObjects
                JSONObject jsonObject = new JSONObject();
                FileInputStream fileInputStream = null;
                InputStreamReader inputStreamReader = null;
                BufferedReader bufferedReader = null;
                try {
                    // Read existing JSON data from the file
                    fileInputStream = new FileInputStream(favListPath);
                    inputStreamReader = new InputStreamReader(fileInputStream);
                    bufferedReader = new BufferedReader(inputStreamReader);
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    // Parse the JSON data
                    String jsonString = stringBuilder.toString();
                    if (!jsonString.isEmpty()) {
                        jsonObject = new JSONObject(jsonString);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // Close resources
                    try {
                        if (fileInputStream != null) {
                            fileInputStream.close();
                        }
                        if (inputStreamReader != null) {
                            inputStreamReader.close();
                        }
                        if (bufferedReader != null) {
                            bufferedReader.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Update JSON data based on button click
                if (pdfFile.getFavourite()) {
                    pdfFile.setFavourite(false);
                    jsonObject.remove(pdfFile.getName());
                    favButton.setIcon(R.drawable.baseline_star_border_24);
                } else {
                    pdfFile.setFavourite(true);
                    try {
                        jsonObject.put(pdfFile.getName(), true);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    favButton.setIcon(R.drawable.baseline_star_24);
                }
//                ArrayList<PDFFile> pdfFiles = BrowserActivity.getPdfFiles();
//                for(int i = 0; i < pdfFiles.size(); i++) {
//                    if(pdfFiles.get(i).getName().equals(pdfFile.getName())) {
//                        pdfFiles.get(i).setFavourite(pdfFile.getFavourite());
//                    }
//                }
                BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0)).setFavourite(pdfFile.getFavourite());
                // Write updated JSON data back to the file
                try {
                    FileWriter fileWriter = new FileWriter(favListPath);
                    fileWriter.write(jsonObject.toString());
                    fileWriter.flush();
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
            if(id == R.id.action_about) {
                Intent intent = new Intent(InfoActivity.this, AboutActivity.class);
                startActivity(intent);
                return true;
            }
            if(id == R.id.action_edit) {
                Intent intent = new Intent(InfoActivity.this, EditActivity.class);
                //intent.putExtra("pdfFile", pdfFile);
                intent.putExtra("position", getIntent().getIntExtra("position", 0));
                startActivity(intent);
                return true;
            }
            if(id == R.id.action_delete) {
                AlertDialog.Builder builder = new AlertDialog.Builder(InfoActivity.this);
                builder.setTitle("Delete");
                builder.setMessage("Are you sure you want to delete this file?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    File file = new File(pdfFile.getLocation());
                    if(file.delete()) {
                        BrowserActivity.getPdfFiles().remove(getIntent().getIntExtra("position", 0));
                        BrowserActivity.getPdfFileAdapter().notifyItemRemoved(getIntent().getIntExtra("position", 0));
                        SharedPreferences sharedPreferences = getSharedPreferences("reader", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove(BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0)).getLocation());
                        try {
                            JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                            jsonArray.remove(getIntent().getIntExtra("position", 0));
                            editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                            editor.apply();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Toast.makeText(InfoActivity.this, "Deleted Successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(InfoActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show();
                    }
                });
                builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                builder.create().show();
                return true;
            }
            return false;
        });
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //getSupportActionBar().setDisplayShowHomeEnabled(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.info_menu,menu);
        MenuItem menuItem = toolbar.getMenu().findItem(R.id.action_favourite);
        if(pdfFile.getFavourite()) {
            menuItem.setIcon(R.drawable.baseline_star_24);
        } else {
            menuItem.setIcon(R.drawable.baseline_star_border_24);
        }
        return true;
    }
}