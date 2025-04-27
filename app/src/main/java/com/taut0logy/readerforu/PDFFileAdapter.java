package com.taut0logy.readerforu;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.taut0logy.readerforu.data.PDFFile;
import com.taut0logy.readerforu.data.PDFRepository;
import com.taut0logy.readerforu.util.ThumbnailUtils;

import org.json.JSONArray;

import java.io.File;
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
        if(pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
            holder.thumbnail.setImageResource(R.drawable.lock);
        } else {
            Bitmap bitmap = pdfFile.getThumbnail();
            if(bitmap == null) {
                // Try to load thumbnail from path if available
                if(pdfFile.getImagePath() != null && !pdfFile.getImagePath().isEmpty()) {
                    try {
                        bitmap = BitmapFactory.decodeFile(pdfFile.getImagePath());
                        // Cache the bitmap in memory for faster access next time
                        if(bitmap != null) {
                            pdfFile.setThumbnail(bitmap);
                        }
                    } catch (Exception e) {
                        Log.e("PDFFileAdapter", "Error loading thumbnail: " + e.getMessage());
                    }
                }
                
                // If still null, use default icon
                if(bitmap == null) {
                    bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.sample);
                }
            }
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
            if(pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "Can't add locked file to favourites", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String pdfFilePath = pdfFile.getLocation();
            boolean success = false;
            
            // Update the favorite status in the database first (this should work for all files)
            try {
                // Get repository instance to update the database
                PDFRepository repository = new PDFRepository(context);
                repository.open();
                repository.updateFavouriteStatus(pdfFilePath, !pdfFile.getFavourite());
                repository.close();
                success = true;
            } catch (Exception e) {
                Log.e("PDFErr", "Error updating database: ", e);
            }
            
            if (success) {
                // Toggle the favorite status
                boolean newFavoriteStatus = !pdfFile.getFavourite();
                pdfFile.setFavourite(newFavoriteStatus);
                
                // Update UI
                if (newFavoriteStatus) {
                    BrowserActivity.getFavPdfFiles().add(pdfFile);
                    holder.favButton.setImageResource(R.drawable.star_solid);
                } else {
                    BrowserActivity.getFavPdfFiles().remove(pdfFile);
                    holder.favButton.setImageResource(R.drawable.star_regular);
                }
                
                // Update cache
                int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
                SharedPreferences sharedPreferences = context.getSharedPreferences("reader", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                try {
                    JSONArray jsonArray = new JSONArray(sharedPreferences.getString(PDF_CACHE_KEY, "[]"));
                    jsonArray.put(position1, pdfFile.toJSON());
                    editor.putString(PDF_CACHE_KEY, jsonArray.toString());
                    editor.apply();
                } catch (Exception e) {
                    Log.e("PDFErr", "Cache update error: ", e);
                }
                
                // Update lists and notify adapter
                pdfFiles.set(position1, pdfFile);
                notifyItemChanged(position);
            } else {
                Toast.makeText(context, "Failed to update favorite status", Toast.LENGTH_SHORT).show();
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            //delete the file
            showConfirmationDialog(context, position);
        });
        holder.editButton.setOnClickListener(v -> {
            if(pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "This file is protected", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(context, EditActivity.class);
            int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            intent.putExtra("position", position1);
            intent.putExtra("file_location", pdfFile.getLocation());
            
            // Add URI data if it's a content URI
            if (pdfFile.getLocation().startsWith("content://")) {
                intent.setData(Uri.parse(pdfFile.getLocation()));
            }
            
            context.startActivity(intent);
        });
        holder.infoButton.setOnClickListener(v -> {
            if(pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "This file is protected", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(context, InfoActivity.class);
            int position1 = BrowserActivity.getPdfFiles().indexOf(pdfFile);
            intent.putExtra("position", position1);
            intent.putExtra("file_location", pdfFile.getLocation());
            context.startActivity(intent);
        });

        holder.shareButton.setOnClickListener(v -> {
            if(pdfFile.getImagePath() != null && pdfFile.getImagePath().equals("__protected")) {
                Toast.makeText(context, "This file is protected", Toast.LENGTH_SHORT).show();
                return;
            }
            sharePDF(v, context, pdfFile.getLocation());
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
        ImageButton favButton, deleteButton, editButton, infoButton, shareButton;
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
            shareButton = itemView.findViewById(R.id.shareButton);
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
        if (position < 0 || position >= filteredPdfFiles.size()) {
            Log.e("PDFFileAdapter", "Invalid position for deletion: " + position);
            return;
        }
        
        // Get the PDF file to delete from filtered list (since that's what the user sees)
        PDFFile pdfToDelete = filteredPdfFiles.get(position);
        String filePath = pdfToDelete.getLocation();
        
        // Check if it's a content URI or a file path
        if (filePath.startsWith("content://")) {
            // For content URIs, we'll use the database and broadcast to notify other components
            try {
                // Get the PDFRepository to delete from database
                PDFRepository repository = new PDFRepository(context);
                repository.open();
                repository.deletePDFFile(filePath);
                repository.close();
                
                // Delete the thumbnail if it exists
                if (pdfToDelete.getImagePath() != null && !pdfToDelete.getImagePath().isEmpty() && 
                    !pdfToDelete.getImagePath().equals("__protected")) {
                    ThumbnailUtils.deleteThumbnail(pdfToDelete.getImagePath());
                }
                
                // Get the position in the master list for broadcasting
                int masterPosition = -1;
                synchronized (BrowserActivity.getPdfFiles()) {
                    for (int i = 0; i < BrowserActivity.getPdfFiles().size(); i++) {
                        if (BrowserActivity.getPdfFiles().get(i).getLocation().equals(filePath)) {
                            masterPosition = i;
                            break;
                        }
                    }
                }
                
                // Update all lists atomically
                synchronized (pdfFiles) {
                    // Remove from master list
                    if (masterPosition != -1) {
                        BrowserActivity.getPdfFiles().remove(masterPosition);
                    }
                    
                    // Remove from favorites list if present
                    if (pdfToDelete.getFavourite()) {
                        BrowserActivity.getFavPdfFiles().remove(pdfToDelete);
                    }
                    
                    // Remove from adapter's lists
                    pdfFiles.remove(pdfToDelete);
                    filteredPdfFiles.remove(position);
                }
                
                // Notify adapter
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, filteredPdfFiles.size());
                
                // Send broadcast to ensure consistency across all components
                Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_DELETED");
                intent.putExtra("position", masterPosition);
                intent.putExtra("path", filePath);
                context.sendBroadcast(intent);
                
                Toast.makeText(context, "File deleted from library", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("PDFFileAdapter", "Error deleting content URI file: " + e.getMessage(), e);
                Toast.makeText(context, "Error deleting file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            // For file path, try to delete the actual file
            File file = new File(filePath);
            if (file.exists() && file.delete()) {
                // File deleted successfully, now update database and lists
                try {
                    // Get the PDFRepository to delete from database
                    PDFRepository repository = new PDFRepository(context);
                    repository.open();
                    repository.deletePDFFile(filePath);
                    repository.close();
                    
                    // Delete the thumbnail if it exists
                    if (pdfToDelete.getImagePath() != null && !pdfToDelete.getImagePath().isEmpty() && 
                        !pdfToDelete.getImagePath().equals("__protected")) {
                        File thumbnail = new File(pdfToDelete.getImagePath());
                        if (thumbnail.exists()) {
                            boolean deleted = thumbnail.delete();
                            Log.d("PDFFileAdapter", "Thumbnail deleted: " + deleted);
                        }
                    }
                    
                    // Get the position in the master list for broadcasting
                    int masterPosition = -1;
                    synchronized (BrowserActivity.getPdfFiles()) {
                        for (int i = 0; i < BrowserActivity.getPdfFiles().size(); i++) {
                            if (BrowserActivity.getPdfFiles().get(i).getLocation().equals(filePath)) {
                                masterPosition = i;
                                break;
                            }
                        }
                    }
                    
                    // Update all lists atomically
                    synchronized (pdfFiles) {
                        // Remove from master list
                        if (masterPosition != -1) {
                            BrowserActivity.getPdfFiles().remove(masterPosition);
                        }
                        
                        // Remove from favorites list if present
                        if (pdfToDelete.getFavourite()) {
                            BrowserActivity.getFavPdfFiles().remove(pdfToDelete);
                        }
                        
                        // Remove from adapter's lists
                        pdfFiles.remove(pdfToDelete);
                        filteredPdfFiles.remove(position);
                    }
                    
                    // Notify adapter
                    notifyItemRemoved(position);
                    notifyItemRangeChanged(position, filteredPdfFiles.size());
                    
                    // Send broadcast to ensure consistency across all components
                    Intent intent = new Intent("com.taut0logy.readerforu.PDF_FILE_DELETED");
                    intent.putExtra("position", masterPosition);
                    intent.putExtra("path", filePath);
                    context.sendBroadcast(intent);
                    
                    Toast.makeText(context, "Deleted Successfully", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("PDFFileAdapter", "Error updating database after file deletion: " + e.getMessage(), e);
                }
            } else {
                Toast.makeText(context, "File doesn't exist or couldn't be deleted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void sharePDF(View view, Context context, String pdfFilePath) {
        File pdfFile = new File(pdfFilePath);
        Uri pdfUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", pdfFile);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        shareIntent.setType("application/pdf");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Via"));
    }

}
