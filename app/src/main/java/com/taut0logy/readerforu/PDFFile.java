package com.taut0logy.readerforu;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.Serializable;

public class PDFFile implements Serializable {
    private String name;
    private String author;
    private String description;
    private String location;
    private String imagePath;
    private int currPage;
    private int totalPages;
    private boolean isFavourite;

    public PDFFile(String name, String author, String description, String location, String imagePath, int currPage, int totalPages, boolean isFavourite) {
        this.name = name;
        this.author = author;
        this.description = description;
        this.location = location;
        this.imagePath = imagePath;
        this.currPage = currPage;
        this.totalPages = totalPages;
        this.isFavourite = isFavourite;
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

    public Bitmap getThumbnail() {
        File file = new File(imagePath);
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

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public void setFavourite(boolean isFavourite) {
        this.isFavourite = isFavourite;
    }
}
