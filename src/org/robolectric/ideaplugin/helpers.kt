package org.robolectric.ideaplugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPanel

fun PsiClass.implementsAnnotation(): ImplementsAnnotation? {
  val annotation = modifierList?.findAnnotation("org.robolectric.annotation.Implements")
  return if (annotation == null) null else ImplementsAnnotation(annotation)
}

class ImplementsAnnotation(val annotation: PsiAnnotation) {
  val frameworkType: PsiClassType?
    get() {
      val valueAttr = annotation.findAttributeValue("value")
      if (valueAttr is PsiClassObjectAccessExpression) {
        val shadowedClassType = valueAttr.operand.type;
        if (shadowedClassType is PsiClassType) {
          return shadowedClassType
        }
      }
      // todo: check className and find via JavaPsiFacade.findClass()

      return null
    }

  val shadowType: PsiClass?
    get() = (annotation.owner as PsiModifierList).parent as PsiClass?
}

class ImplementationAnnotation(val annotation: PsiAnnotation) {
}

fun ghostIcon(original: Icon, opacity: Float): ImageIcon {
  val buf = BufferedImage(original.iconWidth, original.iconHeight, BufferedImage.TYPE_INT_ARGB)
  val graphics2D = buf.createGraphics()
  graphics2D.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
  original.paintIcon(JPanel(), graphics2D, 0, 0)
  return ImageIcon(buf)
}

private val m2RepoDir = "${System.getProperty("user.home")}/.m2/repository"

data class AndroidSdk(val apiLevel: Int, val sdkVersion: String, val build: String) {
  val groupId = "org.robolectric"
  val artifactId = "android-all"
  val version = "$sdkVersion-robolectric-$build"
  val coordinates = "$groupId:$artifactId:$version"

  private val m2partial = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version"

  val jarFile: File
    get() = File("$m2RepoDir/$m2partial.jar")
  val sourceJarFile: File
    get() = File("$m2RepoDir/$m2partial-sources.jar")

  fun exists() = jarFile.exists() && sourceJarFile.exists()
}

class Sdks {
  val sdksByApiLevel = HashMap<Int, AndroidSdk>()
  val sdksByVersion = HashMap<String, AndroidSdk>()

  val sdkVersions = listOf(
      AndroidSdk(16, "4.1.2_r1", "0"),
      AndroidSdk(17, "4.2.2_r1.2", "0"),
      AndroidSdk(18, "4.3_r2", "0"),
      AndroidSdk(19, "4.4_r1", "1"),
      AndroidSdk(21, "5.0.0_r2", "1"),
      AndroidSdk(22, "5.1.1_r9", "1"),
      AndroidSdk(23, "6.0.0_r1", "0"),
      AndroidSdk(24, "7.0.0_r1", "0")
  )

  init {
    sdkVersions.forEach {
      sdksByApiLevel[it.apiLevel] = it
      sdksByVersion[it.version] = it
    }
  }
}

val SDKs = Sdks()
fun getSdk(apiLevel: Int): AndroidSdk? {
  return SDKs.sdksByApiLevel[apiLevel]
}

val LOGGER = Logger.getInstance(org.robolectric.ideaplugin.RobolectricProjectComponent::class.java)