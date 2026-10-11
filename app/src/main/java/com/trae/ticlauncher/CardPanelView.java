package com.trae.ticlauncher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;

/**
 * 卡片页：表盘右侧的 ColorOS 风格快捷入口（运动 / 心率 / 音乐 / 天气）。
 *
 * 使用 XML 布局 + oppoicons drawable，点击有平滑的缩小过渡动画，
 * 动画结束后触发外部监听器启动对应应用。
 */
public class CardPanelView extends LinearLayout {

    public interface OnCardClickListener {
        void onCardClick(int index);
    }

    private static final int[] CARD_IDS = {
            R.id.card_0, R.id.card_1, R.id.card_2, R.id.card_3
    };

    private final LinearLayout[] mCards = new LinearLayout[CARD_IDS.length];
    private OnCardClickListener mListener;

    public CardPanelView(Context context) {
        this(context, null);
    }

    public CardPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardPanelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.panel_cards, this, true);
        setOrientation(VERTICAL);

        for (int i = 0; i < CARD_IDS.length; i++) {
            mCards[i] = findViewById(CARD_IDS[i]);
            final int index = i;
            mCards[i].setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onCardClicked(index);
                }
            });
            mCards[i].setOnTouchListener(new android.view.View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            v.animate().setDuration(120L).scaleX(0.94f).scaleY(0.94f).start();
                            break;
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                        case android.view.MotionEvent.ACTION_OUTSIDE:
                            v.animate().setDuration(120L).scaleX(1f).scaleY(1f).start();
                            break;
                    }
                    return false;
                }
            });
        }
    }

    public void setOnCardClickListener(OnCardClickListener l) {
        mListener = l;
    }

    /** 卡片点击：立即启动目标应用，并复位按下态。 */
    private void onCardClicked(final int index) {
        // 不再「先播完 180ms 缩放动画再启动」——那会让每次点卡片都有一拍迟滞感，
        // 目标应用也晚 180ms 才出现。这里直接启动，视觉上由触摸反馈负责。
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
        if (mListener != null) {
            mListener.onCardClick(index);
        }
    }
}
