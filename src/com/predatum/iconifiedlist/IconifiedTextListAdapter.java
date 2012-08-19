package com.predatum.iconifiedlist;

import java.util.ArrayList;
import java.util.List;
import com.predatum.R;
import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/** @author Steven Osborn - http://steven.bitsetters.com */
public class IconifiedTextListAdapter extends BaseAdapter {
	
		LayoutInflater factory; 
		View newView;
		
		TextView upperTextView;
		TextView bottomTextView;
		ImageView imgView;
		
        /** Remember our context so we can use it when constructing views. */
        private Context mContext;

        private List<IconifiedText> mItems = new ArrayList<IconifiedText>();

        public IconifiedTextListAdapter(Context context) {
                mContext = context;
                factory = LayoutInflater.from(context);
                
        }

        public void addItem(IconifiedText it) { mItems.add(it); }

        public void setListItems(List<IconifiedText> list) { mItems = list; }

        /** @return The number of items in the */
        public int getCount() { return mItems.size(); }

        public Object getItem(int position) { return mItems.get(position); }

        public boolean areAllItemsSelectable() { return false; }

        public boolean isSelectable(int position) {
                return mItems.get(position).isSelectable();
        }

        /** Use the array index as a unique id. */
        public long getItemId(int position) {
                return position;
        }
       
        /** @param convertView The old view to overwrite, if one is passed
         * @returns a IconifiedTextView that holds wraps around an IconifiedText */
        public View getView(int position, View convertView, ViewGroup parent) {
        	if(convertView == null) {
        		newView = factory.inflate(R.layout.row, null);
        	} else {
        		newView = convertView;
        	}
            upperTextView = (TextView)newView.findViewById(R.id.toptext);
            upperTextView.setText(mItems.get(position).getTopText());
            bottomTextView = (TextView)newView.findViewById(R.id.bottomtext);
            bottomTextView.setText(mItems.get(position).getBottomText());
            bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            bottomTextView.setTypeface(null, Typeface.ITALIC);
            imgView = (ImageView)newView.findViewById(R.id.icon);
            imgView.setImageDrawable(mItems.get(position).getIcon());
            
           return newView;
        }
}
