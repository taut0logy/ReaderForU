package com.taut0logy.readerforu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.barteksc.pdfviewer.PDFView;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.ReaderProperties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ReaderActivity extends AppCompatActivity implements JumpToPageFragment.JumpToPageListener {
    private static final String PDF_CACHE_KEY = "pdf_cache";
    private PDFFile pdfFile;
    private TextView etCurrPage;
    TextView tvBookName, tvAuthorName, tvTotalPages;
    private boolean barsVisible = true;
    private int recyclerPosition;
    private ConstraintLayout topBar, bottomBar;
    private PDFView pdfView;
    private boolean isNight = false;
    private int nowPage = 0;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        ImageButton toggleDark, infoBtn;
        Button showDialog;
        pdfView = findViewById(R.id.pdfView);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        tvBookName = findViewById(R.id.tvBookName);
        tvAuthorName = findViewById(R.id.tvAuthorName);
        tvTotalPages = findViewById(R.id.tvTotalPage);
        etCurrPage = findViewById(R.id.etCurrentPage);
        toggleDark = findViewById(R.id.toggleDark);
        infoBtn = findViewById(R.id.infobtn);
        showDialog = findViewById(R.id.showDialog);
        recyclerPosition = getIntent().getIntExtra("position", 0);
        pdfFile = BrowserActivity.getPdfFiles().get(recyclerPosition);
        pdfFile.setLastRead(System.currentTimeMillis());
        loadPreferences();
        tvBookName.setText(pdfFile.getName());
        tvAuthorName.setText(pdfFile.getAuthor());
        tvTotalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        String location = pdfFile.getLocation();
        toggleDark.setOnClickListener(v -> {
            if (isNight) {
                pdfView.setNightMode(false);
                isNight = false;
                pdfView.loadPages();
            } else {
                pdfView.setNightMode(true);
                isNight = true;
                pdfView.loadPages();
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isNight", isNight);
            editor.apply();
        });
        showDialog.setOnClickListener(v -> showJumpToPageDialog(pdfFile.getTotalPages(), nowPage));

        infoBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ReaderActivity.this, InfoActivity.class);
            intent.putExtra("position", recyclerPosition);
            startActivity(intent);
        });
        loadPdf(location);
    }

    @Override
    protected void onPause() {
        pdfFile.setCurrPage(nowPage);
        //BrowserActivity.getPdfFileAdapter().updatePDFFileAt(recyclerPosition, pdfFile);
        BrowserActivity.getPdfFiles().set(recyclerPosition, pdfFile);
        Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
        intent.putExtra("position", recyclerPosition);
        intent.putExtra("pdfFile", pdfFile);
        sendBroadcast(intent);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        try {
            JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
            JSONObject jsonObject = jsonArray.getJSONObject(recyclerPosition);
            jsonObject.put("currPage", nowPage);
            jsonArray.put(recyclerPosition, jsonObject);
            editor.putString(PDF_CACHE_KEY, jsonArray.toString());
        } catch (JSONException ex) {
            Log.d("PDFErr", "onPause: " + ex.getMessage());
            ex.printStackTrace();
        }
        editor.apply();
        super.onPause();
    }

    @Override
    protected void onStop() {
        pdfFile.setCurrPage(nowPage);
        //BrowserActivity.getPdfFileAdapter().updatePDFFileAt(recyclerPosition, pdfFile);
        BrowserActivity.getPdfFiles().set(recyclerPosition, pdfFile);
        Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_UPDATED");
        intent.putExtra("position", recyclerPosition);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isNight", isNight);
        editor.putInt(pdfFile.getLocation() + "nowPage", nowPage);
        editor.apply();
        super.onStop();
    }

    private void loadPreferences() {
        //BrowserActivity.getPdfFileAdapter().updatePDFFileAt(recyclerPosition, pdfFile);
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        isNight = sharedPreferences.getBoolean("isNight", false);
        nowPage = sharedPreferences.getInt(pdfFile.getLocation() + "nowPage", 0);
        Log.d("PDFErr", "loadPreferences: " + nowPage + " " + isNight);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        new Thread(() -> {
            editor.putLong(pdfFile.getLocation() + "_lastRead", pdfFile.getLastRead());
            try {
                JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                jsonArray.put(recyclerPosition, pdfFile.toJSON());
                editor.putString(PDF_CACHE_KEY, jsonArray.toString());
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
            editor.apply();
        }).start();
    }

    private void loadPdf(String location) {
        Log.e("PDFErr", "loadPdf: " + location + " " + nowPage);
        AtomicInteger res= new AtomicInteger(1);
        File file = new File(location);
        PDFView.Configurator configurator;
        configurator = pdfView.fromFile(file).onError(t -> {
            Log.e("PDFErr", "loadPdf: ", t);
            if (Objects.requireNonNull(t.getMessage()).contains("password")) {
                res.set(promptUserForPassword(location));
            } else {
                Log.e("PDFErr", "loadPdf: ", t);
                finish();
            }
        });
        if(res.get() == 0) return;
        configurator.defaultPage(Math.max(0, nowPage - 1));
        configurator.load();
        configurePdfView(configurator);
//        try {
//            configurator.load();
//            configurePdfView(configurator);
//        } catch (Exception e) {
//            Log.e("PDFErr", "loadPdf: ", e);
//            if (Objects.requireNonNull(e.getMessage()).contains("password")) {
//                promptUserForPassword(location);
//            } else {
//                finish();
//            }
//            return;
//        }
        //configurePdfView(configurator);
    }

    private void configurePdfView(PDFView.Configurator configurator) {

        configurator.scrollHandle(new com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle(this));
        configurator.onPageChange((page, pageCount) -> {
            etCurrPage.setText(String.valueOf(page + 1));
            nowPage = page + 1;
        });
        configurator.onPageScroll((page, positionOffset) -> {
            //get direction of scroll
            if (positionOffset > 0) {
                //scrolling down
                if (barsVisible) {
                    hideBarsWithAnimation();
                }
            } else {
                //scrolling up
                if (!barsVisible) {
                    showBarsWithAnimation();
                }
            }
        });
        configurator.onTap(e -> {
            toggleBarsVisibility();
            return true;
        });
        if (isNight) {
            pdfView.setNightMode(true);
        }
    }

    private int promptUserForPassword(String location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.pdf_password_dialog, null);
        builder.setView(view);
        EditText passwordEditText = view.findViewById(R.id.etPassword);
        Button okButton = view.findViewById(R.id.submitPassword);
        Button cancelButton = view.findViewById(R.id.cancelPassword);
        AlertDialog dialog = builder.create();
        okButton.setOnClickListener(v -> {
            String password = passwordEditText.getText().toString();
            try {
                loadPdfWithPassword(location, password);
                dialog.dismiss();
            } catch (Exception e) {
                AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
                builder1.setTitle("Error");
                builder1.setMessage("Invalid password");
                builder1.setPositiveButton("OK", (dialog1, which1) -> finish());
                builder1.show();
                Log.e("PDFErr", "promptUserForPassword: ", e);
            }
        });
        cancelButton.setOnClickListener(v -> finish());
        dialog.show();
        return 0;
    }

    private void loadPdfWithPassword(String location, String password) throws Exception  {
        Log.e("PDFErr", "loadPdfWithPassword: " + location + " " + nowPage);
        File file = new File(location);
        PDFView.Configurator configurator = pdfView.fromFile(file).password(password).onError(t -> {
            Log.e("PDFErr", "loadPdfWithPassword: ", t);
            throw new RuntimeException(t);
        });
        configurator.defaultPage(Math.max(0, nowPage - 1));
        configurePdfView(configurator);
        configurator.load();
        //open pdfdocument with password\
        Log.e("PDFErr", "loadPdfWithPassword: success");
        byte[] bytes = password.getBytes();
        new Intent(ReaderActivity.this, InfoActivity.class).putExtra("password", password);
        PdfDocument pdfDocument = new PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(location, new ReaderProperties().setPassword(bytes)));
        PdfDocumentInfo pdfDocumentInfo = pdfDocument.getDocumentInfo();
        tvBookName.setText(pdfFile.getName());
        tvAuthorName.setText((pdfDocumentInfo.getAuthor()!=null && pdfDocumentInfo.getAuthor().isEmpty()) ? "Unknown author" : pdfDocumentInfo.getAuthor());
        tvTotalPages.setText(String.valueOf(pdfDocument.getNumberOfPages()));
        pdfDocument.close();
        //configurator.password(password);

    }

    private void showJumpToPageDialog(int totalPage, int curPage) {
        JumpToPageFragment dialogFragment = new JumpToPageFragment(totalPage, curPage);
        dialogFragment.setJumpToPageListener(this);
        dialogFragment.show(getSupportFragmentManager(), "JumpToPageDialogFragment");
    }

    @Override
    public void onJumpToPage(int pageNumber) {
        // Handle jumping to the specified page here
        pdfView.jumpTo(pageNumber - 1);
    }

    private void toggleBarsVisibility() {
        if (barsVisible) {
            hideBarsWithAnimation();
        } else {
            showBarsWithAnimation();
        }
        barsVisible = !barsVisible;
    }

    private void showBarsWithAnimation() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        topBar.animate().translationY(0).setDuration(300).start();
        bottomBar.animate().translationY(0).setDuration(300).start();
    }

    private void hideBarsWithAnimation() {
        topBar.animate().translationY(-topBar.getHeight() - 100).setDuration(300).start();
        bottomBar.animate().translationY(bottomBar.getHeight() + 100).setDuration(300).start();
    }
}
