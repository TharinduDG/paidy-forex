package forex.service.scheduler

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.concurrent.{ConcurrentHashMap, Executors}

import cats.effect.{IO, Timer}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.oneframe.errors
import forex.services.oneframe.errors.Error.OneFrameConnectionError
import forex.services.scheduler.interpreters.RatesRefreshScheduler
import forex.services.{CacheMap, OneFrameService}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class RatesRefreshSchedulerTest extends AsyncWordSpecLike with Matchers {

  val pair: Rate.Pair = Rate.Pair(Currency.USD, Currency.NZD)

  val oldRate: Rate = Rate(
    pair,
    Price(BigDecimal(0.42D)),
    Timestamp(OffsetDateTime.of(2021, 4, 29, 5, 24, 30, 0, ZoneOffset.UTC))
  )

  val newRate: Rate = Rate(
    pair,
    Price(BigDecimal(0.43D)),
    Timestamp(OffsetDateTime.of(2021, 4, 29, 5, 25, 30, 0, ZoneOffset.UTC))
  )

  class TestOneFrameService extends OneFrameService[IO] {
    private var states: List[Either[errors.Error, List[Rate]]] = List(Right(List(oldRate)), Right(List(newRate)), Right(List.empty), Left(OneFrameConnectionError("Connection failed!")))

    private def fetchRates(): Either[errors.Error, List[Rate]] = {
      val data = states.headOption.getOrElse(Right(List.empty))
      states = states.tail
      data
    }

    override def getMany(pairs: Seq[Rate.Pair]): IO[Either[errors.Error, List[Rate]]] = {
      IO.pure(fetchRates())
    }
  }

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1)))

  "RatesRefreshScheduler" should {
    "update the cache with the returned rates" in {

      val cache: IO[CacheMap] = IO.pure(new ConcurrentHashMap())
      val service = RatesRefreshScheduler[IO](new TestOneFrameService, cache, 1.second)

      service
        .refreshScheduler()
        .take(1)
        .compile
        .drain
        .unsafeRunSync()

      val result = Option(service.getRates.unsafeRunSync().get(pair))

      result should equal(Some(oldRate))
    }

    "override the cache with the latest rates" in {

      val cache: IO[CacheMap] = IO.pure(new ConcurrentHashMap())
      val service = RatesRefreshScheduler[IO](new TestOneFrameService, cache, 1.second)

      service
        .refreshScheduler()
        .take(2)
        .compile
        .drain
        .unsafeRunSync()

      val result = Option(service.getRates.unsafeRunSync().get(pair))

      result should equal(Some(newRate))
    }

    "should ignore empty rates fetches and retain the old rates" in {
      val cache: IO[CacheMap] = IO.pure(new ConcurrentHashMap())
      val service = RatesRefreshScheduler[IO](new TestOneFrameService, cache, 1.second)

      service
        .refreshScheduler()
        .take(3)
        .compile
        .drain
        .unsafeRunSync()

      val result = Option(service.getRates.unsafeRunSync().get(pair))

      result should equal(Some(newRate))
    }

    "should ignore errors and retain the old rates" in {
      val cache: IO[CacheMap] = IO.pure(new ConcurrentHashMap())
      val service = RatesRefreshScheduler[IO](new TestOneFrameService, cache, 1.second)

      service
        .refreshScheduler()
        .take(4)
        .compile
        .drain
        .unsafeRunSync()

      val result = Option(service.getRates.unsafeRunSync().get(pair))

      result should equal(Some(newRate))
    }
  }
}
