package com.taut0logy.readerforu;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;

public class EditActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ImageView imageView;
    private EditText etName, etAuthor, etDescription, etCreator;
    private PDFFile pdfFile;
    int position;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);
        toolbar = findViewById(R.id.edit_toolbar);
        imageView = findViewById(R.id.bookImageViewEdit);
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
                saveChanges();
                finish();
            });
            builder.setNegativeButton("No", (dialog, which) -> {
                dialog.dismiss();
            });
            builder.create().show();
        });
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if(id == R.id.action_about_edit) {
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            }
            if(id == R.id.action_exit_edit) {
                finish();
                return true;
            }
            return false;
        });
    }

    private void saveChanges() {
        String name = etName.getText().toString();
        String author = etAuthor.getText().toString();
        String description = etDescription.getText().toString();
        String creator = etCreator.getText().toString();
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdfFile.getLocation());
        } catch (Exception e) {
            e.printStackTrace();
        }
        PdfDocument pdfDocument = new PdfDocument(reader);
        PdfDocumentInfo info = pdfDocument.getDocumentInfo();
        if(!name.isEmpty()) {
            info.setTitle(name);
            pdfFile.setName(name);
        }
        if(!author.isEmpty()) {
            info.setAuthor(author);
            pdfFile.setAuthor(author);
        }
        if(!description.isEmpty()) {
            info.setSubject(description);
            pdfFile.setDescription(description);
        }
        if(!creator.isEmpty()) {
            info.setCreator(creator);
        }
        BrowserActivity.getPdfFiles().set(position, pdfFile);
        pdfDocument.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_menu, menu);
        return true;
    }

}