package forex.http.oneframe

import java.time.OffsetDateTime

import forex.domain.{Currency, Price, Rate, Timestamp}
import io.circe.{Decoder, HCursor}

import scala.util.Try

object Protocol {

  implicit val currencyDecoder: Decoder[Currency] = Currency.circeDecoder
  implicit val timestampDecoder: Decoder[OffsetDateTime] =
    implicitly[Decoder[String]].emapTry(s => Try(OffsetDateTime.parse(s)))

  implicit val rateDecoder: Decoder[Rate] = (cursor: HCursor) =>
    for {
      from <- cursor.downField("from").as[Currency]
      to <- cursor.downField("to").as[Currency]
      price <- cursor.downField("price").as[BigDecimal]
      timestamp <- cursor.downField("time_stamp").as[OffsetDateTime]
    } yield {
      Rate(Rate.Pair(from, to), Price(price), Timestamp(timestamp))
    }

  implicit val errorStrOrRatesDecoder: Decoder[Either[String, List[Rate]]] =
    implicitly[Decoder[String]].either(Decoder.decodeList(rateDecoder))
}