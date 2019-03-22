package eec
package compiler
package types

import org.junit.Test
import org.junit.Assert._

class TyperTest {

  import core.Contexts._
  import ast.Trees._
  import parsing.EntryPoint._
  import error.CompilerErrors._
  import error.CompilerErrors.CompilerError._
  import Types._
  import types.Typers._
  import error.CompilerErrors._
  import BootstrapTests._

  val any = types.Types.Type.WildcardType

  @Test def typecheckInteger() = typecheck(
    "0"   -> "Integer",
    "-0"  -> "Integer"
  )

  @Test def failInteger() = noType(
    "0l", // error: no Longs
    "0L"  // error: no Longs
  )

  @Test def typecheckDecimal() = typecheck(
    "3.14159265358979323846264338328" -> "Decimal", // PI
    "6.62607004e-34"                  -> "Decimal", // Planck's constant
    "-273.15"                         -> "Decimal"  // 0 degrees Kelvin
  )

  @Test def failDecimal() = noType(
    "3.14159f", // error: no Floats
    "3.14159F", // error: no Floats
    "3.14159d", // error: no Doubles
    "3.14159D"  // error: no Doubles
  )

  @Test def typecheckBoolean() = typecheck(
    "True"  -> "Boolean",
    "False" -> "Boolean"
  )

  @Test def typecheckChar() = typecheck(
    """'a'"""   -> "Char",
    """'\n'"""  -> "Char"
  )

  @Test def failChar() = noType(
    "''",   // error: empty char
    "'ab'"  // error: char more than one char
  )

  @Test def typecheckString() = typecheck(
    """"test""""        -> "String",
    "\"\"\"test\"\"\""  -> "String"
  )

  @Test def typecheckProducts() = typecheck(
    "()"            -> "()",
    "(())"          -> "()",
    "((),())"       -> "((), ())",
    "((),(),())"    -> "((), (), ())",
    "((),(),(),())" -> "((), (), (), ())"
  )

  @Test def typecheckCompute() = typecheck(
    "!()"       -> "! ()",
    "!((),())"  -> "! ((), ())",
    "(!(),!())" -> "(! (), ! ())"
  )

  @Test def noTypeCompute() = noType(
    """\(c: ! a b) => ()""",  // error: expected types [_] but got [a, b]
    "! () ()"                 // error: expected args [_: _] but got [(): (), (): ()]
  )

  @Test def typecheckIf() = typecheck(
    "if True then () else ()" -> "()",
    """\(a: Boolean) (b: Boolean) => if a then !b else !False""" -> "Boolean -> Boolean -> ! Boolean"
  )

  @Test def failIf() = noType(
    "if 0 then () else ()",   // error: non Boolean condition
    "if True then 0 else ()"  // error: disjoint branches
  )

  @Test def typecheckCase() = typecheck(
    """case () of
        _ => ()"""                  -> "()",
    """case () of
        x => x"""                   -> "()",
    """case () of
        c @ x => (c, x)"""          -> "((), ())",
    """case () of
        _ if True => ()"""          -> "()",
    """case () of
        () | () => ()"""            -> "()",
    """case "hello" of
        "" | _ => ()"""             -> "()",
    """case ((), ()) of
        ((), ())  => ()
        _         => ()"""          -> "()",
    """case ((), ()) of
        (_, a) => a"""              -> "()",
    """case ((), ((), ())) of
        (_, (_, a)) => a"""         -> "()",
    """case ((), ((), ())) of
        (a, (_, b)) => (a, b)"""    -> "((), ())",
    """case ((), ((), ())) of
        (_, (_, _)) => ()"""        -> "()",
    """case ((), (), ()) of
        ((), (), ()) => ()"""       -> "()",
    """case ((), (), (), ()) of
        ((), (), (), ()) => ()"""   -> "()"
  )

  @Test def failCase() = noType(
    """case () of
        (a | _) => ()""", // error: name in alternative
    """case () of
        ()  => 1
        ()  => False""", // error: disjoint bodies
    """case () of
        "abc" => ()""", // error: pattern doesn't match selector
    """case ((), ((), ())) of
        (((), ()), ()) => 0""", // error: pattern doesn't match selector
    """case ((), ((), ())) of
        ((_, _), _) => 0""", // error: pattern doesn't match selector
    """case "hello" of
        ""  => 0
        1   => 0""" // error: mismatching cases
  )

  @Test def typecheckLambda() = typecheck(
    """\(t: ()) => ()"""                -> "() -> ()",
    """\(t: () -> ()) => ()"""          -> "(() -> ()) -> ()",
    """\(t: ()) (u: ()) => ()"""        -> "() -> () -> ()",
    """\(t: ((), ())) => ()"""          -> "((), ()) -> ()",
    """\(t: ((), (), (), ())) => ()"""  -> "((), (), (), ()) -> ()",
    """\(t: b#) => t"""                 -> "b# -> b#"
  )

  @Test def failLambda() = noType(
    """\(f: () -> Char) => ()""", // error: (() -> Char) is not computation co-domain
    """\(f: ()) => 0""", // error: (() -> Integer) is not computation co-domain
    """\(f: (a -> b) -> c#) => ()""" // error: (a -> b) isnt computational codomain
  )

  @Test def typecheckApplication() = typecheck(
    """(\(t: ()) (u: ()) (v: ()) => ()) ()"""       -> "() -> () -> ()",
    """(\(t: ()) (u: ()) (v: ()) => ()) () ()"""    -> "() -> ()",
    """(\(t: ()) (u: ()) (v: ()) => ()) () () ()""" -> "()",
    """\(f: () -> ()) => f ()"""                    -> "(() -> ()) -> ()",
    """\(t: () -> ()) => \(c: ()) => t c"""         -> "(() -> ()) -> () -> ()"
  )

  @Test def failApplication() = noType(
    """(\(f: ()) => ()) 0""" // error: expects () not Integer
  )

  @Test def typecheckLet() = typecheck(
    "let !x = !() in ()"  -> "()",
    "let !x = !0 in !x"   -> "! Integer"
  )

  @Test def failLet() = noType(
    "let !x = () in ()",  // error: () is not ! type
    "let !x = 0 in ()",   // error: 0 is not of ! type
    "let !x = !0 in 0"    // error: 0 is not of computation type
  )

  @Test def typecheckBind(): Unit = {
    typecheck(
      """\(ma: !a) (f: a -> !b) => let !a = ma in f a"""      -> "! a -> (a -> ! b) -> ! b",
      """\(ma: !a) => \(f: a -> !b) => let !a = ma in f a"""  -> "! a -> (a -> ! b) -> ! b",
      """\(a: !a) (f: a -> !b) => let !a = a in f a"""        -> "! a -> (a -> ! b) -> ! b")
  }

  def typecheck(seq: (String, String)*): Unit = {
    import CompilerErrorOps._
    initialCtx.flatMap { (idGen, ctx) =>
      implied for IdGen = idGen
      implied for Context = ctx
      seq.toList.mapE((f, s) => checkTpe(typeExpr(f)(any), s))
    }
  }

  def noType(seq: String*): Unit = {
    import CompilerErrorOps._
    initialCtx.flatMap { (idGen, ctx) =>
      implied for IdGen = idGen
      implied for Context = ctx
      seq.toList.mapE(f => failIfTyped(typeExpr(f)(any)))
    }
  }
}