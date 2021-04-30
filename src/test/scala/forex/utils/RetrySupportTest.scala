package forex.utils

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import cats.effect.{IO, Timer}
import forex.config.RetryConfig
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class RetrySupportTest extends AnyWordSpecLike with Matchers {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4)))
  val retryConfig: RetryConfig =
    RetryConfig(
      maxRetries = 3,
      initialDelay = 100.milliseconds,
      maxDelay = 200.milliseconds,
      1.1
    )

  class RetryTester extends RetrySupport[IO] {
    private val counter: AtomicInteger = new AtomicInteger(0)

    def failingFn(): IO[Int] = {
      if (counter.get() < 3) {
        counter.incrementAndGet()
        throw new RuntimeException("Simulating Failure...")
      }
      IO.pure(counter.get())
    }

    def nonFailingFn(): IO[Int] = {
      IO.pure(counter.incrementAndGet())
    }

    def callWithRetry: IO[Int]    = executeWithRetry(IO.suspend(failingFn()), retryConfig)
    def callWithoutRetry: IO[Int] = executeWithRetry(IO.suspend(nonFailingFn()), retryConfig)
    def getCounter: Int = counter.get()
  }

  val retryTester = new RetryTester()

  "RetrySupport" should {
    "increment counter for every time when a failure occurs" in {
      val result = retryTester.callWithRetry.unsafeRunSync()

      result should be(3)
    }

    "increment counter by 1 when there is no exception" in {
      val initialCounter = retryTester.getCounter
      val finalCounter = retryTester.callWithoutRetry.unsafeRunSync()

      initialCounter should be(finalCounter - 1)
    }
  }
}
