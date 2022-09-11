package com.derpdeveloper.blackscreenoflife;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.derpdeveloper.blackscreenoflife.billingutil.IabHelper;
import com.derpdeveloper.blackscreenoflife.billingutil.IabResult;
import com.derpdeveloper.blackscreenoflife.billingutil.Inventory;
import com.derpdeveloper.blackscreenoflife.billingutil.Purchase;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Created by Micael on 06/10/2015.
 */
public class SettingsActivity extends PreferenceActivity {

    private static final int IN_APP_BUY_REQUEST_CODE = 2001;

    private IabHelper mHelperBuy;
    private IabHelper mHelperCheck;
    private boolean mIsBuyInProgress = false;

    private boolean mIsPremium = false;
    private PreferenceScreen buyPremiumButton;
    private CheckBoxPreference mShowRunningNotificationCB;
    private CheckBoxPreference mShowStoppedNotificationCB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        buyPremiumButton = (PreferenceScreen) findPreference(getString(R.string.prefBuyPremiumKey));
        buyPremiumButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                buyPremiumVersion();
                return true;
            }
        });

        mShowRunningNotificationCB = (CheckBoxPreference) findPreference(getString(R.string.prefShowRunningNotificationKey));
        mShowRunningNotificationCB.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object checked) {
                Intent intent = new Intent();
                intent.setAction(BSoLApplication.BROADCAST_UPDATE_NOTIFICATION);
                sendBroadcast(intent);
                return true;
            }
        });

        mShowStoppedNotificationCB = (CheckBoxPreference) findPreference(getString(R.string.prefShowStoppedNotificationKey));
        mShowStoppedNotificationCB.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object checked) {
                if(!Utility.isMyServiceRunning(SettingsActivity.this, ScreenOffService.class)) {
                    if((Boolean) checked)
                        Utility.showDisabledNotification(getApplicationContext());
                    else
                        Utility.cancelDisabledNotification(getApplicationContext());
                }
                return true;
            }
        });

        mIsPremium = Utility.getLatestPremiumMode(this);
        updateIfPremiumMode();
        checkForPremium();

        /*if(Utility.getIsShakeEnabled(this) &&
                mIsPremium &&
                !Utility.isMyServiceRunning(this, ShakeService.class))
            startService(new Intent(this, ShakeService.class));*/
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mHelperBuy != null)
            mHelperBuy.dispose();
        mHelperBuy = null;

        if (mHelperCheck != null)
            mHelperCheck.dispose();
        mHelperCheck = null;
    }

    @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("BSOL", "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelperBuy == null) return;

        // Pass on the activity result to the helper for handling
        if (!mHelperBuy.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d("BSoL", "onActivityResult handled by IABUtil.");
        }
    }

    private void buyPremiumVersion() {
        if(mIsBuyInProgress)
            return;

        mIsBuyInProgress = true;
        Log.v("BSOL", "User started the In-app Billing BUY PREMIUM process.");

        String base64EncodedPublicKey = BSoLApplication.Base64RSAKeyPt1 + MainActivity.Base64RSAKeyPt2 + Utility.Base64RSAKeyPt3 + getString(R.string.inTheEnd);
        mHelperBuy = new IabHelper(this, base64EncodedPublicKey);
        mHelperBuy.startSetup(new BuyPremiumSetupFinished());
    }

    private class BuyPremiumSetupFinished implements IabHelper.OnIabSetupFinishedListener {
        public void onIabSetupFinished(IabResult result) {
            if (!result.isSuccess()) {
                // Oh noes, there was a problem.
                Log.d("BSoL", "Problem setting up In-app Billing: " + result);
                mIsBuyInProgress = false;
                return;
            }

            // Hooray, IAB is fully set up!
            Log.d("BSoL", "Received good response In-app Billing: " + result);

            IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener
                    = new BuyPremiumPurchaseFinished();

            if(mHelperBuy.isSetupDone() && !mHelperBuy.isAsyncInProgress()) {
                mHelperBuy.launchPurchaseFlow(SettingsActivity.this, BSoLApplication.SKU_PREMIUM, IN_APP_BUY_REQUEST_CODE,
                        mPurchaseFinishedListener, BSoLApplication.IN_APP_BUY_DEVELOPER_PAYLOAD);
            }
        }
    }

    private class BuyPremiumPurchaseFinished implements IabHelper.OnIabPurchaseFinishedListener {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {

            Log.d("BSoL", "Buy Premium Purchase finished: " + result);
            mIsBuyInProgress = false;

            if (result.isFailure()) {
                Log.d("BSOL", "Error purchasing: " + result);
                return;
            }

            Log.d("BSOL", "Purchase successful: " + result);

            if (purchase.getSku().equals(BSoLApplication.SKU_PREMIUM)) {
                if(purchase.getDeveloperPayload().equals(BSoLApplication.IN_APP_BUY_DEVELOPER_PAYLOAD)) {

                    Log.d("BSOL", "Purchase values check!");

                    // give user access to premium content and update the UI
                    mIsPremium = true;
                    Utility.setLatestPremiumMode(SettingsActivity.this, true);
                    updateIfPremiumMode();
                    showThanksForBuyingDialog();
                    //checkForPremium();
                } else {
                    Log.d("BSOL", "Purchase values ARE WRONG!");
                }
            }
            else
                Log.d("BSOL", "Purchase SKU not matched!");
        }
    }

    private void checkForPremium() {
        if (mHelperCheck != null)
            mHelperCheck.dispose();

        String base64EncodedPublicKey = BSoLApplication.Base64RSAKeyPt1 + MainActivity.Base64RSAKeyPt2 + Utility.Base64RSAKeyPt3 + getString(R.string.inTheEnd);
        mHelperCheck = new IabHelper(this, base64EncodedPublicKey);
        mHelperCheck.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d("BSoL", "Problem setting up In-app Billing: " + result);
                    return;
                }

                Log.d("BSoL", "Received good response In-app Billing: " + result);

                if(mHelperCheck.isSetupDone() && ! mHelperCheck.isAsyncInProgress()) {
                    try {
                        mHelperCheck.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
                            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                                if (result.isFailure()) {
                                    Log.d("BSoL", "Problem contacting In-app Billing: " + result);
                                } else {
                                    Purchase premiumPurchase = inventory.getPurchase(BSoLApplication.SKU_PREMIUM);
                                    mIsPremium = (premiumPurchase != null &&
                                            premiumPurchase.getDeveloperPayload().equals(BSoLApplication.IN_APP_BUY_DEVELOPER_PAYLOAD));
                                    Utility.setLatestPremiumMode(SettingsActivity.this, mIsPremium);
                                    updateIfPremiumMode();
                                    Log.d("BSoL", "User has premium: " + mIsPremium);
                                }
                            }
                        });
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        });
    }

    private void updateIfPremiumMode() {
        Log.v("BSoL", "updateIfPremiumMode running");
        if(mIsPremium) {
            Log.v("BSoL", "updateIfPremiumMode IS PREMIUM! changing to premium mode...");
            //noAdsCheckbox.setEnabled(true);
            buyPremiumButton.setTitle(getString(R.string.prefBuyPremiumTitleAfterBuying));
            buyPremiumButton.setSummary(getString(R.string.prefBuyPremiumSummaryAfterBuying));
            buyPremiumButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    return true;
                }
            });
            //PreferenceCategory premiumCategory = (PreferenceCategory) findPreference(getString(R.string.prefCategoryPremiumKey));
            //premiumCategory.removePreference(buyPremiumButton);
        }
        /*else {
            noAdsCheckbox.setEnabled(false);
            shakeCheckbox.setEnabled(false);
            vibrateCheckbox.setEnabled(false);
            //aqui não voltamos a adicionar o buyPremium... Devíamos, mas não é um caso normal e não me apetece fazer
        }*/
    }

    private void showThanksForBuyingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialogThanksPremiumMessage)
                //.setTitle(R.string.dialogFirstEnableTitle)
                .setPositiveButton(R.string.dialogThanksPremiumButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
