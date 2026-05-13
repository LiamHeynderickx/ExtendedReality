package com.example.extendedreality;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import java.util.Random;

public class HomeActivity extends AppCompatActivity {

    private ImageView rotatingEarth;
    private FrameLayout orbitContainer;
    private MaterialCardView earthCard;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        rotatingEarth = findViewById(R.id.rotatingEarth);
        orbitContainer = findViewById(R.id.orbitContainer);
        earthCard = findViewById(R.id.btnStartCamera);
        View clickOverlay = findViewById(R.id.clickOverlay);

        // 1. Rotation Animation (Existing)
        if (rotatingEarth != null) {
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_slowly);
            rotatingEarth.startAnimation(rotate);
        }

        // 2. Breathing Animation
        startBreathingAnimation();

        // 3. Orbiting Icons (Updated with Emojis and Randomized Start)
        addOrbitingIcons();

        // Navigation
        if (clickOverlay != null) {
            clickOverlay.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(intent);
            });
        }

        View btnInfo = findViewById(R.id.btnInfoTop);
        if (btnInfo != null) {
            btnInfo.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, AboutActivity.class);
                startActivity(intent);
            });
        }
    }

    private void startBreathingAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(earthCard, "scaleX", 1.0f, 1.05f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(earthCard, "scaleY", 1.0f, 1.05f);

        scaleX.setDuration(3000);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);

        scaleY.setDuration(3000);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);

        AnimatorSet breathing = new AnimatorSet();
        breathing.playTogether(scaleX, scaleY);
        breathing.start();
    }

    private void addOrbitingIcons() {
        int[] iconResIds = {
            R.drawable.ic_detailed_apple, 
            R.drawable.ic_detailed_fish, 
            R.drawable.ic_detailed_bread,
            R.drawable.ic_detailed_coffee
        };
        float[] distances = {160f, 200f, 240f, 280f}; // DP from center
        long[] durations = {15000L, 20000L, 25000L, 30000L}; // Slower speeds

        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < iconResIds.length; i++) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResIds[i]);
            int size = (int) (40 * density); // Slightly larger icons
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            lp.gravity = Gravity.CENTER;
            icon.setLayoutParams(lp);
            orbitContainer.addView(icon);

            float startAngle = random.nextFloat() * 360f; // Random starting position
            startOrbitAnimation(icon, distances[i] * density, durations[i], startAngle);
        }
    }

    private void startOrbitAnimation(final View view, final float radius, long duration, float startAngle) {
        ValueAnimator orbit = ValueAnimator.ofFloat(startAngle, startAngle + 360f);
        orbit.setDuration(duration);
        orbit.setInterpolator(new LinearInterpolator());
        orbit.setRepeatCount(ValueAnimator.INFINITE);

        orbit.addUpdateListener(animation -> {
            float angle = (float) animation.getAnimatedValue();
            double radians = Math.toRadians(angle);
            
            float x = (float) (Math.cos(radians) * radius);
            float y = (float) (Math.sin(radians) * radius);
            
            view.setTranslationX(x);
            view.setTranslationY(y);
            
            // Subtle rotation of the icon itself as it orbits
            view.setRotation(angle);
        });

        orbit.start();
    }
}
