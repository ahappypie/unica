package io.github.ahappypie.unica

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.ahappypie.unica.grpc.unica.{UnicaRequest, UnicaResponse}

import scala.collection.mutable

object UnicaSupervisor {
  def props(deploymentId: Long): Props = Props(new UnicaSupervisor(deploymentId))
}

class UnicaSupervisor(deploymentId: Long) extends Actor with ActorLogging {

  private val actorBits = 4L
  private val maxActorId = -1L ^ (-1L << actorBits)
  private val workers: mutable.Queue[ActorRef] = new mutable.Queue[ActorRef]()

  override def preStart(): Unit = {
    for(i <- 0L to maxActorId) {
      workers += context.actorOf(UnicaActor.props(deploymentId, i))
    }
  }

  override def receive: Receive = {
    case req: UnicaRequest => workers.dequeue().forward(req)
      log.info("dequeued, actors left %d".format(workers.length))
    case res: UnicaResponse => workers += sender()
      log.info("enqueued, actors available %d".format(workers.length))
  }
}
