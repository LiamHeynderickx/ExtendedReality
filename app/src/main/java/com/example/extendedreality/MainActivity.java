package com.example.extendedreality;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final List<String> TARGET_ITEMS = Arrays.asList(
            "water", "wine", "coffee", "chocolate", "cacao", "cocoa", "honey", "fish", "bread", "apple",
            "salmon", "cod", "tuna", "trout", "mackerel", "seafood", "sardine", "herring", "haring",
            "gala", "fuji", "smith", "jonagold", "elstar", "fruit",
            "wijn", "koffie", "chocolade", "honing", "vis", "brood", "appel",
            "zalm", "kabeljauw", "tonijn", "forel", "makreel", "zeevruchten",
            "eau", "vin", "café", "cafe", "chocolat", "miel", "poisson", "pain", "pomme",
            "saumon", "cabillaud", "thon", "truite", "maquereau", "fruits de mer", "hareng",// Consider adding these to TARGET_ITEMS if coffee detection feels inconsistent:
            "espresso", "latte", "cappuccino", "mocha", "moka"
    );

    private PreviewView viewFinder;
    private TextView climateText;
    private ExecutorService cameraExecutor;
    private TextRecognizer recognizer;
    private ImageLabeler labeler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        climateText = findViewById(R.id.climateText);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize ML Kit Text Recognizer and Image Labeler
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

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

                // Setup Image Analysis for ML Kit
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

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            Task<Text> textTask = recognizer.process(image);
            Task<List<ImageLabel>> labelTask = labeler.process(image);

            Tasks.whenAllComplete(textTask, labelTask)
                    .addOnCompleteListener(task -> {
                        StringBuilder matches = new StringBuilder();

                        // 1. Filter Text Results
                        if (textTask.isSuccessful()) {
                            String fullText = textTask.getResult().getText().toLowerCase();
                            for (String item : TARGET_ITEMS) {
                                if (fullText.contains(item)) {
                                    matches.append("Found Text: ").append(item.toUpperCase()).append("\n");
                                }
                            }
                        }

                        // 2. Filter Label Results
                        if (labelTask.isSuccessful()) {
                            for (ImageLabel label : labelTask.getResult()) {
                                String labelText = label.getText().toLowerCase();
                                for (String item : TARGET_ITEMS) {
                                    // Check if label contains target or vice versa (e.g. "Granny Smith Apple")
                                    if (labelText.contains(item)) {
                                        matches.append("Found Object: ").append(item.toUpperCase())
                                                .append(" (").append(String.format(Locale.US, "%.0f%%", label.getConfidence() * 100))
                                                .append(")\n");
                                    }
                                }
                            }
                        }

                        runOnUiThread(() -> {
                            if (matches.length() == 0) {
                                climateText.setText("Scan items: water, wine, coffee, bread...");
                            } else {
                                climateText.setText(matches.toString());
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
    }
}
