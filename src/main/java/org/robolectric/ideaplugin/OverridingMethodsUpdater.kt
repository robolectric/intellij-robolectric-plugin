package org.robolectric.ideaplugin

import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFunctionalExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.CommonProcessors

class OverridingMethodsUpdater constructor(private val myMethod: PsiMethod, private val myRenderer: PsiElementListCellRenderer<*>) : ListBackgroundUpdaterTask(myMethod.project, "SEARCHING_FOR_OVERRIDING_METHODS") {
  override fun getCaption(size: Int): String {
    return if (myMethod.hasModifierProperty(PsiModifier.ABSTRACT))
      DaemonBundle.message("navigation.title.implementation.method", myMethod.name, size)
    else
      DaemonBundle.message("navigation.title.overrider.method", myMethod.name, size)
  }

  override fun run(indicator: ProgressIndicator) {
    super.run(indicator)
    OverridingMethodsSearch.search(myMethod).forEach(
        object : CommonProcessors.CollectProcessor<PsiMethod>() {
          override fun process(psiMethod: PsiMethod): Boolean {
            if (!updateComponent(psiMethod, myRenderer.comparator)) {
              indicator.cancel()
            }
            indicator.checkCanceled()
            return super.process(psiMethod)
          }
        })
    val psiClass = ApplicationManager.getApplication().runReadAction(Computable<PsiClass> { myMethod.containingClass })
    FunctionalExpressionSearch.search(psiClass).forEach(object : CommonProcessors.CollectProcessor<PsiFunctionalExpression>() {
      override fun process(expr: PsiFunctionalExpression): Boolean {
        if (!updateComponent(expr, myRenderer.comparator)) {
          indicator.cancel()
        }
        indicator.checkCanceled()
        return super.process(expr)
      }
    })
  }
}