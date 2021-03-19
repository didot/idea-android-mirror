/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ClipData
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.Notification.EventType.DISPLAY_CONFIGURATIONS_CHANGED_UI
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_ACTIVE
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_INACTIVE
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.RotationRadian
import com.android.emulator.control.ThemingStyle
import com.android.ide.common.util.Cancelable
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.analytics.toProto
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.Empty
import com.google.common.annotations.VisibleForTesting
import com.google.protobuf.TextFormat.shortDebugString
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.util.Alarm
import com.intellij.util.SofterReference
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.HdrHistogram.Histogram
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.color.ColorSpace
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.lang.Math.PI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import com.android.emulator.control.Image as ImageMessage
import com.android.emulator.control.MouseEvent as MouseEventMessage
import com.android.emulator.control.Notification as EmulatorNotification

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param disposableParent the disposable parent
 * @param emulator the handle of the Emulator
 * @param displayId the ID of the device display
 * @param displaySize the size of the device display; a null value defaults to `emulator.emulatorConfig.displaySize`
 * @param deviceFrameVisible controls visibility of the device frame
 */
class EmulatorView(
  disposableParent: Disposable,
  val emulator: EmulatorController,
  val displayId: Int,
  private val displaySize: Dimension?,
  deviceFrameVisible: Boolean
) : JPanel(BorderLayout()), ComponentListener, ConnectionStateListener, Zoomable, Disposable {

  private var disconnectedStateLabel: JLabel
  private var lastScreenshot: Screenshot? = null
  private var displayRectangle: Rectangle? = null
  private val displayTransform = AffineTransform()
  private val screenshotShape
    get() = lastScreenshot?.displayShape ?: DisplayShape(0, 0, initialOrientation)
  private val initialOrientation
    get() = if (displayId == PRIMARY_DISPLAY_ID) emulator.emulatorConfig.initialOrientation else SkinRotation.PORTRAIT
  private val deviceDisplaySize
    get() = displaySize ?: emulator.emulatorConfig.displaySize
  /** Count of received display frames. */
  @VisibleForTesting
  var frameNumber = 0
    private set
  /** Time of the last frame update in milliseconds since epoch. */
  @VisibleForTesting
  var frameTimestampMillis = 0L
    private set

  private var screenshotFeed: Cancelable? = null
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null

  private var clipboardFeed: Cancelable? = null
  @Volatile
  private var clipboardReceiver: ClipboardReceiver? = null

  private var notificationFeed: Cancelable? = null
  @Volatile
  private var notificationReceiver: NotificationReceiver? = null

  private val displayConfigurationListeners: MutableList<DisplayConfigurationListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  var displayRotation: SkinRotation
    get() = screenshotShape.rotation
    set(value) {
      if (value != screenshotShape.rotation && deviceFrameVisible) {
        requestScreenshotFeed(value)
      }
    }

  var deviceFrameVisible: Boolean = deviceFrameVisible
    set(value) {
      if (field != value) {
        field = value
        requestScreenshotFeed()
      }
    }

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  private var screenScale = 0.0 // Scale factor of the host screen.
    get() {
      if (field == 0.0) {
        field = graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
      }
      return field
    }

  /** Width in physical pixels. */
  private val realWidth
    get() = width.scaled(screenScale)

  /** Height in physical pixels. */
  private val realHeight
    get() = height.scaled(screenScale)

  /** Size in physical pixels. */
  private val realSize
    get() = Dimension(realWidth, realHeight)

  override val screenScalingFactor
    get() = screenScale

  override val scale: Double
    get() = computeScaleToFit(realSize, screenshotShape.rotation)

  /**
   * The size of the device including frame in device pixels.
   */
  val displaySizeWithFrame: Dimension
    get() = computeActualSize(screenshotShape.rotation)

  private var virtualSceneCameraActive = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          if (isFocusOwner) {
            showVirtualSceneCameraPrompt()
          }
        }
        else {
          virtualSceneCameraOperating = false
          hideVirtualSceneCameraPrompt()
        }
      }
    }

  private var virtualSceneCameraOperating = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          startOperatingVirtualSceneCamera()
        }
        else {
          stopOperatingVirtualSceneCamera()
        }
      }
    }

  private var virtualSceneCameraOperatingDisposable: Disposable? = null
  private val virtualSceneCameraVelocityController = VirtualSceneCameraVelocityController(emulator)
  private val stats = Stats()

  init {
    Disposer.register(disposableParent, this)

    background = primaryPanelBackground

    disconnectedStateLabel = JLabel()
    disconnectedStateLabel.horizontalAlignment = SwingConstants.CENTER
    disconnectedStateLabel.font = disconnectedStateLabel.font.deriveFont(disconnectedStateLabel.font.size * 1.2F)

    isFocusable = true // Must be focusable to receive keyboard events.
    focusTraversalKeysEnabled = false // Receive focus traversal keys to send them to the emulator.

    emulator.addConnectionStateListener(this)
    addComponentListener(this)

    // Forward mouse & keyboard events.
    val mouseListener = object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 1)
      }

      override fun mouseReleased(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 0)
      }

      override fun mouseClicked(event: MouseEvent) {
        requestFocusInWindow()
      }

      override fun mouseDragged(event: MouseEvent) {
        if (!virtualSceneCameraActive) {
          sendMouseEvent(event.x, event.y, 1)
        }
      }
    }
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)

    addKeyListener(object : KeyAdapter() {
      override fun keyTyped(event: KeyEvent) {
        if (virtualSceneCameraOperating) {
          return
        }

        val c = event.keyChar
        if (c == CHAR_UNDEFINED || Character.isISOControl(c)) {
          return
        }

        val keyboardEvent = KeyboardEvent.newBuilder().setText(c.toString()).build()
        emulator.sendKey(keyboardEvent)
      }

      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == VK_SHIFT && event.modifiersEx == SHIFT_DOWN_MASK && virtualSceneCameraActive) {
          virtualSceneCameraOperating = true
          return
        }

        if (virtualSceneCameraOperating) {
          when (event.keyCode) {
            VK_LEFT, VK_KP_LEFT -> rotateVirtualSceneCamera(0.0, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
            VK_RIGHT, VK_KP_RIGHT -> rotateVirtualSceneCamera(0.0, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
            VK_UP, VK_KP_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
            VK_DOWN, VK_KP_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
            VK_HOME -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
            VK_END -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
            VK_PAGE_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
            VK_PAGE_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
            else -> virtualSceneCameraVelocityController.keyPressed(event.keyCode)
          }
          return
        }

        // The Tab character is passed to the emulator, but Shift+Tab is converted to Tab and processed locally.
        if (event.keyCode == VK_TAB && event.modifiersEx == SHIFT_DOWN_MASK) {
          val tabEvent = KeyEvent(event.source as Component, event.id, event.getWhen(), 0, event.keyCode, event.keyChar, event.keyLocation)
          traverseFocusLocally(tabEvent)
          return
        }

        if (event.modifiersEx != 0) {
          return
        }
        val keyName =
          when (event.keyCode) {
            VK_BACK_SPACE -> "Backspace"
            VK_DELETE -> if (SystemInfo.isMac) "Backspace" else "Delete"
            VK_ENTER -> "Enter"
            VK_ESCAPE -> "Escape"
            VK_TAB -> "Tab"
            VK_LEFT, VK_KP_LEFT -> "ArrowLeft"
            VK_RIGHT, VK_KP_RIGHT -> "ArrowRight"
            VK_UP, VK_KP_UP -> "ArrowUp"
            VK_DOWN, VK_KP_DOWN -> "ArrowDown"
            VK_HOME -> "Home"
            VK_END -> "End"
            VK_PAGE_UP -> "PageUp"
            VK_PAGE_DOWN -> "PageDown"
            else -> return
          }
        emulator.sendKey(createHardwareKeyEvent(keyName))
      }

      override fun keyReleased(event: KeyEvent) {
        if (event.keyCode == VK_SHIFT) {
          virtualSceneCameraOperating = false
        }
        else if (virtualSceneCameraOperating) {
          virtualSceneCameraVelocityController.keyReleased(event.keyCode)
        }
      }
    })

    if (displayId == PRIMARY_DISPLAY_ID) {
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(event: FocusEvent) {
          if (connected) {
            setDeviceClipboardAndListenToChanges()
          }
          if (virtualSceneCameraActive) {
            showVirtualSceneCameraPrompt()
          }
        }

        override fun focusLost(event: FocusEvent) {
          cancelClipboardFeed()
          hideVirtualSceneCameraPrompt()
        }
      })
    }

    if (StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.get()) {
      val connection = ApplicationManager.getApplication().messageBus.connect(this)
      connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
        if (connected) {
          val style = if (StartupUiUtil.isUnderDarcula()) ThemingStyle.Style.DARK else ThemingStyle.Style.LIGHT
          emulator.setUiTheme(style)
        }
      })
    }

    updateConnectionState(emulator.connectionState)
  }

  override fun dispose() {
    cancelNotificationFeed()
    cancelClipboardFeed()
    cancelScreenshotFeed()
    removeComponentListener(this)
    emulator.removeConnectionStateListener(this)
    Disposer.dispose(stats) // stats have to be disposed last.
  }

  fun addDisplayConfigurationListener(listener: DisplayConfigurationListener) {
    displayConfigurationListeners.add(listener)
  }

  fun removeDisplayConfigurationListener(listener: DisplayConfigurationListener) {
    displayConfigurationListeners.remove(listener)
  }

  override fun zoom(type: ZoomType): Boolean {
    val scaledSize = computeZoomedSize(type)
    if (scaledSize == preferredSize) {
      return false
    }
    preferredSize = scaledSize
    revalidate()
    return true
  }

  override fun canZoomIn(): Boolean {
    return connected && computeZoomedSize(ZoomType.IN) != explicitlySetPreferredSize
  }

  override fun canZoomOut(): Boolean {
    return connected && computeZoomedSize(ZoomType.OUT) != explicitlySetPreferredSize
  }

  override fun canZoomToActual(): Boolean {
    return connected && computeZoomedSize(ZoomType.ACTUAL) != explicitlySetPreferredSize
  }

  override fun canZoomToFit(): Boolean {
    return connected && isPreferredSizeSet
  }

  internal val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

  /**
   * Computes the preferred size in virtual pixels after the given zoom operation.
   * The preferred size is null for zoom to fit.
   */
  private fun computeZoomedSize(zoomType: ZoomType): Dimension? {
    val newScale = when (zoomType) {
      ZoomType.IN -> {
        min(ZoomType.zoomIn((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0, MAX_SCALE)
      }
      ZoomType.OUT -> {
        max(ZoomType.zoomOut((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0, computeScaleToFitInParent())
      }
      ZoomType.ACTUAL -> {
        1.0
      }
      ZoomType.FIT -> {
        return null
      }
      else -> throw IllegalArgumentException("Unsupported zoom type $zoomType")
    }
    val scaledSize = computeScaledSize(newScale, screenshotShape.rotation)
    val availableSize = computeAvailableSize()
    if (scaledSize.width <= availableSize.width && scaledSize.height <= availableSize.height) {
      return null
    }
    return scaledSize.scaled(1 / screenScale)
  }

  private fun computeScaleToFitInParent() = computeScaleToFit(computeAvailableSize(), screenshotShape.rotation)

  private fun computeAvailableSize(): Dimension {
    val insets = parent.insets
    return Dimension((parent.width - insets.left - insets.right).scaled(screenScale),
                     (parent.height - insets.top - insets.bottom).scaled(screenScale))
  }

  private fun computeScaleToFit(availableSize: Dimension, rotation: SkinRotation): Double {
    return computeScaleToFit(computeActualSize(rotation), availableSize)
  }

  private fun computeScaleToFit(actualSize: Dimension, availableSize: Dimension): Double {
    val scale = min(availableSize.width.toDouble() / actualSize.width, availableSize.height.toDouble() / actualSize.height)
    return if (scale <= 1.0) scale else floor(scale)
  }

  private fun computeScaledSize(scale: Double, rotation: SkinRotation): Dimension {
    return computeActualSize(rotation).scaled(scale)
  }

  private fun computeActualSize(rotation: SkinRotation): Dimension {
    val skin = emulator.skinDefinition
    return if (skin != null && deviceFrameVisible) {
      skin.getRotatedFrameSize(rotation, deviceDisplaySize)
    }
    else {
      deviceDisplaySize.rotated(rotation)
    }
  }

  /**
   * Processes a focus traversal key event by passing it to the keyboard focus manager.
   */
  private fun traverseFocusLocally(event: KeyEvent) {
    if (!focusTraversalKeysEnabled) {
      focusTraversalKeysEnabled = true
      try {
        getCurrentKeyboardFocusManager().processKeyEvent(this, event)
      }
      finally {
        focusTraversalKeysEnabled = false
      }
    }
  }

  private fun sendMouseEvent(x: Int, y: Int, button: Int) {
    val displayRect = this.displayRectangle ?: return // Null displayRectangle means that Emulator screen is not displayed.
    val physicalX = x * screenScale // Coordinate is physical screen pixels.
    val physicalY = y * screenScale
    if (!displayRect.contains(physicalX, physicalY)) {
      return // Outside of the display rectangle.
    }
    val normalizedX = (physicalX - displayRect.x) / displayRect.width - 0.5  // X relative to display center in [-0.5, 0.5) range.
    val normalizedY = (physicalY - displayRect.y) / displayRect.height - 0.5 // Y relative to display center in [-0.5, 0.5) range.
    val deviceDisplayWidth = deviceDisplaySize.width
    val deviceDisplayHeight = deviceDisplaySize.height
    val displayX: Int
    val displayY: Int
    when (screenshotShape.rotation) {
      SkinRotation.PORTRAIT -> {
        displayX = ((0.5 + normalizedX) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 + normalizedY) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.LANDSCAPE -> {
        displayX = ((0.5 - normalizedY) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 + normalizedX) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.REVERSE_PORTRAIT -> {
        displayX = ((0.5 - normalizedX) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 - normalizedY) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.REVERSE_LANDSCAPE -> {
        displayX = ((0.5 + normalizedY) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 - normalizedX) * deviceDisplayHeight).roundToInt()
      }
      else -> {
        return
      }
    }
    val mouseEvent = MouseEventMessage.newBuilder()
      .setX(displayX.coerceIn(0, deviceDisplayWidth))
      .setY(displayY.coerceIn(0, deviceDisplayHeight))
      .setButtons(button)
      .build()
    emulator.sendMouse(mouseEvent)
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    invokeLaterInAnyModalityState {
      updateConnectionState(connectionState)
    }
  }

  private fun updateConnectionState(connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      remove(disconnectedStateLabel)
      if (isVisible) {
        if (screenshotFeed == null) {
          requestScreenshotFeed()
        }
        if (displayId == PRIMARY_DISPLAY_ID) {
          if (isFocusOwner) {
            setDeviceClipboardAndListenToChanges()
          }
          if (notificationFeed == null) {
            requestNotificationFeed()
          }
        }
      }
    }
    else if (connectionState == ConnectionState.DISCONNECTED) {
      lastScreenshot = null
      hideLongRunningOperationIndicator()
      disconnectedStateLabel.text = "Disconnected from the Emulator"
      add(disconnectedStateLabel)
    }

    revalidate()
    repaint()
  }

  private fun setDeviceClipboardAndListenToChanges() {
    val text = getClipboardText()
    if (text.isEmpty()) {
      requestClipboardFeed()
    }
    else {
      emulator.setClipboard(ClipData.newBuilder().setText(text).build(), object : EmptyStreamObserver<Empty>() {
        override fun onCompleted() {
          requestClipboardFeed()
        }

        override fun onError(t: Throwable) {
          if (t is StatusRuntimeException && t.status.code == Status.Code.UNIMPLEMENTED) {
            notifyEmulatorIsOutOfDate()
          }
        }
      })
    }
  }

  private fun getClipboardText(): String {
    val synchronizer = ClipboardSynchronizer.getInstance()
    return if (synchronizer.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
      synchronizer.getData(DataFlavor.stringFlavor) as String? ?: ""
    }
    else {
      ""
    }
  }

  private fun notifyEmulatorIsOutOfDate() {
    if (emulatorOutOfDateNotificationShown) {
      return
    }
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this)) ?: return
    val title = "Emulator is out of date"
    val message = "Please update the Android Emulator"
    val notification =
        EMULATOR_NOTIFICATION_GROUP.createNotification(title, XmlStringUtil.wrapInHtml(message), NotificationType.WARNING, null)
    notification.collapseActionsDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
    notification.addAction(object : NotificationAction("Check for updates") {
      override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        notification.expire()
        val action = ActionManager.getInstance().getAction("CheckForUpdate")
        ActionUtil.performActionDumbAware(action, event)
      }
    })
    notification.notify(project)
    emulatorOutOfDateNotificationShown = true
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val screenshot = lastScreenshot ?: return
    val skin = screenshot.skinLayout
    assert(screenshotShape.width != 0)
    assert(screenshotShape.height != 0)
    val displayRect = computeDisplayRectangle(skin)
    displayRectangle = displayRect

    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw display.
    if (displayRect.width == screenshotShape.width && displayRect.height == screenshotShape.height) {
      g.drawImage(screenshot.image, null, displayRect.x, displayRect.y)
    }
    else {
      displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
      displayTransform.scale(displayRect.width.toDouble() / screenshotShape.width, displayRect.height.toDouble() / screenshotShape.height)
      g.drawImage(screenshot.image, displayTransform, null)
    }

    if (deviceFrameVisible) {
      // Draw device frame and mask.
      skin.drawFrameAndMask(g, displayRect)
    }
    if (!screenshot.painted) {
      screenshot.painted = true
      val paintTime = System.currentTimeMillis()
      stats.recordLatencyEndToEnd(paintTime - screenshot.frameOriginationTime)
    }
  }

  private fun computeDisplayRectangle(skin: SkinLayout): Rectangle {
    // The roundScale call below is used to avoid scaling by a fractional factor larger than 1 or
    // by a factor that is only slightly below 1.
    return if (deviceFrameVisible) {
      val frameRectangle = skin.frameRectangle
      val scale = roundScale(min(realWidth.toDouble() / frameRectangle.width, realHeight.toDouble() / frameRectangle.height))
      val fw = frameRectangle.width.scaled(scale)
      val fh = frameRectangle.height.scaled(scale)
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((realWidth - fw) / 2 - frameRectangle.x.scaled(scale), (realHeight - fh) / 2 - frameRectangle.y.scaled(scale), w, h)
    }
    else {
      val scale = roundScale(min(realWidth.toDouble() / screenshotShape.width, realHeight.toDouble() / screenshotShape.height))
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((realWidth - w) / 2, (realHeight - h) / 2, w, h)
    }
  }

  /** Rounds the given value down to an integer if it is above 1, or to the nearest multiple of 1/128 if it is below 1. */
  private fun roundScale(value: Double): Double {
    return if (value >= 1) floor(value) else round(value * 128) / 128
  }

  private fun requestScreenshotFeed() {
    requestScreenshotFeed(screenshotShape.rotation)
  }

  private fun requestScreenshotFeed(rotation: SkinRotation) {
    cancelScreenshotFeed()
    if (width != 0 && height != 0 && connected) {
      val rotatedDisplaySize = deviceDisplaySize.rotated(rotation)
      val actualSize = computeActualSize(rotation)

      // Limit the size of the received screenshots to the size of the device display to avoid wasting gRPC resources.
      val scaleX = (realSize.width.toDouble() / actualSize.width).coerceAtMost(1.0)
      val scaleY = (realSize.height.toDouble() / actualSize.height).coerceAtMost(1.0)
      val w = rotatedDisplaySize.width.scaledDown(scaleX)
      val h = rotatedDisplaySize.height.scaledDown(scaleY)

      val imageFormat = ImageFormat.newBuilder()
        .setDisplay(displayId)
        .setFormat(ImageFormat.ImgFormat.RGB888)
        .setWidth(w)
        .setHeight(h)
        .build()
      val receiver = ScreenshotReceiver(rotation)
      screenshotReceiver = receiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, receiver)
    }
  }

  private fun cancelScreenshotFeed() {
    screenshotReceiver?.let { Disposer.dispose(it) }
    screenshotReceiver = null
    screenshotFeed?.cancel()
    screenshotFeed = null
  }

  private fun requestClipboardFeed() {
    cancelClipboardFeed()
    if (connected) {
      val receiver = ClipboardReceiver()
      clipboardReceiver = receiver
      clipboardFeed = emulator.streamClipboard(receiver)
    }
  }

  private fun cancelClipboardFeed() {
    clipboardReceiver = null
    clipboardFeed?.cancel()
    clipboardFeed = null
  }

  private fun requestNotificationFeed() {
    cancelNotificationFeed()
    if (connected) {
      val receiver = NotificationReceiver()
      notificationReceiver = receiver
      notificationFeed = emulator.streamNotification(receiver)
    }
  }

  private fun cancelNotificationFeed() {
    notificationReceiver = null
    notificationFeed?.cancel()
    notificationFeed = null
  }

  override fun componentResized(event: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentShown(event: ComponentEvent) {
    requestScreenshotFeed()
    if (displayId == PRIMARY_DISPLAY_ID) {
      requestNotificationFeed()
    }
  }

  override fun componentHidden(event: ComponentEvent) {
    cancelClipboardFeed()
  }

  override fun componentMoved(event: ComponentEvent) {
  }

  fun showLongRunningOperationIndicator(text: String) {
    findLoadingPanel()?.apply {
      setLoadingText(text)
      startLoading()
    }
  }

  fun hideLongRunningOperationIndicator() {
    findLoadingPanel()?.stopLoading()
  }

  fun hideLongRunningOperationIndicatorInstantly() {
    findLoadingPanel()?.stopLoadingInstantly()
  }

  private fun showVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.showNotification("Hold Shift to control camera")
  }

  private fun hideVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.hideNotification()
  }

  private fun startOperatingVirtualSceneCamera() {
    findNotificationHolderPanel()?.showNotification("Move camera with WASDQE keys, rotate with mouse or arrow keys")
    val glass = IdeGlassPaneUtil.find(this)
    val cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE)
    val rootPane = glass.rootPane
    val scale = PI / min(rootPane.width, rootPane.height)
    UIUtil.setCursor(rootPane, cursor)
    glass.setCursor(cursor, this)
    val referencePoint = MouseInfo.getPointerInfo().location
    val mouseListener = object: MouseAdapter() {

      override fun mouseMoved(event: MouseEvent) {
        rotateVirtualSceneCamera(-(event.yOnScreen - referencePoint.y) * scale, (referencePoint.x - event.xOnScreen) * scale)
        referencePoint.setLocation(event.xOnScreen, event.yOnScreen)
        event.consume()
      }

      override fun mouseDragged(e: MouseEvent) {
        mouseMoved(e)
      }

      override fun mouseEntered(event: MouseEvent) {
        glass.setCursor(cursor, this)
      }
    }

    val disposable = Disposer.newDisposable("Virtual scene camera operation")
    virtualSceneCameraOperatingDisposable = disposable
    glass.addMousePreprocessor(mouseListener, disposable)
    glass.addMouseMotionPreprocessor(mouseListener, disposable)
  }

  private fun stopOperatingVirtualSceneCamera() {
    virtualSceneCameraVelocityController.stop()
    virtualSceneCameraOperatingDisposable?.let { Disposer.dispose(it) }
    findNotificationHolderPanel()?.showNotification("Hold Shift to control camera")
    val glass = IdeGlassPaneUtil.find(this)
    glass.setCursor(null, this)
    UIUtil.setCursor(glass.rootPane, null)
  }

  private fun rotateVirtualSceneCamera(rotationX: Double, rotationY: Double) {
    val cameraRotation = RotationRadian.newBuilder()
      .setX(rotationX.toFloat())
      .setY(rotationY.toFloat())
      .build()
    emulator.rotateVirtualSceneCamera(cameraRotation)
  }

  private val IdeGlassPane.rootPane
    get() = (this as IdeGlassPaneImpl).rootPane

  private fun findLoadingPanel(): EmulatorLoadingPanel? = findContainingComponent()

  private fun findNotificationHolderPanel(): NotificationHolderPanel? = findContainingComponent()

  private inline fun <reified T : JComponent> findContainingComponent(): T? {
    var component = parent
    while (component != null) {
      if (component is T) {
        return component
      }
      component = component.parent
    }
    return null
  }

  private inner class ClipboardReceiver : EmptyStreamObserver<ClipData>() {
    var responseCount = 0

    override fun onNext(response: ClipData) {
      if (clipboardReceiver != this) {
        return // This clipboard feed has already been cancelled.
      }

      // Skip the first response that reflects the current clipboard state.
      if (responseCount != 0 && response.text.isNotEmpty()) {
        invokeLaterInAnyModalityState {
          val content = StringSelection(response.text)
          ClipboardSynchronizer.getInstance().setContent(content, content)
        }
      }
      responseCount++
    }
  }

  private inner class NotificationReceiver : EmptyStreamObserver<EmulatorNotification>() {

    override fun onNext(response: EmulatorNotification) {
      if (EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS.get()) {
        LOG.info("Notification ${response.event}")
      }

      if (notificationReceiver != this) {
        return // This notification feed has already been cancelled.
      }

      invokeLaterInAnyModalityState {
        when (response.event) {
          VIRTUAL_SCENE_CAMERA_ACTIVE -> virtualSceneCameraActive = true
          VIRTUAL_SCENE_CAMERA_INACTIVE -> virtualSceneCameraActive = false
          DISPLAY_CONFIGURATIONS_CHANGED_UI -> notifyDisplayConfigurationListeners()
          else -> {}
        }
      }
    }
  }

  private fun notifyDisplayConfigurationListeners() {
    for (listener in displayConfigurationListeners) {
      listener.displayConfigurationChanged()
    }
  }

  private inner class ScreenshotReceiver(val rotation: SkinRotation) : EmptyStreamObserver<ImageMessage>(), Disposable {
    private val screenshotForProcessing = AtomicReference<Screenshot?>()
    private val screenshotForDisplay = AtomicReference<Screenshot?>()
    private val skinLayoutCache = SkinLayoutCache(emulator)
    private val recycledImage = AtomicReference<SofterReference<BufferedImage>?>()
    private val alarm = Alarm(this)
    private var expectedFrameNumber = -1

    override fun onNext(response: ImageMessage) {
      val arrivalTime = System.currentTimeMillis()
      val imageFormat = response.format
      val imageRotation = imageFormat.rotation.rotation
      val frameOriginationTime: Long = response.timestampUs / 1000

      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        val latency = arrivalTime - frameOriginationTime
        LOG.info("Screenshot ${response.seq} ${imageFormat.width}x${imageFormat.height} $imageRotation $latency ms latency")
      }
      if (screenshotReceiver != this) {
        expectedFrameNumber++
        return // This screenshot feed has already been cancelled.
      }

      if (imageFormat.width == 0 || imageFormat.height == 0) {
        expectedFrameNumber++
        return // Ignore empty screenshot.
      }

      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (imageRotation != rotation) {
        invokeLaterInAnyModalityState {
          requestScreenshotFeed(imageRotation)
        }
        expectedFrameNumber++
        return
      }

      alarm.cancelAllRequests()
      val recycledImage = recycledImage.getAndSet(null)?.get()
      val image = if (recycledImage?.width == imageFormat.width && recycledImage.height == imageFormat.height) {
        val pixels = (recycledImage.raster.dataBuffer as DataBufferInt).data
        unpackPixels(response.image, pixels)
        recycledImage
      }
      else {
        val pixels = IntArray(imageFormat.width * imageFormat.height)
        unpackPixels(response.image, pixels)
        val buffer = DataBufferInt(pixels, pixels.size)
        val sampleModel = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, imageFormat.width, imageFormat.height, SAMPLE_MODEL_BIT_MASKS)
        val raster = Raster.createWritableRaster(sampleModel, buffer, ZERO_POINT)
        @Suppress("UndesirableClassUsage")
        BufferedImage(COLOR_MODEL, raster, false, null)
      }

      val lostFrames = if (expectedFrameNumber > 0) response.seq - expectedFrameNumber else 0
      stats.recordFrameArrival(arrivalTime - frameOriginationTime, lostFrames, imageFormat.width * imageFormat.height)
      expectedFrameNumber = response.seq + 1

      val displayShape = DisplayShape(imageFormat)
      val screenshot = Screenshot(displayShape, image, frameOriginationTime)
      val skinLayout = skinLayoutCache.getCached(displayShape)
      if (skinLayout == null) {
        computeSkinLayoutOnPooledThread(screenshot)
      }
      else {
        screenshot.skinLayout = skinLayout
        updateDisplayImageOnUiThread(screenshot)
      }
    }

    private fun computeSkinLayoutOnPooledThread(screenshotWithoutSkin: Screenshot) {
      screenshotForProcessing.set(screenshotWithoutSkin)

      executeOnPooledThread {
        // If the screenshot feed has not been cancelled, update the skin and the display image.
        if (screenshotReceiver == this) {
          val screenshot = screenshotForProcessing.getAndSet(null)
          if (screenshot == null) {
            stats.recordDroppedFrame()
          }
          else {
            screenshot.skinLayout = skinLayoutCache.get(screenshot.displayShape)
            updateDisplayImageOnUiThread(screenshot)
          }
        }
      }
    }

    private fun updateDisplayImageOnUiThread(screenshot: Screenshot) {
      screenshotForDisplay.set(screenshot)

      invokeLaterInAnyModalityState {
        // If the screenshot feed has not been cancelled, update the display image.
        if (screenshotReceiver == this) {
          updateDisplayImage()
        }
      }
    }

    @UiThread
    private fun updateDisplayImage() {
      hideLongRunningOperationIndicatorInstantly()

      val screenshot = screenshotForDisplay.getAndSet(null)
      if (screenshot == null) {
        stats.recordDroppedFrame()
        return
      }

      // Creation of a large BufferedImage is expensive. Recycle the old image if it has the proper size.
      lastScreenshot?.image?.let {
        if (it.width == screenshot.displayShape.width && it.height == screenshot.displayShape.height) {
          recycledImage.set(SofterReference(it))
          alarm.cancelAllRequests()
          alarm.addRequest({ recycledImage.set(null) }, CACHED_IMAGE_LIVE_TIME_MILLIS, ModalityState.any())
        }
      }

      lastScreenshot = screenshot

      frameNumber++
      frameTimestampMillis = System.currentTimeMillis()
      repaint()
    }

    /**
     * This takes noticeable time because it processes a lot of data but is still fast enough
     * to be called on the gRPC thread.
     */
    // TODO: Consider moving to native code.
    fun unpackPixels(imageBytes: ByteString, pixels: IntArray) {
      val alpha = 0xFF shl 24
      var j = 0
      for (i in pixels.indices) {
        val red = imageBytes.byteAt(j).toInt() and 0xFF
        val green = imageBytes.byteAt(j + 1).toInt() and 0xFF
        val blue = imageBytes.byteAt(j + 2).toInt() and 0xFF
        j += 3
        pixels[i] = alpha or (red shl 16) or (green shl 8) or blue
      }
    }

    override fun dispose() {
    }
  }

  private class Screenshot(val displayShape: DisplayShape, val image: BufferedImage, val frameOriginationTime: Long) {
    lateinit var skinLayout: SkinLayout
    var painted = false
  }

  /**
   * Stores the last computed scaled [SkinLayout] together with the corresponding display
   * dimensions and orientation.
   */
  private class SkinLayoutCache(val emulator: EmulatorController) {
    var displayShape: DisplayShape? = null
    var skinLayout: SkinLayout? = null

    fun getCached(displayShape: DisplayShape): SkinLayout? {
      synchronized(this) {
        return if (displayShape == this.displayShape) skinLayout else null
      }
    }

    @Slow
    fun get(displayShape: DisplayShape): SkinLayout {
      synchronized(this) {
        var layout = this.skinLayout
        if (displayShape != this.displayShape || layout == null) {
          layout = emulator.skinDefinition?.createScaledLayout(displayShape.width, displayShape.height, displayShape.rotation) ?:
                   SkinLayout(displayShape.width, displayShape.height)
          this.displayShape = displayShape
          this.skinLayout = layout
        }
        return layout
      }
    }
  }

  private data class DisplayShape(val width: Int, val height: Int, val rotation: SkinRotation) {
    constructor(imageFormat: ImageFormat) : this(imageFormat.width, imageFormat.height, imageFormat.rotation.rotation)
  }

  private class Stats: Disposable {
    @GuardedBy("this")
    private var data = Data()
    private val alarm = Alarm(this)

    init {
      scheduleNextLogging()
    }

    @Synchronized
    fun recordFrameArrival(latencyOfArrival: Long, numberOfLostFrames: Int, numberOfPixels: Int) {
      data.frameCount += 1 + numberOfLostFrames
      data.pixelCount += (1 + numberOfLostFrames) * numberOfPixels
      data.latencyOfArrival.recordValue(latencyOfArrival)
      if (numberOfLostFrames != 0) {
        data.droppedFrameCount += numberOfLostFrames
        data.droppedFrameCountBeforeArrival += numberOfLostFrames
      }
    }

    @Synchronized
    fun recordDroppedFrame() {
      data.droppedFrameCount++
    }

    @Synchronized
    fun recordLatencyEndToEnd(latency: Long) {
      data.latencyEndToEnd.recordValue(latency)
    }

    @Synchronized
    override fun dispose() {
      data.log()
    }

    @Synchronized
    private fun getAndSetData(newData: Data): Data {
      val oldData = data
      data = newData
      return oldData
    }

    private fun scheduleNextLogging() {
      alarm.addRequest(::logAndReset, STATS_LOG_INTERVAL, ModalityState.any())
    }

    private fun logAndReset() {
      getAndSetData(Data()).log()
      scheduleNextLogging()
    }

    private class Data {
      var frameCount = 0
      var droppedFrameCount = 0
      var droppedFrameCountBeforeArrival = 0
      var pixelCount = 0L
      val latencyEndToEnd = Histogram(1)
      val latencyOfArrival = Histogram(1)
      val collectionStart = System.currentTimeMillis()

      fun log() {
        if (frameCount != 0) {
          val frameRate = String.format("%.2g", frameCount * 1000.0 / (System.currentTimeMillis() - collectionStart))
          val frameSize = (pixelCount.toDouble() / frameCount).roundToInt()
          val neverArrived = if (droppedFrameCountBeforeArrival != 0) " (${droppedFrameCountBeforeArrival} never arrived)" else ""
          val dropped = if (droppedFrameCount != 0) " dropped frames: $droppedFrameCount$neverArrived" else ""
          thisLogger().info("Frames: $frameCount $dropped average frame rate: $frameRate average frame size: $frameSize pixels\n" +
                            "latency: ${shortDebugString(latencyEndToEnd.toProto())}\n" +
                            "latency of arrival: ${shortDebugString(latencyOfArrival.toProto())}")
        }
      }
    }
  }
}

private var emulatorOutOfDateNotificationShown = false

private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES = 5
private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN = VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES * PI / 180

private const val MAX_SCALE = 2.0 // Zoom above 200% is not allowed.

private val ZOOM_LEVELS = intArrayOf(5, 10, 25, 50, 100, 200) // In percent.

private val ZERO_POINT = Point()
private const val ALPHA_MASK = 0xFF shl 24
private val SAMPLE_MODEL_BIT_MASKS = intArrayOf(0xFF0000, 0xFF00, 0xFF, ALPHA_MASK)
private val COLOR_MODEL = DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                   32, 0xFF0000, 0xFF00, 0xFF, ALPHA_MASK, false, DataBuffer.TYPE_INT)
private const val CACHED_IMAGE_LIVE_TIME_MILLIS = 2000

private val STATS_LOG_INTERVAL = Duration.ofMinutes(2).toMillis() // TODO: Change to 1 hour when switching to analytics logging.

private val LOG = Logger.getInstance(EmulatorView::class.java)