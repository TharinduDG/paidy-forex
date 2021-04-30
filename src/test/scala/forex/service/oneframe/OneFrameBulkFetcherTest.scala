package forex.service.oneframe

import java.time.OffsetDateTime
import java.util.concurrent.Executors

import cats.effect.{IO, Timer}
import forex.config.{HttpConfig, OneFrameConfig, RetryConfig}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.oneframe.errors
import forex.services.oneframe.errors.Error.OneFrameSystemError
import forex.services.oneframe.interpreters.OneFrameBulkFetcher
import forex.utils.AuthTokenProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import sttp.client.testing.SttpBackendStub
import sttp.client.{HttpURLConnectionBackend, Identity, Request, Response, SttpBackend}
import sttp.model.StatusCode

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class OneFrameBulkFetcherTest extends AsyncWordSpecLike with Matchers {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)))

  val stubBackend: SttpBackendStub[Identity, Nothing, Nothing] = HttpURLConnectionBackend.stub

  val config: OneFrameConfig = OneFrameConfig(
    HttpConfig("localhost", 8080, 2.minutes),
    RetryConfig(3, 2.seconds, 5.seconds, 1.1),
    5.minutes
  )
  val tokenProvider = new AuthTokenProvider {
    override def getAuthToken: String = "some token"
  }
  val stubMatcher: Seq[Rate.Pair] => Request[_, _] => Boolean = pairs =>
    r => {
      val queryParams = pairs.map(p => s"pair=${p.from}${p.to}").mkString("&")
      r.uri.toString() == s"http://localhost:8080/rates?$queryParams"
    }

  val pairs: Seq[Rate.Pair] = Seq(Rate.Pair(Currency.USD, Currency.EUR), Rate.Pair(Currency.AUD, Currency.SGD))

  val response: String =
    """
      |[
      |    {
      |        "from": "USD",
      |        "to": "NZD",
      |        "bid": 0.21202340214153104,
      |        "ask": 0.04916615842488037,
      |        "price": 0.130594780283205705,
      |        "time_stamp": "2021-04-30T13:07:14.385Z"
      |    },
      |    {
      |        "from": "SGD",
      |        "to": "GBP",
      |        "bid": 0.6118225421857174,
      |        "ask": 0.8243869101616611,
      |        "price": 0.71810472617368925,
      |        "time_stamp": "2021-04-30T13:07:33.426Z"
      |    }
      |]
      |""".stripMargin

  "OneFrameBulkFetcher" should {

    "return rates for the given currencies" in {
      val expected = Seq(
        Rate(
          Rate.Pair(Currency.USD, Currency.NZD),
          Price(BigDecimal("0.130594780283205705")),
          Timestamp(OffsetDateTime.parse("2021-04-30T13:07:14.385Z"))
        ),
        Rate(
          Rate.Pair(Currency.SGD, Currency.GBP),
          Price(BigDecimal("0.71810472617368925")),
          Timestamp(OffsetDateTime.parse("2021-04-30T13:07:33.426Z"))
        )
      )

      val stubbedQuery: SttpBackend[Identity, Nothing, Nothing] =
        stubBackend.whenRequestMatches(stubMatcher(pairs)).thenRespond(response)

      val resp = new OneFrameBulkFetcher[IO](config, stubbedQuery, tokenProvider).getMany(pairs).unsafeRunSync()

      resp match {
        case Right(v) => expected should be(v)
        case Left(e: errors.Error) => fail(e.toString)
      }
    }

    "retry 3 times if there is a failure" in {
      val errorResponse = Response("error", StatusCode.InternalServerError, "Error Occurred!")
      val okResponse = Response.ok[String](response)

      val stubbedQuery: SttpBackend[Identity, Nothing, Nothing] =
        stubBackend
          .whenRequestMatches(stubMatcher(pairs))
          .thenRespondCyclicResponses(errorResponse, errorResponse, okResponse)

      val expected = Seq(
        Rate(
          Rate.Pair(Currency.USD, Currency.NZD),
          Price(BigDecimal("0.130594780283205705")),
          Timestamp(OffsetDateTime.parse("2021-04-30T13:07:14.385Z"))
        ),
        Rate(
          Rate.Pair(Currency.SGD, Currency.GBP),
          Price(BigDecimal("0.71810472617368925")),
          Timestamp(OffsetDateTime.parse("2021-04-30T13:07:33.426Z"))
        )
      )

      val resp = new OneFrameBulkFetcher[IO](config, stubbedQuery, tokenProvider).getMany(pairs).unsafeRunSync()
      resp match {
        case Right(v) => expected should be(v)
        case Left(e: errors.Error) => fail(e.toString)
      }
    }

    "return appropriate error for bulk fetch errors" in {
      val errorResponse = Response("System is down for maintenance!", StatusCode.InternalServerError, "error")
      val stubbedQuery: SttpBackend[Identity, Nothing, Nothing] =
        stubBackend.whenRequestMatches(stubMatcher(pairs)).thenRespond(errorResponse)

      val resp = new OneFrameBulkFetcher[IO](config, stubbedQuery, tokenProvider).getMany(pairs).unsafeRunSync()
      resp match {
        case Right(_) => fail("Expecting an Error!")
        case Left(e: OneFrameSystemError) => e.message.contains(errorResponse.body) should be(true)
        case Left(_) => fail("Invalid Error!")
      }
    }

    "return appropriate error for response parsing errors" in {
      val okResponse = Response.ok[String]("some string")
      val stubbedQuery: SttpBackend[Identity, Nothing, Nothing] =
        stubBackend.whenRequestMatches(stubMatcher(pairs)).thenRespond(okResponse)

      val resp = new OneFrameBulkFetcher[IO](config, stubbedQuery, tokenProvider).getMany(pairs).unsafeRunSync()
      resp match {
        case Right(_) => fail("Expecting an Error!")
        case Left(e: OneFrameSystemError) => e.message.contains("expected json value got") should be(true)
        case Left(_) => fail("Invalid Error!")
      }
    }

  }
}
