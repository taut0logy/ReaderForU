package com.taut0logy.readerforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.github.barteksc.pdfviewer.PDFView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class ReaderActivity extends AppCompatActivity implements JumpToPageFragment.JumpToPageListener {
    private static final String PDF_CACHE_KEY = "pdf_cache";
    private PDFFile pdfFile;
    private TextView etCurrPage;
    private boolean barsVisible = true;
    private int recyclerPosition;
    private ConstraintLayout topBar,bottomBar;
    private PDFView pdfView;
    private boolean isNight=false;
    private int nowPage=0;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        TextView tvBookName,tvAuthorName,tvTotalPages;
        ImageButton toggleDark, infoBtn;
        Button showDialog;
        pdfView=findViewById(R.id.pdfView);
        topBar=findViewById(R.id.topBar);
        bottomBar=findViewById(R.id.bottomBar);
        tvBookName=findViewById(R.id.tvBookName);
        tvAuthorName=findViewById(R.id.tvAuthorName);
        tvTotalPages=findViewById(R.id.tvTotalPage);
        etCurrPage=findViewById(R.id.etCurrentPage);
        toggleDark=findViewById(R.id.toggleDark);
        infoBtn =findViewById(R.id.infobtn);
        showDialog=findViewById(R.id.showDialog);
        recyclerPosition = getIntent().getIntExtra("position", 0);
        pdfFile = BrowserActivity.getPdfFiles().get(recyclerPosition);
        loadPreferences();
        tvBookName.setText(pdfFile.getName());
        tvAuthorName.setText(pdfFile.getAuthor());
        tvTotalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        String location = pdfFile.getLocation();
        toggleDark.setOnClickListener(v -> {
            if(isNight) {
                pdfView.setNightMode(false);
                isNight = false;
            }
            else {
                pdfView.setNightMode(true);
                isNight = true;
            }
        });
        showDialog.setOnClickListener(v -> showJumpToPageDialog(pdfFile.getTotalPages(),nowPage));

        infoBtn.setOnClickListener(v -> {
            Intent intent=new Intent(ReaderActivity.this,InfoActivity.class);
            intent.putExtra("position",recyclerPosition);
            startActivity(intent);
        });
        loadPdf(location);
    }

    @Override
    protected void onPause() {
        BrowserActivity.getPdfFiles().get(recyclerPosition).setCurrPage(nowPage);
        BrowserActivity.getPdfFileAdapter().notifyItemChanged(recyclerPosition);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isNight", isNight);
        editor.putInt(pdfFile.getLocation()+"nowPage", nowPage);
        try {
            JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
            JSONObject jsonObject = jsonArray.getJSONObject(recyclerPosition);
            jsonObject.put("currPage", nowPage);
            jsonArray.put(recyclerPosition, jsonObject);
            editor.putString(PDF_CACHE_KEY, jsonArray.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        editor.apply();
        super.onPause();
    }

    @Override
    protected void onStop() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isNight", isNight);
        editor.putInt(pdfFile.getLocation()+"nowPage", nowPage);
        try {
            JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
            JSONObject jsonObject = jsonArray.getJSONObject(recyclerPosition);
            jsonObject.put("currPage", nowPage);
            jsonArray.put(recyclerPosition, jsonObject);
            editor.putString(PDF_CACHE_KEY, jsonArray.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        editor.apply();
        Log.d("ReaderActivity", "onStop: "+isNight+" "+nowPage);
        super.onStop();
    }

    private void loadPreferences() {
        sharedPreferences = getSharedPreferences("reader", MODE_PRIVATE);
        isNight = sharedPreferences.getBoolean("isNight", false);
        nowPage = sharedPreferences.getInt(pdfFile.getLocation()+"nowPage", 0);
    }

    private void loadPdf(String location) {
        File file = new File(location);
        PDFView.Configurator configurator = pdfView.fromFile(file);
        configurator.defaultPage(nowPage-1);
        configurator.load();
        configurator.scrollHandle(new com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle(this));
        configurator.onPageChange((page, pageCount) -> {
            etCurrPage.setText(String.valueOf(page+1));
            nowPage = page+1;
        });

        configurator.onPageScroll((page, positionOffset) -> {
            //get direction of scroll
            if(positionOffset>0) {
                //scrolling down
                if(barsVisible) {
                    hideBarsWithAnimation();
                }
            }
            else {
                //scrolling up
                if(!barsVisible) {
                    showBarsWithAnimation();
                }
            }
        });
        configurator.onTap(e -> {
            toggleBarsVisibility();
            return true;
        });
    }
    private void showJumpToPageDialog(int totalPage,int curPage) {
        JumpToPageFragment dialogFragment = new JumpToPageFragment(totalPage,curPage);
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
        topBar.animate().translationY(-topBar.getHeight()-100).setDuration(300).start();
        bottomBar.animate().translationY(bottomBar.getHeight()+100).setDuration(300).start();
    }
}
