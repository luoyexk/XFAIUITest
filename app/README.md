###科大讯飞AIUI功能测试

###接入指南

####在清单中配置如下：

######权限

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_SETTINGS" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

######在清单Application中添加KEY

    需要申请
    <meta-data
    android:name="IFLYTEK_APPKEY"
    android:value="59a3f271" />

####复制so库和jar包

######功能组件添加

    把从AIUI处下载的SDK解压，然后复制libs文件夹里的如下文件放到工程的libs里
    armeabi
    ----libmsc.so
    Msc.jar
    Sunflower.jar

######加载功能组件

    打开model的build.gradle，在defaultConfig{}里添加

```
    ndk {
        abiFilters "armeabi"
    }
```

    在android{}里添加

```
    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }
```

####添加cfg、vad文件夹

    在刚才解压的SDK文件夹里，找到assets文件夹复制cfg文件夹，回到android工程，在模块里新建asstes文件夹，把刚才复制的cfg粘贴到asstes里
    同样的，把解压的res/vad文件夹复制到刚才提到的位置

    文件结构如下：
    main
        assets
            cfg
                aiui_phone.cfg
            vad
                meta_vad_16k.jet

####Application中初始化

    在onCreate中

```
SpeechUtility.createUtility(getApplicationContext(), SpeechConstant.APPID + "=59a3f271");
```

####开始使用

######构造识别代理

    * 首先构造AIUI代理，该功能模块是在原语音识别基础上构造起来的，当识别语音时，返回识别结果，同时也返回讯飞的AI语义匹配结果

```
    private boolean checkAIUIAgent() {
        if (null == mAIUIAgent) {
            Log.e(TAG, "create aiui agent");
            mAIUIAgent = AIUIAgent.createAgent(this, getAIUIParams(), mAIUIListener);
            AIUIMessage startMsg = new AIUIMessage(AIUIConstant.CMD_START, 0, 0, null, null);
            mAIUIAgent.sendMessage(startMsg);
            // updateLexicon();// 更新词典
        }

        if (null == mAIUIAgent) {
            Log.e("tag", "【AIRecognizeServer】类的方法：【checkAIUIAgent】: " + "创建 AIUI Agent 失败！");
        }

        return null != mAIUIAgent;
    }
```

```
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
```

* 识别结果接听器

```
private AIUIListener mAIUIListener = new AIUIResultListener();

    /**
     * 语音识别和语义数据监听器
     */
    private class AIUIResultListener implements AIUIListener {
        @Override
        public void onEvent(AIUIEvent event) {
            switch (event.eventType) {
                case AIUIConstant.EVENT_WAKEUP:
                    Log.e(TAG, "on event: 进入识别状态" + event.eventType);
                    break;

                case AIUIConstant.EVENT_RESULT: {
                    Log.e(TAG, "on event: " + event.eventType);
                    try {

                        String info = event.info;
//                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "这是处理前内容：" + info);
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
                                // todo 获取到识别后的意图，在这里做你想做的事
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
                }
                break;

                case AIUIConstant.EVENT_STOP_RECORD: {
                    Log.i(TAG, "on event: " + event.eventType + " 停止录音");
                }
                break;

                case AIUIConstant.EVENT_STATE: {    // 状态事件
                    mAIUIState = event.arg1;

                    if (AIUIConstant.STATE_IDLE == mAIUIState) {
                        // 闲置状态，AIUI未开启
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "STATE_IDLE");
                    } else if (AIUIConstant.STATE_READY == mAIUIState) {
                        // AIUI已就绪，等待唤醒
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "STATE_READY");
                    } else if (AIUIConstant.STATE_WORKING == mAIUIState) {
                        // AIUI工作中，可进行交互
                        Log.e("tag", "【AIRecognizeServer】类的方法：【onEvent】: " + "STATE_WORKING");
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
```

######开启识别功能

```
    /**
     * 开始识别,语音理解，通过麦克风输入语音，然后识别，识别结束后再调用语义理解
     * 此处开始后就不会主动结束收听，除非触发了语音识别或者主动关闭收听，关闭后需再调用该方法打开收听
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
        }
    }
```

######关闭语音识别功能（可再开启）

```
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
```

###到此，AIUI的功能配置完毕

###TTS功能（Text To Speech）

如果你还需要朗读文字功能，可以尝试以下方法：

* 初始化

```
mTts = SpeechSynthesizer.createSynthesizer(context, new InitListener() {
            @Override
            public void onInit(int error) {
                // 在API文档里未发现error的值注解。
            }
        });
```

```
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
```



