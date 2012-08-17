package com.predatum.iconifiedlist;


import android.graphics.drawable.Drawable;


public class IconifiedText {

        private String topText = "";
        private String bottomText = "";
        private Drawable mIcon;
        private boolean mSelectable = true;
        private int albumID = 0;

        public IconifiedText(String upperText, String bottomText, Drawable bullet, int AlbumID) {
                mIcon = bullet;
                this.topText = upperText;
                this.bottomText = bottomText;
                this.albumID = AlbumID;
        }

        public boolean isSelectable() {
                return mSelectable;
        }

        public void setSelectable(boolean selectable) {
                mSelectable = selectable;
        }

        public String getTopText() {
                return topText;
        }

        public void setTopText(String text) {
                topText = text;
        }

        public String getBottomText() {
                return bottomText;
        }

        public void setBottomText(String text) {
                bottomText = text;
        }

        public void setIcon(Drawable icon) {
                mIcon = icon;
        }

        public Drawable getIcon() {
                return mIcon;
        }
        public void setAlbuID(int id) {
                albumID = id;
        }

        public int getAlbumID() {
                return albumID;
        }
     
}
