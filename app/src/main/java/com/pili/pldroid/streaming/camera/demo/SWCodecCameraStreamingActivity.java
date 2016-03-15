package com.pili.pldroid.streaming.camera.demo;

import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.pili.pldroid.streaming.CameraStreamingManager;
import com.pili.pldroid.streaming.CameraStreamingManager.EncodingType;
import com.pili.pldroid.streaming.CameraStreamingSetting;
import com.pili.pldroid.streaming.StreamStatusCallback;
import com.pili.pldroid.streaming.StreamingPreviewCallback;
import com.pili.pldroid.streaming.StreamingProfile;
import com.pili.pldroid.streaming.StreamingProfile.StreamStatus;
import com.pili.pldroid.streaming.StreamingProfile.StreamStatusConfig;
import com.pili.pldroid.streaming.SurfaceTextureCallback;
import com.pili.pldroid.streaming.camera.demo.gles.FBO;
import com.pili.pldroid.streaming.widget.AspectFrameLayout;
import com.qiniu.android.dns.DnsManager;
import com.qiniu.android.dns.IResolver;
import com.qiniu.android.dns.NetworkInfo;
import com.qiniu.android.dns.http.DnspodFree;
import com.qiniu.android.dns.local.AndroidDnsServer;
import com.qiniu.android.dns.local.Resolver;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by jerikc on 15/10/29.
 */
public class SWCodecCameraStreamingActivity extends StreamingBaseActivity
        implements
        StreamStatusCallback,
        StreamingPreviewCallback,
        SurfaceTextureCallback {
    private static final String TAG = "SWCodecCameraStreaming";
//    private Button mCaptureFrameBtn;
    private StreamingProfile mProfile;
    private TextView mStreamStatus;

    private FBO mFBO = new FBO();

//    private Screenshooter mScreenshooter = new Screenshooter();
    // HappyDns 支持,为了防止 Dns 被劫持，SDK 加入了 HappyDns 支持
    public static DnsManager getMyDnsManager() {
        IResolver r0 = new DnspodFree();
        IResolver r1 = AndroidDnsServer.defaultResolver();
        IResolver r2 = null;
        try {
            r2 = new Resolver(InetAddress.getByName("119.29.29.29"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return new DnsManager(NetworkInfo.normal, new IResolver[]{r0, r1, r2});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: 2016/3/16  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // 设置actionBar覆盖在window内容上面
            requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
        super.onCreate(savedInstanceState);

        // 初始化数据
        initData();
        // 初始化布局
        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        // 设置显示模式
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.REAL);
        // 播放视图控件
        CameraPreviewFrameView cameraPreviewFrameView = (CameraPreviewFrameView) findViewById(R.id.cameraPreview_surfaceView);
//        cameraPreviewFrameView.setListener(this);

        mShutterButton = (Button) findViewById(R.id.toggleRecording_button);

        mSatusTextView = (TextView) findViewById(R.id.streamingStatus);
//        mCaptureFrameBtn = (Button) findViewById(R.id.capture_btn);
        mStreamStatus = (TextView) findViewById(R.id.stream_status);
        // 自定义AVProfile
        StreamingProfile.AudioProfile aProfile = new StreamingProfile.AudioProfile(44100, 96 * 1024);
        StreamingProfile.VideoProfile vProfile = new StreamingProfile.VideoProfile(30, 1000 * 1024, 48);
        StreamingProfile.AVProfile avProfile = new StreamingProfile.AVProfile(vProfile, aProfile);
        // 设置播放路径
        StreamingProfile.Stream stream = new StreamingProfile.Stream(mJSONObject);
        mProfile = new StreamingProfile();
        // 设置音频视频质量、
        mProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_HIGH3)
                .setAudioQuality(StreamingProfile.AUDIO_QUALITY_MEDIUM2)
//                .setPreferredVideoEncodingSize(960, 544)  // 设置EncodingSize
                .setEncodingSizeLevel(Config.ENCODING_LEVEL)// 设置EncodingSize
                .setEncoderRCMode(StreamingProfile.EncoderRCModes.QUALITY_PRIORITY)// 设置质量优先
                .setStream(stream)// 设置路径（重要）
                .setAVProfile(avProfile)// 设置自定义AVProfile
                .setDnsManager(getMyDnsManager())// 防止dns被劫持，HappyDns 支持
                .setStreamStatusConfig(new StreamStatusConfig(3))// 可以获取 StreamStatus 信息
//                .setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.PORT)
                //自适应码率，首先需要构造SendingBufferProfile对象。
                .setSendingBufferProfile(new StreamingProfile.SendingBufferProfile(0.2f, 0.8f, 3.0f, 20 * 1000));

        CameraStreamingSetting setting = new CameraStreamingSetting();
        setting.setCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT)// 设置使用前置摄像头
                .setContinuousFocusModeEnabled(true)// 添加控制连续自动对焦的接口
                .setRecordingHint(false)// 不使用高 FPS 推流，慎开
                .setResetTouchFocusDelayInMs(3000)
                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.MEDIUM)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_16_9);

        mCameraStreamingManager = new CameraStreamingManager(this, afl, cameraPreviewFrameView, EncodingType.SW_VIDEO_WITH_SW_AUDIO_CODEC);  // soft codec
        mCameraStreamingManager.prepare(setting, mProfile);
        // 设置手动对焦
//        setFocusAreaIndicator();

        // update the StreamingProfile
//        mProfile.setStream(new Stream(mJSONObject1));
//        mCameraStreamingManager.setStreamingProfile(mProfile);
        mCameraStreamingManager.setStreamingStateListener(this);// onStateChanged ,onStateHandled
        mCameraStreamingManager.setStreamingPreviewCallback(this);// Filter ,滤镜，onPreviewFrame ，onDrawFrame
        mCameraStreamingManager.setSurfaceTextureCallback(this);// 推流端预览具有滤镜效果需实现 SurfaceTextureCallback
        mCameraStreamingManager.setStreamingSessionListener(this);// onRecordAudioFailedHandled,
        // onRestartStreamingHandled,onPreviewSizeSelected
        mCameraStreamingManager.setStreamStatusCallback(this);// 可以获取 StreamStatus 信息
//        mCameraStreamingManager.setNativeLoggingEnabled(false);


        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mShutterButtonPressed) {
                    stopStreaming();
                } else {
                    startStreaming();
                }
            }
        });
        //截图,以后有用
//        mCaptureFrameBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                mHandler.removeCallbacks(mScreenshooter);
//                mHandler.postDelayed(mScreenshooter, 100);
//            }
//        });

    }

    private void initData() {
        String streamJson = StreamJsonUtils.createStreamJson("rtmp://192.168.1.112/live/123");
        try {
            mJSONObject = new JSONObject(streamJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        mHandler.removeCallbacksAndMessages(null);
    }
    // 过滤器
    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

    }
    // 过滤器
    @Override
    public boolean onPreviewFrame(byte[] bytes, int width, int height) {
//        deal with the yuv data.
//        long start = System.currentTimeMillis();
//        for (int i = 0; i < bytes.length; i++) {
//            bytes[i] = 0x00;
//        }
//        Log.i(TAG, "old onPreviewFrame cost :" + (System.currentTimeMillis() - start));
        return true;
    }
    // 软编模式下，推流端预览具有滤镜效果需实现 SurfaceTextureCallback
    @Override
    public void onSurfaceCreated() {
        Log.i(TAG, "onSurfaceCreated");
        mFBO.initialize(this);
    }
    // 软编模式下，推流端预览具有滤镜效果需实现 SurfaceTextureCallback
    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged width:" + width + ",height:" + height);
        mFBO.updateSurfaceSize(width, height);
    }
    // 软编模式下，推流端预览具有滤镜效果需实现 SurfaceTextureCallback
    @Override
    public void onSurfaceDestroyed() {
        Log.i(TAG, "onSurfaceDestroyed");
        mFBO.release();
    }
    // 软编模式下，推流端预览具有滤镜效果需实现 SurfaceTextureCallback
    @Override
    public int onDrawFrame(int texId, int texWidth, int texHeight) {
        // newTexId should not equal with texId. texId is from the SurfaceTexture.
        // Otherwise, there is no filter effect.
        int newTexId = mFBO.drawFrame(texId, texWidth, texHeight);
//        Log.i(TAG, "onDrawFrame texId:" + texId + ",newTexId:" + newTexId + ",texWidth:" + texWidth + ",texHeight:" + texHeight);
        return newTexId;
    }
    // 获取该信息，您需要实现 StreamStatusCallback
    @Override
    public void notifyStreamStatusChanged(final StreamStatus streamStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamStatus.setText("bitrate:" + streamStatus.totalAVBitrate / 1024 + " kbps"
                        + "\naudio:" + streamStatus.audioFps + " fps"
                        + "\nvideo:" + streamStatus.videoFps + " fps");
            }
        });
    }

    // 屏幕截图
//    private class Screenshooter implements Runnable {
//        @Override
//        public void run() {
//            final String fileName = "PLStreaming_" + System.currentTimeMillis() + ".jpg";
//            mCameraStreamingManager.captureFrame(0, 0, new FrameCapturedCallback() {
//                private Bitmap bitmap;
//
//                @Override
//                public void onFrameCaptured(Bitmap bmp) {
//                    bitmap = bmp;
//                    new Thread(new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                saveToSDCard(fileName, bitmap);
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            } finally {
//                                if (bitmap != null) {
//                                    bitmap.recycle();
//                                    bitmap = null;
//                                }
//                            }
//                        }
//                    }).start();
//                }
//            });
//        }
//    }

//    public void saveToSDCard(String filename, Bitmap bmp) throws IOException {
//        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
//            File file = new File(Environment.getExternalStorageDirectory(), filename);
//            BufferedOutputStream bos = null;
//            try {
//                bos = new BufferedOutputStream(new FileOutputStream(file));
//                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
//                bmp.recycle();
//                bmp = null;
//            } finally {
//                if (bos != null) bos.close();
//            }
//
//            final String info = "Save frame to:" + Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename;
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(mContext, info, Toast.LENGTH_LONG).show();
//                }
//            });
//        }
//    }
    // StreamingStateListener
    @Override
    public void onStateChanged(final int state, Object extra) {
        super.onStateChanged(state, extra);
        switch (state) {
            // 摄像头切换
            case CameraStreamingManager.STATE.CAMERA_SWITCHED:
                mShutterButtonPressed = false;
                if (extra != null) {
                    Log.i(TAG, "current camera id:" + (Integer)extra);
                }
                Log.i(TAG, "camera switched");
                break;
            // 闪光灯
//            case CameraStreamingManager.STATE.TORCH_INFO:
//                if (extra != null) {
//                    final boolean isSupportedTorch = (Boolean) extra;
//                    Log.i(TAG, "isSupportedTorch=" + isSupportedTorch);
//                    this.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            if (isSupportedTorch) {
//                                mTorchBtn.setVisibility(View.VISIBLE);
//                            } else {
//                                mTorchBtn.setVisibility(View.GONE);
//                            }
//                        }
//                    });
//                }
//                break;
        }
    }
    // 自适应码率
    @Override
    public boolean onStateHandled(final int state, Object extra) {
        super.onStateHandled(state, extra);
        switch (state) {
            case CameraStreamingManager.STATE.SENDING_BUFFER_HAS_FEW_ITEMS:
                mProfile.improveVideoQuality(1);
                mCameraStreamingManager.notifyProfileChanged(mProfile);
                return true;
            case CameraStreamingManager.STATE.SENDING_BUFFER_HAS_MANY_ITEMS:
                mProfile.reduceVideoQuality(1);
                mCameraStreamingManager.notifyProfileChanged(mProfile);
                return true;
        }
        return false;
    }
}
