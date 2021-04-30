package forex.services.rates

import cats.effect.Sync
import forex.services.RatesRefreshService
import forex.services.rates.interpreters._
import forex.utils.UTCDateTimeProvider

import scala.concurrent.duration.FiniteDuration

object Interpreters {
  def apply[F[_]: Sync](cache: RatesRefreshService[F], cacheTimeout: FiniteDuration, dateProvider: UTCDateTimeProvider) =
    new OneFrameRatesFetcher[F](cache, cacheTimeout, dateProvider)
}
