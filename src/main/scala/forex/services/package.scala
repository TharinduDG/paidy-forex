package forex

import java.util.concurrent.ConcurrentHashMap

import forex.domain.Rate
import forex.services.scheduler.{Algebra, Interpreters}

package object services {
  type CacheMap = ConcurrentHashMap[Rate.Pair, Rate]

  type RatesFetcherService[F[_]] = rates.Algebra[F]
  final val RatesFetcherService = rates.Interpreters

  type RatesRefreshService[F[_]] = Algebra[F]
  final val RatesRefreshService = Interpreters

  type OneFrameService[F[_]] = oneframe.Algebra[F]
  final val OneFrameService = oneframe.Interpreters
}
