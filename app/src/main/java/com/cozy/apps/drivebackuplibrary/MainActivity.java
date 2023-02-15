package com.cozy.apps.drivebackuplibrary;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.cozy.apps.gdrivebackup.google.GoogleDriveApiDataRepository;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


    }
    private void SyncFiles() {
//        backupMessage.showProgressMessage(getString(R.string.backup_in_progress));
      Task<Void> voidTask =
                GoogleDriveApiDataRepository.getInstance(this,"").restoreAllBackupsTask()
                .addOnSuccessListener(r -> {
                    Log.e("drive api", "download failed");
                }).addOnFailureListener(e -> {
                    if (e instanceof UserRecoverableAuthIOException) {

                    } else {
                        Log.e("drive api", "download failed", e);
                        e.printStackTrace();

                    }
                });
    }
}