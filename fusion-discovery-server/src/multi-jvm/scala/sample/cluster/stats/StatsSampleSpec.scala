package sample.cluster.stats

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior, Props, SpawnProtocol}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.MultiNodeConfig
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import sample.stats.{App, StatsService, StatsWorker}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object StatsSampleSpecConfig extends MultiNodeConfig {
  // register the named roles (nodes) of the test
  // note that this is not the same thing as cluster node roles
  val first = role("first")
  val second = role("second")
  val third = role("thrid")

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    akka.cluster.roles = [compute]
    """).withFallback(ConfigFactory.load()))

  nodeConfig(second)(ConfigFactory.parseString("""{akka.remote.artery.canonical.port = 2552}"""))
  nodeConfig(third)(ConfigFactory.parseString("""{akka.remote.artery.canonical.port = 2553}"""))

}
// need one concrete test class per node
class StatsSampleSpecMultiJvmNode1 extends StatsSampleSpec
class StatsSampleSpecMultiJvmNode2 extends StatsSampleSpec
class StatsSampleSpecMultiJvmNode3 extends StatsSampleSpec

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

abstract class StatsSampleSpec extends MultiNodeSpec(StatsSampleSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll
  with ImplicitSender {

  import StatsSampleSpecConfig._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  implicit val typedSystem = system.toTyped
  // use the SpawnProtocol to run actors even though the multi-jvm testkit only knows of the classic APIs
  val spawnActor = system.actorOf(PropsAdapter(SpawnProtocol())).toTyped[SpawnProtocol.Command]
  def spawn[T](behavior: Behavior[T], name: String): ActorRef[T] = {
    implicit val timeout: Timeout = 3.seconds
    val f: Future[ActorRef[T]] = spawnActor.ask(SpawnProtocol.Spawn(behavior, name, Props.empty, _))

    Await.result(f, 3.seconds)
  }


  "The stats sample" must {

    "illustrate how to startup cluster" in within(15.seconds) {
//      Cluster(typedSystem).subscriptions
      akka.cluster.Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val firstAddress = node(first).address
      val secondAddress = node(second).address
      val thirdAddress = node(third).address

      akka.cluster.Cluster(system).join(firstAddress)


      receiveN(3).collect { case MemberUp(m) => m.address }.toSet should be(
        Set(firstAddress, secondAddress, thirdAddress))

      akka.cluster.Cluster(system).unsubscribe(testActor)

      testConductor.enter("all-up")
    }

    "show usage of the statsService from one node" in within(15.seconds) {
      runOn(first, second) {
        val worker = spawn(StatsWorker(), "StatsWorker")
        val service = spawn(StatsService(worker), "StatsService")
        typedSystem.receptionist ! Receptionist.Register(App.StatsServiceKey, service)
      }
      runOn(third) {
        assertServiceOk()
      }

      testConductor.enter("done-2")
    }

    def assertServiceOk(): Unit = {
      // eventually the service should be ok,
      // first attempts might fail because worker actors not started yet
      awaitAssert {
        val probe = TestProbe[AnyRef]()
        typedSystem.receptionist ! Receptionist.Find(App.StatsServiceKey, probe.ref)
        val App.StatsServiceKey.Listing(actors) = probe.expectMessageType[Receptionist.Listing]
        actors should not be empty

        actors.head ! StatsService.ProcessText("this is the text that will be analyzed", probe.ref)
        probe.expectMessageType[StatsService.JobResult].meanWordLength should be(
          3.875 +- 0.001)
      }
    }

    "show usage of the statsService from all nodes" in within(15.seconds) {
      assertServiceOk()
      testConductor.enter("done-3")
    }

  }

}
