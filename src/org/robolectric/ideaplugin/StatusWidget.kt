package org.robolectric.ideaplugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ClickListener
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.MouseEvent
import javax.swing.Icon

class StatusWidget(val project: Project) : StatusBarWidget.Multiframe, CustomStatusBarWidget {
  private val ICON = IconLoader.getIcon("/icons/robolectric-marker.png")
  private val GHOSTED_ICON : Icon = ghostIcon(ICON, 0.66f)
  private var myStatusBar: StatusBar? = null
  private var ghosted: Boolean = false
  private val myComponent: TextPanel = object : TextPanel.ExtraSize() {
    private val GAP = 2

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (text != null) {
        val r = bounds
        val insets = insets
        val icon = if (ghosted) GHOSTED_ICON else ICON
        icon.paintIcon(this, g, insets.left - GAP - ICON.iconWidth, r.height / 2 - ICON.iconHeight / 2)
      }
    }

    override fun getInsets(): Insets {
      val insets = super.getInsets()
      insets.left += ICON.iconWidth + GAP * 2
      return insets
    }

    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      return Dimension(preferredSize.width + GAP * 2, preferredSize.height)
    }

    init {
      toolTipText = "Currently active Robolectric Android SDK"
      setTextAlignment(Component.RIGHT_ALIGNMENT)

      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          // no op right now
          return true
        }
      }.installOn(this)
    }
  }

  fun changeTo(sdkLevel: AndroidSdk?) {
    if (sdkLevel == null) {
      ghosted = true
      myComponent.text = ""
      myComponent.foreground = UIUtil.getInactiveTextColor()
    } else {
      ghosted = false
      myComponent.text = "SDK ${sdkLevel.apiLevel} (${sdkLevel.sdkVersion})"
      myComponent.foreground = UIUtil.getActiveTextColor()
    }
  }

  override fun getComponent() = myComponent

  override fun install(statusBar: StatusBar) {
    myStatusBar = statusBar
    changeTo(null)
  }

  override fun dispose() {
    Disposer.dispose(this)
//    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", this)
    myStatusBar = null
  }

  override fun ID() = StatusWidget::class.qualifiedName!!

  override fun getPresentation(type: StatusBarWidget.PlatformType) = null

  override fun copy(): StatusBarWidget {
    return StatusWidget(project)
  }
}
