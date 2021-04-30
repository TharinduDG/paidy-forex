package forex.services.rates.interpreters

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneOffset}

import cats.effect.Sync
import cats.implicits._
import forex.domain.Rate
import forex.services.rates.errors.Error.OneFrameLookupFailed
import forex.services.rates.{Algebra, errors}
import forex.services.{CacheMap, RatesRefreshService}
import forex.utils.UTCDateTimeProvider
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.FiniteDuration

class OneFrameRatesFetcher[F[_] : Sync](ratesCache: RatesRefreshService[F],
                                        cacheTimeout: FiniteDuration,
                                        dateProvider: UTCDateTimeProvider)
  extends Algebra[F] {

  private val logger: F[SelfAwareStructuredLogger[F]] = Slf4jLogger.create[F]

  override def get(request: Rate.Pair): F[Either[errors.Error, Rate]] =
    ratesCache.getRates.flatMap { rates: CacheMap =>
      val cachedRate = Option(rates.get(request))

      Sync[F]
        .pure(cachedRate)
        .flatMap {
          case Some(rate) =>
            if (isObsolete(rate.timestamp.value))
              logger.flatMap(_.warn(s"Obsolete currency value found: $rate")) *> Sync[F].pure(
                Left(OneFrameLookupFailed("Rate has expired!"))
              )
            else Sync[F].pure(Right(rate))

          case None => logger.flatMap(_.warn(s"Rates not found: ${request.from} ${request.to}")) *> Sync[F].pure(
            Left(OneFrameLookupFailed("Rate not found. Try again later"))
          )
        }
    }

  private def isObsolete(dateTime: OffsetDateTime): Boolean =
    dateProvider.now
      .isAfter(dateTime.atZoneSameInstant(ZoneOffset.UTC).plus(cacheTimeout.toMillis, ChronoUnit.MILLIS))
}
