package forex

import java.time.{Instant, ZoneOffset, ZonedDateTime}
import java.util.concurrent.ConcurrentHashMap

import cats.effect.{Concurrent, Sync, Timer}
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import forex.services.scheduler.interpreters.RatesRefreshScheduler
import forex.utils.{AuthTokenProvider, UTCDateTimeProvider}
import fs2.Stream
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}
import sttp.client.HttpURLConnectionBackend

class Module[F[_] : Concurrent : Timer](config: ApplicationConfig) {

  private val oneFrame: OneFrameService[F] = {
    val httpClient = HttpURLConnectionBackend()
    val dummyToken = config.dummyAuthToken
    val tokenProvider = new AuthTokenProvider {
      override def getAuthToken: String = dummyToken
    }
    OneFrameService.live[F](config.oneFrame, httpClient, tokenProvider)
  }

  private val ratesCache: RatesRefreshScheduler[F] = {
    val concurrentMap: CacheMap = new ConcurrentHashMap()
    RatesRefreshService[F](oneFrame, Sync[F].delay(concurrentMap), config.oneFrame.ratesRefresh)
  }

  private val ratesService: RatesFetcherService[F] = {
    val dateTimeProvider = new UTCDateTimeProvider {
      override def now: ZonedDateTime = Instant.now().atZone(ZoneOffset.UTC)
    }
    RatesFetcherService[F](ratesCache, config.cacheEntryTimeout, dateTimeProvider)
  }

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

  val ratesRefreshScheduler: Stream[F, Unit] = ratesCache.refreshScheduler()
}
