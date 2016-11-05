package org.robolectric.ideaplugin

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask
import com.intellij.ide.util.MethodOrFunctionalExpressionCellRenderer
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.IconLoader
import com.intellij.patterns.PsiMethodPattern
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.impl.PsiMethodStubImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.CommonProcessors
import java.awt.event.MouseEvent
import java.util.*

class RobolectricLineMarkerProvider : LineMarkerProviderDescriptor() {
  override fun getName(): String? = "Robolectric"

  private val ICON = IconLoader.getIcon("/icons/robolectric-marker.png")

  private val shadowGutterIconNavHandler = GutterIconNavigationHandler<PsiMethod> { mouseEvent, t ->
    if (!checkDumb(t)) {
      openTargets(ShadowMethodWrapper(t).frameworkMethods(), mouseEvent, t)
    }
  }

  private val frameworkGutterIconNavHandler = GutterIconNavigationHandler<PsiMethod> { mouseEvent, t ->
    if (!checkDumb(t)) {
      openTargets(FrameworkMethod(t).shadowMethods(), mouseEvent, t)
    }
  }

  override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
    var lineMarkerInfo : LineMarkerInfo<*>? = null

    val projectComponent = getProjectComponent(psiElement.project)
    projectComponent.findShadowedClasses()

    projectComponent.time("getLineMarkerInfo") {
      when (psiElement) {
        is PsiMethod -> {
          var shadowMethod = ShadowMethodWrapper(psiElement)
          if (shadowMethod.isShadow()) {
            // we're in a shadow class...
            lineMarkerInfo = LineMarkerInfo(psiElement,
                psiElement.nameIdentifier!!.textRange,
                ICON, Pass.UPDATE_ALL, null,
                shadowGutterIconNavHandler,
                GutterIconRenderer.Alignment.LEFT)
          } else {
            // we might be in a framework class...
            if (FrameworkMethod(psiElement).isShadowed()) {
              lineMarkerInfo = LineMarkerInfo(psiElement,
                  psiElement.nameIdentifier!!.textRange,
                  ICON, Pass.UPDATE_ALL, null,
                  frameworkGutterIconNavHandler,
                  GutterIconRenderer.Alignment.LEFT)
            }
          }
        }

        is PsiAnnotation -> {

        }
      }
    }
    return lineMarkerInfo
  }


  private fun checkDumb(method: PsiMethod): Boolean {
    if (DumbService.isDumb(method.project)) {
      DumbService.getInstance(method.project).showDumbModeNotification(
          "Navigation to overriding classes is not possible during index update")
      return true
    }
    return false
  }

  override fun collectSlowLineMarkers(p0: MutableList<PsiElement>, p1: MutableCollection<LineMarkerInfo<PsiElement>>) {
  }


  private fun implementationAnnotation(psiMethod: PsiMethod): ImplementationAnnotation? {
    val annotation = psiMethod.modifierList.findAnnotation("org.robolectric.annotation.Implementation")
    return if (annotation == null) null else ImplementationAnnotation(annotation)
  }

  private fun openTargets(methods: Array<out PsiMethod>, mouseEvent: MouseEvent?, fromMethod: PsiMethod) {
    if (methods.isEmpty()) return
    if (methods.size == 1) methods[0].navigate(true)

    val renderer = MethodOrFunctionalExpressionCellRenderer(true)
    val alternatives = ArrayList<NavigatablePsiElement>()
    alternatives.addAll(methods)
    Collections.sort(alternatives, renderer.comparator)
    val methodsUpdater = OverridingMethodsUpdater(fromMethod, renderer)
    PsiElementListNavigator.openTargets(mouseEvent, alternatives.toTypedArray(), methodsUpdater.getCaption(alternatives.size), "Overriding methods of " + fromMethod.name, renderer, methodsUpdater)
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


  inner class ShadowMethodWrapper(val psiMethod: PsiMethod) {
    fun isShadow(): Boolean {
      val implementsAnnotation = implementsAnnotation()
      if (implementsAnnotation != null) {
        if (isConstructorShadow() || implementationAnnotation(psiMethod) != null) {
          return true
        }
      }
      return false
    }

    private fun isConstructorShadow() = psiMethod.name == "__constructor__"

    fun frameworkClass() = implementsAnnotation()?.frameworkType?.resolve()

    private fun implementsAnnotation(): ImplementsAnnotation? {
      val annotation = psiMethod.containingClass?.modifierList?.findAnnotation("org.robolectric.annotation.Implements")
      return if (annotation == null) null else ImplementsAnnotation(annotation)
    }

    fun frameworkMethods() : Array<out PsiMethod> {
      val frameworkClass = frameworkClass() ?: return emptyArray()

      if (isConstructorShadow()) {
        val ctorMethods = frameworkClass.findMethodsByName(frameworkClass.name, false)
        val ctorMethodsWithSameParams = ctorMethods.filter { it.hasSameParamsAs(psiMethod) }
        return if (ctorMethodsWithSameParams.isEmpty()) ctorMethods else ctorMethodsWithSameParams.toTypedArray()
      }

      val shadowMethod = frameworkClass.findMethodBySignature(psiMethod, false)
      if (shadowMethod != null) {
        return arrayOf(shadowMethod)
      } else {
        return frameworkClass.findMethodsByName(psiMethod.name, false)
      }
    }
  }

  inner class FrameworkMethod(val psiMethod: PsiMethod) {
    val containingClassName: String? = psiMethod.containingClass?.qualifiedName

    fun shadowClasses() : Set<PsiClass> {
      return if (containingClassName == null)
        emptySet()
      else
        getProjectComponent(psiMethod.project).shadowsFor(containingClassName)
    }

    fun isShadowed(): Boolean = shadowClasses().any { matchingShadowMethods(it).isNotEmpty() }
    fun shadowMethods(): Array<PsiMethod> = shadowClasses().flatMap { matchingShadowMethods(it).asIterable() }.toTypedArray()

    private fun matchingShadowMethods(it: PsiClass): Array<out PsiMethod> {
      if (!it.isValid) return emptyArray()
      if (psiMethod.isConstructor) {
        val ctorMethods = it.findMethodsByName("__constructor__", false)
        val ctorMethodsWithSameParams = ctorMethods.filter { it.hasSameParamsAs(psiMethod) }.toTypedArray()
        return if (ctorMethodsWithSameParams.isNotEmpty()) ctorMethodsWithSameParams else ctorMethods
      } else {
        return it.findMethodsBySignature(psiMethod, false)
      }
    }
  }

  fun getProjectComponent(project: Project) = project.getComponent(RobolectricProjectComponent::class.java)

  private fun PsiMethod.hasSameParamsAs(other: PsiMethod): Boolean {
    return parameterList.parameters.map { it.type.canonicalText } == other.parameterList.parameters.map { it.type.canonicalText }
  }
}
