package org.robolectric.ideaplugin

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.Processor

class StubIndex : StringStubIndexExtension<PsiClass>() {

  override fun processAllKeys(project: Project, processor: Processor<String>): Boolean {
    return super.processAllKeys(project, processor)
  }

  override fun getKey(): StubIndexKey<String, PsiClass> {
    return INDEX_KEY
  }

  companion object {
    val INDEX_KEY = StubIndexKey.createIndexKey<String, PsiClass>("robolectric.method.name")
  }
}
