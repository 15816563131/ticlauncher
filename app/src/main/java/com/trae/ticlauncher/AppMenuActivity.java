package com.trae.ticlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 长按应用弹出的 ColorOS 风格操作浮层（打开 / 应用信息 / 卸载）。
 * 独立全屏 Activity，圆形屏幕下比系统 AlertDialog 更贴合。
 */
public class AppMenuActivity extends Activity {

    public static final String EXTRA_PACKAGE = "pkg";
    public static final String EXTRA_CLASS = "cls";
    public static final String EXTRA_LABEL = "label";

    private String mPkg;
    private String mCls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_menu);

        Intent it = getIntent();
        mPkg = it.getStringExtra(EXTRA_PACKAGE);
        mCls = it.getStringExtra(EXTRA_CLASS);
        String label = it.getStringExtra(EXTRA_LABEL);

        ImageView icon = findViewById(R.id.menu_app_icon);
        TextView name = findViewById(R.id.menu_app_name);
        if (mPkg != null) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(mPkg, 0);
                icon.setImageDrawable(pm.getApplicationIcon(ai));
                if (label == null) {
                    label = pm.getApplicationLabel(ai).toString();
                }
            } catch (Exception ignored) {
            }
        }
        name.setText(label == null ? "" : label);

        findViewById(R.id.menu_open).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch();
                finish();
            }
        });

        findViewById(R.id.menu_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppInfo();
                finish();
            }
        });

        findViewById(R.id.menu_uninstall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uninstall();
                finish();
            }
        });

        // 点空白处关闭
        findViewById(R.id.menu_root).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void launch() {
        if (mPkg == null || mCls == null) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(mPkg, mCls));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("无法启动");
        }
    }

    private void openAppInfo() {
        if (mPkg == null) {
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + mPkg));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("无法打开应用信息");
        }
    }

    private void uninstall() {
        if (mPkg == null) {
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + mPkg));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            openAppInfo();
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
