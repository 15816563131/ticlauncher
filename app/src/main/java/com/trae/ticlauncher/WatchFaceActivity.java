package com.trae.ticlauncher;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 列出已安装表盘（WallpaperService），点击切换。ColorOS 网格风格 */
public class WatchFaceActivity extends Activity {

    private final List<Face> mFaces = new ArrayList<>();

    static class Face {
        String label;
        Drawable preview;
        ComponentName component;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watchface);

        loadFaces();

        GridView grid = findViewById(R.id.face_grid);
        TextView empty = findViewById(R.id.face_empty);

        if (mFaces.isEmpty()) {
            grid.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            grid.setAdapter(new FaceAdapter());
            grid.setOnItemClickListener((parent, view, position, id) -> applyFace(mFaces.get(position)));
        }
    }

    private void loadFaces() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        List<ResolveInfo> services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        for (ResolveInfo ri : services) {
            ServiceInfo si = ri.serviceInfo;
            if (si == null) continue;
            Face f = new Face();
            f.label = si.loadLabel(pm).toString();
            f.component = new ComponentName(si.packageName, si.name);
            f.preview = loadPreview(pm, si);
            mFaces.add(f);
        }
    }

    private Drawable loadPreview(PackageManager pm, ServiceInfo si) {
        Bundle meta = si.metaData;
        if (meta == null) return null;
        int resId = meta.getInt("com.google.android.wearable.watchface.preview_circular", 0);
        if (resId == 0) resId = meta.getInt("com.google.android.wearable.watchface.preview", 0);
        if (resId == 0) return null;
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                return pm.getResourcesForApplication(si.packageName).getDrawable(resId, null);
            }
            return pm.getResourcesForApplication(si.packageName).getDrawable(resId);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyFace(Face f) {
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, f.component);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "无法切换表盘", Toast.LENGTH_SHORT).show();
        }
    }

    private class FaceAdapter extends BaseAdapter {
        @Override public int getCount() { return mFaces.size(); }
        @Override public Object getItem(int position) { return mFaces.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(WatchFaceActivity.this)
                        .inflate(R.layout.item_watchface, parent, false);
            }
            Face f = mFaces.get(position);
            ImageView icon = convertView.findViewById(R.id.face_icon);
            TextView name = convertView.findViewById(R.id.face_name);
            if (f.preview != null) {
                icon.setImageDrawable(f.preview);
            } else {
                icon.setImageResource(R.drawable.ic_watchface);
            }
            name.setText(f.label);
            return convertView;
        }
    }
}
