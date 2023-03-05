/*
    Larceny, version 1.0.0. Copyright 2023-23 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package larceny

import dotty.tools.*, dotc.*, reporting.*, ast.Trees.*, ast.tpd, core.*, Constants.Constant, Contexts.*,
    Decorators.*, StdNames.*, plugins.*

import scala.collection.mutable as scm

class LarcenyPlugin() extends StandardPlugin:
  val name: String = "larceny"
  override val description: String = "capture errors"
  def init(options: List[String]): List[PluginPhase] = List(LarcenyTransformer())

class LarcenyTransformer() extends PluginPhase:
  import tpd.*

  val phaseName = "errorcap"
  override val runsAfter = Set("parser")
  override val runsBefore = Set("typer")

  override def transformUnit(tree: Tree)(using Context): Tree =
    import ast.untpd.*
    val classpath = ctx.settings.classpath.value
    System.out.nn.println("Using classpath: "+classpath)
    lazy val allErrors: List[Diagnostic] =
      Subcompiler.compile(ctx.settings.classpath.value, List(ctx.compilationUnit.source.file))

    val transformer = new UntypedTreeMap:
      override def transform(tree: Tree)(using Context): Tree =
        tree match
          case Apply(Ident(name), List(b)) if name.toString == "captureCompileErrors" =>
            val captured = allErrors.filter: diagnostic =>
              try diagnostic.pos.point >= b.span.start && diagnostic.pos.point <= b.span.end
              catch case err: AssertionError => false
            
            val msgs =
              captured.map: diagnostic =>
                val pos = diagnostic.pos
                val code = String(ctx.compilationUnit.source.content.slice(pos.start, pos.end))
                val offset = pos.point - pos.start
                
                Apply(Select(Select(Ident(nme.ROOTPKG), "larceny".toTermName), "CompileError".toTermName), List(
                  Literal(Constant(diagnostic.msg.errorId.ordinal)),
                  Literal(Constant(diagnostic.msg.message)),
                  Literal(Constant(code)),
                  Literal(Constant(offset))
                ))
            
            Apply(Ident(name), List(Block(List(), Apply(Select(Select(Ident(nme.ROOTPKG), nme.scala),nme.List),
                msgs))))
          
          case _ =>
            super.transform(tree)

    ctx.compilationUnit.untpdTree = transformer.transform(ctx.compilationUnit.untpdTree)
    super.transformUnit(tree)

object Subcompiler:
  
  val Scala3: Compiler = new Compiler()

  class CustomReporter() extends Reporter, UniqueMessagePositions, HideNonSensicalMessages:
    val errors: scm.ListBuffer[Diagnostic] = scm.ListBuffer()
    def doReport(diagnostic: Diagnostic)(using Context): Unit = errors += diagnostic

  def compile(classpath: String, sources: List[io.AbstractFile]): List[Diagnostic] =
    val reporter = CustomReporter()
    object driver extends Driver:

      val currentCtx: Context =
        val ctx = initCtx.fresh
        val ctx2 = ctx.setSetting(ctx.settings.classpath, classpath)
        setup(Array[String](""), ctx2).map(_(1)).get
      
      def run(): List[Diagnostic] =
        val ctx = currentCtx.fresh
        val ctx2 = ctx.setReporter(reporter).setSetting(ctx.settings.classpath, classpath)
        val run = Scala3.newRun(using ctx2)
        run.compile(sources)
        finish(Scala3, run)(using ctx2)
        reporter.errors.to(List)
    
    driver.run()