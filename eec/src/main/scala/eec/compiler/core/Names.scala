package eec
package compiler
package core

object Names {

  enum Name {
    case From(name: String)
    case ComputationTag, UnitTag, Tuple2Tag, IntegerTag, DecimalTag,
      BooleanTag, StringTag, CharTag, Wildcard, EmptyName
  }

  val emptyString: String = "<empty>"

  object NameOps {

    import Name._
    import eec.util.{Showable, Readable}

    implicit val NameShowable: Showable[Name] = new {
      override def (name: Name) userString: String = name match {
        case Wildcard       => "_"
        case ComputationTag => "!"
        case UnitTag        => "()"
        case Tuple2Tag      => "(,)"
        case IntegerTag     => "Integer"
        case DecimalTag     => "Decimal"
        case BooleanTag     => "Boolean"
        case StringTag      => "String"
        case CharTag        => "Char"
        case EmptyName      => emptyString
        case From(n)        => n
      }
    }

    implicit val NameReadable: Readable[Name] = new {
      override def (str: String) readAs: Name = str match {
        case "_"        => Wildcard
        case "!"        => ComputationTag
        case "()"       => UnitTag
        case "(,)"      => Tuple2Tag
        case "Integer"  => IntegerTag
        case "Decimal"  => DecimalTag
        case "Boolean"  => BooleanTag
        case "String"   => StringTag
        case "Char"     => CharTag
        case _          => From(str)
      }
    }
  }
}