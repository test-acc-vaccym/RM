package mode.retail.polaroid.mx.retailmode;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mode.retail.polaroid.mx.retailmode.receiver.RMDeviceAdminReceiver;
import mode.retail.polaroid.mx.retailmode.receiver.ScreenReceiver;
import mode.retail.polaroid.mx.retailmode.services.CloseAppService;
import mode.retail.polaroid.mx.retailmode.services.TimeService;

import static android.Manifest.*;

public class RetailModeActivity extends AppCompatActivity {

    private Intent myService;
    private Intent closeService;
    static Window window;
    static Context context;
    int contTouch = 0;

    private ScreenReceiver screenReceiver = new ScreenReceiver();
    private boolean isRegisterReceiver = false;

    private static final String TAG = "RetailModeActivity";

    private String PATH_VIDEOS_DOWNLOAD = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));//"/storage/emulated/0/Download/";

    boolean KEY_CLOSE_APP = false;

    private VideoView videoView;
    List<File> videos;
    int contadorVideos = 1;
    public static boolean isNewInstanceWindow = true;

    //
    private ComponentName mAdminComponentName;
    private DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this.getApplicationContext();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ActivityCompat.requestPermissions(RetailModeActivity.this,
                new String[]{Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.ACCOUNT_MANAGER,
                        Manifest.permission.BIND_DEVICE_ADMIN,
                        Manifest.permission.DISABLE_KEYGUARD,
                        Manifest.permission.WRITE_SETTINGS,
                        Manifest.permission.SYSTEM_ALERT_WINDOW,
                        Manifest.permission.CHANGE_CONFIGURATION,
                        Manifest.permission.WAKE_LOCK
                }, 1);

        // Check if Android M or higher
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (!Settings.canDrawOverlays(this)) {
                Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
//                myIntent.setData(Uri.parse("package: " + getPackageName()));
                startActivity(myIntent);
            }
        }

        window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        // Unlock the screen
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE,
                "RetailMode");
        wl.acquire();

        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock kl = km.newKeyguardLock("RetailMode");
        kl.disableKeyguard();

        contTouch = 0;
        KEY_CLOSE_APP = false;


        // Don't enable the profile again if this activity is being re-initialized.
        if (null == savedInstanceState) {
            mAdminComponentName = getComponentName();
            mDevicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

            if (!mDevicePolicyManager.isAdminActive(mAdminComponentName)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminComponentName);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Retail Mode requiere ser administrador");
                startActivityForResult(intent, 1);
            } else {
                System.err.println("SÍ ES ADMIN");
            }

        }

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(RetailModeActivity.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
        lock.disableKeyguard();

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_retail_mode);


        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        videos = readVideosFromDirectory();
        videoView = (VideoView) findViewById(R.id.retailModeView);
        videoView.setVideoPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + videos.get(0).getName());
        videoView.requestFocus();
        videoView.start();

    /* Video bucle */
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (videos.size() > contadorVideos) {
                    videoView.setVideoPath(PATH_VIDEOS_DOWNLOAD + File.separator + videos.get(contadorVideos).getName());
                    // Incrementa posicion de videos
                    contadorVideos++;
                } else {
                    videoView.setVideoPath(PATH_VIDEOS_DOWNLOAD + File.separator + videos.get(0).getName());
                    contadorVideos = 1;
                }
                videoView.requestFocus();
                videoView.start();
            }
        });

        ComponentName mDeviceAdminSample;
        mDeviceAdminSample = new ComponentName(this, RMDeviceAdminReceiver.class);

        if (Settings.canDrawOverlays(this)) {
            String[] requiredPermissions = new String[]{
                    permission.WRITE_SECURE_SETTINGS
            /* ETC.. */
            };

            Intent intent;
            if (Build.VERSION.SDK_INT > 22 && !hasPermissions(requiredPermissions)) {
                intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Retail Mode requiere ser administrador del dispositivo.");
                startActivityForResult(intent, 1);
            } else {
                Toast.makeText(context, "Contiene permisos", Toast.LENGTH_LONG).show();
            }
        }

    }

    public boolean hasPermissions(@NonNull String... permissions) {
        for (String permission : permissions)
            if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(permission))
                return false;
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first
        window = this.getWindow();

        //Stop TimerService
        if (null != myService) {
            stopService(myService);
        } else {
            myService = new Intent(getApplicationContext(), TimeService.class);
        }

        closeService = new Intent(RetailModeActivity.this, CloseAppService.class);

        contTouch = 0;
        KEY_CLOSE_APP = false;

        // Receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);
        isRegisterReceiver = true;


        unlockScreen();
        System.out.println(":::RetailModeActivity->onResume::: " + hasWindowFocus());

        videoView.start();
        startService(closeService);
    }

    public void onUserInteraction() {
        super.onUserInteraction();
    }

    @Override
    public void onPause() {
        super.onPause();
        startService(myService);

        System.out.println(":::RetailModeActivity->onPause::: " + hasWindowFocus());

        if (contTouch > 1 && KEY_CLOSE_APP) {
            stopService(closeService);
            stopService(myService);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        System.out.println(":::RetailModeActivity->onStop::: " + hasWindowFocus());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isRegisterReceiver) {
            unregisterReceiver(screenReceiver);
        }

        System.out.println(":::RetailModeActivity->onDestroy:::");
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = event.getActionIndex();
        if (index == 1) {
            System.out.println("mutli1");
        } else {
            System.out.println("single");

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                contTouch++;
            }
        }

        //startService(new Intent(RetailModeActivity.this, CloseAppService.class));

        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            KEY_CLOSE_APP = true;
        }

        if (keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_HOME) {
            isNewInstanceWindow = false;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Obtiene listado de videos en el directorio
     *
     * @return
     */
    private List<File> readVideosFromDirectory() {
        List<File> videos = new ArrayList<File>();
        File f = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        File files[] = f.listFiles();
        for (File file : files) {
            if (getExtension(file).equalsIgnoreCase("MP4")) {
                System.out.println(file.getName());

                videos.add(file);
            }
        }
        return videos;
    }

    /*
     * Get the extension of a file.
     */
    public static String getExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }

    public static void unlockScreen() {
        try {

            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);

            // Unlock the screen
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "RetailMode");
            wl.acquire();

            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            KeyguardManager.KeyguardLock kl = km.newKeyguardLock("RetailMode");
            kl.disableKeyguard();

        } catch (Exception e) {
            Log.d(TAG, e.getStackTrace().toString());
        }
    }

    public static void clearScreen(Context context) {
        try {
            unlockScreen();

            Intent myIntent = new Intent(context, RetailModeActivity.class);
            myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startService(myIntent);
        } catch (Exception e) {
            Log.d(TAG, e.getStackTrace().toString());
        }
    }
}