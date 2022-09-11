package com.derpdeveloper.blackscreenoflife;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Micael on 24/07/2015.
 */
class Utility {

    public static final String Base64RSAKeyPt3 = "h+OBZtRG2UH7j9ApjwF1Hk5PTIApDTJi+mFU7jiXWBskQBvtHx2PknYvWi5brEa4tTa5396CWS1nQbYu95C6MyekJrVZm0UbmPs2hvj1PO/pLQY1aLlRIEl05h";
    public static final String LIST_OF_ENABLED_APPS_DELIMITER = ";";
    private final static long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

    public static void updateLastTimeSeenAdWithCurrentTime(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(context.getString(R.string.prefLastSeenAdMillis), System.currentTimeMillis());
        editor.apply();
    }

    public static long getLastTimeAdWasSeen(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getLong(context.getString(R.string.prefLastSeenAdMillis), 0);
    }

    public static void resetActiveBSoLTime(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(context.getString(R.string.prefActiveTimeSinceLastAd), 0);
        editor.apply();
        Log.v("BSoLUtility", "resetActiveBSoLTime");
    }

    public static void addActiveBSoLTime(Context context, long time) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        long currentTime = sharedPreferences.getLong(context.getString(R.string.prefActiveTimeSinceLastAd), 0);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(context.getString(R.string.prefActiveTimeSinceLastAd), currentTime + time);
        editor.apply();
        Log.v("BSoLUtility", "addActiveBSoLTime: adding " + time + ", which totals " + (currentTime + time));
    }

    public static long getBSoLActiveTimeSinceAd(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        long currentTime = sharedPreferences.getLong(context.getString(R.string.prefActiveTimeSinceLastAd), 0);
        Log.v("BSoLUtility", "getBSoLActiveTimeSinceAd: " + currentTime);
        return currentTime;
    }

    public static boolean getUserAlreadyWarnedToWatchAd(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefUserAlreadyWarnedToWatchAd), false);
    }

    public static void setUserAlreadyWarnedToWatchAd(Context context, boolean value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(context.getString(R.string.prefUserAlreadyWarnedToWatchAd), value);
        editor.apply();
    }

    public static boolean getShowOverlayTimer(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefShowOverlayKey), true);
    }

    public static boolean getPlayLockSound(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefPlayLockSoundKey), false);
    }

    public static boolean getVibrateOnLock(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefLockVibrationKey), true);
    }

    public static boolean getShowRunningNotification(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefShowRunningNotificationKey), true);
    }

    public static boolean getShowStoppedNotification(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefShowStoppedNotificationKey), false);
    }

    public static int getDelayScreenOffInMillis(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean delay = sharedPreferences.getBoolean(context.getString(R.string.prefDelayScreenOffKey), false);
        return delay ? 1500 : 0;
    }

    public static boolean getCompatibilityMode(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefCompatibilityBlackOverlayKey), false);
    }

    public static String getFormattedTodayDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
        String formattedDate = df.format(c.getTime());
        return formattedDate;
    }

    public static int getDateDiffInDays(Date newDate, Date oldDate) {
        int diffInDays = (int) ((newDate.getTime() - oldDate.getTime())/ DAY_IN_MILLIS );
        return diffInDays;
    }

    public static int getDateDiffInDays(String formatedNewDate, String formatedOldDate) {
        Date newDate = getDateFromFormattedDayString(formatedNewDate);
        Date oldDate = getDateFromFormattedDayString(formatedOldDate);
        return getDateDiffInDays(newDate, oldDate);
    }

    public static Date getDateFromFormattedDayString(String dateStr){
        SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
        Date date;
        try {
            date = df.parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
            date = Calendar.getInstance().getTime();
        }
        return date;
    }

    public static boolean getLatestPremiumMode(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.randomChars), false);
    }

    public static boolean getStartOnBoot(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(context.getString(R.string.prefStartOnBootKey), false);
    }

    public static void setLatestPremiumMode(Context context, boolean premiumMode) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(context.getString(R.string.randomChars), premiumMode);
        editor.apply();
    }

    public static boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static void showDisabledNotification(Context context){

        /*if(!Utility.getShowRunningNotification(context))
            return;*/

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setContentTitle(context.getString(R.string.notificationStoppedTitle))
                        .setContentText(context.getString(R.string.notificationStoppedText))
                        .setTicker(context.getString(R.string.notificationStoppedTicker))
                                //.setAutoCancel(true)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setSmallIcon(R.drawable.notification_off_21);

        Intent resultIntent = new Intent(context, ScreenOffService.class);
        PendingIntent pendingIntent = PendingIntent.getService(context, ScreenOffService.NOTIFICATION_ID_DISABLED, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(pendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(ScreenOffService.NOTIFICATION_ID_DISABLED, mBuilder.build());
    }

    public static void cancelDisabledNotification(Context context) {
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(ScreenOffService.NOTIFICATION_ID_DISABLED);
    }
}
