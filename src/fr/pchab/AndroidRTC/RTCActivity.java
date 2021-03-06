package fr.pchab.AndroidRTC;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;
import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.util.List;

public class RTCActivity extends Activity implements WebRtcClient.RTCListener {
  private final static int VIDEO_CALL_SENT = 666;
  private static final String VIDEO_CODEC_VP9 = "VP9";
  private static final String AUDIO_CODEC_OPUS = "opus";
  // Local preview screen position before call is connected.
  private static final int LOCAL_X_CONNECTING = 0;
  private static final int LOCAL_Y_CONNECTING = 0;
  private static final int LOCAL_WIDTH_CONNECTING = 100;
  private static final int LOCAL_HEIGHT_CONNECTING = 100;
  // Local preview screen position after call is connected.
  private static final int LOCAL_X_CONNECTED = 72;
  private static final int LOCAL_Y_CONNECTED = 72;
  private static final int LOCAL_WIDTH_CONNECTED = 25;
  private static final int LOCAL_HEIGHT_CONNECTED = 25;
  // Remote video screen position
  private static final int REMOTE_X = 0;
  private static final int REMOTE_Y = 0;
  private static final int REMOTE_WIDTH = 100;
  private static final int REMOTE_HEIGHT = 100;
  private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
  private GLSurfaceView vsv;
  private VideoRenderer.Callbacks localRender;
  private VideoRenderer.Callbacks remoteRender;
  private WebRtcClient client;
  private String mSocketAddress;
  private String callerId;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.main);
    mSocketAddress = "http://" + getResources().getString(R.string.host);
    mSocketAddress += (":" + getResources().getString(R.string.port) + "/");

    vsv = (GLSurfaceView) findViewById(R.id.glview_call);
    vsv.setPreserveEGLContextOnPause(true);
    vsv.setKeepScreenOn(true);
    VideoRendererGui.setView(vsv, new Runnable() {
      @Override
      public void run() {
        init();
      }
    });

    // Camera display view
    remoteRender = VideoRendererGui.create(
            REMOTE_X, REMOTE_Y,
            REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
    localRender = VideoRendererGui.create(
            LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
            LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

    final Intent intent = getIntent();
    final String action = intent.getAction();

    if (Intent.ACTION_VIEW.equals(action)) {
      final List<String> segments = intent.getData().getPathSegments();
      callerId = segments.get(0);
    }
  }

  private void init() {
    Point displaySize = new Point();
    getWindowManager().getDefaultDisplay().getSize(displaySize);
    PeerConnectionParameters params = new PeerConnectionParameters(
            true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);
    PeerConnectionFactory.initializeAndroidGlobals(this, true, true,
            params.videoCodecHwAcceleration, VideoRendererGui.getEGLContext());
    client = new WebRtcClient(this, mSocketAddress);
  }

  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
  }

  @Override
  public void onPause() {
    super.onPause();
    vsv.onPause();
    if(client != null) {
      client.stopVideoSource();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    vsv.onResume();
    if(client != null) {
      client.restartVideoSource();
    }
  }

  @Override
  public void onCallReady(String callId) {
    if (callerId != null) {
      try {
        answer(callerId);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    } else {
      call(callId);
    }
  }

  public void answer(String callerId) throws JSONException {
    client.sendMessage(callerId, "init", null);
    startCam();
  }

  public void call(String callId) {
    Intent msg = new Intent(Intent.ACTION_SEND);
    msg.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId);
    msg.setType("text/plain");
    startActivityForResult(Intent.createChooser(msg, "Call someone :"), VIDEO_CALL_SENT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == VIDEO_CALL_SENT) {
      startCam();
    }
  }

  public void startCam() {
    // Camera settings
    client.setCamera("640", "480");
    client.start("android_test");
  }

  @Override
  public void onStatusChanged(final String newStatus) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onLocalStream(MediaStream localStream) {
    localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
    VideoRendererGui.update(localRender,
            LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
            LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
            VideoRendererGui.ScalingType.SCALE_ASPECT_FIT);
  }

  @Override
  public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
    remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
    VideoRendererGui.update(remoteRender,
            REMOTE_X, REMOTE_Y,
            REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
  }

  @Override
  public void onRemoveRemoteStream(MediaStream remoteStream, int endPoint) {
    VideoRendererGui.remove(remoteRender);
  }
}