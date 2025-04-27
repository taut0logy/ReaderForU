package com.taut0logy.readerforu;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatDialogFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


public class JumpToPageFragment extends AppCompatDialogFragment {

    private EditText jumpInput;
    private final int pageCnt;
    private final int currPage;

    public interface JumpToPageListener {
        void onJumpToPage(int pageNumber);
    }

    public JumpToPageFragment(int n,int m){this.pageCnt =n;this.currPage =m;}

    private JumpToPageListener listener;

    public void setJumpToPageListener(JumpToPageListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
    }

    private void applyTheme() {
        // Get the same shared preferences used in BrowserActivity
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("reader", Context.MODE_PRIVATE);
        String theme = sharedPreferences.getString("theme", "system"); // Default to system
        
        // Set theme based on preference
        switch (theme) {
            case "light":
                setTheme(R.style.Base_Theme_ReaderForU);
                break;
            case "dark":
                setTheme(R.style.Theme_ReaderForU);
                break;
            default: // "system"
                break;
        }
    }

    // Helper method to set theme
    private void setTheme(int themeResId) {
        if (getActivity() != null) {
            getActivity().setTheme(themeResId);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_jump_to_page, container, false);
        jumpInput = view.findViewById(R.id.jumpInp);
        Button jumpButton = view.findViewById(R.id.jumpbtn);
        TextView totalPage = view.findViewById(R.id.totalPage);
        jumpInput.setHint(String.valueOf(currPage));
        String s = "/"+ pageCnt;
        totalPage.setText(s);
        Log.d("JumpToPageFragment", "onCreateView: "+ pageCnt +" "+ currPage);
        jumpButton.setOnClickListener(v -> {
            if(jumpInput.getText().toString().isEmpty())
                jumpInput.setText(String.valueOf(currPage));
            int pageNumber = Integer.parseInt(jumpInput.getText().toString());
            if(pageNumber>0 && pageNumber<= pageCnt)
                jumpToPage();
            else
                jumpInput.setError("Invalid Page Number");
        });
        return view;
    }

    private void jumpToPage() {
        if (listener != null) {
            String pageNumberStr = jumpInput.getText().toString();
            if (!pageNumberStr.isEmpty()) {
                int pageNumber = Integer.parseInt(pageNumberStr);
                listener.onJumpToPage(pageNumber);
                dismiss(); // Close the dialog after jumping to the page
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle("Jump to Page");
        return dialog;
    }
}