package com.example.mediademo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;

public class VideoActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String LOG_TAG = "Demo_VideoActivity";

    private FrameLayout     mMainFrameLayout;
    private FrameLayout     mControlLayout;
    private SurfaceView     mSurface;
    private Button          mBtnBack;
    private Button          mBtnPlay;
    private Button          mBtnFullScreen;
    private TextView        mTxtViewCurTime;
    private TextView        mTxtViewDuration;
    private ProgressBar     mProgressBar;
    private SeekBar         mSeekBar;

    private String          mUri;
    private boolean         mIsVod;

    private HandlerThread   mPlaybackThread;
    private Handler         mPlaybackHandler;

    private Handler         mUiHandler;

    private MediaPlayer     mMediaPlayer;

    private int mStatus;
    private static final int STATE_IDLE = 1;
    private static final int STATE_PLAYING = 1 << 1;
    private static final int STATE_PAUSED = 1 << 2;
    private static final int STATE_Completed = 1 << 3;
    private static final int STATE_STOPPED = 1 << 4;

    private boolean mIsFullScreen;
    private static final int kWhatStart = 0;
    private static final int kWhatBack = 1;
    private static final int kWhatScreenFull = 2;
    private static final int kWhatRelease = 3;

    private Thread           mCurrentTimeStampThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        initView();
        initData();
        startPlay();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
        if (mStatus != STATE_IDLE)
            startPlay();
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
        if (mStatus != STATE_IDLE)
            startPlay();
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.MainFrameLayout: {
                if (mControlLayout.getVisibility() == View.GONE)
                    mControlLayout.setVisibility(View.VISIBLE);
                break;
            }
            case R.id.ControlLayout: {
                if (mControlLayout.getVisibility() == View.VISIBLE)
                    mControlLayout.setVisibility(View.GONE);
                break;
            }
            case R.id.btn_play: {
                startPlay();
                break;
            }
            case R.id.btn_back: {
                back();
                break;
            }
            case R.id.btn_full: {
                screenFull();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(LOG_TAG, "onKeyDown");
        super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (mIsFullScreen) {
                    // Qs: 把screenFull注释掉之后，会直接退出Activity
                    //    把注释打开就不会退出Activity，也不需要return？
                    screenFull();
                    return true;    // 终止KEYCODE_BACK
                } else {
                    reset();
                }
                break;
            }
        }
        return true;
    }

    private void initView() {
        Log.d(LOG_TAG, "initView");
        mMainFrameLayout    = (FrameLayout) findViewById(R.id.MainFrameLayout);
        mControlLayout      = (FrameLayout) findViewById(R.id.ControlLayout);
        mSurface            = (SurfaceView) findViewById(R.id.surfaceView);
        mBtnBack            = (Button)      findViewById(R.id.btn_back);
        mBtnPlay            = (Button)      findViewById(R.id.btn_play);
        mBtnFullScreen      = (Button)      findViewById(R.id.btn_full);
        mTxtViewCurTime     = (TextView)    findViewById(R.id.textCurrentPos);
        mTxtViewDuration    = (TextView)    findViewById(R.id.textDuration);
        mProgressBar        = (ProgressBar) findViewById(R.id.progressBar);
        mSeekBar            = (SeekBar)     findViewById(R.id.seekBar);

        mMainFrameLayout.setOnClickListener(this);
        mControlLayout.setOnClickListener(this);
        mBtnBack.setOnClickListener(this);
        mBtnPlay.setOnClickListener(this);
        mBtnFullScreen.setOnClickListener(this);
        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener());
    }

    private void initData() {
        Log.d(LOG_TAG, "initData");
        Intent intent = getIntent();
        mUri = intent.getStringExtra("uri");
        mIsVod = intent.getBooleanExtra("isVod", true);
        mPlaybackThread = new HandlerThread("Playback Thread");
        mPlaybackThread.start();
        mPlaybackHandler = new PlaybackHandler(mPlaybackThread.getLooper());
        mUiHandler = new Handler(Looper.getMainLooper());
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(new OnPreparedListener());
        mMediaPlayer.setOnBufferingUpdateListener(new OnBufferingUpdateListener());
        mMediaPlayer.setOnInfoListener(new OnInfoListener());
        mMediaPlayer.setOnCompletionListener(new OnCompletionListener());
        mMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener());
        mSurface.getHolder().addCallback(new SurfaceCallback());
        mStatus = STATE_IDLE;
        mIsFullScreen = false;
        mCurrentTimeStampThread = new CurrentTimeStampThread();

        if (!mIsVod)
            mSeekBar.setEnabled(false);
    }

    private class PlaybackHandler extends Handler {
        public PlaybackHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case kWhatStart:
                    _startPlay();
                    break;
                case kWhatBack:
                    _back();
                    break;
                case kWhatScreenFull:
                    _screenFull();
                    break;
                case kWhatRelease:
                    _reset();
                default:
                    break;
            }
        }
    }

    private void startPlay() {
        Log.d(LOG_TAG, "startPlay");
        Message msg = Message.obtain();
        msg.what = kWhatStart;
        msg.setTarget(mPlaybackHandler);
        msg.sendToTarget();
    }

    private void _startPlay() {
        Log.d(LOG_TAG, "_startPlay");
        switch (mStatus) {
            case STATE_IDLE: {
                try {
                    mMediaPlayer.setDataSource(mUri);
                } catch (IOException e) {

                }
                mMediaPlayer.prepareAsync();
                break;
            }
            case STATE_PLAYING: {
                mMediaPlayer.pause();
                mStatus = STATE_PAUSED;
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBtnPlay.setBackground(getResources().getDrawable(R.drawable.icon_play, null));
                    }
                });
                break;
            }
            case STATE_PAUSED: {
                mMediaPlayer.start();
                mStatus = STATE_PLAYING;
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBtnPlay.setBackground(getResources().getDrawable(R.drawable.icon_topause, null));
                    }
                });
                break;
            }
            case STATE_STOPPED: {
                mMediaPlayer.prepareAsync();
                break;
            }
            default:
                break;
        }
    }

    private void back() {
        Log.d(LOG_TAG, "back");
        Message msg = Message.obtain();
        msg.what = kWhatBack;
        msg.setTarget(mPlaybackHandler);
        msg.sendToTarget();
    }

    private void _back() {
        Log.d(LOG_TAG, "_back");
        if (!mIsFullScreen) {
            _reset();
            this.finish();
        } else {
            screenFull();
        }
    }

    private void screenFull() {
        Log.d(LOG_TAG, "screenFull");
        Message msg = Message.obtain();
        msg.what = kWhatScreenFull;
        msg.setTarget(mPlaybackHandler);
        msg.sendToTarget();
    }


    public static int dp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    private void _screenFull() {
        Log.d(LOG_TAG, "_screenFull");
        if (!mIsFullScreen) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "set full");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    // 这里的LayoutParams需要是上一级的layout 还是 根layout的类型？
                    ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    mMainFrameLayout.setLayoutParams(params);
                    mIsFullScreen = true;
                }
            });
        } else {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp2px(VideoActivity.this, 250));
                    mMainFrameLayout.setLayoutParams(params);
                    mIsFullScreen = false;
                }
            });
        }
    }

    private void reset() {
        Log.d(LOG_TAG, "reset");
        Message msg = Message.obtain();
        msg.what = kWhatRelease;
        msg.setTarget(mPlaybackHandler);
        msg.sendToTarget();
    }

    // don't call async
    private void _reset() {
        Log.d(LOG_TAG, "_reset");
        mStatus = STATE_IDLE;
        // quit thread first
        if (mCurrentTimeStampThread.isAlive()) {
            try {
                mCurrentTimeStampThread.join();
            } catch (InterruptedException e) {

            }
        }
        mMediaPlayer.reset();
        mPlaybackThread.quitSafely();
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Log.d(LOG_TAG, "surfaceCreated");
            if (mStatus != STATE_IDLE)
                mMediaPlayer.setDisplay(mSurface.getHolder());
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            Log.d(LOG_TAG, "surfaceChanged");
            if (mStatus != STATE_IDLE)
                mMediaPlayer.setDisplay(mSurface.getHolder());
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            Log.d(LOG_TAG, "surfaceDestroyed");
        }
    }

    private static String time2Text(int time) {
        int seconds = (time / 1000) % 60;
        int minutes = ((time / 1000) / 60) % 60;
        int hours = minutes / 60;
        String durH = hours < 10? ("0"+hours) : String.valueOf(hours);
        String durM = minutes < 10? ("0"+minutes) : String.valueOf(minutes);
        String durS = seconds < 10? ("0"+seconds) : String.valueOf(seconds);
        String dur = durH + ":" + durM + ":" + durS;
        return dur;
    }

    private class CurrentTimeStampThread extends Thread {
        private static final String LOG_TAG = "Demo_VideoActivity";
        @Override
        public void run() {
            while (true) {
                if (mStatus == STATE_IDLE)
                    break;
                // when touch seekbar, not update
                // isPlaying use guard timestamp when onComplete
                if(!mSeekBar.isPressed() && mMediaPlayer.isPlaying()) {
                    int current = mMediaPlayer.getCurrentPosition();
                    Log.d(LOG_TAG, "Demo_VideoActivity getCurrentPosition = " + current);
                    if (current >= 0) {
                        String cur = time2Text(current);
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mTxtViewCurTime.setText(cur);
                                mSeekBar.setProgress(current / 1000);
                            }
                        });
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private class OnPreparedListener implements MediaPlayer.OnPreparedListener {
        @Override
        public void onPrepared(MediaPlayer mp) {
            Log.d(LOG_TAG, "onPrepared");
            mMediaPlayer.start();
            mMediaPlayer.setDisplay(mSurface.getHolder());
            mStatus = STATE_PLAYING;
            if (mIsVod) {
                mCurrentTimeStampThread.start();
                int duration = mMediaPlayer.getDuration();
                if (duration != -1) {
                    String dur = time2Text(duration);
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mTxtViewDuration.setText(dur);
                            mSeekBar.setMax(duration/1000);
                        }
                    });
                }
            }
        }
    }

    private class OnBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener {
        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            Log.d(LOG_TAG, "onBufferingUpdate");

        }
    }

    private class OnInfoListener implements MediaPlayer.OnInfoListener {
        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            Log.d(LOG_TAG, "onInfo what = " + what);
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mProgressBar.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mProgressBar.setVisibility(View.GONE);
                        }
                    });
                    break;
                default:
                    break;
            }
            return false;
        }
    }

    private class OnCompletionListener implements MediaPlayer.OnCompletionListener {

        @Override
        public void onCompletion(MediaPlayer mp) {
            Log.d(LOG_TAG, "onCompletion");
            // onCompletion actually is paused
            if (mStatus == STATE_PLAYING)
                startPlay();
        }
    }

    private class OnSeekCompleteListener implements MediaPlayer.OnSeekCompleteListener {

        @Override
        public void onSeekComplete(MediaPlayer mp) {
            Log.d(LOG_TAG, "onSeekComplete");
        }
    }

    private class OnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.d(LOG_TAG, "onProgressChanged progress = " + progress);
            if (seekBar.isPressed()) {
                String cur = time2Text(progress * 1000);
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTxtViewCurTime.setText(cur);
                    }
                });
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.d(LOG_TAG, "onStartTrackingTouch");
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int progress = seekBar.getProgress();
            Log.d(LOG_TAG, "onStopTrackingTouch progress " + progress);
            switch (mStatus) {
                case STATE_PLAYING:
                    mMediaPlayer.seekTo(progress * 1000);
                    break;
                case STATE_PAUSED:
                    mMediaPlayer.seekTo(progress * 1000);
                    startPlay();
                    break;
                default:
                    break;
            }
        }
    }
}