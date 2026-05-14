package com.example.extendedreality;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

// ML KIT TEXT RECOGNITION
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

// MEDIAPIPE OBJECT DETECTION
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.components.containers.Category;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private PreviewView viewFinder;
    private TextView popUpText;
    private ExecutorService cameraExecutor;

    private TextRecognizer textRecognizer;
    private ObjectDetector objectDetector;

    private final Handler trackingHandler = new Handler(Looper.getMainLooper());
    private Runnable hideTextRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        popUpText = findViewById(R.id.popUpText);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 1. Initialize Text Recognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // 2. Initialize MediaPipe (Using the Professional COCO Model)
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("coco_tracker.tflite")
                    .build();

            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setScoreThreshold(0.35f) // 35% confidence
                    .setMaxResults(3)
                    .build();

            objectDetector = ObjectDetector.createFromOptions(this, options);
            Log.d(TAG, "✅ AI Engines Ready!");
        } catch (Exception e) {
            Log.e(TAG, "🚨 MediaPipe Load Error", e);
        }

        // 3. Camera Permissions
        ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> { if (isGranted) startCamera(); }
        );

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, this::processImageProxy);

                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer);
            } catch (Exception e) { Log.e(TAG, "Camera failed", e); }
        }, ContextCompat.getMainExecutor(this));
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null && objectDetector != null) {

            // 1. CREATE ONE UPRIGHT BITMAP FOR BOTH ENGINES
            Bitmap rotatedBitmap = null;
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            try {
                Bitmap rawBitmap = imageProxy.toBitmap();
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                rotatedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.getWidth(), rawBitmap.getHeight(), matrix, true);
            } catch (Exception e) {
                Log.e(TAG, "Rotation failed", e);
                imageProxy.close();
                return;
            }

            final Bitmap finalBitmap = rotatedBitmap;

            // 2. RUN MEDIAPIPE (OBJECTS)
            MPImage mpImage = new BitmapImageBuilder(finalBitmap).build();
            ObjectDetectorResult objectResult = objectDetector.detect(mpImage);

            // 3. RUN ML KIT (TEXT) - We tell it rotation is 0 because the bitmap is already upright!
            InputImage textImage = InputImage.fromBitmap(finalBitmap, 0);

            textRecognizer.process(textImage)
                    .addOnSuccessListener(textResult -> {
                        String detectedCategory = null;
                        float targetX = -1, targetY = -1;

                        // 🚨 TEXT X-RAY: See what the AI sees in Logcat
                        String allText = textResult.getText().replace('\n', ' ');
                        if (!allText.isEmpty()) Log.d(TAG, "📝 TEXT SEES: " + allText);

                        // A. CHECK TEXT FIRST
                        for (Text.TextBlock block : textResult.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                for (Text.Element element : line.getElements()) {
                                    String cat = getCategory(element.getText());
                                    if (cat != null) {
                                        detectedCategory = cat;
                                        targetX = element.getBoundingBox().centerX();
                                        targetY = element.getBoundingBox().centerY();
                                        break;
                                    }
                                }
                                if (detectedCategory != null) break;
                            }
                            if (detectedCategory != null) break;
                        }

                        // B. IF NO TEXT, CHECK OBJECTS (COCO Model)
                        if (detectedCategory == null && !objectResult.detections().isEmpty()) {
                            for (Detection detection : objectResult.detections()) {
                                String rawLabel = detection.categories().get(0).categoryName().toLowerCase();

                                if (rawLabel.equals("apple")) {
                                    detectedCategory = "APPLE";
                                } else if (rawLabel.equals("bottle")) {
                                    detectedCategory = "WINE";
                                }

                                if (detectedCategory != null) {
                                    targetX = detection.boundingBox().centerX();
                                    targetY = detection.boundingBox().centerY();
                                    Log.d(TAG, "🍎 OBJECT SEES: " + detectedCategory);
                                    break;
                                }
                            }
                        }

                        // C. UPDATE UI (Unified Math)
                        final String finalCat = detectedCategory;
                        final float finalX = targetX;
                        final float finalY = targetY;

                        runOnUiThread(() -> {
                            if (finalCat != null && finalX != -1) {
                                // Since both AIs used the upright bitmap, scaling is simple:
                                float scaleX = (float) viewFinder.getWidth() / finalBitmap.getWidth();
                                float scaleY = (float) viewFinder.getHeight() / finalBitmap.getHeight();

                                popUpText.setText(finalCat);
                                popUpText.setVisibility(View.VISIBLE);
                                popUpText.setX((finalX * scaleX) - (popUpText.getWidth() / 2f));
                                popUpText.setY((finalY * scaleY) - (popUpText.getHeight() / 2f));

                                // Flicker-free tracking
                                if (hideTextRunnable != null) trackingHandler.removeCallbacks(hideTextRunnable);
                                hideTextRunnable = () -> popUpText.setVisibility(View.GONE);
                                trackingHandler.postDelayed(hideTextRunnable, 1000);
                            }
                        });

                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Text detection failed", e);
                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }

    // Ensure this method is updated to handle lowercase for the Object Detector
    private String getCategory(String word) {
        word = word.toLowerCase().trim();
        if (Arrays.asList("apple", "gala", "fuji", "smith", "pomme", "appel").contains(word)) return "APPLE";
        if (Arrays.asList("wine", "wijn", "vin", "bottle").contains(word)) return "WINE";
        if (Arrays.asList("coffee", "espresso", "latte", "koffie", "café").contains(word)) return "COFFEE";
        if (Arrays.asList("water", "eau").contains(word)) return "WATER";
        if (Arrays.asList("chocolate", "cacao", "chocolade").contains(word)) return "CHOCOLATE";
        if (Arrays.asList("bread", "brood", "pain").contains(word)) return "BREAD";
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (objectDetector != null) objectDetector.close();
    }
}