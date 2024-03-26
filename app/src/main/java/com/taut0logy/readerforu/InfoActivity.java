package com.taut0logy.readerforu;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.annotation.SuppressLint;
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
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class InfoActivity extends AppCompatActivity {
    private static final String PDF_CACHE_KEY = "pdf_cache";
    private Toolbar toolbar;
    private PDFFile pdfFile;
    @SuppressLint("NotifyDataSetChanged")
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
        int position = getIntent().getIntExtra("position", 0);
        pdfFile = BrowserActivity.getPdfFiles().get(getIntent().getIntExtra("position", 0));
        if(pdfFile.getImagePath().equals("__protected")) {
            imageView.setImageResource(R.drawable.lock);
        } else
            imageView.setImageBitmap(pdfFile.getThumbnail());
        bookName.setText(pdfFile.getName());
        authorName.setText(pdfFile.getAuthor());
        description.setText(pdfFile.getDescription());
        totalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        Log.d("PDFErr", pdfFile.toString());
        PdfReader reader;
        try {
            reader = new PdfReader(pdfFile.getLocation());
            PdfDocument pdfDocument_ = new PdfDocument(reader);
            PdfDocumentInfo info = pdfDocument_.getDocumentInfo();
            modified.setText(info.getCreator());
            pdfDocument_.close();
        } catch (Exception e) {
            Log.e("PDFErr", "InfoActivity onCreate: ", e);
            if(Objects.requireNonNull(e.getMessage()).contains("password")) {
                String password = getIntent().getStringExtra("password");
                Log.d("PDFErr", "InfoActivity onCreate: "+password);
                if(password == null) {
                    Toast.makeText(this, "File is encrypted", Toast.LENGTH_SHORT).show();
                    finish();
                }
                byte[] passwordB = getIntent().getStringExtra("password").getBytes();
                try {
                    reader = new PdfReader(pdfFile.getLocation(), new ReaderProperties().setPassword(passwordB));
                    PdfDocument pdfDocument_ = new PdfDocument(reader);
                    PdfDocumentInfo info = pdfDocument_.getDocumentInfo();
                    modified.setText(info.getCreator());
                    pdfDocument_.close();
                } catch (Exception Exception) {
                    Log.e("PDFErr", "infoActivity password: ", e);
                    Toast.makeText(this, "File is encrypted", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
        String sizeStr = (new File(pdfFile.getLocation()).length()/1024)+" KB";
        size.setText(sizeStr);
        location.setText(pdfFile.getLocation());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if(id == R.id.action_favourite) {
                if (pdfFile.getImagePath().equals("__protected")) {
                    Toast.makeText(this, "Can't add locked file to favourites", Toast.LENGTH_SHORT).show();
                    return true;
                }
                MenuItem favButton = toolbar.getMenu().findItem(R.id.action_favourite);
                String pdfFilePath = pdfFile.getLocation();
                try {
                    PdfReader pdfReader = new PdfReader(pdfFilePath);
                    PdfWriter pdfWriter = new PdfWriter(pdfFilePath + "_temp");
                    PdfDocument pdfDocument = new PdfDocument(pdfReader, pdfWriter);
                    PdfDocumentInfo pdfDocumentInfo = pdfDocument.getDocumentInfo();
                    if(pdfFile.getFavourite()) {
                        pdfFile.setFavourite(false);
                        pdfDocumentInfo.setMoreInfo("favourite", "false");
                        BrowserActivity.getFavPdfFiles().remove(pdfFile);
                        favButton.setIcon(R.drawable.baseline_star_border_24);
                    } else {
                        pdfFile.setFavourite(true);
                        pdfDocumentInfo.setMoreInfo("favourite", "true");
                        BrowserActivity.getFavPdfFiles().add(pdfFile);
                        favButton.setIcon(R.drawable.baseline_star_24);
                    }
                    Log.d("PDFErr", "onCreate: "+pdfDocumentInfo.getMoreInfo("favourite"));
                    pdfDocument.close();
                    pdfReader.close();
                    pdfWriter.close();
                    new Thread(() -> {
                        try {
                            java.nio.file.Files.move(java.nio.file.Paths.get(pdfFilePath + "_temp"),
                                    java.nio.file.Paths.get(pdfFilePath),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            Log.e("PDFErr", "onBindViewHolder: ", e);
                            e.printStackTrace();
                        }
                    }).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                SharedPreferences sharedPreferences = getSharedPreferences("reader", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                try {
                    JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                    jsonArray.put(position, pdfFile.toJSON());
                    editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                    editor.apply();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //BrowserActivity.getPdfFileAdapter().updatePDFFileAt(position, pdfFile);
                Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
                BrowserActivity.getPdfFiles().set(position, pdfFile);
                intent.putExtra("position", position);
                //intent.putExtra("pdfFile", pdfFile);
                sendBroadcast(intent);
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
                AlertDialog.Builder builder = getBuilder(position);
                builder.create().show();
                finish();
                return true;
            }
            return false;
        });
    }

    @NonNull
    private AlertDialog.Builder getBuilder(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(InfoActivity.this);
        builder.setTitle("Delete");
        builder.setMessage("Are you sure you want to delete this file?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            File file = new File(pdfFile.getLocation());
            if(file.delete()) {
                SharedPreferences sharedPreferences = getSharedPreferences("reader", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove(BrowserActivity.getPdfFiles().get(position).getLocation());
                try {
                    JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                    jsonArray.remove(position);
                    editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                    editor.apply();
                    File thumbnail = new File(BrowserActivity.getPdfFiles().get(position).getImagePath());
                    boolean res=thumbnail.delete();
                    if(res) {
                        Log.d("PDFFileAdapter", "showConfirmationDialog: Thumbnail deleted");
                    } else {
                        Log.d("PDFFileAdapter", "showConfirmationDialog: Thumbnail not deleted");
                    }
                    //BrowserActivity.getPdfFileAdapter().removePDFFileAt(position);
                    BrowserActivity.getPdfFiles().remove(position);
                    //Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_DELETED");
                    //intent.putExtra("position", position);
                    //sendBroadcast(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Toast.makeText(InfoActivity.this, "Deleted Successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(InfoActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        return builder;
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