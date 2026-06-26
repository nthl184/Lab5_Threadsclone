package com.example.lab5

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {

    private val viewModel = MainViewModel()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Xin quyền cho Android 13 trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Surface {
                    ThreadsApp(viewModel = viewModel)
                }
            }
        }
    }
}

// Đặt hàm này ở cấp top-level hoặc companion object để gọi ở mọi nơi
fun showLocalNotification(context: Context, title: String, body: String, postId: String) {
    val channelId = "comments"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    manager.createNotificationChannel(
        NotificationChannel(channelId, "Bình luận", NotificationManager.IMPORTANCE_HIGH)
    )

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("postId", postId)
    }

    val pi = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground) // Đảm bảo icon này có trong res/drawable
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setContentIntent(pi)
        .build()

    manager.notify(System.currentTimeMillis().toInt(), notification)
}