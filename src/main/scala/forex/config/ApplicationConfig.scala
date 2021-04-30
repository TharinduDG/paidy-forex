package forex.config

import scala.concurrent.duration.{Duration, FiniteDuration}

case class ApplicationConfig(
                              http: HttpConfig,
                              oneFrame: OneFrameConfig,
                              cacheEntryTimeout: FiniteDuration,
                              dummyAuthToken: String
                            )

case class HttpConfig(
                       host: String,
                       port: Int,
                       timeout: FiniteDuration
                     )

case class OneFrameConfig(
                           http: HttpConfig,
                           retryConfig: RetryConfig,
                           ratesRefresh: FiniteDuration
                         )

final case class RetryConfig(
                              maxRetries: Int,
                              initialDelay: FiniteDuration,
                              maxDelay: FiniteDuration,
                              backoffFactor: Double,
                              private val evolvedDelay: Option[FiniteDuration] = None,
                            ) {
  def canRetry: Boolean = maxRetries > 0

  def delay: FiniteDuration =
    evolvedDelay.getOrElse(initialDelay)

  def evolve: RetryConfig =
    copy(
      maxRetries = math.max(maxRetries - 1, 0),
      evolvedDelay = Some {
        val nextDelay = evolvedDelay.getOrElse(initialDelay) * backoffFactor
        maxDelay.min(nextDelay) match {
          case ref: FiniteDuration => ref
          case _: Duration.Infinite => maxDelay
        }
      }
    )
}