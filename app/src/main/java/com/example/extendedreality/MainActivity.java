package com.example.extendedreality;

import android.Manifest;
import android.content.Intent;
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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
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
    private TextView timerText;
    private View resultCard;
    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ImageLabeler customLabeler;
    private ImageLabeler generalLabeler;

    // Timer variables for the 10-second pop-up
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable clearTextRunnable;

    private String currentCategoryOnScreen = null; // Keeps track of what we are looking at
    private android.os.CountDownTimer popUpTimer;  // The new ticking timer
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive edge-to-edge
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        climateText = findViewById(R.id.climateText);
        popUpText = findViewById(R.id.popUpText);
        timerText = findViewById(R.id.timerText);
        resultCard = findViewById(R.id.resultCard);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Navigation Buttons
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnInfo).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        });

        // Initialize ML Kit Text Recognizer
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Initialize Custom Image Labeler
        LocalModel localModel = new LocalModel.Builder()
                .setAssetFilePath("model_with_metadata.tflite")
                .build();

        CustomImageLabelerOptions customImageLabelerOptions =
                new CustomImageLabelerOptions.Builder(localModel)
                        .setConfidenceThreshold(0.01f)
                        .setMaxResultCount(5)
                        .build();

        customLabeler = ImageLabeling.getClient(customImageLabelerOptions);

        // Initialize General ML Kit Image Labeler
        generalLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

        ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
                    }
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
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalyzer
                );

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Helper method to group words into a main category
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

        // Return null if the word doesn't match our targets
        return null;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            Task<Text> textTask = recognizer.process(image);
            Task<List<ImageLabel>> customLabelTask = customLabeler.process(image);
            Task<List<ImageLabel>> generalLabelTask = generalLabeler.process(image);

            Tasks.whenAllComplete(textTask, customLabelTask, generalLabelTask)
                    .addOnCompleteListener(task -> {
                        String detectedCategory = null;

                        // 1. Check Text Recognition
                        if (textTask.isSuccessful()) {
                            String fullText = textTask.getResult().getText().toLowerCase();
                            // Split text into words to check against our categories
                            String[] words = fullText.split("\\s+");
                            for (String w : words) {
                                String cat = getCategory(w);
                                if (cat != null) {
                                    detectedCategory = cat;
                                    break; // Stop looking once we find a match
                                }
                            }
                        }

                        // 2. Check Custom Apple Model (Only if text didn't find anything)
                        if (detectedCategory == null && customLabelTask.isSuccessful()) {
                            for (ImageLabel label : customLabelTask.getResult()) {
                                if (label.getText().toLowerCase().contains("apple") && label.getConfidence() > 0.70f) {
                                    detectedCategory = "APPLE";
                                    break;
                                }
                            }
                        }

                        // 3. Check General Model (Only if text and custom model found nothing)
                        if (detectedCategory == null && generalLabelTask.isSuccessful()) {
                            for (ImageLabel label : generalLabelTask.getResult()) {
                                // Add a threshold here too so random noise doesn't trigger it
                                if (label.getConfidence() > 0.65f) {
                                    String cat = getCategory(label.getText());
                                    if (cat != null) {
                                        detectedCategory = cat;
                                        break;
                                    }
                                }
                            }
                        }

                        // 4. Update the UI with the ticking 10-second timer
                        final String finalDetectedCategory = detectedCategory;
                        runOnUiThread(() -> {
                            if (finalDetectedCategory != null) {

                                // Only reset the timer if it's a NEW category, or if the text was currently hidden
                                if (!finalDetectedCategory.equals(currentCategoryOnScreen) || resultCard.getVisibility() != View.VISIBLE) {

                                    currentCategoryOnScreen = finalDetectedCategory;
                                    popUpText.setText(finalDetectedCategory);
                                    
                                    // Animate the result card appearing
                                    if (resultCard.getVisibility() != View.VISIBLE) {
                                        resultCard.setVisibility(View.VISIBLE);
                                        resultCard.setAlpha(0f);
                                        resultCard.setScaleX(0.8f);
                                        resultCard.setScaleY(0.8f);
                                        resultCard.animate()
                                                .alpha(1f)
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(300)
                                                .start();
                                    }

                                    // Cancel any existing timer before starting a new one
                                    if (popUpTimer != null) {
                                        popUpTimer.cancel();
                                    }

                                    // Start a 10-second (10000ms) timer that ticks every 1 second (1000ms)
                                    popUpTimer = new android.os.CountDownTimer(5000, 1000) {
                                        public void onTick(long millisUntilFinished) {
                                            // Update the text with the seconds remaining
                                            timerText.setText((millisUntilFinished / 1000) + "s");
                                        }

                                        public void onFinish() {
                                            // Time's up! Animate hide
                                            resultCard.animate()
                                                    .alpha(0f)
                                                    .scaleX(0.8f)
                                                    .scaleY(0.8f)
                                                    .setDuration(300)
                                                    .withEndAction(() -> {
                                                        resultCard.setVisibility(View.INVISIBLE);
                                                        currentCategoryOnScreen = null;
                                                    })
                                                    .start();
                                        }
                                    }.start();
                                }
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
        // Clean up the timer to prevent memory leaks if the user closes the app
        if (popUpTimer != null) popUpTimer.cancel();
    }
}
