package com.taut0logy.readerforu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class PDFFileAdapter extends RecyclerView.Adapter<PDFFileAdapter.PDFFileViewHolder>{

    private final List<PDFFile> pdfFiles;
    private final Context context;
    private static final String PDF_CACHE_KEY = "pdf_cache";

    public PDFFileAdapter(List<PDFFile> pdfFiles, Context context){
        this.pdfFiles = pdfFiles;
        this.context = context;
    }

    @NonNull
    @Override
    public PDFFileAdapter.PDFFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.book_view, parent, false);
        return new PDFFileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PDFFileAdapter.PDFFileViewHolder holder, int position) {
        PDFFile pdfFile = pdfFiles.get(position);
        holder.name.setText(pdfFile.getName());
        holder.author.setText(pdfFile.getAuthor());
        String pages = String.valueOf(pdfFile.getTotalPages());
        holder.totalPages.setText(pages);
        Log.d("PDFFileAdapter", "onBindViewHolder: "+pdfFile.getCurrPage()+" "+pdfFile.getTotalPages());
        float progress = ((float)pdfFile.getCurrPage()/(float)pdfFile.getTotalPages())*100;
        //keep 2 decimal places
        progress = Math.round(progress*100.0)/100.0f;
        String progressStr = progress+"%";
        holder.progress.setText(progressStr);
        Bitmap bitmap = pdfFile.getThumbnail();
        holder.thumbnail.setImageBitmap(bitmap);
        if(pdfFile.getFavourite()) {
            holder.favButton.setImageResource(R.drawable.star_solid);
        } else {
            holder.favButton.setImageResource(R.drawable.star_regular);
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ReaderActivity.class);
            intent.putExtra("pdfFile", pdfFile);
            intent.putExtra("position", position);
            context.startActivity(intent);
        });
        holder.favButton.setOnClickListener(v -> {
            JSONObject jsonObject = getFavList();
            // Update JSON data based on button click
            if (pdfFile.getFavourite()) {
                pdfFile.setFavourite(false);
                jsonObject.remove(pdfFile.getName());
                holder.favButton.setImageResource(R.drawable.star_regular);
            } else {
                pdfFile.setFavourite(true);
                try {
                    jsonObject.put(pdfFile.getLocation(), true);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                holder.favButton.setImageResource(R.drawable.star_solid);
            }
            // Write updated JSON data back to the file
            writeFavList(jsonObject);
            BrowserActivity.getPdfFiles().get(position).setFavourite(pdfFile.getFavourite());
            notifyItemChanged(position);
        });

        holder.deleteButton.setOnClickListener(v -> {
            //delete the file
            showConfirmationDialog(context, position);
        });
        holder.editButton.setOnClickListener(v -> {
            //edit the file
            Intent intent = new Intent(context, EditActivity.class);
            //intent.putExtra("pdfFile", pdfFile);
            intent.putExtra("position", position);
            context.startActivity(intent);
        });
        holder.infoButton.setOnClickListener(v -> {
            Intent intent = new Intent(context, InfoActivity.class);
            //intent.putExtra("pdfFile", pdfFile);
            intent.putExtra("position", position);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return pdfFiles.size();
    }

    public static class PDFFileViewHolder extends RecyclerView.ViewHolder {

        ShapeableImageView thumbnail;
        TextView name, author, totalPages, progress;
        ImageButton favButton, deleteButton, editButton, infoButton;
        public PDFFileViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.book_thumbnail);
            name = itemView.findViewById(R.id.book_name);
            author = itemView.findViewById(R.id.author_name);
            totalPages = itemView.findViewById(R.id.page_cnt);
            progress = itemView.findViewById(R.id.progress_percentage);
            favButton = itemView.findViewById(R.id.favouriteButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            editButton = itemView.findViewById(R.id.editButton);
            infoButton = itemView.findViewById(R.id.infoButton);
        }
    }

    private void showConfirmationDialog(Context context, final int position){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Delete");
        builder.setMessage("Are you sure you want to delete this file?");
        builder.setPositiveButton("Yes", (dialog, which) -> deletePDFFileAt(context, position));
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    public PDFFile getPDFFileAt(int position) {
        return pdfFiles.get(position);
    }

    public void updatePDFFileAt(int position, PDFFile pdfFile) {
        BrowserActivity.getPdfFiles().set(position, pdfFile);
        pdfFiles.set(position, pdfFile);
        notifyItemChanged(position);
    }
    public void removePDFFileAt(int position) {
        BrowserActivity.getPdfFiles().remove(position);
        pdfFiles.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, pdfFiles.size());
    }

    public void addPDFFile(PDFFile pdfFile) {
        BrowserActivity.getPdfFiles().add(pdfFile);
        pdfFiles.add(pdfFile);
        notifyItemInserted(pdfFiles.size()-1);
    }

    public JSONObject getFavList() {
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
        return jsonObject;
    }

    public void writeFavList(JSONObject jsonObject) {
        String favListPath = Environment.getExternalStorageDirectory() + "/ReaderForU/BookData/favlist.json";
        // Write updated JSON data back to the file
        try {
            FileWriter fileWriter = new FileWriter(favListPath);
            fileWriter.write(jsonObject.toString());
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deletePDFFileAt(Context context, int position) {
        File file = new File(pdfFiles.get(position).getLocation());
        if(file.delete()) {
            SharedPreferences sharedPreferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            try {
                JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                jsonArray.remove(position);
                editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                editor.apply();
                JSONObject jsonObject = getFavList();
                jsonObject.remove(pdfFiles.get(position).getLocation());
                writeFavList(jsonObject);
                File thumbnail = new File(pdfFiles.get(position).getImagePath());
                boolean res=thumbnail.delete();
                if(res) {
                    Log.d("PDFFileAdapter", "showConfirmationDialog: Thumbnail deleted");
                } else {
                    Log.d("PDFFileAdapter", "showConfirmationDialog: Thumbnail not deleted");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            removePDFFileAt(position);
            Toast.makeText(context, "Deleted Successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show();
        }
    }
}
