package com.taut0logy.readerforu;


import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.imageview.ShapeableImageView;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class InfoActivity extends AppCompatActivity {
    private static final String PDF_CACHE_KEY = "pdf_cache";
    private Toolbar toolbar;
    private PDFFile pdfFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        ShapeableImageView imageView;
        TextView bookName,authorName,description,totalPages,size,location, modified;
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
        pdfFile = BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0));
        imageView.setImageBitmap(pdfFile.getThumbnail());
        bookName.setText(pdfFile.getName());
        authorName.setText(pdfFile.getAuthor());
        description.setText(pdfFile.getDescription());
        totalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        Log.d("PDFErr", pdfFile.toString());
        PdfReader reader;
        try {
            reader = new PdfReader(pdfFile.getLocation());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PdfDocument pdfDocument = new PdfDocument(reader);
        PdfDocumentInfo info = pdfDocument.getDocumentInfo();
        modified.setText(info.getCreator());
        String sizeStr = (new File(pdfFile.getLocation()).length()/1024)+" KB";
        size.setText(sizeStr);
        location.setText(pdfFile.getLocation());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if(id == R.id.action_favourite) {
                MenuItem favButton = toolbar.getMenu().findItem(R.id.action_favourite);
                JSONObject jsonObject = BrowserActivity.getPdfFileAdapter().getFavList();
                // Update JSON data based on button click
                if (pdfFile.getFavourite()) {
                    pdfFile.setFavourite(false);
                    jsonObject.remove(pdfFile.getLocation());
                    favButton.setIcon(R.drawable.baseline_star_border_24);
                } else {
                    pdfFile.setFavourite(true);
                    try {
                        jsonObject.put(pdfFile.getLocation(), true);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    favButton.setIcon(R.drawable.baseline_star_24);
                }
                BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0)).setFavourite(pdfFile.getFavourite());
                BrowserActivity.getPdfFileAdapter().notifyItemChanged(getIntent().getIntExtra("position", 0));
                // Write updated JSON data back to the file
                BrowserActivity.getPdfFileAdapter().writeFavList(jsonObject);
                return true;
            }
            if(id == R.id.action_about) {
                BrowserActivity.showAboutDialog(InfoActivity.this);
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
                        int position = getIntent().getIntExtra("position", 0);
                        SharedPreferences sharedPreferences = getSharedPreferences("reader", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove(BrowserActivity.getPdfFiles().get(position).getLocation());
                        try {
                            JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                            jsonArray.remove(position);
                            editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                            editor.apply();
                            JSONObject jsonObject = BrowserActivity.getPdfFileAdapter().getFavList();
                            jsonObject.remove(BrowserActivity.getPdfFiles().get(position).getLocation());
                            BrowserActivity.getPdfFileAdapter().writeFavList(jsonObject);
                            File thumbnail = new File(BrowserActivity.getPdfFiles().get(position).getImagePath());
                            boolean res=thumbnail.delete();
                            if(res) {
                                Log.d("PDFFileAdapter", "showConfirmationDialog: Thumbnail deleted");
                            } else {
                                Log.d("PDFFileAdapter", "showConfirmationDialog: Thumbnail not deleted");
                            }
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.info_menu,menu);
        MenuItem menuItem = toolbar.getMenu().findItem(R.id.action_favourite);
        boolean isFav = BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0)).getFavourite();
        if(isFav) {
            menuItem.setIcon(R.drawable.baseline_star_24);
        } else {
            menuItem.setIcon(R.drawable.baseline_star_border_24);
        }
        return true;
    }
}