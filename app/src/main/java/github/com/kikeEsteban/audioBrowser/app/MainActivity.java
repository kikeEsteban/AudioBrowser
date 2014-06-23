package github.com.kikeEsteban.audioBrowser.app;


import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import java.math.BigDecimal;


public class MainActivity extends ListActivity {


    private MediaCursorAdapter mediaAdapter = null;
    private String currentFile = "";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null, null);

        if(null != cursor)
        {
            cursor.moveToFirst();
            mediaAdapter = new MediaCursorAdapter(this, R.layout.listitem, cursor);
            setListAdapter(mediaAdapter);
        }
    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id) {
        super.onListItemClick(list, view, position, id);
        currentFile = (String) view.getTag();
        // Launch intent
        launchPlayerActivity(currentFile);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void launchPlayerActivity(String songFile){
        Intent intent = new Intent(this, PlayerActivity.class);
        Bundle b = new Bundle();
        b.putString("songFile", songFile);
        intent.putExtras(b);
        startActivity(intent);
    }

    private class MediaCursorAdapter extends SimpleCursorAdapter{

        public MediaCursorAdapter(Context context, int layout, Cursor c) {
            super(context, layout, c,
                    new String[] { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE, MediaStore.Audio.AudioColumns.DURATION},
                    new int[] { R.id.displayname, R.id.title, R.id.duration });
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView title = (TextView)view.findViewById(R.id.title);
            TextView name = (TextView)view.findViewById(R.id.displayname);
            TextView duration = (TextView)view.findViewById(R.id.duration);

            name.setText(cursor.getString(
                    cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)));

            title.setText(cursor.getString(
                    cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)));

            long durationInMs = Long.parseLong(cursor.getString(
                    cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)));

            double durationInMin = ((double)durationInMs/1000.0)/60.0;

            durationInMin = new BigDecimal(Double.toString(durationInMin)).setScale(2, BigDecimal.ROUND_UP).doubleValue();

            duration.setText("" + durationInMin);

            view.setTag(cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA)));
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(R.layout.listitem, parent, false);

            bindView(v, context, cursor);

            return v;
        }
    }

}


