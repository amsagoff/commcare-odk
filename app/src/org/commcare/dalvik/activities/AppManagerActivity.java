package org.commcare.dalvik.activities;

import org.commcare.android.adapters.AppManagerAdapter;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

/**
 * 
 * @author amstone326
 *
 */

public class AppManagerActivity extends Activity {
	
	public static final String KEY_LAUNCH_FROM_MANAGER = "from_manager";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_manager);
		ListView lv = (ListView) findViewById(R.id.apps_list_view);
		lv.setAdapter(new AppManagerAdapter(this, R.layout.custom_list_item_view, appRecordArray()));
	}
	
	public void onResume() {
		super.onResume();
		ListView lv = (ListView) findViewById(R.id.apps_list_view);
		lv.setAdapter(new AppManagerAdapter(this, 
				android.R.layout.simple_list_item_1, appRecordArray()));
	}
	
	private ApplicationRecord[] appRecordArray() {
		SqlStorage<ApplicationRecord> appList = CommCareApplication._().getInstalledAppRecords();
		ApplicationRecord[] appArray = new ApplicationRecord[appList.getNumRecords()];
		int index = 0;
		for (ApplicationRecord r : appList) {
			appArray[index++] = r;
		}
		return appArray;
	}
	
	public ApplicationRecord getAppAtIndex(int index) {
		ApplicationRecord[] currentApps = appRecordArray();
		if (index < 0 || index >= currentApps.length) {
			System.out.println("WARNING: attempting to get ApplicationRecord from ManagerActivity at invalid index");
			return null;
		} else return currentApps[index];
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
	
	/** Uninstalls the selected app **/
	public void uninstallSelected(View v) {
		String appId = (String) v.getContentDescription();
		ApplicationRecord selected = CommCareApplication._().getRecordById(appId);
	}
	
	/** If the app is not archived, sets it to archived (i.e. still installed but 
	 * not visible to users; If it is archived, sets it to unarchived **/
	public void toggleArchiveSelected(View v) {
		String appId = (String) v.getContentDescription();
		ApplicationRecord selected = CommCareApplication._().getRecordById(appId);
		selected.setArchiveStatus(!selected.isArchived());
		Button b = (Button) v;
		if (selected.isArchived()) {
			System.out.println("AppManagerActivity setting button to 'Unarchive'");
			b.setText("Unarchive");
		} else {
			System.out.println("AppManagerAdapter setting button to 'Archive'");
			b.setText("Archive");
		}
	}
	
	/** Opens the MM verification activity for the selected app **/
	public void verifyResourcesForSelected(View v) {
		String appId = (String) v.getContentDescription();
		ApplicationRecord selected = CommCareApplication._().getRecordById(appId);
		CommCareApplication._().initializeAppResources(new CommCareApp(selected));
		Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, CommCareHomeActivity.MISSING_MEDIA_ACTIVITY);
	}
	
	/** Conducts an update for the selected app **/
	public void updateSelected(View v) {
		String appId = (String) v.getContentDescription();
		ApplicationRecord selected = CommCareApplication._().getRecordById(appId);
	}

}