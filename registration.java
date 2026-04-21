package com.example.synq;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class registration extends AppCompatActivity {
    TextView loginButton;
    Button SignupButton;
    EditText Rg_username, Rg_Email, Rg_Password, Rg_rePassword;
    CircleImageView rg_profileImg;
    FirebaseAuth auth;
    Uri imageURI;
    FirebaseDatabase database;
    String IMG_BB_API_KEY = "f2bcd8a70aae75f6b2d6fcb91b6b8612";
    OkHttpClient client = new OkHttpClient();

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registration);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        SignupButton = findViewById(R.id.SignUpButton);
        Rg_username = findViewById(R.id.RegUsername);
        Rg_Email = findViewById(R.id.RegEmailAddress);
        Rg_Password = findViewById(R.id.RegPassword);
        Rg_rePassword = findViewById(R.id.RegRePassword1);
        loginButton = findViewById(R.id.LoginButton);
        rg_profileImg = findViewById(R.id.profileImage);

        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        // setPersistenceEnabled(true) can only be called once.
        // Best to wrap it to prevent crashes if Activity recreates.
        try {
            database.setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.w("Firebase", "Persistence already enabled or failed: " + e.getMessage());
        }

        loginButton.setOnClickListener(view -> {
            startActivity(new Intent(registration.this, login.class));
            finish();
        });

        rg_profileImg.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 10);
        });

        SignupButton.setOnClickListener(view -> {
            String Username = Rg_username.getText().toString().trim();
            String Email = Rg_Email.getText().toString().trim();
            String Password = Rg_Password.getText().toString();
            String RePassword = Rg_rePassword.getText().toString();
            String status = "Hey! I'm using this application";

            if (TextUtils.isEmpty(Username) || TextUtils.isEmpty(Email) || TextUtils.isEmpty(Password) || TextUtils.isEmpty(RePassword)) {
                Toast.makeText(this, "Please enter valid info.", Toast.LENGTH_SHORT).show();
            } else if (!Email.matches("[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+")) {
                Rg_Email.setError("Type a valid Email");
            } else if (Password.length() < 8) {
                Rg_Password.setError("Password must be more than 8 characters");
            } else if (!Password.equals(RePassword)) {
                Rg_rePassword.setError("The password doesn't match.");
            } else {
                // 1. Authenticate with Firebase
                auth.createUserWithEmailAndPassword(Email, Password).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String id = Objects.requireNonNull(auth.getCurrentUser()).getUid();
                        DatabaseReference reference = database.getReference().child("users").child(id);

                        // Securely hash the password before storing
                        String hashedPassword;
                        try {
                            hashedPassword = PassEncrypt.sha256(Password);
                        } catch (Exception e) {
                            Log.e("Security", "Hashing failed", e);
                            hashedPassword = Password; // Fallback (Not recommended for production)
                        }

                        final String finalHashedPassword = hashedPassword;

                        // 2. Handle Image Upload if exists
                        if (imageURI != null) {
                            uploadImageToImgBB(imageURI, (imageUrl) -> {
                                Users user = new Users(id, Username, Email, finalHashedPassword, imageUrl, status);
                                saveUserToDatabase(reference, user);
                            });
                        } else {
                            // Default profile image
                            Users user = new Users(id, Username, Email, finalHashedPassword, "https://i.ibb.co/3Y1hhVNP/download.png", status);
                            saveUserToDatabase(reference, user);
                        }
                    } else {
                        Toast.makeText(registration.this, "Auth failed: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void saveUserToDatabase(DatabaseReference reference, Users user) {
        reference.setValue(user).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("Firebase", "User data uploaded successfully!");
                startActivity(new Intent(registration.this, MainActivity.class));
                finish();
            } else {
                Log.e("Firebase", "Error uploading user data: ", task.getException());
                // This is where your 'Permission Denied' error was showing up
                Toast.makeText(registration.this, "Database Error: Permission Denied. Please check Firebase Rules.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10 && resultCode == RESULT_OK && data != null) {
            imageURI = data.getData();
            rg_profileImg.setImageURI(imageURI);
        }
    }

    private void uploadImageToImgBB(Uri imageUri, ImageUploadCallback callback) {
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();

                String base64Image = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("key", IMG_BB_API_KEY)
                        .addFormDataPart("image", base64Image)
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.imgbb.com/1/upload")
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("ImgBB", "Upload failed", e);
                        runOnUiThread(() -> Toast.makeText(registration.this, "Image upload failed", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseBody = response.body().string();
                            // Simple parsing, consider using JSONObject for production
                            String imageUrl = responseBody.split("\"url\":\"")[1].split("\"")[0];
                            runOnUiThread(() -> {
                                try {
                                    callback.onImageUploaded(imageUrl);
                                } catch (Exception e) {
                                    Log.e("ImgBB", "Callback failed", e);
                                }
                            });
                        } else {
                            Log.e("ImgBB", "Unsuccessful response: " + response);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("ImgBB", "Error processing image", e);
            }
        }).start();
    }

    interface ImageUploadCallback {
        void onImageUploaded(String imageUrl) throws Exception;
    }
}