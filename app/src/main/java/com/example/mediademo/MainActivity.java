package com.example.mediademo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;


public class MainActivity extends AppCompatActivity{
    private static final String LOG_TAG = "Demo_MainActivity";
    private Button mBtnPlay;
    private RadioButton mRadioBtnVod;
    private String mVodPath = "/storage/emulated/0/Movies/cd1.mp4";
    private String mLivePath = "http://hw-m-l.cztv.com/channels/lantian/channel010/1080p.m3u8";
    // https://live.fanmingming.com/
    // https://fanmingming.com/txt?url=https://live.fanmingming.com/tv/m3u/ipv6.m3u
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRadioBtnVod = findViewById(R.id.radio_vod);
        mBtnPlay = (Button) findViewById(R.id.btn_test);
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                if (mRadioBtnVod.isChecked()) {
                    intent.putExtra("uri", mVodPath);
                    intent.putExtra("isVod", true);
                }
                else {
                    intent.putExtra("uri", mLivePath);
                    intent.putExtra("isVod", false);
                }

                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(LOG_TAG, "keyCode = " + keyCode);
        super.onKeyDown(keyCode, event);
        return true;
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

}