package forex.service.rates

import java.time.{OffsetDateTime, ZoneOffset, ZonedDateTime}
import java.util.concurrent.{ConcurrentHashMap, Executors}

import cats.effect.{IO, Timer}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.rates.errors.Error.OneFrameLookupFailed
import forex.services.rates.interpreters.OneFrameRatesFetcher
import forex.services.{CacheMap, RatesRefreshService}
import forex.utils.UTCDateTimeProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.jdk.javaapi.CollectionConverters

class OneFrameRatesFetcherTest extends AsyncWordSpecLike with Matchers {
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)))

  val dateTimeProvider = new UTCDateTimeProvider {
    override def now: ZonedDateTime = ZonedDateTime.of(2021, 4, 29, 5, 25, 30, 0, ZoneOffset.UTC)
  }

  class RatesCacheServiceMock(result: Map[Rate.Pair, Rate]) extends RatesRefreshService[IO] {
    override def getRates: IO[CacheMap] = IO.pure(new ConcurrentHashMap(CollectionConverters.asJava(result)))
  }

  "OneFrameRatesFetcher" should {
    "return an error when rate is not found" in {
      val service = new OneFrameRatesFetcher[IO](
        new RatesCacheServiceMock(Map.empty),
        5.minutes,
        dateTimeProvider
      )

      val result = service.get(Rate.Pair(Currency.SGD, Currency.USD)).unsafeRunSync()
      result should equal(Left(OneFrameLookupFailed("Rate not found. Try again later")))
    }

    "return an error when the rate has expired" in {
      val data = Map(
        Rate.Pair(Currency.SGD, Currency.USD) ->
          Rate(
            Rate.Pair(Currency.SGD, Currency.USD),
            Price(BigDecimal("0.53855002591123285")),
            Timestamp(OffsetDateTime.of(2021, 4, 29, 5, 20, 29, 0, ZoneOffset.UTC))
          )
      )

      val service = new OneFrameRatesFetcher[IO](
        new RatesCacheServiceMock(data),
        5.minutes,
        dateTimeProvider
      )

      val result = service.get(Rate.Pair(Currency.SGD, Currency.USD)).unsafeRunSync()
      result should equal(Left(OneFrameLookupFailed("Rate has expired!")))
    }

    "return the cached rate" in {
      val data = Map(
        Rate.Pair(Currency.SGD, Currency.USD) ->
          Rate(
            Rate.Pair(Currency.SGD, Currency.USD),
            Price(BigDecimal("0.53855002591123285")),
            Timestamp(OffsetDateTime.of(2021, 4, 29, 5, 25, 30, 0, ZoneOffset.UTC))
          )
      )
      val service = new OneFrameRatesFetcher[IO](
        new RatesCacheServiceMock(data),
        5.minutes,
        dateTimeProvider
      )

      val result = service.get(Rate.Pair(Currency.SGD, Currency.USD)).unsafeRunSync().getOrElse(throw new Exception("No result found!"))
      result should equal(Rate(
        Rate.Pair(Currency.SGD, Currency.USD),
        Price(BigDecimal("0.53855002591123285")),
        Timestamp(OffsetDateTime.of(2021, 4, 29, 5, 25, 30, 0, ZoneOffset.UTC))
      ))
    }

  }
}
