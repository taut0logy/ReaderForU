package com.taut0logy.readerforu.data;

import android.graphics.Bitmap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class PDFFile implements Serializable {
    private long id; // Database primary key
    private String name;
    private String author;
    private String description;
    private String location;
    private String imagePath; // Path to the thumbnail image file
    private int currPage;
    private int totalPages; // Changed from final to allow updates
    private boolean isFavourite;
    private long modified, lastRead;
    private long dateAdded;
    private transient Bitmap thumbnail; // Keep thumbnail in memory, path in DB (marked transient to avoid serialization issues)
    private boolean isProtected; // Flag for password-protected files

    private long size;

    private String contentUri;

    private long lastModified;

    public String getContentUri() {
        return contentUri;
    }

    public void setContentUri(String contentUri) {
        this.contentUri = contentUri;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    // Constructor without ID (for initial creation before DB insert)
    public PDFFile(String name, String author, String description, String location, String imagePath, int currPage, int totalPages, boolean isFavourite, long lastRead, long dateAdded) {
        this.name = name != null ? name : "Unknown Title";
        this.author = author != null ? author : "Unknown Author";
        this.description = description;
        this.location = location;
        this.imagePath = imagePath;
        this.currPage = currPage;
        this.totalPages = totalPages;
        this.isFavourite = isFavourite;
        this.modified = modified;
        this.lastRead = lastRead;
        this.dateAdded = dateAdded;
        this.thumbnail = null; // Thumbnail loaded separately
        this.isProtected = false; // Default to not protected
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getImagePath() {
        return imagePath;
    }

    public int getCurrPage() {
        return currPage;
    }

    public int getTotalPages() {
        return totalPages;
    }
    public boolean getFavourite() {
        return isFavourite;
    }

    public long getModified() {
        return modified;
    }

    public long getLastRead() {
        return lastRead;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public boolean isProtected() {
        return isProtected;
    }
    
    public Bitmap getThumbnail() {
        return thumbnail;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
    
    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void setCurrPage(int currPage) {
        this.currPage = currPage;
    }

    public void setLastRead(long lastRead) {
        this.lastRead = lastRead;
    }

    public void setFavourite(boolean isFavourite) {
        this.isFavourite = isFavourite;
    }

    public void setProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }

    public void setModified(long modified) {
        this.modified = modified;
    }

    /**
     * Set the total number of pages
     * @param totalPages The total number of pages in the PDF
     */
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public JSONObject toJSON() {
        JSONObject pdfObj = new JSONObject();
        try {
            pdfObj.put("name", name);
            pdfObj.put("author", author);
            pdfObj.put("description", description);
            pdfObj.put("path", location);
            pdfObj.put("thumbnailPath", imagePath);
            pdfObj.put("currPage", currPage);
            pdfObj.put("totalPages", totalPages);
            pdfObj.put("isFav", isFavourite);
            pdfObj.put("modified", modified);
            pdfObj.put("lastRead", lastRead);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return pdfObj;
    }

    // Static method to create PDFFile from JSON (might need adjustment based on DB fields)
//    public static PDFFile fromJSON(JSONObject jsonObject) {
//        try {
//            String name = jsonObject.optString("name", "Unknown Title");
//            String author = jsonObject.optString("author", "Unknown Author");
//            String description = jsonObject.optString("description", "No Description");
//            String location = jsonObject.getString("location"); // Location is mandatory
//            String imagePath = jsonObject.optString("imagePath", null); // Thumbnail path
//            int currPage = jsonObject.optInt("currPage", 0);
//            int totalPages = jsonObject.optInt("totalPages", 0);
//            boolean isFavourite = jsonObject.optBoolean("isFavourite", false);
//            long lastRead = jsonObject.optLong("lastRead", 0);
//            long dateAdded = jsonObject.optLong("dateAdded", System.currentTimeMillis());
//            // ID and isProtected might not be in old cache JSON, handle gracefully
//            long id = jsonObject.optLong("id", -1); // Default to -1 if not present
//            boolean isProtected = jsonObject.optBoolean("isProtected", false);
//
//            PDFFile pdfFile = new PDFFile(name, author, description, location, imagePath, currPage, totalPages, isFavourite, lastRead, dateAdded);
//            pdfFile.setId(id); // Set the ID if available
//            pdfFile.setProtected(isProtected);
//            // Note: Thumbnail Bitmap is not stored in JSON, only its path (imagePath)
//            return pdfFile;
//        } catch (JSONException e) {
//            Log.e("PDFFile", "Error creating PDFFile from JSON", e);
//            return null;
//        }
//    }
//
//    public long getSize() {
//        return 0;
//    }
}
