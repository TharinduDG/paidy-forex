package forex.utils

import cats.effect._
import cats.implicits._
import cats.{ApplicativeError, Defer, MonadError}
import forex.config.RetryConfig

trait WithRetrySupport[F[_]] {
  private def loop[A, S](fa: F[A], initial: S)(
      f: (Throwable, S, S => F[A]) => F[A]
  )(implicit F: ApplicativeError[F, Throwable], D: Defer[F]): F[A] = {
      fa.handleErrorWith { err =>
        f(err, initial, state => D.defer(loop(fa, state)(f)))
      }
    }

  def executeWithRetry[A](fa: F[A], config: RetryConfig)(implicit
                                                         F: MonadError[F, Throwable],
                                                         D: Defer[F],
                                                         timer: Timer[F]): F[A] =
    loop(fa, config) { (error, state, retry) =>
      if (state.canRetry)
        timer.sleep(state.delay) *> retry(state.evolve)
      else
        F.raiseError(error)
    }
}
