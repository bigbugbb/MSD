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
        private static final String HOST = "http://ec2-54-86-162-1.compute-1.amazonaws.com/";
        public static final String SONG_PROFILES = HOST + "song/profile/";
        public static final String YEAR_PREDICTION = HOST + "song/predict/";
        public static final String RECOMMEND_SONGS = HOST + "song/recommend/";
    }
}
