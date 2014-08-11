/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.twowayview.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import org.lucasr.twowayview.TwoWayView;
import org.lucasr.twowayview.widget.Lanes.LaneInfo;

public class SpannableGridLayoutManager extends GridLayoutManager {
    private static final String LOGTAG = "SpannableGridLayoutManager";

    private static final int DEFAULT_NUM_COLS = 3;
    private static final int DEFAULT_NUM_ROWS = 3;

    protected static class SpannableItemEntry extends BaseLayoutManager.ItemEntry {
        private final int colSpan;
        private final int rowSpan;

        public SpannableItemEntry(int startLane, int anchorLane, int colSpan, int rowSpan) {
            super(startLane, anchorLane);
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }

        public SpannableItemEntry(Parcel in) {
            super(in);
            this.colSpan = in.readInt();
            this.rowSpan = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(colSpan);
            out.writeInt(rowSpan);
        }

        public static final Parcelable.Creator<SpannableItemEntry> CREATOR
                = new Parcelable.Creator<SpannableItemEntry>() {
            @Override
            public SpannableItemEntry createFromParcel(Parcel in) {
                return new SpannableItemEntry(in);
            }

            @Override
            public SpannableItemEntry[] newArray(int size) {
                return new SpannableItemEntry[size];
            }
        };
    }

    private final Context mContext;
    private boolean mMeasuring;

    public SpannableGridLayoutManager(Context context) {
        this(context, null);
    }

    public SpannableGridLayoutManager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpannableGridLayoutManager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, DEFAULT_NUM_COLS, DEFAULT_NUM_ROWS);
        mContext = context;
    }

    public SpannableGridLayoutManager(Context context, Orientation orientation,
                                      int numColumns, int numRows) {
        super(context, orientation, numColumns, numRows);
        mContext = context;
    }

    private int getChildWidth(int colSpan) {
        return getLanes().getLaneSize() * colSpan;
    }

    private int getChildHeight(int rowSpan) {
        return getLanes().getLaneSize() * rowSpan;
    }

    static int getLaneSpan(SpannableGridLayoutManager lm, View child) {
        return getLaneSpan((LayoutParams) child.getLayoutParams(), lm.isVertical());
    }

    static int getLaneSpan(SpannableGridLayoutManager lm, int position) {
        final SpannableItemEntry entry = (SpannableItemEntry) lm.getItemEntryForPosition(position);
        if (entry == null) {
            throw new IllegalStateException("Could not find span for position " + position);
        }

        return getLaneSpan(entry, lm.isVertical());
    }

    private static int getLaneSpan(LayoutParams lp, boolean isVertical) {
        return (isVertical ? lp.colSpan : lp.rowSpan);
    }

    private static int getLaneSpan(SpannableItemEntry entry, boolean isVertical) {
        return (isVertical ? entry.colSpan : entry.rowSpan);
    }

    @Override
    public boolean canScrollHorizontally() {
        return super.canScrollHorizontally() && !mMeasuring;
    }

    @Override
    public boolean canScrollVertically() {
        return super.canScrollVertically() && !mMeasuring;
    }

    @Override
    void getLaneForPosition(LaneInfo outInfo, int position, Direction direction) {
        final SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(position);
        if (entry != null) {
            outInfo.set(entry.startLane, entry.anchorLane);
            return;
        }

        outInfo.setUndefined();
    }

    @Override
    void getLaneForChild(LaneInfo outInfo, View child, Direction direction) {
        super.getLaneForChild(outInfo, child, direction);
        if (outInfo.isUndefined()) {
            getLanes().findLane(outInfo, getLaneSpan(this, child), direction);
        }
    }

    private int getWidthUsed(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return getWidth() - getChildWidth(lp.colSpan);
    }

    private int getHeightUsed(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return getHeight() - getChildHeight(lp.rowSpan);
    }

    @Override
    protected void measureChild(View child) {
        // XXX: This will disable scrolling while measuring this child to ensure that
        // both width and height can use MATCH_PARENT properly.
        mMeasuring = true;
        measureChildWithMargins(child, getWidthUsed(child), getHeightUsed(child));
        mMeasuring = false;
    }

    @Override
    protected void layoutChild(View child, Direction direction) {
        super.layoutChild(child, direction);

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int laneSpan = getLaneSpan(lp, isVertical());
        if (lp.isItemRemoved() || laneSpan == 1) {
            return;
        }

        getLaneForPosition(mTempLaneInfo, getPosition(child), direction);
        final int lane = mTempLaneInfo.startLane;

        // The parent class has already pushed the frame to
        // the main lane. Now we push it to the remaining lanes
        // within the item's span.
        getDecoratedChildFrame(child, mChildFrame);
        getLanes().pushChildFrame(mChildFrame, lane + 1, lane + laneSpan, direction);
    }

    @Override
    protected void detachChild(View child, Direction direction) {
        super.detachChild(child, direction);

        final int laneSpan = getLaneSpan(this, child);
        if (laneSpan == 1) {
            return;
        }

        getLaneForPosition(mTempLaneInfo, getPosition(child), direction);
        final int lane = mTempLaneInfo.startLane;

        // The parent class has already popped the frame from
        // the main lane. Now we pop it from the remaining lanes
        // within the item's span.
        getDecoratedChildFrame(child, mChildFrame);
        getLanes().popChildFrame(mChildFrame, lane + 1, lane + laneSpan, direction);
    }

    @Override
    protected void moveLayoutToPosition(int position, int offset, Recycler recycler, State state) {
        final boolean isVertical = isVertical();
        final Lanes lanes = getLanes();
        final Rect childFrame = new Rect();

        lanes.reset(0);

        for (int i = 0; i <= position; i++) {
            SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(i);
            if (entry != null) {
                mTempLaneInfo.set(entry.startLane, entry.anchorLane);
                lanes.getChildFrame(getChildWidth(entry.colSpan), getChildHeight(entry.rowSpan),
                        mTempLaneInfo, Direction.END, childFrame);
            } else {
                final View child = recycler.getViewForPosition(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                lanes.findLane(mTempLaneInfo, getLaneSpan(lp, isVertical), Direction.END);
                lanes.getChildFrame(getChildWidth(lp.colSpan), getChildHeight(lp.rowSpan),
                        mTempLaneInfo, Direction.END, childFrame);

                entry = (SpannableItemEntry) cacheItemEntry(child, i, mTempLaneInfo, childFrame);
            }

            if (i != position) {
                lanes.pushChildFrame(childFrame, entry.startLane,
                        entry.startLane + getLaneSpan(entry, isVertical), Direction.END);
            }
        }

        getLaneForPosition(mTempLaneInfo, position, Direction.END);
        lanes.getLane(mTempLaneInfo.startLane, mTempRect);

        lanes.reset(Direction.END);
        lanes.offset(offset - (isVertical ? mTempRect.bottom : mTempRect.right));
    }

    @Override
    ItemEntry cacheItemEntry(View child, int position, LaneInfo laneInfo, Rect childFrame) {
        SpannableItemEntry entry = (SpannableItemEntry) getItemEntryForPosition(position);
        if (entry == null) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int colSpan = lp.colSpan;
            final int rowSpan = lp.rowSpan;

            entry = new SpannableItemEntry(laneInfo.startLane, laneInfo.anchorLane,
                    colSpan, rowSpan);
            setItemEntryForPosition(position, entry);
        }

        return entry;
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        if (lp.width != LayoutParams.MATCH_PARENT ||
            lp.height != LayoutParams.MATCH_PARENT) {
            return false;
        }

        if (lp instanceof LayoutParams) {
            final LayoutParams spannableLp = (LayoutParams) lp;

            if (isVertical()) {
                return (spannableLp.rowSpan >= 1 && spannableLp.colSpan >= 1 &&
                        spannableLp.colSpan <= getLaneCount());
            } else {
                return (spannableLp.colSpan >= 1 && spannableLp.rowSpan >= 1 &&
                        spannableLp.rowSpan <= getLaneCount());
            }
        }

        return false;
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        final LayoutParams spannableLp = new LayoutParams((MarginLayoutParams) lp);
        spannableLp.width = LayoutParams.MATCH_PARENT;
        spannableLp.height = LayoutParams.MATCH_PARENT;

        if (lp instanceof LayoutParams) {
            final LayoutParams other = (LayoutParams) lp;
            if (isVertical()) {
                spannableLp.colSpan = Math.max(1, Math.min(other.colSpan, getLaneCount()));
                spannableLp.rowSpan = Math.max(1, other.rowSpan);
            } else {
                spannableLp.colSpan = Math.max(1, other.colSpan);
                spannableLp.rowSpan = Math.max(1, Math.min(other.rowSpan, getLaneCount()));
            }
        }

        return spannableLp;
    }

    @Override
    public LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    public static class LayoutParams extends TwoWayView.LayoutParams {
        private static final int DEFAULT_SPAN = 1;

        public int rowSpan;
        public int colSpan;

        public LayoutParams(int width, int height) {
            super(width, height);
            rowSpan = DEFAULT_SPAN;
            colSpan = DEFAULT_SPAN;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.SpannableGridViewChild);
            colSpan = Math.max(
                    DEFAULT_SPAN, a.getInt(R.styleable.SpannableGridViewChild_colSpan, -1));
            rowSpan = Math.max(
                    DEFAULT_SPAN, a.getInt(R.styleable.SpannableGridViewChild_rowSpan, -1));
            a.recycle();
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);
            init(other);
        }

        public LayoutParams(MarginLayoutParams other) {
            super(other);
            init(other);
        }

        private void init(ViewGroup.LayoutParams other) {
            if (other instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) other;
                rowSpan = lp.rowSpan;
                colSpan = lp.colSpan;
            } else {
                rowSpan = DEFAULT_SPAN;
                colSpan = DEFAULT_SPAN;
            }
        }
    }
}
