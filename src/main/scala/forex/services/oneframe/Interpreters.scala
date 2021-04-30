package forex.services.oneframe

import cats.effect.{Async, Timer}
import forex.config.OneFrameConfig
import forex.services.OneFrameService
import forex.services.oneframe.interpreters.OneFrameBulkFetcher
import forex.utils.AuthTokenProvider
import sttp.client._

object Interpreters {

  def live[F[_]: Async: Timer](config: OneFrameConfig,
                               httpClient: SttpBackend[Identity, Nothing, Nothing],
                               tokenProvider: AuthTokenProvider): OneFrameService[F] =
    OneFrameBulkFetcher(config, httpClient, tokenProvider)
}
