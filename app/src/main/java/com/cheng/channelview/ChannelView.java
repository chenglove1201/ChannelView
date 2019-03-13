package com.cheng.channelview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChannelView extends ScrollView {
    private Context mContext;
    private Map<String, List<Channel>> channelContents = new LinkedHashMap<>();
    private int channelFixedToPosition = -1;
    private ChannelLayout channelLayout;

    /**
     * 列数
     */
    private int channelColumn;

    private int channelWidth;

    private int channelHeight;

    private int channelPadding;

    /**
     * 水平方向上的间隔线
     */
    private int horizontalSpacing;

    /**
     * 竖直方向上的间隔线
     */
    private int verticalSpacing;

    public ChannelView(Context context) {
        this(context, null);
    }

    public ChannelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChannelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ChannelView);
        channelHeight = (int) typedArray.getDimension(R.styleable.ChannelView_channelHeight, 120);
        channelColumn = typedArray.getInteger(R.styleable.ChannelView_channelColumn, 4);
        channelPadding = (int) typedArray.getDimension(R.styleable.ChannelView_channelPadding, 0);
        horizontalSpacing = (int) typedArray.getDimension(R.styleable.ChannelView_channelHorizontalSpacing, 0);
        verticalSpacing = (int) typedArray.getDimension(R.styleable.ChannelView_channelVerticalSpacing, 0);
        if (channelColumn < 1) {
            channelColumn = 1;
        }
        if (channelHeight < 1) {
            channelHeight = 120;
        }
        if (channelPadding < 0) {
            channelPadding = 0;
        }
        if (horizontalSpacing < 0) {
            horizontalSpacing = 0;
        }
        if (verticalSpacing < 0) {
            verticalSpacing = 0;
        }
        typedArray.recycle();
    }

    /**
     * 设置前toPosition+1个channel不能拖动
     *
     * @param toPosition 前toPosition个channel不能拖动，toPosition是下标
     */
    public void setFixedChannel(int toPosition) {
        this.channelFixedToPosition = toPosition;
    }

    /**
     * 添加频道板块
     */
    public void addPlate(String plateName, List<Channel> channelList) {
        if (channelList != null && channelList.size() > 0) {
            if (channelContents.size() != 0) {
                for (Channel channel : channelList) {
                    channel.channelBelong = channelContents.size();
                }
            }
            channelContents.put(plateName, channelList);
        }
    }

    private int channelNormalBackground = R.drawable.bg_channel_normal;
    private int channelSelectedBackground = R.drawable.bg_channel_selected;
    private int channelFocusedBackground = R.drawable.bg_channel_focused;
    private List<TextView> fixedTextView = new ArrayList<>();
    private List<TextView> allTextView = new ArrayList<>();

    /**
     * 设置频道正常状态
     */
    public void setChannelNormalBackground(@DrawableRes int channelNormalBackground) {
        this.channelNormalBackground = channelNormalBackground;
        for (TextView textView : allTextView) {
            textView.setBackgroundResource(channelNormalBackground);
        }
    }

    /**
     * 设置频道已选择状态
     */
    public void setChannelSelectedBackground(@DrawableRes int channelSelectedBackground) {
        this.channelSelectedBackground = channelSelectedBackground;
    }

    /**
     * 设置频道点击状态
     */
    public void setChannelFocusedBackground(@DrawableRes int channelFocusedBackground) {
        this.channelFocusedBackground = channelFocusedBackground;
    }

    /**
     * 设置固定频道的颜色
     *
     * @param channelFixedColorRes
     */
    public void setChannelFixedColor(@ColorInt int channelFixedColorRes) {
        for (TextView textView : fixedTextView) {
            textView.setTextColor(channelFixedColorRes);
        }
    }

    private OnChannelListener onChannelListener;

    /**
     * 添加完频道模块之后，进行填充数据
     */
    public void inflateData() {
        //如果只有一组频道，默认再加上一组
        if (channelContents.size() == 1) {
            channelContents.put("推荐频道", null);
        }
        if (channelLayout == null) {
            channelLayout = new ChannelLayout(mContext);
            addView(channelLayout);
        }
    }

    /**
     * 获取我的频道
     *
     * @return
     */
    public List<Channel> getMyChannel() {
        List<Channel> channels = new ArrayList<>();
        if (channelLayout != null && channelLayout.channelGroups.size() > 0) {
            for (View view : channelLayout.channelGroups.get(0)) {
                channels.add(((ChannelAttr) view.getTag()).channel);
            }
        }
        return channels;
    }

    public interface OnChannelListener {
        void channelItemClick(int position, Channel channel);

        void channelFinish(List<Channel> channelList);
    }

    public void setOnChannelItemClickListener(OnChannelListener onChannelListener) {
        this.onChannelListener = onChannelListener;
    }

    private class ChannelLayout extends GridLayout implements OnLongClickListener, OnClickListener, OnTouchListener {

        /**
         * 频道最小可拖动距离
         */
        private final int RANGE = 100;

        private final int DURATION_TIME = 200;

        /**
         * 频道普通点击
         */
        private final int NORMAL = 0X00;

        /**
         * 点击删除频道
         */
        private final int DELETE = 0x01;

        private int channelClickType = NORMAL;

        /**
         * 是否重新布局
         */
        private boolean isAgainLayout = true;

        private AnimatorSet animatorSet = new AnimatorSet();

        /**
         * 所有频道标题组
         */
        private List<View> channelTitleGroups = new ArrayList<>();

        /**
         * 所有频道组
         */
        private List<ArrayList<View>> channelGroups = new ArrayList<>();

        /**
         * 每组channel的行数
         */
        private int[] groupChannelColumns;

        /**
         * 所有channel组的高度
         */
        private int allChannelGroupsHeight;

        private TextView tipEdit, tipFinish;

        /**
         * 动态高度
         */
        private int animateHeight;

        /**
         * 是否通过动画改变高度
         */
        private boolean isAnimateChangeHeight;

        /**
         * 是否是编辑状态
         */
        private boolean isEditState;

        /**
         * 是否允许拖拽
         */
        private boolean isAccessDrag;

        /**
         * 可允许拖拽的最小间隔时间
         */
        private final int MIN_TIME_INTERVAL = 80;

        public ChannelLayout(Context context) {
            this(context, null);
        }

        public ChannelLayout(Context context, @Nullable AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public ChannelLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);//ChannelLayout的宽
            //不是通过动画改变ChannelLayout的高度
            if (!isAnimateChangeHeight) {
                int height = 0;
                int allChannelTitleHeight = 0;
                for (int i = 0; i < getChildCount(); i++) {
                    View childAt = getChildAt(i);
                    if (((ChannelAttr) childAt.getTag()).type == ChannelAttr.TITLE) {
                        //计算标题View的宽高
                        childAt.measure(MeasureSpec.makeMeasureSpec(width - channelPadding * 2, MeasureSpec.EXACTLY), heightMeasureSpec);
                        allChannelTitleHeight += childAt.getMeasuredHeight();
                    } else if (((ChannelAttr) childAt.getTag()).type == ChannelAttr.CHANNEL) {
                        //计算每个频道的宽高
                        channelWidth = (width - verticalSpacing * (channelColumn * 2 - 2) - channelPadding * 2) / channelColumn;
                        childAt.measure(MeasureSpec.makeMeasureSpec(channelWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(channelHeight, MeasureSpec.EXACTLY));
                    }
                }
                for (int groupChannelColumn : groupChannelColumns) {
                    if (groupChannelColumn > 0) {
                        height += channelHeight * groupChannelColumn + (groupChannelColumn * 2 - 2) * horizontalSpacing;
                    }
                }
                allChannelGroupsHeight = height;
                height += channelPadding * 2 + allChannelTitleHeight;//ChannelLayout的高
                setMeasuredDimension(width, height);
            } else {//通过动画改变ChannelLayout的高度
                setMeasuredDimension(width, animateHeight);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (isAgainLayout) {
                super.onLayout(changed, left, top, right, bottom);
                for (int i = 0; i < getChildCount(); i++) {
                    View childAt = getChildAt(i);
                    ChannelAttr tag = (ChannelAttr) childAt.getTag();
                    tag.coordinate.x = childAt.getX();
                    tag.coordinate.y = childAt.getY();
                }
                isAgainLayout = false;
            }
        }

        private void init() {
            setColumnCount(channelColumn);
            setPadding(channelPadding, channelPadding, channelPadding, channelPadding);
            addChannelView();
        }

        /**
         * 设置频道View
         */
        private void addChannelView() {
            if (channelContents != null) {
                groupChannelColumns = new int[channelContents.size()];
                int j = 0;
                int startRow = 0;
                for (String aKeySet : channelContents.keySet()) {//遍历key值，设置标题名称
                    List<Channel> channelContent = channelContents.get(aKeySet);
                    if (channelContent == null) {
                        channelContent = new ArrayList<>();
                    }
                    groupChannelColumns[j] = channelContent.size() % channelColumn == 0 ? channelContent.size() / channelColumn : channelContent.size() / channelColumn + 1;
                    if (j == 0) {
                        startRow = 0;
                    } else {
                        startRow += groupChannelColumns[j - 1] + 1;
                    }
                    Spec rowSpec = GridLayout.spec(startRow);
                    //标题要占channelColumn列
                    Spec columnSpec = GridLayout.spec(0, channelColumn);
                    LayoutParams layoutParams = new LayoutParams(rowSpec, columnSpec);
                    View view = LayoutInflater.from(mContext).inflate(R.layout.cgl_my_channel, null);
                    if (j == 0) {
                        tipEdit = view.findViewById(R.id.tv_tip_edit);
                        tipEdit.setVisibility(VISIBLE);
                        tipEdit.setOnClickListener(this);
                        tipFinish = view.findViewById(R.id.tv_tip_finish);
                        tipFinish.setVisibility(INVISIBLE);
                        tipFinish.setOnClickListener(this);
                    }
                    ChannelAttr channelTitleAttr = new ChannelAttr();
                    channelTitleAttr.type = ChannelAttr.TITLE;
                    channelTitleAttr.coordinate = new PointF();
                    //为标题View添加一个ChannelAttr属性
                    view.setTag(channelTitleAttr);
                    TextView tvTitle = view.findViewById(R.id.tv_title);
                    tvTitle.setText(aKeySet);
                    addView(view, layoutParams);
                    channelTitleGroups.add(view);
                    ArrayList<View> channelGroup = new ArrayList<>();
                    int remainder = channelContent.size() % channelColumn;
                    for (int i = 0; i < channelContent.size(); i++) {//遍历value中的频道
                        TextView textView = new TextView(mContext);
                        ChannelAttr channelAttr = new ChannelAttr();
                        channelAttr.type = ChannelAttr.CHANNEL;
                        channelAttr.groupIndex = j;
                        channelAttr.coordinate = new PointF();
                        channelAttr.channel = channelContent.get(i);
                        //为频道添加ChannelAttr属性
                        textView.setTag(channelAttr);
                        textView.setText(channelContent.get(i).channelName);
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundResource(channelNormalBackground);
                        if (j == 0 && i <= channelFixedToPosition) {
                            textView.setTextColor(Color.parseColor("#CCCCCC"));
                            fixedTextView.add(textView);
                            allTextView.remove(textView);
                        }
                        allTextView.add(textView);
                        if (j == 0) {
                            textView.setOnLongClickListener(this);
                            textView.setOnTouchListener(this);
                        }
                        textView.setOnClickListener(this);
                        //设置每个频道的间距
                        LayoutParams params = new LayoutParams();
                        int leftMargin = verticalSpacing, topMargin = horizontalSpacing, rightMargin = verticalSpacing, bottomMargin = horizontalSpacing;
                        if (i % channelColumn == 0) {
                            leftMargin = 0;
                        }
                        if ((i + 1) % channelColumn == 0) {
                            rightMargin = 0;
                        }
                        if (i < channelColumn) {
                            topMargin = 0;
                        }
                        if (remainder == 0) {
                            if (i >= channelContent.size() - channelColumn) {
                                bottomMargin = 0;
                            }
                        } else {
                            if (i >= channelContent.size() - remainder) {
                                bottomMargin = 0;
                            }
                        }
                        params.setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
                        addView(textView, params);
                        channelGroup.add(textView);
                    }
                    channelGroups.add(channelGroup);
                    j++;
                }
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
//            //如果点击的是我的频道组中的频道
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
                if (isEditState) {
                    setTime(v);
                }
            }
            if (isEditState) {
                if (event.getAction() == MotionEvent.ACTION_MOVE && isAccessDrag) {
                    //手移动时拖动频道
                    //请求ScrollView不要拦截MOVE事件，交给TextView处理
                    requestDisallowInterceptTouchEvent(true);
                    channelDrag(v, event);
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (thread != null && thread.isAlive() && !thread.isInterrupted()) {
                        thread.interrupt();
                    }
                    if (isAccessDrag) {
                        ChannelAttr vTag = (ChannelAttr) v.getTag();
                        v.animate().x(vTag.coordinate.x).y(vTag.coordinate.y).setDuration(DURATION_TIME);
                        v.setBackgroundResource(channelSelectedBackground);
                        isAccessDrag = false;
                        return true;
                    }
                }
            }
            return false;
        }

        private void setTime(final View v) {
            thread = new Thread() {
                @Override
                public void run() {
                    try {
                        sleep(MIN_TIME_INTERVAL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                    Message message = new Message();
                    message.obj = v;
                    handler.sendMessage(message);
                }
            };
            thread.start();
        }

        private Thread thread;

        private Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                View v = (View) msg.obj;
                v.bringToFront();
                v.setBackgroundResource(channelFocusedBackground);
                isAccessDrag = true;
            }
        };

        @Override
        public void onClick(View v) {
            if (v == tipEdit) {
                edit();
            } else if (v == tipFinish) {//点击完成按钮时
                changeTip(false);
                if (onChannelListener != null) {
                    onChannelListener.channelFinish(getMyChannel());
                }
            } else {
                ChannelAttr tag = (ChannelAttr) v.getTag();
                ArrayList<View> channels = channelGroups.get(tag.groupIndex);
                //如果点击的是我的频道组中的频道
                if (tag.groupIndex == 0) {
                    if (channelClickType == DELETE && channels.indexOf(v) > channelFixedToPosition) {
                        forwardSort(v, channels);
                        //减少我的频道
                        deleteMyChannel(v);
                    } else if (channelClickType == NORMAL) {
                        //普通状态时进行点击事件回调
                        if (onChannelListener != null) {
                            onChannelListener.channelItemClick(channels.indexOf(v), ((ChannelAttr) v.getTag()).channel);
                        }
                    }
                } else {//点击的其他频道组中的频道
                    forwardSort(v, channels);
                    //增加我的频道
                    addMyChannel(v);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (isEditState) {
                return true;
            }
            v.bringToFront();
            ArrayList<View> views = channelGroups.get(0);
            int indexOf = views.indexOf(v);
            if (indexOf > channelFixedToPosition) {
                for (int i = channelFixedToPosition + 1; i < views.size(); i++) {
                    if (i == indexOf) {
                        views.get(i).setBackgroundResource(channelFocusedBackground);
                    } else {
                        views.get(i).setBackgroundResource(channelSelectedBackground);
                    }
                }
                changeTip(true);
            }
            isAccessDrag = true;
            //要返回true，否则会触发onclick事件
            return true;
        }

        private void edit() {
            ArrayList<View> views = channelGroups.get(0);
            for (int i = channelFixedToPosition + 1; i < views.size(); i++) {
                views.get(i).setBackgroundResource(channelSelectedBackground);
            }
            changeTip(true);
        }

        /**
         * 后面的频道向前排序
         *
         * @param v
         * @param channels
         */
        private void forwardSort(View v, ArrayList<View> channels) {
            int size = channels.size();
            int indexOfValue = channels.indexOf(v);
            if (indexOfValue != size - 1) {
                for (int i = size - 1; i > indexOfValue; i--) {
                    View lastView = channels.get(i - 1);
                    ChannelAttr lastViewTag = (ChannelAttr) lastView.getTag();
                    View currentView = channels.get(i);
                    ChannelAttr currentViewTag = (ChannelAttr) currentView.getTag();
                    currentViewTag.coordinate = lastViewTag.coordinate;
                    currentView.animate().x(currentViewTag.coordinate.x).y(currentViewTag.coordinate.y).setDuration(DURATION_TIME);
                }
            }
        }

        /**
         * 增加我的频道
         *
         * @param v
         */
        private void addMyChannel(final View v) {
            //让点击的view置于最前方，避免遮挡
            v.bringToFront();
            ChannelAttr tag = (ChannelAttr) v.getTag();
            ArrayList<View> channels = channelGroups.get(tag.groupIndex);
            ArrayList<View> myChannels = channelGroups.get(0);
            View finalMyChannel;
            if (myChannels.size() == 0) {
                finalMyChannel = channelTitleGroups.get(0);
            } else {
                finalMyChannel = myChannels.get(myChannels.size() - 1);
            }
            ChannelAttr finalMyChannelTag = (ChannelAttr) finalMyChannel.getTag();
            myChannels.add(myChannels.size(), v);
            channels.remove(v);
            v.setOnLongClickListener(this);
            v.setOnTouchListener(this);
            animateChangeGridViewHeight();
            final ViewPropertyAnimator animate = v.animate();
            if (myChannels.size() % channelColumn == 1 || channelColumn == 1) {
                if (myChannels.size() == 1) {
                    tag.coordinate = new PointF(finalMyChannelTag.coordinate.x, finalMyChannelTag.coordinate.y + finalMyChannel.getMeasuredHeight());
                    //我的频道多一行，下面的view往下移
                    viewMove(1, channelHeight);
                } else {
                    ChannelAttr firstMyChannelTag = (ChannelAttr) myChannels.get(0).getTag();
                    tag.coordinate = new PointF(firstMyChannelTag.coordinate.x, finalMyChannelTag.coordinate.y + channelHeight + horizontalSpacing * 2);
                    //我的频道多一行，下面的view往下移
                    viewMove(1, channelHeight + horizontalSpacing * 2);
                }
                animate.x(tag.coordinate.x).y(tag.coordinate.y).setDuration(DURATION_TIME);
            } else {
                tag.coordinate = new PointF(finalMyChannelTag.coordinate.x + channelWidth + verticalSpacing * 2, finalMyChannelTag.coordinate.y);
                animate.x(tag.coordinate.x).y(tag.coordinate.y).setDuration(DURATION_TIME);
            }
            if (channelClickType == DELETE) {
                v.setBackgroundResource(channelSelectedBackground);
            }
            //该频道少一行，下面的view往上移
            if (channels.size() % channelColumn == 0) {
                if (channels.size() == 0) {
                    viewMove(tag.groupIndex + 1, -channelHeight);
                } else {
                    viewMove(tag.groupIndex + 1, -channelHeight - horizontalSpacing * 2);
                }
            }
            tag.groupIndex = 0;
        }

        /**
         * 删除我的频道
         *
         * @param v
         */
        private void deleteMyChannel(View v) {
            //让点击的view置于最前方，避免遮挡
            v.bringToFront();
            if (channelClickType == DELETE) {
                v.setBackgroundResource(channelNormalBackground);
            }
            ChannelAttr tag = (ChannelAttr) v.getTag();
            int belong = tag.channel.channelBelong;
            if (belong < 1 || belong > channelContents.size() - 1) {
                belong = 1;
            }
            ArrayList<View> beLongChannels = channelGroups.get(belong);
            if (beLongChannels.size() == 0) {
                tag.coordinate = new PointF(((ChannelAttr) channelTitleGroups.get(belong).getTag()).coordinate.x, ((ChannelAttr) channelTitleGroups.get(belong).getTag()).coordinate.y + channelTitleGroups.get(belong).getMeasuredHeight());
            } else {
                ChannelAttr arriveTag = (ChannelAttr) beLongChannels.get(0).getTag();
                tag.coordinate = arriveTag.coordinate;
            }
            v.animate().x(tag.coordinate.x).y(tag.coordinate.y).setDuration(DURATION_TIME);
            beLongChannels.add(0, v);
            channelGroups.get(0).remove(v);
            v.setOnLongClickListener(null);
            v.setOnTouchListener(null);
            animateChangeGridViewHeight();
            PointF newPointF;
            ChannelAttr finalChannelViewTag = (ChannelAttr) beLongChannels.get(beLongChannels.size() - 1).getTag();
            //这个地方要注意顺序
            if (channelGroups.get(0).size() % channelColumn == 0) {
                //我的频道中少了一行，底下的所有view全都上移
                if (channelGroups.get(0).size() == 0) {
                    viewMove(1, -channelHeight);
                } else {
                    viewMove(1, -channelHeight - horizontalSpacing * 2);
                }
            }
            if (beLongChannels.size() % channelColumn == 1) {
                //回收来频道中多了一行，底下的所有view全都下移
                if (beLongChannels.size() == 1) {
                    viewMove(belong + 1, channelHeight);
                } else {
                    viewMove(belong + 1, channelHeight + horizontalSpacing * 2);
                }
                newPointF = new PointF(tag.coordinate.x, finalChannelViewTag.coordinate.y + channelHeight + horizontalSpacing * 2);
            } else {
                newPointF = new PointF(finalChannelViewTag.coordinate.x + channelWidth + verticalSpacing * 2, finalChannelViewTag.coordinate.y);
            }
            for (int i = 1; i < beLongChannels.size(); i++) {
                View currentView = beLongChannels.get(i);
                ChannelAttr currentViewTag = (ChannelAttr) currentView.getTag();
                if (i < beLongChannels.size() - 1) {
                    View nextView = beLongChannels.get(i + 1);
                    ChannelAttr nextViewTag = (ChannelAttr) nextView.getTag();
                    currentViewTag.coordinate = nextViewTag.coordinate;
                } else {
                    currentViewTag.coordinate = newPointF;
                }
                currentView.animate().x(currentViewTag.coordinate.x).y(currentViewTag.coordinate.y).setDuration(DURATION_TIME);
            }
            tag.groupIndex = belong;
        }

        /**
         * 行数变化后的gridview高度并用动画改变
         */
        private void animateChangeGridViewHeight() {
            int newAllChannelGroupsHeight = 0;
            for (int i = 0; i < channelGroups.size(); i++) {
                ArrayList<View> channels = channelGroups.get(i);
                groupChannelColumns[i] = channels.size() % channelColumn == 0 ? channels.size() / channelColumn : channels.size() / channelColumn + 1;
            }
            for (int groupChannelColumn : groupChannelColumns) {
                if (groupChannelColumn > 0) {
                    newAllChannelGroupsHeight += channelHeight * groupChannelColumn + (groupChannelColumn * 2 - 2) * horizontalSpacing;
                }
            }
            int changeHeight = newAllChannelGroupsHeight - allChannelGroupsHeight;
            if (changeHeight != 0) {
                allChannelGroupsHeight = newAllChannelGroupsHeight;
                ValueAnimator valueAnimator = ValueAnimator.ofInt(getMeasuredHeight(), getMeasuredHeight() + changeHeight);
                valueAnimator.setDuration(DURATION_TIME);
                valueAnimator.start();
                valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        animateHeight = (int) animation.getAnimatedValue();
                        isAnimateChangeHeight = true;
                        requestLayout();
                    }
                });
                valueAnimator.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimateChangeHeight = false;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
            }
        }

        /**
         * 受到行数所影响的view进行上移或下移操作
         */
        private void viewMove(int position, int offSetY) {
            for (int i = position; i < channelTitleGroups.size(); i++) {
                View view = channelTitleGroups.get(i);
                ChannelAttr tag = (ChannelAttr) view.getTag();
                tag.coordinate = new PointF(tag.coordinate.x, tag.coordinate.y + offSetY);
                view.animate().x(tag.coordinate.x).y(tag.coordinate.y).setDuration(DURATION_TIME);
            }
            for (int i = position; i < channelGroups.size(); i++) {
                ArrayList<View> otherChannels = channelGroups.get(i);
                for (int j = 0; j < otherChannels.size(); j++) {
                    View view = otherChannels.get(j);
                    ChannelAttr tag = (ChannelAttr) view.getTag();
                    tag.coordinate = new PointF(tag.coordinate.x, tag.coordinate.y + offSetY);
                    view.animate().x(tag.coordinate.x).y(tag.coordinate.y).setDuration(DURATION_TIME);
                }
            }
        }

        float downX, downY;
        float moveX, moveY;

        /**
         * 频道拖动
         */
        private void channelDrag(View v, MotionEvent event) {
            moveX = event.getRawX();
            moveY = event.getRawY();
            v.setX(v.getX() + (moveX - downX));
            v.setY(v.getY() + (moveY - downY));
            downX = moveX;
            downY = moveY;
            ArrayList<View> myChannels = channelGroups.get(0);
            ChannelAttr vTag = (ChannelAttr) v.getTag();
            int vIndex = myChannels.indexOf(v);
            for (int i = 0; i < myChannels.size(); i++) {
                if (i > channelFixedToPosition && i != vIndex) {
                    View iChannel = myChannels.get(i);
                    ChannelAttr iChannelTag = (ChannelAttr) iChannel.getTag();
                    int x1 = (int) iChannelTag.coordinate.x;
                    int y1 = (int) iChannelTag.coordinate.y;
                    int sqrt = (int) Math.sqrt((v.getX() - x1) * (v.getX() - x1) + (v.getY() - y1) * (v.getY() - y1));
                    if (sqrt <= RANGE && !animatorSet.isRunning()) {
                        animatorSet = new AnimatorSet();
                        PointF tempPoint = iChannelTag.coordinate;
                        ObjectAnimator[] objectAnimators = new ObjectAnimator[Math.abs(i - vIndex) * 2];
                        if (i < vIndex) {
                            for (int j = i; j < vIndex; j++) {
                                TextView view = (TextView) myChannels.get(j);
                                ChannelAttr viewTag = (ChannelAttr) view.getTag();
                                ChannelAttr nextGridViewAttr = ((ChannelAttr) myChannels.get(j + 1).getTag());
                                viewTag.coordinate = nextGridViewAttr.coordinate;
                                objectAnimators[2 * (j - i)] = ObjectAnimator.ofFloat(view, "X", viewTag.coordinate.x);
                                objectAnimators[2 * (j - i) + 1] = ObjectAnimator.ofFloat(view, "Y", viewTag.coordinate.y);
                            }
                        } else {
                            for (int j = i; j > vIndex; j--) {
                                TextView view = (TextView) myChannels.get(j);
                                ChannelAttr viewTag = (ChannelAttr) view.getTag();
                                ChannelAttr preGridViewAttr = ((ChannelAttr) myChannels.get(j - 1).getTag());
                                viewTag.coordinate = preGridViewAttr.coordinate;
                                objectAnimators[2 * (j - vIndex - 1)] = ObjectAnimator.ofFloat(view, "X", viewTag.coordinate.x);
                                objectAnimators[2 * (j - vIndex - 1) + 1] = ObjectAnimator.ofFloat(view, "Y", viewTag.coordinate.y);
                            }
                        }
                        animatorSet.playTogether(objectAnimators);
                        animatorSet.setDuration(DURATION_TIME);
                        animatorSet.start();
                        vTag.coordinate = tempPoint;
                        myChannels.remove(v);
                        myChannels.add(i, v);
                        break;
                    }
                }
            }
        }

        /**
         * 更改提示语
         *
         * @param state
         */
        private void changeTip(boolean state) {
            ArrayList<View> views = channelGroups.get(0);
            if (state) {
                tipFinish.setVisibility(VISIBLE);
                tipEdit.setVisibility(INVISIBLE);
                channelClickType = DELETE;
                isEditState = true;
            } else {
                tipFinish.setVisibility(INVISIBLE);
                tipEdit.setVisibility(VISIBLE);
                channelClickType = NORMAL;
                isEditState = false;
                for (int i = 0; i < views.size(); i++) {
                    if (i > channelFixedToPosition) {
                        views.get(i).setBackgroundResource(channelNormalBackground);
                    }
                }
            }
        }
    }
}
