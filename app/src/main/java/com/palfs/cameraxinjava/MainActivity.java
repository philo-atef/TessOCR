package com.palfs.cameraxinjava;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.palfs.cameraxinjava.ocr.ImageTextReader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer, View.OnClickListener {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageTextReader mImageTextReader;
    PreviewView previewView;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private Button bRecord;
    private Button bCapture;
    File FinaltessDir;
    Bitmap testIMG;
    private ConvertImageToTextTask convertImageToTextTask;
    File engFile;
    @SuppressLint("RestrictedApi")
    @Override
    public void onClick(View view) {
        convertImageToText(testIMG);
        switch (view.getId()) {
            case R.id.bCapture :
                if(cap)
                    onPause();
                else
                    onResume();
                break;

        }
    }

    private Handler handler = new Handler();

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            capturePhoto();
            handler.postDelayed(this, 1000); // Schedule the Runnable to be called again in 1 second
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(runnable, 1000); // Schedule the Runnable to be called in 1 second
        cap=true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(runnable); // Stop the scheduled Runnable
        cap=false;
    }
    private boolean cap=false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            extractAssets(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        previewView = findViewById(R.id.previewView);
        bCapture = findViewById(R.id.bCapture);
        bRecord = findViewById(R.id.bRecord);
        bRecord.setText("start recording"); // Set the initial text of the button
        
        bCapture.setOnClickListener(this);
        bRecord.setOnClickListener(this);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());

    }

    Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image capture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // Video capture use case
        videoCapture = new VideoCapture.Builder()
                .setVideoFrameRate(30)
                .build();

        // Image analysis use case
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), this);

        //bind to lifecycle:
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, videoCapture);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        // image processing here for the current frame
        Log.d("TAG", "analyze: got the frame at: " + image.getImageInfo().getTimestamp());
        image.close();
    }


//    public void onClick(View view) {
//        switch (view.getId()) {
//            case R.id.bCapture:
//                capturePhoto();
//                break;
//            case R.id.bRecord:
//                if (bRecord.getText() == "start recording"){
//                    bRecord.setText("stop recording");
//                    recordVideo();
//                } else {
//                    bRecord.setText("start recording");
//                    videoCapture.stopRecording();
//                }
//                break;
//
//        }
//    }

    @SuppressLint("RestrictedApi")
//    private void recordVideo() {
//        if (videoCapture != null) {
//
//            long timestamp = System.currentTimeMillis();
//
//            ContentValues contentValues = new ContentValues();
//            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
//            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
//
//            try {
//                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//                    // TODO: Consider calling
//                    //    ActivityCompat#requestPermissions
//                    // here to request the missing permissions, and then overriding
//                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                    //                                          int[] grantResults)
//                    // to handle the case where the user grants the permission. See the documentation
//                    // for ActivityCompat#requestPermissions for more details.
//                    return;
//                }
//                videoCapture.startRecording(
//                        new VideoCapture.OutputFileOptions.Builder(
//                                getContentResolver(),
//                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
//                                contentValues
//                        ).build(),
//                        getExecutor(),
//                        new VideoCapture.OnVideoSavedCallback() {
//                            @Override
//                            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
//                                Toast.makeText(MainActivity.this, "Video has been saved successfully.", Toast.LENGTH_SHORT).show();
//                            }
//
//                            @Override
//                            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
//                                Toast.makeText(MainActivity.this, "Error saving video: " + message, Toast.LENGTH_SHORT).show();
//                            }
//                        }
//                );
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//        }
//    }

    private void capturePhoto() {
        long timestamp = System.currentTimeMillis();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");



        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.d("Xdebug_cam", outputFileResults.getSavedUri().toString());
//                        Log.d("Xdebug_cam", outputFileResults.getSavedUri().getPath());
                        Log.d("Xdebug_crop1", outputFileResults.getSavedUri().toString());
//                        try {
//                            testIMG = MediaStore.Images.Media.getBitmap(getContentResolver(), outputFileResults.getSavedUri());
//                            Log.d("Xdebug_cam", testIMG.getHeight()+" 1 "+testIMG.getWidth());
//                            testIMG=rotateBitmap(testIMG,90);
//                            Log.d("Xdebug_cam", testIMG.getHeight()+" 2 "+testIMG.getWidth());
//                            bitmapToFile(testIMG,"saved.png");
////                            testIMG = Bitmap.createBitmap(testIMG, 0, 0, testIMG.getWidth(), testIMG.getHeight()/5);
//                            saveBitmapToStorage(testIMG);
//                            Log.d("Xdebug_cam", testIMG.getByteCount()+"");
//                            Log.d("Xdebug_cam", testIMG.getHeight()+" "+testIMG.getWidth());
//
//
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                            Log.d("Xdebug_error", e.toString());
//
//                        }
//                        final Toast toast = Toast.makeText(MainActivity.this, "This message will disappear in 1 second", Toast.LENGTH_SHORT);
//                        toast.show();
//
//                        Handler handler = new Handler();
//                        handler.postDelayed(new Runnable() {
//                            @Override
//                            public void run() {
//                                toast.cancel();
//                            }
//                        }, 500);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );

    }
    public static String getTessDataPath(@NonNull Context context) {
        // We need to return folder that contains the "tessdata" folder,
        // which is in this sample directly the app's files dir
        return context.getFilesDir().getAbsolutePath();
    }

    @NonNull
    public static String getLanguage() {
        return "Segment2";
    }

    @NonNull
    public static File getImageFile(@NonNull Context context) {
        return new File(context.getFilesDir(), "sample2.jpg");
    }

    @Nullable
    public static Bitmap getImageBitmap(@NonNull Context context) {
        return BitmapFactory.decodeFile(getImageFile(context).getAbsolutePath());
    }

    public void extractAssets(@NonNull Context context) throws IOException {
        AssetManager am = context.getAssets();

        File imageFile = getImageFile(context);
        if (!imageFile.exists()) {
            Log.d("Xdebug_assets", "no img");
            copyFile(am, "sample2.jpg", imageFile);
            Log.d("Xdebug_assets", imageFile.getPath()+" path");
        }
        Log.d("Xdebug_assets", imageFile.length()/1024+" imgSize");
        testIMG = BitmapFactory.decodeFile(imageFile.getPath());
        File tessDir = new File(getTessDataPath(context), "tessdata");
        if (!tessDir.exists()) {
            tessDir.mkdir();
        }
        Log.d("Xdebug_assets", tessDir.getAbsolutePath());
        engFile = new File(tessDir, "Segment2.traineddata");
        if (!engFile.exists()) {
            copyFile(am, "Segment2.traineddata", engFile);
        }
         FinaltessDir = tessDir;
        //Bitmap bMap = BitmapFactory.decodeFile();
        Log.d("Xdebug_assets",engFile.exists()+" "+engFile.getAbsolutePath()+" "+engFile.length()/1024 );
        Log.d("Xdebug_assets", "message");
//        initializeOCR();

        if (engFile!=null) {
            //region Initialize image text reader
            new Thread() {
                @Override
                public void run() {
                    try {
                        if (mImageTextReader != null) {
                            mImageTextReader.tearDownEverything();
                        }

                        mImageTextReader = ImageTextReader.geInstance(getTessDataPath(context), "Segment2", 12);
                        //check if current language data is valid
                        //if it is invalid(i.e. corrupted, half downloaded, tempered) then delete it
                        if (!mImageTextReader.success) {
                            Log.d("Xdebug_api", "not initialized");
                            mImageTextReader = null;
                        }
                        else{
                            Log.d("Xdebug_api", "initialized");
                        }

                    } catch (Exception e) {
                        Log.d("Xdebug_api", "error ma"+e.toString());
                        mImageTextReader = null;
                    }
                }
            }.start();

            //endregion
        } else {
            Log.d("Xdebug_api", "initializeOCR: language data doesn't exist " + "eng");

        }

    }

    private static void copyFile(@NonNull AssetManager am, @NonNull String assetName,
                                 @NonNull File outFile) {
        try (
                InputStream in = am.open(assetName);
                OutputStream out = new FileOutputStream(outFile)
        ) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void saveBitmapToStorage(Bitmap bitmap) {
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = openFileOutput("last_file.jpeg", Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, fileOutputStream);
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();

        }
    }
    private void initializeOCR() {
        File cf;

        Log.d("Xdebug_api", "initializeOCR: " + "ENG");

        Log.d("Xdebug_api", FinaltessDir.getAbsolutePath());


        if (engFile!=null) {
            //region Initialize image text reader
            new Thread() {
                @Override
                public void run() {
                    try {
                        if (mImageTextReader != null) {
                            mImageTextReader.tearDownEverything();
                        }

                        mImageTextReader = ImageTextReader.geInstance(FinaltessDir.getAbsolutePath(), "Segment2", 12);
                        //check if current language data is valid
                        //if it is invalid(i.e. corrupted, half downloaded, tempered) then delete it
                        if (!mImageTextReader.success) {
                            Log.d("Xdebug_api", "not initialized");
                            mImageTextReader = null;
                        }
                        else{
                            Log.d("Xdebug_api", "initialized");
                        }

                    } catch (Exception e) {
                        Log.d("Xdebug_api", "error ma"+e.toString());
                        mImageTextReader = null;
                    }
                }
            }.start();

            //endregion
        } else {
            Log.d("Xdebug_api", "initializeOCR: language data doesn't exist " + "eng");

        }
    }
    private class ConvertImageToTextTask extends AsyncTask<Bitmap, Void, String> {

        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            Bitmap bitmap = bitmaps[0];
if(mImageTextReader==null)
    return "fail";
            return mImageTextReader.getTextFromBitmap(bitmap);
        }

        @Override



        protected void onPostExecute(String text) {

//            Log.d("resultzz", text);
            String clean_text = Html.fromHtml(text).toString().trim();
            Log.d("resultzz", clean_text);
if(!text.equals("fail"))
            Toast.makeText(MainActivity.this, clean_text, Toast.LENGTH_SHORT).show();


        }

    }
    private void convertImageToText(Bitmap bitmap) {

        Log.d("Xdebug_call",bitmap.getDensity()+"" );
        convertImageToTextTask = new ConvertImageToTextTask();
        convertImageToTextTask.execute(bitmap);
    }

    public static File bitmapToFile(Bitmap bitmap, String fileNameToSave) { // File name like "image.png"
        //create a file to write bitmap data
        File file = null;
        File tempDir= Environment.getExternalStorageDirectory();
        try {
            Log.d("Xdebug_crop2",tempDir.getAbsolutePath() + File.separator + fileNameToSave);
            file = new File(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DOWNLOADS +"/"+ fileNameToSave);
            file.createNewFile();

//Convert bitmap to byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 , bos); // YOU can also save it in JPEG
            byte[] bitmapdata = bos.toByteArray();

//write the bytes in file
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();
            Log.d("Xdebug_crop", file.getAbsolutePath());
            return file;
        }catch (Exception e){
            e.printStackTrace();
            Log.d("Xdebug_crop", e.toString());
            return file; // it will return null
        }
    }
    public Bitmap rotateBitmap(Bitmap original, float degrees) {
        int x = original.getWidth();
        int y = original.getHeight();
        Matrix matrix = new Matrix();
        matrix.preRotate(degrees);
        Bitmap rotatedBitmap = Bitmap.createBitmap(original , 0, 0, original .getWidth(), original .getHeight(), matrix, true);
        return rotatedBitmap;
    }
}