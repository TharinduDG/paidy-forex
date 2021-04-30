package forex.programs.rates

import cats.Functor
import cats.data.EitherT
import forex.domain._
import forex.programs.rates.errors._
import forex.services.RatesFetcherService

class Program[F[_]: Functor](
    ratesService: RatesFetcherService[F]
) extends Algebra[F] {

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] =
    EitherT(ratesService.get(Rate.Pair(request.from, request.to))).leftMap(toProgramError(_)).value

}

object Program {

  def apply[F[_]: Functor](
      ratesService: RatesFetcherService[F]
  ): Algebra[F] = new Program[F](ratesService)

}
