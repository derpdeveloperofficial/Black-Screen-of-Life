package com.derpdeveloper.blackscreenoflife;

import android.app.Application;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

/**
 * Created by Micael on 08/08/2015.
 */

@ReportsCrashes(
        formUri = "https://derpdeveloper.cloudant.com/acra-bsol/_design/acra-storage/_update/report",
        reportType = HttpSender.Type.JSON,
        httpMethod = HttpSender.Method.POST,
        formUriBasicAuthLogin = "dergessevervengiverstand",
        formUriBasicAuthPassword = "c71585f3dafc04e43c048acab241d50413ebe551",
        //formKey = "", // This is required for backward compatibility but not used
        customReportContent = {
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.PACKAGE_NAME,
                ReportField.REPORT_ID,
                ReportField.BUILD,
                ReportField.STACK_TRACE
        },
        mode = ReportingInteractionMode.SILENT
)

public class BSoLApplication extends Application {

    public static final String Base64RSAKeyPt1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApeeGSvB6CChRwWnhlk0lALYnX4c1t93SSDQRJNDMCa5uAGLFya1fFmDmzMq8PPptKCIn6up/p6Iwx0";
    public static boolean iSReleaseVersion;
    public static final String SKU_PREMIUM = "premium.2001";
    public static final String IN_APP_BUY_DEVELOPER_PAYLOAD = "DerpDeveloper";
    public static final String BROADCAST_SHUTDOWN_SERVICE = "com.derpdeveloper.blackscreenoflife.broadcast.shutdown";
    public static final String BROADCAST_UPDATE_NOTIFICATION = "com.derpdeveloper.blackscreenoflife.broadcast.updatenotification";

    @Override
    public void onCreate() {
        super.onCreate();

        iSReleaseVersion = getString(R.string.gradleIsReleaseVersion).equals("true");

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                        .setDefaultFontPath("LeHand.ttf")
                        .setFontAttrId(R.attr.fontPath)
                        .build()
        );

        if(iSReleaseVersion)
            ACRA.init(this);
    }

}
