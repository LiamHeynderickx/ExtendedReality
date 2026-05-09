package com.example.extendedreality;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {

    private ImageView rotatingEarth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        rotatingEarth = findViewById(R.id.rotatingEarth);
        View clickOverlay = findViewById(R.id.clickOverlay);

        // Start the rotation animation
        if (rotatingEarth != null) {
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_slowly);
            rotatingEarth.startAnimation(rotate);
        }

        // Click Listener for the Overlay (The Button)
        if (clickOverlay != null) {
            clickOverlay.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(intent);
            });
        }

        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                // Placeholder for information/settings
            });
        }
    }
}
