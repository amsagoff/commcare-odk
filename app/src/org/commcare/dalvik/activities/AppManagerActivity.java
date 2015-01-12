package org.commcare.dalvik.activities;


import org.commcare.android.adapters.AppManagerAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

/**
 * @author amstone326
 *
 */

public class AppManagerActivity extends Activity implements OnItemClickListener {
	
	public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_manager);
		((ListView)this.findViewById(R.id.apps_list_view)).setOnItemClickListener(this);
	}
	
	private void refreshView() {
		ListView lv = (ListView) findViewById(R.id.apps_list_view);
		lv.setAdapter(new AppManagerAdapter(this, android.R.layout.simple_list_item_1, 
		        CommCareApplication._().appRecordArray()));		
	}
	
	public void onResume() {
		super.onResume();
		System.out.println("ONRESUME called in AppManagerActivity");
		refreshView();
	}
	
	public void installAppClicked(View v) {
		Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
		i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
	    this.startActivityForResult(i, CommCareHomeActivity.INIT_APP);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		switch (requestCode) {
		case CommCareHomeActivity.INIT_APP:
			if (resultCode == RESULT_OK) {
				if(!CommCareApplication._().getCurrentApp().areResourcesValidated()){
		            Intent i = new Intent(this, CommCareVerificationActivity.class);
		            i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
		            this.startActivityForResult(i, CommCareHomeActivity.MISSING_MEDIA_ACTIVITY);
				} else {
					Toast.makeText(this, "New app installed successfully", Toast.LENGTH_LONG).show();
				}
			} else {
				Toast.makeText(this, "No app was installed!", Toast.LENGTH_LONG).show();
			}
			break;
		case CommCareHomeActivity.MISSING_MEDIA_ACTIVITY:
            if (resultCode == RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Media Not Verified");
                builder.setMessage(R.string.skipped_verification_warning)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                            }
                            
                        });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
            else if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
            }
            break;
		}
	}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        Intent i = new Intent(getApplicationContext(), SingleAppManagerActivity.class);
        i.putExtra("position", position);
        startActivity(i);
    }
    
}