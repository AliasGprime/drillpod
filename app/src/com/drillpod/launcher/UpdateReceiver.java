package com.drillpod.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Receives the result of an update installation (Android's package installer). */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android asks the user to confirm the update: show its confirmation screen.
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(confirm); } catch (Exception e) { MainActivity.updateFinished(false, String.valueOf(e.getMessage())); }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            MainActivity.updateFinished(true, "");
        } else {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            MainActivity.updateFinished(false, status == PackageInstaller.STATUS_FAILURE_ABORTED ? "aborted" : String.valueOf(msg));
        }
    }
}
