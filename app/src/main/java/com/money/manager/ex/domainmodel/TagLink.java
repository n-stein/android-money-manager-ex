package com.money.manager.ex.domainmodel;

import android.content.ContentValues;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class TagLink extends EntityBase
        implements Parcelable {

    /* Table
    CREATE TABLE TAGLINK_V1(
        TAGLINKID INTEGER PRIMARY KEY
        , REFTYPE TEXT NOT NULL
        , REFID INTEGER NOT NULL
        , TAGID INTEGER NOT NULL
        , UNIQUE(REFTYPE, REFID, TAGID)
        )
     */

    public static final String TAGLINKID = "TAGLINKID";
    public static final String REFTYPE = "REFTYPE";
    public static final String REFID = "REFID";
    public static final String TAGID = "TAGID";

    public TagLink() { super(); }
    public TagLink(ContentValues contentValues) {
        super(contentValues);
    }

    public static final Creator<TagLink> CREATOR = new Creator<TagLink>() {
        @Override
        public TagLink createFromParcel(Parcel in) {
            return new TagLink(in);
        }

        @Override
        public TagLink[] newArray(int size) {
            return new TagLink[size];
        }
    };

    @Override
    public String getPrimaryKeyColumn() {
        return TAGLINKID;  // This returns the column name
    }

    public String getRefType() { return getString(REFTYPE); }
    public void setRefType(String value) { setString(REFTYPE, value); }
    public void setRefType(RefType value) { setString(REFTYPE, value.getValue());}

    public Long getRefId() { return getLong(REFID); }
    public void setRefId(Long value) { setLong(REFID, value); }

    public Long getTagId() { return getLong(TAGID); }
    public void setTagId(Long value) { setLong(TAGID, value); }

    public boolean inTaglinkList(ArrayList<TagLink> list ) {
        for( TagLink entity : list ) {
            if ( entity.getId() == getId() )
                return true;
        }
        return false;
    }

    public static ArrayList<TagLink> clearCrossReference(ArrayList<TagLink> list) {
        for (TagLink entity : list) {
          entity.setRefType((String) null);
          entity.setRefId(null);
          entity.setId(null);
        }
        return list;
    }

    protected TagLink(Parcel in) {
        setId(nullLong(in.readLong()));
        setRefType(in.readString());
        setRefId(nullLong(in.readLong()));
        setTagId(nullLong(in.readLong()));
    }

    private Long nullLong(long value) {
        if (value == -1)
            return null;
        return value;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong((getId() == null ? -1 : getId()));
        dest.writeString((getRefType() == null ? "" : getRefType()));
        dest.writeLong((getRefId() == null ? -1 : getRefId()));
        dest.writeLong((getTagId() == null ? -1 : getTagId()));
    }
}
