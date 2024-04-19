package com.taut0logy.readerforu;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;

public class PDFFile implements Serializable {
    private String name;
    private String author;
    private String description;
    private String location;
    private String imagePath;
    private int currPage;
    private final int totalPages;
    private boolean isFavourite;
    private long modified, lastRead;

    public PDFFile(String name, String author, String description, String location, String imagePath, int currPage, int totalPages, boolean isFavourite, long modified, long lastRead) {
        this.name = name;
        this.author = author;
        this.description = description;
        this.location = location;
        this.imagePath = imagePath;
        this.currPage = currPage;
        this.totalPages = totalPages;
        this.isFavourite = isFavourite;
        this.modified = modified;
        this.lastRead = lastRead;
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

    public Bitmap getThumbnail() {
        File file = new File(imagePath);
        if(!file.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
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

    public void setCurrPage(int currPage) {
        this.currPage = currPage;
    }

    public void setLastRead(long lastRead) {
        this.lastRead = lastRead;
    }

    public void setFavourite(boolean isFavourite) {
        this.isFavourite = isFavourite;
    }

    public void setModified(long modified) {
        this.modified = modified;
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
}
