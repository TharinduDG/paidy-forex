package forex.services.scheduler.interpreters

import cats.effect.{Async, Sync, Timer}
import cats.implicits._
import forex.domain.{Currency, Rate}
import forex.services.oneframe.errors
import forex.services.{CacheMap, OneFrameService, RatesRefreshService}
import fs2.Stream
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.FiniteDuration
import scala.jdk.javaapi.CollectionConverters

class RatesRefreshScheduler[F[_] : Async : Timer](oneFrame: OneFrameService[F],
                                                  cacheService: F[CacheMap],
                                                  sleepDuration: FiniteDuration)
  extends RatesRefreshService[F] {

  private val logger: F[SelfAwareStructuredLogger[F]] = Slf4jLogger.create[F]

  override def getRates: F[CacheMap] = cacheService

  private val allCurrencyCombinations: List[Rate.Pair] = Currency.combinations.map(Rate.Pair.tupled)

  def refreshScheduler(): Stream[F, Unit] = {
    for {
      _ <- Stream.awakeEvery[F](sleepDuration)
      s <- Stream.eval(refreshRatesCache())
    } yield s
  }

  private def refreshRatesCache(): F[Unit] = {
    val ratesFetch = for {
      ratesBulk <- oneFrame.getMany(allCurrencyCombinations)
      ratesMap <- ratesBulk match {
        case Left(error: errors.Error) => Async[F].raiseError(new Exception(error.getMessage))
        case Right(Nil) => Async[F].raiseError(new Exception("Empty set of rates found!"))
        case Right(rates) => Sync[F].pure(rates.map(rate => rate.pair -> rate).toMap)
      }
      cache <- cacheService
      _ = cache.putAll(CollectionConverters.asJava(ratesMap))
      _ <- logger.flatMap(_.info("Rates refreshed..."))
    } yield ()

    ratesFetch.handleErrorWith { _ =>
      logger.flatMap(_.error("Rates cache refresh failed!"))
    }
  }
}

object RatesRefreshScheduler {
  def apply[F[_] : Async : Timer](oneFrame: OneFrameService[F],
                                  cache: F[CacheMap],
                                  sleepDuration: FiniteDuration): RatesRefreshScheduler[F] =
    new RatesRefreshScheduler(oneFrame, cache, sleepDuration)
}
