package eec
package repl

class EECRepl {
  
  import compiler.types._
  import compiler.parsing._
  import compiler.error.CompilerErrors._
  import compiler.types.Types._
  import compiler.types.Types.Type
  import scala.annotation._
  import pprint._

  val pwd = System.getProperty("user.dir")
  val defaultPrompt = "eec"

  def loop: Unit = {

    def (prompt: String) asPrompt: String = s"$prompt> "

    @tailrec
    def inner(state: LoopState): Unit = state match {
      case s @ LoopState(prompt, false) =>
        println
        print(prompt.asPrompt)
        val nextState = command(s, readLine)
        inner(nextState)
      case LoopState(_, true) =>
        // exit
    }

    val initial = LoopState(defaultPrompt, false)
    println("starting eec REPL...")
    print(defaultPrompt.asPrompt)
    val state = command(initial, readLine)
    inner(state)
  }

  private[this] case class LoopState(prompt: String, break: Boolean)

  private[this] def command(state: LoopState, input: String): LoopState = {

      import Commands._
      import Commands.Command._
      import compiler.ast.Trees._
      import compiler.types.Typers._

      def guarded(string: String)(body: => LoopState): LoopState =
        if string.isBlank then {
          println(s"[ERROR] empty input")
          state
        } else {
          body
        }

      parseCommand(input) match {
        case AstExpr(code) => guarded(code) {
          parseExpr(code) match {
            case e: CompilerError =>
              import CompilerErrorOps._
              println(s"[ERROR] ${e.userString}")
              state
            case expr =>
              import compiler.core.Printing.untyped.AstOps._
              pprintln(expr.asInstanceOf[Tree].toAst, height = Int.MaxValue)
              state
          }
        }
        case TypeExpr(code) => guarded(code) {
          parseExpr(code) match {
            case e: CompilerError =>
              import CompilerErrorOps._
              println(s"[ERROR] ${e.userString}")
              state
            case expr: Tree =>
              import CompilerErrorOps._
              expr.typedAsExpr(Type.WildcardType).fold { error =>
                println(s"[ERROR] ${error.userString}")
                state
              }{ typed =>
                import TypeOps._
                val tpe = typed.tpe
                println(tpe.userString)
                state
              }
          }
        }
        case AstFile(name) => guarded(name) {
          val file = scala.io.Source.fromFile(s"$pwd/$name")
          val code = file.getLines.mkString("\n")
          file.close()
          parseEEC(code) match {
            case e: CompilerError =>
              import CompilerErrorOps._
              println(s"[ERROR] ${e.userString}")
              state
            case ast =>
              import compiler.core.Printing.untyped.AstOps._
              pprintln(ast.asInstanceOf[Tree].toAst, height = Int.MaxValue)
              state
          }
        }
        case SetPrompt(newPrompt) => guarded(newPrompt) {
          state.copy(prompt = newPrompt)
        }
        case Quit =>
          println("Quitting...")
          state.copy(break = true)
        case ShowHelp =>
          println(helpText)
          state
        case Unknown =>
          println(s"[ERROR] unrecognised command: `$input`. Try `:help`")
          state
      }
    }
}