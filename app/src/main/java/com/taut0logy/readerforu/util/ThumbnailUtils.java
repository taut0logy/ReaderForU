package com.taut0logy.readerforu.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for generating and managing PDF thumbnails
 */
public class ThumbnailUtils {
    private static final String TAG = "ThumbnailUtils";
    private static final String THUMBNAIL_DIR = "thumbnails";
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 300;
    private static final int THUMBNAIL_QUALITY = 95; // Higher quality for better thumbnails
    private static final int MAX_CONCURRENT_GENERATIONS = 4; // Limit concurrent thumbnail generation

    /**
     * Generate a thumbnail for a PDF file
     * Always uses the first page for the thumbnail
     * 
     * @param context Application context
     * @param pdfPath Path to the PDF file
     * @return Bitmap thumbnail or null if generation failed
     */
    public static Bitmap generateThumbnail(Context context, String pdfPath) {
        return generateThumbnail(context, pdfPath, false);
    }

    /**
     * Generate a thumbnail for a PDF file
     * Always uses the first page for the thumbnail
     * 
     * @param context Application context
     * @param pdfPath Path to the PDF file
     * @param forceRegenerate Whether to force regeneration even if a cached version exists
     * @return Bitmap thumbnail or null if generation failed
     */
    public static Bitmap generateThumbnail(Context context, String pdfPath, boolean forceRegenerate) {
        // Handle content URIs
        if (pdfPath.startsWith("content://")) {
            return generateThumbnailFromContentUri(context, Uri.parse(pdfPath), forceRegenerate);
        }
        
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            Log.e(TAG, "PDF file does not exist: " + pdfPath);
            return null;
        }

        try {
            // Check if cached thumbnail exists first (unless forced regeneration is requested)
            if (!forceRegenerate) {
                String cachedPath = getThumbnailPath(context, pdfPath);
                if (cachedPath != null) {
                    File cachedFile = new File(cachedPath);
                    if (cachedFile.exists()) {
                        // Check if the cached thumbnail is newer than the PDF
                        if (cachedFile.lastModified() >= pdfFile.lastModified()) {
                            Bitmap cachedBitmap = android.graphics.BitmapFactory.decodeFile(cachedPath);
                            if (cachedBitmap != null) {
                                Log.d(TAG, "Using cached thumbnail for: " + pdfPath);
                                return cachedBitmap;
                            }
                        }
                    }
                }
            }

            ParcelFileDescriptor fileDescriptor = ParcelFileDescriptor.open(
                    pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer = new PdfRenderer(fileDescriptor);

            // Always get the first page for the thumbnail
            if (renderer.getPageCount() > 0) {
                PdfRenderer.Page page = renderer.openPage(0);
                
                // Create bitmap with appropriate dimensions
                Bitmap bitmap = Bitmap.createBitmap(
                        THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888);
                
                // Render the page to the bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                renderer.close();
                fileDescriptor.close();
                
                // Automatically save the generated thumbnail
                saveThumbnail(context, bitmap, pdfPath);
                
                return bitmap;
            } else {
                Log.e(TAG, "PDF has no pages: " + pdfPath);
                renderer.close();
                fileDescriptor.close();
                return null;
            }
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Error generating thumbnail for: " + pdfPath, e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error generating thumbnail: " + pdfPath, e);
            return null;
        }
    }
    
    /**
     * Generate a thumbnail from a content URI
     * @param context Application context
     * @param contentUri URI to the content
     * @param forceRegenerate Whether to force regeneration
     * @return Bitmap thumbnail or null if generation failed
     */
    private static Bitmap generateThumbnailFromContentUri(Context context, Uri contentUri, boolean forceRegenerate) {
        try {
            String uriString = contentUri.toString();
            
            // Check for cached version first (unless force regeneration is requested)
            if (!forceRegenerate) {
                String cachedPath = getThumbnailPath(context, uriString);
                if (cachedPath != null) {
                    File cachedFile = new File(cachedPath);
                    if (cachedFile.exists()) {
                        Bitmap cachedBitmap = android.graphics.BitmapFactory.decodeFile(cachedPath);
                        if (cachedBitmap != null) {
                            Log.d(TAG, "Using cached thumbnail for URI: " + uriString);
                            return cachedBitmap;
                        }
                    }
                }
            }
            
            // Generate new thumbnail from content URI
            ParcelFileDescriptor fileDescriptor = context.getContentResolver().openFileDescriptor(contentUri, "r");
            if (fileDescriptor != null) {
                PdfRenderer renderer = new PdfRenderer(fileDescriptor);
                if (renderer.getPageCount() > 0) {
                    PdfRenderer.Page page = renderer.openPage(0);
                    
                    // Create bitmap
                    Bitmap bitmap = Bitmap.createBitmap(
                            THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888);
                    
                    // Render the page
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();
                    renderer.close();
                    fileDescriptor.close();
                    
                    // Save the thumbnail
                    saveThumbnail(context, bitmap, uriString);
                    
                    return bitmap;
                } else {
                    Log.e(TAG, "PDF has no pages (URI): " + uriString);
                    fileDescriptor.close();
                    return null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating thumbnail from URI: " + contentUri, e);
        }
        return null;
    }

    /**
     * Get the expected path for a thumbnail without checking if it exists
     */
    public static String getThumbnailPath(Context context, String pdfPath) {
        if (pdfPath == null || pdfPath.isEmpty()) {
            return null;
        }
        
        File thumbnailDir = new File(context.getCacheDir(), THUMBNAIL_DIR);
        String filename = String.valueOf(pdfPath.hashCode()) + ".png";
        File thumbnailFile = new File(thumbnailDir, filename);
        return thumbnailFile.getAbsolutePath();
    }

    /**
     * Save a thumbnail bitmap to the app's cache directory
     * @param context Application context
     * @param bitmap Bitmap to save
     * @param pdfPath Path of the PDF file (used to generate unique filename)
     * @return Path to the saved thumbnail or null if saving failed
     */
    public static String saveThumbnail(Context context, Bitmap bitmap, String pdfPath) {
        if (bitmap == null) {
            return null;
        }

        // Create thumbnails directory in cache if it doesn't exist
        File thumbnailDir = new File(context.getCacheDir(), THUMBNAIL_DIR);
        if (!thumbnailDir.exists() && !thumbnailDir.mkdirs()) {
            Log.e(TAG, "Failed to create thumbnail directory");
            return null;
        }

        // Generate unique filename based on PDF path
        String filename = String.valueOf(pdfPath.hashCode()) + ".png";
        File thumbnailFile = new File(thumbnailDir, filename);

        try (FileOutputStream out = new FileOutputStream(thumbnailFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, THUMBNAIL_QUALITY, out);
            
            // Update the thumbnail's last modified time to match or be slightly newer than the PDF
            if (!pdfPath.startsWith("content://")) {
                File pdfFile = new File(pdfPath);
                if (pdfFile.exists()) {
                    thumbnailFile.setLastModified(pdfFile.lastModified());
                }
            }
            
            return thumbnailFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error saving thumbnail: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete a thumbnail file
     * @param thumbnailPath Path to the thumbnail file
     * @return true if deletion was successful, false otherwise
     */
    public static boolean deleteThumbnail(String thumbnailPath) {
        if (thumbnailPath == null || thumbnailPath.isEmpty()) {
            return false;
        }
        
        File thumbnailFile = new File(thumbnailPath);
        if (thumbnailFile.exists()) {
            return thumbnailFile.delete();
        }
        return false;
    }

    /**
     * Clear all thumbnails from the cache directory
     * @param context Application context
     * @return Number of thumbnails deleted
     */
    public static int clearThumbnailCache(Context context) {
        File thumbnailDir = new File(context.getCacheDir(), THUMBNAIL_DIR);
        if (!thumbnailDir.exists()) {
            return 0;
        }
        
        File[] files = thumbnailDir.listFiles();
        if (files == null) {
            return 0;
        }
        
        int count = 0;
        for (File file : files) {
            if (file.delete()) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Rebuild all thumbnails for a list of PDF files
     * @param context Application context
     * @param pdfPaths List of PDF paths to generate thumbnails for
     * @param listener Progress listener to report back progress
     */
    public static void rebuildAllThumbnails(Context context, List<String> pdfPaths, 
                                             ThumbnailRebuildListener listener) {
        if (pdfPaths == null || pdfPaths.isEmpty()) {
            if (listener != null) {
                listener.onComplete(0);
            }
            return;
        }
        
        // Create a thread pool for parallel processing
        ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_GENERATIONS);
        List<Future<Boolean>> futures = new ArrayList<>();
        final int totalFiles = pdfPaths.size();
        final int[] processedFiles = {0};
        final int[] successfulFiles = {0};
        
        // Submit tasks to the thread pool
        for (String pdfPath : pdfPaths) {
            Callable<Boolean> task = () -> {
                // Skip protected files
                if (pdfPath != null && !pdfPath.equals("__protected")) {
                    Bitmap bitmap = generateThumbnail(context, pdfPath, true);
                    boolean success = bitmap != null;
                    
                    // Report progress
                    synchronized (processedFiles) {
                        processedFiles[0]++;
                        if (success) {
                            successfulFiles[0]++;
                        }
                        
                        if (listener != null) {
                            int progress = (processedFiles[0] * 100) / totalFiles;
                            listener.onProgress(progress, processedFiles[0], totalFiles);
                        }
                    }
                    
                    return success;
                }
                return false;
            };
            
            futures.add(executor.submit(task));
        }
        
        // Start a monitor thread to wait for completion
        new Thread(() -> {
            try {
                executor.shutdown();
                executor.awaitTermination(30, TimeUnit.MINUTES);
                
                // All tasks complete, report final count
                if (listener != null) {
                    listener.onComplete(successfulFiles[0]);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Thumbnail rebuild interrupted: " + e.getMessage());
                if (listener != null) {
                    listener.onError("Process was interrupted");
                }
            }
        }).start();
    }
    
    /**
     * Generate thumbnails during first scan
     * Only generates thumbnails for files that don't already have them
     * @param context Application context
     * @param pdfPath Path to PDF file
     * @return Path to thumbnail or null if generation failed
     */
    public static String generateThumbnailForFirstScan(Context context, String pdfPath) {
        if (pdfPath == null || pdfPath.isEmpty() || pdfPath.equals("__protected")) {
            return null;
        }
        
        // Check if a thumbnail already exists
        String cachePath = getThumbnailPath(context, pdfPath);
        if (cachePath != null) {
            File cacheFile = new File(cachePath);
            if (cacheFile.exists()) {
                return cachePath; // Already exists, no need to regenerate
            }
        }
        
        // Generate the thumbnail
        Bitmap thumbnail = generateThumbnail(context, pdfPath);
        if (thumbnail != null) {
            return saveThumbnail(context, thumbnail, pdfPath);
        }
        
        return null;
    }
    
    /**
     * Listener interface for thumbnail rebuild progress
     */
    public interface ThumbnailRebuildListener {
        void onProgress(int percentage, int processed, int total);
        void onComplete(int successCount);
        void onError(String errorMessage);
    }
}