package org.robolectric.ideaplugin;

import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.psi.PsiElement

class MyJavaTargetElementEvaluator : JavaTargetElementEvaluator() {
  override fun getGotoDeclarationTarget(element: PsiElement, navElement: PsiElement?): PsiElement? {
    return super.getGotoDeclarationTarget(element, navElement)
  }
}