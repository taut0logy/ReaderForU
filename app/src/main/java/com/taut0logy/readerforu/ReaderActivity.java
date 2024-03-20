package com.taut0logy.readerforu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import javax.sql.DataSource;

public class ReaderActivity extends AppCompatActivity implements JumpToPageFragment.JumpToPageListener {
    private static final String PDF_CACHE_KEY = "pdf_cache";
    PDFFile pdfFile;
    private boolean barsVisible = true;
    private int recyclerPosition;
    private ConstraintLayout topBar,bottomBar;
    private TextView tvBookName,tvAuthorName,tvTotalPages,tvCurrPage,etCurrPage;
    private Button showDialog;
    private ImageButton toggleDark,infobtn;
    private ConstraintLayout dialog;
    private PDFView pdfView;
    private String location;
    private boolean isNight=false;
    private int nowPage=0;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        pdfView=findViewById(R.id.pdfView);
        topBar=findViewById(R.id.topBar);
        bottomBar=findViewById(R.id.bottomBar);
        tvBookName=findViewById(R.id.tvBookName);
        tvAuthorName=findViewById(R.id.tvAuthorName);
        tvTotalPages=findViewById(R.id.tvTotalPage);
        etCurrPage=findViewById(R.id.etCurrentPage);
        toggleDark=findViewById(R.id.toggleDark);
        infobtn=findViewById(R.id.infobtn);
        showDialog=findViewById(R.id.showDialog);
        recyclerPosition = getIntent().getIntExtra("position", 0);
        pdfFile = BrowserActivity.getPdfFiles().get(recyclerPosition);
        loadPreferences();
        tvBookName.setText(pdfFile.getName());
        tvAuthorName.setText(pdfFile.getAuthor());
        tvTotalPages.setText(String.valueOf(pdfFile.getTotalPages()));
        location = pdfFile.getLocation();
        toggleDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isNight) {
                    pdfView.setNightMode(false);
                    isNight = false;
                }
                else {
                    pdfView.setNightMode(true);
                    isNight = true;
                }
            }
        });
        showDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showJumpToPageDialog(pdfFile.getTotalPages(),nowPage);
            }
        });

        infobtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(ReaderActivity.this,InfoActivity.class);
                intent.putExtra("position",recyclerPosition);
                startActivity(intent);
            }
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

    private void loadPDFInBackground() {
        // Load the PDF file in the background
        // This method should be called in the background thread
        // because it may take a long time to load the PDF file
        // and we don't want to block the UI thread
        // while the file is being loaded
    }

    private void loadPdf(String location) {
        File file = new File(location);
        PDFView.Configurator configurator = pdfView.fromFile(file);
        configurator.defaultPage(nowPage-1);
        configurator.load();
        configurator.scrollHandle(new com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle(this));
        configurator.onPageChange(new OnPageChangeListener() {
            @Override
            public void onPageChanged(int page, int pageCount) {
                etCurrPage.setText(String.valueOf(page+1));
                nowPage = page+1;
            }
        });

        configurator.onPageScroll(new com.github.barteksc.pdfviewer.listener.OnPageScrollListener() {
            @Override
            public void onPageScrolled(int page, float positionOffset) {
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
            }
        });



        configurator.onTap(new com.github.barteksc.pdfviewer.listener.OnTapListener() {
            @Override
            public boolean onTap(MotionEvent e) {
                toggleBarsVisibility();
                return true;
            }
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
