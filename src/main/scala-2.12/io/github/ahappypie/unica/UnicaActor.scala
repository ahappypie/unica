package io.github.ahappypie.unica

import akka.actor.{Actor, ActorLogging, Props}
import io.github.ahappypie.unica.grpc.unica.{UnicaRequest, UnicaResponse}

object UnicaActor {
  def props(deploymentId: Long, id: Long): Props = Props(new UnicaActor(deploymentId, id))
}

class UnicaActor(deploymentId: Long, id: Long) extends Actor with ActorLogging {

  val epoch: Long = 1543622400000L

  private val actorIdBits = 4L
  private val deploymentIdBits = 6L
  private val maxActorId = -1L ^ (-1L << actorIdBits)
  private val maxDeploymentId = -1L ^ (-1L << deploymentIdBits)
  private val sequenceBits = 12L

  private val actorIdShift = sequenceBits
  private val deploymentIdShift = sequenceBits + actorIdBits
  private val timestampShift = sequenceBits + actorIdBits + deploymentIdBits
  private val sequenceMask = -1L ^ (-1L << sequenceBits)

  private var lastTimestamp = -1L
  private var sequence = 0L

  override def preStart(): Unit = {
    super.preStart()
    if(id > maxActorId || id < 0) {
      throw new IllegalArgumentException("actor id can't be greater than %d or less than 0".format(maxActorId))
    }

    if(deploymentId > maxDeploymentId || deploymentId < 0) {
      throw new IllegalArgumentException("deployment id can't be greater than %d or less than 0".format(maxDeploymentId))
    }

    log.info("actor starting. timestamp shift %d, deployment id bits %d, actor id bits %d, sequence bits %d, actorid %d".format(
      timestampShift, deploymentIdBits, actorIdBits, sequenceBits, id))
  }


  override def receive: Receive = {
    case req: UnicaRequest => { val uid = generate()
      log.info("actor id %d generated uid %s".format(id, uid))
      sender ! UnicaResponse(uid)
      context.parent ! UnicaResponse(uid)
    }
  }

  def generate(): Long = synchronized {
    var timestamp = System.currentTimeMillis()

    if(timestamp < lastTimestamp) {
      log.error("clock is moving backwards. Rejecting requests until %d", lastTimestamp)
      throw new Exception("clock is moving backwards. Rejecting requests for %d milliseconds".format(lastTimestamp - timestamp))
    }

    if(lastTimestamp == timestamp) {
      sequence = (sequence + 1) & sequenceMask
      if(sequence == 0) {
        timestamp = holdUntil(lastTimestamp)
      }
    } else {
      sequence = 0
    }

    lastTimestamp = timestamp

    ((timestamp - epoch) << timestampShift) |
      (deploymentId << deploymentIdShift) |
      (id << actorIdShift) |
      sequence
  }

  private def holdUntil(ts: Long): Long = {
    var timestamp = System.currentTimeMillis()
    while(timestamp <= ts) {
      timestamp = System.currentTimeMillis()
    }
    timestamp
  }
}
