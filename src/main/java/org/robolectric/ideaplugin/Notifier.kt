package org.robolectric.ideaplugin

import com.intellij.util.messages.Topic

class Notifier {
  class Notifier object Topics {
    val DEBUG_TOPIC: Topic<DebugListener> = Topic.create("${Notifier::class.qualifiedName}::DEBUG_TOPIC", DebugListener::class.java)
  }

  interface DebugListener {
    fun sdkChanged(androidSdk: AndroidSdk?): Unit
  }
}