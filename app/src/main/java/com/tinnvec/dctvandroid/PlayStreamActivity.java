package com.tinnvec.dctvandroid;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.squareup.picasso.Picasso;
import com.tinnvec.dctvandroid.channel.AbstractChannel;
import com.tinnvec.dctvandroid.channel.DctvChannel;
import com.tinnvec.dctvandroid.channel.Quality;
import com.tinnvec.dctvandroid.channel.YoutubeChannel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.Vitamio;
import io.vov.vitamio.widget.VideoView;

import static android.R.drawable.ic_menu_sort_by_size;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static com.tinnvec.dctvandroid.PlayStreamActivity.PlaybackState.PLAYING;

public class PlayStreamActivity extends AppCompatActivity {
    private static final String TAG = PlayStreamActivity.class.getName();
    private final Handler mHandler = new Handler();
    private ProgressDialog progressDialog;
    private VideoView vidView;
    private String streamUrl;
    //converted to global for interaction with cast methods
    private AbstractChannel channel;
    // added for cast SDK v3
    private CastContext mCastContext;
    private MenuItem mediaRouteMenuItem;
    private CastSession mCastSession;
    private SessionManagerListener<CastSession> mSessionManagerListener;
    private PlaybackLocation mLocation;
    private PlaybackState mPlaybackState;
    private MediaPlayer mediaPlayer;
    private ImageButton mPlayPause;
    private RelativeLayout mLoading;
    private View mControllers;
    private boolean mControllersVisible;
    private Timer mControllersTimer;
    private Properties appConfig;
    private Quality currentQuality;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Vitamio.isInitialized(getApplicationContext());

        channel = getIntent().getExtras().getParcelable(LiveChannelsActivity.CHANNEL_DATA);
        if (channel == null)
            throw new NullPointerException("No Channel passed to PlayStreamActivity");

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PropertyReader pReader = new PropertyReader(this);
        appConfig = pReader.getMyProperties("app.properties");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        setContentView(R.layout.activity_play_stream);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        currentQuality = Quality.valueOf(sharedPreferences.getString("stream_quality", "high").toUpperCase());
        this.streamUrl = channel.getStreamUrl(appConfig, currentQuality);

        // for cast SDK v3
//        setupControlsCallbacks();
        setupCastListener();
        mCastContext = CastContext.getSharedInstance(this);
        mCastContext.registerLifecycleCallbacksBeforeIceCreamSandwich(this, savedInstanceState);
        mCastSession = mCastContext.getSessionManager().getCurrentCastSession();

        String title = channel.getFriendlyAlias();
        title = title != null ? title : "Unknown";

        ImageView channelArtView = (ImageView) findViewById(R.id.channelart);
        String urlChannelart = channel.getImageAssetHDUrl();

        if (urlChannelart != null) {
            Picasso.with(this)
                    .load(urlChannelart)
                    .into(channelArtView);
        } else {
            Drawable defaultArt = ResourcesCompat.getDrawable(getResources(), R.drawable.dctv_bg, null);
            channelArtView.setImageDrawable(defaultArt);
        }


   /*         Bitmap resizedBitmap = channel.getImageBitmap(this);
            Drawable smallerArt = new BitmapDrawable(getResources(), resizedBitmap);
            toolbar.setLogo(smallerArt);
            toolbar.setLogoDescription(R.string.channel_art_description);


 */

        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setTitle(title);
        }

        actionbar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00000000")));
        actionbar.setElevation(0);

/*        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(title);
        progressDialog.setMessage("Loading...");
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
*/
        vidView = (VideoView) findViewById(R.id.video_view);
//        vidView.setOnInfoListener(this);
        //       vidView.setOnPreparedListener(this);
//        vidView.setOnErrorListener(this);
        mPlayPause = (ImageButton) findViewById(R.id.play_pause_button);
        mLoading = (RelativeLayout) findViewById(R.id.buffer_circle);
        mControllers = findViewById(R.id.mediacontroller_anchor);

        setupControlsCallbacks();

/*        MediaController mediaController = new MediaController(vidView.getContext());
        mediaController.setAnchorView(findViewById(R.id.mediacontroller_anchor));
        vidView.setMediaController(mediaController);
*/
        WebView chatWebview = (WebView) findViewById(R.id.chat_webview);
        WebSettings settings = chatWebview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        String url = appConfig.getProperty("irc.web_url");
        String nick = "";
        try {
            nick = URLEncoder.encode(sharedPreferences.getString("chat_name", ""), "UTF-8");
        } catch (UnsupportedEncodingException e) {

        }
        url = url + "?nick=" + nick;
        chatWebview.loadUrl(url);

        try {
            vidView.setVideoPath(streamUrl);
            Log.d(TAG, "Setting url of the VideoView to: " + streamUrl);
            mPlaybackState = PLAYING;
            updatePlayButton(mPlaybackState);
            if (mCastSession != null && mCastSession.isConnected()) {
                updatePlaybackLocation(PlaybackLocation.REMOTE);
                RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
                if (remoteMediaClient.getMediaInfo() == null)
                {
                    loadRemoteMedia(true);
                }
                else if (remoteMediaClient.getMediaInfo() != null){
                    if (!remoteMediaClient.getMediaInfo().getMetadata().getString(MediaMetadata.KEY_TITLE).equals(channel.getFriendlyAlias())) {
                        loadRemoteMedia(true);
                    }
                }
            } else {
                updatePlaybackLocation(PlaybackLocation.LOCAL);
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            throw e;
        }
        if (mLocation == PlaybackLocation.LOCAL) {
            if (getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
                hideSysUi();
            } else if (getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
                showSysUi();
            }
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

                if (mPlaybackState == PLAYING) {
                    loadRemoteMedia(true);
                    vidView.stopPlayback();
                    updatePlaybackLocation(PlaybackLocation.REMOTE);
                    mPlaybackState = PlaybackState.IDLE;
//                        mediaPlayer.stop();
//                        finish();
                    return;
                } else {
                    mPlaybackState = PlaybackState.IDLE;
                    updatePlaybackLocation(PlaybackLocation.REMOTE);
                }

                updatePlayButton(mPlaybackState);
                invalidateOptionsMenu();
            }

            private void onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL);
                mPlaybackState = PlaybackState.IDLE;
                mLocation = PlaybackLocation.LOCAL;
                updatePlayButton(mPlaybackState);
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

    // building MediaInfo package to pass to chromecast and its logic on the phone

    private MediaInfo buildMediaInfo() {
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

        movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, channel.getName());
        movieMetadata.putString(MediaMetadata.KEY_TITLE, channel.getFriendlyAlias());
        if (channel.getImageAssetHDUrl() != null)
            movieMetadata.addImage(new WebImage(Uri.parse(channel.getImageAssetHDUrl())));
        if (channel.getImageAssetUrl() != null)
            movieMetadata.addImage(new WebImage(Uri.parse(channel.getImageAssetUrl())));

        String resolvedStreamUrl = "";
        try {
            resolvedStreamUrl = channel.getResolvedStreamUrl(streamUrl);
        } catch (InterruptedException | ExecutionException ex) {
            Log.e(TAG, "Exception when trying to get full Stream URL", ex);
        }

        Log.d(TAG, "Passing this url to ChromeCast: " + resolvedStreamUrl);

        return new MediaInfo.Builder(resolvedStreamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType("videos/m3u8")
                .setMetadata(movieMetadata)
//                .setStreamDuration(mSelectedMedia.getDuration() * 1000) // not needed
                .build();
    }

    private void updatePlaybackLocation(PlaybackLocation location) {
        mLocation = location;
        if (location == PlaybackLocation.LOCAL) {
            showVideoView();
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00000000")));
            findViewById(R.id.actionbarspacer).setVisibility(View.GONE);

            RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            p.addRule(RelativeLayout.BELOW, R.id.view_group_video);

            findViewById(R.id.chat_webview).setLayoutParams(p);

            //               setCoverArtStatus(null);
            if (mPlaybackState == PLAYING
                    || mPlaybackState == PlaybackState.BUFFERING) {

                startControllersTimer();
            } else {

                stopControllersTimer();
//                setCoverArtStatus(mSelectedMedia.getImage(0));
            }
        } else {
            hideVideoView();
            showSysUi();
            getSupportActionBar().show();


            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#212121")));
            getSupportActionBar().setElevation(getResources().getDimensionPixelSize(R.dimen.actionbar_elevation));

            findViewById(R.id.actionbarspacer).setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            p.addRule(RelativeLayout.BELOW, R.id.actionbarspacer);

            findViewById(R.id.chat_webview).setLayoutParams(p);


            stopControllersTimer();
//            setCoverArtStatus(mSelectedMedia.getImage(0));
            updateControllersVisibility(false);
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

        switch (id) {
            case R.id.action_settings:
                return true;
            case R.id.action_quality:
                String qualities[] = Quality.allAsStrings(channel.getAllowedQualities());

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Pick a stream quality");
                builder.setItems(qualities, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Quality newQuality = channel.getAllowedQualities()[which];
                        if (newQuality != currentQuality) {
                            currentQuality = newQuality;
                            videoQualityChanged();
                        }
                    }
                });
                builder.show();

                return true;
        }


        return super.onOptionsItemSelected(item);
    }

    private void setupControlsCallbacks() {
        vidView.setOnErrorListener(new io.vov.vitamio.MediaPlayer.OnErrorListener() {

            @SuppressLint("NewApi")
            @Override
            public boolean onError(io.vov.vitamio.MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "OnErrorListener.onError(): VideoView encountered an " +
                        "error, what: " + what + ", extra: " + extra);
                String msg = "";
                if (extra == android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                    msg = getString(R.string.video_error_media_load_timeout);
                } else if (what == android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    msg = getString(R.string.video_error_server_unaccessible);
                } else if (channel instanceof YoutubeChannel) {
                    msg = getString(R.string.video_error_youtube);
                } else {
                    msg = getString(R.string.video_error_unknown_error);
                }
                Context context = getApplicationContext();
                int duration = Toast.LENGTH_LONG;
                String text = "Error: " + msg;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                vidView.stopPlayback();
                mPlaybackState = PlaybackState.IDLE;
                updatePlayButton(mPlaybackState);
                return true;
            }
        });

        vidView.setOnPreparedListener(new io.vov.vitamio.MediaPlayer.OnPreparedListener() {

            @Override
            public void onPrepared(MediaPlayer mp) {
                if (mLocation == PlaybackLocation.LOCAL) {
                    vidView.requestFocus();
                    mp.start();
                    mPlaybackState = PLAYING;
                    updatePlayButton(mPlaybackState);
                }
                if (mLocation == PlaybackLocation.REMOTE) {
                    vidView.pause();
                    mp.stop();
                    showSysUi();
                    updatePlayButton(mPlaybackState);
                    if (mCastSession != null && mCastSession.isConnected()) loadRemoteMedia(true);
                }
            }
        });

        vidView.setOnInfoListener(new io.vov.vitamio.MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (mLocation == PlaybackLocation.LOCAL) {
                    switch (what) {
                        case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START:
                            if (mp.isPlaying()) {
                                mp.pause();
                            }
                            mPlaybackState = PlaybackState.BUFFERING;
                            updatePlayButton(mPlaybackState);
                            updateControllersVisibility(true);
                            ImageView channelart = (ImageView) findViewById(R.id.channelart);
                            channelart.setVisibility(View.VISIBLE);
                            break;
                        case android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END:
                            mLocation = PlaybackLocation.LOCAL;
                            mp.start();
                            mPlaybackState = PLAYING;
                            updatePlayButton(mPlaybackState);
                            channelart = (ImageView) findViewById(R.id.channelart);
                            channelart.setVisibility(View.GONE);
                            startControllersTimer();
                            break;
                    }
                }

                return true;
                //           return false;
            }
        });

        vidView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mControllersVisible) {
                    updateControllersVisibility(true);
                }
                if (mPlaybackState == PLAYING) {
                    startControllersTimer();
                }
                return false;
            }
        });


        mPlayPause.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                togglePlayback();
            }
        });
    }

    private void updatePlayButton(PlaybackState state) {
        switch (state) {
            case PLAYING:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(
                        ResourcesCompat.getDrawable(getResources(), R.drawable.big_pause_button, null));
                break;
            case PAUSED:
            case IDLE:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(
                        ResourcesCompat.getDrawable(getResources(), R.drawable.big_play_button, null));
                break;
            case BUFFERING:
                mPlayPause.setVisibility(View.INVISIBLE);
                mLoading.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    private void videoQualityChanged() {
        this.streamUrl = channel.getStreamUrl(appConfig, currentQuality);
        if (mLocation == PlaybackLocation.LOCAL) {
            vidView.setVideoPath(this.streamUrl);
        } else if (mCastSession != null && mCastSession.isConnected()) {
            // reload chromecast
            loadRemoteMedia(true);
        }
    }

    private void togglePlayback() {
        stopControllersTimer();
        switch (mPlaybackState) {
            case PAUSED:
                switch (mLocation) {
                    case LOCAL:
                        vidView.start();
                        mPlaybackState = PLAYING;
                        startControllersTimer();
                        break;
                    case REMOTE:
                        break;
                    default:
                        break;
                }
                break;

            case PLAYING:
                mPlaybackState = PlaybackState.PAUSED;
                vidView.pause();
                break;

            case IDLE:
                vidView.setVideoPath(streamUrl);
                vidView.start();
                mPlaybackState = PLAYING;
                break;

            default:
                break;
        }
        updatePlayButton(mPlaybackState);
    }

    private void stopControllersTimer() {
        if (null != mControllersTimer) {
            mControllersTimer.cancel();
        }
    }

    private void startControllersTimer() {
        if (null != mControllersTimer) {
            mControllersTimer.cancel();
        }
        if (mLocation == PlaybackLocation.REMOTE) {
            return;
        }
        mControllersTimer = new Timer();
        mControllersTimer.schedule(new HideControllersTask(), 3000);
    }

    // should be called from the main thread
    private void updateControllersVisibility(boolean show) {
        if (show) {
            getSupportActionBar().show();
            mControllers.setVisibility(View.VISIBLE);
        } else if (mLocation == PlaybackLocation.LOCAL) {
            getSupportActionBar().hide();
            mControllers.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() was called");
        if (mLocation == PlaybackLocation.LOCAL) {
            if (mControllersTimer != null) {
                mControllersTimer.cancel();
            }
            // since we are playing locally, we need to stop the playback of
            // video (if user is not watching, pause it!)
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
        } else if (this.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT && mLocation == PlaybackLocation.LOCAL) {
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

    public void hideSysUi() {


        View decorView = getWindow().getDecorView();
// Hide both the navigation bar and the status bar.
// SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
// a general rule, you should design your app to hide the status bar whenever you
// hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                //    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
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

    public void showSysUi() {

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
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

    private class HideControllersTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateControllersVisibility(false);
                    mControllersVisible = false;
                }
            });

        }
    }
}
