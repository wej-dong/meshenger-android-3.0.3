package d.d.meshenger.set;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import d.d.meshenger.R;
import d.d.meshenger.main.MeshengerActivity;
import d.d.meshenger.utils.Utils;


public class AboutActivity extends MeshengerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        setTitle(getResources().getString(R.string.menu_about));

        ((TextView) findViewById(R.id.versionTv)).setText(
            Utils.getApplicationVersion(this)
        );

        findViewById(R.id.licenseTV).setOnClickListener(v -> {
            Intent intent = new Intent(this, LicenseActivity.class);
            startActivity(intent);
        });
    }
}
