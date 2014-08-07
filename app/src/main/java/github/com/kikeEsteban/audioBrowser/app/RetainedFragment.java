package github.com.kikeEsteban.audioBrowser.app;

import android.app.Fragment;
import android.os.Bundle;

/**
 * Created by kike on 7/08/14.
 */

public class RetainedFragment extends Fragment {

    // data object we want to retain
    private RetainedData data;

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setData(RetainedData data) {
        this.data = data;
    }

    public RetainedData getData() {
        return data;
    }

}
