package forex.domain

import cats.Show
import enumeratum._

sealed trait Currency extends EnumEntry

object Currency extends Enum[Currency] with CirceEnum[Currency] {
  case object AUD extends Currency
  case object CAD extends Currency
  case object CHF extends Currency
  case object EUR extends Currency
  case object GBP extends Currency
  case object NZD extends Currency
  case object JPY extends Currency
  case object SGD extends Currency
  case object USD extends Currency

  implicit val show: Show[Currency] = Show.show(_.toString)

  def fromString(s: String): Currency = Currency.withName(s.toUpperCase)

  override def values: IndexedSeq[Currency] = findValues

  val combinations: List[(Currency, Currency)] = {
    values
      .combinations(2)
      .flatMap(_.permutations)
      .collect { case Seq(from: Currency, to: Currency) => (from, to) }
      .toList
  }
}
