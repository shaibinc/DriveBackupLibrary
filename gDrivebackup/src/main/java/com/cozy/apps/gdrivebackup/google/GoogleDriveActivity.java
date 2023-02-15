package com.cozy.apps.gdrivebackup.google;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;


import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.util.ArrayList;
import java.util.List;

public abstract class GoogleDriveActivity extends AppCompatActivity {

    public static final int GOOGLE_SIGN_IN_REQUEST = 1010;

    protected void startGoogleDriveSignIn() {
        startGoogleSignIn();
    }

    protected abstract void onGoogleDriveSignedInSuccess(final Drive driveApi);

    protected abstract void onGoogleDriveSignedInFailed(final ApiException exception);


    protected GoogleSignInOptions getGoogleSignInOptions() {
        Scope scopeDriveAppFolder = new Scope(Scopes.DRIVE_APPFOLDER);
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(scopeDriveAppFolder)
                .requestEmail()
                .requestProfile()
                .build();
    }

    protected void onGoogleSignedInSuccess(final GoogleSignInAccount signInAccount) {
        initializeDriveClient(signInAccount);
    }

    protected void onGoogleSignedInFailed(final ApiException exception) {
        onGoogleDriveSignedInFailed(exception);
    }

    private void initializeDriveClient(GoogleSignInAccount signInAccount) {
        List<String> scopes = new ArrayList<>();
        scopes.add(DriveScopes.DRIVE_APPDATA);

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(this, scopes);
        credential.setSelectedAccount(signInAccount.getAccount());
        Drive.Builder builder = new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential
        );
        String appName = "R.string.app_name";
        Drive driveApi = builder
                .setApplicationName(appName)
                .build();
        onGoogleDriveSignedInSuccess(driveApi);
    }

    private void startGoogleSignIn() {
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, getGoogleSignInOptions());
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GOOGLE_SIGN_IN_REQUEST) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            onGoogleSignedInSuccess(account);
        } catch (ApiException e) {
            onGoogleSignedInFailed(e);
        }
    }

}
