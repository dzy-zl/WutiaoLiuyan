package com.wutiaoliuyan.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.IntentCompat
import com.wutiaoliuyan.app.ui.AppViewModel
import com.wutiaoliuyan.app.ui.WutiaoLiuyanRoot

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf(parse(Intent()))
    private var incomingVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming = parse(intent)
        setContent {
            val vm: AppViewModel = viewModel()
            LaunchedEffect(incomingVersion) {
                if (incoming.uris.isNotEmpty() || incoming.text.isNotBlank()) vm.acceptShared(incoming.uris, incoming.text)
                incoming.openTaskId?.let(vm::openTaskById)
            }
            WutiaoLiuyanRoot(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = parse(intent)
        incomingVersion++
    }

    private fun parse(intent: Intent): IncomingPayload {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        val taskId = intent.getLongExtra("open_task_id", -1L).takeIf { it > 0 }
        return IncomingPayload(uris, text, taskId)
    }
}

data class IncomingPayload(val uris: List<Uri> = emptyList(), val text: String = "", val openTaskId: Long? = null)
