/**
 * 
 */
package org.commcare.android.providers;

import java.util.List;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Case;
import org.commcare.android.preloaders.CasePreloader;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.javarosa.core.model.data.IAnswerData;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author ctsims
 *
 */
public class PreloadContentProvider extends ContentProvider {
	
	private static AndroidCommCarePlatform platform;
	private static Context context;
	
	public static final Uri CONTENT_URI = Uri.parse("content://org.commcare.preloadprovider");
	public static final Uri CONTENT_URI_CASE = Uri.parse("content://org.commcare.preloadprovider/case");
	
	public PreloadContentProvider() {
		String empty = "empty";
		empty.subSequence(0,2);
	}
	
	public static void initializeSession(AndroidCommCarePlatform platform, Context context) {
		PreloadContentProvider.platform = platform;
		PreloadContentProvider.context = context;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
	 */
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new RuntimeException("No deleting preload data");
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#getType(android.net.Uri)
	 */
	public String getType(Uri uri) {
		return "vnd.android.cursor.item/vnd.commcare.preloaddata";
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		//Invalid
		throw new RuntimeException("No inserting preload data");
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#onCreate()
	 */
	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		List<String> uriParts = uri.getPathSegments();
		if("case".equals(uriParts.get(0))) {
			String caseId = uriParts.get(uriParts.size() - 2);
			Case c = new SqlIndexedStorageUtility<Case>(Case.STORAGE_KEY, Case.class, context).getRecordForValue(Case.META_CASE_ID, caseId);
			
			CasePreloader preloader = new CasePreloader(c);
			String param = uri.getLastPathSegment();
			IAnswerData data = preloader.handlePreload(param);
			return new PreloadedContentCursor(data.uncast().getString());
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new RuntimeException("No updating preload data");
	}

}
