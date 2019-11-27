package mesa.eec

import quoted._
import quoted.matching._

import java.io.StringReader

import Parsers._
import mesa.util.StackMachine.{InterpretableK, Program, stack, Statement, Stack}
import Program.compile
import Trees.Tree
import Tree._

object Macros {
  def eecImpl(scExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(given qctx: QuoteContext): Expr[Tree[?]] = {

    def reify[U](t: Tree[U], args: Seq[Expr[Any]]): Expr[Tree[?]] = {
      import qctx.tasty.{Tree => _, _, given}
      val expr = t.compile[Tree, U, Expr[Tree[?]]] {
        case Pair(_,_) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[$t1] } :: '{ $b1: Tree[$t2] } :: s1 =>
            '{Pair[$t1, $t2]($a1.asInstanceOf[Tree[$t1]], $b1.asInstanceOf[Tree[$t2]])}::s1

        case Tensor(_,_) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[$t1] } :: '{ $b1: Tree[$t2] } :: s1 =>
            '{Tensor[$t1, $t2]($a1.asInstanceOf[Tree[$t1]], $b1.asInstanceOf[Tree[$t2]])}::s1

        case App(_,_) =>
          (stack: @unchecked) match
          case '{ $f1: Tree[Function1[$t1,$t2]] } :: '{ $x1: Tree[$t3] } :: s1 =>
            '{App($f1.asInstanceOf[Tree[$t1 => $t2]], $x1.asInstanceOf[Tree[$t1]])}::s1

        case Eval(_,_) =>
          (stack: @unchecked) match
          case '{ $f1: Tree[Function1[$t1,$t2]] } :: '{ $x1: Tree[$t3] } :: s1 =>
            '{Eval($f1.asInstanceOf[Tree[$t1 => $t2]], $x1.asInstanceOf[Tree[$t1]])}::s1

        case Lam(x1,_) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[$t1] } :: s1 =>
            '{Lam(${Expr(x1)}, $a1.asInstanceOf[Tree[$t1]])}::s1

        case Lin(x1,_) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[$t1] } :: s1 =>
            '{Lin(${Expr(x1)}, $a1.asInstanceOf[Tree[$t1]])}::s1

        case CaseExpr(_,x,_,y,_) =>
          (stack: @unchecked) match
          case '{ $e1: Tree[Either[$t1, $t2]] } :: '{ $l1: Tree[$t3] } :: '{ $r1: Tree[$t4] } :: s1 =>
            '{CaseExpr($e1.asInstanceOf[Tree[Either[$t1, $t2]]],${Expr(x)}, $l1.asInstanceOf[Tree[$t3]], ${Expr(y)}, $r1.asInstanceOf[Tree[$t3]])}::s1

        case Let(x,_,_) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[$t1] } :: '{ $b1: Tree[$t2] } :: s1 =>
            '{Let(${Expr(x)},$a1.asInstanceOf[Tree[$t1]], $b1.asInstanceOf[Tree[$t2]])}::s1

        case LetT(x,z,_,_) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[Tuple2[$t1, $t2]] } :: '{ $b1: Tree[$t3] } :: s1 =>
            '{LetT(${Expr(x)}, ${Expr(z)}, $a1.asInstanceOf[Tree[($t1, $t2)]], $b1.asInstanceOf[Tree[$t3]])}::s1

        case Bang(t) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[$t1] } :: s1 =>
            '{Bang[$t1]($a1.asInstanceOf[Tree[$t1]])}::s1

        case WhyNot(t) =>
          (stack: @unchecked) match
          case '{ $a1: Tree[?] } :: s1 =>
            '{WhyNot($a1.asInstanceOf[Tree[Nothing]])}::s1

        case Point     => '{Point} :: stack
        case Fst()     => '{Fst()} :: stack
        case Snd()     => '{Snd()} :: stack
        case Inl()     => '{Inl()} :: stack
        case Inr()     => '{Inr()} :: stack
        case Splice(n) => '{lazy val x = ${args(n)}; Value(() => x)} :: stack
        case Value(x)  =>  { error(s"access to value from wrong staging level", rootPosition); ??? }
        case Var(x)    => '{Var(${Expr(x)})} :: stack
      }
      expr.cast[mesa.eec.Trees.Tree[?]]
    }

    def encode(parts: Seq[Expr[String]])(given QuoteContext): String = {
      val sb = new StringBuilder()

      def appendPart(part: Expr[String]) = {
        val Const(value: String) = part
        sb ++= value
      }

      def appendHole(index: Int) = {
        sb ++= Hole.encode(index)
      }

      for ((part, index) <- parts.init.zipWithIndex) {
        appendPart(part)
        appendHole(index)
      }
      appendPart(parts.last)

      sb.toString
    }

    ((scExpr, argsExpr): @unchecked) match {
      case ('{ StringContext(${ExprSeq(parts)}: _*) }, ExprSeq(args)) =>
        val code = encode(parts)
        parseAll(term[Any], StringReader(code)) match {
          case Success(matched,_) => reify(matched, args)
          case f:Failure          => sys.error(f.toString)
          case e:Error            => sys.error(e.toString)
        }
    }
  }
}
