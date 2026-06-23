package com.detetive.app.plugins;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@CapacitorPlugin(
    name = "Noir",
    permissions = {
        @Permission(alias = "location", strings = {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        }),
        @Permission(alias = "background_location", strings = {
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }),
        @Permission(alias = "camera", strings = {
            android.Manifest.permission.CAMERA
        }),
        @Permission(alias = "sms", strings = {
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS
        }),
        @Permission(alias = "audio", strings = {
            android.Manifest.permission.RECORD_AUDIO
        })
    }
)
public class NoirPlugin extends Plugin {

    private static final String TAG = "NoirPlugin";
    private static Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable = null;
    private long timerEndTime = 0;
    private boolean geoFenceActive = false;
    private BroadcastReceiver geoReceiver = null;
    private boolean geoReceiverRegistered = false;

    // ==========================================
    // SHUTDOWN TIMER
    // ==========================================
    @PluginMethod
    public void startShutdownTimer(PluginCall call) {
        int seconds = call.getInt("seconds", 0);
        if (seconds <= 0) {
            call.reject("Seconds must be > 0");
            return;
        }
        stopTimerInternal();
        timerEndTime = System.currentTimeMillis() + seconds * 1000L;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                triggerPowerDialog();
                JSObject ret = new JSObject();
                ret.put("completed", true);
                call.resolve(ret);
            }
        };
        timerHandler.postDelayed(timerRunnable, seconds * 1000L);
        JSObject ret = new JSObject();
        ret.put("scheduled", true);
        ret.put("seconds", seconds);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopShutdownTimer(PluginCall call) {
        stopTimerInternal();
        call.resolve();
    }

    @PluginMethod
    public void getShutdownTimerStatus(PluginCall call) {
        JSObject ret = new JSObject();
        if (timerRunnable != null && timerEndTime > 0) {
            long remaining = Math.max(0, timerEndTime - System.currentTimeMillis());
            ret.put("active", true);
            ret.put("remainingMs", remaining);
        } else {
            ret.put("active", false);
            ret.put("remainingMs", 0);
        }
        call.resolve(ret);
    }

    private void stopTimerInternal() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
        timerEndTime = 0;
    }

    private void triggerPowerDialog() {
        // Removed for Play Store compliance
    }

    @PluginMethod
    public void triggerCoordinatedShutdown(PluginCall call) {
        call.reject("Funcionalidade não disponível nesta versão.");
    }

    @PluginMethod
    public void setShutdownConfig(PluginCall call) {
        call.reject("Funcionalidade não disponível nesta versão.");
    }

    @PluginMethod
    public void setShutdownCoordinate(PluginCall call) {
        call.reject("Funcionalidade não disponível nesta versão.");
    }

    // ==========================================
    // SYSTEM CONTROLS (GRAYSCALE / DND)
    // ==========================================
    @PluginMethod
    public void enableGrayscale(PluginCall call) {
        try {
            Settings.Secure.putInt(getContext().getContentResolver(),
                "accessibility_display_daltonizer_enabled", 1);
            Settings.Secure.putString(getContext().getContentResolver(),
                "accessibility_display_daltonizer", "1");
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to enable grayscale: " + e.getMessage());
        }
    }

    @PluginMethod
    public void disableGrayscale(PluginCall call) {
        try {
            Settings.Secure.putInt(getContext().getContentResolver(),
                "accessibility_display_daltonizer_enabled", 0);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to disable grayscale: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isGrayscaleActive(PluginCall call) {
        try {
            int enabled = Settings.Secure.getInt(getContext().getContentResolver(),
                "accessibility_display_daltonizer_enabled", 0);
            JSObject ret = new JSObject();
            ret.put("active", enabled == 1);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to check grayscale: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setDoNotDisturb(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);
        try {
            NotificationManager nm = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(enabled
                    ? NotificationManager.INTERRUPTION_FILTER_NONE
                    : NotificationManager.INTERRUPTION_FILTER_ALL);
                call.resolve();
            } else {
                // Request permission
                Intent intent = new Intent(
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                getActivity().startActivity(intent);
                call.reject("Notification policy access not granted");
            }
        } catch (Exception e) {
            call.reject("Failed to set DND: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isDndActive(PluginCall call) {
        try {
            NotificationManager nm = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            JSObject ret = new JSObject();
            if (nm != null) {
                int filter = nm.getCurrentInterruptionFilter();
                ret.put("active", filter == NotificationManager.INTERRUPTION_FILTER_NONE
                    || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS);
                ret.put("filter", filter);
            } else {
                ret.put("active", false);
                ret.put("filter", -1);
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to check DND: " + e.getMessage());
        }
    }

    // ==========================================
    // REMOTE KILL — WIPE ALL APP DATA
    // ==========================================
    @PluginMethod
    public void remoteWipe(PluginCall call) {
        new Thread(() -> {
            try {
                Context ctx = getContext();

                // Wipe internal data dir (files, databases, shared_prefs, cache, code_cache)
                File dataDir = new File(ctx.getApplicationInfo().dataDir);
                File[] entries = dataDir.listFiles();
                if (entries != null) {
                    for (File f : entries) {
                        if (!f.getName().equals("lib")) {
                            deleteRecursive(f);
                        }
                    }
                }

                // Wipe external files if available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    File[] externalDirs = ctx.getExternalFilesDirs(null);
                    if (externalDirs != null) {
                        for (File dir : externalDirs) {
                            if (dir != null) deleteRecursive(dir);
                        }
                    }
                }

                // Clear WebView storage
                try {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                } catch (Exception ignored) {}

                call.resolve();

                // Terminate the process
                Process.killProcess(Process.myPid());
            } catch (Exception e) {
                call.reject("Remote wipe failed: " + e.getMessage());
            }
        }).start();
    }

    // ==========================================
    // GUTMANN / DoD FILE SHREDDER (SAF)
    // ==========================================
    @PluginMethod
    public void pickAndShred(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(call, intent, "onShredderFilePicked");
    }

    @ActivityCallback
    public void onShredderFilePicked(PluginCall call, ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("File selection cancelled");
            return;
        }

        Uri uri = result.getData().getData();
        String method = call.getString("method", "gutmann");
        final int passes = "dod".equals(method) ? 3 : 35;

        new Thread(() -> {
            try {
                shredFile(uri, passes);
                call.resolve();
            } catch (Exception e) {
                call.reject("Shredding failed: " + e.getMessage());
            }
        }).start();
    }

    private void shredFile(Uri uri, int passes) throws IOException {
        Context ctx = getContext();
        ContentResolver cr = ctx.getContentResolver();
        String mode = "rw";

        try (ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, mode)) {
            if (pfd == null) throw new IOException("Cannot open file for writing");

            FileChannel channel = new FileOutputStream(pfd.getFileDescriptor()).getChannel();
            long fileSize = channel.size();
            if (fileSize <= 0) {
                channel.close();
                cr.delete(uri, null);
                return;
            }

            SecureRandom rng = new SecureRandom();
            byte[][] patterns = generateGutmannPatterns(rng);

            for (int pass = 0; pass < passes; pass++) {
                channel.position(0);
                long remaining = fileSize;
                byte[] buf = new byte[8192];
                byte[] pat = patterns[pass % patterns.length];

                while (remaining > 0) {
                    int chunk = (int) Math.min(buf.length, remaining);
                    if (pat.length == 1) {
                        // Single-byte pattern (0x00, 0xFF, etc.)
                        for (int i = 0; i < chunk; i++) buf[i] = pat[0];
                    } else {
                        System.arraycopy(pat, 0, buf, 0, chunk);
                    }
                    ByteBuffer bb = ByteBuffer.wrap(buf, 0, chunk);
                    channel.write(bb);
                    remaining -= chunk;
                }
                channel.force(true);

                // Report progress
                JSObject progress = new JSObject();
                progress.put("pass", pass + 1);
                progress.put("total", passes);
                progress.put("percent", (int) ((pass + 1) * 100.0 / passes));
                notifyListeners("shredderProgress", progress);
            }

            channel.close();
            pfd.close();

            // Delete the file
            cr.delete(uri, null);
        }
    }

    private byte[][] generateGutmannPatterns(SecureRandom rng) {
        byte[][] patterns = new byte[35][];
        // Pass 1-4: random
        for (int i = 0; i < 4; i++) {
            patterns[i] = new byte[1];
            rng.nextBytes(patterns[i]);
        }
        // Pass 5-31: specific magnetic patterns
        byte[] spec = {0x55, (byte)0xAA, (byte)0x92, 0x49, 0x24, 0x00, 0x11, 0x22, 0x44,
                       (byte)0x88, 0x55, (byte)0xAA, (byte)0x92, 0x49, 0x24, 0x00,
                       0x55, (byte)0xAA, (byte)0x92, 0x49, 0x24, 0x00, 0x11, 0x22,
                       0x44, (byte)0x88, 0x55};
        for (int i = 0; i < 27; i++) {
            patterns[4 + i] = new byte[]{spec[i]};
        }
        // Pass 32-35: random
        for (int i = 0; i < 4; i++) {
            patterns[31 + i] = new byte[1];
            rng.nextBytes(patterns[31 + i]);
        }
        return patterns;
    }

    // ==========================================
    // GEO-FENCE (LocationManager + PendingIntent)
    // ==========================================
    private void ensureGeoReceiver() {
        if (geoReceiver != null) return;
        geoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                boolean entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, true);
                JSObject data = new JSObject();
                data.put("entering", entering);
                data.put("message", entering
                    ? "🟢 Entrou na área segura"
                    : "🔴 Saiu da área segura!");
                notifyListeners("geoFenceAlert", data);
            }
        };
    }

    @PluginMethod
    public void startGeoFence(PluginCall call) {
        double lat = call.getDouble("lat", 0.0);
        double lng = call.getDouble("lng", 0.0);
        Float radiusObj = call.getFloat("radius", 100.0f);
        float radius = radiusObj != null ? radiusObj : 100.0f;

        try {
            Context ctx = getContext();

            // Check fine location permission
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionForAlias("location", call, "geoPermissionCallback");
                return;
            }

            // Check if location is enabled
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                call.reject("Location service not available");
                return;
            }
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                getActivity().startActivity(gpsIntent);
                call.reject("Location not enabled. Please enable GPS.");
                return;
            }

            // Register broadcast receiver
            ensureGeoReceiver();
            IntentFilter filter = new IntentFilter("com.detetive.app.GEOFENCE_ALERT");
            int flags = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                flags = Context.RECEIVER_EXPORTED;
            }
            ctx.registerReceiver(geoReceiver, filter, flags);
            geoReceiverRegistered = true;

            // Create PendingIntent
            Intent intent = new Intent("com.detetive.app.GEOFENCE_ALERT");
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                piFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent, piFlags);

            // Add proximity alert
            lm.addProximityAlert(lat, lng, radius, -1, pi);

            geoFenceActive = true;

            JSObject ret = new JSObject();
            ret.put("active", true);
            ret.put("lat", lat);
            ret.put("lng", lng);
            ret.put("radius", radius);
            call.resolve(ret);

        } catch (SecurityException e) {
            call.reject("Location permission denied: " + e.getMessage());
        } catch (Exception e) {
            call.reject("Failed to start geofence: " + e.getMessage());
        }
    }

    @PermissionCallback
    public void geoPermissionCallback(PluginCall call) {
        // Re-check permission after user response
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Retry starting geofence
            startGeoFence(call);
        } else {
            call.reject("Location permission denied by user.");
        }
    }

    @PluginMethod
    public void stopGeoFence(PluginCall call) {
        try {
            Context ctx = getContext();
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Intent intent = new Intent("com.detetive.app.GEOFENCE_ALERT");
                int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    piFlags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent, piFlags);
                lm.removeProximityAlert(pi);
            }
            if (geoReceiver != null && geoReceiverRegistered) {
                try { ctx.unregisterReceiver(geoReceiver); } catch (Exception ignored) {}
                geoReceiverRegistered = false;
            }
            geoFenceActive = false;
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to stop geofence: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getGeoFenceStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("active", geoFenceActive);
        call.resolve(ret);
    }

    // ==========================================
    // INTRUDER SELFIE (Camera2 stealth capture)
    // ==========================================
    private HandlerThread cameraThread = null;
    private Handler cameraHandler = null;
    private CameraDevice cameraDevice = null;

    // Face detection
    private boolean faceDetectionActive = false;
    private HandlerThread faceThread = null;
    private Handler faceHandler = null;
    private Runnable faceRunnable = null;

    // Keyword trigger
    private SpeechRecognizer speechRecognizer = null;
    private boolean keywordActive = false;
    private String[] triggerKeywords = null;
    private AudioRecord keywordAudioRecord = null;
    private HandlerThread keywordThread = null;
    private Handler keywordHandler = null;
    private Runnable keywordRunnable = null;
    private boolean keywordUsingSpeech = false;
    private HandlerThread keywordSpeechThread = null;
    private String selfieCallId = null;

    @PluginMethod
    public void captureSelfie(PluginCall call) {
        Context ctx = getContext();
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionForAlias("camera", call, "cameraPermissionCallback");
            return;
        }
        selfieCallId = call.getCallbackId();
        startCameraCapture(call);
    }

    @PermissionCallback
    public void cameraPermissionCallback(PluginCall call) {
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            selfieCallId = call.getCallbackId();
            startCameraCapture(call);
        } else {
            call.reject("Camera permission denied.");
        }
    }

    private void startCameraCapture(PluginCall call) {
        try {
            CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) { call.reject("Camera service not available"); return; }

            String frontCameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id;
                    break;
                }
            }
            if (frontCameraId == null) { call.reject("No front camera"); return; }

            // Start camera thread
            cameraThread = new HandlerThread("CameraSelfie");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());

            manager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cd) {
                    cameraDevice = cd;
                    takePhoto(cd, call);
                }
                @Override
                public void onDisconnected(CameraDevice cd) {
                    cd.close();
                    cameraDevice = null;
                    if (call.getCallbackId().equals(selfieCallId)) {
                        call.reject("Camera disconnected");
                    }
                }
                @Override
                public void onError(CameraDevice cd, int error) {
                    cd.close();
                    cameraDevice = null;
                    if (call.getCallbackId().equals(selfieCallId)) {
                        call.reject("Camera error: " + error);
                    }
                }
            }, cameraHandler);
        } catch (SecurityException e) {
            call.reject("Camera permission denied: " + e.getMessage());
        } catch (Exception e) {
            call.reject("Camera error: " + e.getMessage());
        }
    }

    private void takePhoto(CameraDevice cd, PluginCall call) {
        try {
            ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            final int imgWidth = 640, imgHeight = 480;

            Surface surface = reader.getSurface();
            cd.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        CaptureRequest.Builder builder = cd.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(surface);
                        session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult tr) {
                                Image image = reader.acquireLatestImage();
                                if (image != null) {
                                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                    byte[] jpeg = new byte[buffer.remaining()];
                                    buffer.get(jpeg);
                                    image.close();
                                    saveSelfieAndRespond(jpeg, call);
                                } else {
                                    call.reject("Failed to capture image");
                                }
                                cd.close();
                                cameraDevice = null;
                                reader.close();
                                if (cameraThread != null) {
                                    cameraThread.quitSafely();
                                    cameraThread = null;
                                }
                            }
                        }, cameraHandler);
                    } catch (Exception e) {
                        call.reject("Capture error: " + e.getMessage());
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession s) {
                    call.reject("Camera session configure failed");
                }
            }, cameraHandler);
        } catch (Exception e) {
            call.reject("Take photo error: " + e.getMessage());
        }
    }

    private void saveSelfieAndRespond(byte[] jpeg, PluginCall call) {
        try {
            // Mirror the image (front camera)
            Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            Matrix matrix = new Matrix();
            matrix.postScale(-1, 1); // Mirror horizontally
            Bitmap mirrored = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mirrored.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] finalJpeg = baos.toByteArray();
            mirrored.recycle();
            bmp.recycle();

            // Save to internal storage
            File dir = new File(getContext().getFilesDir(), "selfies");
            dir.mkdirs();
            new File(dir, ".nomedia").createNewFile();
            File file = new File(dir, "intruder_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(finalJpeg);
            fos.close();

            String b64 = Base64.encodeToString(finalJpeg, Base64.NO_WRAP);

            JSObject ret = new JSObject();
            ret.put("base64", "data:image/jpeg;base64," + b64);
            ret.put("path", file.getAbsolutePath());
            ret.put("size", finalJpeg.length);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Save error: " + e.getMessage());
        }
    }

    // ==========================================
    // SMS GUARDIAN (SmsManager + BroadcastReceiver)
    // ==========================================
    private BroadcastReceiver smsReceiver = null;
    private boolean smsReceiverRegistered = false;

    @PluginMethod
    public void sendSms(PluginCall call) {
        String number = call.getString("number", "");
        String message = call.getString("message", "");
        if (number.isEmpty()) { call.reject("Number is required"); return; }
        if (message.isEmpty()) { call.reject("Message is required"); return; }

        Context ctx = getContext();
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionForAlias("sms", call, "smsSendPermissionCallback");
            return;
        }

        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(number, null, message, null, null);
            call.resolve();
        } catch (Exception e) {
            call.reject("SMS failed: " + e.getMessage());
        }
    }

    @PermissionCallback
    public void smsSendPermissionCallback(PluginCall call) {
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            sendSms(call);
        } else {
            call.reject("SMS permission denied.");
        }
    }

    @PluginMethod
    public void startSmsListener(PluginCall call) {
        if (smsReceiver != null && smsReceiverRegistered) {
            call.resolve();
            return;
        }
        try {
            smsReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
                    Bundle extras = intent.getExtras();
                    if (extras == null) return;
                    Object[] pdus = (Object[]) extras.get("pdus");
                    if (pdus == null) return;
                    StringBuilder fullMsg = new StringBuilder();
                    String sender = "";
                    for (Object pdu : pdus) {
                        SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu);
                        if (sender.isEmpty()) sender = msg.getOriginatingAddress();
                        fullMsg.append(msg.getMessageBody());
                    }
                    JSObject data = new JSObject();
                    data.put("sender", sender != null ? sender : "");
                    data.put("message", fullMsg.toString());
                    notifyListeners("smsReceived", data);
                }
            };
            IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
            int flags = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                flags = Context.RECEIVER_EXPORTED;
            }
            getContext().registerReceiver(smsReceiver, filter, flags);
            smsReceiverRegistered = true;
            call.resolve();
        } catch (Exception e) {
            call.reject("SMS listener failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopSmsListener(PluginCall call) {
        if (smsReceiver != null && smsReceiverRegistered) {
            try {
                getContext().unregisterReceiver(smsReceiver);
            } catch (Exception ignored) {}
            smsReceiverRegistered = false;
        }
        call.resolve();
    }

    // ==========================================
    // BURNER NOTES (Android KeyStore AES-GCM)
    // ==========================================
    private static final String KEYSTORE_ALIAS = "noir_burner_key";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String PREFS_BURNER = "noir_burner";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
        ks.load(null);
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return (SecretKey) ks.getKey(KEYSTORE_ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER);
        kg.init(256);
        SecretKey key = kg.generateKey();
        return key;
    }

    @PluginMethod
    public void burnerEncrypt(PluginCall call) {
        String text = call.getString("text", "");
        if (text.isEmpty()) { call.reject("Text is required"); return; }
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(text.getBytes("UTF-8"));
            JSObject ret = new JSObject();
            ret.put("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            ret.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Encrypt failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void burnerDecrypt(PluginCall call) {
        String encryptedB64 = call.getString("encrypted", "");
        String ivB64 = call.getString("iv", "");
        if (encryptedB64.isEmpty() || ivB64.isEmpty()) {
            call.reject("Encrypted data and IV required");
            return;
        }
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            JSObject ret = new JSObject();
            ret.put("text", new String(decrypted, "UTF-8"));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Decrypt failed: " + e.getMessage());
        }
    }

    // ==========================================
    // BLOQUEIO DE SITES (removed for Play Store)
    // ==========================================
    @PluginMethod
    public void blocklistAdd(PluginCall call) {
        call.reject("Blocklist não disponível nesta versão.");
    }

    @PluginMethod
    public void blocklistRemove(PluginCall call) {
        call.reject("Blocklist não disponível nesta versão.");
    }

    @PluginMethod
    public void blocklistGetAll(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("domains", new String[0]);
        call.resolve(ret);
    }

    @PluginMethod
    public void blockVpnStart(PluginCall call) {
        call.reject("VPN não disponível nesta versão.");
    }

    @PluginMethod
    public void blockVpnStop(PluginCall call) {
        call.resolve();
    }

    // ==========================================
    // AUDIO RECORDER (MediaRecorder)
    // ==========================================
    private MediaRecorder audioRecorder = null;
    private boolean audioRecording = false;
    private String audioFilePath = null;

    @PluginMethod
    public void startAudioRecording(PluginCall call) {
        Context ctx = getContext();
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionForAlias("audio", call, "audioPermissionCallback");
            return;
        }
        try {
            if (audioRecording) { call.reject("Already recording"); return; }

            File dir = new File(ctx.getFilesDir(), "recordings");
            dir.mkdirs();
            audioFilePath = new File(dir, "audio_" + System.currentTimeMillis() + ".mp4").getAbsolutePath();

            audioRecorder = new MediaRecorder();
            audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            audioRecorder.setAudioSamplingRate(44100);
            audioRecorder.setOutputFile(audioFilePath);
            audioRecorder.prepare();
            audioRecorder.start();
            audioRecording = true;

            JSObject ret = new JSObject();
            ret.put("recording", true);
            ret.put("path", audioFilePath);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Audio recording failed: " + e.getMessage());
        }
    }

    @PermissionCallback
    public void audioPermissionCallback(PluginCall call) {
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startAudioRecording(call);
        } else {
            call.reject("Audio permission denied.");
        }
    }

    @PluginMethod
    public void stopAudioRecording(PluginCall call) {
        if (audioRecorder != null && audioRecording) {
            try {
                audioRecorder.stop();
                audioRecorder.release();
            } catch (Exception ignored) {}
            audioRecorder = null;
            audioRecording = false;

            JSObject ret = new JSObject();
            ret.put("recording", false);
            ret.put("path", audioFilePath != null ? audioFilePath : "");
            ret.put("duration", -1);
            call.resolve(ret);
        } else {
            call.reject("Not recording");
        }
    }

    @PluginMethod
    public void getAudioRecordings(PluginCall call) {
        try {
            File dir = new File(getContext().getFilesDir(), "recordings");
            File[] files = dir.listFiles();
            JSObject ret = new JSObject();
            if (files != null) {
                java.util.ArrayList<JSObject> list = new java.util.ArrayList<>();
                for (File f : files) {
                    if (f.getName().endsWith(".mp4")) {
                        JSObject item = new JSObject();
                        item.put("name", f.getName());
                        item.put("path", f.getAbsolutePath());
                        item.put("size", f.length());
                        item.put("lastModified", f.lastModified());
                        list.add(item);
                    }
                }
                ret.put("recordings", list.toArray());
            } else {
                ret.put("recordings", new JSObject[0]);
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to list recordings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteAudioRecording(PluginCall call) {
        String path = call.getString("path", "");
        if (path.isEmpty()) { call.reject("Path required"); return; }
        File f = new File(path);
        if (f.exists() && f.delete()) {
            call.resolve();
        } else {
            call.reject("Failed to delete file");
        }
    }

    // ==========================================
    // SEED-BASED ENCRYPTED AUDIO (AES-GCM)
    // ==========================================
    private static final String NOIR_AUDIO_DIR = "noir_audio";

    private byte[] deriveKeyFromSeed(String seed) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(seed.getBytes("UTF-8"));
    }

    @PluginMethod
    public void encryptRecording(PluginCall call) {
        String seed = call.getString("seed", "");
        if (seed.isEmpty()) { call.reject("Seed is required"); return; }
        try {
            Context ctx = getContext();
            File recordingsDir = new File(ctx.getFilesDir(), "recordings");
            File[] mp4Files = recordingsDir.listFiles((d, name) -> name.endsWith(".mp4"));
            if (mp4Files == null || mp4Files.length == 0) {
                call.reject("No recordings found. Record audio first.");
                return;
            }
            // Find most recent recording
            File latest = mp4Files[0];
            for (File f : mp4Files) {
                if (f.lastModified() > latest.lastModified()) latest = f;
            }

            byte[] fileBytes = java.nio.file.Files.readAllBytes(latest.toPath());
            byte[] keyBytes = deriveKeyFromSeed(seed);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(fileBytes);

            // Format: IV (12 bytes) + encrypted data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(iv);
            baos.write(encrypted);
            byte[] noirData = baos.toByteArray();

            // Save as .noir in noir_audio directory
            File noirDir = new File(ctx.getFilesDir(), NOIR_AUDIO_DIR);
            noirDir.mkdirs();
            String baseName = latest.getName().replace(".mp4", "");
            File noirFile = new File(noirDir, baseName + ".noir");
            FileOutputStream fos = new FileOutputStream(noirFile);
            fos.write(noirData);
            fos.close();

            // Delete the original mp4
            latest.delete();

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("path", noirFile.getAbsolutePath());
            ret.put("name", noirFile.getName());
            ret.put("size", noirFile.length());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Encrypt recording failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void decryptAudioFile(PluginCall call) {
        String fileName = call.getString("fileName", "");
        String seed = call.getString("seed", "");
        if (fileName.isEmpty() || seed.isEmpty()) {
            call.reject("fileName and seed required");
            return;
        }
        try {
            Context ctx = getContext();
            File noirDir = new File(ctx.getFilesDir(), NOIR_AUDIO_DIR);
            File noirFile = new File(noirDir, fileName);
            if (!noirFile.exists()) {
                call.reject("File not found: " + fileName);
                return;
            }

            byte[] noirData = java.nio.file.Files.readAllBytes(noirFile.toPath());
            if (noirData.length < 12) {
                call.reject("Invalid .noir file (too short)");
                return;
            }

            // Extract IV (first 12 bytes) and ciphertext
            byte[] iv = new byte[12];
            System.arraycopy(noirData, 0, iv, 0, 12);
            byte[] encrypted = new byte[noirData.length - 12];
            System.arraycopy(noirData, 12, encrypted, 0, encrypted.length);

            byte[] keyBytes = deriveKeyFromSeed(seed);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            // Write to a temp .mp4 in cache dir for playback
            File cacheDir = new File(ctx.getCacheDir(), "noir_decoded");
            cacheDir.mkdirs();
            String baseName = fileName.replace(".noir", "");
            File tempMp4 = new File(cacheDir, baseName + ".mp4");
            FileOutputStream fos = new FileOutputStream(tempMp4);
            fos.write(decrypted);
            fos.close();

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("path", tempMp4.getAbsolutePath());
            ret.put("name", tempMp4.getName());
            ret.put("size", tempMp4.length());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Decrypt failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void listEncryptedAudio(PluginCall call) {
        try {
            File noirDir = new File(getContext().getFilesDir(), NOIR_AUDIO_DIR);
            File[] files = noirDir.listFiles((d, name) -> name.endsWith(".noir"));
            JSObject ret = new JSObject();
            if (files != null) {
                java.util.ArrayList<JSObject> list = new java.util.ArrayList<>();
                for (File f : files) {
                    JSObject item = new JSObject();
                    item.put("name", f.getName());
                    item.put("size", f.length());
                    item.put("lastModified", f.lastModified());
                    list.add(item);
                }
                ret.put("files", list.toArray());
            } else {
                ret.put("files", new JSObject[0]);
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to list encrypted audio: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteEncryptedAudio(PluginCall call) {
        String fileName = call.getString("fileName", "");
        if (fileName.isEmpty()) { call.reject("fileName required"); return; }
        try {
            File noirDir = new File(getContext().getFilesDir(), NOIR_AUDIO_DIR);
            File f = new File(noirDir, fileName);
            if (f.exists() && f.delete()) {
                call.resolve();
            } else {
                call.reject("File not found or could not be deleted: " + fileName);
            }
        } catch (Exception e) {
            call.reject("Delete failed: " + e.getMessage());
        }
    }

    // ==========================================
    // FLASHLIGHT / SOS (CameraManager Torch)
    // ==========================================
    private Handler torchHandler = null;
    private HandlerThread torchThread = null;
    private Runnable torchRunnable = null;

    private String getTorchCameraId() {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Boolean hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) return id;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void setTorch(boolean on) throws Exception {
        String id = getTorchCameraId();
        if (id == null) throw new Exception("No flash available");
        CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) throw new Exception("Camera service not available");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.setTorchMode(id, on);
        } else {
            throw new Exception("Torch requires Android 6+");
        }
    }

    @PluginMethod
    public void torchOn(PluginCall call) {
        stopTorchPattern();
        try {
            setTorch(true);
            call.resolve();
        } catch (Exception e) {
            call.reject("Torch on failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void torchOff(PluginCall call) {
        stopTorchPattern();
        try {
            setTorch(false);
            call.resolve();
        } catch (Exception e) {
            call.reject("Torch off failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void torchSos(PluginCall call) {
        stopTorchPattern();
        try {
            torchThread = new HandlerThread("TorchSOS");
            torchThread.start();
            torchHandler = new Handler(torchThread.getLooper());
            torchSosActive = true;
            final int[] step = {0};
            // SOS pattern: 3 short, 3 long, 3 short (unit=200ms)
            final int[] onTimes = {200, 200, 200, 600, 600, 600, 200, 200, 200};
            final int[] offTimes = {200, 200, 200, 200, 200, 200, 200, 200, 200};
            torchRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!torchSosActive || step[0] >= onTimes.length) {
                        try { setTorch(false); } catch (Exception ignored) {}
                        torchSosActive = false;
                        return;
                    }
                    try {
                        setTorch(true);
                        torchHandler.postDelayed(() -> {
                            try {
                                setTorch(false);
                            } catch (Exception ignored) {}
                            if (torchSosActive) {
                                step[0]++;
                                torchHandler.postDelayed(this, offTimes[step[0] - 1]);
                            }
                        }, onTimes[step[0]]);
                    } catch (Exception ignored) {
                        torchSosActive = false;
                    }
                }
            };
            torchRunnable.run();
            call.resolve();
        } catch (Exception e) {
            call.reject("SOS failed: " + e.getMessage());
        }
    }

    private boolean torchSosActive = false;
    private boolean torchStrobeActive = false;

    @PluginMethod
    public void torchStrobe(PluginCall call) {
        stopTorchPattern();
        try {
            torchThread = new HandlerThread("TorchStrobe");
            torchThread.start();
            torchHandler = new Handler(torchThread.getLooper());
            torchStrobeActive = true;
            final boolean[] on = {true};
            torchRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!torchStrobeActive) {
                        try { setTorch(false); } catch (Exception ignored) {}
                        return;
                    }
                    try {
                        setTorch(on[0]);
                    } catch (Exception ignored) {}
                    on[0] = !on[0];
                    torchHandler.postDelayed(this, 100);
                }
            };
            torchRunnable.run();
            call.resolve();
        } catch (Exception e) {
            call.reject("Strobe failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void torchStopPattern(PluginCall call) {
        stopTorchPattern();
        try { setTorch(false); } catch (Exception ignored) {}
        call.resolve();
    }

    private void stopTorchPattern() {
        torchSosActive = false;
        torchStrobeActive = false;
        if (torchRunnable != null) {
            if (torchHandler != null) torchHandler.removeCallbacks(torchRunnable);
            torchRunnable = null;
        }
        if (torchThread != null) {
            torchThread.quitSafely();
            torchThread = null;
        }
        torchHandler = null;
    }

    // ==========================================
    // WI-FI SCANNER (WifiManager)
    // ==========================================
    private BroadcastReceiver wifiReceiver = null;
    private boolean wifiReceiverRegistered = false;
    private List<ScanResult> lastWifiResults = new java.util.ArrayList<>();
    private PluginCall wifiPendingCall = null;

    @PluginMethod
    public void wifiStartScan(PluginCall call) {
        try {
            Context ctx = getContext();
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm == null) { call.reject("WiFi not available"); return; }

            if (!wm.isWifiEnabled()) { call.reject("WiFi is disabled"); return; }

            // Save call reference for async response
            wifiPendingCall = call;

            if (wifiReceiver == null) {
                wifiReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                            WifiManager wm2 = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                            if (wm2 != null && success) {
                                lastWifiResults = wm2.getScanResults();
                            }
                            respondWifiResults();
                        }
                    }
                };
            }

            if (!wifiReceiverRegistered) {
                IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                int flags = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    flags = Context.RECEIVER_EXPORTED;
                }
                ctx.registerReceiver(wifiReceiver, filter, flags);
                wifiReceiverRegistered = true;
            }

            boolean started = wm.startScan();
            if (!started) {
                // Scan failed, return cached results
                respondWifiResults();
            }
            // If scan started, response comes via receiver (with timeout fallback)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (wifiPendingCall != null) {
                    respondWifiResults();
                }
            }, 10000);
        } catch (Exception e) {
            call.reject("WiFi scan failed: " + e.getMessage());
        }
    }

    private void respondWifiResults() {
        PluginCall call = wifiPendingCall;
        if (call == null) return;
        wifiPendingCall = null;

        JSObject ret = new JSObject();
        java.util.ArrayList<JSObject> list = new java.util.ArrayList<>();
        for (ScanResult r : lastWifiResults) {
            JSObject item = new JSObject();
            item.put("ssid", r.SSID != null ? r.SSID : "");
            item.put("bssid", r.BSSID != null ? r.BSSID : "");
            item.put("level", r.level);
            item.put("frequency", r.frequency);
            long ts = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                try { ts = r.timestamp; } catch (Exception ignored) {}
            }
            item.put("timestamp", ts);
            list.add(item);
        }
        ret.put("networks", list.toArray());
        ret.put("count", list.size());
        call.resolve(ret);
    }

    @PluginMethod
    public void wifiGetScanResults(PluginCall call) {
        JSObject ret = new JSObject();
        java.util.ArrayList<JSObject> list = new java.util.ArrayList<>();
        for (ScanResult r : lastWifiResults) {
            JSObject item = new JSObject();
            item.put("ssid", r.SSID != null ? r.SSID : "");
            item.put("bssid", r.BSSID != null ? r.BSSID : "");
            item.put("level", r.level);
            item.put("frequency", r.frequency);
            list.add(item);
        }
        ret.put("networks", list.toArray());
        ret.put("count", list.size());
        call.resolve(ret);
    }

    // ==========================================
    // BIOMETRIC AUTH (BiometricPrompt API 28+)
    // ==========================================
    @PluginMethod
    public void biometricIsAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                java.lang.Object bm = getContext().getSystemService("biometric");
                if (bm != null) {
                    int result = 0;
                    try {
                        java.lang.reflect.Method m = bm.getClass().getMethod("canAuthenticate");
                        result = (int) m.invoke(bm);
                    } catch (Exception ignored) {}
                    ret.put("available", result == 0);
                    ret.put("reason", result);
                } else {
                    ret.put("available", false);
                }
            } catch (Exception e) {
                ret.put("available", false);
            }
        } else {
            ret.put("available", false);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void biometricAuth(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            call.reject("Biometric not available (API < 28)");
            return;
        }
        try {
            android.hardware.biometrics.BiometricPrompt bp = new android.hardware.biometrics.BiometricPrompt.Builder(getContext())
                .setTitle("Detetive App")
                .setSubtitle("Desbloqueio biométrico")
                .setDescription("Use sua digital ou face para acessar")
                .setNegativeButton("Cancelar",
                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                    (dialog, which) -> call.reject("Auth cancelled"))
                .build();

            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            android.os.CancellationSignal cancelSignal = new android.os.CancellationSignal();
            bp.authenticate(cancelSignal, executor,
                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        call.resolve();
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        call.reject("Auth error: " + errString);
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        call.reject("Auth failed");
                    }
                });
        } catch (Exception e) {
            call.reject("Biometric error: " + e.getMessage());
        }
    }

    // ==========================================
    // SOUND METER (AudioRecord)
    // ==========================================
    private AudioRecord soundMeter = null;
    private boolean soundMeterActive = false;
    private Handler soundMeterHandler = null;
    private HandlerThread soundMeterThread = null;
    private Runnable soundMeterRunnable = null;

    @PluginMethod
    public void startSoundMeter(PluginCall call) {
        try {
            if (soundMeterActive) { call.resolve(); return; }
            int sampleRate = 44100;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            soundMeter = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            soundMeter.startRecording();
            soundMeterActive = true;

            soundMeterThread = new HandlerThread("SoundMeter");
            soundMeterThread.start();
            soundMeterHandler = new Handler(soundMeterThread.getLooper());

            soundMeterRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!soundMeterActive) return;
                    try {
                        short[] buf = new short[1024];
                        int read = soundMeter.read(buf, 0, buf.length);
                        if (read > 0) {
                            double sum = 0;
                            for (int i = 0; i < read; i++) sum += buf[i] * buf[i];
                            double rms = Math.sqrt(sum / read);
                            double db = 20 * Math.log10(rms > 0 ? rms : 1);
                            double level = Math.min(100, Math.max(0, (db + 50) * 1.5));
                            JSObject data = new JSObject();
                            data.put("level", Math.round(level));
                            data.put("db", Math.round(db * 100.0) / 100.0);
                            notifyListeners("soundLevel", data);
                        }
                    } catch (Exception ignored) {}
                    soundMeterHandler.postDelayed(this, 200);
                }
            };
            soundMeterRunnable.run();
            call.resolve();
        } catch (Exception e) {
            call.reject("Sound meter start failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopSoundMeter(PluginCall call) {
        soundMeterActive = false;
        if (soundMeterRunnable != null && soundMeterHandler != null) {
            soundMeterHandler.removeCallbacks(soundMeterRunnable);
        }
        if (soundMeterThread != null) {
            soundMeterThread.quitSafely();
            soundMeterThread = null;
        }
        soundMeterHandler = null;
        soundMeterRunnable = null;
        if (soundMeter != null) {
            try { soundMeter.stop(); } catch (Exception ignored) {}
            soundMeter.release();
            soundMeter = null;
        }
        call.resolve();
    }

    @PluginMethod
    public void getSoundLevel(PluginCall call) {
        try {
            int sampleRate = 44100;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            recorder.startRecording();
            short[] buf = new short[bufferSize / 2];
            recorder.read(buf, 0, buf.length);
            recorder.stop();
            recorder.release();

            double sum = 0;
            for (short s : buf) sum += s * s;
            double rms = Math.sqrt(sum / buf.length);
            double db = 20 * Math.log10(rms > 0 ? rms : 1);
            double level = Math.min(100, Math.max(0, (db + 50) * 1.5));

            JSObject ret = new JSObject();
            ret.put("level", Math.round(level));
            ret.put("db", Math.round(db * 100.0) / 100.0);
            ret.put("rms", rms);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Sound meter error: " + e.getMessage());
        }
    }

    // ==========================================
    // COMPASS (SensorManager)
    // ==========================================
    private String getDirection(float azimuth) {
        if (azimuth < 22.5 || azimuth >= 337.5) return "N";
        if (azimuth < 67.5) return "NE";
        if (azimuth < 112.5) return "E";
        if (azimuth < 157.5) return "SE";
        if (azimuth < 202.5) return "S";
        if (azimuth < 247.5) return "SW";
        if (azimuth < 292.5) return "W";
        return "NW";
    }

    @PluginMethod
    public void getCompassHeading(PluginCall call) {
        try {
            SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            Sensor magnetic = sm.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD);
            Sensor accelerometer = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
            if (magnetic == null || accelerometer == null) {
                call.reject("Compass sensors not available");
                return;
            }

            final float[] gravity = new float[3];
            final float[] geomag = new float[3];
            final boolean[] done = {false, false};

            android.hardware.SensorEventListener listener = new android.hardware.SensorEventListener() {
                @Override
                public void onSensorChanged(android.hardware.SensorEvent event) {
                    if (event.sensor.getType() == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, gravity, 0, 3);
                        done[0] = true;
                    }
                    if (event.sensor.getType() == android.hardware.Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, geomag, 0, 3);
                        done[1] = true;
                    }
                    if (done[0] && done[1]) {
                        sm.unregisterListener(this);
                        float[] R = new float[9];
                        float[] I = new float[9];
                        if (android.hardware.SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
                            float[] orientation = new float[3];
                            android.hardware.SensorManager.getOrientation(R, orientation);
                            float azimuthDeg = (float) Math.toDegrees(orientation[0]);
                            if (azimuthDeg < 0) azimuthDeg += 360;

                            JSObject ret = new JSObject();
                            ret.put("azimuth", azimuthDeg);
                            ret.put("pitch", (float) Math.toDegrees(orientation[1]));
                            ret.put("roll", (float) Math.toDegrees(orientation[2]));
                            ret.put("direction", getDirection(azimuthDeg));
                            call.resolve(ret);
                        } else {
                            call.reject("Could not compute rotation matrix");
                        }
                    }
                }
                @Override
                public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
            };

            sm.registerListener(listener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
            sm.registerListener(listener, magnetic, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                sm.unregisterListener(listener);
                if (!done[0] || !done[1]) {
                    call.reject("Compass timeout");
                }
            }, 3000);
        } catch (Exception e) {
            call.reject("Compass error: " + e.getMessage());
        }
    }

    // ==========================================
    // FACE DETECTION
    // ==========================================
    @PluginMethod
    public void startFaceDetection(PluginCall call) {
        if (faceDetectionActive) { call.resolve(); return; }
        try {
            faceDetectionActive = true;
            faceThread = new HandlerThread("FaceDetect");
            faceThread.start();
            faceHandler = new Handler(faceThread.getLooper());
            faceRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!faceDetectionActive || cameraDevice == null) return;
                    try {
                        final ImageReader reader = ImageReader.newInstance(320, 240, ImageFormat.NV21, 2);
                        reader.setOnImageAvailableListener(r -> {
                            try (Image img = r.acquireLatestImage()) {
                                if (img == null) return;
                                Image.Plane[] planes = img.getPlanes();
                                if (planes.length > 0) {
                                    ByteBuffer buffer = planes[0].getBuffer();
                                    byte[] nv21 = new byte[buffer.remaining()];
                                    buffer.get(nv21);
                                    // Convert NV21 to RGBA bitmap for FaceDetector
                                    int width = img.getWidth();
                                    int height = img.getHeight();
                                    Bitmap bmp = nv21ToBitmap(nv21, width, height);
                                    if (bmp != null) {
                                        FaceDetector detector = new FaceDetector(bmp.getWidth(), bmp.getHeight(), 8);
                                        Face[] faces = new Face[8];
                                        int count = detector.findFaces(bmp, faces);
                                        if (count > 0) {
                                            JSObject data = new JSObject();
                                            data.put("count", count);
                                            data.put("confidence", faces[0].confidence());
                                            data.put("timestamp", System.currentTimeMillis());
                                            notifyListeners("faceDetected", data);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }, faceHandler);
                        try {
                            final Surface surface = reader.getSurface();
                            final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    try {
                                        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                        builder.addTarget(surface);
                                        session.setRepeatingRequest(builder.build(), null, null);
                                    } catch (Exception ignored) {}
                                }
                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {}
                            };
                            cameraDevice.createCaptureSession(java.util.Collections.singletonList(surface), sessionCallback, null);
                        } catch (Exception ignored) {}
                    } catch (Exception ignored) {}
                    if (faceDetectionActive) {
                        faceHandler.postDelayed(this, 2000);
                    }
                }
            };
            faceHandler.post(faceRunnable);
            call.resolve();
        } catch (Exception e) {
            call.reject("Face detection error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopFaceDetection(PluginCall call) {
        faceDetectionActive = false;
        if (faceRunnable != null && faceHandler != null) {
            faceHandler.removeCallbacks(faceRunnable);
        }
        if (faceThread != null) {
            faceThread.quitSafely();
            faceThread = null;
        }
        faceHandler = null;
        faceRunnable = null;
        call.resolve();
    }

    private Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
        int[] pixels = new int[width * height];
        int frameSize = width * height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yIndex = y * width + x;
                int uvIndex = frameSize + (y / 2) * width + (x / 2) * 2;
                int Y = nv21[yIndex] & 0xFF;
                int U = nv21[uvIndex] & 0xFF;
                int V = nv21[uvIndex + 1] & 0xFF;
                // Convert YUV to RGB
                int R = (int) (Y + 1.4075 * (V - 128));
                int G = (int) (Y - 0.3455 * (U - 128) - 0.7169 * (V - 128));
                int B = (int) (Y + 1.7790 * (U - 128));
                R = Math.max(0, Math.min(255, R));
                G = Math.max(0, Math.min(255, G));
                B = Math.max(0, Math.min(255, B));
                pixels[yIndex] = 0xFF000000 | (R << 16) | (G << 8) | B;
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    // ==========================================
    // KEYWORD TRIGGER
    // ==========================================
    @PluginMethod
    public void startKeywordTrigger(PluginCall call) {
        if (keywordActive) { call.resolve(); return; }
        String keyword = call.getString("keyword", "");
        if (keyword.isEmpty()) { call.reject("Keyword required"); return; }
        try {
            keywordActive = true;
            triggerKeywords = keyword.toLowerCase().split(",");
            for (int i = 0; i < triggerKeywords.length; i++) {
                triggerKeywords[i] = triggerKeywords[i].trim();
            }

            // Try SpeechRecognizer first
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
                if (speechRecognizer != null) {
                    keywordUsingSpeech = true;
                    final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
                    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

                    speechRecognizer.setRecognitionListener(new RecognitionListener() {
                        @Override
                        public void onResults(Bundle results) {
                            if (!keywordActive) return;
                            checkSpeechResults(results);
                            // Restart listening
                            if (keywordActive && speechRecognizer != null) {
                                speechRecognizer.startListening(recognizerIntent);
                            }
                        }
                        @Override
                        public void onPartialResults(Bundle partialResults) {
                            if (!keywordActive) return;
                            checkSpeechResults(partialResults);
                        }
                        @Override public void onReadyForSpeech(Bundle params) {}
                        @Override public void onBeginningOfSpeech() {}
                        @Override public void onRmsChanged(float rmsdB) {}
                        @Override public void onBufferReceived(byte[] buffer) {}
                        @Override public void onEndOfSpeech() {
                            if (keywordActive && speechRecognizer != null) {
                                speechRecognizer.startListening(recognizerIntent);
                            }
                        }
                        @Override public void onError(int error) {
                            // SpeechRecognizer failed, fall back to AudioRecord
                            if (keywordActive && !keywordUsingSpeech) {
                                startKeywordAudioFallback();
                            }
                        }
                        @Override public void onEvent(int eventType, Bundle params) {}
                    });
                    speechRecognizer.startListening(recognizerIntent);
                    call.resolve();
                    return;
                }
            } catch (Exception ignored) {
                speechRecognizer = null;
            }

            // Fallback to AudioRecord amplitude monitoring
            keywordUsingSpeech = false;
            startKeywordAudioFallback();
            call.resolve();
        } catch (Exception e) {
            keywordActive = false;
            call.reject("Keyword trigger error: " + e.getMessage());
        }
    }

    private void checkSpeechResults(Bundle results) {
        if (results == null) return;
        java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null) return;
        for (String match : matches) {
            String lower = match.toLowerCase();
            for (String kw : triggerKeywords) {
                if (lower.contains(kw)) {
                    JSObject data = new JSObject();
                    data.put("keyword", kw);
                    data.put("text", match);
                    data.put("source", "speech");
                    data.put("timestamp", System.currentTimeMillis());
                    notifyListeners("keywordTrigger", data);
                    return;
                }
            }
        }
    }

    private void startKeywordAudioFallback() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize <= 0) bufferSize = 4096;
            keywordAudioRecord = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC, 8000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 4);
            keywordAudioRecord.startRecording();
            keywordThread = new HandlerThread("KeywordAudio");
            keywordThread.start();
            keywordHandler = new Handler(keywordThread.getLooper());
            keywordRunnable = new Runnable() {
                final short[] buf = new short[512];
                long lastActiveTime = 0;
                long activeDuration = 0;

                @Override
                public void run() {
                    if (!keywordActive || keywordAudioRecord == null) return;
                    int read = keywordAudioRecord.read(buf, 0, buf.length);
                    if (read > 0) {
                        long sum = 0;
                        for (int i = 0; i < read; i++) sum += Math.abs(buf[i]);
                        long avg = sum / read;
                        // If above threshold, accumulate active time
                        if (avg > 1500) {
                            if (lastActiveTime == 0) lastActiveTime = System.currentTimeMillis();
                            activeDuration += 200;
                            if (activeDuration > 3000) {
                                // Sustained speech-level audio detected
                                JSObject data = new JSObject();
                                data.put("keyword", "audio_activity");
                                data.put("amplitude", avg);
                                data.put("source", "audio_fallback");
                                data.put("duration", activeDuration);
                                data.put("timestamp", System.currentTimeMillis());
                                notifyListeners("keywordTrigger", data);
                                activeDuration = 0;
                                lastActiveTime = 0;
                            }
                        } else {
                            lastActiveTime = 0;
                            activeDuration = 0;
                        }
                    }
                    if (keywordActive) {
                        keywordHandler.postDelayed(this, 200);
                    }
                }
            };
            keywordHandler.post(keywordRunnable);
        } catch (Exception ignored) {}
    }

    @PluginMethod
    public void stopKeywordTrigger(PluginCall call) {
        keywordActive = false;
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        if (keywordRunnable != null && keywordHandler != null) {
            keywordHandler.removeCallbacks(keywordRunnable);
        }
        if (keywordThread != null) {
            keywordThread.quitSafely();
            keywordThread = null;
        }
        keywordHandler = null;
        keywordRunnable = null;
        if (keywordAudioRecord != null) {
            try { keywordAudioRecord.stop(); } catch (Exception ignored) {}
            keywordAudioRecord.release();
            keywordAudioRecord = null;
        }
        keywordUsingSpeech = false;
        call.resolve();
    }

    // ==========================================
    // STEALTH MODE (removed for Play Store compliance)
    // ==========================================
    @PluginMethod
    public void toggleStealthMode(PluginCall call) {
        call.reject("Stealth mode não disponível nesta versão.");
    }

    @PluginMethod
    public void getStealthMode(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("stealth", false);
        call.resolve(ret);
    }

    public static void ensureDefaultState(android.content.Context context) {
        // No-op: stealth mode removed
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================
    @Override
    protected void handleOnDestroy() {
        if (geoReceiver != null && geoReceiverRegistered) {
            try { getContext().unregisterReceiver(geoReceiver); } catch (Exception ignored) {}
            geoReceiverRegistered = false;
        }
        if (smsReceiver != null && smsReceiverRegistered) {
            try { getContext().unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
            smsReceiverRegistered = false;
        }
        if (wifiReceiver != null && wifiReceiverRegistered) {
            try { getContext().unregisterReceiver(wifiReceiver); } catch (Exception ignored) {}
            wifiReceiverRegistered = false;
        }
        if (audioRecorder != null) {
            try {
                if (audioRecording) audioRecorder.stop();
                audioRecorder.release();
            } catch (Exception ignored) {}
            audioRecorder = null;
            audioRecording = false;
        }
        stopSoundMeterInternal();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
        stopTorchPattern();
        stopFaceDetectionInternal();
        stopKeywordTriggerInternal();
        super.handleOnDestroy();
    }

    private void stopFaceDetectionInternal() {
        faceDetectionActive = false;
        if (faceRunnable != null && faceHandler != null) {
            faceHandler.removeCallbacks(faceRunnable);
        }
        if (faceThread != null) {
            faceThread.quitSafely();
            faceThread = null;
        }
        faceHandler = null;
        faceRunnable = null;
    }

    private void stopKeywordTriggerInternal() {
        keywordActive = false;
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        if (keywordRunnable != null && keywordHandler != null) {
            keywordHandler.removeCallbacks(keywordRunnable);
        }
        if (keywordThread != null) {
            keywordThread.quitSafely();
            keywordThread = null;
        }
        keywordHandler = null;
        keywordRunnable = null;
        if (keywordAudioRecord != null) {
            try { keywordAudioRecord.stop(); } catch (Exception ignored) {}
            keywordAudioRecord.release();
            keywordAudioRecord = null;
        }
        keywordUsingSpeech = false;
    }

    private void stopSoundMeterInternal() {
        soundMeterActive = false;
        if (soundMeterRunnable != null && soundMeterHandler != null) {
            soundMeterHandler.removeCallbacks(soundMeterRunnable);
        }
        if (soundMeterThread != null) {
            soundMeterThread.quitSafely();
            soundMeterThread = null;
        }
        soundMeterHandler = null;
        soundMeterRunnable = null;
        if (soundMeter != null) {
            try { soundMeter.stop(); } catch (Exception ignored) {}
            soundMeter.release();
            soundMeter = null;
        }
    }

    // ==========================================
    // CHECK PERMISSION STATUS
    // ==========================================
    @PluginMethod
    public void checkPermission(PluginCall call) {
        String name = call.getString("name", "");
        if (name.isEmpty()) { call.reject("Permission name required"); return; }
        try {
            int result = getContext().checkCallingOrSelfPermission(name);
            JSObject ret = new JSObject();
            ret.put("granted", result == PackageManager.PERMISSION_GRANTED);
            ret.put("name", name);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to check permission: " + e.getMessage());
        }
    }

    // ==========================================
    // GET INSTALLED APPS (non-system)
    // ==========================================
    @PluginMethod
    public void getInstalledApps(PluginCall call) {
        try {
            PackageManager pm = getContext().getPackageManager();
            List<android.content.pm.PackageInfo> packages = pm.getInstalledPackages(0);
            java.util.ArrayList<JSObject> appsList = new java.util.ArrayList<>();
            for (android.content.pm.PackageInfo pkg : packages) {
                // Skip system apps
                if ((pkg.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                JSObject app = new JSObject();
                app.put("name", pm.getApplicationLabel(pkg.applicationInfo).toString());
                app.put("package", pkg.packageName);
                app.put("version", pkg.versionName != null ? pkg.versionName : "");
                appsList.add(app);
            }
            JSObject ret = new JSObject();
            ret.put("apps", appsList.toArray());
            ret.put("count", appsList.size());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to list apps: " + e.getMessage());
        }
    }

    // ==========================================
    // GET APP USAGE STATISTICS (requires PACKAGE_USAGE_STATS permission)
    // ==========================================
    @PluginMethod
    public void getAppUsageStats(PluginCall call) {
        try {
            Context ctx = getContext();
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            PackageManager pm = ctx.getPackageManager();
            long now = System.currentTimeMillis();

            // today (since midnight)
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            // this year
            cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY);
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            long yearStart = cal.getTimeInMillis();

            // all time (use first Jan 2020 as lower bound)
            java.util.Calendar calAll = java.util.Calendar.getInstance();
            calAll.set(2020, java.util.Calendar.JANUARY, 1, 0, 0, 0);
            long allStart = calAll.getTimeInMillis();

            // Check if usage access is granted
            boolean hasPermission = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.app.AppOpsManager appOps = (android.app.AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
                int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), ctx.getPackageName());
                hasPermission = (mode == android.app.AppOpsManager.MODE_ALLOWED);
            }

            if (!hasPermission) {
                JSObject ret = new JSObject();
                ret.put("hasPermission", false);
                ret.put("error", "PACKAGE_USAGE_STATS permission not granted. Enable in Settings > Apps > Special Access > Usage Access.");
                call.resolve(ret);
                return;
            }

            // Query usage stats for each interval
            List<UsageStats> todayStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, now);
            List<UsageStats> yearStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, yearStart, now);
            List<UsageStats> allStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, allStart, now);

            // Build index by package name for all intervals
            java.util.Map<String, Long> todayMap = new java.util.HashMap<>();
            java.util.Map<String, Long> yearMap = new java.util.HashMap<>();
            java.util.Map<String, Long> allMap = new java.util.HashMap<>();
            java.util.Set<String> allPackages = new java.util.HashSet<>();

            for (UsageStats us : todayStats) {
                String pkg = us.getPackageName();
                long time = us.getTotalTimeInForeground();
                if (time > 0) {
                    todayMap.put(pkg, time);
                    allPackages.add(pkg);
                }
            }
            for (UsageStats us : yearStats) {
                String pkg = us.getPackageName();
                long time = us.getTotalTimeInForeground();
                if (time > 0) {
                    yearMap.put(pkg, time);
                    allPackages.add(pkg);
                }
            }
            for (UsageStats us : allStats) {
                String pkg = us.getPackageName();
                long time = us.getTotalTimeInForeground();
                if (time > 0) {
                    allMap.put(pkg, time);
                    allPackages.add(pkg);
                }
            }

            // Build response sorted by total time descending
            java.util.List<JSObject> appsList = new java.util.ArrayList<>();
            for (String pkg : allPackages) {
                long t = allMap.containsKey(pkg) ? allMap.get(pkg) : 0;
                long y = yearMap.containsKey(pkg) ? yearMap.get(pkg) : 0;
                long d = todayMap.containsKey(pkg) ? todayMap.get(pkg) : 0;
                if (t < 60000) continue; // Skip apps with less than 1 min total

                String appName = pkg;
                try {
                    appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                } catch (Exception ignored) {}

                JSObject app = new JSObject();
                app.put("name", appName);
                app.put("package", pkg);
                app.put("todayMs", d);
                app.put("yearMs", y);
                app.put("totalMs", t);
                appsList.add(app);
            }

            // Sort by total descending
            java.util.Collections.sort(appsList, (a, b) -> Long.compare(
                b.optLong("totalMs", 0), a.optLong("totalMs", 0)
            ));

            // Calculate totals
            long totalTodayMs = 0, totalYearMs = 0, totalAllMs = 0;
            for (JSObject app : appsList) {
                totalTodayMs += app.optLong("todayMs", 0);
                totalYearMs += app.optLong("yearMs", 0);
                totalAllMs += app.optLong("totalMs", 0);
            }

            JSObject ret = new JSObject();
            ret.put("hasPermission", true);
            ret.put("apps", appsList.toArray());
            ret.put("count", appsList.size());
            ret.put("totalTodayMs", totalTodayMs);
            ret.put("totalYearMs", totalYearMs);
            ret.put("totalAllMs", totalAllMs);
            call.resolve(ret);
        } catch (Exception e) {
            JSObject ret = new JSObject();
            ret.put("hasPermission", true);
            ret.put("error", e.getMessage());
            ret.put("apps", new java.util.ArrayList<>());
            ret.put("count", 0);
            call.resolve(ret);
        }
    }

    // ==========================================
    // OPEN USAGE ACCESS SETTINGS
    // ==========================================
    @PluginMethod
    public void openUsageAccessSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to open settings: " + e.getMessage());
        }
    }

    // ==========================================
    // LAUNCH APP BY PACKAGE NAME
    // ==========================================
    @PluginMethod
    public void launchApp(PluginCall call) {
        String packageName = call.getString("package", "");
        if (packageName.isEmpty()) { call.reject("Package name required"); return; }
        try {
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                getContext().startActivity(intent);
                call.resolve();
            } else {
                call.reject("App not found or no launch intent: " + packageName);
            }
        } catch (Exception e) {
            call.reject("Failed to launch app: " + e.getMessage());
        }
    }

    // ==========================================
    // APK INTEGRITY CHECK (signature verification)
    // ==========================================
    @PluginMethod
    public void checkApkIntegrity(PluginCall call) {
        try {
            Context ctx = getContext();
            PackageManager pm = ctx.getPackageManager();
            String pkgName = ctx.getPackageName();
            int flags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags = PackageManager.GET_SIGNING_CERTIFICATES;
            }

            JSObject ret = new JSObject();
            ret.put("packageName", pkgName);
            ret.put("valid", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // API 28+ - use signing certificates
                android.content.pm.SigningInfo si = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                if (si == null) {
                    ret.put("valid", false);
                    ret.put("reason", "No signing info");
                    call.resolve(ret);
                    return;
                }
                android.content.pm.Signature[] sigs = si.getApkContentsSigners();
                if (sigs == null || sigs.length == 0) {
                    sigs = si.getSigningCertificateHistory();
                }
                if (sigs != null && sigs.length > 0) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(sigs[0].toByteArray());
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest) hex.append(String.format("%02x", b));
                    ret.put("signatureHash", hex.toString());
                    ret.put("signerCount", sigs.length);
                } else {
                    ret.put("valid", false);
                    ret.put("reason", "No signers found");
                }
            } else {
                // Legacy API - GET_SIGNATURES
                Signature[] sigs = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures;
                if (sigs != null && sigs.length > 0) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(sigs[0].toByteArray());
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest) hex.append(String.format("%02x", b));
                    ret.put("signatureHash", hex.toString());
                    ret.put("signerCount", sigs.length);
                } else {
                    ret.put("valid", false);
                    ret.put("reason", "No signatures found");
                }
            }

            ret.put("installer", pm.getInstallerPackageName(pkgName));
            ret.put("firstInstallTime", pm.getPackageInfo(pkgName, 0).firstInstallTime);
            ret.put("lastUpdateTime", pm.getPackageInfo(pkgName, 0).lastUpdateTime);
            call.resolve(ret);
        } catch (Exception e) {
            JSObject ret = new JSObject();
            ret.put("valid", false);
            ret.put("reason", e.getMessage());
            call.resolve(ret);
        }
    }

    // ==========================================
    // ANTI-DEBUGGING CHECKS
    // ==========================================
    @PluginMethod
    public void antiDebuggingCheck(PluginCall call) {
        JSObject ret = new JSObject();

        // 1. Debugger connected?
        boolean debuggerConnected = Debug.isDebuggerConnected();
        ret.put("debuggerConnected", debuggerConnected);

        // 2. App debuggable?
        boolean debuggable = (getContext().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        ret.put("appDebuggable", debuggable);

        // 3. Emulator detection
        boolean isEmulator = false;
        try {
            String brand = android.os.Build.BRAND.toLowerCase();
            String device = android.os.Build.DEVICE.toLowerCase();
            String model = android.os.Build.MODEL.toLowerCase();
            String product = android.os.Build.PRODUCT.toLowerCase();
            String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
            String fingerprint = android.os.Build.FINGERPRINT.toLowerCase();

            isEmulator = brand.contains("google") && device.contains("generic")
                || model.contains("emulator") || model.contains("android sdk")
                || product.contains("sdk") || product.contains("emulator")
                || fingerprint.contains("generic")
                || manufacturer.contains("genymotion")
                || (brand.contains("android") && device.contains("generic"));
        } catch (Exception ignored) {}

        ret.put("isEmulator", isEmulator);

        // 4. WaitForDebugger (if debuggable, check if someone is waiting to attach)
        boolean waitForDebugger = false;
        if (debuggable) {
            try {
                waitForDebugger = Debug.waitingForDebugger();
            } catch (Exception ignored) {}
        }
        ret.put("waitForDebugger", waitForDebugger);

        // Overall threat level
        int score = 0;
        if (debuggerConnected) score += 50;
        if (debuggable) score += 20;
        if (isEmulator) score += 30;
        if (waitForDebugger) score += 10;
        ret.put("threatScore", score);
        if (score >= 50) ret.put("threatLevel", "CRITICAL");
        else if (score >= 20) ret.put("threatLevel", "HIGH");
        else ret.put("threatLevel", "LOW");

        call.resolve(ret);
    }

    private void deleteRecursive(File file) {
        if (file == null) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
