package com.cozy.apps.gdrivebackup.google;

import static com.google.common.io.Files.copy;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;


import com.cozy.apps.gdrivebackup.DeletedFileManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GoogleDriveApiDataRepository {

    private final String APP_DATA_FOLDER_SPACE = "appDataFolder";
    public static final String BACKUP_FILE_NAME = "backupComplete.dd";
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;
    private static GoogleDriveApiDataRepository driveApiDataRepository;
    private Context context;
    private Task restoreTask;
    private Task backupTask;
    private static String backupFileName;


    public static GoogleDriveApiDataRepository getInstance(Context context,String folderNameInFileDir) {
        backupFileName = folderNameInFileDir;
        initIfRequired(context);
        return driveApiDataRepository;
    }

    public static boolean isBackupDriveIsReady() {
        return driveApiDataRepository != null;
    }

    public static void initIfRequired(Context context) {
        if (driveApiDataRepository != null) {
            return;
        }
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null)
            return;
        List<String> scopes = new ArrayList<>();
        scopes.add(DriveScopes.DRIVE_APPDATA);
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scopes);
        credential.setSelectedAccount(account.getAccount());
        Drive.Builder builder = new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                new GsonFactory(),
                credential);
        String appName = backupFileName;
        Drive driveApi = builder
                .setApplicationName(appName)
                .build();
        GoogleDriveApiDataRepository.init(driveApi, context);
    }

    public static void init(Drive driveService, Context context) {
        if (driveApiDataRepository == null) {
            driveApiDataRepository = new GoogleDriveApiDataRepository(driveService, context);
        }
    }

    public GoogleDriveApiDataRepository(Drive driveApi, Context mContext) {
        this.context = mContext;
        this.mDriveService = driveApi;
    }


    public Task<Void> restoreAllBackupsTask() {
        if (restoreTask == null || restoreTask.isComplete())
            restoreTask = Tasks.call(mExecutor, () -> {
                java.io.File restoreFileDir = new java.io.File(context.getFilesDir() + "/" + backupFileName + "/");
                if (!restoreFileDir.exists())
                    restoreFileDir.mkdirs();
                final FileList fileList = mDriveService.files().list().setSpaces(APP_DATA_FOLDER_SPACE).execute();
                if (fileList == null) {
                    throw new IOException("Null file list when requesting file download.");
                }
                for (File f : fileList.getFiles()) {
                    if (!f.getMimeType().contains("folder")) {
                        continue;
                    }

                    if (new DeletedFileManager(context).isDeletedFile(f.getName())) {
                        deleteFile(f);
                        continue;
                    }

                    java.io.File tempFileName = new java.io.File(context.getCacheDir() + "/" + backupFileName + "/" + f.getName());
                    java.io.File fileName = new java.io.File(restoreFileDir.getPath() + "/" + f.getName());

                    if (fileName.exists()) {
                        continue;
                    }

                    if (!tempFileName.exists()) {
                        tempFileName.mkdirs();
                    }
                    FileList list = mDriveService.files().list().setSpaces(APP_DATA_FOLDER_SPACE)
                            .setQ("parents='" + f.getId() + "'").setFields("*").execute();
                    for (File file : list.getFiles()) {
                        final String fileId = file.getId();
                        java.io.File eachFileInFolder = new java.io.File(tempFileName.getPath() + "/" + file.getName());
                        downloadFile(eachFileInFolder, fileId);
                    }
                    java.io.File[] files = tempFileName.listFiles();
                    fileName.mkdirs();

                    for (java.io.File file1 : files) {
                        java.io.File eachFileInFolder = new java.io.File(fileName.getPath() + "/" + file1.getName());
                        copy(file1, eachFileInFolder);
                    }

                    java.io.File backupComplete = new java.io.File(fileName.getPath(), BACKUP_FILE_NAME);
                    backupComplete.createNewFile();
                }
                return null;
            });
        return restoreTask;
    }

    private void downloadFile(@NonNull final java.io.File file, @NonNull final String fileId) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mDriveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            outputStream.writeTo(stream);
        }
    }

    public Task<Void> backupAllFilesTask(java.io.File[] files) throws IOException {
        if (backupTask == null || backupTask.isComplete())
            backupTask = Tasks.call(mExecutor, () -> {
                for (java.io.File fileDir : files) {
                    java.io.File backupFile = new java.io.File(fileDir.getPath(), BACKUP_FILE_NAME);
                    if (!fileDir.isDirectory()
                            || backupFile.exists()) {
                        continue;
                    }
                    syncFilesWithDrive(fileDir);
                }
                return null;
            });
        return backupTask;
    }

    public Task<Void> checkDrivePermissionGiven() {
        return Tasks.call(mExecutor, () -> {
            mDriveService.files().list().setSpaces(APP_DATA_FOLDER_SPACE).execute();
            return null;
        });
    }


    public Task<Void> backupFileTask(java.io.File file) throws IOException {
        return Tasks.call(mExecutor, () -> {
            syncFilesWithDrive(file);
            return null;
        });
    }

    void syncFilesWithDrive(java.io.File backupFileDir) throws IOException {
        if (!backupFileDir.exists()) {
            return;
        }
        if (!new java.io.File(backupFileDir.getPath(), "contents.json").exists()) {
            Log.d("driveApi", "invalid file");
            return;
        }
        FileList fileList = mDriveService.files().list().setSpaces(APP_DATA_FOLDER_SPACE).execute();
        if (fileList == null) {
            throw new IOException("Null file list when requesting file download.");
        }
        File driveFolder = null;
        for (File f : fileList.getFiles()) {
            if (f.getName().equals(backupFileDir.getName())) {
                driveFolder = f;
                break;
            }
        }
        String folderName = backupFileDir.getName();
        java.io.File backupComplete = new java.io.File(backupFileDir.getPath(), BACKUP_FILE_NAME);
        backupComplete.createNewFile();
        if (driveFolder == null) {
            File folderMetaData = new File();
            folderMetaData.setName(folderName);
            folderMetaData.setParents(Collections.singletonList(APP_DATA_FOLDER_SPACE));
            folderMetaData.setMimeType("application/vnd.google-apps.folder");
            File drFile = mDriveService.files().create(folderMetaData)
                    .setFields("id")
                    .execute();
            String folderId = drFile.getId();
            uploadFiles(backupFileDir.listFiles(), folderId);
        } else {
            //Auto sync ?
            doSync(driveFolder, backupFileDir.getPath());
        }

    }

    private void uploadFiles(java.io.File[] contents, String parentFolder) throws IOException {
        //Get all files in folder
        for (java.io.File fileMetadata : contents) {

            if (fileMetadata.getName().equals(BACKUP_FILE_NAME)) {
                continue;
            }

            File fileMetaFile = new File();
            fileMetaFile.setName(fileMetadata.getName());
            //Get mime/type
            String mimeType = URLConnection.guessContentTypeFromName(fileMetadata.getName());
            FileContent mediaContent = new FileContent(mimeType, fileMetadata);
            fileMetaFile.setParents(Collections.singletonList(parentFolder));
            mDriveService.files().create(fileMetaFile, mediaContent).setFields("id, parents").execute();
        }
    }


    public void doSync(File folderRef, String path) throws IOException {
        java.io.File localFolder = new java.io.File(path);
        String parentId = folderRef.getId();
        FileList result = mDriveService.files().list().setSpaces(APP_DATA_FOLDER_SPACE)
                .setQ("parents='" + parentId + "'").setFields("*").execute();
        List<File> filesDrive = result.getFiles();
        java.io.File[] filesLocal = localFolder.listFiles();
        boolean[] filesSync = new boolean[filesDrive.size()];

        for (java.io.File local : filesLocal) {
            boolean found = false;
            int auxI = 0;
            for (File remote : filesDrive) {
                if (remote.getName().equals(local.getName())) {
                    found = true;
                    long localModified = local.lastModified();
                    long remoteModified = remote.getModifiedTime().getValue();
                    if (localModified > remoteModified) {
                        updateFile(remote, local);
                    }
                    filesSync[auxI] = true;
                    break;
                }
                ++auxI;
            }
            if (!found && !local.getName().equals(BACKUP_FILE_NAME)) {
                uploadFile(local, parentId);
            }
        }
        for (int i = 0; i < filesSync.length; i++) {
            if (!filesSync[i]) {
                File removed = filesDrive.get(i);
                deleteFile(removed);
            }
        }
    }

    public void reset() {
        driveApiDataRepository = null;
    }

    public void uploadFile(java.io.File local, String folderId) throws IOException {
        File newFile = new File();
        newFile.setName(local.getName());
        newFile.setParents(Collections.singletonList(folderId));
        String mimeType = URLConnection.guessContentTypeFromName(local.getName());
        FileContent newContent = new FileContent(mimeType, local);
        mDriveService.files().create(newFile, newContent).setFields("id, parents").execute();
    }

    public Task<Void> deleteFolder(java.io.File fileDir) {
        return Tasks.call(mExecutor, () -> {
            final FileList fileList = mDriveService.files().list().setSpaces(APP_DATA_FOLDER_SPACE).execute();
            if (fileList == null) {
                throw new IOException("Null file list when requesting file download.");
            }
            File driveFolder = null;
            for (File f : fileList.getFiles()) {
                if (f.getName().equals(fileDir.getName())) {
                    driveFolder = f;
                    break;
                }
            }
            if (driveFolder == null) {
                return null;
            }
            deleteFile(driveFolder);
            return null;
        });

    }

    public void deleteFile(File remote) throws IOException {
        mDriveService.files().delete(remote.getId()).execute();
    }

    public void updateFile(File remote, java.io.File local) throws IOException {
        File newFile = new File();
        newFile.setName(local.getName());
        //newFile.setParents(remote.getParents());
        String mimeType = URLConnection.guessContentTypeFromName(local.getName());
        FileContent newContent = new FileContent(mimeType, local);
        File updated = mDriveService.files().update(remote.getId(), newFile, newContent).execute();

    }

}
