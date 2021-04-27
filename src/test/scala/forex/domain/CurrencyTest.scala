package forex.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class CurrencyTest extends AnyWordSpecLike with Matchers {

  "Currency" should {
    "be the same after serialization & deserialization" in {
      Currency.values.forall(c => Currency.fromString(Currency.show.show(c)) == c)
    }
  }
}
