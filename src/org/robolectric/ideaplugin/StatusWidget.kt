package org.robolectric.ideaplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import java.awt.Component

class StatusWidget(val project: Project) :
    StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation {
  var currentSdk: Int? = null

  private var myStatusBar: StatusBar? = null

  override fun getClickConsumer() = null

  override fun getAlignment() = Component.CENTER_ALIGNMENT

  override fun getText() = "Android SDK ${currentSdk ?: "Unknown"}"

  override fun getTooltipText() = "Currently active Robolectric Android SDK"

  override fun getMaxPossibleText() = "Android SDK: 9999"

  override fun install(statusBar: StatusBar) {
    myStatusBar = statusBar
  }

  override fun dispose() {
    Disposer.dispose(this)
//    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", this)
    myStatusBar = null
  }

  override fun ID() = StatusWidget::class.qualifiedName!!

  override fun getPresentation(type: StatusBarWidget.PlatformType): StatusBarWidget.WidgetPresentation? {
    return this
  }

  override fun copy(): StatusBarWidget {
    return StatusWidget(project)
  }
}
