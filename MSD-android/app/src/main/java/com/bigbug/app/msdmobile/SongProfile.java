package com.bigbug.app.msdmobile;

import java.io.Serializable;

/**
 * Created by bigbug on 12/11/14.
 */
public class SongProfile implements Serializable {

    public String mTrackId;

    public String mReleaseId;

    public String mTitle;

    public String mArtist;

    public String mYear;

    public String mImage;

    public SongProfile() {}

    public SongProfile(SongProfile profile) {
        mTrackId   = profile.mTrackId;
        mReleaseId = profile.mReleaseId;
        mTitle     = profile.mTitle;
        mArtist    = profile.mArtist;
        mYear      = profile.mYear;
        mImage     = profile.mImage;
    }
}
