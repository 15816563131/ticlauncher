package com.trae.ticlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/** 桌面相关工具：返回系统自带桌面（备用出口） */
public final class HomeHelper {

    private HomeHelper() {
    }

    /** 启动系统自带桌面（排除本应用），作为随时退回主屏的暗门 */
    public static boolean openStockHome(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> list = pm.queryIntentActivities(home, 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (ctx.getPackageName().equals(pkg)) {
                continue;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setComponent(new ComponentName(pkg, ri.activityInfo.name));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                ctx.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
