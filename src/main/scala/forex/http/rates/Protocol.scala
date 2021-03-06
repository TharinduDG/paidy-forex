package forex.http
package rates

import forex.domain.Currency.show
import forex.domain.Rate.Pair
import forex.domain._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder

object Protocol {

  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  final case class GetApiRequest(
                                  from: Currency,
                                  to: Currency
                                )

  final case class GetApiResponse(
                                   from: Currency,
                                   to: Currency,
                                   price: Price,
                                   timestamp: Timestamp
                                 )

  implicit val currencyEncoder: Encoder[Currency] = Encoder.instance { show.show _ andThen Json.fromString }

  implicit val pairEncoder: Encoder[Pair] = deriveConfiguredEncoder

  implicit val rateEncoder: Encoder[Rate] = deriveConfiguredEncoder

  implicit val responseEncoder: Encoder[GetApiResponse] = deriveConfiguredEncoder

  implicit val errorResponseEncoder: Encoder[ErrorResponse] = deriveConfiguredEncoder

}
