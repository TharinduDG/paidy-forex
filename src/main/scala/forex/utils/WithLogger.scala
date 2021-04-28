package forex.utils

import cats.effect.Sync
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait WithLogger[F[_]] {
  implicit protected def sync: Sync[F]
  protected val getLogger: F[SelfAwareStructuredLogger[F]] = Slf4jLogger.create[F]
}
