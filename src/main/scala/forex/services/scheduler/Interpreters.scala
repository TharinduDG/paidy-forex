package forex.services.scheduler

import cats.effect.{Async, Timer}
import forex.services.scheduler.interpreters.RatesRefreshScheduler
import forex.services.{CacheMap, OneFrameService}

import scala.concurrent.duration.FiniteDuration

object Interpreters {

  def apply[F[_] : Async : Timer](oneFrame: OneFrameService[F],
                                  cache: F[CacheMap],
                                  sleepDuration: FiniteDuration): RatesRefreshScheduler[F] =
    RatesRefreshScheduler(oneFrame, cache, sleepDuration)

}
