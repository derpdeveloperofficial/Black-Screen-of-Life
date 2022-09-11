package com.derpdeveloper.blackscreenoflife;

/**
 * Created by Micael on 14/07/2015.
 */
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.acra.ACRA;

import java.io.IOException;


public class ScreenOffService extends Service implements SensorEventListener {

    private static final int DELAY_BEFORE_SCREEN_OFF = 2500; //delay desde que e' detectada proximidade
    private static final int DELAY_BEFORE_OVERLAY_WARNING = 500; //delay desde que e' detectada proximidade
    private static final int DELAY_BEFORE_RELEASING_LOCK = 500; //delay desde que e' detectada longitude
    private static final int DELAY_FOR_CHECKING_MUSIC_PLAYING = 10000; //delay desde que e' detectada proximidade
    private static final int RELEASE_SCREEN_IF_NOT_PLAYING_MUSIC_AFTER = 30000;
    private static final float DISTANCE_CONSIDERED_CLOSE = 5.0f; //Typically, the far value is a value > 5 cm

    private static final int NOTIFICATION_ID_AD = 0;
    private static final int NOTIFICATION_ID_RUNNING = 1;
    static final int NOTIFICATION_ID_DISABLED = 2;

    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private AudioManager mAudioManager;
    private float mClosestDistanceEverRead = Float.MAX_VALUE;
    private float mMaximumSensorRange;

    private int sensorChangedCurrentRequest = 0;
    private long lastTimeBSoLWasActivated = -1;
    private boolean currentLockingUserNeedToWatchAd = false;

    private View mOverlayView;
    private Typeface mLeHandFont;
    private PowerManager.WakeLock mWakeLock;
    private Thread mCheckForMusicPlayingThread;
    private View mOverlayBlackCompabilityView;

    private final Object mOverlayViewLock = new Object();
    private final Object mCurrentRequestLock = new Object();
    private final Object mOverlayBlackCompabilityViewLock = new Object();

    private BroadcastReceiver mPowerKeyReceiver = null;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mSensorManager.registerListener(this, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);

        mLeHandFont = Typeface.createFromAsset(getAssets(), "LeHand.ttf");

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if(Build.VERSION.SDK_INT >= 21) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, getClass().getName());
        }
        else {

            /*try {
                Method method = powerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
                int supportedWakeLockFlags = (Integer) method.invoke(powerManager);
                Field field = PowerManager.class.getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK");
                int proximityScreenOffWakeLock = (Integer) field.get(null);
                if ((supportedWakeLockFlags & proximityScreenOffWakeLock) != 0x0) {
                    mWakeLock = powerManager.newWakeLock(proximityScreenOffWakeLock, getClass().getName());
                }
            } catch (Exception e){
                ACRA.getErrorReporter().handleException(e);
            }*/

            int field = 0x00000020;
            try {
                // Yeah, this is hidden field.
                field = PowerManager.class.getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
            } catch (Throwable ex) {
                ACRA.getErrorReporter().handleException(ex);
            }
            mWakeLock = powerManager.newWakeLock(field, getClass().getName());
        }

        mAudioManager = (AudioManager)ScreenOffService.this.getSystemService(Context.AUDIO_SERVICE);

        Utility.cancelDisabledNotification(getApplicationContext());

        registerBroadcastReceiver();
        showRunningNotification();

        //TODO a distancia minima devia ser gravado nos settings e lido no arranque
        mMaximumSensorRange = mProximitySensor.getMaximumRange();
        Log.v("BSoL", "OverlayService running... Sensor maximum range = " + mMaximumSensorRange);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v("BSoL", "OverlayService DESTROYED!");

        showDisabledNotification();

        incrementSensorChangedRequest();
        mSensorManager.unregisterListener(this);
        releaseWakeLockAndKillMusicThread();
        removeOverlayWindow();
        unregisterBroadcastReceiver();

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(NOTIFICATION_ID_RUNNING);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //return super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    private void removeOverlayWindow() {
        synchronized (mOverlayViewLock) {
            if (mOverlayView != null) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(mOverlayView);
                mOverlayView = null;
            }
        }
    }

    private void removeBlackCompatibilityOverlay() {
        synchronized (mOverlayBlackCompabilityViewLock) {
            if (mOverlayBlackCompabilityView != null) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(mOverlayBlackCompabilityView);
                mOverlayBlackCompabilityView = null;
            }
        }
    }

    private void releaseWakeLockAndKillMusicThread() {
        if(!Utility.getCompatibilityMode(this)) {
            //modo desligar o ecrã
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        else {
            //modo compatibilidade
            removeBlackCompatibilityOverlay();
        }
        if (lastTimeBSoLWasActivated > 0)
            Utility.addActiveBSoLTime(this, System.currentTimeMillis() - lastTimeBSoLWasActivated);
        lastTimeBSoLWasActivated = -1;
        //playLockSoundAndVibrate();
        userNeedToWatchAd(true); // update para mostrar o warning se necessário
        Log.v("BSoL", "BSoL is now deactivated :(");

        if(mCheckForMusicPlayingThread != null) {
            mCheckForMusicPlayingThread.interrupt();
            mCheckForMusicPlayingThread = null;
        }
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_PROXIMITY) {
            float proximity = sensorEvent.values[0];
            Log.v("BSoL", "Proximity changed: " + proximity);

            if(proximity < mClosestDistanceEverRead)
                mClosestDistanceEverRead = proximity;

            incrementSensorChangedRequest();
            final Handler handler = new Handler();

            if (proximity < DISTANCE_CONSIDERED_CLOSE &&
                    mClosestDistanceEverRead == proximity &&
                    mClosestDistanceEverRead < mMaximumSensorRange) {

                currentLockingUserNeedToWatchAd = userNeedToWatchAd(false);

                int userDelay = Utility.getDelayScreenOffInMillis(this);

                //verificamos se o utilizador precisa de ver o anuncio para mostrar o overlay
                if(currentLockingUserNeedToWatchAd) {
                    handler.postDelayed(new DisplayOverlayWarningTask(sensorChangedCurrentRequest), DELAY_BEFORE_OVERLAY_WARNING);
                }
                else {
                    if(Utility.getShowOverlayTimer(this)) {
                        handler.postDelayed(new DisplayOverlayWarningTask(sensorChangedCurrentRequest), DELAY_BEFORE_OVERLAY_WARNING + userDelay);
                        handler.postDelayed(new TurnScreenOffTask(sensorChangedCurrentRequest), DELAY_BEFORE_SCREEN_OFF + userDelay);
                    }
                    else {
                        handler.postDelayed(new TurnScreenOffTask(sensorChangedCurrentRequest), DELAY_BEFORE_OVERLAY_WARNING + userDelay);
                    }
                }
            }
            else {
                removeOverlayWindow(); //este faz-se imediatamente para o utilizador nao ver que demora muito
                handler.postDelayed(new ReleaseLockTask(sensorChangedCurrentRequest), DELAY_BEFORE_RELEASING_LOCK);
            }
        }
    }

    private void incrementSensorChangedRequest() {
        synchronized (mCurrentRequestLock) {
            sensorChangedCurrentRequest++;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }


    private void registerBroadcastReceiver() {
        Log.v("BSoL", "Registering BroadcastReceiver");

        if(mPowerKeyReceiver == null) { // se nao for null e' porque ja estamos registados
            final IntentFilter theFilter = new IntentFilter();
            theFilter.addAction(Intent.ACTION_SCREEN_ON);
            theFilter.addAction(Intent.ACTION_SCREEN_OFF);
            theFilter.addAction(BSoLApplication.BROADCAST_SHUTDOWN_SERVICE);
            theFilter.addAction(BSoLApplication.BROADCAST_UPDATE_NOTIFICATION);

            mPowerKeyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String strAction = intent.getAction();

                    if (strAction.equals(Intent.ACTION_SCREEN_OFF)) {
                        Log.v("BSoL", "ACTION_SCREEN_OFF");
                        incrementSensorChangedRequest();
                        mSensorManager.unregisterListener(ScreenOffService.this);
                        releaseWakeLockAndKillMusicThread();
                        removeOverlayWindow();
                    } else if (strAction.equals(Intent.ACTION_SCREEN_ON)) {
                        incrementSensorChangedRequest();
                        Log.v("BSoL", "ACTION_SCREEN_ON");
                        mSensorManager.registerListener(ScreenOffService.this, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
                    } else if (strAction.equals(BSoLApplication.BROADCAST_SHUTDOWN_SERVICE)) {
                        Log.v("BSOL", "BROADCAST INTENT received. Proceding on destroying this shit.");
                        ScreenOffService.this.stopSelf();
                    }else if (strAction.equals(BSoLApplication.BROADCAST_UPDATE_NOTIFICATION)) {
                        Log.v("BSOL", "BROADCAST INTENT received. Updating notification!");
                        updateRunningNotification();
                    }
                }
            };

            getApplicationContext().registerReceiver(mPowerKeyReceiver, theFilter);
        }
    }

    private void unregisterBroadcastReceiver() {
        Log.v("BSoL", "Unregistering BroadcastReceiver");
        getApplicationContext().unregisterReceiver(mPowerKeyReceiver);
        mPowerKeyReceiver = null;
    }

    private boolean userNeedToWatchAd(boolean showWarning) {
        if(Utility.getLatestPremiumMode(this))
            return false;

        long lastTimeAdWasSeen = Utility.getLastTimeAdWasSeen(ScreenOffService.this);
        long diff = System.currentTimeMillis() - lastTimeAdWasSeen;

        if(diff < 0) //o tempo de visualização é superior ao actual
            Utility.updateLastTimeSeenAdWithCurrentTime(this);

        long activeTimeSinceAd = Utility.getBSoLActiveTimeSinceAd(this);

        if(diff >= MainActivity.MINIMUM_TIME_INTERVAL_BETWEEN_ADS &&
                activeTimeSinceAd >= MainActivity.MINIMUM_ACTIVE_TIME_BETWEEN_ADS ||
                activeTimeSinceAd >= MainActivity.MAXIMUM_BSOL_ENABLED_BEFORE_AD ) {

            boolean userAlreadyWarnedToWatchAd = Utility.getUserAlreadyWarnedToWatchAd(this);

            if(userAlreadyWarnedToWatchAd) {
                return true;
            }
            else {
                if(showWarning) {
                    showAdNotificationWarning();
                    Utility.setUserAlreadyWarnedToWatchAd(this, true);
                }
            }
        }

        return false;
    }

    private void showAdNotificationError() {
        Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.error);
        showAdNotification(soundUri);
    }

    private void showAdNotificationWarning() {
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        showAdNotification(soundUri);
        //showAdNotification(null);
    }

    private void showAdNotification(Uri soundUri){
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContentTitle(getString(R.string.notificationAdTitle))
                        .setContentText(getString(R.string.notificationAdText))
                        .setTicker(getString(R.string.notificationAdText))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_MAX);

        mBuilder.setSmallIcon(R.drawable.notification_ad_21);

        if(soundUri != null)
            mBuilder.setSound(soundUri);

        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.putExtra(getString(R.string.intentWatchAd), true);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        NOTIFICATION_ID_AD,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(NOTIFICATION_ID_AD, mBuilder.build());
    }


    private void showRunningNotification(){

        if(!Utility.getShowRunningNotification(this))
            return;

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setContentTitle(getString(R.string.notificationRunningTitle))
                        .setContentText(getString(R.string.notificationRunningText))
                        .setTicker(getString(R.string.notificationRunningTicker))
                        //.setAutoCancel(true)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setSmallIcon(R.drawable.notification_21);

        Intent resultIntent = new Intent();
        resultIntent.setAction(BSoLApplication.BROADCAST_SHUTDOWN_SERVICE);

        PendingIntent resultPendingIntent = PendingIntent.getBroadcast(this, NOTIFICATION_ID_RUNNING, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(resultPendingIntent);

        /*NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(NOTIFICATION_ID_RUNNING, mBuilder.build());*/

        // para o sistema nunca matar o serviço
        startForeground(NOTIFICATION_ID_RUNNING, mBuilder.build());
    }

    private void showDisabledNotification(){

        Utility.showDisabledNotification(getApplicationContext());

        if(!Utility.getShowStoppedNotification(this)) {
            Utility.cancelDisabledNotification(getApplicationContext());
        }
    }

    private void updateRunningNotification() {
        if(Utility.getShowRunningNotification(this)) {
            showRunningNotification();
        }
        else {
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancel(NOTIFICATION_ID_RUNNING);
        }
    }

    private void playLockSoundAndVibrate() {

        //tocar som
        if(Utility.getPlayLockSound(this)) {
            final AudioManager.OnAudioFocusChangeListener afChangeListener =
                    new AudioManager.OnAudioFocusChangeListener() {
                        public void onAudioFocusChange(int focusChange) {
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                // Pause playback
                            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                                // Resume playback
                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                                mAudioManager.abandonAudioFocus(this);
                                // Stop playback
                            }
                        }
                    };

            //para previnir a excepção do som
            int result;
            try {
                result = mAudioManager.requestAudioFocus(afChangeListener,
                        AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } catch (Exception e) {
                return;
            }

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

                MediaPlayer lockSoundPlayer = new MediaPlayer();
                lockSoundPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
                try {
                    Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.light_off);
                    lockSoundPlayer.setDataSource(getApplicationContext(), soundUri);
                    lockSoundPlayer.prepare(); //might take long! (for buffering, etc)
                } catch (IOException e) {
                    e.printStackTrace();
                }

                lockSoundPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mAudioManager.abandonAudioFocus(afChangeListener);
                        mp.reset();
                        mp.release();
                    }
                });

                lockSoundPlayer.start();
                Log.v("BSoL", "LOCK SOUND PLAYED!");
            }
        }

        //vibrar
        if(Utility.getVibrateOnLock(this)) {
            Vibrator vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(150);
        }
    }

    /*private void initLockSoundPlayer() {
        if(mLockSoundPlayer != null)
            releaseLockSoundPlayer();

        mLockSoundPlayer = new MediaPlayer();
        mLockSoundPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
        try {
            // diz que isto da' buraco no LOLIPOP: CONFIRMAR!
            Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.light_off);
            mLockSoundPlayer.setDataSource(getApplicationContext(), soundUri);
            mLockSoundPlayer.prepareAsync(); //  might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseLockSoundPlayer() {
        mLockSoundPlayer.release();
        mLockSoundPlayer = null;
    }*/


    private class TurnScreenOffTask implements Runnable {

        private int mRequest;

        public TurnScreenOffTask(int request) {
            super();
            mRequest = request;
        }

        @Override
        public void run() {
            boolean execute;
            synchronized (mCurrentRequestLock) {
                execute = mRequest == sensorChangedCurrentRequest;
            }

            if(execute && !currentLockingUserNeedToWatchAd) {

                lastTimeBSoLWasActivated = System.currentTimeMillis();
                if(!Utility.getCompatibilityMode(ScreenOffService.this)) {
                    //desligar ecrã
                    if(!mWakeLock.isHeld())
                        mWakeLock.acquire();
                }
                else {
                    removeBlackCompatibilityOverlay();

                    //modo compatibilidade
                    synchronized (mOverlayBlackCompabilityViewLock) {
                        mOverlayBlackCompabilityView = new LinearLayout(ScreenOffService.this);
                        mOverlayBlackCompabilityView.setBackgroundColor(0xFF000000); // The opaque BLACK color
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DIM_BEHIND |WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                PixelFormat.TRANSLUCENT);
                        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
                        //params.dimAmount = 1;
                        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                        wm.addView(mOverlayBlackCompabilityView, params);

                        //se ele nao tiver mostrado, fazermos este horrible hack para o utilizador
                        //nao furar a janela TYPE_SYSTEM_OVERLAY (a outra é TYPE_SYSTEM_ALERT e já apanha toques)
                        if(!Utility.getShowOverlayTimer(ScreenOffService.this))
                            new DisplayOverlayWarningTask(mRequest).run();
                    }
                }
                Log.v("BSoL", "BSoL is ACTIVE NOW!");

                playLockSoundAndVibrate();

                if(mCheckForMusicPlayingThread != null)
                    mCheckForMusicPlayingThread.interrupt();
                mCheckForMusicPlayingThread = new CheckForMusicPlayingThread();
                mCheckForMusicPlayingThread.start();
            }
        }
    }

    private class DisplayOverlayWarningTask implements Runnable {

        private int mRequest;

        public DisplayOverlayWarningTask(int request) {
            super();
            mRequest = request;
        }

        @Override
        public void run() {
            boolean execute;
            synchronized (mCurrentRequestLock) {
                execute = mRequest == sensorChangedCurrentRequest;
            }

            if(execute) {
                removeOverlayWindow();

                LayoutInflater inflater =
                        (LayoutInflater) ScreenOffService.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                synchronized (mOverlayViewLock) {
                    if(currentLockingUserNeedToWatchAd) {
                        mOverlayView = inflater.inflate(R.layout.overlay_need_to_watch_ad, null);
                        showAdNotificationError();
                    } else {
                        mOverlayView = inflater.inflate(R.layout.overlay_warning, null);
                    }

                    TextView textView = (TextView) mOverlayView.findViewById(R.id.textView);
                    textView.setTypeface(mLeHandFont);

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, //TYPE_SYSTEM_OVERLAY,
                            0, // | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            //WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |WindowManager.LayoutParams.FLAG_FULLSCREEN,
                            PixelFormat.TRANSLUCENT);
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    wm.addView(mOverlayView, params);
                }

                new Handler().postDelayed(new UpdateOverlayTimerTask(1),
                        (DELAY_BEFORE_SCREEN_OFF - DELAY_BEFORE_OVERLAY_WARNING) / 4);
            }
        }
    }

    private class UpdateOverlayTimerTask implements Runnable {

        int mStep;

        public UpdateOverlayTimerTask(int step) {
            super();
            mStep = step;
        }

        @Override
        public void run() {

            synchronized (mOverlayViewLock) {

                if(mOverlayView == null)
                    return;

                ImageView imageViewTimer = (ImageView) mOverlayView.findViewById(R.id.imageViewTimer);
                if(imageViewTimer == null)
                    return;

                switch (mStep) {
                    case 1:
                        imageViewTimer.setBackgroundResource(R.drawable.timer1);
                        new Handler().postDelayed(new UpdateOverlayTimerTask(2),
                                (DELAY_BEFORE_SCREEN_OFF - DELAY_BEFORE_OVERLAY_WARNING) / 4);
                        break;

                    case 2:
                        imageViewTimer.setBackgroundResource(R.drawable.timer2);
                        new Handler().postDelayed(new UpdateOverlayTimerTask(3),
                                (DELAY_BEFORE_SCREEN_OFF - DELAY_BEFORE_OVERLAY_WARNING) / 4);
                        break;

                    case 3:
                        imageViewTimer.setBackgroundResource(R.drawable.timer3);
                        break;
                }
            }
        }
    }

    private class CheckForMusicPlayingThread extends Thread {

        //TODO [NFCV] isto podia ser melhorado
        boolean musicWasPlaying = false;
        long firstTimeMusicStoppedPlaying = -1;

        @Override
        public void run() {
            try {
                while(mWakeLock.isHeld()) { //nao e' preciso verificar pelo modo de compatibilidade porque se tivermos nesse o ecra desliga-se por ele
                    Thread.sleep(DELAY_FOR_CHECKING_MUSIC_PLAYING);

                    if (mAudioManager.isMusicActive()) {
                        musicWasPlaying = true;
                        firstTimeMusicStoppedPlaying = -1;
                        Log.v("BSoL", "Music is playing! Sleeping now...");
                    } else {
                        if(firstTimeMusicStoppedPlaying < 0)
                            firstTimeMusicStoppedPlaying = System.currentTimeMillis();

                        if(musicWasPlaying &&
                                System.currentTimeMillis() - firstTimeMusicStoppedPlaying >=
                                        RELEASE_SCREEN_IF_NOT_PLAYING_MUSIC_AFTER) {

                            Log.v("BSoL", "Music is NOT playing after treshold! Releasing lock and deactivating now...");
                            removeOverlayWindow();
                            releaseWakeLockAndKillMusicThread();
                        }
                    }
                }

                Log.v("BSoL", "CheckForMusicPlayingTask quitting!");
            } catch (InterruptedException e) {
                Log.v("BSoL", "CheckForMusicPlayingTask Interrupted, quitting!");
            }
        }
    }

    private class ReleaseLockTask implements Runnable {

        private int mRequest;

        public ReleaseLockTask(int request) {
            super();
            mRequest = request;
        }

        @Override
        public void run() {
            boolean execute;
            synchronized (mCurrentRequestLock) {
                execute = mRequest == sensorChangedCurrentRequest;
            }

            if (execute) {
                releaseWakeLockAndKillMusicThread();
            }
        }
    }
}