package com.bigbug.app.msdmobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;


public class MainActivity extends Activity implements SongListFragment.OnFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getActionBar().setTitle(Html.fromHtml("<font color=\"0xffffff\"><b>" + getString(R.string.app_name) + "</b></font>"));

        // Find the fragment or create a new one
        String tag = SongListFragment.class.getSimpleName();
        Fragment fragment = getFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            fragment = SongListFragment.newInstance(null);
        }

        getFragmentManager().beginTransaction()
                            .replace(android.R.id.content, fragment, tag)
                            .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(final SongProfile profile) {
        // Make the request url
        String url = Constants.Url.YEAR_PREDICTION + profile.mReleaseId;

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    // Create the string of predicted result
                    final String result = String.format("Actual: %s  Predicted: %s", profile.mYear, response);

                    // Show dialog with the prediction
                    PredictionDialogFragment dialogFragment = new PredictionDialogFragment();
                    dialogFragment.setProfile(profile);
                    dialogFragment.setPredictedResult(result);
                    dialogFragment.show(getFragmentManager(), getString(R.string.year_prediction));
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(getApplicationContext(), R.string.connection_error, Toast.LENGTH_LONG);
                }
            }
        );

        // Add the request to the RequestQueue.
        MSDApplication.getInstance().addToRequestQueue(stringRequest);
    }

    public static class PredictionDialogFragment extends DialogFragment {

        private String mResult;

        private SongProfile mProfile;

        public void setProfile(final SongProfile profile) {
            mProfile = new SongProfile(profile);
        }

        public void setPredictedResult(final String result) {
            mResult = new String(result);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            return new AlertDialog.Builder(getActivity())
                // Set Dialog Icon
                .setIcon(R.drawable.ic_launcher)
                        // Set Dialog Title
                .setTitle(R.string.year_prediction)
                        // Set Dialog Message
                .setMessage(mResult)

                        // Positive button
                .setPositiveButton("Recommend", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(getActivity(), RecommendActivity.class);
                        intent.putExtra(Constants.Key.SONG_PROFILE, mProfile);
                        startActivity(intent);
                    }
                })

                        // Negative Button
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do something else
                    }
                }).create();
        }
    }
}
