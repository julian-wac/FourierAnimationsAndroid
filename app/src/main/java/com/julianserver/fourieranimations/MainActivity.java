package com.julianserver.fourieranimations;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private Button playVideoButton;
    private ProgressBar progressBar;
    private VideoView videoView;
    private SeekBar epicyclesSeekBar;
    private TextView epicyclesText;
    private SeekBar speedSeekBar;
    private TextView speedText;
    private ProgressBar renderProgressBar;
    private TextView renderProgressText;

    // Screen dimensions
    private float screenWidth, screenHeight;
    private float centerX, centerY;

    // Touch offset compensation
    private float touchOffsetY = 0;
    private boolean offsetCalculated = false;

    // Animation settings
    private int numEpicycles = 50;  // Default value
    private int animationDuration = 12;  // Default 12 seconds

    // HTTP client for backend communication
    private OkHttpClient httpClient;
    private static final String BACKEND_URL = "http://192.168.0.33:5000";

    // Video management
    private String currentVideoPath = null;

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
        playVideoButton = findViewById(R.id.playVideoButton);
        progressBar = findViewById(R.id.progressBar);
        videoView = findViewById(R.id.videoView);
        epicyclesSeekBar = findViewById(R.id.epicyclesSeekBar);
        epicyclesText = findViewById(R.id.epicyclesText);
        speedSeekBar = findViewById(R.id.speedSeekBar);
        speedText = findViewById(R.id.speedText);
        renderProgressBar = findViewById(R.id.renderProgressBar);
        renderProgressText = findViewById(R.id.renderProgressText);

        // Configure HTTP client with longer timeouts for video generation
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)  // 3 minutes for video generation
                .build();

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
        playVideoButton.setOnClickListener(v -> playCurrentVideo());

        // Set up epicycles slider
        epicyclesSeekBar.setMin(5);  // Minimum 5 epicycles
        epicyclesSeekBar.setMax(200); // Maximum 200 epicycles
        epicyclesSeekBar.setProgress(numEpicycles); // Set default
        epicyclesText.setText("Epicycles: " + numEpicycles);

        epicyclesSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                numEpicycles = progress;
                epicyclesText.setText("Epicycles: " + numEpicycles);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });

        // Set up speed slider
        speedSeekBar.setMin(2);   // Minimum 2 seconds
        speedSeekBar.setMax(30);  // Maximum 30 seconds
        speedSeekBar.setProgress(animationDuration); // Set default
        speedText.setText("Duration: " + animationDuration + "s");

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                animationDuration = progress;
                speedText.setText("Duration: " + animationDuration + "s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Not needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Not needed
            }
        });

        // Initially hide video UI
        videoView.setVisibility(View.GONE);
        playVideoButton.setVisibility(View.GONE);

        // Calculate touch offset after layout is complete
        imageView.post(() -> calculateTouchOffset());
    }

    private void calculateTouchOffset() {
        // Get the location of the ImageView on screen
        int[] location = new int[2];
        imageView.getLocationOnScreen(location);

        // Calculate any offset from the top of the screen
        touchOffsetY = location[1];
        offsetCalculated = true;

        Log.i("Touch", "Touch offset calculated: Y=" + touchOffsetY);
    }

    private void setupDrawing() {
        // Get actual dimensions of the ImageView after layout
        imageView.post(() -> {
            int viewWidth = imageView.getWidth();
            int viewHeight = imageView.getHeight();

            if (viewWidth > 0 && viewHeight > 0) {
                // Use actual view dimensions for the bitmap
                bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
                canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);

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

                // Update center points based on actual view size
                centerX = viewWidth / 2f;
                centerY = viewHeight / 2f;

                Log.i("Drawing", "Canvas initialized: " + viewWidth + "x" + viewHeight);
            }
        });
    }

    public boolean onTouch(View v, MotionEvent event) {
        // Get raw coordinates and adjust for view position
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        // Get view location on screen
        int[] viewLocation = new int[2];
        v.getLocationOnScreen(viewLocation);

        // Calculate actual touch position relative to the view
        float x = rawX - viewLocation[0];
        float y = rawY - viewLocation[1];

        // Alternative method - use regular coordinates but ensure they're within bounds
        // float x = event.getX();
        // float y = event.getY();

        // Ensure coordinates are within view bounds
        x = Math.max(0, Math.min(x, v.getWidth()));
        y = Math.max(0, Math.min(y, v.getHeight()));

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

        // Draw a small circle at the start point for visual feedback
        canvas.drawCircle(x, y, 4, paint);
        imageView.invalidate();
    }

    private void continueStroke(float x, float y) {
        if (isDrawing) {
            currentPath.lineTo(x, y);
            addPointToStroke(x, y);

            // Clear canvas and redraw everything including current path
            canvas.drawColor(Color.WHITE);

            // Redraw all previous strokes
            Path allPreviousPath = new Path();
            PointF prevPoint = null;
            for (PointF point : allPoints) {
                if (prevPoint == null) {
                    allPreviousPath.moveTo(point.x, point.y);
                } else {
                    allPreviousPath.lineTo(point.x, point.y);
                }
                prevPoint = point;
            }
            canvas.drawPath(allPreviousPath, paint);

            // Draw current path
            canvas.drawPath(currentPath, paint);
            imageView.invalidate();
        }
    }

    private void finishStroke(float x, float y) {
        if (isDrawing) {
            addPointToStroke(x, y);
            allPoints.addAll(new ArrayList<>(currentStroke));
            isDrawing = false;
            Log.i("Drawing", "Stroke finished with " + currentStroke.size() + " points");

            // Final redraw
            canvas.drawPath(currentPath, paint);
            imageView.invalidate();
        }
    }

    private void cancelStroke() {
        isDrawing = false;
        currentStroke.clear();
        currentPath.reset();

        // Redraw without the cancelled stroke
        redrawAllStrokes();
    }

    private void redrawAllStrokes() {
        canvas.drawColor(Color.WHITE);

        Path allPath = new Path();
        PointF prevPoint = null;
        for (PointF point : allPoints) {
            if (prevPoint == null) {
                allPath.moveTo(point.x, point.y);
            } else {
                allPath.lineTo(point.x, point.y);
            }
            prevPoint = point;
        }
        canvas.drawPath(allPath, paint);
        imageView.invalidate();
    }

    private void addPointToStroke(float x, float y) {
        currentStroke.add(new PointF(x, y));
    }

    private void generateFourierAnimation() {
        if (allPoints.isEmpty()) {
            Toast.makeText(this, "Please draw something first!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading overlay with progress bar
        findViewById(R.id.loadingOverlay).setVisibility(View.VISIBLE);
        renderProgressBar.setProgress(0);
        renderProgressText.setText("Starting...");
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");

        // Hide video UI during generation
        videoView.setVisibility(View.GONE);
        playVideoButton.setVisibility(View.GONE);

        JSONArray pointsArray = new JSONArray();

        try {
            // Use actual ImageView dimensions (the drawable area only)
            float drawableWidth = imageView.getWidth();
            float drawableHeight = imageView.getHeight();
            float drawableCenterX = drawableWidth / 2f;
            float drawableCenterY = drawableHeight / 2f;

            Log.i("Canvas", "Drawable area dimensions: " + drawableWidth + "x" + drawableHeight);

            // Calculate bounds of the drawing
            float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
            float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;

            for (PointF point : allPoints) {
                // Get normalized coordinates
                float normX = (point.x - drawableCenterX) / (drawableWidth / 2);
                float normY = -(point.y - drawableCenterY) / (drawableHeight / 2);

                minX = Math.min(minX, normX);
                maxX = Math.max(maxX, normX);
                minY = Math.min(minY, normY);
                maxY = Math.max(maxY, normY);

                JSONObject pointObj = new JSONObject();
                pointObj.put("x", normX);
                pointObj.put("y", normY);
                pointsArray.put(pointObj);
            }

            // Create bounds object
            JSONObject boundsObj = new JSONObject();
            boundsObj.put("min_x", minX);
            boundsObj.put("max_x", maxX);
            boundsObj.put("min_y", minY);
            boundsObj.put("max_y", maxY);
            boundsObj.put("center_x", (minX + maxX) / 2);
            boundsObj.put("center_y", (minY + maxY) / 2);
            boundsObj.put("width", maxX - minX);
            boundsObj.put("height", maxY - minY);

            JSONObject requestBody = new JSONObject();
            requestBody.put("points", pointsArray);
            requestBody.put("num_epicycles", numEpicycles);  // Use the slider value
            requestBody.put("animation_length", animationDuration);   // Use the duration slider value

            // Send ImageView dimensions (drawable area only, not full screen)
            requestBody.put("canvas_width", drawableWidth);
            requestBody.put("canvas_height", drawableHeight);

            // Send bounds information
            requestBody.put("bounds", boundsObj);

            // Add timestamp to ensure unique requests
            requestBody.put("timestamp", System.currentTimeMillis());

            Log.i("Request", "Sending animation request with drawable area: " + drawableWidth + "x" + drawableHeight);
            Log.i("Request", String.format("Drawing bounds: X[%.2f, %.2f], Y[%.2f, %.2f]", minX, maxX, minY, maxY));
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

        // Simulate progress updates
        simulateProgress();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showError("Network error: " + e.getMessage());
                    resetUI();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        try {
                            String responseData = response.body().string();
                            JSONObject result = new JSONObject(responseData);
                            String animationUrl = result.getString("animation_url");

                            // Update progress to completion
                            renderProgressBar.setProgress(100);
                            renderProgressText.setText("Complete! Downloading video...");

                            handleAnimationReady(animationUrl);

                        } catch (Exception e) {
                            showError("Error processing response: " + e.getMessage());
                        }
                    } else {
                        showError("Server error: " + response.code());
                    }
                    resetUI();
                });
            }
        });
    }

    private void simulateProgress() {
        // Simulate progress updates based on typical generation time
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                runOnUiThread(() -> {
                    renderProgressBar.setProgress(10);
                    renderProgressText.setText("Processing points...");
                });

                Thread.sleep(2000);
                runOnUiThread(() -> {
                    renderProgressBar.setProgress(25);
                    renderProgressText.setText("Calculating Fourier coefficients...");
                });

                Thread.sleep(3000);
                runOnUiThread(() -> {
                    renderProgressBar.setProgress(40);
                    renderProgressText.setText("Creating epicycles...");
                });

                Thread.sleep(4000);
                runOnUiThread(() -> {
                    renderProgressBar.setProgress(60);
                    renderProgressText.setText("Rendering animation...");
                });

                Thread.sleep(5000);
                runOnUiThread(() -> {
                    renderProgressBar.setProgress(80);
                    renderProgressText.setText("Finalizing video...");
                });

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleAnimationReady(String animationUrl) {
        Log.i("Animation", "Animation ready at: " + animationUrl);

        // Clear any previous video first
        if (currentVideoPath != null) {
            File oldFile = new File(currentVideoPath);
            if (oldFile.exists()) {
                boolean deleted = oldFile.delete();
                Log.i("Video", "Deleted old video: " + deleted);
            }
            currentVideoPath = null;
        }

        Toast.makeText(this, "Animation ready! Downloading...", Toast.LENGTH_SHORT).show();

        // Download the video
        downloadVideo(animationUrl);
    }

    private void downloadVideo(String animationUrl) {
        String fullUrl = BACKEND_URL + animationUrl;

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Cache-Control", "no-cache") // Force fresh download
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> showError("Failed to download video: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Create unique filename with timestamp
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String filename = "fourier_animation_" + timestamp + ".mp4";
                    File videoFile = new File(getFilesDir(), filename);

                    // Delete old video file if it exists
                    if (currentVideoPath != null) {
                        File oldFile = new File(currentVideoPath);
                        if (oldFile.exists()) {
                            oldFile.delete();
                            Log.i("Video", "Deleted old video file");
                        }
                    }

                    try (InputStream inputStream = response.body().byteStream();
                         FileOutputStream outputStream = new FileOutputStream(videoFile)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalBytes = 0;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }

                        currentVideoPath = videoFile.getAbsolutePath();

                        Log.i("Video", "Downloaded new video: " + filename + " (" + totalBytes + " bytes)");

                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "New video downloaded! Ready to play.", Toast.LENGTH_SHORT).show();
                            showVideoControls();
                        });

                    } catch (IOException e) {
                        runOnUiThread(() -> showError("Failed to save video: " + e.getMessage()));
                    }
                } else {
                    runOnUiThread(() -> showError("Failed to download video: " + response.code()));
                }
            }
        });
    }

    private void showVideoControls() {
        playVideoButton.setVisibility(View.VISIBLE);
        playVideoButton.setText("Play Animation");
    }

    private void playCurrentVideo() {
        if (currentVideoPath == null) {
            Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.i("Video", "Playing video: " + currentVideoPath);

        // Hide drawing canvas and show video
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);

        // Clear any previous video and force reload
        videoView.stopPlayback();
        videoView.clearFocus();

        // Set up video playback with fresh URI
        Uri videoUri = Uri.fromFile(new File(currentVideoPath));
        Log.i("Video", "Setting video URI: " + videoUri.toString());

        videoView.setVideoURI(videoUri);

        // Add media controller for play/pause controls
        MediaController mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        mediaController.setAnchorView(videoView);

        // Set up video completion listener
        videoView.setOnCompletionListener(mp -> {
            Log.i("Video", "Video playback completed");
            // Video finished playing, show drawing canvas again
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            playVideoButton.setText("Replay Animation");
        });

        // Set up error listener
        videoView.setOnErrorListener((mp, what, extra) -> {
            Log.e("Video", "Video playback error: what=" + what + ", extra=" + extra);
            showError("Video playback failed");
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            return true;
        });

        // Start playing
        videoView.start();

        Toast.makeText(this, "Playing NEW Fourier animation!", Toast.LENGTH_SHORT).show();
    }

    private void clearDrawing() {
        allPoints.clear();
        currentStroke.clear();
        currentPath.reset();

        canvas.drawColor(Color.WHITE);
        imageView.invalidate();

        // Hide video controls
        videoView.setVisibility(View.GONE);
        playVideoButton.setVisibility(View.GONE);
        currentVideoPath = null;

        // Show drawing canvas
        imageView.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Drawing cleared", Toast.LENGTH_SHORT).show();
    }

    private void showError(String message) {
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
        Log.e("FourierApp", message);
    }

    private void resetUI() {
        findViewById(R.id.loadingOverlay).setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        generateButton.setEnabled(true);
        generateButton.setText("Generate Fourier Animation");
    }
}