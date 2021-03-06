package com.tinnvec.dctvandroid;

import android.os.Bundle;

import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import com.google.android.youtube.player.YouTubeInitializationResult;

import java.security.Provider;
import java.util.Properties;


/**
 * Created by kev on 5/23/16.
 */
public class YoutubeStreamActivity extends YouTubeBaseActivity implements YouTubePlayer.OnInitializedListener {

    private Properties appConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PropertyReader pReader = new PropertyReader(this);
        appConfig = pReader.getMyProperties("app.properties");

        setContentView(R.layout.activity_youtube_stream);

        YouTubePlayerView youTubeView = (YouTubePlayerView)
                findViewById(R.id.yt_view);
        String key = appConfig.getProperty("api.youtube.api_key");
        youTubeView.initialize(key, this);
    }

    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider,
                                        YouTubePlayer player, boolean wasRestored) {
        if (!wasRestored) player.cueVideo("XSMOykMIO3c"); // your video to play
    }
    @Override
    public void onInitializationFailure(YouTubePlayer.Provider arg0,
                                        YouTubeInitializationResult arg1){
    }
}
