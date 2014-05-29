/**
 * 
 */
package org.commcare.android.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.commcare.android.models.Entity;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.Reference;
import org.odk.collect.android.utilities.MediaUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.media.AudioManager;
import android.media.MediaPlayer;

/**
 * @author ctsims
 *
 */
public class EntityView extends LinearLayout {
	
	private View[] views;
	private String[] forms;
	private TextToSpeech tts; 
	private MediaPlayer mp;
	private FileInputStream fis;
	private String[] searchTerms;

	/*
	 * Constructor for row/column contents
	 */
	public EntityView(Context context, Detail d, Entity e, TextToSpeech tts, String[] searchTerms) {
		super(context);
		Log.i("EntityView", "constructor with setParams");
		this.searchTerms = searchTerms;
		this.tts = tts;

		this.setWeightSum(1);
		
		views = new View[e.getNumFields()];
		forms = d.getTemplateForms();
		System.out.println("ALL FORMS FOR THIS ENTITY");
		for (int i = 0 ; i < forms.length; i++) {
			System.out.println(forms[i]);
		}
		
		float[] weights = calculateDetailWeights(d.getTemplateSizeHints());
		
		for(int i = 0 ; i < views.length ; ++i) {
			if (weights[i] != 0) {
				LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, weights[i]);
		        views[i] = getView(context, null, forms[i]);
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
        
		setParams(e, false);
	}
	
	/*
	 * Constructor for row/column headers
	 */
	public EntityView(Context context, Detail d, String[] headerText) {
		super(context);
		Log.i("EntityView", "constructor without setParams");
		this.setWeightSum(1);
		views = new View[headerText.length];
		
		
		float[] lengths = calculateDetailWeights(d.getHeaderSizeHints());
		String[] headerForms = d.getHeaderForms();
		
		for(int i = 0 ; i < views.length ; ++i) {
			if(lengths[i] != 0) {
		        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, lengths[i]);
		        /*this first call is just demarcating the space for each view in a row, and then
		         * setParams will fill in those existing views with the appropriate content, depending
		         * on the form type
		         */
		        views[i] = getView(context, headerText[i], headerForms[i]);
		        
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
	}
	
	/*
	 * if form = "text", then 'text' field is just normal text
	 * if form = "audio" or "image", then text is the path to the audio/image
	 */
	private View getView(Context context, String text, String form) {
		View retVal;
		if ("image".equals(form)) {
			Log.i("form","=image");
			ImageView iv =(ImageView)View.inflate(context, R.layout.entity_item_image, null);
			retVal = iv;
        	if (text != null) {
				try {
					Bitmap b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(text).getStream());
					iv.setImageBitmap(b);
				} catch (IOException e) {
					e.printStackTrace();
					//Error loading image
				} catch (InvalidReferenceException e) {
					e.printStackTrace();
					//No image
				}
        	}
        } 
		else if ("audio".equals(form)) {
			Log.i("form","=audio");
			View layout = View.inflate(context, R.layout.component_audio_text, null);
    		setupAudioLayout(layout,text);
    		retVal = layout;
        } 
        else {
        	Log.i("form","=regular text");
    		View layout = View.inflate(context, R.layout.component_audio_text, null);
    		setupTextAndTTSLayout(layout, text);
    		retVal = layout;
        }
        return retVal;
	}
	
	public void setSearchTerms(String[] terms) {
		this.searchTerms = terms;
	}
	

	public void setParams(Entity e, boolean currentlySelected) {
		for (int i = 0; i < e.getNumFields() ; ++i) {
			String textField = e.getField(i);
			Log.i("EntityView","textField in setParams: " + textField);
			View view = views[i];
			String form = forms[i];
			
			if (view == null) { continue; }
			//if (textField == null) continue;
			
			if ("audio".equals(form)) {
				setupAudioLayout(view, textField);
			}
			else if("image".equals(form)) {
				setupImageLayout(view, textField);
	        } 
			else { //text to speech
		        setupTextAndTTSLayout(view, textField);
	        }
		}
		
		if (currentlySelected) {
			this.setBackgroundResource(R.drawable.grey_bordered_box);
		} else {
			this.setBackgroundDrawable(null);
		}
	}
	 
        
    private void setupAudioLayout(View layout, final String text) {
    	Log.i("form", "setupAudioLayout entered");
    	ImageButton btn = (ImageButton)layout.findViewById(R.id.component_audio_text_btn_audio);
		btn.setFocusable(false);
		btn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				if (mp.isPlaying()) {
					mp.stop();
					try {
						fis.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else {
					try {
						mp.prepare();
						mp.start();
					} catch (IllegalStateException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
    	});
		
		if (text != null) {
			//try to initialize the media player
			boolean mpFailure = false;
			try {
				mp = new MediaPlayer();
				InputStream is = ReferenceManager._().DeriveReference(text).getStream();
				fis = MediaUtil.inputStreamToFIS(is);
				mp.setDataSource(fis.getFD());
				mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
			}
			catch (Exception e) {
				mpFailure = true;
				System.out.println("could not set data source");
				e.printStackTrace();
			}
			
			//show/hide audio button based on media player set-up
			if (mpFailure) {
				System.out.println("audio button set to invisible");
				btn.setVisibility(View.INVISIBLE);
				RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
				params.width = 0;
				btn.setLayoutParams(params);
			} 
			else {
				System.out.println("audio button set to visible");
				btn.setVisibility(View.VISIBLE);
				RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
				params.width = LayoutParams.WRAP_CONTENT;
				btn.setLayoutParams(params);
			}
		}
    }
	
	private void setupTextAndTTSLayout(View layout, final String text) {
    	Log.i("form", "setupTextAndTTSLayout entered");
		TextView tv = (TextView)layout.findViewById(R.id.component_audio_text_txt);
		ImageButton btn = (ImageButton)layout.findViewById(R.id.component_audio_text_btn_audio);
		btn.setFocusable(false);

		btn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				String textToRead = text;
				tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
			}
    	});
		if (tts == null || text == null || text.equals("")) {
			btn.setVisibility(View.INVISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width= 0;
			btn.setLayoutParams(params);
		} else {
			btn.setVisibility(View.VISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width=LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		}
	    tv.setText(highlightSearches(text == null ? "" : text));
    }
	
	
	public void setupImageLayout(View layout, final String text) {
    	Log.i("form", "setupImageLayout entered");
		ImageView iv = (ImageView) layout;
		Bitmap b;
		try {
			if(!text.equals("")) {
				b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(text).getStream());
				//TODO: shouldn't this check if b is null?
				iv.setImageBitmap(b);
				Log.i("form", "bitmap image set");
			} else{
				Log.i("form", "text is empty");
				iv.setImageBitmap(null);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			//Error loading image
			iv.setImageBitmap(null);
		} catch (InvalidReferenceException ex) {
			ex.printStackTrace();
			//No image
			iv.setImageBitmap(null);
		}
	}
    
    private Spannable highlightSearches(String input) {
    	
	    Spannable raw=new SpannableString(input);
	    
	    String normalized = input.toLowerCase();
		
    	if(searchTerms == null) {
    		return raw;
    	}
	    
	    //Zero out the existing spans
	    BackgroundColorSpan[] spans=raw.getSpans(0,raw.length(), BackgroundColorSpan.class);
		for (BackgroundColorSpan span : spans) {
			raw.removeSpan(span);
		}
	    
	    for(String searchText : searchTerms) {
	    	if(searchText == "") { continue;}
	
		    int index=TextUtils.indexOf(normalized, searchText);
		    
		    while (index >= 0) {
		      raw.setSpan(new BackgroundColorSpan(this.getContext().getResources().getColor(R.color.search_highlight)), index, index
		          + searchText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		      index=TextUtils.indexOf(raw, searchText, index + searchText.length());
		    }
	    }
	    
	    return raw;
    }
    
    
	
	private float[] calculateDetailWeights(int[] hints) {
		float[] weights = new float[hints.length];
		int fullSize = 100;
		int sharedBetween = 0;
		for(int hint : hints) {
			if(hint != -1) {
				fullSize -= hint;
			} else {
				sharedBetween ++;
			}
		}
		
		double average = ((double)fullSize) / (double)sharedBetween;
		
		for(int i = 0; i < hints.length; ++i) {
			int hint = hints[i];
			weights[i] = hint == -1? (float)(average/100.0) :  (float)(((double)hint)/100.0);
		}

		return weights;
	}

}
