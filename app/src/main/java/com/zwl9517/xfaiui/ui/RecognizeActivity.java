package com.zwl9517.xfaiui.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zwl9517.xfaiui.R;
import com.zwl9517.xfaiui.constant.ConstantString;
import com.zwl9517.xfaiui.server.AIRecognizeServer;
import com.zwl9517.xfaiui.server.WakeUpServer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecognizeActivity extends AppCompatActivity implements View.OnClickListener, AIRecognizeServer.RecognizeCallback {

    private int num;
    private View btnStartService;
    private View btnStartAgain;
    private TextView tv, tvState;
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AIRecognizeServer.Binder binder = (AIRecognizeServer.Binder) service;
            binder.getServer().setCallback(RecognizeActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognized);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermissions();
        }
        initViews();
        WakeUpServer.start(this);
        if (isServiceWork()) {
            bind();// 为了设置AIRecognizeServer.RecognizeCallback
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkSelfPermissions() {
        List<String> deniedPermission = new ArrayList<>();
        for (String basePermission : ConstantString.BASE_PERMISSIONS) {
            int i = checkSelfPermission(basePermission);
            if (i == PackageManager.PERMISSION_DENIED) {
                deniedPermission.add(basePermission);
            }
        }
        requestPermissions(deniedPermission.toArray(new String[deniedPermission.size()]), ConstantString.REQUEST_PERMISSION_CODE);
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) && PackageManager.PERMISSION_DENIED == checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            Toast.makeText(this, "NO Record Audio Permission", Toast.LENGTH_SHORT).show();
    }

    private void initViews() {
        btnStartService = findViewById(R.id.btn_start_service);
        btnStartAgain = findViewById(R.id.btn_start_again_service);
        tv = ((TextView) findViewById(R.id.tv_recognize));
        tvState = ((TextView) findViewById(R.id.tv_state));
        scrollView = ((ScrollView) findViewById(R.id.scrollView));

        btnStartService.setOnClickListener(this);
        btnStartAgain.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == btnStartService) {
            startService(new Intent(this, AIRecognizeServer.class));
            bindAIService();
        } else if (v == btnStartAgain) {
            WakeUpServer.start(this);
        }
    }

    private void bindAIService() {
        tv.postDelayed(new Runnable() {
            @Override
            public void run() {
                bind();
                tv.setText("开始说话吧");
            }
        }, 1000);
    }

    private void bind() {
        RecognizeActivity.this.bindService(new Intent(RecognizeActivity.this, AIRecognizeServer.class), conn, Context.BIND_AUTO_CREATE);
    }

    /**
     * 判断某个服务是否正在运行的方法
     */
    public boolean isServiceWork() {
        boolean isWork = false;
        ActivityManager myAM = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> myList = myAM.getRunningServices(40);
        if (myList.size() <= 0) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            String mName = myList.get(i).service.getClassName();
            if (mName.equals("com.tuling.robot.xfaiui.server.AIRecognizeServer")) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }

    @Override
    public void result(String content, String understanding) {
        tv.setText(String.format("识别结果为:%1$s", content) + "\n" + String.format("这是理解语义：%1$s", understanding));
    }

    @Override
    public void stateResult(String content) {
        if (tvState.getText().toString().length() > 3000) {
            tvState.setText("");
            num = 0;
        }
        tvState.append(new SimpleDateFormat(++num + "\tHH-mm-ss", Locale.getDefault()).format(new Date()) + "\t" + content);
        tvState.append("\n");
        tvState.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }
}
