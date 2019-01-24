package org.robolectric.ideaplugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
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
        val shadowedClassType = valueAttr.operand.type
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

class ImplementationAnnotation(val annotation: PsiAnnotation)

fun ghostIcon(original: Icon, opacity: Float): ImageIcon {
  val buf = BufferedImage(original.iconWidth, original.iconHeight, BufferedImage.TYPE_INT_ARGB)
  val graphics2D = buf.createGraphics()
  graphics2D.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
  original.paintIcon(JPanel(), graphics2D, 0, 0)
  return ImageIcon(buf)
}

val LOGGER = Logger.getInstance(org.robolectric.ideaplugin.RobolectricProjectComponent::class.java)