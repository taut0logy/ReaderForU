package com.taut0logy.readerforu;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class EditActivity extends AppCompatActivity {
    private EditText etName, etAuthor, etDescription, etCreator;
    private PDFFile pdfFile;
    int position;
    private static final String PDF_CACHE_KEY = "pdf_cache";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        Toolbar toolbar = findViewById(R.id.edit_toolbar);
        ImageView imageView = findViewById(R.id.bookImageViewEdit);
        etName = findViewById(R.id.bookNameEdit);
        etAuthor = findViewById(R.id.authorEdit);
        etDescription = findViewById(R.id.descriptionEdit);
        etCreator = findViewById(R.id.creatorEdit);
        Button saveButton = findViewById(R.id.editSaveButton);
        setSupportActionBar(toolbar);
        position = getIntent().getIntExtra("position", 0);
        pdfFile = BrowserActivity.getPdfFiles().get(position);
        imageView.setImageBitmap(pdfFile.getThumbnail());
        saveButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Save Changes");
            builder.setMessage("Are you sure you want to save the changes?");
            builder.setPositiveButton("Yes", (dialog, which) -> {
                try {
                    saveChanges();
                } catch (JSONException e) {
                    Log.e("EditActivity", "onCreate: ", e);
                    e.printStackTrace();
                }
                finish();
            });
            builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        });
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if(id == R.id.action_about_edit) {
                BrowserActivity.showAboutDialog(this);
                return true;
            }
            if(id == R.id.action_exit_edit) {
                finish();
                return true;
            }
            return false;
        });
    }

    private void saveChanges() throws JSONException {
        String name = etName.getText().toString();
        String author = etAuthor.getText().toString();
        String description = etDescription.getText().toString();
        String creator = etCreator.getText().toString();
        String pdfFilePath = pdfFile.getLocation();
        try {
            // Read the existing PDF document
            PdfReader reader = new PdfReader(pdfFilePath);
            PdfWriter writer = new PdfWriter(pdfFilePath + "_temp");
            PdfDocument pdfDocument = new PdfDocument(reader, writer);
            // Get the document info
            PdfDocumentInfo info = pdfDocument.getDocumentInfo();
            // Update metadata
            if (!name.isEmpty()) {
                pdfFile.setName(name);
                info.setTitle(name);
            }
            if (!author.isEmpty()) {
                pdfFile.setAuthor(author);
                info.setAuthor(author);
            }
            if (!description.isEmpty()) {
                pdfFile.setDescription(description);
                info.setSubject(description);
            }
            if (!creator.isEmpty()) {
                info.setCreator(creator);
            }
            // Close the PDF document
            pdfDocument.close();
            reader.close();
            writer.close();
            // Rename the updated temporary file to the original file
            ((Runnable) () -> {
                try {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Save Changes");
                    builder.setMessage("Saving changes...");
                    builder.setCancelable(false);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    Files.move(Paths.get(pdfFilePath + "_temp"),
                            Paths.get(pdfFilePath),
                            StandardCopyOption.REPLACE_EXISTING);
                    dialog.dismiss();
                } catch (Exception e) {
                    Log.e("PDFErr", "saveChanges: ", e);
                    e.printStackTrace();
                }
            }).run();
        } catch (Exception e) {
            if(Objects.requireNonNull(e.getMessage()).contains("password")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Error");
                builder.setMessage("File is encrypted");
                builder.setCancelable(false);
                builder.setPositiveButton("OK", (dialog, which) -> finish());
                builder.show();
            }
            Log.e("PDFErr", "saveChanges: ", e);
            e.printStackTrace();
        }
        pdfFile.setModified(new File(pdfFile.getLocation()).lastModified());
        //BrowserActivity.getPdfFileAdapter().updatePDFFileAt(position, pdfFile);
        BrowserActivity.getPdfFiles().set(position, pdfFile);
        Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
        intent.putExtra("position", position);
        //intent.putExtra("pdfFile", pdfFile);
        sendBroadcast(intent);
        SharedPreferences sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        try {
            JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
            jsonArray.put(position, pdfFile.toJSON());
            editor.putString(PDF_CACHE_KEY, jsonArray.toString());
            editor.apply();
        } catch (JSONException e) {
            Log.e("EditActivity", "saveChanges: ", e);
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_menu, menu);
        return true;
    }

}