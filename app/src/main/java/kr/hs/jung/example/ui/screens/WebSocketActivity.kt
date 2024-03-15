package kr.hs.jung.example.ui.screens

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.hs.jung.example.ui.theme.ExampleApplicationTheme
import kr.hs.jung.example.utils.WebSocketClient

class WebSocketActivity: ComponentActivity() {
    private val webSocketClient = WebSocketClient("http://172.22.7.6:8443/helloworld")

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("WebSocketActivity", "onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            ExampleApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebSocketTestButtons(webSocketClient)
                }
            }
        }
    }
}

@Composable
fun WebSocketTestButtons(webSocketClient: WebSocketClient) {
    Column(
        modifier = Modifier.padding(8.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {
                Log.d("WebSocketActivity", "Connect Button")
                webSocketClient.connect()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Connect to server")
        }
        Button(
            onClick = {
                Log.d("WebSocketActivity", "Disconnect Button")
                webSocketClient.disconnect()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Disconnect to server")
        }
    }
}