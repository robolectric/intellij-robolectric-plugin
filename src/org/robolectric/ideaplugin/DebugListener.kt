package org.robolectric.ideaplugin

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.IntegerValue
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest

class DebugListener(private val project: Project) : DebuggerManagerListener {
  private var previousSdkLevel = 0

  override fun sessionRemoved(session: DebuggerSession?) {
    println("session = $session")
  }

  override fun sessionAttached(session: DebuggerSession?) {
    val xDebugSessionImpl = session!!.xDebugSession as XDebugSessionImpl
//        xDebugSessionImpl.ui.
//        xDebugSessionImpl.addExtraActions()

    val process = session!!.process
    process.appendPositionManager(object : PositionManagerImpl(process) {
      override fun locationsOfLine(type: ReferenceType, position: SourcePosition): MutableList<Location> {
        val locationsOfLine = super.locationsOfLine(type, position)
        println("locations of line $type $position -> $locationsOfLine")
        return locationsOfLine
      }

      override fun getAllClasses(classPosition: SourcePosition): MutableList<ReferenceType> {
        val allClasses = getAllClasses(classPosition)
        println("getAllClasses $classPosition -> $allClasses")
        return allClasses
      }

      override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
        val prepareRequest = createPrepareRequest(requestor, position)
        println("create prepare request $requestor $position -> $prepareRequest")
        return prepareRequest
      }

      override fun getSourcePosition(location: Location?): SourcePosition? {
        val sourcePosition = super.getSourcePosition(location)
        println("get source position $location -> $sourcePosition")
        return sourcePosition
      }
    })

    process.suspendManager.eventContexts

    println("session = $session")
    session.contextManager.addListener { debuggerContextImpl, event ->
      println(event.name)
      if (debuggerContextImpl.isEvaluationPossible) {
        val evaluationContext = debuggerContextImpl.createEvaluationContext()
        val builder = EvaluatorBuilderImpl.getInstance()
        val psiFileFactory = PsiFileFactory.getInstance(project)
        val psiFile: PsiJavaFile = (psiFileFactory.createFileFromText(JavaLanguage.INSTANCE,
            "class A { String apiLevel() { return org.robolectric.RuntimeEnvironment.getApiLevel(); } }") as PsiJavaFile?)!!
        val psiCodeBlock = psiFile.classes[0].methods[0].body
        println(psiCodeBlock)
        val evaluator = builder.build(psiCodeBlock, SourcePosition.createFromLine(psiFile, 1))
        process.managerThread.invokeAndWait(object : DebuggerCommandImpl() {
          override fun action() {
            val value = evaluator.evaluate(evaluationContext)
            println("value = $value")
            println(DebuggerUtils.getValueAsString(evaluationContext, value))

            val sdkLevel = (value as IntegerValue).value()
            notifyActiveSdkLevel(sdkLevel)
          }
        })
      }
    }
  }

  override fun sessionCreated(session: DebuggerSession?) {
    println("session = $session")
  }

  override fun sessionDetached(session: DebuggerSession?) {
    println("session = $session")
    notifyActiveSdkLevel(0)
    project.messageBus.syncPublisher(Notifier.Topics.DEBUG_TOPIC).sdkChanged(null)
  }

  private fun notifyActiveSdkLevel(sdkLevel: Int) {
    if (sdkLevel != previousSdkLevel) {
      val publisher = project.messageBus.syncPublisher(Notifier.Topics.DEBUG_TOPIC)
      publisher.sdkChanged(if (sdkLevel == 0) null else sdkLevel)

      previousSdkLevel = sdkLevel
    }
  }
}