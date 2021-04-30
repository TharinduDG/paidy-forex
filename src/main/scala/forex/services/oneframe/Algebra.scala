package forex.services.oneframe

import forex.domain.Rate
import forex.services.oneframe.errors.Error

trait Algebra[F[_]] {
  def getMany(pairs: Seq[Rate.Pair]): F[Either[Error, List[Rate]]]
}
