package com.taut0logy.readerforu.data;

import android.graphics.Bitmap;
import android.util.Log;

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
            Log.e("PDFFile", "Error converting PDFFile to JSON", e);
        }
        return pdfObj;
    }
}
