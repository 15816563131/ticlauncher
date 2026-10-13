package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    /** 列表形态（纵向曲线列表）—— 官方「列表视图」 */
    public static final int MODE_LIST = 0;
    /** 星环形态（同心圆 + 可旋转，见 {@link RadialAppView}）—— 官方「行星视图」 */
    public static final int MODE_STAR = 1;
    /** 宫格形态（圆形图标网格）—— 官方「宫格视图」 */
    public static final int MODE_GRID = 2;

    private final Activity mActivity;
    private final List<AppEntry> mApps = new ArrayList<>();
    private final Set<Integer> mAnimated = new HashSet<>();
    private int mMode = MODE_LIST;

    public AppListAdapter(Activity activity, List<AppEntry> apps) {
        mActivity = activity;
        mApps.addAll(apps);
    }

    /** 切换列表形态（影响条目布局与入场动效节奏）。 */
    public void setMode(int mode) {
        if (mMode != mode) {
            mMode = mode;
            mAnimated.clear();
            notifyDataSetChanged();
        }
    }

    /** 重新播放入场动效。 */
    public void replayEnter() {
        mAnimated.clear();
        notifyDataSetChanged();
    }

    public void updateApps(List<AppEntry> apps) {
        mApps.clear();
        mApps.addAll(apps);
        mAnimated.clear();
        notifyDataSetChanged();
    }

    /** 进出场动效：首次绑定淡入 + 轻微放大，逐条延迟。 */
    private void playEnter(View v, int position) {
        if (!mAnimated.add(position)) {
            v.setAlpha(1f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        v.setAlpha(0f);
        v.setScaleX(0.84f);
        v.setScaleY(0.84f);
        v.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(Math.min(position, mMode == MODE_GRID ? 12 : 7)
                        * (mMode == MODE_GRID ? 24L : 38L))
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = mMode == MODE_GRID ? R.layout.item_app_grid : R.layout.item_app;
        View v = LayoutInflater.from(mActivity).inflate(layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        final AppEntry app = mApps.get(position);
        playEnter(h.itemView, position);
        h.name.setText(app.label);
        h.icon.setImageDrawable(CircleIconDrawable.of(iconOf(app)));
        if (app.type == AppEntry.TYPE_APP) {
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(app);
                }
            });
            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showMenu(app);
                    return true;
                }
            });
        } else {
            final boolean isFace = app.type == AppEntry.TYPE_WATCHFACE;
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Class<?> target = isFace ? WatchFaceActivity.class : SettingsActivity.class;
                    try {
                        mActivity.startActivity(new Intent(mActivity, target));
                    } catch (Exception ignored) {
                    }
                }
            });
            h.itemView.setOnLongClickListener(null);
        }
    }

    private Drawable iconOf(AppEntry app) {
        if (app.icon != null) {
            return app.icon;
        }
        if (app.type == AppEntry.TYPE_WATCHFACE) {
            return mActivity.getDrawable(R.drawable.ic_watchface);
        }
        if (app.type == AppEntry.TYPE_SETTINGS) {
            return mActivity.getDrawable(R.drawable.ic_app_settings);
        }
        return null;
    }

    private void launch(AppEntry app) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(app.component);
        // 限定在本应用内启动，避免同名 HeyTap action 被 OPPO 官方 APK 抢走（见 HANDOVER 4.3）。
        i.setPackage(app.component.getPackageName());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mActivity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(mActivity, "无法启动 " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showMenu(AppEntry app) {
        Intent i = new Intent(mActivity, AppMenuActivity.class);
        i.putExtra(AppMenuActivity.EXTRA_PACKAGE, app.component.getPackageName());
        i.putExtra(AppMenuActivity.EXTRA_CLASS, app.component.getClassName());
        i.putExtra(AppMenuActivity.EXTRA_LABEL, app.label);
        try {
            mActivity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(mActivity, "无法打开菜单", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return mApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;

        ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.app_icon);
            name = v.findViewById(R.id.app_name);
        }
    }
}
