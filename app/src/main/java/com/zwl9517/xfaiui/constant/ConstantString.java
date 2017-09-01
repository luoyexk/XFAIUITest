package com.zwl9517.xfaiui.constant;

import android.Manifest;

import java.util.Random;

/**
 * <pre>
 *      author : zouweilin
 *      e-mail : zwl9517@hotmail.com
 *      time   : 2017/08/30
 *      version:
 *      desc   :
 * </pre>
 */
public class ConstantString {

    private static final String[] NO_ANSWERS = {
            "这个问题我答不上来",
            "小白还在学习中，以后一定可以回答你这个问题",
            "啥，刚才说啥？",
            "我想我还可以更智能",
            "要回答你的问题，我首先要成为最强大脑",
            "我得好好复习十万个为什么"
    };

    public static String noAnswer() {
        return NO_ANSWERS[new Random().nextInt(NO_ANSWERS.length)];
    }

    public static final String[] BASE_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE,
//            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_SETTINGS
    };
    public static final int REQUEST_PERMISSION_CODE = 0x100;
}
