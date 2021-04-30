package forex.http.rates

import cats.implicits.toBifunctorOps
import forex.domain.Currency
import org.http4s.dsl.impl.ValidatingQueryParamDecoderMatcher
import org.http4s.{ ParseFailure, QueryParamDecoder }

import scala.util.Try

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].emap { p =>
      Try(Currency.fromString(p)).toEither
        .leftMap(
          t =>
            ParseFailure(
              t.getMessage,
              s"Invalid Currency Code `$p`. Supported Codes: ${Currency.values.mkString(", ")}"
            )
        )
    }

  object FromQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("to")

}
