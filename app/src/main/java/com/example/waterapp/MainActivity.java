package com.example.waterapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.text.format.DateFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "channel_id01";
    private static final int NOTIFICATION_ID = 1;

    private Handler handler;

    private EditText weightInput;
    private TextView waterIntakeGoal;
    private TextView waterIntakeProgress;
    private ImageView waterDrop;
    private Button add50mlButton;
    private Button add100mlButton;
    private Button add500mlButton;
    private ImageView resetButton;

    private int dailyGoal = 0;
    private int currentIntake = 0;

    private static final String PREFS_NAME = "WaterAppPrefs";
    private static final String KEY_DAILY_GOAL = "dailyGoal";
    private static final String KEY_CURRENT_INTAKE = "currentIntake";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_DAILY_COMPLETION = "dailyCompletion";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendNotification();
                handler.postDelayed(this, 20000); //Тут надо менять значение для интервала между уведомлениями(и в NotificationService)
            }
        }, 20000);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        weightInput = findViewById(R.id.weightInput);
        waterIntakeGoal = findViewById(R.id.waterIntakeGoal);
        waterIntakeProgress = findViewById(R.id.waterIntakeProgress);
        waterDrop = findViewById(R.id.waterDrop);
        add50mlButton = findViewById(R.id.add50mlButton);
        add100mlButton = findViewById(R.id.add100mlButton);
        add500mlButton = findViewById(R.id.add500mlButton);
        resetButton = findViewById(R.id.reset_button);

        loadPreferences();

        weightInput.setOnEditorActionListener((v, actionId, event) -> {
            if (!weightInput.getText().toString().isEmpty()) {
                int weight = Integer.parseInt(weightInput.getText().toString());
                dailyGoal = weight * 30; // 30 мл воды на кг веса
                waterIntakeGoal.setText("Цель: " + dailyGoal + " ml");
                savePreferences(weight, dailyGoal, currentIntake);
            } else {
                weightInput.setError("Введите вес");
            }
            return true;
        });

        add50mlButton.setOnClickListener(v -> addWater(50));
        add100mlButton.setOnClickListener(v -> addWater(100));
        add500mlButton.setOnClickListener(v -> addWater(500));
        resetButton.setOnClickListener(v -> resetPreferences());

        ImageView calendarButton = findViewById(R.id.calendar_button);
        calendarButton.setOnClickListener(v -> showDatePickerDialog());
    }

    private void sendNotification() {
        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("WaterApp")
                .setContentText("Выпей воды!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "My Notification";
            String description = "My notification description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateWaterDropImage() {
        float percentage = dailyGoal == 0 ? 0 : ((float) currentIntake / dailyGoal) * 100;
        TextView completeText = findViewById(R.id.complete);
        completeText.setText("");
        if (percentage < 25) {
            waterDrop.setImageResource(R.drawable.drip_0);
            completeText.setText("");
        } else if (percentage < 50) {
            waterDrop.setImageResource(R.drawable.drip_25);
            completeText.setText("");
        } else if (percentage < 75) {
            waterDrop.setImageResource(R.drawable.drip_50);
            completeText.setText("");
        } else if (percentage < 100) {
            waterDrop.setImageResource(R.drawable.drip_75);
            completeText.setText("");
        } else {
            waterDrop.setImageResource(R.drawable.drip_100);

                completeText.setText("Вы выполнили цель!");
        }
    }

    private void addWater(int amount) {
        if (dailyGoal == 0) {
            if (weightInput.getText().toString().isEmpty()) {
                weightInput.setError("Введите вес");
                return;
            } else {
                int weight = Integer.parseInt(weightInput.getText().toString());
                dailyGoal = weight * 30; // 30 мл воды на кг веса
                waterIntakeGoal.setText("Цель: " + dailyGoal + " ml");
                savePreferences(weight, dailyGoal, currentIntake);
            }
        }
        currentIntake += amount;
        waterIntakeProgress.setText("Выпито: " + currentIntake + " ml");
        updateWaterDropImage();
        savePreferences(Integer.parseInt(weightInput.getText().toString()), dailyGoal, currentIntake);
    }

    private void savePreferences(int weight, int dailyGoal, int currentIntake) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_WEIGHT, weight);
        editor.putInt(KEY_DAILY_GOAL, dailyGoal);
        editor.putInt(KEY_CURRENT_INTAKE, currentIntake);
        editor.apply();
    }

    private void loadPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int weight = sharedPreferences.getInt(KEY_WEIGHT, 0);
        dailyGoal = sharedPreferences.getInt(KEY_DAILY_GOAL, 0);
        currentIntake = sharedPreferences.getInt(KEY_CURRENT_INTAKE, 0);

        weightInput.setText(weight == 0 ? "" : String.valueOf(weight));
        waterIntakeGoal.setText("Цель: " + (dailyGoal == 0 ? "-" : dailyGoal + " ml"));
        waterIntakeProgress.setText("Выпито: " + currentIntake + " ml");

        updateWaterDropImage();
    }

    private void resetPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_WEIGHT);
        editor.remove(KEY_DAILY_GOAL);
        editor.remove(KEY_CURRENT_INTAKE);
        editor.apply();

        weightInput.setText("");
        waterIntakeGoal.setText("Цель: -");
        currentIntake = 0;
        waterIntakeProgress.setText("Выпито: -");
        updateWaterDropImage();
    }

    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            calendar.set(selectedYear, selectedMonth, selectedDay);
            String selectedDate = DateFormat.format("yyyy-MM-dd", calendar).toString();
            showCompletionStatus(selectedDate);
        }, year, month, day);
        datePickerDialog.show();
    }

    private void showCompletionStatus(String selectedDate) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String dailyCompletion = sharedPreferences.getString(KEY_DAILY_COMPLETION, "[]");

        try {
            JSONArray jsonArray = new JSONArray(dailyCompletion);
            boolean found = false;
            boolean completed = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (jsonObject.getString("date").equals(selectedDate)) {
                    completed = jsonObject.getBoolean("completed");
                    found = true;
                    break;
                }
            }

            showAlertDialog(selectedDate, completed, found);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showAlertDialog(String date, boolean completed, boolean found) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(date);
        builder.setMessage(completed ? "Норма выполнена🟢" : "Норма не выполнена🔴");
        builder.setPositiveButton("ОК", (dialog, which) -> dialog.dismiss());
        if (!found) {
            builder.setNeutralButton("Добавить", (dialog, which) -> {
                saveDailyCompletion(date, true);
                dialog.dismiss();
            });
        }
        builder.show();
    }

    private void saveDailyCompletion(String date, boolean isCompleted) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String dailyCompletion = sharedPreferences.getString(KEY_DAILY_COMPLETION, "[]");

        try {
            JSONArray jsonArray = new JSONArray(dailyCompletion);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("date", date);
            jsonObject.put("completed", isCompleted);
            jsonArray.put(jsonObject);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_DAILY_COMPLETION, jsonArray.toString());
            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}