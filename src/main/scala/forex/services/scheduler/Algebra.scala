package forex.services.scheduler

import forex.services.CacheMap

trait Algebra[F[_]] {
  def getRates: F[CacheMap]
}
