package com.bigbug.app.msdmobile;

/**
 * Created by bigbug on 12/11/14.
 */
public final class Constants {

    private Constants() {
    }

    public static class Config {
        public static final boolean DEVELOPER_MODE = false;
    }

    public static class Key {
        public static final String SONG_PROFILE = "SONG_PROFILE";
        public static final String SONG_RELEASE_ID = "SONG_RELEASE_ID";
        public static final String JSON_SONG_PROFILES = "JSON_SONG_PROFILES";
    }

    public static class Url {
        public static final String SONG_PROFILES = "http://54.86.162.1/song/profile/";
        public static final String YEAR_PREDICTION = "http://54.86.162.1/song/predict/";
        public static final String RECOMMEND_SONGS = "http://54.86.162.1/song/recommend/";
    }
}
