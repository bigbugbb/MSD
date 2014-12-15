package com.bigbug.app.msdmobile;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;


public class RecommendActivity extends Activity {

    private SongProfile mProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend);

        // Get the song profile. Recommend other songs that have the common attributes with this song.
        Intent intent = getIntent();
        mProfile = (SongProfile) intent.getSerializableExtra(Constants.Key.SONG_PROFILE);

        getActionBar().setTitle(Html.fromHtml("<font color=\"0xffffff\"><b>" + getString(R.string.recommend_song) + "</b></font>"));

        // Find the fragment or create a new one
        String tag = RecommendListFragment.class.getSimpleName();
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            fragment = RecommendListFragment.newInstance(mProfile.mReleaseId);
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment, tag)
                .commit();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_recommend, menu);
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
}
