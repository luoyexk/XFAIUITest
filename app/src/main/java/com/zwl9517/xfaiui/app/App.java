package com.zwl9517.xfaiui.app;

import android.app.Application;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

/**
 * <pre>
 *      author : zouweilin
 *      e-mail : zwl9517@hotmail.com
 *      time   : 2017/08/29
 *      version:
 *      desc   :
 * </pre>
 */
public class App extends Application {

    @Override
    public void onCreate() {
        SpeechUtility.createUtility(getApplicationContext(), SpeechConstant.APPID + "=59a3f271");
        super.onCreate();
    }
}
