package com.taut0logy.readerforu;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PDFFileAdapter extends RecyclerView.Adapter<PDFFileAdapter.PDFFileViewHolder> implements Filterable {

    private List<PDFFile> pdfFiles;
    private List<PDFFile> filteredPdfFiles;
    private final Context context;
    private static final String PDF_CACHE_KEY = "pdf_cache";

    public PDFFileAdapter(List<PDFFile> pdfFiles, List<PDFFile> filteredPdfFiles, Context context){
        this.pdfFiles = pdfFiles;
        this.filteredPdfFiles = filteredPdfFiles;
        this.context = context;
        Log.d("PDFErr", "PDFFileAdapter constructor: "+ pdfFiles.size() + " "+filteredPdfFiles.size());
    }

    @NonNull
    @Override
    public PDFFileAdapter.PDFFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.book_view, parent, false);
        return new PDFFileViewHolder(view);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBindViewHolder(@NonNull PDFFileAdapter.PDFFileViewHolder holder, int position) {
        PDFFile pdfFile= filteredPdfFiles.get(position);
        holder.name.setText(pdfFile.getName());
        holder.author.setText(pdfFile.getAuthor());
        String pages = String.valueOf(pdfFile.getTotalPages());
        holder.totalPages.setText(pages);
        Log.d("PDFFileAdapter", "onBindViewHolder check: "+pdfFile.getCurrPage()+" "+pdfFile.getTotalPages());
        float progress = ((float)pdfFile.getCurrPage()/(float)pdfFile.getTotalPages())*100;
        if(pdfFile.getTotalPages() == 0) {
            progress = 0;
        }
        progress = Math.round(progress*100.0)/100.0f;
        holder.progress.setProgress((int)progress, true);
        if(pdfFile.getImagePath().equals("__protected")) {
            holder.thumbnail.setImageResource(R.drawable.lock);
        } else {
            Bitmap bitmap = pdfFile.getThumbnail();
            holder.thumbnail.setImageBitmap(bitmap);
        }
        if(pdfFile.getFavourite()) {
            holder.favButton.setImageResource(R.drawable.star_solid);
        } else {
            holder.favButton.setImageResource(R.drawable.star_regular);
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ReaderActivity.class);
            int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            intent.putExtra("position", position1);
            context.startActivity(intent);
        });
        holder.favButton.setOnClickListener(v -> {
            if(pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "Can't add locked file to favourites", Toast.LENGTH_SHORT).show();
                return;
            }
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
                    holder.favButton.setImageResource(R.drawable.star_regular);
                } else {
                    pdfFile.setFavourite(true);
                    pdfDocumentInfo.setMoreInfo("favourite", "true");
                    BrowserActivity.getFavPdfFiles().add(pdfFile);
                    holder.favButton.setImageResource(R.drawable.star_solid);
                }
                Log.d("PDFErr", "onBindViewHolder: "+pdfDocumentInfo.getMoreInfo("favourite"));
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
            } catch (Exception e) {
                Log.e("PDFErr", "onBindViewHolder: " + e.getClass().getName(), e);
                e.printStackTrace();
            }
            int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            SharedPreferences sharedPreferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            try {
                JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                jsonArray.put(position1, pdfFile.toJSON());
                editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                editor.apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
            pdfFiles.set(position1, pdfFile);
            notifyItemChanged(position);
        });

        holder.deleteButton.setOnClickListener(v -> {
            //delete the file
            showConfirmationDialog(context, position);
        });
        holder.editButton.setOnClickListener(v -> {
            if(pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "This file is protected", Toast.LENGTH_SHORT).show();
                return;
            }
            //edit the file
            Intent intent = new Intent(context, EditActivity.class);
            int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            intent.putExtra("position", position1);
            context.startActivity(intent);
        });
        holder.infoButton.setOnClickListener(v -> {
            if(pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "This file is protected", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(context, InfoActivity.class);
            int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            intent.putExtra("position", position1);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return filteredPdfFiles.size();
    }

    @Override
    public Filter getFilter() {
        return searchFilter;
    }

    Filter searchFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<PDFFile> filteredList = new ArrayList<>();
            if(constraint == null || constraint.length() == 0) {
                filteredList.addAll(pdfFiles);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for(PDFFile pdfFile : pdfFiles) {
                    if(pdfFile.getName().toLowerCase().contains(filterPattern) || pdfFile.getAuthor().toLowerCase().contains(filterPattern)) {
                        filteredList.add(pdfFile);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredPdfFiles.clear();
            filteredPdfFiles.addAll((Collection<? extends PDFFile>) results.values);
            notifyDataSetChanged();
            Log.d("PDFErr", "publishResults: "+filteredPdfFiles.size() + " "+pdfFiles.size() + " "+results.values);
        }
    };

    public static class PDFFileViewHolder extends RecyclerView.ViewHolder {

        ShapeableImageView thumbnail;
        TextView name, author, totalPages;
        ImageButton favButton, deleteButton, editButton, infoButton;
        ProgressBar progress;
        public PDFFileViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.book_thumbnail);
            name = itemView.findViewById(R.id.book_name);
            author = itemView.findViewById(R.id.author_name);
            totalPages = itemView.findViewById(R.id.page_cnt);
            progress = itemView.findViewById(R.id.progressBar);
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
        //BrowserActivity.getPdfFiles().set(position, pdfFile);
        pdfFiles.set(position, pdfFile);
        notifyItemChanged(position);
    }
    public void removePDFFileAt(int position) {
        //BrowserActivity.getPdfFiles().remove(position);
        pdfFiles.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, pdfFiles.size());
    }

    public void addPDFFile(PDFFile pdfFile) {
        //BrowserActivity.getPdfFiles().add(pdfFile);
        pdfFiles.add(pdfFile);
        notifyItemInserted(pdfFiles.size()-1);
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
            Toast.makeText(context, "Deleted Successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "File doesn't exist", Toast.LENGTH_SHORT).show();
        }
        //removePDFFileAt(position);
        int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFiles.get(position));
        pdfFiles.remove(position1);
        filteredPdfFiles.remove(position);
        notifyItemRemoved(position);
    }
}
