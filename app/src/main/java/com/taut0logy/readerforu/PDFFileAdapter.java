package com.taut0logy.readerforu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.util.List;

public class PDFFileAdapter extends RecyclerView.Adapter<PDFFileAdapter.PDFFileViewHolder>{

    private List<PDFFile> pdfFiles;
    private Context context;

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
        holder.progress.setText(progress+"%");
        Bitmap bitmap = pdfFile.getThumbnail();
        holder.thumbnail.setImageBitmap(bitmap);
        if(pdfFile.getFavourite()) {
            holder.favButton.setImageResource(R.drawable.star_solid);
        } else {
            holder.favButton.setImageResource(R.drawable.star_regular);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, ReaderActivity.class);
                intent.putExtra("pdfFile", pdfFile);
                intent.putExtra("position", position);
                context.startActivity(intent);
            }
        });
        holder.favButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                    holder.favButton.setImageResource(R.drawable.star_regular);
                } else {
                    pdfFile.setFavourite(true);
                    try {
                        jsonObject.put(pdfFile.getName(), true);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    holder.favButton.setImageResource(R.drawable.star_solid);
                }

                // Write updated JSON data back to the file
                try {
                    FileWriter fileWriter = new FileWriter(favListPath);
                    fileWriter.write(jsonObject.toString());
                    fileWriter.flush();
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Update PDF file at the position in the list
                updatePDFFileAt(position, pdfFile);
            }
        });

        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //delete the file
                showConfirmationDialog(context, position);
            }
        });
        holder.editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //edit the file
                Intent intent = new Intent(context, EditActivity.class);
                //intent.putExtra("pdfFile", pdfFile);
                intent.putExtra("position", position);
                context.startActivity(intent);
            }
        });
        holder.infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, InfoActivity.class);
                //intent.putExtra("pdfFile", pdfFile);
                intent.putExtra("position", position);
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pdfFiles.size();
    }

    public class PDFFileViewHolder extends RecyclerView.ViewHolder {

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
        builder.setPositiveButton("Yes", (dialog, which) -> {
            File file = new File(pdfFiles.get(position).getLocation());
            if(file.delete()) {
                pdfFiles.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, pdfFiles.size());
                Toast.makeText(context, "Deleted Successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    public void setPDFFiles(List<PDFFile> pdfFiles) {
        this.pdfFiles = pdfFiles;
        notifyDataSetChanged();
    }

    public PDFFile getPDFFileAt(int position) {
        return pdfFiles.get(position);
    }

    public void removePDFFileAt(int position) {
        pdfFiles.remove(position);
        notifyItemRemoved(position);
    }

    public void addPDFFile(PDFFile pdfFile) {
        pdfFiles.add(pdfFile);
        notifyItemInserted(pdfFiles.size()-1);
    }

    public void updatePDFFileAt(int position, PDFFile pdfFile) {
        pdfFiles.set(position, pdfFile);
        notifyItemChanged(position);
    }

    public Bitmap getThumbnailAt(int position) {
        return pdfFiles.get(position).getThumbnail();
    }
}
