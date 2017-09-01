package com.zwl9517.xfaiui.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.zwl9517.xfaiui.ConstantAIUI;
import com.zwl9517.xfaiui.receiver.WakeUpBroadcastReceiver;
import com.zwl9517.xfaiui.util.FucUtil;
import com.zwl9517.xfaiui.util.PatternUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

import static com.iflytek.cloud.VerifierResult.TAG;

/**
 * <pre>
 *      author : zouweilin
 *      e-mail : zwl9517@hotmail.com
 *      time   : 2017/08/29
 *      version:
 *      desc   : 语音识别+语义服务
 *      note   : 如果不喜欢自问自答（把自己的声音录进去然后识别自己的声音这种模式）
 *               你可以戴耳机或修改{@link SynthesizerTTSListener#onSpeakBegin()}里当朗读时关闭收听功能
 * </pre>
 */
public class AIRecognizeServer extends Service {

    private static final long AUTO_STOP_VOICE_NLP_TIME = 30000;     // 30秒后关闭语音识别功能，此后必须通过广播唤醒功能
    private int mAIUIState;             // AIUI目前的状态
    private String speechText;          // 即将朗读的文字
    private AIUIHandler handler;        // 用于自动停止识别
    private AIUIAgent mAIUIAgent;       // AIUI功能模块
    private SpeechSynthesizer mTts;     // 语音朗读模块
    private RecognizeCallback callback; // 识别结果回调

    private AIUIListener mAIUIListener = new AIUIResultListener();  // AIUI状态监听，从中可获取识别和语义匹配数据
    private SynthesizerListener synthesizerListener = new SynthesizerTTSListener(); // TTS朗读状态监听器
    /**
     * 唤醒广播接收器
     */
    private WakeUpBroadcastReceiver receiver = new WakeUpBroadcastReceiver() {
        @Override
        protected void onReceiveWakeUpBroadcast(Context context, Intent intent) {
            stopSpeaking();
            handler.postDelayed(new WakeUpRunnable(), 500);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new AIUIHandler(Looper.getMainLooper());
        registerReceiver(true);
        // 初始化TTS文字转写语音
        mTts = SpeechSynthesizer.createSynthesizer(AIRecognizeServer.this, new InitListener() {
            @Override
            public void onInit(int error) {
                // 在API文档里未发现error的值注解。
                Log.e("tag", "【AIRecognizeServer】类的方法：" + "onInit() called with: error = [" + error + "]");
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        startRecognizeNlp();// 服务被绑定时，开始识别
        return new Binder();
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        startRecognizeNlp();// 被再次绑定服务时，开启识别
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startRecognizeNlp();// 再次开启服务时，开启识别
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVoiceNlp();// onDestroy时，回收资源
        destroyTTS();
        registerReceiver(false);
        handler = null;
    }

    /**
     * 开始合成语音并播放
     */
    private void startSpeaking() {
        if (mTts != null && !TextUtils.isEmpty(speechText)) {
            mTts.startSpeaking(speechText, synthesizerListener);
        }
    }

    /**
     * 停止语音播放
     */
    private void stopSpeaking() {
        if (mTts != null && mTts.isSpeaking()) {
            mTts.stopSpeaking();
        }
    }

    /**
     * 恢复播放
     */
    private void resumeSpeaking() {
        if (mTts != null && !mTts.isSpeaking()) {
            mTts.resumeSpeaking();
        }
    }

    /**
     * 暂停语音播放
     */
    private void pauseSpeaking() {
        if (mTts != null && mTts.isSpeaking()) {
            mTts.pauseSpeaking();
        }
    }

    /**
     * 释放语音合成功能对象
     */

    private void destroyTTS() {
        if (null != mTts) {
            mTts.stopSpeaking();
            // 退出时释放连接
            mTts.destroy();
        }
    }

    /**
     * 开始识别,语音理解，通过麦克风输入语音，然后识别，识别结束后再调用语义理解
     */
    private void startRecognizeNlp() {
        if (checkAIUIAgent()) {
            //
            Log.e(TAG, "start voice nlp");

            // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
            if (AIUIConstant.STATE_WORKING != this.mAIUIState) {
                AIUIMessage wakeupMsg = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
                mAIUIAgent.sendMessage(wakeupMsg);
            }

            // 打开AIUI内部录音机，开始录音
            String params = "sample_rate=16000,data_type=audio";
            AIUIMessage writeMsg = new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params, null);
            mAIUIAgent.sendMessage(writeMsg);
            handler.sendEmptyMessage(handler.WAIT_FOR_STOP);
        }
    }

    /**
     * 检查代理是否创建成功
     */
    private boolean checkAIUIAgent() {
        if (null == mAIUIAgent) {
            Log.e(TAG, "create aiui agent");
            mAIUIAgent = AIUIAgent.createAgent(this, getAIUIParams(), mAIUIListener);
            AIUIMessage startMsg = new AIUIMessage(AIUIConstant.CMD_START, 0, 0, null, null);
            mAIUIAgent.sendMessage(startMsg);
            updateLexicon();// 更新词典，语音拼音符合提交的文字的拼音，那么直接匹配为词典里的词语
        }

        if (null == mAIUIAgent) {
            Log.e("tag", "【AIRecognizeServer】类的方法：【checkAIUIAgent】: " + "创建 AIUI Agent 失败！");
        }

        return null != mAIUIAgent;
    }

    /**
     * 停止语音理解：AIUI 是连续会话，一次 start 后，可以连续的录音并返回结果；要停止需要调用 stop
     */
    private void stopVoiceNlp() {
        Log.e(TAG, "stop voice nlp");
        // 停止录音
        String params = "sample_rate=16000,data_type=audio";
        AIUIMessage stopWriteMsg = new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, params, null);

        mAIUIAgent.sendMessage(stopWriteMsg);
    }

    private String getAIUIParams() {
        String params = "";

        AssetManager assetManager = getResources().getAssets();
        try {
            InputStream ins = assetManager.open("cfg/aiui_phone.cfg");
            byte[] buffer = new byte[ins.available()];

            ins.read(buffer);
            ins.close();

            params = new String(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return params;
    }

    /**
     * 更新词典，即根据词条进行识别比对，比对成功直接识别为词条里的词
     */
    private void updateLexicon() {
        String params = null;
        String contents = FucUtil.readFile(this, "userwords", "utf-8");
        try {
            JSONObject joAiuiLexicon = new JSONObject();
            joAiuiLexicon.put("name", "userword");
            joAiuiLexicon.put("content", contents);
            params = joAiuiLexicon.toString();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        AIUIMessage msg = new AIUIMessage(AIUIConstant.CMD_UPLOAD_LEXICON, 0, 0, params, null);
        mAIUIAgent.sendMessage(msg);
    }

    /**
     * 注册唤醒广播接收器
     *
     * @param register {@code true}注册，{@code false}反注册
     */
    private void registerReceiver(boolean register) {
        if (register) {
            registerReceiver(receiver, new IntentFilter(ConstantAIUI.WAKE_UP_ACTION));
        } else {
            unregisterReceiver(receiver);
        }
    }

    public class Binder extends android.os.Binder {
        public AIRecognizeServer getServer() {
            return AIRecognizeServer.this;
        }
    }

    /**
     * 回调给Activity用于识别结果的界面展示
     */
    public interface RecognizeCallback {
        void result(String content, String understanding);

        void stateResult(String content);
    }

    public void setCallback(RecognizeCallback callback) {
        this.callback = callback;
    }

    private void setStateResult(String stateResult) {
        if (callback != null)
            callback.stateResult(stateResult);
    }

    /**
     * 通过Handler来实现语音识别的自动关闭
     */
    private class AIUIHandler extends Handler {
        final int WAIT_FOR_STOP = 0x1000;
        final int STOP_VOICE_NLP = 0x1010;

        AIUIHandler(Looper mainLooper) {
            super(mainLooper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WAIT_FOR_STOP:
                    removeMessages(STOP_VOICE_NLP);                                         // 移除队列韩总延迟的消息
                    sendEmptyMessageDelayed(STOP_VOICE_NLP, AUTO_STOP_VOICE_NLP_TIME);      // 刷新关闭语音识别消息
                    break;
                case STOP_VOICE_NLP:
                    if (mTts != null) {
                        if (mTts.isSpeaking()) {
                            // 如果正在朗读，则自动延时30s关闭语音监听器
                            sendEmptyMessage(WAIT_FOR_STOP);
                            break;
                        } else {
                            // 如果没有语音、也没有朗读则关闭语音识别，等待下一次唤醒语音识别
                            mTts.startSpeaking("小白已休眠，唤醒请说 “你好，小白”", null);
                        }
                    }
                    stopVoiceNlp();// 延迟自动关闭识别
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }

    /**
     * 语音识别和语义数据监听器
     */
    private class AIUIResultListener implements AIUIListener {

        @Override
        public void onEvent(AIUIEvent event) {
            switch (event.eventType) {
                case AIUIConstant.EVENT_WAKEUP:
                    Log.e(TAG, "on event: 进入识别状态" + event.eventType);
                    setStateResult("进入识别状态");
                    break;

                case AIUIConstant.EVENT_RESULT: {
                    Log.e(TAG, "on event: " + event.eventType);
                    try {

                        String info = event.info;
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "这是处理前内容：" + info);
                        JSONObject bizParamJson = new JSONObject(info);
                        JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
                        JSONObject params = data.getJSONObject("params");
                        JSONObject content = data.getJSONArray("content").getJSONObject(0);

                        if (content.has("cnt_id")) {
                            String cnt_id = content.getString("cnt_id");
                            JSONObject cntJson = new JSONObject(new String(event.data.getByteArray(cnt_id), "utf-8"));

                            Log.e("tag", "【AIUIResultListener】类的方法：【onEvent】: " + cntJson.toString());
                            String sub = params.optString("sub");
                            if ("nlp".equals(sub)) {
                                // 解析得到语义结果
                                String resultStr = cntJson.optString("intent");
                                Log.e("tag", "【AIUIResultListener】类的方法：【onEvent】解析得到语义结果: " + resultStr);
                                if ("{}".equals(resultStr)) return;
                                speechText = "";
                                try {
                                    JSONObject jsonObject = new JSONObject(resultStr);
                                    JSONObject answer = jsonObject.optJSONObject("answer");
                                    speechText = answer == null ? "这个问题没有回答结果"/*+ConstantString.noAnswer()*/ : answer.optString("text");
                                    speechText = PatternUtil.matchTrain(speechText);
                                    startSpeaking();
                                    if (null != callback)
                                        callback.result(jsonObject.optString("text"), speechText);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    Log.e("tag", "【AIUIResultListener】类的方法：【onEvent】: " + e.toString());
                                    setStateResult(e.toString());
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】:Throwable " + e.getLocalizedMessage());
                    }

                }
                break;

                case AIUIConstant.EVENT_ERROR: {
                    Log.i(TAG, "on event: " + event.eventType + "\n" + "错误: " + event.arg1 + "\n" + event.info);
                }
                break;

                case AIUIConstant.EVENT_VAD: {
                    if (AIUIConstant.VAD_BOS == event.arg1) {
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "找到vad_bos");
                    } else if (AIUIConstant.VAD_EOS == event.arg1) {
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "找到vad_eos");

                    } else {
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + event.arg2);
                    }
                }
                break;

                case AIUIConstant.EVENT_START_RECORD: {
                    Log.e(TAG, "on event: " + event.eventType + " 开始录音");
                    setStateResult("开始录音");
                }
                break;

                case AIUIConstant.EVENT_STOP_RECORD: {
                    Log.i(TAG, "on event: " + event.eventType + " 停止录音");
                    setStateResult("停止录音");
                }
                break;

                case AIUIConstant.EVENT_STATE: {    // 状态事件
                    mAIUIState = event.arg1;

                    if (AIUIConstant.STATE_IDLE == mAIUIState) {
                        // 闲置状态，AIUI未开启
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "STATE_IDLE");
                        setStateResult("STATE_IDLE");
                    } else if (AIUIConstant.STATE_READY == mAIUIState) {
                        // AIUI已就绪，等待唤醒
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "STATE_READY");
                        setStateResult("STATE_READY");
                    } else if (AIUIConstant.STATE_WORKING == mAIUIState) {
                        // AIUI工作中，可进行交互
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "STATE_WORKING");
                        setStateResult("STATE_WORKING");
                    }
                }
                break;

                case AIUIConstant.EVENT_CMD_RETURN: {
                    if (AIUIConstant.CMD_UPLOAD_LEXICON == event.arg1) {
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "上传" + (0 == event.arg2 ? "成功" : "失败"));
                    }
                }
                break;

                default:
                    break;
            }
        }
    }

    /**
     * 合成监听器
     */
    private class SynthesizerTTSListener implements SynthesizerListener {
        @Override
        public void onSpeakBegin() {
//            stopVoiceNlp();// 机器人开始朗读时停止识别
            startRecognizeNlp();// 语音播放结束，开启识别
        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {

        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakResumed() {

        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {

        }

        @Override
        public void onCompleted(SpeechError speechError) {
            startRecognizeNlp();// 语音播放结束，开启识别
        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    }

    private class WakeUpRunnable implements Runnable {
        @Override
        public void run() {
            speechText = "在的";
            startSpeaking();
        }
    }

}
