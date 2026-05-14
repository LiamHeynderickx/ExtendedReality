package com.example.extendedreality;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.view.View;
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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.common.model.LocalModel;

// NEW OBJECT DETECTION IMPORTS
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private PreviewView viewFinder;
    private TextView climateText;
    private TextView popUpText;

    private ExecutorService cameraExecutor;

    // The two ML pipelines
    private TextRecognizer textRecognizer;
    private ObjectDetector objectDetector; // Upgraded from ImageLabeler!

    // The Flicker-Free Tracking Buffer
    private final Handler trackingHandler = new Handler(Looper.getMainLooper());
    private Runnable hideTextRunnable;
    private String currentCategoryOnScreen = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        climateText = findViewById(R.id.climateText);
        popUpText = findViewById(R.id.popUpText);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 1. Initialize ML Kit Text Recognizer
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // 2. Initialize Custom Object Detector (Make sure you point to your new Roboflow model!)
        LocalModel localModel = new LocalModel.Builder()
                .setAssetFilePath("my_object_detector.tflite") // <-- UPDATE THIS to your new file name
                .build();

        // STREAM_MODE is the secret to smooth AR tracking!
        CustomObjectDetectorOptions customObjectDetectorOptions =
                new CustomObjectDetectorOptions.Builder(localModel)
                        .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                        .enableClassification() // Returns the text labels
                        .setClassificationConfidenceThreshold(0.5f)
                        .setMaxPerObjectLabelCount(1)
                        .build();

        objectDetector = ObjectDetection.getClient(customObjectDetectorOptions);

        ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) startCamera();
                    else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
                }
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

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private String getCategory(String word) {
        word = word.toLowerCase();
        if (Arrays.asList("apple", "gala", "fuji", "smith", "jonagold", "elstar", "appel", "pomme").contains(word)) return "APPLE";
        if (Arrays.asList("fish", "salmon", "cod", "tuna", "trout", "mackerel", "seafood", "sardine", "herring", "haring", "vis", "zalm", "kabeljauw", "tonijn", "forel", "makreel", "zeevruchten", "poisson", "saumon", "cabillaud", "thon", "truite", "maquereau", "hareng", "fruits de mer").contains(word)) return "FISH";
        if (Arrays.asList("coffee", "espresso", "latte", "cappuccino", "mocha", "moka", "koffie", "café", "cafe").contains(word)) return "COFFEE";
        if (Arrays.asList("water", "eau").contains(word)) return "WATER";
        if (Arrays.asList("wine", "wijn", "vin").contains(word)) return "WINE";
        if (Arrays.asList("chocolate", "cacao", "cocoa", "chocolade", "chocolat").contains(word)) return "CHOCOLATE";
        if (Arrays.asList("honey", "honing", "miel").contains(word)) return "HONEY";
        if (Arrays.asList("bread", "brood", "pain").contains(word)) return "BREAD";

        return null;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            Task<Text> textTask = textRecognizer.process(image);
            Task<List<DetectedObject>> objectTask = objectDetector.process(image);

            Tasks.whenAllComplete(textTask, objectTask)
                    .addOnCompleteListener(task -> {
                        String detectedCategory = null;
                        int targetX = -1;
                        int targetY = -1;

                        // 1. Check Text Recognition WITH BOUNDING BOXES
                        if (textTask.isSuccessful()) {
                            Text textResult = textTask.getResult();
                            boolean foundMatch = false;

                            for (Text.TextBlock block : textResult.getTextBlocks()) {
                                for (Text.Line line : block.getLines()) {
                                    for (Text.Element element : line.getElements()) {
                                        String cat = getCategory(element.getText());
                                        if (cat != null) {
                                            detectedCategory = cat;
                                            foundMatch = true;

                                            android.graphics.Rect wordBox = element.getBoundingBox();
                                            if (wordBox != null) {
                                                targetX = wordBox.centerX();
                                                targetY = wordBox.centerY();
                                            }
                                            break;
                                        }
                                    }
                                    if (foundMatch) break;
                                }
                                if (foundMatch) break;
                            }
                        }

                        // 2. Check Custom Object Detection WITH BOUNDING BOXES (If text didn't find anything)
                        if (detectedCategory == null && objectTask.isSuccessful()) {
                            for (DetectedObject obj : objectTask.getResult()) {
                                for (DetectedObject.Label label : obj.getLabels()) {

                                    // Check if the object label matches one of our categories
                                    String cat = getCategory(label.getText());

                                    // Fallback: If it's an apple but the label is just "0 Apple" or "Apple"
                                    if (cat == null && label.getText().toLowerCase().contains("apple")) {
                                        cat = "APPLE";
                                    }

                                    if (cat != null && label.getConfidence() > 0.60f) {
                                        detectedCategory = cat;

                                        // Get the exact center coordinate of the physical object
                                        android.graphics.Rect objBox = obj.getBoundingBox();
                                        targetX = objBox.centerX();
                                        targetY = objBox.centerY();

                                        Log.d(TAG, "AR TRACKING ID: " + obj.getTrackingId()); // Awesome for AR debugging!
                                        break;
                                    }
                                }
                                if (detectedCategory != null) break;
                            }
                        }

                        // 3. Update the UI and TRACK THE TARGET!
                        final String finalDetectedCategory = detectedCategory;
                        final int finalX = targetX;
                        final int finalY = targetY;

                        runOnUiThread(() -> {
                            if (finalDetectedCategory != null) {

                                // --- A. CONTINUOUS TRACKING ---
                                if (finalX != -1 && finalY != -1) {
                                    float scaleX = (float) viewFinder.getWidth() / imageProxy.getHeight();
                                    float scaleY = (float) viewFinder.getHeight() / imageProxy.getWidth();

                                    float screenX = finalX * scaleX;
                                    float screenY = finalY * scaleY;

                                    popUpText.post(() -> {
                                        popUpText.setText(finalDetectedCategory);
                                        popUpText.setVisibility(View.VISIBLE);
                                        popUpText.setX(screenX - (popUpText.getWidth() / 2f));
                                        popUpText.setY(screenY - (popUpText.getHeight() / 2f));
                                    });
                                }

                                // --- B. THE FLICKER BUFFER ---
                                if (hideTextRunnable != null) {
                                    trackingHandler.removeCallbacks(hideTextRunnable);
                                }

                                hideTextRunnable = () -> {
                                    popUpText.setVisibility(View.GONE);
                                    currentCategoryOnScreen = null;
                                };
                                trackingHandler.postDelayed(hideTextRunnable, 1000);
                            }
                        });

                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (hideTextRunnable != null) {
            trackingHandler.removeCallbacks(hideTextRunnable);
        }
    }
}