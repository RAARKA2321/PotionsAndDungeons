package com.example.potionsanddungeons

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[AndroidGameSession::class.java]
        setContent { GameApp(model.session, Modifier.safeDrawingPadding()) }
    }
}

internal class AndroidGameSession(application: Application) : AndroidViewModel(application) {
    val session = GameSession(androidPreferences(application, "saved_match"))

    override fun onCleared() {
        session.closeLanRoom()
        super.onCleared()
    }
}
