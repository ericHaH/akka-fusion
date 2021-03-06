/*
 * Copyright 2019 akka-fusion.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.stats

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.Routers
import akka.cluster.typed.Cluster
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.ClusterSingletonSettings
import akka.cluster.typed.SingletonActor

object AppOneMaster {
  val WorkerServiceKey = ServiceKey[StatsWorker.Process]("Worker")

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      val singletonSettings = ClusterSingletonSettings(ctx.system).withRole("compute")
      val serviceSingleton = SingletonActor(Behaviors.setup[StatsService.Command] { ctx =>
        // the service singleton accesses available workers through a group router
        val workersRouter = ctx.spawn(Routers.group(WorkerServiceKey), "WorkersRouter")
        StatsService(workersRouter)
      }, "StatsService").withStopMessage(StatsService.Stop).withSettings(singletonSettings)
      val serviceProxy = ClusterSingleton(ctx.system).init(serviceSingleton)

      if (cluster.selfMember.hasRole("compute")) {
        // on every compute node N local workers, which a cluster singleton stats service delegates work to
        val numberOfWorkers = ctx.system.settings.config.getInt("stats-service.workers-per-node")
        ctx.log.info("Starting {} workers", numberOfWorkers)
        (0 to numberOfWorkers).foreach { n =>
          val worker = ctx.spawn(StatsWorker(), s"StatsWorker$n")
          ctx.system.receptionist ! Receptionist.Register(WorkerServiceKey, worker)
        }
      }
      if (cluster.selfMember.hasRole("client")) {
        ctx.spawn(StatsSampleClient(serviceProxy), "Client")
      }
      Behaviors.empty
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup("compute", 25251)
      startup("compute", 25252)
      startup("compute", 0)
      startup("client", 0)
    } else {
      require(args.size == 2, "Usage: role port")
      startup(args(0), args(1).toInt)
    }
  }

  def startup(role: String, port: Int): Unit = {
    // Override the configuration of the port when specified as program argument
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [compute]
      """).withFallback(ConfigFactory.load("stats"))

    val system = ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
  }
}
