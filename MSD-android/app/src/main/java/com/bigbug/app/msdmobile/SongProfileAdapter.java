package com.bigbug.app.msdmobile;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.RoundedBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

/**
 * Created by bigbug on 12/11/14.
 */
public class SongProfileAdapter extends BaseAdapter {

    private static class ViewHolder {
        TextView  title;
        TextView  artist;
        ImageView image;
    }

    // The options passed to image loader to control the loading behaviors.
    private DisplayImageOptions mOptions;

    // The song profiles fetched from EC2
    private Records<SongProfile> mRecords;

    private LayoutInflater mInflater;

    private ImageLoadingListener mAnimateFirstListener = new AnimateFirstDisplayListener();

    SongProfileAdapter(Activity acitivity, Records<SongProfile> records) {
        mRecords  = records;
        mInflater = LayoutInflater.from(acitivity);
        mOptions  = new DisplayImageOptions.Builder()
                .showImageOnLoading(R.drawable.ic_stub)
                .showImageForEmptyUri(R.drawable.ic_empty)
                .showImageOnFail(R.drawable.ic_error)
                .cacheInMemory(true)
                .cacheOnDisk(true)
                .considerExifParams(true)
                .displayer(new RoundedBitmapDisplayer(20))
                .build();
    }

    @Override
    public int getCount() {
        return mRecords.size();
    }

    @Override
    public Object getItem(int position) {
        return mRecords.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        // Create views and save them for re-usage
        View view = convertView;
        final ViewHolder holder;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.listitem_song, parent, false);
            holder = new ViewHolder();
            holder.title  = (TextView) view.findViewById(R.id.title);
            holder.artist = (TextView) view.findViewById(R.id.artist);
            holder.image  = (ImageView) view.findViewById(R.id.image);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        SongProfile profile = mRecords.get(position);
        holder.title.setText(profile.mTitle);
        holder.artist.setText(profile.mArtist);

        // Load the image of the this song
        ImageLoader.getInstance().displayImage(profile.mImage, holder.image, mOptions, mAnimateFirstListener);

        return view;
    }
}
