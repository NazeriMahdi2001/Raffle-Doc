package controllers

import javax.inject._
import play.api.mvc._
import io.circe.Json
import play.api.libs.circe.Circe
import scalaj.http._
import io.circe.jawn
import helpers.Utils
import scorex.crypto.hash._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.Address
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.impl.ErgoTreeContract

object client

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents, utils: Utils) extends BaseController
  with Circe {

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def exception(e: Throwable): Result = {
    BadRequest(s"""{"success": false, "message": "${e.getMessage}"}""").as("application/json")
  }

  /*
  Input format should be like:
    {\"data1\": \"hello\", \"data2\": \"to\", \"data3\": \"the\", \"data4\": \"world\"}"
  */
//  def getHash(): Action[Json] = Action(circe.json) { implicit request =>
//    try {
//      val str1 = request.body.hcursor.downField("data1").as[String].getOrElse(throw new Throwable("data1 field must exist"))
//      val str2 = request.body.hcursor.downField("data2").as[String].getOrElse(throw new Throwable("data2 field must exist"))
//      val str3 = request.body.hcursor.downField("data3").as[String].getOrElse(throw new Throwable("data3 field must exist"))
//      val str4 = request.body.hcursor.downField("data4").as[String].getOrElse(throw new Throwable("data4 field must exist"))
//      val str = str1 + str2 + str3 + str4
//
//      val SHA1 = utils.SHA1(str)
//      val Blake256 = Blake2b256(str)
//      Ok(s"""{"SHA1": "${SHA1}", "Blake2b256": "${Blake256}"}""").as("application/json")
//
//    } catch {
//      case e: Throwable => exception(e)
//    }
//  }
//  def getById(tokenId: String) = Action {
//    try {
//      val response: HttpResponse[String] = Http(utils.getAPILink() + tokenId).header("Accept", "application/json").asString
//      val res1 = jawn.parse(response.body).getOrElse(throw new Throwable("parse error"))
//      val res2 = res1.hcursor.downField("items").as[List[Json]].getOrElse(throw new Throwable("parse error"))
//      val boxes = res2.map("\"" + _.hcursor.downField("boxId").as[String].getOrElse(throw new Throwable("parse error")) + "\"").mkString(", ")
//
//      Ok(
//        s"""{
//           |  "tokenId": "${tokenId}",
//           |  "boxIds" : [${boxes}]
//           |}""".stripMargin
//      ).as("application/json")
//    } catch {
//      case e: Throwable => exception(e)
//    }
//  }

  def boxToRaffle(boxId: String): String = {
    try {
      val response: HttpResponse[String] =
        Http(utils.getAPILink("getApiV1BoxesP1") + boxId)
          .header("Accept", "application/json").asString
      val box = jawn.parse(response.body).getOrElse(throw new Throwable("Body Not Found"))
      val register = box.hcursor.downField("additionalRegisters")
        .downField("R9").as[Json].getOrElse(throw new Throwable("parse error"))
      val id = box.hcursor.downField("boxId").as[String].getOrElse(throw new Throwable("parse error"))
      val name = register.hcursor.downField("name").as[String].getOrElse(throw new Throwable("parse error"))
      val description = register.hcursor.downField("description").as[String].getOrElse(throw new Throwable("parse error"))
      val deadline = register.hcursor.downField("deadline").as[String].getOrElse(throw new Throwable("parse error"))
      val erg = box.hcursor.downField("value").as[Long].getOrElse(throw new Throwable("parse error"))
      val organizer_addr = register.hcursor.downField("organizer_addr").as[String].getOrElse(throw new Throwable("parse error"))
      val min = register.hcursor.downField("min").as[Long].getOrElse(throw new Throwable("parse error"))
      val charity_addr =register.hcursor.downField("charity_addr").as[String].getOrElse(throw new Throwable("parse error"))
      val result = s"""{
                      |  "id" : "${id}"
                      |  "name" : "${name}"
                      |  "description" : "${description}"
                      |  "deadline" : "${deadline}"
                      |  "erg" : ${erg}
                      |  "organizer_addr" : ${organizer_addr}
                      |  "charity_addr" : ${charity_addr}
                      |  "min" : ${min}
                      |}""".stripMargin
      return result
    } catch {
      case e: Throwable => exception(e)
      return ""
    }
  }

  def getRaffles(offset: Int, limit: Int) = Action {
    try {
      val response: HttpResponse[String] =
        Http(utils.getAPILink("unspentBoxesAPILink") + utils.getTokenId() + s"?limit=${limit}&offset=${offset}")
        .header("Accept", "application/json").asString
//      val creatorBoxId = utils.getBoxCreator()
      val boxes = jawn.parse(response.body).getOrElse(throw new Throwable("Not Found"))
      val items = boxes.hcursor.downField("items").as[List[Json]].getOrElse(throw new Throwable("parse error"))
      val result = items.map(m => boxToRaffle(m.hcursor.downField("boxId").as[String].getOrElse(throw new Throwable("BoxId Not Found"))))
        .mkString(", ")
      Ok(
        s"""{
           | items : [${result}]
           | total : ${result.size}
           |}""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable => exception(e)
    }
  }

  def getRafflesById(raffleId: String) = Action {
    try {
      Ok(
        boxToRaffle(raffleId)
      ).as("application/json")
    } catch {
      case e: Throwable => exception(e)
    }
  }

  def createRaffle(name: String, description: String, deadlineHeight: Long, organizerAddr: String, charityAddr: String, minToRaise: Long) = Action {
    try {
      var transactionId: String = ""
      var raffleId: String = ""
      val client = RestApiErgoClient.create("http://213.239.193.208:9053/swagger", NetworkType.TESTNET, " ")
      client.execute(ctx => {
        val minFee = 1000000
        val secret = BigInt("187b05ba1eb459d3e347753e2fb9da0e2fb3211e3e1a896a0665666b6ab5a2a8", 16)
        val prover = ctx.newProverBuilder()
          .withDLogSecret(secret.bigInteger)
          .build()
        val pkAddress: Address = Address.create("9egoqjL1F5dJg4oUdLZLMZw8C968dfoMoRp4ytH9FXfJBbpk8bu") // should be changed
        val listBoxes = ctx.getUnspentBoxesFor(pkAddress)

        var sumBoxes: Long = 0
        listBoxes.forEach(sumBoxes += _.getValue)
        if (sumBoxes < 2 * minFee)
          throw new ErgoClientException(s"Not enough coins in the wallet to pay $minFee", null)

        val txB = ctx.newTxBuilder()

        val outBuilder = txB.outBoxBuilder()
          .contract(new ErgoTreeContract(pkAddress._address.script))
          .value(sumBoxes - 2 * minFee)
          .build()

        val scriptTokenRepo =
          s"""{
             |	val totalSoldTicket = SELF.R4[Long].get
             |  val totalSoldTicketBI: BigInt = totalSoldTicket.toBigInt
             |	val totalRaised = totalSoldTicket * ticketPrice
             |	val charityCoef = SELF.R5[Long].get
             |	val projectCoef = SELF.R6[Long].get
             |	val winnerCoef = 100L - charityCoef - projectCoef
             |	sigmaProp(
             |		if (HEIGHT < deadlineHeight) {
             |			allOf(Coll(
             |						// validate Script
             |						OUTPUTS(0).propositionBytes == SELF.propositionBytes,
             |						blake2b256(OUTPUTS(1).propositionBytes) == ticketScriptHash,
             |            OUTPUTS(1).R6[Coll[Byte]].get == blake2b256(SELF.propositionBytes),
             |						// minERG
             |						INPUTS(1).value >= ticketPrice + 2 * minFee,
             |						// validate Register
             |						OUTPUTS(0).R4[Long].get == totalSoldTicket + (INPUTS(1).value - 2 * minFee) / ticketPrice,
             |						OUTPUTS(1).R4[Long].get == totalSoldTicket,
             |						OUTPUTS(1).R5[Long].get == (INPUTS(1).value - 2 * minFee) / ticketPrice,
             |						// validate Token
             |						OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
             |						OUTPUTS(0).tokens(0)._2 == SELF.tokens(0)._2 - (INPUTS(1).value - 2 * minFee) / ticketPrice,
             |						OUTPUTS(0).tokens(1)._1 == SELF.tokens(1)._1, // Raffle Service Token
             |						OUTPUTS(1).tokens(0)._1 == SELF.tokens(0)._1,
             |						OUTPUTS(1).tokens(0)._2 == (INPUTS(1).value - 2 * minFee) / ticketPrice,
             |						// ERG Protect
             |						OUTPUTS(0).value == SELF.value + INPUTS(1).value - 2 * minFee,
             |						OUTPUTS(1).value == minFee,
             |						// same Coef
             |						OUTPUTS(0).R5[Long].get == charityCoef,
             |						OUTPUTS(0).R6[Long].get == projectCoef
             |						))
             |		}
             |		else {
             |  		if (totalRaised >= minToRaise) {
             |				allOf(Coll(
             |							// Validate Size
             |							INPUTS.size == 1 && OUTPUTS.size == 5,
             |              // Pay Back Raffle Service Token
             |              OUTPUTS(0).tokens(0)._1 == SELF.tokens(1)._1,
             |              OUTPUTS(0).tokens(0)._2 == 1,
             |              OUTPUTS(0).propositionBytes == servicePubKey.propBytes,
             |							// Charity Box
             |							OUTPUTS(1).value >= totalRaised * charityCoef / 100,
             |							OUTPUTS(1).propositionBytes == charityPubKey.propBytes,
             |							// Project Box
             |							OUTPUTS(2).value >= totalRaised * projectCoef / 100,
             |							OUTPUTS(2).propositionBytes == servicePubKey.propBytes,
             |							// Validate Seed
             |							CONTEXT.dataInputs(0).tokens(0)._1 == oracleNebulaNFT,
             |							// Winner Box
             |							OUTPUTS(3).value  >= totalRaised * winnerCoef / 100,
             |							blake2b256(OUTPUTS(3).propositionBytes) == winnerScriptHash,
             |							OUTPUTS(3).R4[Long].get == ((byteArrayToBigInt(CONTEXT.dataInputs(0).id.slice(0, 15)).toBigInt + totalSoldTicketBI) % totalSoldTicketBI).toBigInt,
             |							OUTPUTS(3).tokens(0)._1 == SELF.tokens(0)._1,
             |              OUTPUTS(3).tokens(0)._2 == SELF.tokens(0)._2
             |         ))
             |			}
             |			else {
             |			if (totalRaised < minToRaise) {
             |				if(totalSoldTicket > 0){
             |					allOf(Coll(
             |								// validate Script
             |								OUTPUTS(0).propositionBytes == SELF.propositionBytes,
             |								// validate Token & ERG
             |								OUTPUTS(0).tokens(0)._1 == SELF.tokens(0)._1,
             |								OUTPUTS(0).value >= SELF.value - (OUTPUTS(0).tokens(0)._2 - SELF.tokens(0)._2) * ticketPrice,
             |								OUTPUTS(0).tokens(0)._2 > SELF.tokens(0)._2,
             |                OUTPUTS(0).R4[Long].get == SELF.R4[Long].get - (OUTPUTS(0).tokens(0)._2 - SELF.tokens(0)._2)
             |					))
             |				}
             |				else
             |				{
             |					allOf(Coll(
             |								// Pay Back Raffle Service Token
             |                OUTPUTS(0).tokens(0)._1 == SELF.tokens(1)._1,
             |                OUTPUTS(0).tokens(0)._2 == 1,
             |                OUTPUTS(0).propositionBytes == servicePubKey.propBytes
             |					))
             |				}
             |			}
             |			else {
             |				false
             |			}
             |		}
             |	})
             |}""".stripMargin

        val serviceAddress: Address = Address.create(organizerAddr)
        val charityAddress: Address = Address.create(charityAddr)
        val winnerScript =
          s"""{
             |  sigmaProp(
             |		allOf(Coll(
             |					// Valid Ticket
             |					INPUTS(1).tokens(0)._1 == SELF.tokens(0)._1,
             |					INPUTS(1).R4[Long].get <= SELF.R4[Long].get,
             |					INPUTS(1).R4[Long].get + INPUTS(1).R5[Long].get > SELF.R4[Long].get
             |		))
             |	)
             |}""".stripMargin

        val winnerContract = ctx.compileContract(
          ConstantsBuilder.create()
            .build(),
          winnerScript)
        val winnerErgoTree = winnerContract.getErgoTree
        val winnerScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(winnerErgoTree.bytes)

        val TicketScript =
          s"""{
             |  val refundPhaseSpend = HEIGHT > deadlineHeight &&
             |												 blake2b256(INPUTS(0).propositionBytes) == SELF.R6[Coll[Byte]].get &&
             |												 INPUTS(0).tokens(0)._1 == SELF.tokens(0)._1
             |
             |	val winnerPhaseSpend = HEIGHT > deadlineHeight &&
             |												 blake2b256(INPUTS(0).propositionBytes) == winnerScriptHash &&
             |												 INPUTS(0).tokens(0)._1 == SELF.tokens(0)._1
             |
             |	val receiverCheck = OUTPUTS(1).propositionBytes	== SELF.R7[Coll[Byte]].get &&
             |											OUTPUTS(1).value == SELF.tokens(0)._2 * ticketPrice &&
             |											INPUTS.size == 2
             |
             |  val receiverCheckWinner = OUTPUTS(0).propositionBytes == SELF.R7[Coll[Byte]].get &&
             |											      OUTPUTS(0).value == INPUTS(0).value
             |
             |	sigmaProp((receiverCheck && refundPhaseSpend) || (receiverCheckWinner && winnerPhaseSpend))
             |}""".stripMargin

        val raffleProjectAddress : Address = Address.create(organizerAddr)
        val ticketContract = ctx.compileContract(
          ConstantsBuilder.create()
            .item("deadlineHeight", 50)
            .item("winnerScriptHash", winnerScriptHash)
            .item("ticketPrice", 1000000L)
            .item("projectPubKey", raffleProjectAddress.getPublicKey)
            .build(),
          TicketScript)

        val ticketErgoTree = ticketContract.getErgoTree
        val ticketScriptHash: Digest32 = scorex.crypto.hash.Blake2b256(ticketErgoTree.bytes)


        val contractTokenRepo = ctx.compileContract(
          ConstantsBuilder.create()
            .item("tokenId", ErgoId.create(utils.getTokenId()).getBytes)
            .item("ticketPrice", 1000000L)
            .item("minToRaise", minToRaise)
            .item("deadlineHeight", deadlineHeight)
            .item("charityPubKey", charityAddress.getPublicKey)
            .item("servicePubKey", serviceAddress.getPublicKey)
            .item("winnerScriptHash", winnerScriptHash)
            .item("minFee", 1000000L)
            .item("ticketScriptHash", ticketScriptHash)
            .item("oracleNebulaNFT", ErgoId.create(utils.getOracleId()).getBytes)
            .build(),
          scriptTokenRepo)

        val outNewRaffle = txB.outBoxBuilder()
          .contract(contractTokenRepo)
          .value(minFee)
          .tokens()
          .registers()
          .build()

        val tx = txB.boxesToSpend(listBoxes)
          .outputs(outBuilder, outNewRaffle)
          .fee(minFee)
          .sendChangeTo(pkAddress.getErgoAddress)
          .build()

        val signedTx = prover.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        transactionId = txId



      }
      )
      Ok(
        s""""{
           |  raffle_id : "$raffleId"
           |  txId : "$transactionId"
           |}""".stripMargin
      ).as("application/json")
    } catch {
      case e: Throwable => exception(e)
    }
  }
}
