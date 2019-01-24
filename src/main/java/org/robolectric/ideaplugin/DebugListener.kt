package org.robolectric.ideaplugin

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.EverythingGlobalScope
import com.sun.jdi.ClassType
import com.sun.jdi.IntegerValue
import com.sun.jdi.Location
import java.util.*
import javax.swing.SwingUtilities

class DebugListener(private val project: Project) : DebuggerManagerListener {
  private val application = ApplicationManager.getApplication()
  private val javaPsiFacade = JavaPsiFacade.getInstance(project)!!
  private val virtualFileManager = VirtualFileManager.getInstance()
  private val psiManager = PsiManager.getInstance(project)
  private val sdkLibraryManager = RobolectricProjectComponent.getInstance(project).sdkLibraryManager
  private var lastNotifiedApiLevel = 0

  init {
    sdkLibraryManager.findSdkLibraries()
    sdkLibraryManager.addMissingLibraries()
  }

  private val sessionHandlers: MutableMap<DebuggerSession, DebugSessionHandler> = HashMap()

  override fun sessionCreated(session: DebuggerSession?) {
    println("session created: $session")
    session!!
    sessionHandlers[session] = DebugSessionHandler(session)
  }

  override fun sessionAttached(session: DebuggerSession?) {
    println("session attached: $session")
    sessionHandlers[session!!]!!.attached()
  }

  override fun sessionDetached(session: DebuggerSession?) {
    println("session detached: $session")
    sessionHandlers[session!!]!!.detached()
  }

  override fun sessionRemoved(session: DebuggerSession?) {
    println("session removed: $session")
    sessionHandlers[session!!]!!.removed()
  }

  inner class DebugSessionHandler(private val session: DebuggerSession) {
    private var currentApiLevel = 0

    fun attached() {
      notifyActiveApiLevel(currentApiLevel)
      val process = session.process
      process.managerThread.invokeAndWait(object: DebuggerCommandImpl() {
        override fun action() {
          process.appendPositionManager(MyPositionManager(process))
        }
      })

      session.contextManager.addListener { debuggerContext, event ->
        println("${event.name} on $session")
        if (event == DebuggerSession.Event.CONTEXT) {
          queryAndroidApiLevel(debuggerContext, process)
          notifyActiveApiLevel(currentApiLevel)
          println("This debugger just became active... $session")
        } else if (event == DebuggerSession.Event.PAUSE) {
//        process.xdebugProcess.custom step manager thingy?

          queryAndroidApiLevel(debuggerContext, process)
          notifyActiveApiLevel(currentApiLevel)
        }
      }
    }

    fun detached() {
      notifyActiveApiLevel(0)
      project.messageBus.syncPublisher(Notifier.Topics.DEBUG_TOPIC).sdkChanged(null)
    }

    fun removed() {
    }

    private fun queryAndroidApiLevel(debuggerContext: DebuggerContextImpl, process: DebugProcessImpl) {
      if (debuggerContext.isEvaluationPossible) {
        process.managerThread.invokeAndWait(object : DebuggerCommandImpl() {
          override fun action() {
            val evaluationContext = debuggerContext.createEvaluationContext()
            if (evaluationContext == null) {
              println("huh, no evaluationContext!")
            } else {
              val vm = process.virtualMachineProxy
              val currentClassLoader = evaluationContext.classLoader
              val runtimeEnvVmClass = vm.classesByName("org.robolectric.RuntimeEnvironment")
                  .find { it.classLoader() == currentClassLoader }!! as ClassType

              val getApiLevelVmMethod = DebuggerUtils.findMethod(runtimeEnvVmClass, "getApiLevel", "()I")!!
              val value = process.invokeMethod(evaluationContext, runtimeEnvVmClass, getApiLevelVmMethod, Collections.emptyList())

              println("value = $value")
              println(DebuggerUtils.getValueAsString(evaluationContext, value))

              currentApiLevel = (value as IntegerValue).value()
            }
          }
        })
      }
    }
  }

  private fun evalInDebugger() {
    //            application.runReadAction {
    //              val builder = EvaluatorBuilderImpl.getInstance()
    //              val psiFileFactory = PsiFileFactory.getInstance(project)
    //              val psiFile: PsiJavaFile = (psiFileFactory.createFileFromText(JavaLanguage.INSTANCE,
    //                  "class A { String apiLevel() { return org.robolectric.RuntimeEnvironment.getApiLevel(); } }") as PsiJavaFile?)!!
    //              val psiCodeBlock = psiFile.classes[0].methods[0].body
    //              println(psiCodeBlock)
    //              val evaluator = builder.build(psiCodeBlock, SourcePosition.createFromLine(psiFile, 1))
    //              val value = evaluator.evaluate(evaluationContext)
    //              println("value = $value")
    //              println(DebuggerUtils.getValueAsString(evaluationContext, value))
    //
    //              val sdkLevel = (value as IntegerValue).value()
    //
    //              // here do a thing?
    //              notifyActiveApiLevel(sdkLevel)
    //            }
  }

  private fun nowOrLaterOnUiThread(function: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
      function.invoke()
    } else {
      SwingUtilities.invokeLater(function)
    }
  }

//  var undoAddLibrary: Runnable? = null
//  private val ADD_LIBRARY_TO_MODULE: Boolean = false

  private fun notifyActiveApiLevel(sdkLevel: Int) {
//    if (sdkLevel != currentApiLevel) {
//      currentApiLevel = sdkLevel
//
//      if (undoAddLibrary != null) {
//        application.runWriteAction {
//          undoAddLibrary!!.run()
//        }
//        undoAddLibrary = null
//      }
//
//      if (ADD_LIBRARY_TO_MODULE) {
//        addLibaryToModule(module, sdkLevel)
//      }
//  }

    if (sdkLevel != lastNotifiedApiLevel) {
      lastNotifiedApiLevel = sdkLevel

      nowOrLaterOnUiThread {
        val androidSdk = if (sdkLevel == 0) null else sdkLibraryManager.getSdk(sdkLevel)
        val publisher = project.messageBus.syncPublisher(Notifier.Topics.DEBUG_TOPIC)
        publisher.sdkChanged(androidSdk)
      }
    }
  }

//  private fun addLibaryToModule(module: Module?, sdkLevel: Int) {
//    val androidSdk = sdkLibraryManager.getSdk(sdkLevel)
//    if (androidSdk != null) {
//      val library = sdkLibraryManager.getLibrary(androidSdk)
//      if (library != null) {
//        application.invokeLater {
//          application.runWriteAction {
//            if (sdkLibraryManager.addLibraryToModule(library, module!!)) {
//              undoAddLibrary = Runnable {
//                sdkLibraryManager.removeLibraryFromModule(library, module)
//              }
//            }
//          }
//        }
//      }
//    }
//  }

  inner class MyPositionManager(process: DebugProcessImpl) : PositionManagerImpl(process) {
    private fun sdkLibraryManager(project: Project) = RobolectricProjectComponent.getInstance(project).sdkLibraryManager

//    override fun locationsOfLine(type: ReferenceType, position: SourcePosition): MutableList<Location> {
//      val locationsOfLine = super.locationsOfLine(type, position)
//      println("locations of line $type $position -> $locationsOfLine")
//      return locationsOfLine
//    }
//
//    override fun getAllClasses(classPosition: SourcePosition): MutableList<ReferenceType> {
//      val allClasses = super.getAllClasses(classPosition)
//      println("getAllClasses $classPosition -> $allClasses")
//      return allClasses
//    }
//
//    override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
//      val prepareRequest = super.createPrepareRequest(requestor, position)
//      println("create prepare request $requestor $position -> $prepareRequest")
//      return prepareRequest
//    }

    override fun getPsiFileByLocation(project: Project?, location: Location?): PsiFile? {
      val typeName = location?.declaringType()?.name()
      if (project != null && location != null && typeName != null) {
        val sourceFinder = sdkLibraryManager(project).sourceFinder
        val srcClass = sourceFinder?.findClass(typeName, EverythingGlobalScope())
        if (srcClass != null) {
          return srcClass.containingFile
        }
      }

      return super.getPsiFileByLocation(project, location)
//      if (currentSdkLevel != 0 && typeName.startsWith("android.")) {
//        location!!
//
//        val androidSdk = sdkLibraryManager.getSdk(currentSdkLevel)
//        if (false && androidSdk != null) {
//          val srcFilePath = "jar://${androidSdk.sourceJarFile.path}!/${typeName.replace('.', '/')}.java"
//          val srcVFile = virtualFileManager.findFileByUrl(srcFilePath)
//          if (srcVFile != null) {
//            return psiManager.findFile(srcVFile)
//          }
//        }
//
////        val library = androidAllSdkLibraries[androidSdk]
////        if (library == null) {
////          LOGGER.warn("no Android source found for SDK $currentSdkLevel ($androidSdk)")
////        } else {//          androidSdk.sourceJar
////          val classPsi = javaPsiFacade.findClass(typeName, LibraryScope(project, library))
////          LOGGER.info("using library = $library for $location")
//      }
//
//      return super.getPsiFileByLocation(project, location)
    }

    override fun getSourcePosition(location: Location?): SourcePosition? {
//        DebuggerManagerThreadImpl.assertIsManagerThread()
//
//        val typeName = location?.declaringType()?.name() ?: ""
//        if (false && typeName.startsWith("android.")) {
//          location!!
//
//          val version = sdksByApiLevel[currentSdkLevel]
//          val library = androidAllSdkLibraries[version]
//          val classPsi = javaPsiFacade.findClass(typeName, LibraryScope(project, library))
//          println("using library = $library for $location")
//          val psiFile = classPsi!!.containingFile
//
//          var lineNumber: Int
//          try {
//            lineNumber = location.lineNumber() - 1
//          } catch (e: InternalError) {
//            lineNumber = -1
//          }
//
//          var sourcePosition: SourcePosition? = null
//          if (lineNumber > -1) {
//            sourcePosition = calcLineMappedSourcePosition(psiFile, lineNumber)
//          }
//
//          return SourcePosition.createFromElement(psiFile)
//        } else {
      val sourcePosition = super.getSourcePosition(location)
      println("get source position $location -> $sourcePosition")
      return sourcePosition
//        }
    }

    private fun calcLineMappedSourcePosition(psiFile: PsiFile, originalLine: Int): SourcePosition? {
      val line = DebuggerUtilsEx.bytecodeToSourceLine(psiFile, originalLine)
      if (line > -1) {
        return SourcePosition.createFromLine(psiFile, line - 1)
      }
      return null
    }
  }
}