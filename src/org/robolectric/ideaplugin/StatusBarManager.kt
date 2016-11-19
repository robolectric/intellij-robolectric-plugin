package org.robolectric.ideaplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.messages.MessageBusConnection
import java.util.concurrent.atomic.AtomicBoolean

class StatusBarManager(private val myProject: Project) : Disposable {
  private val opened = AtomicBoolean()
  private val myConnection: MessageBusConnection
  private var statusWidget = StatusWidget(myProject)

  init {
    myConnection = myProject.messageBus.connect()
    myConnection.subscribe(Notifier.Topics.DEBUG_TOPIC, object : Notifier.DebugListener {
      override fun sdkChanged(sdkLevel: Int?) {
        statusWidget.changeTo(sdkLevel)

        statusBar()?.updateWidget(statusWidget.ID())
      }
    })
  }

  private fun install() {
    statusBar()?.addWidget(statusWidget, myProject)
  }

  private fun uninstall() {
    statusBar()?.removeWidget(statusWidget.ID())
  }

  private fun statusBar() = WindowManager.getInstance().getStatusBar(myProject)

  fun opened() {
    if (opened.compareAndSet(false, true)) {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
//        statusWidget = StatusWidget.create(myProject)
        install()
//        statusWidget!!.setVisible(GitToolBoxConfig.getInstance().showStatusWidget)
      }
    }
  }

  fun closed() {
    opened.compareAndSet(true, false)
  }

  override fun dispose() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
//      if (statusWidget != null) {
        uninstall()
//        statusWidget = null
//      }
    }
    myConnection.disconnect()
  }
}

