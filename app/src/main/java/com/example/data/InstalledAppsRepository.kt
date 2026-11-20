package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.LaunchableAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(private val context: Context) {

  suspend fun getLaunchableApps(): List<LaunchableAppInfo> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val launcherIntent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

      val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0L),
          )
        } else {
          @Suppress("DEPRECATION")
          pm.queryIntentActivities(launcherIntent, 0)
        }

      val ownPackage = context.packageName
      val seenPackages = HashSet<String>()
      resolveInfos
        .mapNotNull { info ->
          val activityInfo = info.activityInfo ?: return@mapNotNull null
          val packageName = activityInfo.packageName ?: return@mapNotNull null
          if (packageName == ownPackage || !seenPackages.add(packageName)) {
            return@mapNotNull null
          }
          val label = info.loadLabel(pm).toString().trim().ifEmpty { packageName }
          val uid = activityInfo.applicationInfo.uid
          LaunchableAppInfo(
            packageName = packageName,
            label = label,
            uid = uid,
          )
        }
        .sortedBy { it.label.lowercase() }
    }
}
