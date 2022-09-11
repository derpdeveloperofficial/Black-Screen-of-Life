package com.derpdeveloper.blackscreenoflife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AutoStartBoot extends BroadcastReceiver
{
    public void onReceive(Context context, Intent intent)
    {
        if(Utility.getStartOnBoot(context)) {
            if(Utility.getShowStoppedNotification(context)) {
                Utility.showDisabledNotification(context);
            }
            else {
                Intent startIntent = new Intent(context, ScreenOffService.class);
                context.startService(startIntent);
            }
        }
    }
}
