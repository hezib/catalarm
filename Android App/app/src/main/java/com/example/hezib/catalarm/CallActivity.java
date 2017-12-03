package com.example.hezib.catalarm;

import android.content.res.ColorStateList;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.RuntimeException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.example.hezib.catalarm.CatAlarmAudioManager.AudioDevice;
import com.example.hezib.catalarm.CatAlarmAudioManager.AudioManagerEvents;
import com.example.hezib.catalarm.Uv4lRTCClient.SignalingParameters;
import com.example.hezib.catalarm.PeerConnectionClient.PeerConnectionParameters;

import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoRenderer;

public class CallActivity extends AppCompatActivity implements Uv4lRTCClient.SignalingEvents,
        PeerConnectionClient.PeerConnectionEvents, CallFragment.OnCallEvents {
    private static final String TAG = CallActivity.class.getName();

    static {
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("boringssl.cr");
            System.loadLibrary("protobuf_lite.cr");
        } catch (UnsatisfiedLinkError e) {
            Logging.w(TAG, "Failed to load native dependencies: ", e);
        }
    }

    public static final String EXTRA_RPI_IP = "com.example.hezib.catalarm.SERVER";
    public static final String EXTRA_UV4L_PORT = "com.example.hezib.catalarm.UV4L_PORT";
    public static final String EXTRA_MOTION_PORT = "com.example.hezib.catalarm.MOTION_PORT";
    public static final String EXTRA_IDLE_SLEEP = "com.example.hezib.catalarm.IDLE_SLEEP";
    public static final String EXTRA_ACTIVE_SLEEP = "com.example.hezib.catalarm.ACTIVE_SLEEP";
    public static final String EXTRA_ALARM_SOUND = "com.example.hezib.catalarm.ALARM_SOUND";
    public static final String EXTRA_VIDEOCODEC = "com.example.hezib.catalarm.VIDEOCODEC";
    public static final String EXTRA_HWCODEC_ENABLED = "com.example.hezib.catalarm.HWCODEC";
    public static final String EXTRA_AUDIO_BITRATE = "com.example.hezib.catalarm.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "com.example.hezib.catalarm.AUDIOCODEC";
    public static final String EXTRA_OPENSLES_ENABLED = "com.example.hezib.catalarm.OPENSLES";
    public static final String EXTRA_DISABLE_BUILT_IN_AEC = "com.example.hezib.catalarm.DISABLE_BUILT_IN_AEC";
    public static final String EXTRA_DISABLE_BUILT_IN_AGC = "com.example.hezib.catalarm.DISABLE_BUILT_IN_AGC";
    public static final String EXTRA_DISABLE_BUILT_IN_NS = "com.example.hezib.catalarm.DISABLE_BUILT_IN_NS";
    public static final String EXTRA_ENABLE_LEVEL_CONTROL = "com.example.hezib.catalarm.ENABLE_LEVEL_CONTROL";
    public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF = "com.example.hezib.catalarm.DISABLE_WEBRTC_GAIN_CONTROL";

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    private static class ProxyRenderer implements VideoRenderer.Callbacks {
        private VideoRenderer.Callbacks target;

        @Override
        synchronized public void renderFrame(VideoRenderer.I420Frame frame) {
            if (target == null) {
                Logging.d(TAG, "Dropping frame in proxy because target is null.");
                VideoRenderer.renderFrameDone(frame);
                return;
            }

            target.renderFrame(frame);
        }

        synchronized public void setTarget(VideoRenderer.Callbacks target) {
            this.target = target;
        }
    }

    private final ProxyRenderer remoteProxyRenderer = new ProxyRenderer();
    private PeerConnectionClient peerConnectionClient = null;
    private MotionClient motionClient;
    private Uv4lRTCClient uv4lRtcClient;
    private SignalingParameters signalingParameters;
    private CatAlarmAudioManager audioManager = null;
    private SurfaceViewRenderer fullscreenRenderer;
    private VideoFileRenderer videoFileRenderer;
    private final List<VideoRenderer.Callbacks> remoteRenderers = new ArrayList<>();
    private Toast logToast;
    private boolean activityRunning;
    private Uv4lRTCClient.ConnectionParameters uv4lConnectionParameters;
    private PeerConnectionParameters peerConnectionParameters;
    private boolean iceConnected;
    private boolean isError;
    private boolean callControlFragmentVisible = true;
    private long callStartedTimeMs = 0;
    private boolean micEnabled = false;
    private FloatingActionButton micFab;
    private boolean isRec = false;

    private CallFragment callFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.activity_call);

        iceConnected = false;
        signalingParameters = null;

        fullscreenRenderer = (SurfaceViewRenderer) findViewById(R.id.fullscreen_video_view);
        callFragment = new CallFragment();
        micFab = (FloatingActionButton) findViewById(R.id.micFab);
        micFab.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        v.performClick();
                        micFab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
                        onToggleMic();
                        break;

                    case MotionEvent.ACTION_UP:
                        onToggleMic();
                        micFab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimaryDark)));
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });


        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCallControlFragmentVisibility();
            }
        };

        fullscreenRenderer.setOnClickListener(listener);
        remoteRenderers.add(remoteProxyRenderer);

        final Intent intent = getIntent();

        peerConnectionClient = new PeerConnectionClient();
        fullscreenRenderer.init(peerConnectionClient.getRenderContext(), null);
        fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);
        fullscreenRenderer.setEnableHardwareScaler(true /* enabled */);

        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission " + permission + " is not granted");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        peerConnectionParameters =
                new PeerConnectionParameters(
                        intent.getStringExtra(EXTRA_VIDEOCODEC),
                        intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
                        intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
                        intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
                        intent.getBooleanExtra(EXTRA_ENABLE_LEVEL_CONTROL, false),
                        intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false));

        String url = intent.getStringExtra(EXTRA_RPI_IP);
        Log.d(TAG, "Server URL: " + url);
        if (url == null || url.length() == 0) {
            logAndToast(getString(R.string.missing_url));
            Log.e(TAG, "Incorrect URL in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        String uv4lPort = intent.getStringExtra(EXTRA_UV4L_PORT);
        String motionPort = intent.getStringExtra(EXTRA_MOTION_PORT);
        Log.d(TAG, "UV4L port: " + uv4lPort + " Motion port: " + motionPort);
        if (uv4lPort == null || uv4lPort.length() == 0 || motionPort == null || motionPort.length() == 0) {
            logAndToast(getString(R.string.missing_port));
            Log.e(TAG, "Incorrect port in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        String idleSleep = intent.getStringExtra(EXTRA_IDLE_SLEEP);
        String activeSleep = intent.getStringExtra(EXTRA_ACTIVE_SLEEP);
        Log.d(TAG, "Idle sleep: " + idleSleep + " Active sleep: " + activeSleep);
        if (idleSleep == null || idleSleep.length() == 0 || activeSleep == null || activeSleep.length() == 0) {
            logAndToast(getString(R.string.missing_sleep));
            Log.e(TAG, "Incorrect port in intent!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        String alarmSound = intent.getStringExtra(EXTRA_ALARM_SOUND);
        Log.d(TAG, "Alarm sound: " + alarmSound);

        motionClient = new MotionClient(url, motionPort, idleSleep, activeSleep, alarmSound);
        uv4lRtcClient = new WebSocketRTCClient(this);
        uv4lConnectionParameters = new Uv4lRTCClient.ConnectionParameters(url, uv4lPort);
        callFragment.setArguments(intent.getExtras());
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.call_fragment_container, callFragment);
        ft.commit();

        peerConnectionClient.createPeerConnectionFactory(getApplicationContext(), peerConnectionParameters, CallActivity.this);
        peerConnectionClient.setAudioEnabled(micEnabled);

        startCall();
    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    @Override
    public void onStop() {
        super.onStop();
        activityRunning = false;
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        activityRunning = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.startVideoSource();
        }
    }

    @Override
    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        disconnect();
        if (logToast != null) {
            logToast.cancel();
        }
        activityRunning = false;
        super.onDestroy();
    }

    @Override
    public void onCallHangUp() {
        disconnect();
    }

    @Override
    public void onVideoScalingSwitch(ScalingType scalingType) {
        fullscreenRenderer.setScalingType(scalingType);
    }

    @Override
    public void onToggleRec() {
        if (!isRec) {
            String filename = "VID_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".mp4";
            String saveRemoteVideoToFile = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES).getAbsolutePath() + File.separator + filename;
            int videoOutWidth = 640;
            int videoOutHeight = 480;
            try {
                videoFileRenderer = new VideoFileRenderer(saveRemoteVideoToFile, videoOutWidth,
                        videoOutHeight, peerConnectionClient.getRenderContext());
                peerConnectionClient.addRenderer(videoFileRenderer);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to open video file for output: " + saveRemoteVideoToFile, e);
            }
            isRec = true;
        } else {
            if (videoFileRenderer != null) {
                peerConnectionClient.removeRenderer();
                videoFileRenderer.release();
                videoFileRenderer = null;
            }
            logAndToast("Video saved to \n" + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath());
            isRec = false;
        }

    }

    @Override
    public boolean onToggleMic() {
        if (peerConnectionClient != null) {
            micEnabled = !micEnabled;
            peerConnectionClient.setAudioEnabled(micEnabled);
        }
        return micEnabled;
    }

    private void toggleCallControlFragmentVisibility() {
        if (!iceConnected || !callFragment.isAdded()) {
            return;
        }
        callControlFragmentVisible = !callControlFragmentVisible;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (callControlFragmentVisible) {
            ft.show(callFragment);
        } else {
            ft.hide(callFragment);
        }
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    private void startCall() {
        if (uv4lRtcClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.");
            return;
        }
        callStartedTimeMs = System.currentTimeMillis();

        logAndToast(getString(R.string.connecting_to, uv4lConnectionParameters.url));
        motionClient.killMotionServer();
        uv4lRtcClient.connectToUv4l(uv4lConnectionParameters);
        audioManager = CatAlarmAudioManager.create(getApplicationContext());
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(new AudioManagerEvents() {
            @Override
            public void onAudioDeviceChanged(
                    AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
    }

    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(TAG, "Call connected: delay=" + delta + "ms");
        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state");
            return;
        }

        remoteProxyRenderer.setTarget(fullscreenRenderer);
        fullscreenRenderer.setMirror(false);
    }

    private void onAudioManagerDevicesChanged(
            final AudioDevice device, final Set<AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
    }

    private void disconnect() {
        activityRunning = false;
        remoteProxyRenderer.setTarget(null);
        if (uv4lRtcClient != null) {
            uv4lRtcClient.disconnectFromUv4l();
            uv4lRtcClient = null;
        }
        if(motionClient != null) {
            motionClient.startMotionServer();
            motionClient = null;
        }
        if (videoFileRenderer != null) {
            peerConnectionClient.removeRenderer();
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
        if (fullscreenRenderer != null) {
            fullscreenRenderer.release();
            fullscreenRenderer = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }
        if (iceConnected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (!activityRunning) {
            Log.e(TAG, "Critical error: " + errorMessage);
            disconnect();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getText(R.string.channel_error_title))
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            disconnect();
                        }
                    })
                    .create()
                    .show();
        }
    }

    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        logToast.show();
    }

    private void reportError(final String description) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    isError = true;
                    disconnectWithErrorMessage(description);
                }
            }
        });
    }

    @Override
    public void onConnectedToUv4l(final SignalingParameters params) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(params);
            }
        });
    }

    private void onConnectedToRoomInternal(final SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        logAndToast("Creating peer connection, delay=" + delta + "ms");

        peerConnectionClient.createPeerConnection(remoteRenderers, signalingParameters);
    }

    @Override
    public void onRemoteDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
                peerConnectionClient.setRemoteDescription(sdp);
                logAndToast("Creating ANSWER...");
                peerConnectionClient.createAnswer();
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        });
    }

    @Override
    public void onChannelClose() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("Remote end hung up; dropping PeerConnection");
                disconnect();
            }
        });
    }

    @Override
    public void onChannelError(final String description) {
        reportError(description);
    }

    @Override
    public void onLocalDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (uv4lRtcClient != null) {
                    logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");

                    uv4lRtcClient.sendAnswerSdp(sdp);
                    uv4lRtcClient.requestIceCandidates();

                }
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (uv4lRtcClient != null) {
                    uv4lRtcClient.sendLocalIceCandidate(candidate);
                }
            }
        });
    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE connected, delay=" + delta + "ms");
                iceConnected = true;
                callConnected();
            }
        });
    }

    @Override
    public void onIceDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logAndToast("ICE disconnected");
                iceConnected = false;
                disconnect();
            }
        });
    }

    @Override
    public void onPeerConnectionClosed() {}

    @Override
    public void onPeerConnectionError(final String description) {
        reportError(description);
    }
}

