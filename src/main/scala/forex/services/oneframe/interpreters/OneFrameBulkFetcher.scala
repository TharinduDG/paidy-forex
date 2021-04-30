package forex.services.oneframe.interpreters

import cats.effect.{Async, Timer}
import cats.implicits._
import forex.config.OneFrameConfig
import forex.domain.Rate
import forex.http.oneframe.Protocol._
import forex.services.oneframe.errors.Error
import forex.services.oneframe.errors.Error.{OneFrameConnectionError, OneFrameSystemError}
import forex.services.oneframe.{Algebra, errors}
import forex.utils.{AuthTokenProvider, RetrySupport}
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.{Error => CError}
import sttp.client._
import sttp.client.circe.asJson
import sttp.model.{Header, Uri}

class OneFrameBulkFetcher[F[_] : Async : Timer](config: OneFrameConfig,
                                                httpClient: SttpBackend[Identity, Nothing, Nothing],
                                                tokenProvider: AuthTokenProvider)
  extends Algebra[F]
    with RetrySupport[F] {

  private val logger: F[SelfAwareStructuredLogger[F]] = Slf4jLogger.create[F]

  override def getMany(pairs: Seq[Rate.Pair]): F[Either[Error, List[Rate]]] = {
    val params = pairs.map(pair => "pair" -> s"${pair.from}${pair.to}")
    val url = Uri("http")
      .host(config.http.host)
      .port(config.http.port)
      .path("rates")
      .params(params: _*)

    val ratesRefreshRequest = Async[F]
      .delay {
        httpClient
          .send {
            basicRequest
              .headers(Header("accept", "application/json"), Header("token", tokenProvider.getAuthToken))
              .readTimeout(config.http.timeout)
              .get(url)
              .response(asJson[Either[String, List[Rate]]])
          }
      }
      .flatMap[Either[errors.Error, List[Rate]]] { r =>
        r.body match {
          case Left(e: ResponseError[CError]) =>
            logger.flatMap(_.error(e.fillInStackTrace())("Error when sending request!")) *> Async[F]
              .raiseError[Either[errors.Error, List[Rate]]](new Exception(e.getMessage))
          case Right(result: Either[String, List[Rate]]) =>
            result match {
              case Left(e: String) =>
                logger.flatMap(_.error(e)) *> Async[F].raiseError[Either[errors.Error, List[Rate]]](new Exception(e))
              case Right(rates) => Async[F].pure(Right(rates))
            }
        }
      }

    val retriedResponse = executeWithRetry(ratesRefreshRequest, config.retryConfig)
    retriedResponse.handleErrorWith {
      case e: SttpClientException => logger.flatMap(_.error(e)("Connection Error")) *> Async[F].pure(Left(OneFrameConnectionError(e.getMessage)))
      case t: Throwable => logger.flatMap(_.error(t)("Unexpected Error")) *> Async[F].pure(Left(OneFrameSystemError(t.getMessage)))
    }
  }
}

object OneFrameBulkFetcher {
  def apply[F[_] : Async : Timer](config: OneFrameConfig,
                                  httpClient: SttpBackend[Identity, Nothing, Nothing],
                                  tokenProvider: AuthTokenProvider): OneFrameBulkFetcher[F] =
    new OneFrameBulkFetcher(config, httpClient, tokenProvider)
}