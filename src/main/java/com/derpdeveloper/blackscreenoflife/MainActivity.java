package com.derpdeveloper.blackscreenoflife;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;

import com.derpdeveloper.blackscreenoflife.billingutil.IabHelper;
import com.derpdeveloper.blackscreenoflife.billingutil.IabResult;
import com.derpdeveloper.blackscreenoflife.billingutil.Inventory;
import com.derpdeveloper.blackscreenoflife.billingutil.Purchase;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;


public class MainActivity extends Activity {

    static final String Base64RSAKeyPt2 = "A1szRJ8Q23U5wlSIvY7IfGGbDhK35UYisv/hwOVkfexKYCAmt6QyP7fMuM8/CasqbVS5IRa6ZejIOwJtpuA/5S9b9xS2KeW6xS6FxzN4CO0c5ruFVSrRrztS";

    public static final long MINIMUM_TIME_INTERVAL_BETWEEN_ADS = 24 * 60 * 60 * 1000; // 20 horas
    public static final long MINIMUM_ACTIVE_TIME_BETWEEN_ADS = 30 * 60 * 1000; // 30 minutos
    public static final long MAXIMUM_BSOL_ENABLED_BEFORE_AD = 90 * 60 * 1000; // 90 minutos
    public static final int SHOW_RATE_APP_DIALOG_AFTER_N_DAYS = 5;
    public static final int SHOW_RATE_APP_DIALOG_DAYS_INTERVAL = 3;

    private ImageView mStatusImageView;
    private ImageView mStatusImageViewRainbow;
    private ImageView mSettingsImageView;
    private ImageView mInfoImageView;
    private Button mWatchAdButton;
    private View mMainLayout;
    private View mLoadingAdLayout;
    private Animation mAnimFadein;
    private Animation mAnimFadeout;

    private InterstitialAd mInterstitialAd;
    private IabHelper mHelper;
    private boolean mIsPremium = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager pm = this.getPackageManager();
        boolean hasProximitySensor = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY);
        SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if(!hasProximitySensor || mProximitySensor == null) {
            setContentView(R.layout.activity_main_no_sensor);
            return;
        }

        setContentView(R.layout.activity_main);

        mStatusImageView = (ImageView) findViewById(R.id.imageViewStatus);
        mStatusImageViewRainbow = (ImageView) findViewById(R.id.imageViewStatusRainbow);
        mSettingsImageView = (ImageView) findViewById(R.id.imageViewSettings);
        mInfoImageView = (ImageView) findViewById(R.id.imageViewInfo);
        mWatchAdButton = (Button) findViewById(R.id.buttonWatchAd);
        mMainLayout = findViewById(R.id.layoutMain);
        mLoadingAdLayout = findViewById(R.id.layoutLoadingAd);

        mAnimFadein = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
        mAnimFadeout = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_out);

        /*Typeface font = Typeface.createFromAsset(getAssets(), "LeHand.ttf");
        mWatchAdButton.setTypeface(font);*/

        mInterstitialAd = new InterstitialAd(this);
        if(BSoLApplication.iSReleaseVersion)
            mInterstitialAd.setAdUnitId(getString(R.string.adsMainInterstitialId));
        else mInterstitialAd.setAdUnitId(getString(R.string.adsTestId));
        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                resetWatchAdVariables();
                showMainLayout();
                //requestNewInterstitial();
            }

            @Override
            public void onAdLoaded() {
                showInterstitialAd();
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                switch (errorCode) {
                    case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                    case AdRequest.ERROR_CODE_INVALID_REQUEST:
                    case AdRequest.ERROR_CODE_NO_FILL:
                        showErrorLoadingAd(false);
                        resetWatchAdVariables();
                        break;
                    case AdRequest.ERROR_CODE_NETWORK_ERROR:
                        showErrorLoadingAd(true);
                        break;
                }
                showMainLayout();
            }
        });
        //requestNewInterstitial();

        mStatusImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int action = event.getAction();
                int evX = (int) event.getX();
                int evY = (int) event.getY();

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        Log.v("BSoL", "MotionEvent.ACTION_DOWN");
                        return true;
                    case MotionEvent.ACTION_UP:
                        Log.v("BSoL", "MotionEvent.ACTION_UP");
                        mStatusImageView.setDrawingCacheEnabled(true);
                        Bitmap imgbmp = Bitmap.createBitmap(mStatusImageView.getDrawingCache());
                        mStatusImageView.setDrawingCacheEnabled(false);

                        /*if (evX < 0 || evY < 0 ||
                                evX >= imgbmp.getWidth() || evY >= imgbmp.getHeight())
                            break;*/

                        if(evX > 0 && evX < imgbmp.getWidth() &&
                                evY > 0 && evY < imgbmp.getHeight()) {
                            int pxl = imgbmp.getPixel(evX, evY);

                            if (Color.alpha(pxl) > 0) {
                                toggleBSoLService();
                            }
                        }
                        break;
                }

                return false;
            }
        });

        mSettingsImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        mInfoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, InfoActivity.class);
                startActivity(intent);
            }
        });

        mWatchAdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInterstitialAd();
            }
        });

        mAnimFadein.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mStatusImageViewRainbow.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {}
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        mAnimFadeout.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mStatusImageViewRainbow.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        performFirstRunOperations();

        boolean watchAd = getIntent().getBooleanExtra(getString(R.string.intentWatchAd), false);
        if(watchAd)
            showInterstitialAd();

        boolean turnOff = getIntent().getBooleanExtra(getString(R.string.intentStopBSoL), false);
        if(turnOff)
            stopBSoLService();

        mIsPremium = Utility.getLatestPremiumMode(this);
        updateIfPremiumMode();
        showChangelogIfNecessary();
    }

    @Override
    protected void onStart() {
        super.onStart();

        updateStatusAndNotificationIcon();
        checkForPremium();

        /*if(Utility.getIsShakeEnabled(this) &&
                mIsPremium &&
                !Utility.isMyServiceRunning(this, ShakeService.class))
            startService(new Intent(this, ShakeService.class));*/
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mStatusImageViewRainbow != null)
            mStatusImageViewRainbow.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHelper != null)
            mHelper.dispose();
        mHelper = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("BSOL", "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d("BSoL", "onActivityResult handled by IABUtil.");
        }
    }

    private void checkForPremium() {
        if (mHelper != null)
            mHelper.dispose();

        String base64EncodedPublicKey = BSoLApplication.Base64RSAKeyPt1 + Base64RSAKeyPt2 + Utility.Base64RSAKeyPt3 + getString(R.string.inTheEnd);
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d("BSoL", "Problem setting up In-app Billing: " + result);
                    return;
                }

                Log.d("BSoL", "Received good response In-app Billing: " + result);

                if(mHelper.isSetupDone() && ! mHelper.isAsyncInProgress()) {
                    try {
                        mHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                                if (result.isFailure()) {
                                    Log.d("BSoL", "Problem contacting In-app Billing: " + result);
                                } else {
                                    Purchase premiumPurchase = inventory.getPurchase(BSoLApplication.SKU_PREMIUM);
                                    mIsPremium = (premiumPurchase != null && premiumPurchase.getDeveloperPayload().equals(BSoLApplication.IN_APP_BUY_DEVELOPER_PAYLOAD));
                                    Utility.setLatestPremiumMode(MainActivity.this, mIsPremium);
                                    updateIfPremiumMode();
                                    Log.d("BSoL", "User has premium: " + mIsPremium);
                                }
                            }
                        });
                    }
                    catch (Exception e) {
                        return;
                    }
                }
            }
        });
    }

    /*private void consumePremium() {
        if (mHelper != null)
            mHelper.dispose();

        String base64EncodedPublicKey = BSoLApplication.Base64RSAKeyPt1 + Base64RSAKeyPt2 + Utility.Base64RSAKeyPt3 + getString(R.string.inTheEnd);
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d("BSoL", "Problem setting up In-app Billing: " + result);
                    return;
                }

                Log.d("BSoL", "Received good response In-app Billing: " + result);

                mHelper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                    public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                        if (inventory.hasPurchase(BSoLApplication.SKU_PREMIUM)) {
                            mHelper.consumeAsync(inventory.getPurchase(BSoLApplication.SKU_PREMIUM), new IabHelper.OnConsumeFinishedListener() {
                                @Override
                                public void onConsumeFinished(Purchase purchase, IabResult result) {
                                    if (result.isSuccess()) {
                                        mIsPremium = false;
                                        Utility.setLatestPremiumMode(MainActivity.this, false);
                                        updateIfPremiumMode();
                                        Log.d("BSoL", "CONSUMED!");
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
    }*/

    private void updateIfPremiumMode() {
        if(mIsPremium) {
            //mWatchAdButton.setVisibility(View.GONE);
            mWatchAdButton.setText(getString(R.string.buttonWatchAdPremiumText));
            mWatchAdButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage(R.string.dialogThanksPremiumMessage)
                            //.setTitle(R.string.dialogFirstEnableTitle)
                            .setPositiveButton(R.string.dialogThanksPremiumButton, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    //consumePremium();
                                    dialog.dismiss();
                                }
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });
            //mWatchAdButton.setBackgroundColor(Color.parseColor("#FFD700"));
        }
        /*else {
            mWatchAdButton.setVisibility(View.VISIBLE);
        }*/
    }

    private void toggleBSoLService() {
        Intent intent = new Intent(MainActivity.this, ScreenOffService.class);
        if (!Utility.isMyServiceRunning(this, ScreenOffService.class)) {
            Log.v("BSoL", "Service not running, starting it...");
            startService(intent);
            showFirstTimeEnabledIfNecessary();
            new DisplayRateDialogIfRequiredTask(this).execute();
        } else {
            Log.v("BSoL", "Service ALREADY running, stopping it...");
            stopService(intent);
        }
        updateStatusAndNotificationIcon();
    }

    private void stopBSoLService() {
        Intent intent = new Intent(MainActivity.this, ScreenOffService.class);
        stopService(intent);
        updateStatusAndNotificationIcon();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    private void performFirstRunOperations() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFirsRun = sharedPreferences.getBoolean(
                getString(R.string.prefIsFirstRun), true);

        if(isFirsRun) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.prefIsFirstRun), false);
            editor.commit();

            Utility.updateLastTimeSeenAdWithCurrentTime(this);
            Utility.resetActiveBSoLTime(this);
        }
    }

    private void showChangelogIfNecessary() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String latestShownChangelog = sharedPreferences.getString(getString(R.string.prefLatestChangelogShown), null);

        //hack para mostrar de imediato na versao que quero fazer release
        if(latestShownChangelog == null) {
            String firstEnabledDateStr =
                    sharedPreferences.getString(getString(R.string.prefFirstEnabledDate), null);
            if(firstEnabledDateStr != null)
                latestShownChangelog = "showDialog!";
        }

        String versionName = null;
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName  = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        Log.d("BSoL", "versionName=" + versionName + "; latestShownChangelog=" + latestShownChangelog);

        if(latestShownChangelog != null && versionName != null &&
                !versionName.equals(latestShownChangelog)) {
            // neste caso a app já estava instalada e queremos mostrar o que mudou
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialogChangelogMessage)
                    .setTitle(R.string.dialogChangelogTitle)
                    .setPositiveButton(R.string.dialogChangelogButton, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.prefLatestChangelogShown), versionName);
        editor.apply();
    }

    private void showFirstTimeEnabledIfNecessary() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFirsEnabled = sharedPreferences.getBoolean(
                getString(R.string.prefIsFirstEnabled), true);
        if(isFirsEnabled) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialogFirstEnableMessage)
                    //.setTitle(R.string.dialogFirstEnableTitle)
                    .setPositiveButton(R.string.dialogFirstEnableButton, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getString(R.string.prefIsFirstEnabled), false);
            editor.apply();
        }
    }

    private void showErrorLoadingAd(boolean internetProblem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialogAdErrorTitle)
                .setPositiveButton(R.string.dialogAdErrorButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        if(internetProblem)
            builder.setMessage(R.string.dialogAdInternetErrorMessage);
        else builder.setMessage(R.string.dialogAdErrorMessage);

        AlertDialog dialog = builder.create();
        if(!isFinishing())
            dialog.show();
    }

    private void updateStatusAndNotificationIcon() {
        if(mStatusImageView == null)
            return;

        if (Utility.isMyServiceRunning(this, ScreenOffService.class)) {
            mStatusImageView.setImageResource(R.drawable.ic_status_enabled_2);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    cancelCurrentAnimation(mStatusImageViewRainbow);
                    mStatusImageViewRainbow.startAnimation(mAnimFadein);
                    //mStatusImageViewRainbow.setVisibility(View.VISIBLE);
                }
            }, 200);
        } else {
            cancelCurrentAnimation(mStatusImageViewRainbow);
            //para nao animarmos no start (quando o utilizador arranca na app em vez de carregar no botao
            if(mStatusImageViewRainbow.getVisibility() == View.VISIBLE)
                mStatusImageViewRainbow.startAnimation(mAnimFadeout);
            //mStatusImageViewRainbow.setVisibility(View.GONE);
            mStatusImageView.setImageResource(R.drawable.ic_status_disabled);

            if(Utility.getShowStoppedNotification(getApplicationContext()))
                Utility.showDisabledNotification(getApplicationContext());
        }
    }

    private void cancelCurrentAnimation(View v) {
        Animation currAnim = v.getAnimation();
        if(currAnim != null)
            currAnim.cancel();
        v.clearAnimation();
    }

    private void showInterstitialAd() {
        if (mInterstitialAd.isLoaded()) {
            mInterstitialAd.show();
        }
        else {
            requestNewInterstitial();
            showLoadingAdLayout();
        }
    }

    private void resetWatchAdVariables() {
        Utility.updateLastTimeSeenAdWithCurrentTime(MainActivity.this);
        Utility.resetActiveBSoLTime(MainActivity.this);
        Utility.setUserAlreadyWarnedToWatchAd(this, false);
    }

    private void showLoadingAdLayout() {
        mMainLayout.setVisibility(View.GONE);
        mLoadingAdLayout.setVisibility(View.VISIBLE);
    }

    private void showMainLayout() {
        mMainLayout.setVisibility(View.VISIBLE);
        mLoadingAdLayout.setVisibility(View.GONE);
    }

    private void requestNewInterstitial() {

        AdRequest adRequest;

        if(BSoLApplication.iSReleaseVersion)
            adRequest = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .build();
        else
            adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .addTestDevice("C65B0169B71ECAA5D05E7A456901E093")
                .build();

        mInterstitialAd.loadAd(adRequest);
    }

    private void createAndShowRateAppDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialogRateTitle)
                .setMessage(getString(R.string.dialogRateMessage))
                .setNegativeButton(R.string.dialogRateNegativeButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //nunca
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(getString(R.string.prefAppAlreadyRated), true);
                        editor.apply();

                        dialog.dismiss();
                    }
                })
                .setNeutralButton(R.string.dialogRateNeutralButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //mais tarde
                        dialog.dismiss();

                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(getString(R.string.prefRateDialogLastShownDate), Utility.getFormattedTodayDate());
                        editor.apply();
                    }
                })
                .setPositiveButton(R.string.dialogRatePositiveButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //avaliar
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(getString(R.string.prefAppAlreadyRated), true);
                        editor.apply();

                        dialog.dismiss();
                        String appId = MainActivity.this.getPackageName();
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appId)));
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.setCancelable(false);
    }


    public class DisplayRateDialogIfRequiredTask extends AsyncTask<Void, Void, Void> {

        private Context mContext;
        private boolean mShowDialog = false;

        public DisplayRateDialogIfRequiredTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean appAlreadyRated =
                    sharedPreferences.getBoolean(mContext.getString(R.string.prefAppAlreadyRated), false);

            if(!appAlreadyRated) {

                String firstEnabledDateStr =
                        sharedPreferences.getString(mContext.getString(R.string.prefFirstEnabledDate), null);

                if(firstEnabledDateStr == null) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(getString(R.string.prefFirstEnabledDate), Utility.getFormattedTodayDate());
                    editor.apply();
                    return null;
                }
                else {
                    if(Utility.getDateDiffInDays(Utility.getFormattedTodayDate(), firstEnabledDateStr)
                            >= SHOW_RATE_APP_DIALOG_AFTER_N_DAYS) {
                        String dialogLastShownDateStr =
                                sharedPreferences.getString(mContext.getString(R.string.prefRateDialogLastShownDate), null);

                        if(dialogLastShownDateStr == null ||
                                Utility.getDateDiffInDays(Utility.getFormattedTodayDate(), dialogLastShownDateStr)
                                        >= SHOW_RATE_APP_DIALOG_DAYS_INTERVAL) {
                            mShowDialog = true;
                        }
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if(mShowDialog)
                createAndShowRateAppDialog();
        }
    }
}
