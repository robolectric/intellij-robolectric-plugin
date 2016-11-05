package org.robolectric.ideaplugin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class StubIndex extends StringStubIndexExtension<PsiClass> {
  @Override
  public boolean processAllKeys(Project project, Processor<String> processor) {
    return super.processAllKeys(project, processor);
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiClass> getKey() {
    return StubIndexKey.createIndexKey("robolectric.method.name");
  }
}
