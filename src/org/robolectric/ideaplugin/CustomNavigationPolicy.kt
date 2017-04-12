package org.robolectric.ideaplugin;

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.search.EverythingGlobalScope

class CustomNavigationPolicy : ClsCustomNavigationPolicy {
  private fun sdkLibraryManager(project: Project) = RobolectricProjectComponent.getInstance(project).sdkLibraryManager
  override fun getNavigationElement(clsClass: ClsClassImpl): PsiElement? {
    return null
  }

  override fun getNavigationElement(clsMethod: ClsMethodImpl): PsiElement? {
    val containingClassName = clsMethod.containingClass?.qualifiedName
    if (containingClassName == "android.view.View") {
      println("getNavigationElement($clsMethod)")
    }
    val finder = sdkLibraryManager(clsMethod.project).sourceFinder
    if (finder != null && containingClassName != null) {
        val navClass = finder.findClass(containingClassName, EverythingGlobalScope())
        if (navClass != null) {
          val navMethod = navClass.findMethodBySignature(clsMethod, false)
          if (navMethod != null) {
            println("CustomNavigationPolicy found $clsMethod in ${navMethod.containingFile.containingDirectory}!")
            return navMethod
          }
        }

//      val extensions = Extensions.getExtensions(PsiElementFinder.EP_NAME, clsMethod.project)
//      extensions.forEach { classFinder ->
//        val navClass = classFinder.findClass(containingClassName, GlobalSearchScope.allScope(clsMethod.project))
//        if (navClass != null) {
//          val navMethod = navClass.findMethodBySignature(clsMethod, false)
//          if (navMethod != null) {
//            return navMethod
//          }
//        }
//      }
    }
    return null
  }

  override fun getNavigationElement(clsField: ClsFieldImpl): PsiElement? {
    return null
  }
}