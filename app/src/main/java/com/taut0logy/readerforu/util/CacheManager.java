package com.taut0logy.readerforu.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.taut0logy.readerforu.data.PDFFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages caching of PDF file data to improve app startup time
 */
public class CacheManager {
    private static final String TAG = "CacheManager";
    private static final String CACHE_DIR = "pdf_cache";
    private static final String PDF_LIST_CACHE_FILE = "pdf_list.json";
    private static final String CACHE_PREFS = "cache_prefs";
    private static final String LAST_SCAN_KEY = "last_scan_timestamp";
    private static final String FOLDERS_HASH_KEY = "folders_hash";
    
    /**
     * Save the PDF file list to cache
     * @param context Application context
     * @param pdfFiles List of PDF files to cache
     * @param foldersList List of scanned folders
     * @return true if successful, false otherwise
     */
    public static boolean savePdfListToCache(Context context, List<PDFFile> pdfFiles, List<String> foldersList) {
        if (pdfFiles == null || pdfFiles.isEmpty()) {
            return false;
        }
        
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.e(TAG, "Failed to create cache directory");
            return false;
        }
        
        File cacheFile = new File(cacheDir, PDF_LIST_CACHE_FILE);
        
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            JSONObject cacheData = new JSONObject();
            
            // Add metadata
            cacheData.put("timestamp", System.currentTimeMillis());
            cacheData.put("version", 1);
            cacheData.put("count", pdfFiles.size());
            
            // Add folders list
            JSONArray foldersArray = new JSONArray();
            if (foldersList != null) {
                for (String folder : foldersList) {
                    foldersArray.put(folder);
                }
            }
            cacheData.put("folders", foldersArray);
            
            // Add PDF files
            JSONArray filesArray = new JSONArray();
            for (PDFFile pdfFile : pdfFiles) {
                filesArray.put(pdfFileToJson(pdfFile));
            }
            cacheData.put("files", filesArray);
            
            // Write to file
            fos.write(cacheData.toString().getBytes());
            
            // Update preferences
            updateCacheMetadata(context, calculateFoldersHash(foldersList));
            
            Log.d(TAG, "PDF list cached successfully: " + pdfFiles.size() + " files");
            return true;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving PDF list to cache: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Load PDF file list from cache
     * @param context Application context
     * @return List of cached PDF files or null if cache is invalid or missing
     */
    public static List<PDFFile> loadPdfListFromCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        File cacheFile = new File(cacheDir, PDF_LIST_CACHE_FILE);
        
        if (!cacheFile.exists()) {
            Log.d(TAG, "Cache file doesn't exist");
            return null;
        }
        
        try {
            // Read the file
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }
            
            // Parse JSON
            JSONObject cacheData = new JSONObject(content.toString());
            long timestamp = cacheData.optLong("timestamp", 0);
            int version = cacheData.optInt("version", 0);
            
            // Basic validation
            if (version != 1 || timestamp == 0) {
                Log.e(TAG, "Invalid cache format or version");
                return null;
            }
            
            // Read PDF files
            JSONArray filesArray = cacheData.getJSONArray("files");
            List<PDFFile> pdfFiles = new ArrayList<>();
            
            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject jsonFile = filesArray.getJSONObject(i);
                PDFFile pdfFile = jsonToPdfFile(jsonFile);
                if (pdfFile != null) {
                    pdfFiles.add(pdfFile);
                }
            }
            
            Log.d(TAG, "Loaded " + pdfFiles.size() + " PDF files from cache");
            return pdfFiles;
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading PDF list from cache: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if the cache is still valid
     * @param context Application context
     * @param foldersList Current folders list
     * @return true if cache is valid, false otherwise
     */
    public static boolean isCacheValid(Context context, List<String> foldersList) {
        SharedPreferences prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        long lastScan = prefs.getLong(LAST_SCAN_KEY, 0);
        String savedFoldersHash = prefs.getString(FOLDERS_HASH_KEY, "");
        String currentFoldersHash = calculateFoldersHash(foldersList);
        
        // Check if folders have changed
        if (!savedFoldersHash.equals(currentFoldersHash)) {
            Log.d(TAG, "Cache invalid: Folders changed");
            return false;
        }
        
        // Check cache file exists
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        File cacheFile = new File(cacheDir, PDF_LIST_CACHE_FILE);
        if (!cacheFile.exists()) {
            Log.d(TAG, "Cache invalid: Cache file doesn't exist");
            return false;
        }
        
        // Cache is valid
        Log.d(TAG, "Cache is valid. Last scan: " + lastScan);
        return true;
    }
    
    /**
     * Clear the PDF list cache
     * @param context Application context
     * @return true if successful, false otherwise
     */
    public static boolean clearCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        File cacheFile = new File(cacheDir, PDF_LIST_CACHE_FILE);
        
        boolean success = true;
        if (cacheFile.exists()) {
            success = cacheFile.delete();
        }
        
        // Reset preferences
        if (success) {
            SharedPreferences prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(LAST_SCAN_KEY);
            editor.remove(FOLDERS_HASH_KEY);
            editor.apply();
            Log.d(TAG, "Cache cleared successfully");
        } else {
            Log.e(TAG, "Failed to clear cache");
        }
        
        return success;
    }
    
    /**
     * Update cache metadata in SharedPreferences
     */
    private static void updateCacheMetadata(Context context, String foldersHash) {
        SharedPreferences prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(LAST_SCAN_KEY, System.currentTimeMillis());
        editor.putString(FOLDERS_HASH_KEY, foldersHash);
        editor.apply();
    }
    
    /**
     * Calculate a hash for the folders list
     */
    private static String calculateFoldersHash(List<String> foldersList) {
        if (foldersList == null || foldersList.isEmpty()) {
            return "empty";
        }
        
        // Sort and join folders to create a unique string
        Set<String> foldersSet = new HashSet<>(foldersList);
        StringBuilder sb = new StringBuilder();
        for (String folder : foldersSet) {
            sb.append(folder).append(";");
        }
        
        return String.valueOf(sb.toString().hashCode());
    }
    
    /**
     * Convert a PDFFile to JSON
     */
    private static JSONObject pdfFileToJson(PDFFile pdfFile) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", pdfFile.getId());
        json.put("name", pdfFile.getName());
        json.put("author", pdfFile.getAuthor());
        json.put("description", pdfFile.getDescription());
        json.put("location", pdfFile.getLocation());
        json.put("imagePath", pdfFile.getImagePath());
        json.put("currPage", pdfFile.getCurrPage());
        json.put("totalPages", pdfFile.getTotalPages());
        json.put("isFavourite", pdfFile.getFavourite());
        json.put("lastRead", pdfFile.getLastRead());
        json.put("modified", pdfFile.getModified());
        json.put("dateAdded", pdfFile.getDateAdded());
        json.put("isProtected", pdfFile.isProtected());
        json.put("size", pdfFile.getSize());
        json.put("lastModified", pdfFile.getLastModified());
        json.put("contentUri", pdfFile.getContentUri());
        return json;
    }
    
    /**
     * Convert JSON to a PDFFile
     */
    private static PDFFile jsonToPdfFile(JSONObject json) {
        try {
            String name = json.optString("name", "Unknown Title");
            String author = json.optString("author", "Unknown Author");
            String description = json.optString("description", "");
            String location = json.getString("location");
            String imagePath = json.optString("imagePath", null);
            int currPage = json.optInt("currPage", 0);
            int totalPages = json.optInt("totalPages", 0);
            boolean isFavourite = json.optBoolean("isFavourite", false);
            long lastRead = json.optLong("lastRead", 0);
            long dateAdded = json.optLong("dateAdded", 0);
            
            PDFFile pdfFile = new PDFFile(name, author, description, location, imagePath,
                    currPage, totalPages, isFavourite, lastRead, dateAdded);
            
            pdfFile.setId(json.optLong("id", -1));
            pdfFile.setProtected(json.optBoolean("isProtected", false));
            pdfFile.setModified(json.optLong("modified", 0));
            pdfFile.setSize(json.optLong("size", 0));
            pdfFile.setLastModified(json.optLong("lastModified", 0));
            pdfFile.setContentUri(json.optString("contentUri", null));
            
            return pdfFile;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing PDFFile from JSON: " + e.getMessage());
            return null;
        }
    }
} 