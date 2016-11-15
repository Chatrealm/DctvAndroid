package com.tinnvec.dctvandroid.channel;

import android.content.Context;
import android.os.Parcel;

import com.tinnvec.dctvandroid.R;

import java.util.Properties;

public class YoutubeChannel extends AbstractChannel {

    public static final Creator<YoutubeChannel> CREATOR = new Creator<YoutubeChannel>() {
        public YoutubeChannel createFromParcel(Parcel in) {
            return new YoutubeChannel(in);
        }

        public YoutubeChannel[] newArray(int size) {
            return new YoutubeChannel[size];
        }
    };

    private String liveUrl;
    private boolean isUpcoming;

    public YoutubeChannel() { }

    public YoutubeChannel(Parcel in) {
        super(in);
        liveUrl = in.readString();
        isUpcoming = in.readByte() != 0;

    }
    @Override
    public String getStreamUrl(Properties app_config, Quality quality) {
        if (streamUrl != null) return streamUrl;

        String baseUrl = app_config.getProperty("api.dctv.base_url");
        return String.format("%sapi/hlsredirect.php?c=%d", baseUrl, channelID);
    }

    @Override
    public Quality[] getAllowedQualities() {
        return new Quality[] {Quality.high};
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(liveUrl);
        dest.writeByte((byte) (isUpcoming ? 1 : 0));
    }

    public String getLiveUrl() {
        return liveUrl;
    }

    public void setLiveUrl(String liveUrl) {
        this.liveUrl = liveUrl;
    }

    public boolean isUpcoming() {
        return isUpcoming;
    }

    public void setUpcoming(boolean upcoming) {
        isUpcoming = upcoming;
    }
}
