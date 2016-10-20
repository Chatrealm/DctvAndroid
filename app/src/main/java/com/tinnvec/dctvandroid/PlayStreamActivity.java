package com.tinnvec.dctvandroid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.MediaPlayer.OnErrorListener;
import io.vov.vitamio.MediaPlayer.OnPreparedListener;
import io.vov.vitamio.Vitamio;
import io.vov.vitamio.widget.MediaController;
import io.vov.vitamio.widget.VideoView;

import static android.R.attr.screenOrientation;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class PlayStreamActivity extends AppCompatActivity
        implements OnPreparedListener, OnErrorListener, MediaPlayer.OnInfoListener {
    private static final String TAG = PlayStreamActivity.class.getName();
    private ProgressDialog progressDialog;
    private VideoView vidView;
    private String dctvBaseUrl;
    //converted to global for interaction with cast methods
    private DctvChannel channel;

    // added for cast SDK v3
    private CastContext mCastContext;
    private MenuItem mediaRouteMenuItem;
    private CastSession mCastSession;
    private SessionManagerListener<CastSession> mSessionManagerListener;
    private PlaybackLocation mLocation;
    private PlaybackState mPlaybackState;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Vitamio.isInitialized(getApplicationContext());

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        this.dctvBaseUrl = getString(R.string.dctv_base_url);

        setContentView(R.layout.activity_play_stream);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        // for cast SDK v3
//        setupControlsCallbacks();
        setupCastListener();
        mCastContext = CastContext.getSharedInstance(this);
        mCastContext.registerLifecycleCallbacksBeforeIceCreamSandwich(this, savedInstanceState);
        mCastSession = mCastContext.getSessionManager().getCurrentCastSession();

        channel = getIntent().getExtras().getParcelable(LiveChannelsActivity.CHANNEL_DATA);
        String title;
        if (channel != null) {
            title = channel.friendlyalias;

            Bitmap resizedBitmap = channel.getImageBitmap(this);
            Drawable smallerArt = new BitmapDrawable(getResources(), resizedBitmap);
            toolbar.setLogo(smallerArt);
            toolbar.setLogoDescription(R.string.channel_art_description);
        } else {
            title = "Unknown";
        }

        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setTitle(title);
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(title);
        progressDialog.setMessage("Loading...");
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);

        vidView = (VideoView) findViewById(R.id.video_view);
        vidView.setOnInfoListener(this);
        vidView.setOnPreparedListener(this);
        vidView.setOnErrorListener(this);

        MediaController mediaController = new MediaController(vidView.getContext());
        mediaController.setAnchorView(findViewById(R.id.mediacontroller_anchor));
        vidView.setMediaController(mediaController);

        WebView chatWebview = (WebView) findViewById(R.id.chat_webview);
        WebSettings settings = chatWebview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        chatWebview.loadUrl("http://irc.chatrealm.net");

        try {
            if (channel != null) {
                vidView.setVideoPath(channel.getStreamUrl(this));
                Log.d(TAG, "Setting url of the VideoView to: " + channel.getStreamUrl(this));
                mPlaybackState = PlaybackState.PLAYING;
                if (mCastSession != null && mCastSession.isConnected()) {
                    updatePlaybackLocation(PlaybackLocation.REMOTE);
                    loadRemoteMedia(true);
//                    vidView.pause();
                } else {
                    updatePlaybackLocation(PlaybackLocation.LOCAL);
                    vidView.setVideoPath(channel.getStreamUrl(this));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        if (this.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE && mLocation == PlaybackLocation.LOCAL) {
            hideSysUi();
        }   else if(this.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT && mLocation == PlaybackLocation.LOCAL) {
            showSysUi();
        }
    }

    //sets up a listener for cast events
    private void setupCastListener() {
        mSessionManagerListener = new SessionManagerListener<CastSession>() {

            @Override
            public void onSessionEnded(CastSession session, int error) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionResumed(CastSession session, boolean wasSuspended) {
                onApplicationConnected(session);
            }

            @Override
            public void onSessionResumeFailed(CastSession session, int error) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionStarted(CastSession session, String sessionId) {
                onApplicationConnected(session);
            }

            @Override
            public void onSessionStartFailed(CastSession session, int error) {
                onApplicationDisconnected();
            }

            @Override
            public void onSessionStarting(CastSession session) {
            }

            @Override
            public void onSessionEnding(CastSession session) {
            }

            @Override
            public void onSessionResuming(CastSession session, String sessionId) {
            }

            @Override
            public void onSessionSuspended(CastSession session, int reason) {
            }

            private void onApplicationConnected(CastSession castSession) {
                mCastSession = castSession;
                if (null != channel) {

                    if (mPlaybackState == PlaybackState.PLAYING) {
                        vidView.pause();
//                        mediaPlayer.stop();
                        loadRemoteMedia(true);
                        finish();
                        return;
                    } else {
                        mPlaybackState = PlaybackState.IDLE;
                        updatePlaybackLocation(PlaybackLocation.REMOTE);
                    }
                }
//                updatePlayButton(mPlaybackState);
                invalidateOptionsMenu();
            }

            private void onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL);
                mPlaybackState = PlaybackState.IDLE;
                mLocation = PlaybackLocation.LOCAL;
//                updatePlayButton(mPlaybackState);
                invalidateOptionsMenu();
            }
        };
    }

    // loads channel to cast device
    private void loadRemoteMedia(boolean autoPlay) {
        if (mCastSession == null) {
            return;
        }
        RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        remoteMediaClient.load(buildMediaInfo(), autoPlay);
    }

    private MediaInfo buildMediaInfo() {
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

        movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, channel.getChannelname());
        movieMetadata.putString(MediaMetadata.KEY_TITLE, channel.getFriendlyAlias());
        movieMetadata.addImage(new WebImage(Uri.parse(channel.getBigChannelArtUrl())));
        movieMetadata.addImage(new WebImage(Uri.parse(channel.getChannelArtUrl())));

        String streamUrl = null;
        if (channel.getChannelname().equals("dctv")) {
            streamUrl = channel.getStreamUrl(this);
        } else {
            if (!channel.getChannelname().equals("dctv") && channel.getStreamtype().equals("rtmp-hls")) {
                if (channel.getChannelname().equals("frogpantsstudios") && channel.getStreamtype().equals("rtmp-hls")) {
                    streamUrl = "http://ingest.diamondclub.tv/high/" + "scottjohnson" + ".m3u8";
                }
                if (channel.getChannelname().equals("sgtmuffin") && channel.getStreamtype().equals("rtmp-hls")) {
                    streamUrl = "http://ingest.diamondclub.tv/high/" + "muffin" + ".m3u8";
                } else {
                    streamUrl = "http://ingest.diamondclub.tv/high/" + channel.getChannelname() + ".m3u8";
                }
            } else {
                Context context = getApplicationContext();
                CharSequence text = "Sorry, we can't cast this stream for now. Maybe the 24/7 channel shows your show?";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                updatePlaybackLocation(PlaybackLocation.LOCAL);

                return null;
            }
        }
        Log.d(TAG, "Passing this url to cast: " + streamUrl);

        return new MediaInfo.Builder(streamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType("videos/m3u8")
                .setMetadata(movieMetadata)
//                .setStreamDuration(mSelectedMedia.getDuration() * 1000) // not needed
                .build();
    }

    private void updatePlaybackLocation(PlaybackLocation location) {
        mLocation = location;
        if (location == PlaybackLocation.LOCAL) {
            if (mPlaybackState == PlaybackState.PLAYING
                    || mPlaybackState == PlaybackState.BUFFERING) {
                showVideoView();
                //               setCoverArtStatus(null);
                //               startControllersTimer();
            } else {

//                stopControllersTimer();
//                setCoverArtStatus(mSelectedMedia.getImage(0));
            }
        } else {
            hideVideoView();
            showSysUi();
//            stopControllersTimer();
//            setCoverArtStatus(mSelectedMedia.getImage(0));
//            updateControllersVisibility(false);
        }
    }

    private void hideVideoView() {
        if (findViewById(R.id.view_group_video).getVisibility() == View.VISIBLE) {
            findViewById(R.id.view_group_video).setVisibility(View.GONE);
        }
    }

    private void showVideoView() {
        if (findViewById(R.id.view_group_video).getVisibility() != View.VISIBLE) {
            findViewById(R.id.view_group_video).setVisibility(View.VISIBLE);
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_play_stream, menu);

        // add media router button for cast
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.stop();
        mPlaybackState = PlaybackState.IDLE;
        progressDialog.dismiss();
//        startActivity(new Intent(getBaseContext(), LiveChannelsActivity.class));
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (mLocation == PlaybackLocation.LOCAL) {
            switch (what) {
                case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    if (mp.isPlaying()) {
                        mp.pause();
                    }
                    progressDialog.setMessage("Buffering...");
                    progressDialog.show();
                    mPlaybackState = PlaybackState.BUFFERING;
                    break;
                case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    progressDialog.dismiss();
                    mLocation = PlaybackLocation.LOCAL;
                    mp.start();
                    mPlaybackState = PlaybackState.PLAYING;

                    break;
            }
        }

        return true;
//        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mLocation == PlaybackLocation.LOCAL) {
            progressDialog.dismiss();
            vidView.requestFocus();
            mp.start();
            mPlaybackState = PlaybackState.PLAYING;
        }
        if (mLocation == PlaybackLocation.REMOTE) {
            vidView.pause();
            mp.stop();
            showSysUi();
            if (mCastSession != null && mCastSession.isConnected()) loadRemoteMedia(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() was called");
        if (mLocation == PlaybackLocation.LOCAL) {

/*            if (mSeekbarTimer != null) {
                mSeekbarTimer.cancel();
                mSeekbarTimer = null;
            }
            if (mControllersTimer != null) {
                mControllersTimer.cancel();
            }
            // since we are playing locally, we need to stop the playback of
*/            // video (if user is not watching, pause it!)
            vidView.pause();
            mPlaybackState = PlaybackState.PAUSED;
//           updatePlayButton(PlaybackState.PAUSED);
        }
        mCastContext.getSessionManager().removeSessionManagerListener(
                mSessionManagerListener, CastSession.class);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume() was called");
        mCastContext.getSessionManager().addSessionManagerListener(
                mSessionManagerListener, CastSession.class);
        if (mCastSession != null && mCastSession.isConnected()) {
            updatePlaybackLocation(PlaybackLocation.REMOTE);
        } else {
            updatePlaybackLocation(PlaybackLocation.LOCAL);
        }
        if (this.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE && mLocation == PlaybackLocation.LOCAL) {
            hideSysUi();
        }   else if(this.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT && mLocation == PlaybackLocation.LOCAL) {
            showSysUi();
        }
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Checks the orientation of the screen
        if (newConfig.orientation == ORIENTATION_LANDSCAPE && mLocation == PlaybackLocation.LOCAL) {
            hideSysUi();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && mLocation == PlaybackLocation.LOCAL) {
            showSysUi();
        }
    }

    public void hideSysUi(){
        getSupportActionBar().hide();

        View decorView = getWindow().getDecorView();
// Hide both the navigation bar and the status bar.
// SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
// a general rule, you should design your app to hide the status bar whenever you
// hide the navigation bar.
        int uiOptions =  View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            findViewById(R.id.root_coordinator).setFitsSystemWindows(false);
        }
        findViewById(R.id.view_group_video).setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        vidView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void showSysUi(){
        getSupportActionBar().show();
        View decorView = getWindow().getDecorView();
        int uiOptions =     View.SYSTEM_UI_FLAG_VISIBLE;
        decorView.setSystemUiVisibility(uiOptions);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            findViewById(R.id.root_coordinator).setFitsSystemWindows(true);
        }

        findViewById(R.id.view_group_video).setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // getting the videoview to be 16:9
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        float h = displaymetrics.heightPixels;
        float w = displaymetrics.widthPixels;
        float floatHeight = (float) (w * 0.5625);
        int intHeight = Math.round(floatHeight);
        int intWidth = (int) w;
        vidView.setLayoutParams(new FrameLayout.LayoutParams(intWidth, intHeight));
    }

    /**
     * indicates whether we are doing a local or a remote playback
     */
    public enum PlaybackLocation {
        LOCAL,
        REMOTE
    }

    /**
     * List of various states that we can be in
     */
    public enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }
}
