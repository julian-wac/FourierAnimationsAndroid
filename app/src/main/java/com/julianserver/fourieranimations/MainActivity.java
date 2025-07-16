package com.julianserver.fourieranimations;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    ImageView imageView;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;

    // Enhanced drawing variables
    private Path currentPath;
    private List<PointF> allPoints;
    private List<PointF> currentStroke;
    private boolean isDrawing = false;

    // UI elements
    private Button generateButton;
    private Button clearButton;
    private ProgressBar progressBar;

    // Screen dimensions
    private float screenWidth, screenHeight;
    private float centerX, centerY;

    // HTTP client for backend communication
    private OkHttpClient httpClient;
    private static final String BACKEND_URL = "http://192.168.1.182:5000"; // Change this to your backend URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeComponents();
        setupDrawing();
    }

    private void initializeComponents() {
        imageView = findViewById(R.id.imageView);
        generateButton = findViewById(R.id.generateButton);
        clearButton = findViewById(R.id.clearButton);
        progressBar = findViewById(R.id.progressBar);

        httpClient = new OkHttpClient();
        allPoints = new ArrayList<>();
        currentStroke = new ArrayList<>();

        Display currentDisplay = getWindowManager().getDefaultDisplay();
        screenWidth = currentDisplay.getWidth();
        screenHeight = currentDisplay.getHeight();
        centerX = screenWidth / 2;
        centerY = screenHeight / 2;

        // Set up button listeners
        generateButton.setOnClickListener(v -> generateFourierAnimation());
        clearButton.setOnClickListener(v -> clearDrawing());
    }

    private void setupDrawing() {
        bitmap = Bitmap.createBitmap((int) screenWidth, (int) screenHeight, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE); // White background

        // Main drawing paint
        paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(8f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        currentPath = new Path();

        imageView.setImageBitmap(bitmap);
        imageView.setOnTouchListener(this);
    }

    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startNewStroke(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                continueStroke(x, y);
                break;

            case MotionEvent.ACTION_UP:
                finishStroke(x, y);
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelStroke();
                break;
        }

        return true;
    }

    private void startNewStroke(float x, float y) {
        isDrawing = true;
        currentStroke.clear();
        currentPath.reset();
        currentPath.moveTo(x, y);

        addPointToStroke(x, y);
    }

    private void continueStroke(float x, float y) {
        if (isDrawing) {
            currentPath.lineTo(x, y);
            addPointToStroke(x, y);

            // Draw on canvas
            canvas.drawPath(currentPath, paint);
            imageView.invalidate();
        }
    }

    private void finishStroke(float x, float y) {
        if (isDrawing) {
            addPointToStroke(x, y);

            // Add current stroke to all points
            allPoints.addAll(new ArrayList<>(currentStroke));

            isDrawing = false;
            Log.i("Drawing", "Stroke finished with " + currentStroke.size() + " points");
            Log.i("Drawing", "Total points: " + allPoints.size());
        }
    }

    private void cancelStroke() {
        isDrawing = false;
        currentStroke.clear();
        currentPath.reset();
    }

    private void addPointToStroke(float x, float y) {
        currentStroke.add(new PointF(x, y));
    }

    private void generateFourierAnimation() {
        if (allPoints.isEmpty()) {
            Toast.makeText(this, "Please draw something first!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");

        Log.i("Fourier", "Starting animation generation with " + allPoints.size() + " points");

        // Convert points to normalized complex coordinates
        JSONArray pointsArray = new JSONArray();

        try {
            for (PointF point : allPoints) {
                JSONObject pointObj = new JSONObject();
                // Normalize coordinates to [-1, 1] range and center
                double normalizedX = (point.x - centerX) / (screenWidth / 2);
                double normalizedY = -(point.y - centerY) / (screenHeight / 2); // Flip Y for math convention

                pointObj.put("x", normalizedX);
                pointObj.put("y", normalizedY);
                pointsArray.put(pointObj);
            }

            // Create request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("points", pointsArray);
            requestBody.put("num_epicycles", 50); // Number of epicycles to use
            requestBody.put("animation_length", 10); // Animation length in seconds

            sendToBackend(requestBody.toString());

        } catch (JSONException e) {
            Log.e("JSON", "Error creating request: " + e.getMessage());
            showError("Error preparing animation data");
            resetUI();
        }
    }

    private void sendToBackend(String jsonData) {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonData, JSON);

        Request request = new Request.Builder()
                .url(BACKEND_URL + "/generate_fourier_animation")
                .post(body)
                .build();

        Log.i("HTTP", "Sending request to: " + BACKEND_URL);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("HTTP", "Request failed: " + e.getMessage());
                runOnUiThread(() -> {
                    showError("Network error: " + e.getMessage());
                    resetUI();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("HTTP", "Response received with code: " + response.code());

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            String responseData = response.body().string();
                            Log.i("HTTP", "Response data: " + responseData);

                            JSONObject result = new JSONObject(responseData);
                            String animationUrl = result.getString("animation_url");

                            // Handle successful animation generation
                            handleAnimationReady(animationUrl);

                        } catch (Exception e) {
                            Log.e("JSON", "Error processing response: " + e.getMessage());
                            showError("Error processing response: " + e.getMessage());
                        }
                    } else {
                        Log.e("HTTP", "Server error: " + response.code());
                        showError("Server error: " + response.code());
                    }
                    resetUI();
                });
            }
        });
    }

    private void handleAnimationReady(String animationUrl) {
        Log.i("Animation", "Animation ready at: " + animationUrl);
        Toast.makeText(this, "Animation ready! Opening...", Toast.LENGTH_LONG).show();

        // Here you could:
        // 1. Download and display the video
        // 2. Open it in a video player
        // 3. Share the URL
        // 4. Show a preview

        // For now, just show success message
        showSuccess("Fourier animation generated successfully!\nURL: " + animationUrl);
    }

    private void clearDrawing() {
        allPoints.clear();
        currentStroke.clear();
        currentPath.reset();

        // Clear the canvas
        canvas.drawColor(Color.WHITE);
        imageView.invalidate();

        Log.i("Drawing", "Drawing cleared");
        Toast.makeText(this, "Drawing cleared", Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
        Log.e("FourierApp", message);
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.i("FourierApp", message);
    }

    private void resetUI() {
        progressBar.setVisibility(View.GONE);
        generateButton.setEnabled(true);
        generateButton.setText("Generate Fourier Animation");
    }

    // Utility method to get drawing stats
    public void logDrawingStats() {
        Log.i("Stats", "Total points collected: " + allPoints.size());
        if (!allPoints.isEmpty()) {
            Log.i("Stats", "Drawing bounds: (" + getMinX() + "," + getMinY() + ") to (" + getMaxX() + "," + getMaxY() + ")");
        }
    }

    private float getMinX() {
        float min = Float.MAX_VALUE;
        for (PointF point : allPoints) {
            if (point.x < min) min = point.x;
        }
        return min;
    }

    private float getMaxX() {
        float max = Float.MIN_VALUE;
        for (PointF point : allPoints) {
            if (point.x > max) max = point.x;
        }
        return max;
    }

    private float getMinY() {
        float min = Float.MAX_VALUE;
        for (PointF point : allPoints) {
            if (point.y < min) min = point.y;
        }
        return min;
    }

    private float getMaxY() {
        float max = Float.MIN_VALUE;
        for (PointF point : allPoints) {
            if (point.y > max) max = point.y;
        }
        return max;
    }
}