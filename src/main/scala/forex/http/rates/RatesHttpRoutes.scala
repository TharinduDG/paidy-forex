package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.errors.Error.RateLookupFailed
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._
  import Protocol._
  import QueryParams._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      from.fold(
        fromParseFailures => BadRequest(ErrorResponse(fromParseFailures.head.details)),
        fromCurrency =>
          to.fold(
            toParseFailures => BadRequest(ErrorResponse(toParseFailures.head.details)),
            toCurrency =>
              if (fromCurrency == toCurrency) BadRequest(ErrorResponse(s"`from` & `to` currencies cannot be the same"))
              else
                rates
                  .get(RatesProgramProtocol.GetRatesRequest(fromCurrency, toCurrency))
                  .flatMap {
                    case Left(error: RateLookupFailed) => InternalServerError(ErrorResponse(error.msg))
                    case Right(rate)             => Ok(rate.asGetApiResponse)
                  }
          )
      )
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}
