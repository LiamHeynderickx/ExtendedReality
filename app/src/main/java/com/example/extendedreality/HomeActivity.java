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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make the layout draw behind the system bars for an immersive look
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

        setContentView(R.layout.activity_home);

        rotatingEarth = findViewById(R.id.rotatingEarth);
        orbitContainer = findViewById(R.id.orbitContainer);
        earthCard = findViewById(R.id.btnStartCamera);
        View clickOverlay = findViewById(R.id.clickOverlay);

        // 1. Breathing Animation
        startBreathingAnimation();

        // 2. Synchronized Orbiting Icons
        addOrbitingIcons();

        // 3. Subtle tap hint animation
        View tapHint = findViewById(R.id.tapHint);
        if (tapHint != null) {
            tapHint.animate()
                    .alpha(0.7f)
                    .setDuration(1500)
                    .setStartDelay(2000)
                    .withEndAction(() -> {
                        tapHint.animate()
                                .alpha(0.3f)
                                .setDuration(1500)
                                .setStartDelay(3000)
                                .start();
                    })
                    .start();
        }

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
            R.drawable.ic_detailed_coffee,
            R.drawable.ic_detailed_wine,
            R.drawable.ic_detailed_honey,
            R.drawable.ic_detailed_chocolate,
            R.drawable.ic_detailed_water
        };
        
        float orbitRadius = 170f; // Reduced from 200f to keep items in frame
        long orbitDuration = 30000L; // Slower uniform speed for more items

        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < iconResIds.length; i++) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconResIds[i]);
            int size = (int) (40 * density);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
            lp.gravity = Gravity.CENTER;
            icon.setLayoutParams(lp);
            orbitContainer.addView(icon);

            // Spaced apart equally (360 / 8 = 45 degrees)
            float startAngle = i * 45f;
            startOrbitAnimation(icon, orbitRadius * density, orbitDuration, startAngle);
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
