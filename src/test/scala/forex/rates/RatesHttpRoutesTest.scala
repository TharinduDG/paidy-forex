package forex.rates

import java.time.{Instant, ZoneOffset}

import cats.effect.IO
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.http.rates.RatesHttpRoutes
import forex.programs.RatesProgram
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors
import forex.programs.rates.errors.Error.RateLookupFailed
import io.circe.Json
import io.circe.literal._
import org.http4s.Request
import org.http4s.circe._
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import scala.util.Try

class RatesHttpRoutesTest extends AsyncWordSpecLike with Matchers {
  val program = new TestRatesProgram
  val routes  = new RatesHttpRoutes[IO](program)

  "RatesHttpRoutes" should {

    "return error when the actual rate is missing" in {
      val request  = Request[IO](uri = uri"/rates?from=USD&to=AUD")
      val expected = "Rate not found. Try again later"

      val actual = Try(routes.routes(request).value.flatMap(_.get.as[Json]))
        .getOrElse(IO.raiseError(new Exception("Empty Response!")))
        .unsafeRunSync()

      actual.hcursor.downField("message").as[String].getOrElse("Empty message!") equals expected should be(true)
    }

    "return error when the `from` currency in invalid" in {
      val request  = Request[IO](uri = uri"/rates?from=XYZ&to=USD")
      val expected = "Invalid Currency Code `XYZ`"

      val actual = Try(routes.routes(request).value.flatMap(_.get.as[Json]))
        .getOrElse(IO.raiseError(new Exception("Empty Response!")))
        .unsafeRunSync()

      actual.hcursor.downField("message").as[String].getOrElse("Empty message!") contains expected should be(true)
    }

    "return error when the `to` currency in invalid" in {
      val request  = Request[IO](uri = uri"/rates?from=USD&to=PQR")
      val expected = "Invalid Currency Code `PQR`"

      val actual = Try(routes.routes(request).value.flatMap(_.get.as[Json]))
        .getOrElse(IO.raiseError(new Exception("Empty Response!")))
        .unsafeRunSync()

      actual.hcursor.downField("message").as[String].getOrElse("Empty message!") contains expected should be(true)
    }

    "return exchange rate for valid `from` & `to` currencies" in {
      val request  = Request[IO](uri = uri"/rates?from=SGD&to=GBP")
      val expected = json"""
                        {
                          "from": "SGD",
                          "to": "GBP",
                          "price": 0.87,
                          "timestamp": "2021-04-25T06:30:30Z"
                        }
                        """

      val actual = Try(routes.routes(request).value.flatMap(_.get.as[Json]))
        .getOrElse(IO.raiseError(new Exception("Empty Response!")))
        .unsafeRunSync()

      actual should equal(expected)
    }
  }

  class TestRatesProgram extends RatesProgram[IO] {
    val mockResponseMap: Map[GetRatesRequest, Rate] = Map(
      GetRatesRequest(Currency.SGD, Currency.GBP) -> Rate(
        Rate.Pair(Currency.SGD, Currency.GBP),
        Price(BigDecimal(0.87D)),
        Timestamp(Instant.parse("2021-04-25T06:30:30Z").atOffset(ZoneOffset.UTC))
      )
    )

    override def get(request: GetRatesRequest): IO[Either[errors.Error, Rate]] =
      IO.delay(
        Either.cond(mockResponseMap.contains(request), mockResponseMap(request), RateLookupFailed("Rate not found. Try again later"))
      )
  }
}
