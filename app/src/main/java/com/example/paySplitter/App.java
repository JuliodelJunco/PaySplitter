    package com.example.paySplitter;

    import android.content.Context;
    import android.content.Intent;
    import android.content.SharedPreferences;
    import android.content.res.Configuration;
    import android.os.Bundle;
    import android.widget.Toast;

    import androidx.activity.result.ActivityResultLauncher;
    import androidx.activity.result.contract.ActivityResultContracts;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.appcompat.app.AppCompatDelegate;

    import com.example.paySplitter.Controller.APIController;
    import com.example.paySplitter.Model.Group;
    import com.example.paySplitter.Model.User;
    import com.example.paySplitter.View.MainPage;
    import com.google.android.gms.auth.api.signin.GoogleSignIn;
    import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
    import com.google.android.gms.auth.api.signin.GoogleSignInClient;
    import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
    import com.google.android.gms.common.api.Scope;
    import com.google.android.gms.tasks.Task;
    import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
    import com.google.api.client.http.javanet.NetHttpTransport;
    import com.google.api.client.json.gson.GsonFactory;
    import com.google.api.services.drive.Drive;
    import com.google.api.services.drive.DriveScopes;


    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.Locale;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.Future;
    //This class sets up the login of the drive Service and the shared preferences
    public class App extends AppCompatActivity {
        private static ActivityResultLauncher<Intent> signInLauncher;
        private GoogleSignInClient googleSignInClient;
        private final APIController apiController = APIController.getInstance();
        private ExecutorService executor = Executors.newSingleThreadExecutor();
        // This method gets the sharedPrefs of darkMode and Language and sets them
        @Override
        protected void attachBaseContext(Context newBase) {
            SharedPreferences prefs = newBase.getSharedPreferences("app_settings", MODE_PRIVATE);
            String langCode = prefs.getString("lang", Locale.getDefault().getLanguage());

            Locale locale = new Locale(langCode);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.setLocale(locale);

            int nightMode = prefs.getBoolean("dark_mode", false)
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(nightMode);

            super.attachBaseContext(newBase.createConfigurationContext(config));
        }
        //Gets the result of the login if needed and loads the groups
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            signInLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                            if (task.isSuccessful()) {
                                setContentView(R.layout.loading);
                                GoogleSignInAccount account = task.getResult();
                                setupDriveClient(account);
                                Future<ArrayList<Group>> future = executor.submit(() -> apiController.loadGroupNames());
                                executor.submit(() -> {
                                    try{
                                        ArrayList<Group> groups = future.get();
                                        runOnUiThread(() -> {
                                            while (groups == null);
                                            Intent intent = new Intent(this, MainPage.class);
                                            intent.putExtra("groups", groups);
                                            startActivity(intent);
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        recreate();
                                    }
                                });
                            } else {
                                Toast.makeText(this, R.string.login_failed, Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        } else {
                            Toast.makeText(this, R.string.login_canceled, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
            );
            //If the user is already logged in, loads the groups
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                setContentView(R.layout.loading);
                setupDriveClient(account);
                Future<ArrayList<Group>> future = executor.submit(() -> apiController.loadGroupNames());
                executor.submit(() -> {
                    try{
                        ArrayList<Group> groups = future.get();
                        runOnUiThread(() -> {
                            while (groups == null);
                            Intent intent = new Intent(this, MainPage.class);
                            intent.putExtra("groups", groups);
                            startActivity(intent);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        recreate();
                    }
                });
            } else {
                startSignIn();
            }

        }
        //Sets up the drive client into the API Controller
        private void setupDriveClient(GoogleSignInAccount account) {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE));
            credential.setSelectedAccount(account.getAccount());

            try {
                Drive driveService = new Drive.Builder(
                        new NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                ).setApplicationName(getString(R.string.app_name)).build();
                User user = new User();
                user.setName(account.getDisplayName());
                user.setGmail(account.getEmail());
                apiController.setAPIController(driveService,user);
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_initializing, Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
        // If the app is deleted, revokes the access
        @Override
        protected void onDestroy() {
            if (googleSignInClient != null) {
                googleSignInClient.revokeAccess().addOnCompleteListener(task -> {});
            }
            super.onDestroy();
        }
        // Calls to the sign in method from Google
        private void startSignIn() {
            GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(new Scope(DriveScopes.DRIVE))
                    .build();

            googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
            signInLauncher.launch(googleSignInClient.getSignInIntent());
        }


    }
