package com.tinnvec.dctvandroid;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.transition.Explode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.tinnvec.dctvandroid.adapters.ChannelListAdapter;
import com.tinnvec.dctvandroid.adapters.ChannelListCallback;
import com.tinnvec.dctvandroid.channel.AbstractChannel;

import java.util.Properties;

public class LiveChannelsActivity extends AppCompatActivity implements ChannelListCallback {

    public static final String CHANNEL_DATA = "com.tinnvec.dctv_android.CHANNEL_MESSAGE";
    private static final String TAG = LiveChannelsActivity.class.getName();
    private Properties appConfig;
    private RecyclerView mRecyclerView;
    private ChannelListAdapter mAdapter;
    private SwipeRefreshLayout swipeContainer;

    // added for cast SDK v3
    private CastContext mCastContext;
    private MenuItem mediaRouteMenuItem;
    private IntroductoryOverlay mIntroductoryOverlay;
    private CastStateListener mCastStateListener;
    private SwipeRefreshLayout.OnRefreshListener swipeRefreshListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        appConfig = ((DctvApplication) getApplication()).getAppConfig();
        setContentView(R.layout.activity_live_channels);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Explode explode;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            explode = new Explode();
            getWindow().setExitTransition(explode);
        }


        mCastStateListener = new CastStateListener() {
            @Override
            public void onCastStateChanged(int newState) {
                if (newState != CastState.NO_DEVICES_AVAILABLE) {
                    showIntroductoryOverlay();
                }
            }
        };

        mCastContext = CastContext.getSharedInstance(this); // initialises castcontext

        AccountHeader headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withTranslucentStatusBar(true)
                .withHeaderBackground(R.drawable.product_logo_header)
                .build();

        Drawer result = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withHasStableIds(true)
                .withAccountHeader(headerResult)
                .addDrawerItems(
                        new PrimaryDrawerItem().withIdentifier(1).withName(R.string.live_video).withIcon(R.drawable.ic_live_video),
                        new PrimaryDrawerItem().withIdentifier(2).withName(R.string.live_audio).withIcon(R.drawable.ic_live_radio),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withIdentifier(3).withName(R.string.chat_activity).withIcon(R.drawable.ic_chatrealm_bubble_white_24px),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withIdentifier(4).withName(R.string.action_settings).withIcon(R.drawable.settings_white),
                        new PrimaryDrawerItem().withIdentifier(5).withName(R.string.about).withIcon(R.drawable.ic_about)

                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            Intent intent = null;
                            if (drawerItem.getIdentifier() == 1) {
                                intent = new Intent(LiveChannelsActivity.this, LiveChannelsActivity.class);
                            } else if (drawerItem.getIdentifier() == 2) {
                                intent = new Intent(LiveChannelsActivity.this, RadioChannelsActivity.class);
                            } else if (drawerItem.getIdentifier() == 3) {
                                intent = new Intent(LiveChannelsActivity.this, JustChatActivity.class);
                            } else if (drawerItem.getIdentifier() == 4) {
                                intent = new Intent(LiveChannelsActivity.this, SettingsActivity.class);
                            } else if (drawerItem.getIdentifier() == 5) {
                                intent = new Intent(LiveChannelsActivity.this, AboutActivity.class);
                            }
                            if (intent != null) {
                                LiveChannelsActivity.this.startActivity(intent);
                                LiveChannelsActivity.this.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                            }
                        }

                        return false;
                    }
                })
                .withSavedInstance(savedInstanceState)
                .withShowDrawerOnFirstLaunch(true)
                .withCloseOnClick(false)
                .build();
        if (savedInstanceState == null) {
            // set the selection to the item with the identifier 1
            result.setSelection(1, false);
        }

/*        mRecyclerView = (RecyclerView) findViewById(R.id.live_list);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getBaseContext(), null));
        mRecyclerView.setHasFixedSize(true);
        mAdapter = new ChannelListAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        swipeRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new LoadLiveChannelsTask(mRecyclerView, appConfig) {

                    @Override
                    protected void onPostExecute(List<AbstractChannel> result) {
                        super.onPostExecute(result);
                        swipeContainer.setRefreshing(false);
                    }
                }.execute();
            }
        };
        swipeContainer.setColorSchemeColors(getResources().getColor(R.color.colorAccentDark));
        swipeContainer.setOnRefreshListener(swipeRefreshListener);

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        ArrayList<AbstractChannel> savedChannels = null;
        if (savedInstanceState != null) {
            savedChannels = savedInstanceState.getParcelableArrayList("CHANNEL_LIST");
        }
        if (savedChannels != null) {
            mAdapter.addAll(savedChannels);
        } else {
            new LoadLiveChannelsTask(mRecyclerView, appConfig).execute();
        }*/

        Button dcNoticeButton = (Button) findViewById(R.id.dc_notice_button);

        dcNoticeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "http://diamondclub.tv/";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (prefs.getLong("lastRunVersionCode", 0) < pInfo.versionCode) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                // Add the buttons
                builder.setPositiveButton(R.string.visit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                        String url = "http://diamondclub.tv/";
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                    }
                });
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                        dialog.cancel();
                    }
                });
                // Set other dialog properties

                builder.setMessage(R.string.dc_notice_dialog_message)
                        .setTitle(R.string.dc_notice_dialog_title);

                // Create the AlertDialog
                AlertDialog dialog = builder.create();

                dialog.show();

                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong("lastRunVersionCode", pInfo.versionCode);
                editor.apply();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error reading versionCode");
            e.printStackTrace();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_live_channels, menu);

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
/*        if (id == R.id.refresh) {
            swipeContainer.post(new Runnable() {
                @Override
                public void run() {
                    swipeContainer.setRefreshing(true);
                    // directly call onRefresh() method
                    swipeRefreshListener.onRefresh();
                }
            });
        }*/

        //noinspection SimplifiableIfStatement
        return super.onOptionsItemSelected(item);
    }

    private void showIntroductoryOverlay() {
        if (mIntroductoryOverlay != null) {
            mIntroductoryOverlay.remove();
        }
        if ((mediaRouteMenuItem != null) && mediaRouteMenuItem.isVisible()) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    mIntroductoryOverlay = new IntroductoryOverlay.Builder(
                            LiveChannelsActivity.this, mediaRouteMenuItem)
                            .setTitleText(getString(R.string.cast_introduction))
                            .setSingleTime()
                            .setOnOverlayDismissedListener(
                                    new IntroductoryOverlay.OnOverlayDismissedListener() {
                                        @Override
                                        public void onOverlayDismissed() {
                                            mIntroductoryOverlay = null;
                                        }
                                    })
                            .build();
                    mIntroductoryOverlay.show();
                }
            });
        }
    }

    @Override
    public void onChannelClicked(AbstractChannel channel, int position, ImageView channelArt) {
        Intent intent = new Intent(this, PlayStreamActivity.class);
        Bundle bundle = new Bundle();
        bundle.putParcelable(CHANNEL_DATA, channel);
        intent.putExtras(bundle);
        LiveChannelsActivity.this.startActivity(intent);
    }

    @Override
    protected void onResume() {
        mCastContext.addCastStateListener(mCastStateListener);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mCastContext.removeCastStateListener(mCastStateListener);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
//        outState.putParcelableArrayList(
//                "CHANNEL_LIST", (ArrayList<AbstractChannel>) mAdapter.getChannelList());
    }
}
