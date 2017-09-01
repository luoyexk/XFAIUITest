package com.zwl9517.xfaiui.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.zwl9517.xfaiui.ConstantAIUI;


/**
 * <pre>
 *      author : zouweilin
 *      e-mail : zwl9517@hotmail.com
 *      time   : 2017/08/30
 *      version:
 *      desc   : 唤醒服务，当收到唤醒指令时
 * </pre>
 */
public class WakeUpServer extends Service {

    public static void start(Context context) {
        context.startService(new Intent(context, WakeUpServer.class));
    }

    public static boolean stop(Context context) {
        return context.stopService(new Intent(context, WakeUpServer.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("tag", "【WakeUpServer】类的方法：【onStartCommand】: " + "");
        processWakeUp();
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void processWakeUp() {
        sendBroadcast(new Intent(ConstantAIUI.WAKE_UP_ACTION));
    }
}
